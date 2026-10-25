package io.github.cluno1.sonorus.features.catalog.data.remote

import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoom
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomContainedWork
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomItem
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSection
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSectionOrder
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSectionSummary
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSummary
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomUser
import java.time.LocalDate

/** issue 124: validates `/v2/rooms` payloads into domain models; malformed data fails loudly. */
internal object CatalogRoomMapper {
    fun rooms(dto: RoomListDto): List<CatalogRoomSummary> = dto.items.orEmpty().map(::summary)

    fun room(dto: RoomDto): CatalogRoom {
        val sections = dto.sections.orEmpty().map(::section).sortedBy(CatalogRoomSection::position)
        require(sections.isNotEmpty()) { "room has no section" }
        return CatalogRoom(
            id = text(dto.id, "room.id"),
            name = text(dto.name, "room.name"),
            serviceDate = date(dto.serviceDate, "room.service_date"),
            createdBy = user(dto.createdBy, "room.created_by"),
            itemCount = nonNegative(dto.itemCount, "room.item_count"),
            revision = positive(dto.revision, "room.revision"),
            canDelete = dto.canDelete ?: false,
            sections = sections,
        )
    }

    fun order(order: List<CatalogRoomSectionOrder>): RoomOrderDto =
        RoomOrderDto(order.map { RoomOrderSectionDto(it.sectionId, it.itemIds) })

    fun user(dto: RoomUserDto?, field: String): CatalogRoomUser {
        requireNotNull(dto) { "$field is missing" }
        return CatalogRoomUser(
            userId = text(dto.userId, "$field.user_id"),
            displayName = dto.displayName?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun summary(dto: RoomSummaryDto): CatalogRoomSummary = CatalogRoomSummary(
        id = text(dto.id, "room.id"),
        name = text(dto.name, "room.name"),
        serviceDate = date(dto.serviceDate, "room.service_date"),
        createdBy = user(dto.createdBy, "room.created_by"),
        itemCount = nonNegative(dto.itemCount, "room.item_count"),
        revision = positive(dto.revision, "room.revision"),
        canDelete = dto.canDelete ?: false,
        sections = dto.sections.orEmpty().map { section ->
            CatalogRoomSectionSummary(
                id = text(section.id, "room_section.id"),
                name = section.name?.trim()?.takeIf(String::isNotEmpty),
                position = positive(section.position, "room_section.position"),
                assignee = section.assignee?.let { user(it, "room_section.assignee") },
                itemCount = nonNegative(section.itemCount, "room_section.item_count"),
            )
        }.sortedBy(CatalogRoomSectionSummary::position),
        containsWork = dto.containsWork?.let { contained ->
            CatalogRoomContainedWork(
                itemId = text(contained.itemId, "contains_work.item_id"),
                sectionId = text(contained.sectionId, "contains_work.section_id"),
                position = positive(contained.position, "contains_work.position"),
                addedBy = user(contained.addedBy, "contains_work.added_by"),
            )
        },
    )

    private fun section(dto: RoomSectionDto): CatalogRoomSection = CatalogRoomSection(
        id = text(dto.id, "room_section.id"),
        name = dto.name?.trim()?.takeIf(String::isNotEmpty),
        position = positive(dto.position, "room_section.position"),
        assignee = dto.assignee?.let { user(it, "room_section.assignee") },
        items = dto.items.orEmpty().map { item ->
            CatalogRoomItem(
                id = text(item.id, "room_item.id"),
                position = positive(item.position, "room_item.position"),
                work = CatalogDtoMapper.librarySearchItem(requireNotNull(item.work) { "room_item.work is missing" }),
                addedBy = user(item.addedBy, "room_item.added_by"),
                canRemove = item.canRemove ?: false,
            )
        }.sortedBy(CatalogRoomItem::position),
    )

    private fun text(value: String?, field: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) { "$field is missing" }

    private fun date(value: String?, field: String): LocalDate =
        runCatching { LocalDate.parse(text(value, field)) }.getOrElse { throw IllegalArgumentException("$field is not a date") }

    private fun positive(value: Int?, field: String): Int =
        requireNotNull(value) { "$field is missing" }.also { require(it > 0) { "$field must be positive" } }

    private fun nonNegative(value: Int?, field: String): Int =
        requireNotNull(value) { "$field is missing" }.also { require(it >= 0) { "$field must not be negative" } }
}
