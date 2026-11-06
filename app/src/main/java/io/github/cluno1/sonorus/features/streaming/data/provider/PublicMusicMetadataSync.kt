package io.github.cluno1.sonorus.features.streaming.data.provider

import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogMusicApi
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicMetadataWriteDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicSongDto
import kotlinx.coroutines.CancellationException

/**
 * Reads stored public music metadata and writes back what the client resolved.
 *
 * Invariant: a localized "unknown artist" placeholder never reaches the shared metadata store.
 * Why: every user reads the same record, and the backend requires at least one artist.
 * Silent failure if broken: other users, in any language, see one device's placeholder as the artist.
 * Accepted residual: a song with no known artist is not stored this time, so it is fetched again on the
 * next play and self-heals once an artist is known. Rejected: writing an empty artist list (backend 422).
 *
 * Accepted residual: several artists arrive here already joined with the device's display separator and
 * are stored as one name, as before; the separator now follows the device language. This is cosmetic and
 * splitting back is unsafe because a real name can contain the separator. It ends when writes move server-side.
 */
/** The backend picks the audio format from the order configured for the source. */
internal fun publicMusicStreamPath(source: String, song: String): String =
    "/v2/music/sources/$source/songs/$song/stream?quality=auto"

/**
 * [lyricsMissing] (issue 166) is true only when the song's own source answered that it has no lyrics.
 * A failed or timed-out lyrics request leaves it false: the client must not fall back to other lyrics
 * sources and store their match for everyone just because the network blinked.
 */
internal data class PublicMusicMetadataSyncResult(
    val song: PublicMusicSongDto?,
    val writeSkipped: Boolean = false,
    val lyricsMissing: Boolean = false,
)

private suspend fun storedLyrics(api: CatalogMusicApi, source: String, song: String): String? = try {
    api.metadata(source, song).takeIf { it.isSuccessful }?.body()?.lyricsLrc?.takeIf(String::isNotBlank)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    null
}

/** The source's lyrics, or why there are none: a 404 or a blank body confirms "no lyrics". */
private data class SourceLyrics(val text: String?, val confirmedMissing: Boolean)

private suspend fun sourceLyrics(api: CatalogMusicApi, source: String, song: String): SourceLyrics {
    val response = try {
        api.lyrics(source, song, refresh = true)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        return SourceLyrics(null, confirmedMissing = false)
    }
    val text = if (response.isSuccessful) response.body()?.lrc?.takeIf(String::isNotBlank) else null
    return SourceLyrics(text, confirmedMissing = text == null && (response.isSuccessful || response.code() == 404))
}

internal fun publicMusicWriteArtists(
    stored: List<String>?,
    localArtist: String,
    detail: List<String>?,
    placeholders: Set<String>,
): List<String>? {
    stored?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)?.let { return it }
    localArtist.trim().takeIf { it.isNotEmpty() && it !in placeholders }?.let { return listOf(it) }
    return detail?.filter(String::isNotBlank)?.takeIf(List<String>::isNotEmpty)
}

internal suspend fun syncPublicMusicMetadata(
    api: CatalogMusicApi,
    source: String,
    song: String,
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    artworkUrl: String?,
    manual: Boolean,
    artistPlaceholders: Set<String>,
): PublicMusicMetadataSyncResult {
    var stored: PublicMusicSongDto? = null
    if (!manual) {
        val cached = api.metadata(source, song)
        if (cached.isSuccessful) {
            stored = cached.body()
            if (!stored?.lyricsLrc.isNullOrBlank()) return PublicMusicMetadataSyncResult(stored)
        }
        if (!cached.isSuccessful && cached.code() != 404) {
            throw PublicMusicException(PublicMusicException.Kind.METADATA_READ_FAILED, cached.code())
        }
    }
    val detail = runCatching { api.song(source, song, refresh = true).body() }.getOrNull()
    val fetched = sourceLyrics(api, source, song)
    val lyrics = fetched.text
    if (stored != null && detail == null && lyrics == null) {
        return PublicMusicMetadataSyncResult(stored, lyricsMissing = fetched.confirmedMissing && stored.lyricsLrc.isNullOrBlank())
    }
    // issue 166: a manual refresh skips the stored record, but must not erase lyrics that a fallback
    // or a user chose when the song's own source still has none.
    val keptLyrics = if (manual && lyrics == null) storedLyrics(api, source, song) else null

    val resolvedTitle = stored?.title ?: title.ifBlank { detail?.title.orEmpty() }
    if (resolvedTitle.isBlank()) return PublicMusicMetadataSyncResult(null)
    val resolvedAlbum = stored?.album ?: album.ifBlank { detail?.album.orEmpty() }.ifBlank { null }
    val resolvedDuration = stored?.durationMs ?: durationMs.takeIf { it > 0 } ?: detail?.durationMs
    val resolvedArtwork = stored?.artworkUrl ?: detail?.artworkUrl ?: artworkUrl
    val resolvedAvatar = stored?.artistAvatarUrl ?: detail?.artistAvatarUrl
    val resolvedLyrics = stored?.lyricsLrc ?: lyrics ?: keptLyrics
    val lyricsMissing = fetched.confirmedMissing && resolvedLyrics.isNullOrBlank()
    val resolvedQualities = stored?.qualities ?: detail?.qualities.orEmpty()

    val artists = publicMusicWriteArtists(stored?.artists, artist, detail?.artists, artistPlaceholders)
        ?: return PublicMusicMetadataSyncResult(
            PublicMusicSongDto(
                sourceId = source, externalId = song, title = resolvedTitle, artists = listOf(artist),
                album = resolvedAlbum, durationMs = resolvedDuration, artworkUrl = resolvedArtwork,
                artistAvatarUrl = resolvedAvatar, lyricsLrc = resolvedLyrics, qualities = resolvedQualities,
            ),
            writeSkipped = true,
            lyricsMissing = lyricsMissing,
        )
    val saved = api.saveMetadata(
        source,
        song,
        PublicMusicMetadataWriteDto(
            title = resolvedTitle, artists = artists, album = resolvedAlbum, durationMs = resolvedDuration,
            artworkUrl = resolvedArtwork, artistAvatarUrl = resolvedAvatar, lyricsLrc = resolvedLyrics,
            qualities = resolvedQualities, mode = if (manual || stored?.mode == "manual") "manual" else "auto",
        ),
    )
    if (!saved.isSuccessful) throw PublicMusicException(PublicMusicException.Kind.METADATA_SAVE_FAILED, saved.code())
    return PublicMusicMetadataSyncResult(saved.body(), lyricsMissing = lyricsMissing)
}
