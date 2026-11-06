/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.data.device

import io.github.cluno1.sonorus.shared.data.model.LyricsData

/**
 * issue 166: which adopted lyrics go to the backend, and in which form.
 *
 * Invariants
 * - Only lyrics that were adopted are shared: an automatic match that passed the same thresholds as
 *   before, or a candidate the user pressed "use" on. Search candidates that were only previewed, and
 *   lyrics read from the audio file or a sibling file, are never uploaded.
 * - Local songs are shared by title + artist (+ duration, checked by the backend). A song without a
 *   real artist is never shared, otherwise untagged files would swap lyrics by title alone.
 * - Everything stays behind the "public metadata" privacy switch, like the LRCLIB lookup itself.
 *
 * Accepted residual
 * - The read order puts this device's cache before the backend (the user's chosen order), so a phone
 *   that already cached lyrics keeps them after another user picks different ones, until the user
 *   searches again or clears the lyrics cache.
 */
internal object SharedLyricsPolicy {
    const val SHARED_SOURCE = "DEVICE_SHARED"
    private val syncedLine = Regex("\\[\\d{1,3}:\\d{2}")
    private val unknownTags = setOf("unknown", "<unknown>", "unknown artist")

    /** The text stored on the backend: synced lyrics when present, otherwise plain lyrics. */
    fun lrc(lyrics: LyricsData): String? =
        lyrics.syncedLyrics?.takeIf(String::isNotBlank) ?: lyrics.plainLyrics?.takeIf(String::isNotBlank)

    /** Lyrics read back from the backend, shown with the "shared lyrics" source label. */
    fun lyricsData(text: String): LyricsData =
        if (syncedLine.containsMatchIn(text)) {
            LyricsData(plainLyrics = null, syncedLyrics = text, source = SHARED_SOURCE)
        } else {
            LyricsData(plainLyrics = text, syncedLyrics = null, source = SHARED_SOURCE)
        }

    /** `LRCLIB`, or `CHINESE_LYRICS:netease` for one platform of the Chinese lyrics source. */
    fun provider(candidate: DeviceLyricsCandidate): String =
        listOfNotNull(candidate.provider.name, candidate.platform?.takeIf(String::isNotBlank))
            .joinToString(":")
            .take(40)

    /** The artist to share under, or null when the song has no real artist tag. */
    fun shareableArtist(artist: String?, placeholders: Set<String>): String? {
        val value = artist?.trim().orEmpty()
        if (value.isEmpty() || value in placeholders || value.lowercase() in unknownTags) return null
        if (value.lowercase().startsWith("unknown ")) return null
        return value
    }
}
