package io.github.cluno1.sonorus.features.catalog.data.remote

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.cluno1.sonorus.features.catalog.domain.AnnouncementBlock
import io.github.cluno1.sonorus.features.catalog.domain.AnnouncementDocument
import io.github.cluno1.sonorus.features.catalog.domain.AnnouncementInline
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementDetail
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementFetch
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementImagePolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAnnouncementSummary
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import retrofit2.Response
import java.time.Instant
import java.time.OffsetDateTime

/**
 * issue 154: server JSON to domain. The body parser must survive any JSON: a newer server may add
 * nodes, and an announcement must never be able to crash the app. Whatever it cannot draw is
 * skipped and reported through [AnnouncementDocument.hasUnsupportedContent].
 */
internal object CatalogAnnouncementMapper {
    private const val TAG = "CatalogAnnouncements"
    private const val MAX_DEPTH = 16

    /** 304 is not "successful" to Retrofit, so it is checked before the shared failure mapping. */
    fun fetch(response: Response<AnnouncementListDto>, failure: (Int) -> CatalogFailure): CatalogAnnouncementFetch {
        if (response.code() == 304) return CatalogAnnouncementFetch.NotModified
        if (!response.isSuccessful) throw failure(response.code())
        val body = response.body() ?: throw CatalogFailure.InvalidData("服务器返回空响应")
        return CatalogAnnouncementFetch.Fetched(page(body), response.headers()["ETag"])
    }

    fun failure(code: Int, shared: (Int) -> CatalogFailure): CatalogFailure =
        if (code == 404) CatalogFailure.AnnouncementUnavailable() else shared(code)

    fun page(dto: AnnouncementListDto): CatalogAnnouncementPage = CatalogAnnouncementPage(
        items = dto.items.orEmpty().mapNotNull { item ->
            summary(item.id, item.title, item.summary, item.importance, item.publishedAt, item.coverImage).also {
                if (it == null) Log.w(TAG, "skipped an announcement with missing fields")
            }
        },
        nextCursor = dto.nextCursor,
    )

    fun detail(dto: AnnouncementDetailDto): CatalogAnnouncementDetail {
        val summary = summary(dto.id, dto.title, dto.summary, dto.importance, dto.publishedAt, dto.coverImage)
            ?: throw CatalogFailure.InvalidData("公告缺少必要字段")
        return CatalogAnnouncementDetail(summary, document(dto.body))
    }

    private fun summary(
        id: String?,
        title: String?,
        summary: String?,
        importance: String?,
        publishedAt: String?,
        coverImage: String?,
    ): CatalogAnnouncementSummary? {
        if (id.isNullOrBlank() || title == null) return null
        val time = publishedAt?.let(::instant) ?: return null
        return CatalogAnnouncementSummary(
            id = id,
            title = title,
            summary = summary.orEmpty(),
            important = importance == "important",
            publishedAt = time,
            coverImagePath = coverImage?.takeIf(CatalogAnnouncementImagePolicy::isValidPath),
        )
    }

    private fun instant(value: String): Instant? = runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

    fun document(body: JsonObject?): AnnouncementDocument {
        if (body == null) return AnnouncementDocument(emptyList(), hasUnsupportedContent = true)
        val parser = BodyParser()
        val blocks = parser.blocks(body.array("content"), depth = 0)
        // The editor keeps an empty paragraph at the end so the caret has somewhere to go.
        return AnnouncementDocument(blocks.dropLastWhile(::isEmptyParagraph), parser.unsupported)
    }

    private fun isEmptyParagraph(block: AnnouncementBlock): Boolean =
        block is AnnouncementBlock.Paragraph && block.inlines.all { it.text.isBlank() }

    private class BodyParser {
        var unsupported = false
            private set

        fun blocks(nodes: JsonArray?, depth: Int): List<AnnouncementBlock> {
            if (nodes == null) return emptyList()
            if (depth > MAX_DEPTH) {
                unsupported = true
                return emptyList()
            }
            return nodes.mapNotNull { block(it, depth) }
        }

        private fun block(element: JsonElement, depth: Int): AnnouncementBlock? {
            val node = element.objectOrNull()
            val content = node?.array("content")
            return when (node?.string("type")) {
                "paragraph" -> AnnouncementBlock.Paragraph(inlines(content))
                "heading" -> AnnouncementBlock.Heading(inlines(content))
                "bulletList" -> AnnouncementBlock.BulletList(listItems(content, depth))
                "orderedList" -> AnnouncementBlock.OrderedList(
                    start = node.attrs()?.int("start")?.takeIf { it >= 1 } ?: 1,
                    items = listItems(content, depth),
                )
                "blockquote" -> AnnouncementBlock.Quote(blocks(content, depth + 1))
                "horizontalRule" -> AnnouncementBlock.Divider
                "image" -> image(node)
                else -> {
                    unsupported = true
                    null
                }
            }
        }

        private fun image(node: JsonObject): AnnouncementBlock? {
            val attrs = node.attrs()
            val path = attrs?.string("src")
            if (path == null || !CatalogAnnouncementImagePolicy.isValidPath(path)) {
                unsupported = true
                return null
            }
            return AnnouncementBlock.Image(
                path = path,
                alt = attrs.string("alt")?.takeIf(String::isNotBlank),
                width = attrs.int("width")?.takeIf { it > 0 },
                height = attrs.int("height")?.takeIf { it > 0 },
            )
        }

        private fun listItems(nodes: JsonArray?, depth: Int): List<List<AnnouncementBlock>> =
            nodes.orEmpty().mapNotNull { element ->
                val node = element.objectOrNull()
                if (node?.string("type") == "listItem") {
                    blocks(node.array("content"), depth + 1)
                } else {
                    unsupported = true
                    null
                }
            }

        private fun inlines(nodes: JsonArray?): List<AnnouncementInline> =
            nodes.orEmpty().mapNotNull { element ->
                val node = element.objectOrNull()
                when (node?.string("type")) {
                    "text" -> node.string("text")?.let { text(node, it) }
                    "hardBreak" -> AnnouncementInline("\n")
                    else -> {
                        unsupported = true
                        null
                    }
                }
            }

        private fun text(node: JsonObject, value: String): AnnouncementInline {
            var inline = AnnouncementInline(value)
            node.array("marks").orEmpty().forEach { element ->
                val mark = element.objectOrNull()
                when (mark?.string("type")) {
                    "bold" -> inline = inline.copy(bold = true)
                    "italic" -> inline = inline.copy(italic = true)
                    // Readable without the mark, so an unknown style does not ask for an update.
                    "link" -> inline = inline.copy(href = mark.attrs()?.string("href")?.takeIf(::isHttps))
                }
            }
            return inline
        }

        private fun isHttps(href: String) = href.startsWith("https://", ignoreCase = true)

        private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()
    }
}

private fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

private fun JsonObject.array(name: String): JsonArray? = get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.attrs(): JsonObject? = get("attrs")?.objectOrNull()

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonObject.int(name: String): Int? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asNumber?.toInt()
