package cn.qzxy.hotwater

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
)

data class AppUpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val update: AppUpdateInfo) : AppUpdateCheckResult
    data object UpToDate : AppUpdateCheckResult
    data object NoRelease : AppUpdateCheckResult
    data class InvalidRelease(val message: String) : AppUpdateCheckResult
    data class Failed(val message: String) : AppUpdateCheckResult
}

sealed interface AppUpdateInstallResult {
    data object InstallationLaunched : AppUpdateInstallResult
    data object SourcePermissionRequired : AppUpdateInstallResult
    data class Failed(val message: String) : AppUpdateInstallResult
}

class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val pendingInstallPreferences = appContext.getSharedPreferences(
        PENDING_INSTALL_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @OptIn(InternalCoroutinesApi::class)
    suspend fun checkForUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "QZXY-HotWater-Android/${BuildConfig.VERSION_NAME}")
                .build()
            val call = client.newCall(request)
            val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true,
            ) { cause ->
                if (cause is CancellationException) call.cancel()
            }
            try {
                runInterruptible(Dispatchers.IO) { call.execute() }.use { response ->
                if (response.code == 404) {
                    return@withContext AppUpdateCheckResult.NoRelease
                }
                if (!response.isSuccessful) {
                    return@withContext AppUpdateCheckResult.Failed("检查更新失败：HTTP ${response.code}")
                }

                val payload = response.body?.string().orEmpty()
                val release = runCatching { JSONObject(payload) }.getOrElse {
                    return@withContext AppUpdateCheckResult.InvalidRelease("更新服务返回的数据格式无效")
                }
                val versionName = parseReleaseVersion(release.optString("tag_name"))
                    ?: return@withContext AppUpdateCheckResult.InvalidRelease("Release 标签必须使用 vX.Y.Z 格式")
                if (compareVersions(versionName, BuildConfig.VERSION_NAME) <= 0) {
                    return@withContext AppUpdateCheckResult.UpToDate
                }

                val apkUrl = findApkUrl(release.optJSONArray("assets"), versionName)
                    ?: return@withContext AppUpdateCheckResult.InvalidRelease("Release 中未找到 Android APK")
                AppUpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        versionName = versionName,
                        releaseNotes = release.optString("body").trim(),
                        apkUrl = apkUrl,
                    ),
                )
                }
            } finally {
                cancellationHandle.dispose()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            AppUpdateCheckResult.Failed("网络连接失败，请稍后重试")
        } catch (error: Exception) {
            AppUpdateCheckResult.Failed("检查更新失败：${error.message ?: "未知错误"}")
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun downloadAndInstall(
        update: AppUpdateInfo,
        onProgress: (AppUpdateDownloadProgress) -> Unit = {},
    ): AppUpdateInstallResult = updateInstallMutex.withLock {
        withContext(Dispatchers.IO) {
            val targetFile = updateFile(update.versionName)
            val partialFile = File(targetFile.parentFile, "${targetFile.name}.part")
            try {
                clearPendingInstall()
                if (targetFile.isFile && validateUpdatePackage(targetFile, update.versionName) == null) {
                    return@withContext installDownloadedPackage(
                        apkFile = targetFile,
                        versionName = update.versionName,
                        openPermissionSettings = true,
                    )
                }
                if (targetFile.exists() && !targetFile.delete()) {
                    return@withContext AppUpdateInstallResult.Failed("无法替换旧的安装包")
                }

                val request = Request.Builder()
                    .url(update.apkUrl)
                    .header("User-Agent", "QZXY-HotWater-Android/${BuildConfig.VERSION_NAME}")
                    .build()
                partialFile.delete()
                val call = client.newCall(request)
                val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion(
                    onCancelling = true,
                    invokeImmediately = true,
                ) { cause ->
                    if (cause is CancellationException) call.cancel()
                }
                try {
                    runInterruptible(Dispatchers.IO) { call.execute() }.use { response ->
                        if (!response.isSuccessful) {
                            return@withContext AppUpdateInstallResult.Failed(
                                "下载安装包失败：HTTP ${response.code}",
                            )
                        }
                        val body = response.body ?: return@withContext AppUpdateInstallResult.Failed(
                            "下载安装包失败：响应为空",
                        )
                        val totalBytes = body.contentLength().takeIf { it > 0L }
                        var downloadedBytes = 0L
                        var lastReportedPercent = -1
                        var lastReportedBytes = -PROGRESS_REPORT_INTERVAL_BYTES

                        suspend fun reportProgress(force: Boolean = false) {
                            val progress = AppUpdateDownloadProgress(downloadedBytes, totalBytes)
                            val percent = progress.fraction?.let { (it * 100).toInt() }
                            val shouldReport = force || when {
                                percent != null -> percent != lastReportedPercent
                                else -> downloadedBytes - lastReportedBytes >=
                                    PROGRESS_REPORT_INTERVAL_BYTES
                            }
                            if (shouldReport) {
                                lastReportedPercent = percent ?: lastReportedPercent
                                lastReportedBytes = downloadedBytes
                                withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                            }
                        }

                        reportProgress(force = true)
                        partialFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            body.byteStream().use { input ->
                                while (true) {
                                    val read = runInterruptible(Dispatchers.IO) { input.read(buffer) }
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    downloadedBytes += read
                                    reportProgress()
                                }
                            }
                            output.flush()
                        }
                        reportProgress(force = true)
                    }
                } finally {
                    cancellationHandle.dispose()
                }

                if (targetFile.exists() && !targetFile.delete()) {
                    partialFile.delete()
                    return@withContext AppUpdateInstallResult.Failed("无法替换旧的安装包")
                }
                if (!partialFile.renameTo(targetFile)) {
                    partialFile.copyTo(targetFile, overwrite = true)
                    partialFile.delete()
                }

                validateUpdatePackage(targetFile, update.versionName)?.let { validationError ->
                    targetFile.delete()
                    return@withContext AppUpdateInstallResult.Failed(validationError)
                }
                installDownloadedPackage(
                    apkFile = targetFile,
                    versionName = update.versionName,
                    openPermissionSettings = true,
                )
            } catch (cancelled: CancellationException) {
                partialFile.delete()
                throw cancelled
            } catch (error: IOException) {
                partialFile.delete()
                AppUpdateInstallResult.Failed("下载安装包失败，请检查网络后重试")
            } catch (error: Exception) {
                partialFile.delete()
                AppUpdateInstallResult.Failed("无法启动安装：${error.message ?: "未知错误"}")
            }
        }
    }

    suspend fun resumePendingInstall(
        canLaunchInstaller: () -> Boolean = { true },
    ): AppUpdateInstallResult? = updateInstallMutex.withLock {
        withContext(Dispatchers.IO) {
            val versionName = readPendingInstallVersion() ?: return@withContext null
            val targetFile = updateFile(versionName)
            validateUpdatePackage(targetFile, versionName)?.let { validationError ->
                clearPendingInstall()
                targetFile.delete()
                return@withContext AppUpdateInstallResult.Failed(
                    "已下载的$validationError，请重新下载更新。",
                )
            }
            installDownloadedPackageIfAllowed(
                apkFile = targetFile,
                versionName = versionName,
                openPermissionSettings = false,
                canLaunchInstaller = canLaunchInstaller,
            )
        }
    }

    private suspend fun installDownloadedPackage(
        apkFile: File,
        versionName: String,
        openPermissionSettings: Boolean,
    ): AppUpdateInstallResult = checkNotNull(
        installDownloadedPackageIfAllowed(
            apkFile = apkFile,
            versionName = versionName,
            openPermissionSettings = openPermissionSettings,
            canLaunchInstaller = null,
        ),
    )

    private suspend fun installDownloadedPackageIfAllowed(
        apkFile: File,
        versionName: String,
        openPermissionSettings: Boolean,
        canLaunchInstaller: (() -> Boolean)?,
    ): AppUpdateInstallResult? {
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            if (!rememberPendingInstall(versionName)) {
                return AppUpdateInstallResult.Failed("无法保存待安装状态，请重试")
            }
            if (openPermissionSettings) {
                try {
                    withContext(Dispatchers.Main) { openUnknownSourceSettings() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    clearPendingInstall()
                    return AppUpdateInstallResult.Failed(
                        "无法打开安装权限设置：${error.message ?: "未知错误"}",
                    )
                }
            }
            return AppUpdateInstallResult.SourcePermissionRequired
        }

        return try {
            val launched = withContext(Dispatchers.Main.immediate) {
                if (canLaunchInstaller?.invoke() == false) {
                    false
                } else {
                    launchPackageInstaller(apkFile)
                    true
                }
            }
            if (!launched) return null
            clearPendingInstall()
            AppUpdateInstallResult.InstallationLaunched
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            clearPendingInstall()
            AppUpdateInstallResult.Failed("无法启动安装：${error.message ?: "未知错误"}")
        }
    }

    private fun validateUpdatePackage(apkFile: File, expectedVersionName: String): String? {
        if (!apkFile.isFile || apkFile.length() <= 0L) return "安装包不存在"
        val signingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = appContext.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            signingFlags,
        ) ?: return "安装包无效"
        if (packageInfo.packageName != appContext.packageName) {
            return "安装包与当前应用不匹配"
        }
        if (packageInfo.versionName != expectedVersionName) {
            return "安装包版本与更新信息不匹配"
        }
        if (packageVersionCode(packageInfo) <= BuildConfig.VERSION_CODE.toLong()) {
            return "安装包版本不高于当前版本"
        }
        if (!hasCompatibleSigningCertificate(packageInfo, signingFlags)) {
            return "安装包签名与当前应用不匹配"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(packageInfo: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    private fun hasCompatibleSigningCertificate(
        archivePackageInfo: android.content.pm.PackageInfo,
        signingFlags: Int,
    ): Boolean = runCatching {
        val installedPackageInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            signingFlags,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val installedSigningInfo = installedPackageInfo.signingInfo ?: return@runCatching false
            val archiveSigningInfo = archivePackageInfo.signingInfo ?: return@runCatching false
            if (installedSigningInfo.hasMultipleSigners() || archiveSigningInfo.hasMultipleSigners()) {
                installedSigningInfo.apkContentsSigners.orEmpty().toSet() ==
                    archiveSigningInfo.apkContentsSigners.orEmpty().toSet()
            } else {
                val installedSigner = installedSigningInfo.apkContentsSigners
                    .orEmpty()
                    .singleOrNull()
                    ?: return@runCatching false
                archiveSigningInfo.signingCertificateHistory
                    .orEmpty()
                    .any { archiveSigner -> archiveSigner == installedSigner }
            }
        } else {
            val installedSignatures = installedPackageInfo.signatures.orEmpty().toSet()
            installedSignatures.isNotEmpty() &&
                installedSignatures == archivePackageInfo.signatures.orEmpty().toSet()
        }
    }.getOrDefault(false)

    // Settings may replace this process immediately after the pending marker is written.
    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun rememberPendingInstall(versionName: String): Boolean =
        pendingInstallPreferences.edit()
            .putString(PENDING_INSTALL_VERSION, versionName)
            .commit()

    private fun readPendingInstallVersion(): String? {
        val versionName = pendingInstallPreferences.getString(PENDING_INSTALL_VERSION, null)
            ?: return null
        if (!RELEASE_VERSION_PATTERN.matches(versionName)) {
            clearPendingInstall()
            return null
        }
        return versionName
    }

    // Persist removal before handing control to the package installer.
    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun clearPendingInstall() {
        pendingInstallPreferences.edit().remove(PENDING_INSTALL_VERSION).commit()
    }

    private fun updateFile(versionName: String): File {
        val directory = File(appContext.cacheDir, "updates").apply { mkdirs() }
        return File(directory, "hotwater-android-v$versionName.apk")
    }

    private fun openUnknownSourceSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${appContext.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private fun launchPackageInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    private fun findApkUrl(assets: JSONArray?, versionName: String): String? {
        if (assets == null) return null
        val expectedName = "hotwater-android-v$versionName.apk"
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
                .ifBlank { asset.optString("download_url") }
            if (url.isBlank() || !url.startsWith("https://")) continue
            if (name == expectedName) return url
        }
        return null
    }

    private fun parseReleaseVersion(tag: String): String? {
        val match = VERSION_PATTERN.matchEntire(tag.trim()) ?: return null
        return "${match.groupValues[1]}.${match.groupValues[2]}.${match.groupValues[3]}"
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split('.').map { it.toInt() }
        val rightParts = right.split('.').map { it.toInt() }
        return leftParts.zip(rightParts)
            .map { (leftPart, rightPart) -> leftPart.compareTo(rightPart) }
            .firstOrNull { it != 0 }
            ?: 0
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://gitee.com/api/v5/repos/Solbrilo/quzhi-campus-third-party/releases/latest"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val PROGRESS_REPORT_INTERVAL_BYTES = 256L * 1024L
        const val PENDING_INSTALL_PREFERENCES = "app_update_install"
        const val PENDING_INSTALL_VERSION = "pending_version"
        val VERSION_PATTERN = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)$")
        val RELEASE_VERSION_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+$")

        // MainActivity and AboutActivity own separate managers but share update files and state.
        val updateInstallMutex = Mutex()
    }
}
