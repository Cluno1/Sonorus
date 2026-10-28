package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.annotations.SerializedName
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyrics
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsItem
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsMatch
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSearchResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSource

/** `GET /v2/library/external-lyrics/search` response (issue 127). */
internal data class ExternalLyricsSearchDto(
    @SerializedName("query") val query: String?,
    @SerializedName("items") val items: List<ExternalLyricsItemDto>?,
    @SerializedName("unavailable_sources") val unavailableSources: List<String>? = null,
)

internal data class ExternalLyricsItemDto(
    @SerializedName("source") val source: String?,
    @SerializedName("external_id") val externalId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artists") val artists: List<String>?,
    @SerializedName("album") val album: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("match_field") val matchField: String? = null,
    @SerializedName("snippet") val snippet: String? = null,
)

/** `GET /v2/library/external-lyrics/{source}/{id}` response (issue 127). */
internal data class ExternalLyricsDto(
    @SerializedName("source") val source: String?,
    @SerializedName("external_id") val externalId: String?,
    @SerializedName("lyrics") val lyrics: String?,
    @SerializedName("translation") val translation: String? = null,
)

/** `GET /v2/library/external-lyrics/match` response (issue 127). */
internal data class ExternalLyricsMatchDto(
    @SerializedName("items") val items: List<ExternalLyricsMatchItemDto>?,
    @SerializedName("unavailable_sources") val unavailableSources: List<String>? = null,
)

internal data class ExternalLyricsMatchItemDto(
    @SerializedName("source") val source: String?,
    @SerializedName("external_id") val externalId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artists") val artists: List<String>?,
    @SerializedName("album") val album: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("lyrics") val lyrics: String?,
    @SerializedName("synced_lrc") val syncedLrc: String? = null,
    @SerializedName("translation") val translation: String? = null,
)

internal object CatalogExternalLyricsMapper {
    private val externalId = Regex("^[A-Za-z0-9]{1,64}$")

    /** Malformed rows are dropped; the rest of the page still shows. */
    fun search(dto: ExternalLyricsSearchDto): CatalogExternalLyricsSearchResult = CatalogExternalLyricsSearchResult(
        query = dto.query.orEmpty(),
        items = (dto.items ?: throw IllegalArgumentException("external lyrics response has no items"))
            .mapNotNull(::item)
            .distinctBy { it.key },
        unavailableSources = dto.unavailableSources.orEmpty().filter(String::isNotBlank),
    )

    fun lyrics(dto: ExternalLyricsDto): CatalogExternalLyrics {
        val source = requireNotNull(CatalogExternalLyricsSource.fromWire(dto.source)) { "unknown lyrics source" }
        val id = dto.externalId?.trim().orEmpty()
        require(externalId.matches(id)) { "invalid external lyrics id" }
        val lyrics = dto.lyrics?.trim().orEmpty()
        require(lyrics.isNotEmpty()) { "external lyrics are empty" }
        return CatalogExternalLyrics(
            source = source,
            externalId = id,
            lyrics = lyrics,
            translation = dto.translation?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    /** Candidates without lyrics or with a malformed identity are dropped. */
    fun match(dto: ExternalLyricsMatchDto): List<CatalogExternalLyricsMatch> =
        (dto.items ?: throw IllegalArgumentException("external lyrics match has no items"))
            .mapNotNull { item ->
                val source = CatalogExternalLyricsSource.fromWire(item.source) ?: return@mapNotNull null
                val id = item.externalId?.trim()?.takeIf(externalId::matches) ?: return@mapNotNull null
                val title = item.title?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val lyrics = item.lyrics?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                CatalogExternalLyricsMatch(
                    source = source,
                    externalId = id,
                    title = title,
                    artists = item.artists.orEmpty().map(String::trim).filter(String::isNotEmpty),
                    album = item.album?.trim()?.takeIf(String::isNotEmpty),
                    durationMs = item.durationMs?.takeIf { it > 0 },
                    lyrics = lyrics,
                    syncedLrc = item.syncedLrc?.trim()?.takeIf(String::isNotEmpty),
                    translation = item.translation?.trim()?.takeIf(String::isNotEmpty),
                )
            }
            .distinctBy { it.source to it.externalId }

    internal fun item(dto: ExternalLyricsItemDto): CatalogExternalLyricsItem? {
        val source = CatalogExternalLyricsSource.fromWire(dto.source) ?: return null
        val id = dto.externalId?.trim()?.takeIf(externalId::matches) ?: return null
        val title = dto.title?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return CatalogExternalLyricsItem(
            source = source,
            externalId = id,
            title = title,
            artists = dto.artists.orEmpty().map(String::trim).filter(String::isNotEmpty),
            album = dto.album?.trim()?.takeIf(String::isNotEmpty),
            durationMs = dto.durationMs?.takeIf { it > 0 },
            matchField = if (dto.matchField?.trim() == "title") "title" else "lyrics",
            snippet = dto.snippet?.trim()?.takeIf(String::isNotEmpty),
        )
    }
}
