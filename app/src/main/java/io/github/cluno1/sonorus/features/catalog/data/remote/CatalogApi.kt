package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.http.Streaming
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.PUT

/** Second-phase API surface. Deliberately contains only GET and HEAD operations. */
internal interface CatalogApi {
    @GET("healthz")
    suspend fun health(): Response<HealthDto>

    @GET("v2/works")
    suspend fun works(
        @Query("q") query: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 50,
    ): Response<WorkPageDto>

    @GET("v2/library/songs")
    suspend fun librarySongs(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 200,
    ): Response<LibrarySongPageDto>

    @GET("v2/library/albums")
    suspend fun libraryAlbums(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 200,
    ): Response<LibraryAlbumPageDto>

    @GET("v2/library/score-works")
    suspend fun libraryScoreWorks(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 200,
    ): Response<LibraryScoreWorkPageDto>

    @GET("v2/library/search")
    suspend fun librarySearch(
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("limit") limit: Int = 50,
    ): Response<LibrarySearchDto>

    @GET("v2/library/tag-works")
    suspend fun tagWorks(
        @Query("tag") tagKey: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 50,
    ): Response<LibraryTagWorksPageDto>

    @GET("v2/library/albums/{id}")
    suspend fun libraryAlbum(@Path("id") albumId: String): Response<LibraryAlbumDetailDto>

    @GET("v2/works/{id}/bundle")
    suspend fun workBundle(
        @Path("id") workId: String,
        @Header("If-None-Match") etag: String? = null,
    ): Response<WorkBundleDto>

    @GET("v2/score-revisions/{id}")
    suspend fun scoreRevision(@Path("id") revisionId: String): Response<ScoreRevisionDto>

    @GET("v2/renditions/{id}/playback")
    suspend fun playback(
        @Path("id") renditionId: String,
        @Query("prefer") prefer: String = "stream",
    ): Response<PlaybackDto>

    @GET("v2/assets/{id}/delivery")
    suspend fun assetDelivery(@Path("id") assetId: String): Response<AssetDeliveryDto>

    @Streaming
    @GET
    suspend fun deliveredAsset(@Url url: String): Response<ResponseBody>

    @Streaming
    @GET("v2/assets/{id}/content")
    suspend fun asset(@Path("id") assetId: String): Response<ResponseBody>

    @HEAD("v2/assets/{id}/content")
    suspend fun headAsset(@Path("id") assetId: String): Response<Void>

    @GET("v2/sync/changes")
    suspend fun changes(
        @Query("after") after: Long,
        @Query("limit") limit: Int = 100,
    ): Response<ChangesDto>
}

/** The only device-writable Catalog surface. Keep general entity mutation out of the APK. */
internal interface CatalogLyricsWriteApi {
    @PUT("v2/renditions/{id}/lyrics/{language}")
    suspend fun replaceRenditionLyrics(
        @Path("id") renditionId: String,
        @Path("language") language: String,
        @Header("If-Match") ifMatch: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: RenditionLyricReplaceDto,
    ): Response<RenditionLyricWriteDto>
}

/** issue 116: the only Work write a device may make — the tag set; the response is the updated Work. */
internal interface CatalogWorkTagsWriteApi {
    @PATCH("v2/works/{id}/tags")
    suspend fun replaceWorkTags(
        @Path("id") workId: String,
        @Header("If-Match") ifMatch: String,
        @Body body: WorkTagsPatchDto,
    ): Response<WorkDto>
}

internal data class HealthDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("version") val version: String? = null,
)
