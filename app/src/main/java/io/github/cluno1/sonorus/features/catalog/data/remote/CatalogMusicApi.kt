package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

internal data class PublicMusicSourceDto(
    val id: String,
    val name: String,
)

internal data class PublicMusicSourcesDto(val items: List<PublicMusicSourceDto>)

internal data class PublicMusicSongDto(
    @SerializedName("source_id") val sourceId: String,
    @SerializedName("external_id") val externalId: String,
    val title: String = "",
    val artists: List<String> = emptyList(),
    val album: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("artwork_url") val artworkUrl: String? = null,
    @SerializedName("artist_avatar_url") val artistAvatarUrl: String? = null,
    @SerializedName("lyrics_lrc") val lyricsLrc: String? = null,
    val qualities: List<String> = emptyList(),
    val mode: String? = null,
)

internal data class PublicMusicPageDto(val items: List<PublicMusicSongDto>)
internal data class PublicMusicSearchDto(val sources: List<PublicMusicPageDto>)
internal data class PublicMusicLyricsDto(val lrc: String)

internal data class PublicMusicMetadataWriteDto(
    val title: String,
    val artists: List<String>,
    val album: String?,
    @SerializedName("duration_ms") val durationMs: Long?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("artist_avatar_url") val artistAvatarUrl: String?,
    @SerializedName("lyrics_lrc") val lyricsLrc: String?,
    val qualities: List<String>,
    val mode: String,
)

internal interface CatalogMusicApi {
    @GET("v2/music/sources")
    suspend fun sources(): Response<PublicMusicSourcesDto>

    @GET("v2/music/search")
    suspend fun search(@Query("q") query: String, @Query("page") page: Int = 1): Response<PublicMusicSearchDto>

    @GET("v2/music/sources/{source}/recommend")
    suspend fun recommend(@Path("source") source: String, @Query("page") page: Int = 1): Response<PublicMusicPageDto>

    @GET("v2/music/sources/{source}/songs/{song}/metadata")
    suspend fun metadata(@Path("source") source: String, @Path("song") song: String): Response<PublicMusicSongDto>

    @GET("v2/music/sources/{source}/songs/{song}")
    suspend fun song(@Path("source") source: String, @Path("song") song: String, @Query("refresh") refresh: Boolean = false): Response<PublicMusicSongDto>

    @GET("v2/music/sources/{source}/songs/{song}/lyrics")
    suspend fun lyrics(@Path("source") source: String, @Path("song") song: String, @Query("refresh") refresh: Boolean = false): Response<PublicMusicLyricsDto>

    @PUT("v2/music/sources/{source}/songs/{song}/metadata")
    suspend fun saveMetadata(@Path("source") source: String, @Path("song") song: String, @Body body: PublicMusicMetadataWriteDto): Response<PublicMusicSongDto>
}
