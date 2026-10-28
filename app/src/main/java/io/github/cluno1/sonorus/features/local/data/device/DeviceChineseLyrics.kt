/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.data.device

import android.content.Context
import android.util.Log
import io.github.cluno1.sonorus.features.catalog.di.CatalogModule
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsMatch
import io.github.cluno1.sonorus.shared.data.model.AppSettings
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * issue 127: "Chinese lyrics" (NetEase / QQ Music / Kugou through the backend's Meting provider)
 * as a public lyrics source on the same level as LRCLIB.
 *
 * Invariants
 * - Same rules as LRCLIB: candidates are scored by [DeviceMetadataMatcher.score], automatic use
 *   needs [DeviceMetadataMatcher.isAutomaticMatch] with the LRCLIB thresholds, and DEVICE songs
 *   stay behind the public-metadata privacy switch (checked by the caller).
 * - Automatic matching tries one source at a time ([automaticOrder]); LRCLIB and Chinese
 *   candidates are never mixed before the ambiguity check.
 * - Lyrics only. Meting has no year / track / genre, so no metadata or artwork search is offered.
 *
 * What breaks silently if changed
 * - The same song exists on NetEase, QQ and Kugou with near-equal scores. Without [collapseSameSong]
 *   the 0.03 ambiguity margin rejects every automatic match (local songs rarely carry an album, so
 *   the 0.98 unconditional threshold is unreachable). Manual search keeps all platforms on purpose.
 * - Mixing sources before the ambiguity check would make LRCLIB and Chinese copies of one song
 *   block each other.
 *
 * Rejected
 * - Calling the platforms from the device: the backend keeps their endpoint churn and caching.
 */
internal object DeviceChineseLyrics {
    private const val TAG = "DeviceChineseLyrics"
    private const val MAX_CANDIDATES = 12
    private const val SAME_SONG_SECONDS = 3.0
    private val cjk = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]")
    private val noise = Regex("[\\p{P}\\p{S}\\s]+")

    /** Source tag stored on [LyricsData.source]; read by the lyrics source label. */
    const val SOURCE = "CNLYRICS"
    const val DEVICE_SOURCE = "DEVICE_CNLYRICS"
    const val DEVICE_SELECTED_SOURCE = "DEVICE_CNLYRICS_SELECTED"

    fun isEnabled(context: Context): Boolean = AppSettings.getInstance(context).chineseLyricsApiEnabled.value

    fun prefersChinese(title: String?, artist: String?): Boolean =
        cjk.containsMatchIn(title.orEmpty()) || cjk.containsMatchIn(artist.orEmpty())

    /** Chinese titles or artists ask the Chinese source first; everything else keeps LRCLIB first. */
    fun automaticOrder(chineseEnabled: Boolean, title: String?, artist: String?): List<DevicePublicMetadataProvider> {
        if (!chineseEnabled) return listOf(DevicePublicMetadataProvider.LRCLIB)
        return if (prefersChinese(title, artist)) {
            listOf(DevicePublicMetadataProvider.CHINESE_LYRICS, DevicePublicMetadataProvider.LRCLIB)
        } else {
            listOf(DevicePublicMetadataProvider.LRCLIB, DevicePublicMetadataProvider.CHINESE_LYRICS)
        }
    }

    suspend fun search(
        context: Context,
        request: DeviceMetadataRequest,
        input: DeviceMatchInput,
    ): DeviceProviderSearchResult<DeviceLyricsCandidate> {
        val provider = DevicePublicMetadataProvider.CHINESE_LYRICS
        val result = CatalogModule.repository(context)
            .matchExternalLyrics(request.title, request.artist, request.album, request.durationSeconds)
        return result.fold(
            onSuccess = { matches -> DeviceProviderSearchResult(provider, candidates(input, matches)) },
            onFailure = { error ->
                // Expected when the library is not enrolled or offline; LRCLIB still answers.
                Log.w(TAG, "Chinese lyrics match unavailable: ${error.javaClass.simpleName}")
                DeviceProviderSearchResult(provider, emptyList(), failed = true)
            },
        )
    }

    fun candidates(input: DeviceMatchInput, matches: List<CatalogExternalLyricsMatch>): List<DeviceLyricsCandidate> =
        matches.map { match ->
            val artist = match.artists.joinToString("、")
            val seconds = match.durationMs?.div(1000.0)
            DeviceLyricsCandidate(
                externalId = "${match.source.wireValue}:${match.externalId}",
                title = match.title,
                artist = artist,
                album = match.album.orEmpty(),
                durationSeconds = seconds,
                confidence = DeviceMetadataMatcher.score(input, match.title, artist, match.album, seconds),
                lyrics = LyricsData(match.lyrics, match.syncedLrc, source = SOURCE),
                provider = DevicePublicMetadataProvider.CHINESE_LYRICS,
                platform = match.source.wireValue,
            )
        }.sortedByDescending(DeviceLyricsCandidate::confidence).take(MAX_CANDIDATES)

    /**
     * One entry per song for the automatic ambiguity check: same title and artist within
     * [SAME_SONG_SECONDS]. Keeps the best score, preferring synced lyrics on a tie.
     */
    fun collapseSameSong(candidates: List<DeviceLyricsCandidate>): List<DeviceLyricsCandidate> {
        val kept = mutableListOf<DeviceLyricsCandidate>()
        val ordered = candidates.sortedWith(
            compareByDescending<DeviceLyricsCandidate> { it.confidence }
                .thenByDescending { !it.lyrics.syncedLyrics.isNullOrBlank() },
        )
        for (candidate in ordered) {
            if (kept.none { sameSong(it, candidate) }) kept += candidate
        }
        return kept
    }

    /** The automatically usable candidate, or null when nothing is confident and unambiguous. */
    fun automaticPick(candidates: List<DeviceLyricsCandidate>): DeviceLyricsCandidate? {
        val collapsed = collapseSameSong(candidates)
        return collapsed.firstOrNull()?.takeIf {
            DeviceMetadataMatcher.isAutomaticMatch(
                it.confidence,
                collapsed.getOrNull(1)?.confidence,
                DeviceMetadataRepository.MIN_AUTO_CONFIDENCE,
                unconditionalThreshold = DeviceMetadataRepository.EXACT_AUTO_CONFIDENCE,
            )
        }
    }

    /** "DEVICE_CNLYRICS|title|artist|87%|netease"; the label shows it as "Chinese lyrics · …". */
    fun deviceLabel(candidate: DeviceLyricsCandidate, userSelected: Boolean): String =
        label(if (userSelected) DEVICE_SELECTED_SOURCE else DEVICE_SOURCE, candidate)

    fun label(prefix: String, candidate: DeviceLyricsCandidate): String =
        listOf(
            prefix,
            candidate.title,
            candidate.artist,
            "${(candidate.confidence * 100).toInt()}%",
            candidate.platform.orEmpty(),
        ).joinToString("|")

    /**
     * Player lyrics chain (songs outside DEVICE, issue 127): the LRCLIB-style automatic match
     * without a duration, used before the other online sources for Chinese titles.
     */
    suspend fun fetchForPlayer(context: Context, title: String, artist: String): LyricsData? {
        if (title.isBlank()) return null
        val matches = CatalogModule.repository(context)
            .matchExternalLyrics(title, artist.takeIf(String::isNotBlank), null, null)
            .onFailure { Log.w(TAG, "Chinese lyrics match unavailable: ${it.javaClass.simpleName}") }
            .getOrNull() ?: return null
        val pick = automaticPick(candidates(DeviceMatchInput(title, artist, "", 0L), matches)) ?: return null
        return pick.lyrics.copy(source = label(SOURCE, pick))
    }

    private fun sameSong(left: DeviceLyricsCandidate, right: DeviceLyricsCandidate): Boolean {
        if (key(left.title) != key(right.title) || key(left.artist) != key(right.artist)) return false
        val a = left.durationSeconds
        val b = right.durationSeconds
        return a == null || b == null || abs(a - b) <= SAME_SONG_SECONDS
    }

    private fun key(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(noise, "")
}
