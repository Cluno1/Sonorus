package io.github.cluno1.sonorus.features.catalog.domain

import java.time.LocalDate

/**
 * issue 124: rooms where several people pick and order the songs of one gathering.
 *
 * Every song sits in a section. Today a room has one unnamed default section and the UI shows a
 * plain list; admin room templates (named sections such as 开场/圣餐/结束) and section assignees
 * are a later phase that only adds data — [CatalogRoomSection.name], [CatalogRoomSection.assignee]
 * and [rankRoomTargets] are where it plugs in.
 */
data class CatalogRoomUser(
    val userId: String,
    /** Null when the user has no nickname; show [roomUserLabel] instead. */
    val displayName: String?,
)

data class CatalogRoomSectionSummary(
    val id: String,
    val name: String?,
    val position: Int,
    val assignee: CatalogRoomUser?,
    val itemCount: Int,
)

/** The asked-for Work is already in this room (list queried with a work id). */
data class CatalogRoomContainedWork(
    val itemId: String,
    val sectionId: String,
    val position: Int,
    val addedBy: CatalogRoomUser,
)

data class CatalogRoomSummary(
    val id: String,
    val name: String,
    val serviceDate: LocalDate,
    val createdBy: CatalogRoomUser,
    val itemCount: Int,
    val revision: Int,
    val canDelete: Boolean,
    val sections: List<CatalogRoomSectionSummary>,
    val containsWork: CatalogRoomContainedWork?,
)

data class CatalogRoomItem(
    val id: String,
    /** 1-based ordinal across the whole room (section order, then order inside the section). */
    val position: Int,
    val work: CatalogLibrarySearchItem,
    val addedBy: CatalogRoomUser,
    val canRemove: Boolean,
)

data class CatalogRoomSection(
    val id: String,
    val name: String?,
    val position: Int,
    val assignee: CatalogRoomUser?,
    val items: List<CatalogRoomItem>,
)

data class CatalogRoom(
    val id: String,
    val name: String,
    val serviceDate: LocalDate,
    val createdBy: CatalogRoomUser,
    val itemCount: Int,
    val revision: Int,
    val canDelete: Boolean,
    val sections: List<CatalogRoomSection>,
) {
    /** Only the default unnamed section: render a plain list without section headers. */
    val isPlainList: Boolean get() = sections.size == 1 && sections.single().name == null
}

/** One section's complete new order, as the reorder endpoint takes it. */
data class CatalogRoomSectionOrder(val sectionId: String, val itemIds: List<String>)

interface CatalogRoomsRepository {
    /** Rooms that have not expired, nearest gathering first; [workId] fills [CatalogRoomSummary.containsWork]. */
    suspend fun listRooms(workId: String? = null): Result<List<CatalogRoomSummary>>

    suspend fun room(roomId: String): Result<CatalogRoom>

    suspend fun createRoom(name: String, serviceDate: LocalDate, idempotencyKey: String): Result<CatalogRoom>

    /** Appends to [sectionId], or to the last section when null. Same key = same request (no duplicate). */
    suspend fun addRoomSong(
        roomId: String,
        workId: String,
        sectionId: String?,
        idempotencyKey: String,
    ): Result<CatalogRoom>

    suspend fun removeRoomSong(roomId: String, itemId: String): Result<CatalogRoom>

    /** Fails with [CatalogFailure.StaleRevision] when someone else changed the room first. */
    suspend fun reorderRoom(
        roomId: String,
        order: List<CatalogRoomSectionOrder>,
        expectedRevision: Int,
    ): Result<CatalogRoom>

    suspend fun deleteRoom(roomId: String): Result<Unit>
}

/** Nickname, or "<fallback> 1a2b" from the last characters of the id when the user has none. */
fun roomUserLabel(user: CatalogRoomUser, fallbackPrefix: String): String =
    user.displayName?.trim()?.takeIf(String::isNotEmpty)
        ?: "$fallbackPrefix ${user.userId.takeLast(4)}"

/** Where "add to room" may put a song: a room, or a room's section. */
data class CatalogRoomTarget(
    val room: CatalogRoomSummary,
    /** Null = the room's last section (the only one today). */
    val section: CatalogRoomSectionSummary?,
    /** The current user is assigned to [section] (phase 2; never true today). */
    val assignedToMe: Boolean,
)

/**
 * Order of the "add to room" sheet. Today: nearest gathering first, one target per room.
 * Phase 2 (section assignees): sections assigned to [currentUserId] come first, each as its own
 * target, then every room as before — recommendation only, every room stays selectable.
 */
fun rankRoomTargets(rooms: List<CatalogRoomSummary>, currentUserId: String?): List<CatalogRoomTarget> {
    val byDate = rooms.sortedWith(compareBy(CatalogRoomSummary::serviceDate, CatalogRoomSummary::name))
    val mine = if (currentUserId == null) {
        emptyList()
    } else {
        byDate.flatMap { room ->
            room.sections
                .filter { it.assignee?.userId == currentUserId }
                .map { CatalogRoomTarget(room, it, assignedToMe = true) }
        }
    }
    return mine + byDate.map { CatalogRoomTarget(it, section = null, assignedToMe = false) }
}

/** A row of the editable room list: a section header (only shown with named sections) or a song. */
sealed interface CatalogRoomRow {
    val key: String

    data class Header(val section: CatalogRoomSection) : CatalogRoomRow {
        override val key: String get() = "section:${section.id}"
    }

    data class Song(val item: CatalogRoomItem, val sectionId: String) : CatalogRoomRow {
        override val key: String get() = "item:${item.id}"
    }
}

/**
 * The room as an editable flat list, and the moves people make on it. Pure, so the ordering
 * rules (ordinals, crossing sections, the order sent to the server) are unit-tested.
 */
object CatalogRoomOrder {
    fun rows(room: CatalogRoom): List<CatalogRoomRow> = buildList {
        room.sections.sortedBy(CatalogRoomSection::position).forEach { section ->
            if (!room.isPlainList) add(CatalogRoomRow.Header(section))
            section.items.sortedBy(CatalogRoomItem::position)
                .forEach { add(CatalogRoomRow.Song(it, section.id)) }
        }
    }

    /** Drag move from [from] to [to] (indices in [rows]); a song takes the section of its new place. */
    fun move(rows: List<CatalogRoomRow>, from: Int, to: Int): List<CatalogRoomRow> {
        if (from == to || from !in rows.indices || to !in rows.indices) return rows
        if (rows[from] !is CatalogRoomRow.Song) return rows
        // With named sections nothing may sit above the first header.
        if (to == 0 && rows.first() is CatalogRoomRow.Header) return rows
        val moved = rows.toMutableList()
        moved.add(to, moved.removeAt(from))
        return reassignSections(moved)
    }

    /** One step up/down; passing a section header moves the song into the neighbouring section. */
    fun step(rows: List<CatalogRoomRow>, itemId: String, up: Boolean): List<CatalogRoomRow> {
        val index = rows.indexOfFirst { it is CatalogRoomRow.Song && it.item.id == itemId }
        if (index < 0) return rows
        val target = if (up) index - 1 else index + 1
        if (target !in rows.indices) return rows
        // Above the first header there is no section to move into.
        if (up && target == 0 && rows[0] is CatalogRoomRow.Header) return rows
        return move(rows, index, target)
    }

    fun canStep(rows: List<CatalogRoomRow>, itemId: String, up: Boolean): Boolean = step(rows, itemId, up) != rows

    /** 1-based ordinal of every song in list order. */
    fun ordinals(rows: List<CatalogRoomRow>): Map<String, Int> {
        var ordinal = 0
        return rows.filterIsInstance<CatalogRoomRow.Song>().associate { it.item.id to ++ordinal }
    }

    /** The complete order the server expects: every section, in order, with its songs. */
    fun toOrder(room: CatalogRoom, rows: List<CatalogRoomRow>): List<CatalogRoomSectionOrder> {
        val songs = rows.filterIsInstance<CatalogRoomRow.Song>()
        return room.sections.sortedBy(CatalogRoomSection::position).map { section ->
            CatalogRoomSectionOrder(section.id, songs.filter { it.sectionId == section.id }.map { it.item.id })
        }
    }

    private fun reassignSections(rows: List<CatalogRoomRow>): List<CatalogRoomRow> {
        var current = rows.firstNotNullOfOrNull { (it as? CatalogRoomRow.Song)?.sectionId }
        return rows.map { row ->
            when (row) {
                is CatalogRoomRow.Header -> row.also { current = it.section.id }
                is CatalogRoomRow.Song -> if (current == null || row.sectionId == current) row else row.copy(sectionId = current!!)
            }
        }
    }
}

/**
 * One tap, one write: while a room write is in flight further taps are ignored, so a double tap
 * or an impatient retry never adds a song twice or submits two orders built on the same revision.
 */
class CatalogRoomWriteGate {
    var running: Boolean = false
        private set

    fun tryStart(): Boolean {
        if (running) return false
        running = true
        return true
    }

    fun finish() {
        running = false
    }
}
