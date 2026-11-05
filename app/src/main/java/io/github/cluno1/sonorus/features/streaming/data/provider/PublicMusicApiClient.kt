package io.github.cluno1.sonorus.features.streaming.data.provider

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.edit
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogApiClient
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogMusicApi
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicMetadataWriteDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicSongDto
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSongMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

data class PublicMusicPlaylist(
    val sourceId: String,
    val externalId: String,
    val name: String,
    val artworkUrl: String?,
    val songCount: Int,
)

/** The first-party, device-authenticated gateway replacing LX01 in private builds. */
class PublicMusicApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val credentials = CatalogCredentialsStore(context)
    private val prefs = context.getSharedPreferences("public_music_service", Context.MODE_PRIVATE)
    private var cachedApi: Pair<String, CatalogMusicApi>? = null

    // Cached songs can keep the placeholder of the language active when they were mapped.
    private val artistPlaceholders: Set<String> by lazy {
        listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE)
            .mapTo(mutableSetOf(appContext.getString(R.string.unknown_artist))) { locale ->
                val configuration = Configuration(appContext.resources.configuration).apply { setLocale(locale) }
                appContext.createConfigurationContext(configuration).getString(R.string.unknown_artist)
            }
    }

    private fun server(): String = credentials.loadDevice()?.serverUrl
        ?: throw PublicMusicException(PublicMusicException.Kind.DEVICE_NOT_REGISTERED)

    private fun api(): CatalogMusicApi {
        val url = server()
        synchronized(this) {
            cachedApi?.takeIf { it.first == url }?.let { return it.second }
            val created = CatalogApiClient(url, credentials).musicApi
            cachedApi = url to created
            return created
        }
    }

    fun isConnected(): Boolean = runCatching {
        prefs.getString("server_url", null) == server()
    }.getOrDefault(false)

    fun serverUrl(): String = if (isConnected()) server() else ""

    suspend fun connect(rawUrl: String): ProviderConnectionResult {
        val url = CatalogEndpoint.normalize(rawUrl)
        val device = credentials.loadDevice() ?: throw PublicMusicException(PublicMusicException.Kind.DEVICE_NOT_REGISTERED)
        if (device.serverUrl != url) throw PublicMusicException(PublicMusicException.Kind.SERVER_MISMATCH)
        val sources = api().sources()
        if (!sources.isSuccessful || sources.body()?.items.isNullOrEmpty()) {
            throw PublicMusicException(PublicMusicException.Kind.SERVICE_UNAVAILABLE, sources.code())
        }
        prefs.edit { putString("server_url", url) }
        return ProviderConnectionResult(device.userId, url)
    }

    fun disconnect() {
        prefs.edit { remove("server_url") }
    }

    suspend fun ping(): Boolean = isConnected() && api().sources().isSuccessful

    suspend fun searchSongs(query: String): List<ProviderSong> {
        val response = api().search(query)
        if (!response.isSuccessful) throw PublicMusicException(PublicMusicException.Kind.SEARCH_FAILED, response.code())
        return response.body()?.sources.orEmpty().flatMap { page -> page.items.mapNotNull(::toSong) }
    }

    suspend fun recommendedSongs(limit: Int): List<ProviderSong> {
        val sources = api().sources()
        if (!sources.isSuccessful) throw PublicMusicException(PublicMusicException.Kind.SOURCES_FAILED, sources.code())
        return sources.body()?.items.orEmpty().flatMap { source ->
            runCatching { api().recommend(source.id).body()?.items.orEmpty().mapNotNull(::toSong) }
                .getOrDefault(emptyList())
        }.take(limit)
    }

    suspend fun recommendedPlaylists(limit: Int): List<PublicMusicPlaylist> {
        val response = api().recommendedAlbums(limit)
        if (!response.isSuccessful) throw PublicMusicException(PublicMusicException.Kind.SOURCES_FAILED, response.code())
        return response.body()?.items.orEmpty()
            .filter { it.name.isNotBlank() && split("${it.sourceId}:${it.externalId}") != null }
            .map { PublicMusicPlaylist(it.sourceId, it.externalId, it.name, displayArtwork(it.artworkUrl), it.songCount) }
    }

    /** Loads at most [MAX_PLAYLIST_PAGES] pages so opening a large playlist stays fast. */
    suspend fun playlistSongs(sourceId: String, externalId: String): List<ProviderSong> {
        val first = api().playlist(sourceId, externalId, 1)
        if (!first.isSuccessful) throw PublicMusicException(PublicMusicException.Kind.SOURCES_FAILED, first.code())
        val page = first.body() ?: return emptyList()
        val rest = if (!page.hasMore) emptyList() else coroutineScope {
            (2..MAX_PLAYLIST_PAGES).map { number ->
                async {
                    runCatching { api().playlist(sourceId, externalId, number).body()?.items.orEmpty() }
                        .onFailure { Log.w(TAG, "Playlist page failed: source=$sourceId page=$number", it) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        return (page.items + rest).mapNotNull(::toSong).distinctBy { it.providerId }
    }

    fun streamUrl(providerId: String): String? {
        val pair = split(providerId) ?: return null
        return "${server()}${publicMusicStreamPath(pair.first, pair.second)}"
    }

    suspend fun metadata(
        providerId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        artworkUrl: String?,
        manual: Boolean = false,
    ): LanSongMetadata? {
        val (source, song) = split(providerId) ?: return null
        val result = syncPublicMusicMetadata(
            api(), source, song, title, artist, album, durationMs, artworkUrl, manual, artistPlaceholders,
        )
        if (result.writeSkipped) Log.w(TAG, "Metadata write skipped without a known artist: $providerId")
        return result.song?.let { toMetadata(providerId, it) }
    }

    suspend fun saveManual(
        providerId: String,
        title: String,
        artist: String,
        album: String,
        lyrics: String,
        artworkUrl: String?,
        artistAvatarUrl: String?,
    ): LanSongMetadata {
        val (source, song) = split(providerId) ?: throw PublicMusicException(PublicMusicException.Kind.INVALID_SONG_ID)
        val previous = api().metadata(source, song).body()
        val response = api().saveMetadata(source, song, PublicMusicMetadataWriteDto(
            title = title.trim(), artists = listOf(artist.trim()), album = album.trim().ifBlank { null },
            durationMs = previous?.durationMs, artworkUrl = artworkUrl, artistAvatarUrl = artistAvatarUrl,
            lyricsLrc = lyrics.takeIf(String::isNotBlank), qualities = previous?.qualities.orEmpty(), mode = "manual",
        ))
        if (!response.isSuccessful) throw PublicMusicException(PublicMusicException.Kind.MANUAL_SAVE_FAILED, response.code())
        return toMetadata(providerId, requireNotNull(response.body()))
    }

    private fun toSong(item: PublicMusicSongDto): ProviderSong? {
        if (split("${item.sourceId}:${item.externalId}") == null || item.title.isBlank()) return null
        return ProviderSong(
            providerId = "${item.sourceId}:${item.externalId}",
            title = item.title,
            artist = item.artists.joinToString(artistSeparator()).ifBlank { appContext.getString(R.string.unknown_artist) },
            album = item.album.orEmpty(),
            durationMs = item.durationMs ?: 0L,
            artworkUrl = displayArtwork(item.artworkUrl),
        )
    }

    private fun toMetadata(providerId: String, item: PublicMusicSongDto): LanSongMetadata = LanSongMetadata(
        trackId = providerId,
        fields = mapOf("title" to item.title, "artist" to item.artists.joinToString(artistSeparator()), "album" to item.album.orEmpty()),
        artworkUrl = displayArtwork(item.artworkUrl),
        plainLyrics = item.lyricsLrc?.takeUnless { it.contains(Regex("\\[\\d{2}:\\d{2}")) }.orEmpty(),
        syncedLyrics = item.lyricsLrc?.takeIf { it.contains(Regex("\\[\\d{2}:\\d{2}")) }.orEmpty(),
        lyricsSource = appContext.getString(R.string.settings_lan_music), lyricsExternalId = "",
        overriddenFields = setOf("title", "artist", "album"),
    )

    private fun artistSeparator(): String = appContext.getString(R.string.public_music_artist_separator)

    private fun displayArtwork(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (!value.startsWith("/v2/music/artwork?")) return value
        // The gallery image host is public; Media3 audio still uses the signed first-party gateway.
        return ("${server()}$value").toHttpUrlOrNull()?.queryParameter("url")
    }

    private fun split(providerId: String): Pair<String, String>? {
        val match = Regex("^([a-z][a-z0-9_-]{1,39}):([A-Za-z0-9_-]{1,80})$").matchEntire(providerId)
            ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private companion object {
        const val TAG = "PublicMusicApiClient"
        const val MAX_PLAYLIST_PAGES = 5
    }
}
