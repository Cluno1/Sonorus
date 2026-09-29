/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.streaming.domain.model

import android.net.Uri
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import io.github.cluno1.sonorus.shared.data.model.Song

/** The gateway's current values, without revision or contribution records. */
data class LanSongMetadata(
    val trackId: String,
    val fields: Map<String, String>,
    val artworkUrl: String?,
    val plainLyrics: String,
    val syncedLyrics: String,
    val lyricsSource: String,
    val lyricsExternalId: String,
) {
    fun lyrics(): LyricsData? = LyricsData(
        plainLyrics.takeIf(String::isNotBlank),
        syncedLyrics.takeIf(String::isNotBlank),
        source = lyricsSource.ifBlank { "LAN gateway" },
    ).takeIf(LyricsData::hasLyrics)

    fun applyTo(song: Song): Song = song.copy(
        title = fields["title"] ?: song.title,
        artist = fields["artist"] ?: song.artist,
        album = fields["album"] ?: song.album,
        albumArtist = fields["albumArtist"]?.takeIf(String::isNotBlank),
        trackNumber = fields["trackNumber"]?.toIntOrNull() ?: song.trackNumber,
        discNumber = fields["discNumber"]?.toIntOrNull() ?: song.discNumber,
        year = fields["year"]?.toIntOrNull() ?: song.year,
        genre = fields["genre"]?.takeIf(String::isNotBlank),
        artworkUri = artworkUrl?.let(Uri::parse),
    )

    fun applyTo(song: StreamingSong): StreamingSong = song.copy(
        title = fields["title"] ?: song.title,
        artist = fields["artist"] ?: song.artist,
        album = fields["album"] ?: song.album,
        albumArtist = fields["albumArtist"]?.takeIf(String::isNotBlank),
        trackNumber = fields["trackNumber"]?.toIntOrNull(),
        discNumber = fields["discNumber"]?.toIntOrNull(),
        year = fields["year"]?.toIntOrNull(),
        genre = fields["genre"]?.takeIf(String::isNotBlank),
        artworkUri = artworkUrl,
    )

    companion object {
        val stringFields = listOf("title", "artist", "album", "albumArtist", "composer", "genre", "language", "version")
        val numberFields = listOf("year", "trackNumber", "discNumber")
        val editableFields = stringFields + numberFields
    }
}
