/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.cluno1.sonorus.features.local.data.device

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import io.github.cluno1.sonorus.features.local.data.database.RhythmDatabase
import io.github.cluno1.sonorus.features.local.data.database.entity.DeviceMetadataEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.DeviceAlbumMetadataEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.DeviceSongAlbumEntity
import io.github.cluno1.sonorus.features.local.data.database.entity.toEntity
import io.github.cluno1.sonorus.network.LrcLibLyrics
import io.github.cluno1.sonorus.network.DeezerArtist
import io.github.cluno1.sonorus.network.DeezerTrack
import io.github.cluno1.sonorus.network.MusicBrainzRecording
import io.github.cluno1.sonorus.network.MusicBrainzRelease
import io.github.cluno1.sonorus.network.NetworkClient
import io.github.cluno1.sonorus.network.WikipediaProvider
import io.github.cluno1.sonorus.shared.data.model.LyricsData
import io.github.cluno1.sonorus.shared.data.model.Song
import io.github.cluno1.sonorus.util.MediaUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class GatewayArtworkTooLargeException : java.io.IOException("Cover image exceeds the gateway limit")

data class DeviceLyricsCandidate(
    val externalId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double?,
    val confidence: Double,
    val lyrics: LyricsData
)

private data class MusicBrainzArtworkMatch(
    val recordingId: String,
    val title: String,
    val artist: String,
    val artistAliases: List<String>,
    val durationSeconds: Double?,
    val firstReleaseDate: String?,
    val release: MusicBrainzRelease,
    val confidence: Double,
    val relatedReleaseIds: List<String>
)

private data class CachedArtworkFile(
    val file: File,
    val sha256: String,
    val mediaType: String,
    val byteSize: Long
)

private data class SiblingArtwork(val file: File, val albumScoped: Boolean)

private data class ArtworkCandidates(val userSelected: Uri?, val cached: Uri?)

private fun <T> Result<T>.throwIfCancelled(): Result<T> = onFailure { error ->
    if (error is CancellationException) throw error
}

class DeviceMetadataRepository(private val context: Context, private val allowLanQueries: Boolean = false) {
    private val dao = RhythmDatabase.getInstance(context).deviceMetadataDao()
    private val albumDao = RhythmDatabase.getInstance(context).deviceAlbumMetadataDao()
    private val songDao = RhythmDatabase.getInstance(context).songDao()
    private val artworkValidator = ArtworkUriValidator(context)
    val folders = DeviceFolderAccess(context)

    fun stableId(song: Song): String = sha256("${song.uri}|${song.id}")
    fun fingerprint(song: Song): String = sha256(
        "${song.uri}|${song.duration}|${song.dateModified}|${sourceSize(song)}|${song.path.orEmpty()}"
    )

    suspend fun cachedLyrics(song: Song): LyricsData? {
        val row = dao.getBySongId(song.id) ?: return null
        if (row.fingerprint != fingerprint(song)) return null
        if (row.lyricsPlain.isNullOrBlank() && row.lyricsSynced.isNullOrBlank()) return null
        return LyricsData(row.lyricsPlain, row.lyricsSynced, source = row.lyricsSource ?: "Device cache")
    }

    suspend fun pinnedLyrics(song: Song): LyricsData? {
        val row = dao.getBySongId(song.id) ?: return null
        if (!row.lyricsPinned || row.fingerprint != fingerprint(song)) return null
        if (row.lyricsPlain.isNullOrBlank() && row.lyricsSynced.isNullOrBlank()) return null
        return LyricsData(row.lyricsPlain, row.lyricsSynced, source = row.lyricsSource)
    }

    suspend fun saveLyrics(song: Song, value: LyricsData, provider: String?, externalId: String?, confidence: Double?, cachePath: String? = null, pinned: Boolean = false) {
        val old = dao.getBySongId(song.id)
        if (
            old?.fingerprint == fingerprint(song) &&
            DeviceMetadataPolicy.shouldPreservePinnedSelection(old.lyricsPinned, pinned)
        ) {
            return
        }
        val resolvedCachePath = cachePath ?: provider?.let {
            metadataDir().resolve("lyrics-${stableId(song)}.json").also { file -> file.writeText(Gson().toJson(value)) }.absolutePath
        }
        dao.upsert(base(song, old).copy(
            lyricsSource = value.source,
            lyricsProvider = provider,
            lyricsExternalId = externalId,
            lyricsConfidence = confidence,
            lyricsPlain = value.plainLyrics,
            lyricsSynced = value.syncedLyrics,
            lyricsCachePath = resolvedCachePath,
            lyricsPinned = pinned,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun clearLyrics(song: Song) {
        dao.getBySongId(song.id)?.lyricsCachePath?.let { runCatching { File(it).delete() } }
        dao.clearLyrics(song.id)
    }

    suspend fun searchLyrics(
        song: Song,
        query: DeviceMetadataRequest = requestFor(song),
    ): List<DeviceLyricsCandidate> = searchLyricsResult(song, query).candidates

    suspend fun searchLyricsResult(
        song: Song,
        query: DeviceMetadataRequest,
    ): DeviceProviderSearchResult<DeviceLyricsCandidate> = withContext(Dispatchers.IO) {
        fun result(candidates: List<DeviceLyricsCandidate> = emptyList(), failed: Boolean = false) =
            DeviceProviderSearchResult(DevicePublicMetadataProvider.LRCLIB, candidates, failed)
        if (!song.isPublicQueryTarget()) return@withContext result()
        if (!NetworkClient.isDevicePublicMetadataEnabled()) return@withContext result()
        val service = NetworkClient.lrclibApiService ?: return@withContext result(failed = true)
        val request = query.normalized()
        if (request.title.isBlank()) return@withContext result()
        val input = DeviceMatchInput(
            request.title,
            request.artist.orEmpty(),
            request.album.orEmpty(),
            request.durationSeconds?.times(1000L) ?: 0L,
        )
        val preciseResult = runCatching {
            service.searchLyrics(
                trackName = request.title,
                artistName = request.artist,
                albumName = request.album,
                duration = request.durationSeconds
            )
        }.throwIfCancelled()
        val broadResult = runCatching {
            service.searchLyrics(query = "${request.artist.orEmpty()} ${request.title}".trim())
        }.throwIfCancelled()
        val precise = preciseResult.getOrDefault(emptyList())
        val broad = broadResult.getOrDefault(emptyList())
        val results = (precise + broad).distinctBy(LrcLibLyrics::id)
        val candidates = results.asSequence().filter(LrcLibLyrics::hasLyrics).map { item ->
            val confidence = DeviceMetadataMatcher.score(input, item.trackName ?: item.name, item.artistName, item.albumName, item.duration)
            DeviceLyricsCandidate(
                externalId = item.id.toString(), title = item.trackName ?: item.name.orEmpty(),
                artist = item.artistName.orEmpty(), album = item.albumName.orEmpty(),
                durationSeconds = item.duration, confidence = confidence,
                lyrics = LyricsData(item.plainLyrics, item.syncedLyrics, source = "LRCLIB")
            )
        }.sortedByDescending(DeviceLyricsCandidate::confidence).take(12).toList()
        result(candidates, failed = preciseResult.isFailure && broadResult.isFailure)
    }

    suspend fun applyLyrics(song: Song, candidate: DeviceLyricsCandidate, userSelected: Boolean = false): LyricsData {
        val prefix = if (userSelected) "DEVICE_LRCLIB_SELECTED" else "DEVICE_LRCLIB"
        val labelled = candidate.lyrics.copy(source = "$prefix|${candidate.title}|${candidate.artist}|${(candidate.confidence * 100).toInt()}%")
        saveLyrics(song, labelled, "LRCLIB", candidate.externalId, candidate.confidence, pinned = userSelected)
        return labelled
    }

    suspend fun searchArtwork(
        song: Song,
        query: DeviceMetadataRequest,
        providers: Set<DevicePublicMetadataProvider>,
    ): List<DeviceArtworkCandidate> = withContext(Dispatchers.IO) {
        if (!song.isDeviceSong()) return@withContext emptyList()
        if (!NetworkClient.isDevicePublicMetadataEnabled()) return@withContext emptyList()
        val request = query.normalized()
        if (request.title.isBlank()) return@withContext emptyList()
        providers.flatMap { provider ->
            searchArtworkResult(song, request, provider).candidates
        }.distinctBy { it.provider to it.externalId }
            .sortedByDescending(DeviceArtworkCandidate::confidence)
            .take(MAX_MANUAL_RESULTS)
    }

    suspend fun searchArtworkResult(
        song: Song,
        query: DeviceMetadataRequest,
        provider: DevicePublicMetadataProvider,
    ): DeviceProviderSearchResult<DeviceArtworkCandidate> = withContext(Dispatchers.IO) {
        fun result(candidates: List<DeviceArtworkCandidate> = emptyList(), failed: Boolean = false) =
            DeviceProviderSearchResult(provider, candidates, failed)
        if (!song.isPublicQueryTarget()) return@withContext result()
        if (!NetworkClient.isDevicePublicMetadataEnabled()) return@withContext result()
        val request = query.normalized()
        if (request.title.isBlank()) return@withContext result()
        val querySong = song.withMetadataRequest(request)
        runCatching {
            when (provider) {
                DevicePublicMetadataProvider.MUSICBRAINZ_CAA -> {
                    searchMusicBrainzCandidates(querySong, throwOnFailure = true)
                        .take(MAX_MANUAL_RESULTS).mapNotNull { match ->
                            val url = "https://coverartarchive.org/release/${match.release.id}/front-500"
                            DeviceMetadataPolicy.safeCoverArtUrl(url)?.let { safeUrl ->
                                DeviceArtworkCandidate(
                                    provider = DevicePublicMetadataProvider.MUSICBRAINZ_CAA,
                                    externalId = match.release.id,
                                    releaseGroupId = match.release.releaseGroup?.id,
                                    title = match.title,
                                    artist = match.artist,
                                    album = match.release.title,
                                    durationSeconds = match.durationSeconds,
                                    confidence = match.confidence,
                                    imageUrl = safeUrl,
                                )
                                }
                            }
                        }
                DevicePublicMetadataProvider.DEEZER -> {
                    rankDeezerArtwork(querySong, null, throwOnAllFailure = true)
                        .take(MAX_MANUAL_RESULTS).mapNotNull { (track, confidence) ->
                            val album = track.album ?: return@mapNotNull null
                            val url = album.coverXl ?: album.coverBig ?: album.coverMedium ?: album.cover
                            val safeUrl = url?.let(DeviceMetadataPolicy::safeDeezerArtworkUrl)
                                ?: return@mapNotNull null
                            DeviceArtworkCandidate(
                                provider = DevicePublicMetadataProvider.DEEZER,
                                externalId = track.id.toString(),
                                title = track.title,
                                artist = track.artist?.name.orEmpty(),
                                album = album.title,
                                durationSeconds = track.duration?.toDouble(),
                                confidence = confidence,
                                imageUrl = safeUrl,
                            )
                        }
                    }
                DevicePublicMetadataProvider.ITUNES -> {
                    val service = NetworkClient.itunesSearchApiService
                        ?: error("iTunes Search service unavailable")
                    val input = DeviceMatchInput(
                        querySong.title,
                        querySong.artist,
                        querySong.album,
                        querySong.duration,
                    )
                    service.searchSongs(
                        "${querySong.artist} ${querySong.title}".trim(),
                        limit = MAX_MANUAL_RESULTS * 2,
                    ).results.asSequence().mapNotNull { track ->
                        val rawUrl = track.artworkUrl100?.replace(
                            Regex("/\\d+x\\d+bb\\."),
                            "/600x600bb.",
                        ) ?: return@mapNotNull null
                        val safeUrl = DeviceMetadataPolicy.safeItunesArtworkUrl(rawUrl)
                            ?: return@mapNotNull null
                        DeviceArtworkCandidate(
                            provider = DevicePublicMetadataProvider.ITUNES,
                            externalId = track.trackId.toString(),
                            title = track.trackName.orEmpty(),
                            artist = track.artistName.orEmpty(),
                            album = track.collectionName.orEmpty(),
                            durationSeconds = track.trackTimeMillis?.div(1000.0),
                            confidence = DeviceMetadataMatcher.score(
                                input,
                                track.trackName,
                                track.artistName,
                                track.collectionName,
                                track.trackTimeMillis?.div(1000.0),
                            ),
                            imageUrl = safeUrl,
                        )
                    }.sortedByDescending(DeviceArtworkCandidate::confidence)
                        .distinctBy(DeviceArtworkCandidate::externalId)
                        .take(MAX_MANUAL_RESULTS)
                        .toList()
                }
                DevicePublicMetadataProvider.LRCLIB,
                DevicePublicMetadataProvider.WIKIPEDIA -> emptyList()
            }
        }.throwIfCancelled().fold(
            onSuccess = { result(it) },
            onFailure = { result(failed = true) },
        )
    }

    suspend fun searchArtistArtworkResult(
        artistName: String,
    ): DeviceProviderSearchResult<DeviceArtistArtworkCandidate> = withContext(Dispatchers.IO) {
        fun result(
            candidates: List<DeviceArtistArtworkCandidate> = emptyList(),
            failed: Boolean = false,
        ) = DeviceProviderSearchResult(DevicePublicMetadataProvider.DEEZER, candidates, failed)

        if (!NetworkClient.isDevicePublicMetadataEnabled()) return@withContext result()
        val query = artistName.trim()
        if (query.isBlank()) return@withContext result()
        val service = NetworkClient.deezerApiService ?: return@withContext result(failed = true)
        runCatching { service.searchArtists(query, MAX_MANUAL_RESULTS * 2).data }
            .throwIfCancelled()
            .fold(
                onSuccess = { artists ->
                    result(
                        artists.asSequence()
                            .mapNotNull { artist -> artist.toArtworkCandidate(query) }
                            .distinctBy(DeviceArtistArtworkCandidate::externalId)
                            .sortedWith(
                                compareByDescending<DeviceArtistArtworkCandidate> { it.confidence }
                                    .thenByDescending { it.fanCount },
                            )
                            .take(MAX_MANUAL_RESULTS)
                            .toList(),
                    )
                },
                onFailure = { result(failed = true) },
            )
    }

    private fun DeezerArtist.toArtworkCandidate(query: String): DeviceArtistArtworkCandidate? {
        val rawUrl = pictureXl ?: pictureBig ?: pictureMedium ?: picture ?: return null
        val safeUrl = DeviceMetadataPolicy.safeDeezerArtworkUrl(rawUrl) ?: return null
        return DeviceArtistArtworkCandidate(
            provider = DevicePublicMetadataProvider.DEEZER,
            externalId = id.toString(),
            artistName = name,
            imageUrl = safeUrl,
            albumCount = nbAlbum,
            fanCount = nbFan,
            confidence = DeviceMetadataMatcher.artistNameScore(query, name),
        )
    }

    suspend fun searchDetailsResult(
        song: Song,
        query: DeviceMetadataRequest,
        provider: DevicePublicMetadataProvider,
    ): DeviceProviderSearchResult<DeviceDetailsCandidate> = withContext(Dispatchers.IO) {
        fun result(candidates: List<DeviceDetailsCandidate> = emptyList(), failed: Boolean = false) =
            DeviceProviderSearchResult(provider, candidates, failed)
        if (!song.isPublicQueryTarget()) return@withContext result()
        if (!NetworkClient.isDevicePublicMetadataEnabled()) return@withContext result()
        val request = query.normalized()
        if (request.title.isBlank()) return@withContext result()
        val querySong = song.withMetadataRequest(request)

        runCatching {
            when (provider) {
                DevicePublicMetadataProvider.MUSICBRAINZ_CAA ->
                    searchMusicBrainzCandidates(querySong, throwOnFailure = true)
                        .take(MAX_MANUAL_RESULTS)
                        .map { match ->
                            val release = match.release
                            DeviceDetailsCandidate(
                                provider = provider,
                                externalId = match.recordingId,
                                title = match.title,
                                artist = match.artist,
                                album = release.title,
                                albumArtist = match.artist,
                                durationSeconds = match.durationSeconds,
                                releaseDate = release.date ?: match.firstReleaseDate,
                                trackCount = release.trackCount,
                                albumType = buildList {
                                    release.releaseGroup?.primaryType?.let(::add)
                                    addAll(release.releaseGroup?.secondaryTypes.orEmpty())
                                }.distinct().joinToString(" · ").takeIf(String::isNotBlank),
                                country = release.country,
                                label = release.labelInfo.firstNotNullOfOrNull { it.label?.name },
                                confidence = match.confidence,
                            )
                        }
                DevicePublicMetadataProvider.DEEZER -> {
                    val service = NetworkClient.deezerApiService
                        ?: error("Deezer service unavailable")
                    val ranked = rankDeezerArtwork(querySong, null, throwOnAllFailure = true)
                        .take(MAX_MANUAL_RESULTS)
                    val albumRows = runCatching {
                        service.searchAlbums(
                            "${request.album.orEmpty()} ${request.artist.orEmpty()}".trim(),
                            MAX_MANUAL_RESULTS * 2,
                        ).data
                    }.throwIfCancelled().getOrDefault(emptyList())
                    val artistRows = request.artist?.let { artist ->
                        runCatching { service.searchArtists(artist, MAX_MANUAL_RESULTS * 2).data }
                            .throwIfCancelled().getOrDefault(emptyList())
                    }.orEmpty()
                    ranked.map { (track, confidence) ->
                        val album = albumRows.firstOrNull { it.id == track.album?.id }
                            ?: albumRows.firstOrNull {
                                it.title.equals(track.album?.title, ignoreCase = true)
                            }
                        val artist = artistRows.firstOrNull { it.id == track.artist?.id }
                            ?: artistRows.firstOrNull {
                                it.name.equals(track.artist?.name, ignoreCase = true)
                            }
                        val artwork = track.album?.let { item ->
                            item.coverXl ?: item.coverBig ?: item.coverMedium ?: item.cover
                        }?.let(DeviceMetadataPolicy::safeDeezerArtworkUrl)
                        DeviceDetailsCandidate(
                            provider = provider,
                            externalId = track.id.toString(),
                            title = track.title,
                            artist = track.artist?.name.orEmpty(),
                            album = track.album?.title.orEmpty(),
                            albumArtist = track.artist?.name,
                            durationSeconds = track.duration?.toDouble(),
                            releaseDate = album?.releaseDate,
                            trackCount = album?.nbTracks?.takeIf { it > 0 },
                            artistAlbumCount = artist?.nbAlbum?.takeIf { it > 0 },
                            artistFanCount = artist?.nbFan?.takeIf { it > 0 },
                            artworkUrl = artwork,
                            confidence = confidence,
                        )
                    }
                }
                DevicePublicMetadataProvider.ITUNES -> {
                    val service = NetworkClient.itunesSearchApiService
                        ?: error("iTunes Search service unavailable")
                    val input = DeviceMatchInput(
                        request.title,
                        request.artist.orEmpty(),
                        request.album.orEmpty(),
                        request.durationSeconds?.times(1000L) ?: 0L,
                    )
                    service.searchSongs(
                        "${request.artist.orEmpty()} ${request.title}".trim(),
                        limit = MAX_MANUAL_RESULTS * 2,
                    ).results.asSequence().map { track ->
                        val artwork = track.artworkUrl100?.replace(
                            Regex("/\\d+x\\d+bb\\."),
                            "/600x600bb.",
                        )?.let(DeviceMetadataPolicy::safeItunesArtworkUrl)
                        DeviceDetailsCandidate(
                            provider = provider,
                            externalId = track.trackId.toString(),
                            title = track.trackName.orEmpty(),
                            artist = track.artistName.orEmpty(),
                            album = track.collectionName.orEmpty(),
                            albumArtist = track.artistName,
                            durationSeconds = track.trackTimeMillis?.div(1000.0),
                            releaseDate = track.releaseDate,
                            trackNumber = track.trackNumber,
                            discNumber = track.discNumber,
                            trackCount = track.trackCount,
                            genre = track.primaryGenreName,
                            country = track.country,
                            artworkUrl = artwork,
                            confidence = DeviceMetadataMatcher.score(
                                input,
                                track.trackName,
                                track.artistName,
                                track.collectionName,
                                track.trackTimeMillis?.div(1000.0),
                            ),
                        )
                    }.sortedByDescending(DeviceDetailsCandidate::confidence)
                        .distinctBy(DeviceDetailsCandidate::externalId)
                        .take(MAX_MANUAL_RESULTS)
                        .toList()
                }
                DevicePublicMetadataProvider.LRCLIB,
                DevicePublicMetadataProvider.WIKIPEDIA -> emptyList()
            }
        }.throwIfCancelled().fold(
            onSuccess = { result(it) },
            onFailure = { result(failed = true) },
        )
    }

    suspend fun searchEditorialResult(
        query: DeviceMetadataRequest,
    ): DeviceProviderSearchResult<DeviceEditorialCandidate> = withContext(Dispatchers.IO) {
        fun result(candidates: List<DeviceEditorialCandidate> = emptyList(), failed: Boolean = false) =
            DeviceProviderSearchResult(DevicePublicMetadataProvider.WIKIPEDIA, candidates, failed)
        if (!NetworkClient.isDevicePublicMetadataEnabled()) return@withContext result()
        val request = query.normalized()
        runCatching {
            buildList {
                request.album?.let { album ->
                    WikipediaProvider.getAlbumDescription(album, request.artist)?.let { description ->
                        add(
                            DeviceEditorialCandidate(
                                provider = DevicePublicMetadataProvider.WIKIPEDIA,
                                externalId = "album:${album.lowercase(Locale.ROOT)}",
                                subject = DeviceEditorialSubject.ALBUM,
                                title = album,
                                description = description.take(MAX_EDITORIAL_CHARS),
                            )
                        )
                    }
                }
                request.artist?.let { artist ->
                    WikipediaProvider.getArtistDescription(artist)?.let { description ->
                        add(
                            DeviceEditorialCandidate(
                                provider = DevicePublicMetadataProvider.WIKIPEDIA,
                                externalId = "artist:${artist.lowercase(Locale.ROOT)}",
                                subject = DeviceEditorialSubject.ARTIST,
                                title = artist,
                                description = description.take(MAX_EDITORIAL_CHARS),
                            )
                        )
                    }
                }
            }
        }.throwIfCancelled().fold(
            onSuccess = { result(it) },
            onFailure = { result(failed = true) },
        )
    }

    suspend fun applyDetails(
        song: Song,
        candidate: DeviceDetailsCandidate,
        fields: Set<DeviceDetailsField>,
    ): Song =
        withContext(Dispatchers.IO) {
            require(song.isDeviceSong()) { "Only DEVICE songs can own public metadata overrides" }
            require(fields.isNotEmpty()) { "At least one detail field must be selected" }
            DeviceAlbumIdentity.key(song)?.let { ensureAlbumLink(song, it) }
            val old = dao.getBySongId(song.id)
            val updated = song.copy(
                title = candidate.title.trim().takeIf {
                    DeviceDetailsField.TITLE in fields && it.isNotEmpty()
                } ?: song.title,
                artist = candidate.artist.trim().takeIf {
                    DeviceDetailsField.ARTIST in fields && it.isNotEmpty()
                } ?: song.artist,
                album = candidate.album.trim().takeIf {
                    DeviceDetailsField.ALBUM in fields && it.isNotEmpty()
                } ?: song.album,
                albumArtist = candidate.albumArtist?.trim()?.takeIf {
                    DeviceDetailsField.ALBUM_ARTIST in fields && it.isNotEmpty()
                } ?: song.albumArtist,
                year = candidate.year?.takeIf { DeviceDetailsField.YEAR in fields } ?: song.year,
                trackNumber = candidate.trackNumber?.takeIf {
                    DeviceDetailsField.TRACK_NUMBER in fields && it > 0
                } ?: song.trackNumber,
                discNumber = candidate.discNumber?.takeIf {
                    DeviceDetailsField.DISC_NUMBER in fields && it > 0
                } ?: song.discNumber,
                genre = candidate.genre?.trim()?.takeIf {
                    DeviceDetailsField.GENRE in fields && it.isNotEmpty()
                } ?: song.genre,
            )
            dao.upsert(
                base(song, old).copy(
                    detailsProvider = candidate.provider.name,
                    detailsExternalId = candidate.externalId,
                    detailsConfidence = candidate.confidence,
                    detailsPinned = true,
                    titleOverride = updated.title.takeIf { DeviceDetailsField.TITLE in fields }
                        ?: old?.titleOverride,
                    artistOverride = updated.artist.takeIf { DeviceDetailsField.ARTIST in fields }
                        ?: old?.artistOverride,
                    albumOverride = updated.album.takeIf { DeviceDetailsField.ALBUM in fields }
                        ?: old?.albumOverride,
                    albumArtistOverride = updated.albumArtist.takeIf {
                        DeviceDetailsField.ALBUM_ARTIST in fields
                    } ?: old?.albumArtistOverride,
                    yearOverride = updated.year.takeIf {
                        DeviceDetailsField.YEAR in fields && it > 0
                    } ?: old?.yearOverride,
                    trackNumberOverride = updated.trackNumber.takeIf {
                        DeviceDetailsField.TRACK_NUMBER in fields && it > 0
                    } ?: old?.trackNumberOverride,
                    discNumberOverride = updated.discNumber.takeIf {
                        DeviceDetailsField.DISC_NUMBER in fields && it > 0
                    } ?: old?.discNumberOverride,
                    genreOverride = updated.genre.takeIf { DeviceDetailsField.GENRE in fields }
                        ?: old?.genreOverride,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            updated
        }

    suspend fun projectDetails(songs: List<Song>): List<Song> = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext songs
        val overrides = dao.getAll().filter(DeviceMetadataEntity::detailsPinned)
            .associateBy(DeviceMetadataEntity::songId)
        songs.map { song ->
            val row = overrides[song.id] ?: return@map song
            song.copy(
                title = row.titleOverride?.takeIf(String::isNotBlank) ?: song.title,
                artist = row.artistOverride?.takeIf(String::isNotBlank) ?: song.artist,
                album = row.albumOverride?.takeIf(String::isNotBlank) ?: song.album,
                albumArtist = row.albumArtistOverride?.takeIf(String::isNotBlank) ?: song.albumArtist,
                year = row.yearOverride?.takeIf { it > 0 } ?: song.year,
                trackNumber = row.trackNumberOverride?.takeIf { it > 0 } ?: song.trackNumber,
                discNumber = row.discNumberOverride?.takeIf { it > 0 } ?: song.discNumber,
                genre = row.genreOverride?.takeIf(String::isNotBlank) ?: song.genre,
            )
        }
    }

    suspend fun applyArtwork(
        song: Song,
        candidate: DeviceArtworkCandidate,
        saveTarget: DeviceArtworkSaveTarget = DeviceArtworkSaveTarget.APP_ONLY,
        destinationTreeUri: Uri? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        if (!song.isDeviceSong()) return@withContext null
        val albumKey = albumKeyFor(song) ?: return@withContext null
        val downloadProvider = when (candidate.provider) {
            DevicePublicMetadataProvider.MUSICBRAINZ_CAA -> ArtworkProvider.COVER_ART_ARCHIVE
            DevicePublicMetadataProvider.DEEZER -> ArtworkProvider.DEEZER
            DevicePublicMetadataProvider.ITUNES -> ArtworkProvider.ITUNES
            DevicePublicMetadataProvider.LRCLIB,
            DevicePublicMetadataProvider.WIKIPEDIA -> return@withContext null
        }
        albumLocks.computeIfAbsent(albumKey) { Mutex() }.withLock {
            val cached = downloadArtwork(
                candidate.imageUrl,
                "album-manual-${sha256("$albumKey|${candidate.provider}|${candidate.externalId}|${candidate.imageUrl}")}.jpg",
                downloadProvider,
            ) ?: return@withLock null
            if (saveTarget == DeviceArtworkSaveTarget.MUSIC_FOLDER) {
                val destination = destinationTreeUri ?: return@withLock null
                val audioFileName = song.path?.let(::File)?.name
                    ?: contentDisplayName(song.uri)
                    ?: song.title
                folders.writeArtwork(destination, cached.file, cached.mediaType, audioFileName)
                    ?: return@withLock null
            }
            saveAlbumArtwork(
                song = song,
                file = cached.file,
                source = "USER_SELECTED",
                provider = candidate.provider.name,
                externalReleaseId = candidate.externalId,
                externalReleaseGroupId = candidate.releaseGroupId,
                confidence = candidate.confidence,
                cached = cached,
                pinned = true,
            )
            cached.file.toUri()
        }
    }

    suspend fun cachedArtwork(song: Song): Uri? {
        if (!song.isDeviceSong()) return null
        val candidates = artworkCandidates(song)
        return candidates.userSelected ?: candidates.cached
    }

    suspend fun pinnedArtwork(song: Song): Uri? {
        if (!song.isDeviceSong()) return null
        return artworkCandidates(song).userSelected
    }

    private suspend fun artworkCandidates(song: Song): ArtworkCandidates {
        val albumKey = albumKeyFor(song)
        if (albumKey != null) ensureAlbumLink(song, albumKey)
        val album = albumKey?.let { albumDao.getAlbum(it) }
        val albumFile = album?.let(::validAlbumArtwork)?.toUri()
        val selected = albumFile?.takeIf {
            album.pinned && album.artworkSource == "USER_SELECTED"
        }
        val songRow = dao.getBySongId(song.id)
        val songFile = songRow
            ?.takeIf { it.fingerprint == fingerprint(song) }
            ?.artworkCachePath
            ?.let(::File)
            ?.takeIf { it.isFile && artworkValidator.isReadable(it.toUri()) }
            ?.toUri()
        return ArtworkCandidates(selected, songFile ?: albumFile)
    }

    /** Applies valid DEVICE album cache and removes unreadable shell URIs from the projection. */
    suspend fun projectArtwork(songs: List<Song>): List<Song> = withContext(Dispatchers.IO) {
        val projected = songs.map { song ->
            if (!song.isDeviceSong()) return@map song
            val candidates = artworkCandidates(song)
            val current = song.artworkUri?.takeIf { uri ->
                (uri.scheme == "content" || uri.scheme == "file" || uri.scheme == null) &&
                    !isManagedAlbumArtwork(uri) && artworkValidator.isReadable(uri)
            }
            val resolved = DeviceMetadataPolicy.resolveArtwork(
                userSelected = candidates.userSelected,
                local = current,
                cached = candidates.cached,
            )
            if (resolved == song.artworkUri) song else song.copy(artworkUri = resolved)
        }
        val changed = projected.filterIndexed { index, song -> song.artworkUri != songs[index].artworkUri }
        if (changed.isNotEmpty()) songDao.upsertAll(changed.map(Song::toEntity))
        projected
    }

    suspend fun restoreLocalArtwork(song: Song): Uri? = withContext(Dispatchers.IO) {
        if (!song.isDeviceSong()) return@withContext null
        dao.getBySongId(song.id)?.artworkCachePath?.let { path ->
            if (path.startsWith(metadataDir().absolutePath)) runCatching { File(path).delete() }
        }
        dao.clearArtwork(song.id)
        albumKeyFor(song)?.let { albumKey ->
            albumDao.getAlbum(albumKey)?.artworkCachePath?.let { path ->
                if (path.startsWith(metadataDir().absolutePath)) runCatching { File(path).delete() }
            }
            albumDao.clearArtwork(albumKey)
        }
        song.artworkUri?.takeIf { it.scheme == "content" || it.scheme == "file" || it.scheme == null }
            ?.takeIf(artworkValidator::isReadable)
            ?.let { return@withContext it }
        MediaUtils.extractEmbeddedAlbumArt(context, song.uri, context.filesDir, false, song.path)?.let { uri ->
            uri.path?.let(::File)?.takeIf(File::isFile)?.let { saveArtwork(song, it, "EMBEDDED", null, null, 1.0) }
            return@withContext uri
        }
        findSiblingArtwork(song)?.let { sibling ->
            if (sibling.albumScoped) saveAlbumArtwork(song, sibling.file, "SIBLING", null, null, null, 1.0)
            else saveArtwork(song, sibling.file, "SIBLING", null, null, 1.0)
            sibling.file.toUri()
        }
    }

    suspend fun findOrFetchArtwork(song: Song, forceOnline: Boolean = false): Uri? = withContext(Dispatchers.IO) {
        if (!song.isDeviceSong()) return@withContext null
        pinnedArtwork(song)?.let { return@withContext it }
        if (!forceOnline) {
            song.artworkUri?.takeIf { it.scheme == "content" || it.scheme == "file" || it.scheme == null }
                ?.takeIf(artworkValidator::isReadable)
                ?.let { return@withContext it }
            MediaUtils.extractEmbeddedAlbumArt(context, song.uri, context.filesDir, false, song.path)?.let { uri ->
                uri.path?.let(::File)?.takeIf(File::isFile)?.let { file ->
                    saveArtwork(song, file, "EMBEDDED", null, null, 1.0)
                    return@withContext uri
                }
            }
            findSiblingArtwork(song)?.let { sibling ->
                if (sibling.albumScoped) saveAlbumArtwork(song, sibling.file, "SIBLING", null, null, null, 1.0)
                else saveArtwork(song, sibling.file, "SIBLING", null, null, 1.0)
                return@withContext sibling.file.toUri()
            }
            cachedArtwork(song)?.let { return@withContext it }
        }
        if (!NetworkClient.isDevicePublicMetadataEnabled()) return@withContext null

        val albumKey = albumKeyFor(song) ?: return@withContext null
        albumLocks.computeIfAbsent(albumKey) { Mutex() }.withLock {
            ensureAlbumLink(song, albumKey)
            val previous = albumDao.getAlbum(albumKey)
            if (!forceOnline) {
                previous?.let(::validAlbumArtwork)?.let { return@withLock it.toUri() }
                if ((previous?.negativeUntil ?: 0L) > System.currentTimeMillis()) return@withLock null
            }

            val musicBrainz = searchMusicBrainz(song)
            if (musicBrainz != null) {
                val releaseUrls = buildList {
                    add("https://coverartarchive.org/release/${musicBrainz.release.id}/front-500")
                    musicBrainz.relatedReleaseIds.filterNot { it == musicBrainz.release.id }.forEach {
                        add("https://coverartarchive.org/release/$it/front-500")
                    }
                    musicBrainz.release.releaseGroup?.id?.let {
                        add("https://coverartarchive.org/release-group/$it/front-500")
                    }
                }
                for (url in releaseUrls.distinct()) {
                    val cached = downloadArtwork(url, "album-${sha256(albumKey)}.jpg", ArtworkProvider.COVER_ART_ARCHIVE)
                    if (cached != null) {
                        saveAlbumArtwork(
                            song, cached.file, "PUBLIC_API", "MUSICBRAINZ_CAA",
                            musicBrainz.release.id, musicBrainz.release.releaseGroup?.id,
                            musicBrainz.confidence, cached
                        )
                        return@withLock cached.file.toUri()
                    }
                }
            }

            val deezer = searchDeezerArtwork(song, musicBrainz)
            if (deezer != null) {
                val cached = downloadArtwork(deezer.first, "album-${sha256(albumKey)}.jpg", ArtworkProvider.DEEZER)
                if (cached != null) {
                    saveAlbumArtwork(song, cached.file, "PUBLIC_API", "DEEZER", deezer.second, null, deezer.third, cached)
                    return@withLock cached.file.toUri()
                }
            }

            val itunesCandidates = searchArtworkResult(
                song,
                requestFor(song),
                DevicePublicMetadataProvider.ITUNES,
            ).candidates
            val itunes = itunesCandidates.firstOrNull()?.takeIf { best ->
                DeviceMetadataMatcher.isAutomaticMatch(
                    best.confidence,
                    itunesCandidates.getOrNull(1)?.confidence,
                    MUSICBRAINZ_AUTO_CONFIDENCE,
                    MUSICBRAINZ_AUTO_MARGIN,
                    unconditionalThreshold = EXACT_AUTO_CONFIDENCE,
                )
            }
            if (itunes != null) {
                val cached = downloadArtwork(
                    itunes.imageUrl,
                    "album-${sha256(albumKey)}.jpg",
                    ArtworkProvider.ITUNES,
                )
                if (cached != null) {
                    saveAlbumArtwork(
                        song,
                        cached.file,
                        "PUBLIC_API",
                        "ITUNES",
                        itunes.externalId,
                        null,
                        itunes.confidence,
                        cached,
                    )
                    return@withLock cached.file.toUri()
                }
            }

            albumDao.upsertAlbum(albumBase(song, previous, albumKey).copy(
                negativeUntil = System.currentTimeMillis() + NEGATIVE_CACHE_MS,
                updatedAt = System.currentTimeMillis()
            ))
            null
        }
    }

    private suspend fun saveArtwork(song: Song, file: File, source: String, provider: String?, externalId: String?, confidence: Double?) {
        val old = dao.getBySongId(song.id)
        dao.upsert(base(song, old).copy(
            artworkSource = source, artworkProvider = provider, artworkExternalId = externalId,
            artworkConfidence = confidence, artworkCachePath = file.absolutePath, updatedAt = System.currentTimeMillis()
        ))
    }

    private fun base(song: Song, old: DeviceMetadataEntity?) = (old ?: DeviceMetadataEntity(
        stableId = stableId(song), songId = song.id, contentUri = song.uri.toString(), fingerprint = fingerprint(song)
    )).copy(contentUri = song.uri.toString(), fingerprint = fingerprint(song))

    private suspend fun saveAlbumArtwork(
        song: Song,
        file: File,
        source: String,
        provider: String?,
        externalReleaseId: String?,
        externalReleaseGroupId: String?,
        confidence: Double?,
        cached: CachedArtworkFile? = null,
        pinned: Boolean = false
    ) {
        val albumKey = albumKeyFor(song) ?: return
        ensureAlbumLink(song, albumKey)
        val old = albumDao.getAlbum(albumKey)
        if (
            old != null &&
            DeviceMetadataPolicy.shouldPreservePinnedSelection(old.pinned, pinned)
        ) {
            return
        }
        val materialized = cached ?: cachedFile(file)
        albumDao.upsertAlbum(albumBase(song, old, albumKey).copy(
            provider = provider,
            externalReleaseId = externalReleaseId,
            externalReleaseGroupId = externalReleaseGroupId,
            confidence = confidence,
            pinned = pinned,
            artworkSource = source,
            artworkCachePath = file.absolutePath,
            artworkSha256 = materialized?.sha256,
            mediaType = materialized?.mediaType,
            byteSize = materialized?.byteSize,
            matchedAt = if (provider != null) System.currentTimeMillis() else old?.matchedAt,
            updatedAt = System.currentTimeMillis(),
            negativeUntil = 0L
        ))
    }

    private fun albumBase(
        song: Song,
        old: DeviceAlbumMetadataEntity?,
        albumKey: String,
    ): DeviceAlbumMetadataEntity {
        return (old ?: DeviceAlbumMetadataEntity(
            albumKey = albumKey,
            localTitle = song.album,
            localArtist = song.albumArtist?.takeUnless { it.isUnknown() } ?: song.artist
        )).copy(localTitle = song.album, localArtist = song.albumArtist?.takeUnless { it.isUnknown() } ?: song.artist)
    }

    private suspend fun ensureAlbumLink(song: Song, albumKey: String) {
        albumDao.upsertSongAlbum(DeviceSongAlbumEntity(stableId(song), albumKey))
    }

    /** A pinned app-only tag override must not detach the song from its existing artwork album. */
    private suspend fun albumKeyFor(song: Song): String? {
        val computed = DeviceAlbumIdentity.key(song) ?: return null
        val detailsPinned = dao.getBySongId(song.id)?.detailsPinned == true
        return if (detailsPinned) {
            albumDao.getAlbumKeyForSong(stableId(song)) ?: computed
        } else {
            computed
        }
    }

    private suspend fun searchMusicBrainz(song: Song): MusicBrainzArtworkMatch? {
        val candidates = searchMusicBrainzCandidates(song)
        val best = candidates.firstOrNull() ?: return null
        val runnerUp = candidates.drop(1).firstOrNull {
            it.release.id != best.release.id &&
                it.release.releaseGroup?.id != best.release.releaseGroup?.id
        }?.confidence
        if (!DeviceMetadataMatcher.isAutomaticMatch(
                best.confidence,
                runnerUp,
                MUSICBRAINZ_AUTO_CONFIDENCE,
                MUSICBRAINZ_AUTO_MARGIN,
                unconditionalThreshold = EXACT_AUTO_CONFIDENCE,
            )
        ) {
            return null
        }
        return best
    }

    private suspend fun searchMusicBrainzCandidates(
        song: Song,
        throwOnFailure: Boolean = false,
    ): List<MusicBrainzArtworkMatch> {
        val service = NetworkClient.musicBrainzApiService
            ?: if (throwOnFailure) error("MusicBrainz service unavailable") else return emptyList()
        val clauses = buildList {
            add("recording:\"${lucene(song.title)}\"")
            song.artist.takeUnless { it.isUnknown() }?.let { add("artist:\"${lucene(it)}\"") }
            song.album.takeUnless { it.isUnknown() }?.let { add("release:\"${lucene(it)}\"") }
        }
        val response = musicBrainzRateMutex.withLock {
            val waitMs = MUSICBRAINZ_INTERVAL_MS - (System.currentTimeMillis() - lastMusicBrainzRequestAt)
            if (waitMs > 0) delay(waitMs)
            lastMusicBrainzRequestAt = System.currentTimeMillis()
            if (throwOnFailure) service.searchRecordings(clauses.joinToString(" AND "))
            else runCatching {
                service.searchRecordings(clauses.joinToString(" AND "))
            }.throwIfCancelled().getOrNull()
        } ?: return emptyList()

        val input = DeviceMatchInput(song.title, song.artist, song.album, song.duration)
        val candidates = response.recordings.flatMap { recording ->
            val artist = recording.artistCredit.joinToString("") { it.name ?: it.artist?.name.orEmpty() }
            val aliases = recording.artistCredit.flatMap { credit ->
                buildList {
                    credit.artist?.name?.let(::add)
                    credit.artist?.sortName?.let(::add)
                    credit.artist?.aliases.orEmpty().forEach { alias ->
                        alias.name?.let(::add)
                        alias.sortName?.let(::add)
                    }
                }
            }.distinct()
            recording.releases.map { release ->
                val confidence = DeviceMetadataMatcher.score(
                    input, recording.title, artist, release.title, recording.length?.div(1000.0)
                ) + if (release.status.equals("Official", true)) 0.02 else 0.0
                Triple(recording, release, confidence.coerceAtMost(1.0)) to aliases
            }
        }.sortedByDescending { it.first.third }
            .distinctBy { it.first.second.id }

        return candidates.map { ranked ->
            val recording: MusicBrainzRecording = ranked.first.first
            val release = ranked.first.second
            val groupId = release.releaseGroup?.id
            val related = candidates.asSequence()
                .map { it.first.second }
                .filter { it.status.equals("Official", true) && it.releaseGroup?.id == groupId }
                .map(MusicBrainzRelease::id)
                .distinct()
                .take(MAX_CAA_RELEASE_ATTEMPTS)
                .toList()
            MusicBrainzArtworkMatch(
                recordingId = recording.id,
                title = recording.title,
                artist = recording.artistCredit.joinToString("") { it.name ?: it.artist?.name.orEmpty() },
                artistAliases = ranked.second,
                durationSeconds = recording.length?.div(1000.0),
                firstReleaseDate = recording.firstReleaseDate,
                release = release,
                confidence = ranked.first.third,
                relatedReleaseIds = related,
            )
        }
    }

    private suspend fun searchDeezerArtwork(
        song: Song,
        musicBrainz: MusicBrainzArtworkMatch?
    ): Triple<String, String, Double>? {
        val ranked = rankDeezerArtwork(song, musicBrainz)
        val best = ranked.firstOrNull() ?: return null
        if (!DeviceMetadataMatcher.isAutomaticMatch(
                best.second,
                ranked.getOrNull(1)?.second,
                MUSICBRAINZ_AUTO_CONFIDENCE,
                MUSICBRAINZ_AUTO_MARGIN,
                unconditionalThreshold = EXACT_AUTO_CONFIDENCE,
            )
        ) {
            return null
        }
        val trackAlbum = best.first.album
        val url = trackAlbum?.coverXl ?: trackAlbum?.coverBig ?: trackAlbum?.coverMedium ?: trackAlbum?.cover
            ?: run {
                val service = NetworkClient.deezerApiService ?: return null
                val albumId = trackAlbum?.id ?: return null
                val albums = runCatching {
                    service.searchAlbums(
                        "album:\"${trackAlbum.title}\" artist:\"${best.first.artist?.name.orEmpty()}\"",
                        10,
                    ).data
                }.throwIfCancelled().getOrDefault(emptyList())
                val album = albums.firstOrNull { it.id == albumId } ?: albums.firstOrNull()
                album?.coverXl ?: album?.coverBig ?: album?.coverMedium ?: album?.cover
            } ?: return null
        return Triple(url, best.first.id.toString(), best.second)
    }

    private suspend fun rankDeezerArtwork(
        song: Song,
        musicBrainz: MusicBrainzArtworkMatch?,
        throwOnAllFailure: Boolean = false,
    ): List<Pair<DeezerTrack, Double>> {
        val service = NetworkClient.deezerApiService
            ?: if (throwOnAllFailure) error("Deezer service unavailable") else return emptyList()
        val queries = buildList {
            add("track:\"${song.title}\" artist:\"${song.artist}\"")
            add("${song.title} ${song.artist}".trim())
            if (musicBrainz != null) {
                add("${musicBrainz.title} ${musicBrainz.artist}".trim())
                musicBrainz.artistAliases.take(4).forEach { add("${musicBrainz.title} $it".trim()) }
            }
            add(song.title)
        }.map(String::trim).filter(String::isNotEmpty).distinct()

        val input = DeviceMatchInput(song.title, song.artist, song.album, song.duration)
        val candidates = linkedMapOf<Long, Pair<io.github.cluno1.sonorus.network.DeezerTrack, Double>>()
        var successfulRequests = 0
        var lastFailure: Throwable? = null
        for (query in queries) {
            val response = runCatching { service.searchTracks(query, 25).data }.throwIfCancelled()
            response.onSuccess { successfulRequests++ }.onFailure { lastFailure = it }
            val tracks = response.getOrDefault(emptyList())
            tracks.forEach { track ->
                val score = DeviceMetadataMatcher.score(
                    input, track.title, track.artist?.name, track.album?.title, track.duration?.toDouble()
                )
                val previous = candidates[track.id]
                if (previous == null || score > previous.second) candidates[track.id] = track to score
            }
            val ranked = candidates.values.sortedByDescending { it.second }
            if (ranked.firstOrNull()?.let {
                    DeviceMetadataMatcher.isAutomaticMatch(
                        it.second,
                        ranked.getOrNull(1)?.second,
                        MUSICBRAINZ_AUTO_CONFIDENCE,
                        MUSICBRAINZ_AUTO_MARGIN,
                        unconditionalThreshold = EXACT_AUTO_CONFIDENCE,
                    )
                } == true
            ) break
        }
        if (throwOnAllFailure && successfulRequests == 0 && lastFailure != null) throw lastFailure
        return candidates.values.sortedByDescending { it.second }
    }

    private fun findSiblingArtwork(song: Song): SiblingArtwork? {
        findPhysicalSiblingArtwork(song)?.let { return it }
        val found = folders.findSibling(
            song.uri, setOf("jpg", "jpeg", "png", "webp"), setOf("cover", "folder", "album", "front")
        ) ?: return null
        val audioBaseName = song.path?.substringAfterLast('/')?.substringBeforeLast('.')
            ?: contentDisplayName(song.uri)?.substringBeforeLast('.')
        val albumScoped = !found.name.substringBeforeLast('.').equals(audioBaseName, true)
        val key = if (albumScoped) DeviceAlbumIdentity.key(song)?.let(::sha256) ?: stableId(song) else stableId(song)
        val target = copyContent(found.uri, "sibling-${if (albumScoped) "album-" else ""}$key.img") ?: return null
        return target.takeIf { artworkValidator.isReadable(it.toUri()) }?.let { SiblingArtwork(it, albumScoped) }
    }

    private fun findPhysicalSiblingArtwork(song: Song): SiblingArtwork? {
        val audio = song.path?.let(::File)?.takeIf(File::isFile) ?: return null
        val siblings = audio.parentFile?.listFiles().orEmpty()
        val extensions = setOf("jpg", "jpeg", "png", "webp")
        val sameName = siblings.firstOrNull { it.isFile && it.nameWithoutExtension.equals(audio.nameWithoutExtension, true) && it.extension.lowercase() in extensions }
        val generic = siblings.firstOrNull { it.isFile && it.nameWithoutExtension.lowercase() in setOf("cover", "folder", "album", "front") && it.extension.lowercase() in extensions }
        val source = sameName ?: generic ?: return null
        val albumScoped = sameName == null
        val key = if (albumScoped) DeviceAlbumIdentity.key(song)?.let(::sha256) ?: stableId(song) else stableId(song)
        val target = metadataDir().resolve("sibling-${if (albumScoped) "album-" else ""}$key.${source.extension.ifBlank { "img" }}")
        runCatching { source.copyTo(target, overwrite = true) }.getOrNull() ?: return null
        return target.takeIf { artworkValidator.isReadable(it.toUri()) }?.let { SiblingArtwork(it, albumScoped) }
    }

    private fun copyContent(uri: Uri, name: String): File? = runCatching {
        val target = metadataDir().resolve(name)
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) } ?: return null
        target
    }.getOrNull()

    private fun contentDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    private enum class ArtworkProvider { DEEZER, COVER_ART_ARCHIVE, ITUNES }

    /** Reads only the URI granted by the system picker; the result stays in the editor draft. */
    suspend fun gatewayArtworkBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        check(allowLanQueries && uri.scheme == "content")
        normalizedGatewayArtworkBytes {
            context.contentResolver.openInputStream(uri) ?: error("Unable to open cover image")
        }
    }

    /** Reuses the public-provider URL and image checks, without writing device song metadata. */
    suspend fun gatewayArtworkBytes(candidate: DeviceArtworkCandidate): ByteArray = withContext(Dispatchers.IO) {
        check(allowLanQueries && NetworkClient.isDevicePublicMetadataEnabled())
        val provider = when (candidate.provider) {
            DevicePublicMetadataProvider.DEEZER -> ArtworkProvider.DEEZER
            DevicePublicMetadataProvider.MUSICBRAINZ_CAA -> ArtworkProvider.COVER_ART_ARCHIVE
            DevicePublicMetadataProvider.ITUNES -> ArtworkProvider.ITUNES
            else -> error("Unsupported artwork provider")
        }
        val cached = downloadArtwork(candidate.imageUrl, "lan-${java.util.UUID.randomUUID()}.jpg", provider)
            ?: error("Unable to download cover image")
        try {
            normalizedGatewayArtworkBytes { cached.file.inputStream() }
        } finally { cached.file.delete() }
    }

    private fun normalizedGatewayArtworkBytes(open: () -> InputStream): ByteArray {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to read cover image" }
        if (bounds.outWidth > 8192 || bounds.outHeight > 8192) throw GatewayArtworkTooLargeException()
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 2048 || bounds.outHeight / sampleSize > 2048) sampleSize *= 2
        val orientation = runCatching {
            open().use {
                androidx.exifinterface.media.ExifInterface(it).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
        val bitmap = open().use {
            android.graphics.BitmapFactory.decodeStream(it, null,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("Unable to decode cover image")
        var oriented = bitmap
        try {
            val matrix = android.graphics.Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) oriented = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
            )
            val output = java.io.ByteArrayOutputStream()
            check(oriented.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output))
            return output.toByteArray().also {
                if (it.size > 6 * 1024 * 1024) throw GatewayArtworkTooLargeException()
            }
        } finally {
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()
        }
    }

    private fun downloadArtwork(url: String, name: String, provider: ArtworkProvider): CachedArtworkFile? = runCatching {
        val safe = when (provider) {
            ArtworkProvider.DEEZER -> DeviceMetadataPolicy.safeDeezerArtworkUrl(url)
            ArtworkProvider.COVER_ART_ARCHIVE -> DeviceMetadataPolicy.safeCoverArtUrl(url)
            ArtworkProvider.ITUNES -> DeviceMetadataPolicy.safeItunesArtworkUrl(url)
        } ?: return null
        val client = if (provider == ArtworkProvider.DEEZER) NetworkClient.genericHttpClient else NetworkClient.coverArtHttpClient
        client.newCall(Request.Builder().url(safe).get().build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val finalUrl = response.request.url
            val trustedFinal = when (provider) {
                ArtworkProvider.DEEZER -> DeviceMetadataPolicy.safeDeezerArtworkUrl(finalUrl.toString())
                ArtworkProvider.COVER_ART_ARCHIVE -> DeviceMetadataPolicy.safeCoverArtUrl(finalUrl.toString())
                ArtworkProvider.ITUNES -> DeviceMetadataPolicy.safeItunesArtworkUrl(finalUrl.toString())
            }
            if (trustedFinal == null) return null
            if (!DeviceMetadataPolicy.isImageContentType(response.header("Content-Type"))) return null
            val declaredLength = response.body.contentLength()
            if (declaredLength > MAX_ARTWORK_BYTES) return null
            val target = metadataDir().resolve(name)
            val temporary = metadataDir().resolve(".$name.${System.nanoTime()}.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            response.body.byteStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_ARTWORK_BYTES) {
                            temporary.delete()
                            return null
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            if (total <= 0 || !artworkValidator.isReadable(temporary.toUri())) {
                temporary.delete()
                return null
            }
            runCatching {
                java.nio.file.Files.move(
                    temporary.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            }.recoverCatching {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }.getOrThrow()
            CachedArtworkFile(
                file = target,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                mediaType = response.header("Content-Type").orEmpty().substringBefore(';').lowercase(Locale.ROOT),
                byteSize = total
            )
        }
    }.getOrNull()

    private fun cachedFile(file: File): CachedArtworkFile? = runCatching {
        if (!file.isFile || !artworkValidator.isReadable(file.toUri())) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val mediaType = when (file.extension.lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        CachedArtworkFile(file, digest.digest().joinToString("") { "%02x".format(it) }, mediaType, file.length())
    }.getOrNull()

    private fun validAlbumArtwork(entity: DeviceAlbumMetadataEntity): File? {
        val file = entity.artworkCachePath?.let(::File)?.takeIf(File::isFile) ?: return null
        val validationKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}:${entity.byteSize}:${entity.artworkSha256}"
        albumFileValidationCache[validationKey]?.let { cached -> return if (cached) file else null }
        val valid = (entity.byteSize == null || entity.byteSize == file.length()) &&
            artworkValidator.isReadable(file.toUri()) &&
            (entity.artworkSha256 == null || entity.artworkSha256.equals(sha256(file), true))
        albumFileValidationCache[validationKey] = valid
        return file.takeIf { valid }
    }

    private fun isManagedAlbumArtwork(uri: Uri): Boolean {
        val file = uri.path?.let(::File) ?: return false
        if (file.parentFile?.absolutePath != metadataDir().absolutePath) return false
        return file.name.startsWith("album-") || file.name.startsWith("sibling-album-")
    }

    private fun lucene(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun requestFor(song: Song) = DeviceMetadataRequest(
        title = song.title,
        artist = song.artist.takeUnless { it.isUnknown() },
        album = song.album.takeUnless { it.isUnknown() },
        durationSeconds = (song.duration / 1000L).toInt().takeIf { it > 0 },
    )

    private fun Song.withMetadataRequest(request: DeviceMetadataRequest): Song = copy(
        title = request.title,
        artist = request.artist.orEmpty(),
        album = request.album.orEmpty(),
        duration = request.durationSeconds?.times(1000L) ?: 0L,
    )

    private fun metadataDir() = File(context.filesDir, "device_metadata").apply { mkdirs() }
    suspend fun clearAllCachedMetadata() = withContext(Dispatchers.IO) {
        File(context.filesDir, "device_metadata").listFiles()?.forEach(File::delete)
        dao.deleteAll()
        albumDao.deleteAllSongAlbums()
        albumDao.deleteAllAlbums()
    }
    private fun sourceSize(song: Song): Long = song.path?.let(::File)?.takeIf(File::isFile)?.length() ?: runCatching {
        context.contentResolver.query(song.uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun String.isUnknown() = isBlank() || equals("unknown", true) || equals("<unknown>", true) || startsWith("unknown ", true)
    private fun Song.isDeviceSong() = DeviceMetadataPolicy.isEligible(id, uri.scheme)
    private fun Song.isPublicQueryTarget() = isDeviceSong() || (allowLanQueries &&
        io.github.cluno1.sonorus.features.streaming.domain.model.LanSubsonicPlaybackPolicy.isLanSong(
            io.github.cluno1.sonorus.core.ProductCapabilities.lanSubsonicOnly, id, uri.toString(),
        ))

    companion object {
        const val MIN_AUTO_CONFIDENCE = 0.72
        const val EXACT_AUTO_CONFIDENCE = 0.98
        const val MAX_ARTWORK_BYTES = 8L * 1024L * 1024L
        private const val MUSICBRAINZ_AUTO_CONFIDENCE = 0.85
        private const val MUSICBRAINZ_AUTO_MARGIN = 0.10
        private const val MUSICBRAINZ_INTERVAL_MS = 1_100L
        private const val NEGATIVE_CACHE_MS = 24L * 60L * 60L * 1_000L
        private const val MAX_CAA_RELEASE_ATTEMPTS = 3
        private const val MAX_MANUAL_RESULTS = 12
        private const val MAX_EDITORIAL_CHARS = 4_000
        private val albumLocks = ConcurrentHashMap<String, Mutex>()
        private val albumFileValidationCache = ConcurrentHashMap<String, Boolean>()
        private val musicBrainzRateMutex = Mutex()
        private var lastMusicBrainzRequestAt = 0L
    }
}
