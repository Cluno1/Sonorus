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
    val overriddenFields: Set<String>,
) {
    fun lyrics(): LyricsData? = LyricsData(
        plainLyrics.takeIf(String::isNotBlank),
        syncedLyrics.takeIf(String::isNotBlank),
        source = lyricsSource.ifBlank { "LAN gateway" },
    ).takeIf(LyricsData::hasLyrics)

    fun applyTo(song: Song): Song = song.copy(
        title = if ("title" in overriddenFields) fields["title"] ?: song.title else song.title,
        artist = if ("artist" in overriddenFields) fields["artist"] ?: song.artist else song.artist,
        album = if ("album" in overriddenFields) fields["album"] ?: song.album else song.album,
        albumArtist = if ("albumArtist" in overriddenFields) fields["albumArtist"]?.takeIf(String::isNotBlank) else song.albumArtist,
        trackNumber = if ("trackNumber" in overriddenFields) fields["trackNumber"]?.toIntOrNull() ?: song.trackNumber else song.trackNumber,
        discNumber = if ("discNumber" in overriddenFields) fields["discNumber"]?.toIntOrNull() ?: song.discNumber else song.discNumber,
        year = if ("year" in overriddenFields) fields["year"]?.toIntOrNull() ?: song.year else song.year,
        genre = if ("genre" in overriddenFields) fields["genre"]?.takeIf(String::isNotBlank) else song.genre,
        artworkUri = artworkUrl?.let(Uri::parse),
    )

    fun applyTo(song: StreamingSong): StreamingSong = song.copy(
        title = if ("title" in overriddenFields) fields["title"] ?: song.title else song.title,
        artist = if ("artist" in overriddenFields) fields["artist"] ?: song.artist else song.artist,
        album = if ("album" in overriddenFields) fields["album"] ?: song.album else song.album,
        albumArtist = if ("albumArtist" in overriddenFields) fields["albumArtist"]?.takeIf(String::isNotBlank) else song.albumArtist,
        trackNumber = if ("trackNumber" in overriddenFields) fields["trackNumber"]?.toIntOrNull() else song.trackNumber,
        discNumber = if ("discNumber" in overriddenFields) fields["discNumber"]?.toIntOrNull() else song.discNumber,
        year = if ("year" in overriddenFields) fields["year"]?.toIntOrNull() else song.year,
        genre = if ("genre" in overriddenFields) fields["genre"]?.takeIf(String::isNotBlank) else song.genre,
        artworkUri = artworkUrl,
    )

    companion object {
        val stringFields = listOf("title", "artist", "album", "albumArtist", "composer", "genre", "language", "version")
        val numberFields = listOf("year", "trackNumber", "discNumber")
        val editableFields = stringFields + numberFields
    }
}
