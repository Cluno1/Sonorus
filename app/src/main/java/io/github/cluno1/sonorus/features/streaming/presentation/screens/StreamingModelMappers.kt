/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.streaming.presentation.screens

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.core.ProductCapabilities
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy
import io.github.cluno1.sonorus.features.streaming.domain.model.StreamingAlbum
import io.github.cluno1.sonorus.features.streaming.domain.model.StreamingArtist
import io.github.cluno1.sonorus.features.streaming.domain.model.StreamingPlaylist
import io.github.cluno1.sonorus.features.streaming.domain.model.StreamingSong
import io.github.cluno1.sonorus.shared.data.model.Album
import io.github.cluno1.sonorus.shared.data.model.Artist
import io.github.cluno1.sonorus.shared.data.model.Playlist
import io.github.cluno1.sonorus.shared.data.model.Song

/**
 * Streaming → shared local-model mappers.
 *
 * These let the local Home/Library screens render streaming (Go-mode) content
 * through the exact same UI used for local content: streaming data is converted
 * to local model types and fed into the existing screens.
 */

fun StreamingSong.toLibrarySong(): Song {
    val playbackUri = when {
        ProductCapabilities.lanSubsonicOnly ->
            LanSubsonicPlaybackPolicy.playbackUri(id).toUri()
        !streamingUrl.isNullOrBlank() -> (streamingUrl).toUri()
        !previewUrl.isNullOrBlank() -> (previewUrl).toUri()
        else -> ("streaming://track/$id").toUri()
    }

    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId.orEmpty().takeIf { it.isNotBlank() } ?: "${sourceType.name}:${artist.lowercase()}:${album.lowercase()}",
        duration = duration,
        uri = playbackUri,
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        albumArtist = albumArtist,
        trackNumber = trackNumber ?: 0,
        year = year ?: 0,
        genre = genre,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        codec = codec
    )
}

fun StreamingPlaylist.toLibraryPlaylist(context: Context): Playlist {
    val loadedTracks = getTracks()
    val displaySongs = if (loadedTracks.isNotEmpty()) {
        loadedTracks.map { it.toLibrarySong() }
    } else if (songCount > 0) {
        // Generate placeholder songs so the count displays correctly in the UI
        (1..songCount).map { i ->
            Song(
                id = "${id}_placeholder_$i",
                title = context.getString(R.string.streaming_placeholder_track_format, i),
                artist = "",
                album = name,
                duration = 0L,
                uri = ("streaming://playlist/$id/track/$i").toUri()
            )
        }
    } else {
        emptyList()
    }
    return Playlist(
        id = id,
        name = name,
        songs = displaySongs,
        dateCreated = externalId?.hashCode()?.toLong() ?: id.hashCode().toLong(),
        dateModified = snapshotId?.hashCode()?.toLong() ?: songCount.toLong(),
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    )
}

fun StreamingAlbum.toLibraryAlbum(librarySongs: List<Song>): Album {
    val streamingTracks = tracks
    val matchingSongs = if (streamingTracks.isNotEmpty()) {
        streamingTracks.map { it.toLibrarySong() }
    } else if (librarySongs.isNotEmpty()) {
        librarySongs.filter {
            it.album.equals(title, ignoreCase = true) &&
                it.artist.equals(artist, ignoreCase = true)
        }
    } else {
        emptyList()
    }

    val displayedSongCount = when {
        songCount > 0 -> songCount
        streamingTracks.isNotEmpty() -> streamingTracks.size
        else -> matchingSongs.size
    }

    return Album(
        id = id,
        title = title,
        artist = artist,
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        year = year ?: 0,
        songs = matchingSongs,
        numberOfSongs = displayedSongCount
    )
}

fun StreamingArtist.toLibraryArtist(
    librarySongs: List<Song>,
    libraryAlbums: List<Album>,
    separatorEnabled: Boolean,
    separatorDelimiters: String
): Artist {
    val matchingSongs = if (librarySongs.isNotEmpty()) {
        librarySongs.filter { song ->
            song.artist.equals(name, ignoreCase = true) ||
                io.github.cluno1.sonorus.util.ArtistSeparator.splitArtistNames(
                    song.artist,
                    delimiters = separatorDelimiters,
                    enabled = separatorEnabled
                ).any { splitName -> splitName.equals(name, ignoreCase = true) }
        }
    } else {
        getTopTracks().map { it.toLibrarySong() }
    }

    val matchingAlbums = if (libraryAlbums.isNotEmpty()) {
        libraryAlbums.filter { it.artist.equals(name, ignoreCase = true) }
    } else {
        getAlbumsList().map { it.toLibraryAlbum(matchingSongs) }
    }

    return Artist(
        id = id,
        name = name,
        artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse),
        albums = matchingAlbums,
        songs = matchingSongs,
        numberOfAlbums = if (albumCount > 0) albumCount else matchingAlbums.size,
        numberOfTracks = if (songCount > 0) songCount else matchingSongs.size
    )
}
