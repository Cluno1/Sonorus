package io.github.cluno1.sonorus.features.clientimages.background

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Android 14+ user-initiated data-transfer job. */
class ClientImageTransferJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<Int, Job>()
    private val stopped = ConcurrentHashMap.newKeySet<Int>()

    override fun onStartJob(params: JobParameters): Boolean {
        val batchId = params.extras.getString(ClientImageTransferWorker.KEY_BATCH_ID)
            ?: return false
        stopped.remove(params.jobId)
        val notificationId = ClientImageTransferScheduler.notificationId(batchId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setNotification(
                params,
                notificationId,
                ClientImageTransferNotifications.build(this),
                JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var retry = false
            try {
                retry = ClientImageTransferRunner(this@ClientImageTransferJobService).run(batchId) { progress ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setNotification(
                            params,
                            notificationId,
                            ClientImageTransferNotifications.build(this@ClientImageTransferJobService, progress),
                            JOB_END_NOTIFICATION_POLICY_REMOVE,
                        )
                    }
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Throwable) {
                retry = true
            } finally {
                running.remove(params.jobId)
                if (!stopped.remove(params.jobId)) {
                    jobFinished(params, retry)
                }
            }
        }
        running.put(params.jobId, job)?.cancel()
        job.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped.add(params.jobId)
        running.remove(params.jobId)?.cancel()
        return false
    }

    override fun onDestroy() {
        stopped.addAll(running.keys)
        scope.cancel()
        running.clear()
        super.onDestroy()
    }
}
