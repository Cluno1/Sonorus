package io.github.cluno1.sonorus.shared.presentation.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * issue 177: one visit to the search page, kept while a result (playlist, album, lyrics, …) is open
 * on top of it.
 *
 * Invariant: opening a result and coming back shows the same results without asking the server
 * again. The typed text, submitted search, filters and open "view all" page live in
 * `rememberSaveable`; the server answers live here, scoped to the search page's back stack entry,
 * so they are dropped when the search page itself is left.
 *
 * Why: the search destination leaves composition when another page is pushed. Plain `remember`
 * state was lost, so back landed on recent searches; keeping only the text would refetch every
 * group on return (loading flash, public playlist pages lost, scroll position lost).
 *
 * Silent failure if changed: an entry left in Loading means its request was cancelled when the page
 * left composition, so it must run again — reusing it would show a spinner forever.
 */
internal class UniversalSearchSessionViewModel : ViewModel() {
    var lyricsState by mutableStateOf<UniversalCatalogTextSearchState>(UniversalCatalogTextSearchState.Idle)
    var numberState by mutableStateOf<UniversalCatalogTextSearchState>(UniversalCatalogTextSearchState.Idle)
    var tagState by mutableStateOf<UniversalCatalogTextSearchState>(UniversalCatalogTextSearchState.Idle)
    var scoreState by mutableStateOf<UniversalCatalogTextSearchState>(UniversalCatalogTextSearchState.Idle)
    var externalLyricsState by mutableStateOf<UniversalExternalLyricsSearchState>(UniversalExternalLyricsSearchState.Idle)

    /** The request each state above answers; null when the state was not produced by a request. */
    var lyricsKey: String? = null
    var numberKey: String? = null
    var tagKey: String? = null
    var scoreKey: String? = null
    var externalLyricsKey: String? = null

    /** The last public music search this page sent to the shared streaming view model. */
    var streamingKey: String? = null
}

/**
 * issue 177: a request already answered on this search page is reused. [answerValid] is false while
 * the stored answer is still Loading (its request was cancelled) or was replaced by another page.
 */
internal fun shouldReuseUniversalSearchAnswer(answeredKey: String?, key: String, answerValid: Boolean): Boolean =
    answeredKey == key && answerValid

internal enum class UniversalSearchBackStep { CLOSE_ALL_RESULTS, CLEAR_SEARCH, LEAVE }

/** issue 177: back steps out one layer at a time: "view all" → results → recent searches → leave. */
internal fun universalSearchBackStep(allResultsOpen: Boolean, query: String, showFilters: Boolean): UniversalSearchBackStep =
    when {
        allResultsOpen -> UniversalSearchBackStep.CLOSE_ALL_RESULTS
        query.isNotEmpty() || showFilters -> UniversalSearchBackStep.CLEAR_SEARCH
        else -> UniversalSearchBackStep.LEAVE
    }

/** issue 177: the keyboard opens only on a fresh search page, not when returning to results. */
internal fun shouldFocusUniversalSearchOnOpen(showKeyboardOnSearchOpen: Boolean, query: String): Boolean =
    showKeyboardOnSearchOpen && query.isEmpty()
