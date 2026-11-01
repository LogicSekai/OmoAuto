// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Logic Sekai and OmoAuto contributors. See NOTICE.

package com.logicsekai.omoauto.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.logicsekai.omoauto.common.OmoLog
import com.logicsekai.omoauto.common.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Implemented by the Application so [UpdateCheckWorker] can reach the shared [UpdateManager]. */
interface UpdateManagerOwner {
    val updateManager: UpdateManager
}

/**
 * Finds, downloads and installs new OmoAuto versions from GitHub Releases.
 *
 * - Foreground checks ([check]) feed [state] for the Home banner and the Update screen.
 * - Background checks run every 12 h through WorkManager and post one notification per version.
 * - When [canSelfInstall] is false (debug builds) the UI only links to the release page.
 * - Google Play builds pass a blank repository, so everything here is switched off.
 */
class UpdateManager(
    context: Context,
    private val checker: UpdateChecker,
    val canSelfInstall: Boolean,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        val release: UpdateChecker.Release? get() = null

        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(override val release: UpdateChecker.Release) : State
        data class Downloading(override val release: UpdateChecker.Release, val progress: Float?) : State
        data class ReadyToInstall(override val release: UpdateChecker.Release, val file: File) : State
        data class Failed(override val release: UpdateChecker.Release?, val problem: Problem) : State
    }

    enum class Problem { NETWORK, CHECKSUM, INVALID_APK, STORAGE }

    enum class InstallResult { STARTED, NEEDS_PERMISSION, UNAVAILABLE }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(appContext.cacheDir, "updates")
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    val isEnabled: Boolean get() = checker.isEnabled
    val releasesUrl: String get() = checker.releasesUrl

    /** Time of the last successful check (epoch millis), 0 when never checked. */
    val lastCheckMillis: Long get() = prefs.getLong(KEY_LAST_CHECK, 0)

    /** Call once from Application.onCreate. */
    fun start() {
        if (!isEnabled) {
            cancelBackgroundChecks()
            return
        }
        if (_autoCheck.value) scheduleBackgroundChecks() else cancelBackgroundChecks()
        // Downloads from an earlier run are either installed or stale.
        scope.launch(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
    }

    fun setAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        _autoCheck.value = enabled
        if (enabled && isEnabled) scheduleBackgroundChecks() else cancelBackgroundChecks()
    }

    /**
     * Checks for a new version. Without [force] it does nothing while a check or download is
     * running or a new version is already known.
     */
    fun check(force: Boolean = false) {
        if (!isEnabled) return
        if (job?.isActive == true) return
        val current = _state.value
        if (!force && (current is State.Available || current is State.ReadyToInstall)) return
        job = scope.launch {
            _state.value = State.Checking
            val result = checker.latest()
            _state.value = when (result) {
                is UpdateChecker.CheckResult.Newer -> State.Available(result.release)
                UpdateChecker.CheckResult.UpToDate -> State.UpToDate
                UpdateChecker.CheckResult.Failed -> State.Failed(null, Problem.NETWORK)
            }
            if (result !is UpdateChecker.CheckResult.Failed) markChecked()
        }
    }

    /** Background check from [UpdateCheckWorker]. Returns false to ask WorkManager for a retry. */
    suspend fun checkInBackground(): Boolean {
        if (!isEnabled || !_autoCheck.value) return true
        val result = checker.latest()
        if (result is UpdateChecker.CheckResult.Failed) return false
        markChecked()
        if (result is UpdateChecker.CheckResult.Newer) {
            val release = result.release
            val current = _state.value
            if (current is State.Idle || current is State.UpToDate || current is State.Failed) {
                _state.value = State.Available(release)
            }
            if (prefs.getString(KEY_NOTIFIED_VERSION, null) != release.version) {
                if (UpdateNotifier.show(appContext, release)) {
                    prefs.edit().putString(KEY_NOTIFIED_VERSION, release.version).apply()
                }
            }
        }
        return true
    }

    /** Downloads the APK of the known new version (GitHub builds only). */
    fun download() {
        if (!canSelfInstall) return
        val release = _state.value.release ?: return
        val url = release.apkUrl ?: return
        job?.cancel()
        job = scope.launch {
            _state.value = State.Downloading(release, null)
            _state.value = withContext(Dispatchers.IO) { fetch(release, url) }
        }
    }

    fun cancelDownload() {
        val current = _state.value as? State.Downloading ?: return
        job?.cancel()
        _state.value = State.Available(current.release)
    }

    /**
     * Opens the system installer for a finished download. If Android does not yet allow OmoAuto to
     * install apps, opens that setting instead and returns [InstallResult.NEEDS_PERMISSION].
     */
    fun install(context: Context): InstallResult {
        val ready = _state.value as? State.ReadyToInstall ?: return InstallResult.UNAVAILABLE
        if (!ready.file.exists()) {
            _state.value = State.Available(ready.release)
            return InstallResult.UNAVAILABLE
        }
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            return if (startActivity(context, settings)) InstallResult.NEEDS_PERMISSION else InstallResult.UNAVAILABLE
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}$AUTHORITY_SUFFIX", ready.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        UpdateNotifier.cancel(appContext)
        return if (startActivity(context, intent)) InstallResult.STARTED else InstallResult.UNAVAILABLE
    }

    private suspend fun fetch(release: UpdateChecker.Release, url: String): State {
        val target = File(dir, "OmoAuto-${release.version}.apk")
        val part = File(dir, "${target.name}.part")
        return try {
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return State.Failed(release, Problem.NETWORK)
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
                val digest = MessageDigest.getInstance("SHA-256")
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var reported = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            if (total != null && done - reported >= 256 * 1024) {
                                reported = done
                                _state.value = State.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                val sha256 = digest.digest().toHex()
                if (release.apkSha256 != null && !sha256.equals(release.apkSha256, ignoreCase = true)) {
                    OmoLog.w(TAG, "Checksum mismatch for ${release.version}: $sha256")
                    part.delete()
                    return State.Failed(release, Problem.CHECKSUM)
                }
                if (!isInstallableUpdate(part)) {
                    part.delete()
                    return State.Failed(release, Problem.INVALID_APK)
                }
                if (!part.renameTo(target)) {
                    part.delete()
                    return State.Failed(release, Problem.STORAGE)
                }
                OmoLog.i(TAG, "Downloaded ${release.version} (${target.length()} bytes, sha256 $sha256)")
                State.ReadyToInstall(release, target)
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: IOException) {
            OmoLog.w(TAG, "Download failed", e)
            part.delete()
            State.Failed(release, Problem.NETWORK)
        } catch (e: SecurityException) {
            OmoLog.w(TAG, "Download failed", e)
            part.delete()
            State.Failed(release, Problem.STORAGE)
        }
    }

    /** The file must be OmoAuto itself (same package) and newer than the installed version. */
    @Suppress("DEPRECATION")
    private fun isInstallableUpdate(apk: File): Boolean {
        val pm = appContext.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, 0) ?: return false
        if (archive.packageName != appContext.packageName) {
            OmoLog.w(TAG, "Downloaded APK is ${archive.packageName}, expected ${appContext.packageName}")
            return false
        }
        val installed = pm.getPackageInfo(appContext.packageName, 0)
        return archive.longVersion() > installed.longVersion()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersion(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    private fun markChecked() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    private fun scheduleBackgroundChecks() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun cancelBackgroundChecks() {
        WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
    }

    private fun startActivity(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    companion object {
        private const val TAG = "OmoUpdate"
        private const val PREFS = "omo_update"
        private const val KEY_AUTO_CHECK = "auto_check"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_NOTIFIED_VERSION = "notified_version"
        private const val WORK_NAME = "omo-update-check"
        private const val APK_MIME = "application/vnd.android.package-archive"

        /** Must match the FileProvider authority in core/data's AndroidManifest.xml. */
        private const val AUTHORITY_SUFFIX = ".updates"
    }
}
