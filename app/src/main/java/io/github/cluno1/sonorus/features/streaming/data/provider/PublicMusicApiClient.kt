package io.github.cluno1.sonorus.features.streaming.data.provider

import android.content.Context
import androidx.core.content.edit
import io.github.cluno1.sonorus.features.catalog.data.CatalogCredentialsStore
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogApiClient
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogMusicApi
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicMetadataWriteDto
import io.github.cluno1.sonorus.features.catalog.data.remote.PublicMusicSongDto
import io.github.cluno1.sonorus.features.streaming.domain.model.LanSongMetadata
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/** The first-party, device-authenticated gateway replacing LX01 in private builds. */
class PublicMusicApiClient(context: Context) {
    private val credentials = CatalogCredentialsStore(context)
    private val prefs = context.getSharedPreferences("public_music_service", Context.MODE_PRIVATE)
    private var cachedApi: Pair<String, CatalogMusicApi>? = null

    private fun server(): String = credentials.loadDevice()?.serverUrl
        ?: throw IllegalStateException("请先登记音乐库服务器和设备")

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
        val device = credentials.loadDevice() ?: error("请先用邀请码登记音乐库服务器")
        require(device.serverUrl == url) { "公共音乐服务地址须与已登记的音乐库服务器一致" }
        val sources = api().sources()
        check(sources.isSuccessful && !sources.body()?.items.isNullOrEmpty()) {
            "公共音乐服务不可用，请在 Web 管理台启用至少一个来源"
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
        if (!response.isSuccessful) throw IOException("音乐搜索失败（${response.code()}）")
        return response.body()?.sources.orEmpty().flatMap { page -> page.items.mapNotNull(::toSong) }
    }

    suspend fun recommendedSongs(limit: Int): List<ProviderSong> {
        val sources = api().sources()
        if (!sources.isSuccessful) throw IOException("音乐来源读取失败（${sources.code()}）")
        return sources.body()?.items.orEmpty().flatMap { source ->
            runCatching { api().recommend(source.id).body()?.items.orEmpty().mapNotNull(::toSong) }
                .getOrDefault(emptyList())
        }.take(limit)
    }

    fun streamUrl(providerId: String): String? {
        val pair = split(providerId) ?: return null
        return "${server()}/v2/music/sources/${pair.first}/songs/${pair.second}/stream?quality=mp3"
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
        var stored: PublicMusicSongDto? = null
        if (!manual) {
            val cached = api().metadata(source, song)
            if (cached.isSuccessful) {
                stored = cached.body()
                if (!stored?.lyricsLrc.isNullOrBlank()) return toMetadata(providerId, stored)
            }
            if (!cached.isSuccessful && cached.code() != 404) throw IOException("音乐元数据读取失败（${cached.code()}）")
        }
        val detail = runCatching { api().song(source, song, refresh = true).body() }.getOrNull()
        val lyrics = runCatching { api().lyrics(source, song, refresh = true).body()?.lrc }.getOrNull()
        if (stored != null && detail == null && lyrics == null) return toMetadata(providerId, stored)
        val body = PublicMusicMetadataWriteDto(
            title = stored?.title ?: title.ifBlank { detail?.title.orEmpty() },
            artists = stored?.artists ?: listOf(artist.ifBlank { detail?.artists?.firstOrNull().orEmpty() }.ifBlank { "未知歌手" }),
            album = stored?.album ?: album.ifBlank { detail?.album.orEmpty() }.ifBlank { null },
            durationMs = stored?.durationMs ?: durationMs.takeIf { it > 0 } ?: detail?.durationMs,
            artworkUrl = stored?.artworkUrl ?: detail?.artworkUrl ?: artworkUrl,
            artistAvatarUrl = stored?.artistAvatarUrl ?: detail?.artistAvatarUrl,
            lyricsLrc = stored?.lyricsLrc ?: lyrics,
            qualities = stored?.qualities ?: detail?.qualities.orEmpty(),
            mode = if (manual || stored?.mode == "manual") "manual" else "auto",
        )
        if (body.title.isBlank()) return null
        val saved = api().saveMetadata(source, song, body)
        if (!saved.isSuccessful) throw IOException("音乐元数据保存失败（${saved.code()}）")
        return saved.body()?.let { toMetadata(providerId, it) }
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
        val (source, song) = split(providerId) ?: error("歌曲 ID 无效")
        val previous = api().metadata(source, song).body()
        val response = api().saveMetadata(source, song, PublicMusicMetadataWriteDto(
            title = title.trim(), artists = listOf(artist.trim()), album = album.trim().ifBlank { null },
            durationMs = previous?.durationMs, artworkUrl = artworkUrl, artistAvatarUrl = artistAvatarUrl,
            lyricsLrc = lyrics.takeIf(String::isNotBlank), qualities = previous?.qualities.orEmpty(), mode = "manual",
        ))
        if (!response.isSuccessful) throw IOException("手动元数据保存失败（${response.code()}）")
        return toMetadata(providerId, requireNotNull(response.body()))
    }

    private fun toSong(item: PublicMusicSongDto): ProviderSong? {
        if (split("${item.sourceId}:${item.externalId}") == null || item.title.isBlank()) return null
        return ProviderSong(
            providerId = "${item.sourceId}:${item.externalId}",
            title = item.title,
            artist = item.artists.joinToString("、").ifBlank { "未知歌手" },
            album = item.album.orEmpty(),
            durationMs = item.durationMs ?: 0L,
            artworkUrl = displayArtwork(item.artworkUrl),
        )
    }

    private fun toMetadata(providerId: String, item: PublicMusicSongDto): LanSongMetadata = LanSongMetadata(
        trackId = providerId,
        fields = mapOf("title" to item.title, "artist" to item.artists.joinToString("、"), "album" to item.album.orEmpty()),
        artworkUrl = displayArtwork(item.artworkUrl),
        plainLyrics = item.lyricsLrc?.takeUnless { it.contains(Regex("\\[\\d{2}:\\d{2}")) }.orEmpty(),
        syncedLyrics = item.lyricsLrc?.takeIf { it.contains(Regex("\\[\\d{2}:\\d{2}")) }.orEmpty(),
        lyricsSource = "公共音乐聚合服务", lyricsExternalId = "",
        overriddenFields = setOf("title", "artist", "album"),
    )

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
}
