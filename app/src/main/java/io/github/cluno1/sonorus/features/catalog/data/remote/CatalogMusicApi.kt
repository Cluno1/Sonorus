package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

internal data class PublicMusicSourceDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
)

internal data class PublicMusicSourcesDto(@SerializedName("items") val items: List<PublicMusicSourceDto>)

internal data class PublicMusicSongDto(
    @SerializedName("source_id") val sourceId: String,
    @SerializedName("external_id") val externalId: String,
    @SerializedName("title") val title: String = "",
    @SerializedName("artists") val artists: List<String> = emptyList(),
    @SerializedName("album") val album: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("artwork_url") val artworkUrl: String? = null,
    @SerializedName("artist_avatar_url") val artistAvatarUrl: String? = null,
    @SerializedName("lyrics_lrc") val lyricsLrc: String? = null,
    @SerializedName("qualities") val qualities: List<String> = emptyList(),
    @SerializedName("mode") val mode: String? = null,
    // issue 166: who chose the lyrics, and whether a lyrics PUT changed them.
    @SerializedName("lyrics_mode") val lyricsMode: String? = null,
    @SerializedName("lyrics_provider") val lyricsProvider: String? = null,
    @SerializedName("written") val written: Boolean? = null,
)

internal data class PublicMusicPageDto(
    @SerializedName("items") val items: List<PublicMusicSongDto>,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

internal data class PublicMusicRecommendedAlbumDto(
    @SerializedName("source_id") val sourceId: String,
    @SerializedName("external_id") val externalId: String,
    @SerializedName("name") val name: String = "",
    @SerializedName("artwork_url") val artworkUrl: String? = null,
    @SerializedName("song_count") val songCount: Int = 0,
)

internal data class PublicMusicRecommendedAlbumsDto(@SerializedName("items") val items: List<PublicMusicRecommendedAlbumDto>)
internal data class PublicMusicSearchDto(@SerializedName("sources") val sources: List<PublicMusicPageDto>)
internal data class PublicMusicLyricsDto(@SerializedName("lrc") val lrc: String)

internal data class PublicMusicMetadataWriteDto(
    @SerializedName("title") val title: String,
    @SerializedName("artists") val artists: List<String>,
    @SerializedName("album") val album: String?,
    @SerializedName("duration_ms") val durationMs: Long?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("artist_avatar_url") val artistAvatarUrl: String?,
    @SerializedName("lyrics_lrc") val lyricsLrc: String?,
    @SerializedName("qualities") val qualities: List<String>,
    @SerializedName("mode") val mode: String,
)

/** issue 167: one source's page of playlists matching a keyword. */
internal data class PublicMusicPlaylistSearchPageDto(
    @SerializedName("source_id") val sourceId: String = "",
    @SerializedName("items") val items: List<PublicMusicRecommendedAlbumDto> = emptyList(),
    @SerializedName("page") val page: Int = 1,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

internal data class PublicMusicPlaylistSearchDto(
    @SerializedName("sources") val sources: List<PublicMusicPlaylistSearchPageDto> = emptyList(),
)

/** issue 166: lyrics only; the backend keeps every other field of the song. */
internal data class PublicMusicLyricsWriteDto(
    @SerializedName("lyrics_lrc") val lyricsLrc: String,
    @SerializedName("mode") val mode: String,
    @SerializedName("provider") val provider: String?,
)

/** issue 166: lyrics adopted for a local song, shared by title, artist and duration. */
internal data class DeviceSongLyricsDto(
    @SerializedName("title") val title: String = "",
    @SerializedName("artist") val artist: String = "",
    @SerializedName("lyrics_lrc") val lyricsLrc: String = "",
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("provider") val provider: String? = null,
)

internal data class DeviceSongLyricsSaveDto(
    @SerializedName("written") val written: Boolean = false,
    @SerializedName("lyrics") val lyrics: DeviceSongLyricsDto? = null,
)

internal data class DeviceSongLyricsWriteDto(
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String?,
    @SerializedName("duration_ms") val durationMs: Long?,
    @SerializedName("lyrics_lrc") val lyricsLrc: String,
    @SerializedName("mode") val mode: String,
    @SerializedName("provider") val provider: String?,
)

internal interface CatalogMusicApi {
    @GET("v2/music/sources")
    suspend fun sources(): Response<PublicMusicSourcesDto>

    @GET("v2/music/search")
    suspend fun search(@Query("q") query: String, @Query("page") page: Int = 1): Response<PublicMusicSearchDto>

    @GET("v2/music/sources/{source}/recommend")
    suspend fun recommend(@Path("source") source: String, @Query("page") page: Int = 1): Response<PublicMusicPageDto>

    @GET("v2/music/recommended-albums")
    suspend fun recommendedAlbums(@Query("limit") limit: Int): Response<PublicMusicRecommendedAlbumsDto>

    @GET("v2/music/sources/{source}/playlists/{playlist}")
    suspend fun playlist(@Path("source") source: String, @Path("playlist") playlist: String, @Query("page") page: Int): Response<PublicMusicPageDto>

    @GET("v2/music/sources/{source}/songs/{song}/metadata")
    suspend fun metadata(@Path("source") source: String, @Path("song") song: String): Response<PublicMusicSongDto>

    @GET("v2/music/sources/{source}/songs/{song}")
    suspend fun song(@Path("source") source: String, @Path("song") song: String, @Query("refresh") refresh: Boolean = false): Response<PublicMusicSongDto>

    @GET("v2/music/sources/{source}/songs/{song}/lyrics")
    suspend fun lyrics(@Path("source") source: String, @Path("song") song: String, @Query("refresh") refresh: Boolean = false): Response<PublicMusicLyricsDto>

    @PUT("v2/music/sources/{source}/songs/{song}/metadata")
    suspend fun saveMetadata(@Path("source") source: String, @Path("song") song: String, @Body body: PublicMusicMetadataWriteDto): Response<PublicMusicSongDto>

    @PUT("v2/music/sources/{source}/songs/{song}/lyrics")
    suspend fun saveLyrics(@Path("source") source: String, @Path("song") song: String, @Body body: PublicMusicLyricsWriteDto): Response<PublicMusicSongDto>

    @GET("v2/music/playlists/search")
    suspend fun searchPlaylists(@Query("q") query: String, @Query("page") page: Int): Response<PublicMusicPlaylistSearchDto>

    @GET("v2/music/device-lyrics")
    suspend fun deviceLyrics(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("duration_ms") durationMs: Long?,
    ): Response<DeviceSongLyricsDto>

    @PUT("v2/music/device-lyrics")
    suspend fun saveDeviceLyrics(@Body body: DeviceSongLyricsWriteDto): Response<DeviceSongLyricsSaveDto>
}
