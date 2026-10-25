package io.github.cluno1.sonorus.features.catalog.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * issue 124: rooms. Reads are open to every enrolled device; writes need the `rooms:write` scope,
 * which device tokens receive on their next refresh. Every path stays under `v2/rooms`.
 */
internal interface CatalogRoomsApi {
    @GET("v2/rooms")
    suspend fun rooms(@Query("work_id") workId: String? = null): Response<RoomListDto>

    @GET("v2/rooms/{id}")
    suspend fun room(@Path("id") roomId: String): Response<RoomDto>

    @POST("v2/rooms")
    suspend fun createRoom(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: RoomCreateDto,
    ): Response<RoomDto>

    @DELETE("v2/rooms/{id}")
    suspend fun deleteRoom(@Path("id") roomId: String): Response<Void>

    @POST("v2/rooms/{id}/items")
    suspend fun addSong(
        @Path("id") roomId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: RoomItemCreateDto,
    ): Response<RoomDto>

    @DELETE("v2/rooms/{id}/items/{itemId}")
    suspend fun removeSong(@Path("id") roomId: String, @Path("itemId") itemId: String): Response<RoomDto>

    @PUT("v2/rooms/{id}/order")
    suspend fun reorder(
        @Path("id") roomId: String,
        @Header("If-Match") ifMatch: String,
        @Body body: RoomOrderDto,
    ): Response<RoomDto>
}
