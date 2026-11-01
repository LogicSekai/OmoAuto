// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.streaming.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.logicsekai.omoauto.common.OmoLog
import java.net.InetAddress
import java.net.Socket

/**
 * Creates sockets for the display link. If a Wi-Fi network routes to the display's address the
 * socket is bound to it, so traffic never leaks onto mobile data; otherwise the kernel routing
 * table (Wi-Fi Direct interface) is used as is.
 */
object BikeSockets {

    fun create(context: Context, host: String): Socket {
        val socket = Socket()
        try {
            val target = InetAddress.getByName(host)
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return socket
            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val links = cm.getLinkProperties(network) ?: continue
                val routes = links.routes.any { route ->
                    val prefix = route.destination
                    prefix.prefixLength > 0 && matches(prefix.address.address, prefix.prefixLength, target.address)
                }
                if (routes) {
                    network.bindSocket(socket)
                    break
                }
            }
        } catch (e: Exception) {
            OmoLog.w(TAG, "Socket binding skipped", e)
        }
        return socket
    }

    /** True if [address] is inside [prefix]/[length]. */
    internal fun matches(prefix: ByteArray, length: Int, address: ByteArray): Boolean {
        if (prefix.size != address.size) return false
        var bits = length
        var i = 0
        while (bits > 0 && i < prefix.size) {
            val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((prefix[i].toInt() and mask) != (address[i].toInt() and mask)) return false
            bits -= 8
            i++
        }
        return true
    }

    private const val TAG = "OmoSockets"
}
