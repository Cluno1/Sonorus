package io.github.cluno1.sonorus.features.catalog.domain

import java.time.Instant

/** issue 154: one published announcement as it appears in the list. */
data class CatalogAnnouncementSummary(
    val id: String,
    val title: String,
    val summary: String,
    val important: Boolean,
    val publishedAt: Instant,
    val coverImagePath: String?,
)

data class CatalogAnnouncementPage(
    val items: List<CatalogAnnouncementSummary>,
    val nextCursor: String?,
)

sealed interface CatalogAnnouncementFetch {
    /** The server answered 304: what the caller already holds is current. */
    data object NotModified : CatalogAnnouncementFetch

    data class Fetched(val page: CatalogAnnouncementPage, val etag: String?) : CatalogAnnouncementFetch
}

data class CatalogAnnouncementDetail(
    val summary: CatalogAnnouncementSummary,
    val document: AnnouncementDocument,
)

/**
 * [hasUnsupportedContent] means a newer server sent something this version cannot draw. The rest
 * still renders; the reader is told at the end that part of it needs an update.
 */
data class AnnouncementDocument(
    val blocks: List<AnnouncementBlock>,
    val hasUnsupportedContent: Boolean,
)

sealed interface AnnouncementBlock {
    data class Paragraph(val inlines: List<AnnouncementInline>) : AnnouncementBlock
    data class Heading(val inlines: List<AnnouncementInline>) : AnnouncementBlock
    data class BulletList(val items: List<List<AnnouncementBlock>>) : AnnouncementBlock
    data class OrderedList(val start: Int, val items: List<List<AnnouncementBlock>>) : AnnouncementBlock
    data class Quote(val blocks: List<AnnouncementBlock>) : AnnouncementBlock
    data object Divider : AnnouncementBlock
    data class Image(val path: String, val alt: String?, val width: Int?, val height: Int?) : AnnouncementBlock
}

data class AnnouncementInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val href: String? = null,
)

interface CatalogAnnouncementsRepository {
    /** Pass [etag] only for the first page; a later page is never compared. */
    suspend fun listAnnouncements(cursor: String? = null, etag: String? = null): Result<CatalogAnnouncementFetch>

    /** Fails with [CatalogFailure.AnnouncementUnavailable] once the announcement was taken down. */
    suspend fun announcement(id: String): Result<CatalogAnnouncementDetail>
}

object CatalogAnnouncementImagePolicy {
    private val PATH = Regex("^/v2/announcements/images/[a-f0-9]{64}\\.(?:jpg|png|webp|gif)$")

    fun isValidPath(path: String): Boolean = PATH.matches(path)

    /** Only our own content-addressed images load: no third party learns a reader's address. */
    fun url(serverUrl: String, path: String?): String? {
        if (path == null || !isValidPath(path) || serverUrl.isBlank()) return null
        return serverUrl.trimEnd('/') + path
    }
}
