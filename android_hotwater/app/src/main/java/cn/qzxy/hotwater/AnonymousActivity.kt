package cn.qzxy.hotwater

import android.content.Context
import android.util.AtomicFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object ActivityToken {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    fun forDay(seed: ByteArray, day: String): String {
        require(seed.size == 32)
        LocalDate.parse(day)
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(seed, "HmacSHA256"))
            doFinal(("hotwater-activity-v1:" + day).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

/** Counts anonymous installations, never accounts. The seed never leaves this device. */
internal object AnonymousActivity {
    private const val PREFS = "anonymous_activity"
    private const val SEED_FILE = "anonymous_activity_seed"
    const val WORK_TAG = "anonymous_daily_activity"
    private val lock = Any()
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, enabled: Boolean) {
        synchronized(lock) {
            prefs(context).edit().putBoolean("enabled", enabled).commit()
            if (!enabled) WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
        if (enabled) recordOpen(context)
    }

    fun recordOpen(context: Context) = synchronized(lock) {
        if (!isEnabled(context)) return@synchronized
        val day = LocalDate.now(ActivityToken.zone).toString()
        if (prefs(context).getString("ack_day", null) == day) return@synchronized
        // A no-backup random seed avoids both hardware identifiers and cloud-backup cloning.
        val token = runCatching {
            val file = AtomicFile(File(context.noBackupFilesDir, SEED_FILE))
            val existing = if (file.baseFile.exists()) file.readFully() else null
            val seed = existing?.takeIf { it.size == 32 } ?: ByteArray(32).also { fresh ->
                SecureRandom().nextBytes(fresh)
                val stream = file.startWrite()
                try { stream.write(fresh); file.finishWrite(stream) }
                catch (e: Exception) { file.failWrite(stream); throw e }
            }
            ActivityToken.forDay(seed, day)
        }.getOrNull() ?: return@synchronized
        val request = OneTimeWorkRequestBuilder<DailyActivityWorker>()
            .setInputData(workDataOf("day" to day, "token" to token))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(WORK_TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$WORK_TAG:$day", ExistingWorkPolicy.KEEP, request)
    }

    fun acknowledge(context: Context, day: String) = synchronized(lock) {
        if (!isEnabled(context) || !File(context.noBackupFilesDir, SEED_FILE).exists()) return@synchronized
        val previous = prefs(context).getString("ack_day", "").orEmpty()
        if (day > previous) prefs(context).edit().putString("ack_day", day).apply()
    }

    fun clear(context: Context): Boolean = synchronized(lock) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        // Keep an explicit opt-out after clearing until the user chooses to enable again.
        val cleared = prefs(context).edit().clear().putBoolean("enabled", false).commit()
        AtomicFile(File(context.noBackupFilesDir, SEED_FILE)).delete()
        cleared
    }
}

class DailyActivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!AnonymousActivity.isEnabled(applicationContext)) return@withContext Result.success()
        val day = inputData.getString("day") ?: return@withContext Result.failure()
        val token = inputData.getString("token") ?: return@withContext Result.failure()
        val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return@withContext Result.failure()
        val today = LocalDate.now(ActivityToken.zone)
        if (date.isAfter(today) || date.isBefore(today.minusDays(7))) return@withContext Result.success()
        val json = JSONObject().put("day", day).put("token", token).toString()
        val request = Request.Builder().url("https://nebularive.top/api/hotwater-activity")
            .header("User-Agent", "HotWater-AnonymousActivity/1")
            .post(json.toRequestBody("application/json".toMediaType())).build()
        try {
            CLIENT.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        AnonymousActivity.acknowledge(applicationContext, day)
                        Result.success()
                    }
                    response.code == 429 || response.code >= 500 -> Result.retry()
                    else -> Result.failure()
                }
            }
        } catch (_: java.io.IOException) { Result.retry() }
    }

    companion object {
        // No authentication, cookies, device attributes, account fields or request logging.
        private val CLIENT = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}
