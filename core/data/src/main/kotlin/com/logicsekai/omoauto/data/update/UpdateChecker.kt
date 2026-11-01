// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.update

import com.logicsekai.omoauto.common.OmoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer version. [repository] is "owner/name"; leave it blank to
 * disable update checks (e.g. for Play Store builds).
 */
class UpdateChecker(private val repository: String, private val currentVersion: String) {

    /**
     * A published release. [apkUrl] is the release APK attached to it (null when none is attached),
     * [apkSha256] its checksum as reported by GitHub (null when GitHub does not report one).
     */
    data class Release(
        val version: String,
        val pageUrl: String,
        val notes: String,
        val apkUrl: String? = null,
        val apkSize: Long = 0,
        val apkSha256: String? = null,
    )

    sealed interface CheckResult {
        data class Newer(val release: Release) : CheckResult
        data object UpToDate : CheckResult
        data object Failed : CheckResult
    }

    val isEnabled: Boolean get() = repository.isNotBlank()

    /** URL of the releases page, or blank when updates are disabled. */
    val releasesUrl: String get() = if (isEnabled) "https://github.com/$repository/releases" else ""

    suspend fun latest(): CheckResult = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext CheckResult.UpToDate
        try {
            val connection = URL("https://api.github.com/repos/$repository/releases/latest").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> Unit
                    // No release published yet.
                    HttpURLConnection.HTTP_NOT_FOUND -> return@withContext CheckResult.UpToDate
                    else -> return@withContext CheckResult.Failed
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val release = parseRelease(json) ?: return@withContext CheckResult.UpToDate
                if (isNewer(release.version, currentVersion)) CheckResult.Newer(release) else CheckResult.UpToDate
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            OmoLog.w(TAG, "Update check failed", e)
            CheckResult.Failed
        }
    }

    /** Convenience for callers that only care about a newer release. */
    suspend fun newerRelease(): Release? = (latest() as? CheckResult.Newer)?.release

    companion object {
        private const val TAG = "OmoUpdate"

        fun parseRelease(json: JSONObject): Release? {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val version = json.optString("tag_name").removePrefix("v").trim()
            if (version.isBlank()) return null
            val apk = pickApk(json.optJSONArray("assets"))
            return Release(
                version = version,
                pageUrl = json.optString("html_url"),
                notes = json.optString("body").trim(),
                apkUrl = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") },
                apkSize = apk?.optLong("size") ?: 0,
                apkSha256 = apk?.optString("digest")
                    ?.takeIf { it.startsWith("sha256:") }
                    ?.removePrefix("sha256:")
                    ?.lowercase(),
            )
        }

        /** The release APK: an ".apk" asset that is not a debug build. */
        private fun pickApk(assets: JSONArray?): JSONObject? {
            if (assets == null) return null
            val apks = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                .filterNot { it.optString("name").contains("debug", ignoreCase = true) }
            return apks.firstOrNull { it.optString("name").contains("release", ignoreCase = true) } ?: apks.firstOrNull()
        }

        /** Semantic-ish comparison: "0.10.0" > "0.9.3"; suffixes like "-beta" sort before the plain version. */
        fun isNewer(remote: String, rawCurrent: String): Boolean {
            // A local "-debug" build of 0.3.0 is not a pre-release of 0.3.0.
            val current = rawCurrent.removeSuffix("-debug")
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(remote)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            val remotePre = remote.contains('-')
            val currentPre = current.contains('-')
            return currentPre && !remotePre
        }
    }
}
