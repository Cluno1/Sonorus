package io.github.cluno1.sonorus.features.clientimages.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.cluno1.sonorus.features.clientimages.data.ClientImageRepository

object ClientImageTransferScheduler {
    suspend fun start(context: Context, batchLocalId: String) {
        val app = context.applicationContext
        val batch = ClientImageRepository.get(app).batch(batchLocalId) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val extras = PersistableBundle().apply {
                putString(ClientImageTransferWorker.KEY_BATCH_ID, batchLocalId)
            }
            val network = if (batch.wifiOnly) {
                JobInfo.NETWORK_TYPE_UNMETERED
            } else {
                JobInfo.NETWORK_TYPE_ANY
            }
            val info = JobInfo.Builder(
                jobId(batchLocalId),
                ComponentName(app, ClientImageTransferJobService::class.java),
            )
                .setUserInitiated(true)
                .setRequiredNetworkType(network)
                .setEstimatedNetworkBytes(0L, batch.totalBytes.coerceAtLeast(1L))
                .setExtras(extras)
                .build()
            app.getSystemService(JobScheduler::class.java).schedule(info)
        } else {
            val request = OneTimeWorkRequestBuilder<ClientImageTransferWorker>()
                .setInputData(Data.Builder().putString(ClientImageTransferWorker.KEY_BATCH_ID, batchLocalId).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (batch.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .addTag(workName(batchLocalId))
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(
                workName(batchLocalId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    fun pause(context: Context, batchLocalId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(JobScheduler::class.java).cancel(jobId(batchLocalId))
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(workName(batchLocalId))
        }
    }

    fun notificationId(batchLocalId: String): Int =
        19_100 + (batchLocalId.hashCode() and 0x3fffffff)

    private fun jobId(batchLocalId: String): Int =
        91_000 + (batchLocalId.hashCode() and 0x3fffffff)

    private fun workName(batchLocalId: String) = "client-image-upload-$batchLocalId"
}
