package io.github.cluno1.sonorus.features.streaming.domain

import io.github.cluno1.sonorus.features.streaming.data.provider.PublicMusicPlaylist
import io.github.cluno1.sonorus.features.streaming.data.provider.PublicMusicPlaylistSearchPage

/** issue 167: public playlists found by name in the universal search, one page at a time. */
data class PublicPlaylistSearchState(
    val query: String = "",
    val items: List<PublicMusicPlaylist> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Invariants
 * - A page is applied only when it answers the current query and is exactly the next page. A slow
 *   answer for an old keyword, or a page that arrives twice, is dropped instead of being mixed in.
 * - Results are kept by `source + playlist ID`, so the same playlist on two pages is listed once and
 *   equal IDs from two sources stay apart.
 * - A failed page keeps what was already listed; only the "more" action is offered again.
 */
object PublicPlaylistSearch {
    fun start(query: String): PublicPlaylistSearchState =
        PublicPlaylistSearchState(query = query.trim(), loading = query.isNotBlank())

    fun nextPage(state: PublicPlaylistSearchState): Int? =
        if (state.query.isNotBlank() && !state.loading && (state.page == 0 || state.hasMore)) state.page + 1 else null

    fun loading(state: PublicPlaylistSearchState): PublicPlaylistSearchState = state.copy(loading = true, failed = false)

    fun applyPage(
        state: PublicPlaylistSearchState,
        query: String,
        result: PublicMusicPlaylistSearchPage,
    ): PublicPlaylistSearchState {
        if (query.trim() != state.query || result.page != state.page + 1) return state
        val merged = (state.items + result.items).distinctBy { it.sourceId to it.externalId }
        return state.copy(items = merged, page = result.page, hasMore = result.hasMore, loading = false, failed = false)
    }

    fun fail(state: PublicPlaylistSearchState, query: String): PublicPlaylistSearchState =
        if (query.trim() != state.query) state else state.copy(loading = false, failed = true)
}
