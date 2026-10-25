package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.annotations.SerializedName

/** issue 124: `/v2/rooms` payloads. Registered in scripts/verify_catalog_json_contract.py. */
internal data class RoomCreateDto(
    @SerializedName("name") val name: String,
    @SerializedName("service_date") val serviceDate: String,
)

internal data class RoomItemCreateDto(
    @SerializedName("work_id") val workId: String,
    @SerializedName("section_id") val sectionId: String? = null,
)

internal data class RoomOrderDto(
    @SerializedName("sections") val sections: List<RoomOrderSectionDto>,
)

internal data class RoomOrderSectionDto(
    @SerializedName("section_id") val sectionId: String,
    @SerializedName("item_ids") val itemIds: List<String>,
)

internal data class RoomUserDto(
    @SerializedName("user_id") val userId: String?,
    @SerializedName("display_name") val displayName: String? = null,
)

internal data class RoomContainedWorkDto(
    @SerializedName("item_id") val itemId: String?,
    @SerializedName("section_id") val sectionId: String?,
    @SerializedName("position") val position: Int?,
    @SerializedName("added_by") val addedBy: RoomUserDto?,
)

internal data class RoomSectionSummaryDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String? = null,
    @SerializedName("position") val position: Int?,
    @SerializedName("assignee") val assignee: RoomUserDto? = null,
    @SerializedName("item_count") val itemCount: Int?,
)

internal data class RoomSummaryDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("service_date") val serviceDate: String?,
    @SerializedName("created_by") val createdBy: RoomUserDto?,
    @SerializedName("item_count") val itemCount: Int?,
    @SerializedName("revision") val revision: Int?,
    @SerializedName("can_delete") val canDelete: Boolean?,
    @SerializedName("sections") val sections: List<RoomSectionSummaryDto>?,
    @SerializedName("contains_work") val containsWork: RoomContainedWorkDto? = null,
)

internal data class RoomListDto(
    @SerializedName("items") val items: List<RoomSummaryDto>?,
)

internal data class RoomItemDto(
    @SerializedName("id") val id: String?,
    @SerializedName("position") val position: Int?,
    @SerializedName("work") val work: LibrarySearchItemDto?,
    @SerializedName("added_by") val addedBy: RoomUserDto?,
    @SerializedName("can_remove") val canRemove: Boolean?,
)

internal data class RoomSectionDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String? = null,
    @SerializedName("position") val position: Int?,
    @SerializedName("assignee") val assignee: RoomUserDto? = null,
    @SerializedName("items") val items: List<RoomItemDto>?,
)

internal data class RoomDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("service_date") val serviceDate: String?,
    @SerializedName("created_by") val createdBy: RoomUserDto?,
    @SerializedName("item_count") val itemCount: Int?,
    @SerializedName("revision") val revision: Int?,
    @SerializedName("can_delete") val canDelete: Boolean?,
    @SerializedName("sections") val sections: List<RoomSectionDto>?,
)

/** Problem body of `409` when the Work is already in the room. */
internal data class RoomConflictDto(
    @SerializedName("item_id") val itemId: String? = null,
    @SerializedName("added_by") val addedBy: RoomUserDto? = null,
)
