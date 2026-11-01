// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.link

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.logicsekai.omoauto.common.OmoLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wi-Fi Direct link to the motorcycle display. The display is the group owner; we join as a
 * client (PBC, group-owner intent 1) and read the display's IP from the connection info.
 *
 * Like WayXAuto, the group is **persistent** (Android 10+): the phone and the display remember
 * each other. A display that only knows the phone from a temporary group accepts the projection
 * connection but keeps its screen black; it showed the picture only after WayXAuto (which joins
 * persistently) had been used. A temporary group left over from earlier is therefore removed and
 * joined again persistently.
 *
 * All calls must happen on the main thread. Callers check runtime permissions first
 * (NEARBY_WIFI_DEVICES on Android 13+, ACCESS_FINE_LOCATION before).
 */
class WifiDirectLink(context: Context) {

    data class Peer(val address: String, val name: String, val status: Int) {
        /** Display names come as "DIRECT-xx-Name"; strip the Wi-Fi Direct prefix. */
        val displayName: String
            get() = when {
                name.isBlank() -> address
                name.startsWith("DIRECT-") && name.length > 7 -> name.substring(7)
                else -> name
            }
    }

    sealed interface State {
        data object Idle : State
        data object Unavailable : State
        data class Searching(val sinceMillis: Long) : State
        data class Connecting(val peer: Peer) : State
        data class Connected(val peer: Peer, val hostAddress: String) : State
        data class Failed(val reason: Reason, val peer: Peer?) : State
    }

    enum class Reason { WIFI_OFF, PERMISSION, CONNECT_FAILED, TIMEOUT, OTHER_GROUP_ACTIVE, LOST }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val manager: WifiP2pManager? = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var p2pEnabled = true
    private var target: Peer? = null
    private var generation = 0

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    p2pEnabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!p2pEnabled) fail(Reason.WIFI_OFF)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
            }
        }
    }

    private val searchTimeout = Runnable {
        if (_state.value is State.Searching) {
            stopDiscoveryQuietly()
            _state.value = State.Idle
        }
    }

    private val connectTimeout = Runnable {
        val current = _state.value
        if (current is State.Connecting) {
            OmoLog.w(TAG, "Wi-Fi Direct connect timed out")
            cancelConnectQuietly()
            _state.value = State.Failed(Reason.TIMEOUT, current.peer)
        }
    }

    val isSupported: Boolean get() = manager != null

    private fun ensureChannel(): WifiP2pManager.Channel? {
        val m = manager ?: run {
            _state.value = State.Unavailable
            return null
        }
        if (channel == null) {
            channel = m.initialize(appContext, Looper.getMainLooper()) {
                OmoLog.w(TAG, "Wi-Fi Direct channel lost")
                channel = null
            }
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        return channel
    }

    /** Searches for nearby displays for [durationMs]. */
    @SuppressLint("MissingPermission")
    fun startDiscovery(durationMs: Long = 15_000) {
        val ch = ensureChannel() ?: return
        if (_state.value is State.Connected || _state.value is State.Connecting) return
        _state.value = State.Searching(System.currentTimeMillis())
        try {
            manager?.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = requestPeers()
                override fun onFailure(reason: Int) {
                    OmoLog.w(TAG, "discoverPeers failed ($reason)")
                    _state.value = State.Failed(if (reason == WifiP2pManager.P2P_UNSUPPORTED) Reason.WIFI_OFF else Reason.CONNECT_FAILED, null)
                }
            })
        } catch (e: SecurityException) {
            _state.value = State.Failed(Reason.PERMISSION, null)
            return
        }
        handler.removeCallbacks(searchTimeout)
        handler.postDelayed(searchTimeout, durationMs)
    }

    fun stopDiscovery() {
        handler.removeCallbacks(searchTimeout)
        stopDiscoveryQuietly()
        if (_state.value is State.Searching) _state.value = State.Idle
    }

    /** Joins the display's group (persistently; see the class comment). */
    @SuppressLint("MissingPermission")
    fun connect(address: String, name: String) {
        val ch = ensureChannel() ?: return
        val peer = _peers.value.firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?: Peer(address, name, WifiP2pDevice.AVAILABLE)
        target = peer
        generation++
        val gen = generation
        handler.removeCallbacks(searchTimeout)
        _state.value = State.Connecting(peer)
        handler.removeCallbacks(connectTimeout)
        handler.postDelayed(connectTimeout, 25_000)
        try {
            manager?.requestGroupInfo(ch) { group ->
                if (gen != generation) return@requestGroupInfo
                val ownerIsDisplay = group?.owner?.deviceAddress?.equals(address, ignoreCase = true) == true
                val temporary = Build.VERSION.SDK_INT >= 30 && group != null && group.networkId == TEMPORARY_NETWORK_ID
                when {
                    group == null -> joinGroup(ch, peer, gen)
                    ownerIsDisplay && !temporary -> {
                        OmoLog.i(TAG, "Already in ${peer.displayName}'s group")
                        requestConnectionInfo()
                    }
                    else -> {
                        OmoLog.i(TAG, if (ownerIsDisplay) "Leaving the temporary group to join persistently" else "Leaving another Wi-Fi Direct group")
                        manager?.removeGroup(ch, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() { handler.postDelayed({ if (gen == generation) joinGroup(ch, peer, gen) }, 800) }
                            override fun onFailure(reason: Int) { if (gen == generation) joinGroup(ch, peer, gen) }
                        })
                    }
                }
            }
        } catch (e: SecurityException) {
            _state.value = State.Failed(Reason.PERMISSION, peer)
        }
    }

    @SuppressLint("MissingPermission")
    private fun joinGroup(ch: WifiP2pManager.Channel, peer: Peer, gen: Int) {
        val config = if (Build.VERSION.SDK_INT >= 29) {
            WifiP2pConfig.Builder()
                .setDeviceAddress(MacAddress.fromString(peer.address))
                .enablePersistentMode(true)
                .build()
        } else {
            WifiP2pConfig().apply { deviceAddress = peer.address }
        }.apply {
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 1
        }
        try {
            manager?.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    OmoLog.i(TAG, "Connecting to ${peer.displayName}…")
                    requestConnectionInfo()
                }

                override fun onFailure(reason: Int) {
                    if (gen != generation) return
                    OmoLog.w(TAG, "connect failed ($reason)")
                    handler.removeCallbacks(connectTimeout)
                    _state.value = State.Failed(Reason.CONNECT_FAILED, peer)
                }
            })
        } catch (e: SecurityException) {
            _state.value = State.Failed(Reason.PERMISSION, peer)
        } catch (e: IllegalArgumentException) {
            OmoLog.w(TAG, "Invalid Wi-Fi Direct config", e)
            _state.value = State.Failed(Reason.CONNECT_FAILED, peer)
        }
    }

    /** Leaves the group (if any) and forgets the target. */
    fun disconnect() {
        target = null
        generation++
        handler.removeCallbacks(connectTimeout)
        val ch = channel
        if (ch != null) {
            cancelConnectQuietly()
            manager?.removeGroup(ch, null)
        }
        _state.value = State.Idle
    }

    fun release() {
        disconnect()
        stopDiscovery()
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        if (Build.VERSION.SDK_INT >= 27) channel?.close()
        channel = null
    }

    // ---------------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun requestPeers() {
        val ch = channel ?: return
        try {
            manager?.requestPeers(ch) { list ->
                _peers.value = list.deviceList
                    .map { Peer(it.deviceAddress, it.deviceName.orEmpty(), it.status) }
                    .sortedBy { it.displayName.lowercase() }
            }
        } catch (e: SecurityException) {
            OmoLog.w(TAG, "requestPeers denied", e)
        }
    }

    private fun requestConnectionInfo() {
        val ch = channel ?: return
        val gen = generation
        manager?.requestConnectionInfo(ch) { info -> onConnectionInfo(info, gen) }
    }

    @SuppressLint("MissingPermission")
    private fun onConnectionInfo(info: WifiP2pInfo?, gen: Int) {
        if (gen != generation) return
        val wanted = target
        val current = _state.value
        if (info == null || !info.groupFormed) {
            if (current is State.Connected) {
                OmoLog.w(TAG, "Wi-Fi Direct group lost")
                _state.value = State.Failed(Reason.LOST, current.peer)
            }
            return
        }
        if (wanted == null) return
        if (info.isGroupOwner || info.groupOwnerAddress == null) {
            _state.value = State.Failed(Reason.OTHER_GROUP_ACTIVE, wanted)
            return
        }
        val host = info.groupOwnerAddress.hostAddress ?: return
        val ch = channel ?: return
        try {
            manager?.requestGroupInfo(ch) { group ->
                if (gen != generation) return@requestGroupInfo
                val owner = group?.owner?.deviceAddress
                if (owner != null && !owner.equals(wanted.address, ignoreCase = true)) {
                    _state.value = State.Failed(Reason.OTHER_GROUP_ACTIVE, wanted)
                    return@requestGroupInfo
                }
                handler.removeCallbacks(connectTimeout)
                stopDiscoveryQuietly()
                if ((_state.value as? State.Connected)?.hostAddress != host) {
                    OmoLog.i(TAG, "Wi-Fi Direct connected to ${wanted.displayName} at $host")
                    _state.value = State.Connected(wanted, host)
                }
            }
        } catch (e: SecurityException) {
            _state.value = State.Failed(Reason.PERMISSION, wanted)
        }
    }

    private fun fail(reason: Reason) {
        handler.removeCallbacks(connectTimeout)
        handler.removeCallbacks(searchTimeout)
        _state.value = State.Failed(reason, target)
    }

    private fun stopDiscoveryQuietly() {
        val ch = channel ?: return
        runCatching { manager?.stopPeerDiscovery(ch, null) }
    }

    private fun cancelConnectQuietly() {
        val ch = channel ?: return
        runCatching { manager?.cancelConnect(ch, null) }
    }

    private companion object {
        const val TAG = "OmoWifiDirect"
        /** WifiP2pGroup.NETWORK_ID_TEMPORARY (public only from API 30). */
        const val TEMPORARY_NETWORK_ID = -1
    }
}
