package io.github.cluno1.sonorus.features.catalog.presentation

import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchSong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogWorkNumber

private const val IHOPE_NUMBER_KEY = "ihope_num"
private const val HEAVEN_SONG_NUMBER_KEY = "heavenSong_num"
private val lrcTimestampPrefix = Regex("""^\s*(?:\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?])+\s*""")

/** Display name of a hymnal number key, e.g. `ihope_num` -> `ihope`, `heavenSong_num` -> `Heaven Song`. */
fun workNumberKeyLabel(key: String): String = when (key) {
    IHOPE_NUMBER_KEY -> "ihope"
    HEAVEN_SONG_NUMBER_KEY -> "Heaven Song"
    else -> key.removeSuffix("_num").ifBlank { key }
}

fun CatalogWorkNumber.displayLabel(): String = "${workNumberKeyLabel(key)} $value"

/** Same order as the server: ihope, Heaven Song, then any other key (stable). */
fun orderedWorkNumbers(numbers: List<CatalogWorkNumber>): List<CatalogWorkNumber> =
    numbers.sortedBy {
        when (it.key) {
            IHOPE_NUMBER_KEY -> 0
            HEAVEN_SONG_NUMBER_KEY -> 1
            else -> 2
        }
    }

/** e.g. `ihope 256 · Heaven Song 352`; empty when the Work has no numbers. */
fun formatWorkNumbers(numbers: List<CatalogWorkNumber>): String =
    orderedWorkNumbers(numbers).joinToString(" · ") { it.displayLabel() }

/** The number search only runs for queries that are digits once whitespace is removed. */
fun isWorkNumberQuery(query: String): Boolean {
    val compact = query.filterNot(Char::isWhitespace)
    return compact.isNotEmpty() && compact.all { it in '0'..'9' }
}

/** Work lyrics are shown as plain text; drop LRC line timestamps if an editor left them in. */
fun workLyricsDisplayText(lyrics: String): String =
    lyrics.lineSequence()
        .map { it.replace(lrcTimestampPrefix, "") }
        .joinToString("\n")
        .trim()

/**
 * Navigation argument for the playable songs of a Work: `renditionId:albumId` pairs joined by
 * commas. Only UUIDs are expected, so neither separator can appear inside a value. An empty list
 * is encoded as [NO_PLAYABLE_WORK_SONGS] so "known to have no song" survives navigation (an absent
 * argument means "unknown", and the caller then looks the songs up in the local library).
 */
fun encodeWorkLyricsSongs(songs: List<CatalogLibrarySearchSong>): String =
    songs.joinToString(",") { "${it.renditionId}:${it.albumId}" }.ifEmpty { NO_PLAYABLE_WORK_SONGS }

const val NO_PLAYABLE_WORK_SONGS = "-"

fun decodeWorkLyricsSongs(value: String?, title: String): List<CatalogLibrarySearchSong> =
    value.orEmpty()
        .split(',')
        .mapNotNull { pair ->
            val renditionId = pair.substringBefore(':', "").trim()
            val albumId = pair.substringAfter(':', "").trim()
            if (renditionId.isEmpty() || albumId.isEmpty() || albumId.contains(':')) {
                null
            } else {
                CatalogLibrarySearchSong(renditionId, albumId, title, trackNo = null)
            }
        }
        .distinctBy { it.renditionId }
