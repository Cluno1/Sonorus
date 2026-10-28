/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.cluno1.sonorus.features.local.data.device

enum class DeviceManualMetadataKind { LYRICS, ARTWORK, ARTIST_ARTWORK, DETAILS }

/** CHINESE_LYRICS (issue 127): NetEase / QQ Music / Kugou lyrics via the backend, lyrics only. */
enum class DevicePublicMetadataProvider { LRCLIB, MUSICBRAINZ_CAA, DEEZER, ITUNES, WIKIPEDIA, CHINESE_LYRICS }

/** Where a manually selected cover should be materialized. */
enum class DeviceArtworkSaveTarget { APP_ONLY, MUSIC_FOLDER }

enum class DeviceDetailsField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    YEAR,
    TRACK_NUMBER,
    DISC_NUMBER,
    GENRE,
}

data class DeviceProviderSearchResult<T>(
    val provider: DevicePublicMetadataProvider,
    val candidates: List<T>,
    val failed: Boolean = false,
)

data class DeviceArtworkCandidate(
    val provider: DevicePublicMetadataProvider,
    val externalId: String,
    val releaseGroupId: String? = null,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double?,
    val confidence: Double,
    val imageUrl: String,
)

data class DeviceArtistArtworkCandidate(
    val provider: DevicePublicMetadataProvider,
    val externalId: String,
    val artistName: String,
    val imageUrl: String,
    val albumCount: Int,
    val fanCount: Int,
    val confidence: Double,
)

data class DeviceDetailsCandidate(
    val provider: DevicePublicMetadataProvider,
    val externalId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String? = null,
    val durationSeconds: Double? = null,
    val releaseDate: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val trackCount: Int? = null,
    val genre: String? = null,
    val albumType: String? = null,
    val country: String? = null,
    val label: String? = null,
    val artistAlbumCount: Int? = null,
    val artistFanCount: Int? = null,
    val artworkUrl: String? = null,
    val confidence: Double,
) {
    val year: Int?
        get() = releaseDate?.take(4)?.toIntOrNull()?.takeIf { it in 1000..2999 }
}

enum class DeviceEditorialSubject { ALBUM, ARTIST }

data class DeviceEditorialCandidate(
    val provider: DevicePublicMetadataProvider,
    val externalId: String,
    val subject: DeviceEditorialSubject,
    val title: String,
    val description: String,
)

fun DeviceMetadataRequest.normalized(): DeviceMetadataRequest = copy(
    title = title.trim(),
    artist = artist?.trim()?.takeIf(String::isNotEmpty),
    album = album?.trim()?.takeIf(String::isNotEmpty),
    durationSeconds = durationSeconds?.takeIf { it > 0 },
)
