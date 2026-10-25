package cn.qzxy.hotwater

import android.content.Context
import java.io.File

class OnboardingPreferences(context: Context) {
    private val markerFile = File(context.noBackupFilesDir, MARKER_FILE_NAME)

    fun shouldShowForCurrentVersion(skipUpgradeForRelease: Boolean = false): Boolean {
        val completedVersionCode = completedVersionCode()
        return OnboardingVersionGate.shouldShow(
            completedVersionCode,
            BuildConfig.VERSION_CODE,
            skipUpgradeForRelease,
        )
    }

    fun completedVersionCode(): Int? = runCatching {
        markerFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
    }.getOrNull()

    fun markCurrentVersionCompleted() {
        runCatching {
            markerFile.parentFile?.mkdirs()
            markerFile.writeText(BuildConfig.VERSION_CODE.toString())
        }
    }

    fun clear() {
        runCatching { markerFile.delete() }
    }

    companion object {
        private const val MARKER_FILE_NAME = "hotwater_onboarding_version"
    }
}

internal object OnboardingVersionGate {
    fun shouldShow(
        completedVersionCode: Int?,
        currentVersionCode: Int,
        skipUpgradeForRelease: Boolean = false,
    ): Boolean {
        if (skipUpgradeForRelease && completedVersionCode != null && completedVersionCode < currentVersionCode) {
            return false
        }
        return completedVersionCode != currentVersionCode
    }
}
