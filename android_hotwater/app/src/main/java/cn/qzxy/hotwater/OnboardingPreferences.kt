package cn.qzxy.hotwater

import android.content.Context
import java.io.File
import android.util.AtomicFile

enum class InstallationIntroduction { NONE, FIRST_INSTALL, UPDATE }

class OnboardingPreferences(context: Context) {
    private val markerFile = File(context.noBackupFilesDir, MARKER_FILE_NAME)
    private val experienceFile = AtomicFile(File(context.noBackupFilesDir, "hotwater_introduction_experience"))

    fun introductionForCurrentVersion(): InstallationIntroduction = OnboardingVersionGate.introduction(
        completedVersionCode(), BuildConfig.VERSION_CODE,
        runCatching { experienceFile.readFully().toString(Charsets.UTF_8) == experienceKey() }.getOrDefault(false),
    )

    private fun experienceKey() = "${BuildConfig.VERSION_CODE}:2"

    fun completedVersionCode(): Int? = runCatching {
        markerFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
    }.getOrNull()

    fun markCurrentVersionCompleted() {
        runCatching {
            markerFile.parentFile?.mkdirs()
            val output = experienceFile.startWrite()
            try {
                output.write(experienceKey().toByteArray(Charsets.UTF_8))
                experienceFile.finishWrite(output)
            } catch (error: Exception) {
                experienceFile.failWrite(output)
                throw error
            }
            markerFile.writeText(BuildConfig.VERSION_CODE.toString())
        }
    }

    fun clear() {
        runCatching { markerFile.delete() }
        experienceFile.delete()
    }

    companion object {
        private const val MARKER_FILE_NAME = "hotwater_onboarding_version"
    }
}

internal object OnboardingVersionGate {
    fun introduction(completedVersionCode: Int?, currentVersionCode: Int, currentExperienceCompleted: Boolean): InstallationIntroduction = when {
        completedVersionCode == null -> InstallationIntroduction.FIRST_INSTALL
        completedVersionCode != currentVersionCode || !currentExperienceCompleted -> InstallationIntroduction.UPDATE
        else -> InstallationIntroduction.NONE
    }
}
