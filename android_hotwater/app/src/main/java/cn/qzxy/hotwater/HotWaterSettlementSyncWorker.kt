package cn.qzxy.hotwater

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

object HotWaterSettlementSyncScheduler {
    fun enqueue(context: Context) {
        val appContext = context.applicationContext
        if (HotWaterConfigStore(appContext).loadPendingSettlements().isEmpty()) return

        val request = OneTimeWorkRequestBuilder<HotWaterSettlementSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .setInitialDelay(8L, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private const val UNIQUE_WORK_NAME = "hot_water_pending_settlement_sync"
}

class HotWaterSettlementSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = HotWaterConfigStore(applicationContext)
        if (store.loadPendingSettlements().isEmpty()) return Result.success()

        val config = RuntimeDeviceInfoResolver.enrichConfig(
            applicationContext,
            store.loadConfig(),
        )
        if (!config.isComplete()) return Result.success()

        val repository = HotWaterRepository(HotWaterApi(), store)
        var lastError: Throwable? = null
        for (delayMs in LOCAL_RETRY_DELAYS_MS) {
            delay(delayMs)
            try {
                repository.check(config)
                refreshSurfaces()
                if (store.loadPendingSettlements().isEmpty()) {
                    return Result.success()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: CredentialExpiredException) {
                if (!store.markCredentialsExpired(config.loginCode)) {
                    return Result.success()
                }
                store.saveWidgetState(
                    title = "凭证已过期",
                    message = CREDENTIAL_EXPIRED_MESSAGE,
                    isBusy = false,
                    tone = "warning",
                )
                refreshSurfaces()
                return Result.failure(workDataOf(KEY_ERROR to CREDENTIAL_EXPIRED_MESSAGE))
            } catch (error: Exception) {
                lastError = error
            }
        }

        return if (runAttemptCount < MAX_WORK_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure(
                workDataOf(KEY_ERROR to (lastError?.message ?: "Settlement still pending")),
            )
        }
    }

    private fun refreshSurfaces() {
        HotWaterWidgetUi.updateAllWidgets(applicationContext)
        TileService.requestListeningState(
            applicationContext,
            ComponentName(applicationContext, HotWaterTileService::class.java),
        )
    }

    companion object {
        private const val KEY_ERROR = "settlement_sync_error"
        private const val MAX_WORK_ATTEMPTS = 5
        private val LOCAL_RETRY_DELAYS_MS = longArrayOf(350L, 900L, 1_800L, 3_200L)
    }
}
