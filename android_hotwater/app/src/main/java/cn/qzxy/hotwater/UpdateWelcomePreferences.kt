package cn.qzxy.hotwater

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Local installation marker, excluded from backups and independent of account sign-in. */
internal class UpdateWelcomePreferences(context: Context) {
    private val marker = AtomicFile(File(context.noBackupFilesDir, "welcome_3_1_2"))

    fun shouldShow(previousCompletedVersion: Int?, firstInstallTime: Long, lastUpdateTime: Long): Boolean {
        if (BuildConfig.VERSION_NAME != UpdateWelcomeGate.VERSION) return false
        val state = runCatching { marker.openRead().use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        if (state == "seen" || state == "fresh") return false
        if (state == "pending") return true
        val eligible = UpdateWelcomeGate.isUpgrade(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            previousCompletedVersion, firstInstallTime, lastUpdateTime)
        write(if (eligible) "pending" else "fresh")
        return eligible
    }

    fun markSeen(): Boolean = write("seen")

    private fun write(value: String): Boolean {
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = marker.startWrite()
            stream.write(value.toByteArray(Charsets.UTF_8))
            marker.finishWrite(stream)
            true
        } catch (_: Exception) {
            stream?.let(marker::failWrite)
            false
        }
    }
}

internal object UpdateWelcomeGate {
    const val VERSION = "3.1.2"
    fun isUpgrade(version: String, currentCode: Int, previousCode: Int?, firstInstallTime: Long, lastUpdateTime: Long): Boolean {
        if (version != VERSION) return false
        if (previousCode != null) return previousCode in 1 until currentCode
        return firstInstallTime > 0L && lastUpdateTime > firstInstallTime
    }
}
