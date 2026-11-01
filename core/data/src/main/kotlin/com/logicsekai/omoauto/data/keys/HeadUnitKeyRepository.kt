// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.keys

import android.content.Context
import android.net.Uri
import com.logicsekai.omoauto.common.OmoLog
import com.logicsekai.omoauto.common.sha256Hex
import com.logicsekai.omoauto.protocol.aa.HeadUnitIdentity
import com.logicsekai.omoauto.protocol.aa.HeadUnitKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/**
 * Stores the head-unit certificate + key inside the app (no external storage).
 *
 * "Get keys" downloads the public sample identity from the open-source Open Headunit project,
 * pinned to an exact commit and verified by SHA-256 before it is used. Users can also import
 * their own PEM files.
 */
class HeadUnitKeyRepository(context: Context) {

    sealed interface KeyState {
        data object Missing : KeyState
        data class Ready(val name: String, val validUntil: Date, val source: Source) : KeyState
        data class Invalid(val problem: HeadUnitKeys.Problem) : KeyState
    }

    enum class Source { SAMPLE_DOWNLOAD, IMPORTED }

    sealed interface DownloadResult {
        data object Success : DownloadResult
        data class NetworkError(val message: String) : DownloadResult
        /** GitHub returned files that differ from the pinned fingerprints; they were discarded. */
        data object FingerprintMismatch : DownloadResult
        data class Rejected(val problem: HeadUnitKeys.Problem) : DownloadResult
    }

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "headunit").apply { mkdirs() }
    private val certFile = File(dir, "cert.pem")
    private val keyFile = File(dir, "key.pem")
    private val sourceFile = File(dir, "source")

    private val _state = MutableStateFlow<KeyState>(KeyState.Missing)
    val state: StateFlow<KeyState> = _state.asStateFlow()

    @Volatile private var cachedIdentity: HeadUnitIdentity? = null

    init {
        reload()
    }

    /** The identity for the AA session, or null when keys are missing/invalid. */
    fun identity(): HeadUnitIdentity? {
        cachedIdentity?.let { return it }
        reload()
        return cachedIdentity
    }

    suspend fun downloadSampleKeys(): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val cert = fetch(SAMPLE_CERT_URL)
            val key = fetch(SAMPLE_KEY_URL)
            if (cert.sha256Hex() != SAMPLE_CERT_SHA256 || key.sha256Hex() != SAMPLE_KEY_SHA256) {
                OmoLog.w(TAG, "Sample keys fingerprint mismatch; discarded")
                return@withContext DownloadResult.FingerprintMismatch
            }
            when (val result = save(String(cert), String(key), Source.SAMPLE_DOWNLOAD)) {
                is KeyState.Ready -> DownloadResult.Success
                is KeyState.Invalid -> DownloadResult.Rejected(result.problem)
                KeyState.Missing -> DownloadResult.Rejected(HeadUnitKeys.Problem.MISSING_PARTS)
            }
        } catch (e: IOException) {
            DownloadResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Imports PEM text from one or more files. The certificate and the key can be in separate
     * files or together in one file.
     */
    suspend fun importFromUris(uris: List<Uri>): KeyState = withContext(Dispatchers.IO) {
        val texts = uris.mapNotNull { uri ->
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().take(MAX_IMPORT_BYTES).toByteArray().toString(Charsets.UTF_8) }
            }.getOrNull()
        }
        val all = texts.joinToString("\n")
        val cert = extractBlock(all, "CERTIFICATE")
        val key = extractBlock(all, "ENCRYPTED PRIVATE KEY")
            ?: extractBlock(all, "PRIVATE KEY")
            ?: extractBlock(all, "RSA PRIVATE KEY")
            ?: extractBlock(all, "EC PRIVATE KEY")
        if (cert == null || key == null) {
            KeyState.Invalid(HeadUnitKeys.Problem.MISSING_PARTS)
        } else {
            save(cert, key, Source.IMPORTED, keepOldOnFailure = true)
        }
    }

    fun clear() {
        certFile.delete()
        keyFile.delete()
        sourceFile.delete()
        cachedIdentity = null
        _state.value = KeyState.Missing
    }

    private fun save(cert: String, key: String, source: Source, keepOldOnFailure: Boolean = false): KeyState {
        val parsed = HeadUnitKeys.parse(cert, key)
        if (parsed is HeadUnitKeys.Result.Invalid) {
            val invalid = KeyState.Invalid(parsed.problem)
            if (!keepOldOnFailure || _state.value !is KeyState.Ready) _state.value = invalid
            return invalid
        }
        certFile.writeText(cert)
        keyFile.writeText(key)
        sourceFile.writeText(source.name)
        reload()
        return _state.value
    }

    private fun reload() {
        if (!certFile.exists() || !keyFile.exists()) {
            cachedIdentity = null
            _state.value = KeyState.Missing
            return
        }
        when (val result = HeadUnitKeys.parse(certFile.readText(), keyFile.readText())) {
            is HeadUnitKeys.Result.Valid -> {
                cachedIdentity = result.identity
                val source = runCatching { Source.valueOf(sourceFile.readText().trim()) }.getOrDefault(Source.IMPORTED)
                _state.value = KeyState.Ready(HeadUnitKeys.displayName(result.subject), result.validUntil, source)
            }
            is HeadUnitKeys.Result.Invalid -> {
                cachedIdentity = null
                _state.value = KeyState.Invalid(result.problem)
            }
        }
    }

    private fun fetch(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractBlock(text: String, label: String): String? {
        val begin = "-----BEGIN $label-----"
        val end = "-----END $label-----"
        val start = text.indexOf(begin)
        if (start < 0) return null
        val stop = text.indexOf(end, start)
        if (stop < 0) return null
        return text.substring(start, stop + end.length) + "\n"
    }

    companion object {
        private const val TAG = "OmoKeys"
        private const val MAX_IMPORT_BYTES = 64 * 1024

        /** Open Headunit (andreknieriem/open-headunit), exact commit the app was tested with. */
        const val SAMPLE_KEYS_COMMIT = "a8d8a92d80256feee56174a21d2d33cc2664c9a7"
        const val SAMPLE_KEYS_BROWSE_URL =
            "https://github.com/andreknieriem/open-headunit/tree/$SAMPLE_KEYS_COMMIT/app/src/main/res/raw"
        private const val RAW = "https://raw.githubusercontent.com/andreknieriem/open-headunit/$SAMPLE_KEYS_COMMIT/app/src/main/res/raw"
        const val SAMPLE_CERT_URL = "$RAW/cert"
        const val SAMPLE_KEY_URL = "$RAW/privkey"
        const val SAMPLE_CERT_SHA256 = "851d80c86bf469a3dd121b7a7084ca97b91b3d2cc72922c451e0156b455f38c1"
        const val SAMPLE_KEY_SHA256 = "015d172a6e4d5b7f04a5c820a60a41673f99d6e01c2f658e2a0909efbf6d49f7"
    }
}
