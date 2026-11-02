package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal data class AdminDeviceDto(
    @SerializedName("deviceId") val deviceId: String?,
    @SerializedName("userId") val userId: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("applicationId") val applicationId: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("isAdministrator") val isAdministrator: Boolean?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("lastSeenAt") val lastSeenAt: String?,
)

internal data class AdminDeviceListDto(
    @SerializedName("items") val items: List<AdminDeviceDto>?,
)

internal data class AdministratorChangeDto(
    @SerializedName("deviceId") val deviceId: String?,
    @SerializedName("isAdministrator") val isAdministrator: Boolean?,
)

internal interface CatalogAdminApi {
    @GET("v2/admin/devices")
    suspend fun devices(): Response<AdminDeviceListDto>

    @POST("v2/admin/devices/{id}/administrator")
    suspend fun grantAdministrator(@Path("id") deviceId: String): Response<AdministratorChangeDto>

    @DELETE("v2/admin/devices/{id}/administrator")
    suspend fun revokeAdministrator(@Path("id") deviceId: String): Response<AdministratorChangeDto>

    @POST("v2/admin/invites")
    suspend fun createInvite(@Body body: InviteRequest): Response<InviteDto>

}
