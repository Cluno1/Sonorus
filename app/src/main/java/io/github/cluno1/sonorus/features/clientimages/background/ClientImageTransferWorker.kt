package io.github.cluno1.sonorus.features.clientimages.background

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class ClientImageTransferWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return Result.failure()
        setForeground(createForegroundInfo(batchId))
        val notificationId = ClientImageTransferScheduler.notificationId(batchId)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        return try {
            val retry = ClientImageTransferRunner(applicationContext).run(batchId) { progress ->
                manager.notify(notificationId, ClientImageTransferNotifications.build(applicationContext, progress))
            }
            if (retry && runAttemptCount < 5) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private fun createForegroundInfo(batchId: String) = ForegroundInfo(
        ClientImageTransferScheduler.notificationId(batchId),
        ClientImageTransferNotifications.build(applicationContext),
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    companion object {
        const val KEY_BATCH_ID = "client_image_batch_id"
    }
}
