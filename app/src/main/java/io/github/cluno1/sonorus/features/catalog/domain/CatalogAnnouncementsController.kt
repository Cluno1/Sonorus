package io.github.cluno1.sonorus.features.catalog.domain

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

data class CatalogAnnouncementsState(
    val items: List<CatalogAnnouncementSummary> = emptyList(),
    val nextCursor: String? = null,
    val read: AnnouncementReadState,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
) {
    val hasUnread: Boolean get() = read.hasUnread(items)
    val banner: CatalogAnnouncementSummary? get() = read.banner(items)
}

/**
 * issue 154: the announcement list every screen shares.
 *
 * Invariants:
 * - A failed fetch never throws and never clears what is shown. Announcements must not be able to
 *   disturb listening: the entry just keeps its last state and the next resume tries again.
 * - At most one request runs at a time. A refresh arriving during another is dropped, not queued,
 *   because the running one answers the same question.
 * - Only a successful fetch starts the 30 minute wait, so a device coming back online recovers on
 *   its next resume instead of staying blank for half an hour.
 *
 * Accepted residue: the list lives in memory, so a cold start without network shows no entry until
 * a fetch succeeds. An announcement taken down elsewhere can stay listed until the next refresh;
 * opening it answers 404, which [remove] handles. Later pages are dropped by a refresh that returns
 * a new first page, and scrolling loads them again.
 */
class CatalogAnnouncementsController(
    private val repository: CatalogAnnouncementsRepository,
    private val persistence: AnnouncementReadPersistence,
    private val serverUrl: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gate = Mutex()
    @Volatile private var etag: String? = null
    @Volatile private var lastSuccessAt: Long? = null
    @Volatile private var lastServer: String? = null

    private val _state = MutableStateFlow(CatalogAnnouncementsState(read = loadReadState()))
    val state: StateFlow<CatalogAnnouncementsState> = _state.asStateFlow()

    private fun loadReadState(): AnnouncementReadState = persistence.load()
        ?: AnnouncementReadState(baselineEpochMillis = clock()).also(persistence::save)

    suspend fun refresh(force: Boolean = false) {
        val server = serverUrl()
        val last = lastSuccessAt
        val fresh = server == lastServer && last != null && clock() - last < REFRESH_INTERVAL_MS
        if (!force && fresh) return
        if (!gate.tryLock()) return
        try {
            if (server != lastServer) {
                lastServer = server
                forgetList()
            }
            _state.update { it.copy(loading = true) }
            repository.listAnnouncements(etag = etag).fold(
                onSuccess = { fetch ->
                    if (fetch is CatalogAnnouncementFetch.Fetched) {
                        etag = fetch.etag
                        _state.update { it.copy(items = fetch.page.items, nextCursor = fetch.page.nextCursor) }
                    }
                    lastSuccessAt = clock()
                    _state.update { it.copy(loadFailed = false) }
                },
                onFailure = ::onFetchFailed,
            )
        } finally {
            _state.update { it.copy(loading = false) }
            gate.unlock()
        }
    }

    suspend fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (!gate.tryLock()) return
        try {
            repository.listAnnouncements(cursor = cursor).fold(
                onSuccess = { fetch ->
                    if (fetch is CatalogAnnouncementFetch.Fetched) {
                        _state.update { current ->
                            current.copy(
                                items = (current.items + fetch.page.items).distinctBy { it.id },
                                nextCursor = fetch.page.nextCursor,
                            )
                        }
                    }
                },
                onFailure = ::onFetchFailed,
            )
        } finally {
            gate.unlock()
        }
    }

    fun markRead(id: String) = updateRead { it.withRead(id) }

    fun dismissBanner(id: String) = updateRead { it.withDismissed(id) }

    /** The server said this announcement no longer exists. */
    fun remove(id: String) {
        _state.update { current -> current.copy(items = current.items.filterNot { it.id == id }) }
    }

    private fun updateRead(change: (AnnouncementReadState) -> AnnouncementReadState) {
        val before = _state.value.read
        val after = change(before)
        if (after == before) return
        _state.update { it.copy(read = after) }
        persistence.save(after)
    }

    private fun onFetchFailed(error: Throwable) {
        if (error is CancellationException) throw error
        when (error) {
            // Not connected to a server is a normal state for this app, not a fault.
            is CatalogFailure.NotConfigured -> {
                Log.d(TAG, "announcements skipped: catalog not configured")
                forgetList()
            }
            else -> {
                Log.w(TAG, "announcement fetch failed (${error.javaClass.simpleName})")
                _state.update { it.copy(loadFailed = true) }
            }
        }
    }

    private fun forgetList() {
        etag = null
        lastSuccessAt = null
        _state.update { it.copy(items = emptyList(), nextCursor = null, loadFailed = false) }
    }

    companion object {
        const val REFRESH_INTERVAL_MS = 30L * 60 * 1000
        private const val TAG = "CatalogAnnouncements"
    }
}
