/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.local.data.database.entity

import android.net.Uri
import androidx.room.Entity
import io.github.cluno1.sonorus.features.local.data.model.UnifiedPlaylistPolicy
import io.github.cluno1.sonorus.features.local.data.model.UnifiedPlaylistSource
import io.github.cluno1.sonorus.shared.data.model.Song

/**
 * Playlist-owned display and deferred-playback snapshot.
 *
 * Catalog and LAN rows intentionally live outside the device `songs` table so a media rescan
 * cannot erase them or make them appear as device files.
 */
@Entity(
    tableName = "playlist_song_snapshots",
    primaryKeys = ["playlistId", "songId"],
)
data class PlaylistSongSnapshotEntity(
    val playlistId: String,
    val songId: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val duration: Long,
    val uri: String,
    val artworkUri: String?,
    val trackNumber: Int,
    val year: Int,
    val genre: String?,
    val dateAdded: Long,
    val dateModified: Long,
    val albumArtist: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val codec: String?,
    val discNumber: Int,
    val path: String?,
)

fun Song.toPlaylistSnapshotEntity(playlistId: String): PlaylistSongSnapshotEntity {
    val stableSong = toUnifiedPlaylistSong()
    val source = UnifiedPlaylistPolicy.source(stableSong.id)
    return PlaylistSongSnapshotEntity(
        playlistId = playlistId,
        songId = stableSong.id,
        source = source.name,
        title = stableSong.title,
        artist = stableSong.artist,
        album = stableSong.album,
        albumId = stableSong.albumId,
        duration = stableSong.duration,
        uri = stableSong.uri.toString(),
        artworkUri = stableSong.artworkUri?.toString(),
        trackNumber = stableSong.trackNumber,
        year = stableSong.year,
        genre = stableSong.genre,
        dateAdded = stableSong.dateAdded,
        dateModified = stableSong.dateModified,
        albumArtist = stableSong.albumArtist,
        bitrate = stableSong.bitrate,
        sampleRate = stableSong.sampleRate,
        channels = stableSong.channels,
        codec = stableSong.codec,
        discNumber = stableSong.discNumber,
        path = stableSong.path,
    )
}

fun Song.toUnifiedPlaylistSong(): Song {
    val stableId = UnifiedPlaylistPolicy.canonicalMediaId(id)
    return when (UnifiedPlaylistPolicy.source(stableId)) {
        UnifiedPlaylistSource.CATALOG,
        UnifiedPlaylistSource.LAN_SUBSONIC,
        -> copy(
            id = stableId,
            uri = Uri.parse(UnifiedPlaylistPolicy.canonicalUri(stableId, uri.toString())),
            artworkUri = UnifiedPlaylistPolicy.canonicalArtworkUri(
                stableId,
                artworkUri?.toString(),
            )?.let(Uri::parse),
            path = null,
        )
        UnifiedPlaylistSource.DEVICE_OR_LEGACY ->
            if (stableId == id) this else copy(id = stableId)
    }
}

fun PlaylistSongSnapshotEntity.toSong(): Song = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    duration = duration,
    uri = Uri.parse(uri),
    artworkUri = artworkUri?.let(Uri::parse),
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    dateAdded = dateAdded,
    dateModified = dateModified,
    albumArtist = albumArtist,
    bitrate = bitrate,
    sampleRate = sampleRate,
    channels = channels,
    codec = codec,
    discNumber = discNumber,
    path = path,
).toUnifiedPlaylistSong()
