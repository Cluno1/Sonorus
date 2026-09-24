package io.github.cluno1.sonorus.features.clientimages.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.cluno1.sonorus.features.clientimages.data.ClientImageRepository
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageBatchProgress
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageTransferState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ClientImageTransferRunner(context: Context) {
    private val applicationContext = context.applicationContext
    private val repository = ClientImageRepository.get(context)

    suspend fun run(
        batchLocalId: String,
        onProgress: suspend (ClientImageBatchProgress) -> Unit = {},
    ): Boolean {
        val batch = repository.batch(batchLocalId) ?: return false
        val identity = repository.currentIdentity() ?: return false
        if (batch.ownerUserId != identity.first || batch.serverOrigin != identity.second) return false
        if (batch.state in setOf(ClientImageTransferState.CANCELLED, ClientImageTransferState.PAUSED)) {
            return false
        }
        val capabilities = repository.capabilities()
        if (!capabilities.enabled) return false
        val concurrency = if (isWifi()) 3 else 2
        val semaphore = Semaphore(concurrency.coerceAtMost(capabilities.maxParallelUploads).coerceAtLeast(1))
        val items = repository.runnableItems(batchLocalId)
        supervisorScope {
            items.map { item ->
                async {
                    semaphore.withPermit {
                        try {
                            repository.uploadItem(batchLocalId, item.localId, capabilities)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // The repository persists a retryable/permanent per-file result. One bad
                            // file must not cancel the other 159 items in the batch.
                        } finally {
                            onProgress(repository.progress(batchLocalId))
                        }
                    }
                }
            }.awaitAll()
        }
        return repository.runnableItems(batchLocalId).isNotEmpty()
    }

    private fun isWifi(): Boolean {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
