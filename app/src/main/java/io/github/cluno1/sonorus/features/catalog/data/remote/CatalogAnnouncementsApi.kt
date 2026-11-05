package io.github.cluno1.sonorus.features.catalog.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/** issue 154: devices only read announcements; publishing happens in the admin Web console. */
internal interface CatalogAnnouncementsApi {
    @GET("v2/announcements")
    suspend fun list(
        @Query("cursor") cursor: String? = null,
        @Header("If-None-Match") etag: String? = null,
    ): Response<AnnouncementListDto>

    @GET("v2/announcements/{id}")
    suspend fun detail(@Path("id") id: String): Response<AnnouncementDetailDto>
}
