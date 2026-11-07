package io.github.cluno1.sonorus.features.local.presentation.viewmodel

/**
 * issue 176: what a public song does when its lyrics cannot be read from or saved to the backend.
 *
 * Invariant: lyrics the user kept "on this phone only" win over the shared lyrics until a later
 * save to the backend succeeds, which clears the local copy so everyone sees the same lyrics again.
 * Why: a failed backend write used to drop the user's pick silently (HTTP 422 on the metadata write).
 * Silent failure if broken: the shared lyrics replace the local pick after the next sync, or an
 * outage leaves the song without lyrics although LRCLIB / Chinese lyrics have a match.
 * Accepted residual: the local copy lives only on this phone and is not shared; it is cleared by a
 * later successful backend save, or by the user clearing the song's lyrics cache.
 */
internal object PublicLyricsFallbackPolicy {
    /** Lyrics shown for a public song: a local pick first, then the shared lyrics. */
    fun <T> lyricsToShow(local: T?, shared: T?): T? = local ?: shared

    /** Try the automatic LRCLIB / Chinese match when nothing local exists and the backend has none. */
    fun shouldTryFallback(hasLocal: Boolean, lyricsMissing: Boolean, loadFailed: Boolean): Boolean =
        !hasLocal && (lyricsMissing || loadFailed)

    /** A failed manual pick on a public song offers "this phone only" / retry instead of a bare error. */
    fun offerLocalSave(isPublicSong: Boolean): Boolean = isPublicSong
}
