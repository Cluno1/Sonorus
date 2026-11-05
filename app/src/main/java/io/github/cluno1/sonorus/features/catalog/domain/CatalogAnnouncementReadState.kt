package io.github.cluno1.sonorus.features.catalog.domain

/**
 * issue 154: what this device has seen. Kept on the device only, so no device write endpoint exists.
 *
 * [baselineEpochMillis] is set on first use: anything published earlier is not "unread", so a new
 * install is not greeted by the whole history. The important banner deliberately ignores it, since
 * an outage notice published yesterday is still worth one dismissible line to someone who just
 * updated, and at most one banner ever shows.
 */
data class AnnouncementReadState(
    val baselineEpochMillis: Long,
    val readIds: List<String> = emptyList(),
    val dismissedIds: List<String> = emptyList(),
) {
    fun isUnread(item: CatalogAnnouncementSummary): Boolean =
        item.id !in readIds && item.publishedAt.toEpochMilli() > baselineEpochMillis

    fun hasUnread(items: List<CatalogAnnouncementSummary>): Boolean = items.any(::isUnread)

    /** The newest important announcement not yet opened or dismissed. */
    fun banner(items: List<CatalogAnnouncementSummary>): CatalogAnnouncementSummary? = items
        .filter { it.important && it.id !in readIds && it.id !in dismissedIds }
        .maxByOrNull { it.publishedAt }

    fun withRead(id: String): AnnouncementReadState =
        if (id in readIds) this else copy(readIds = (readIds + id).takeLast(MAX_REMEMBERED))

    fun withDismissed(id: String): AnnouncementReadState =
        if (id in dismissedIds) this else copy(dismissedIds = (dismissedIds + id).takeLast(MAX_REMEMBERED))

    companion object {
        /** Announcements are rare; this is far beyond what the first page of the list can show. */
        const val MAX_REMEMBERED = 200
    }
}

interface AnnouncementReadPersistence {
    fun load(): AnnouncementReadState?
    fun save(state: AnnouncementReadState)
}
