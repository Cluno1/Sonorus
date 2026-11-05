package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/** issue 154: `/v2/announcements` payloads. Registered in scripts/verify_catalog_json_contract.py. */
internal data class AnnouncementListDto(
    @SerializedName("items") val items: List<AnnouncementListItemDto>?,
    @SerializedName("next_cursor") val nextCursor: String?,
)

internal data class AnnouncementListItemDto(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("importance") val importance: String?,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("cover_image") val coverImage: String?,
)

internal data class AnnouncementDetailDto(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("importance") val importance: String?,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("cover_image") val coverImage: String?,
    @SerializedName("body") val body: JsonObject?,
)
