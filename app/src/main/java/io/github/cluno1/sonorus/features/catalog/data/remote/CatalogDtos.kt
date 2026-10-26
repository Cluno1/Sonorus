package io.github.cluno1.sonorus.features.catalog.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

internal data class WorkPageDto(
    @SerializedName("items") val items: List<WorkDto>?,
    @SerializedName("next_cursor") val nextCursor: String?,
)

internal data class LibrarySongPageDto(
    @SerializedName("items") val items: List<LibrarySongDto>?,
    @SerializedName("next_cursor") val nextCursor: String?,
)

internal data class LibraryAlbumPageDto(
    @SerializedName("items") val items: List<LibraryAlbumDto>?,
    @SerializedName("next_cursor") val nextCursor: String?,
)

internal data class LibraryScoreWorkPageDto(
    @SerializedName("items") val items: List<LibraryScoreWorkDto>?,
    @SerializedName("next_cursor") val nextCursor: String?,
)

internal data class LibraryScoreWorkDto(
    @SerializedName("work_id") val workId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("cover_asset_id") val coverAssetId: String?,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("default_score_id") val defaultScoreId: String?,
    @SerializedName("latest_published_at") val latestPublishedAt: String?,
    @SerializedName("score_count") val scoreCount: Int?,
    @SerializedName("origins") val origins: List<String>?,
    @SerializedName("score_options") val scoreOptions: List<LibraryScoreOptionDto>?,
)

internal data class LibraryScoreOptionDto(
    @SerializedName("arrangement_id") val arrangementId: String?,
    @SerializedName("arrangement_name") val arrangementName: String?,
    @SerializedName("score_id") val scoreId: String?,
    @SerializedName("revision_id") val revisionId: String?,
    @SerializedName("score_label") val scoreLabel: String?,
    @SerializedName("origin") val origin: String?,
    @SerializedName("part_count") val partCount: Int?,
    @SerializedName("revision_no") val revisionNo: Int?,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("preferred") val preferred: Boolean?,
)

internal data class LibraryAlbumDetailDto(
    @SerializedName("album") val album: LibraryAlbumDto?,
    @SerializedName("songs") val songs: List<LibrarySongDto>?,
)

internal data class LibrarySongDto(
    @SerializedName("work_id") val workId: String?,
    @SerializedName("arrangement_id") val arrangementId: String?,
    @SerializedName("rendition_id") val renditionId: String?,
    @SerializedName("rendition_revision") val renditionRevision: Int? = null,
    @SerializedName("album_id") val albumId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("album_title") val albumTitle: String?,
    @SerializedName("duration_ms") val durationMs: Long?,
    @SerializedName("track_no") val trackNo: Int?,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("lyrics") val lyrics: String?,
    @SerializedName("lyrics_language") val lyricsLanguage: String? = null,
    @SerializedName("lyrics_translations") val lyricsTranslations: List<LyricsTranslationDto>? = null,
    @SerializedName("cover_asset_id") val coverAssetId: String? = null,
    @SerializedName("lyrics_source_images") val lyricsSourceImages: List<LyricSourceImageDto>? = null,
    @SerializedName("lyric_source_count") val lyricSourceCount: Int? = null,
    @SerializedName("lyrics_formats") val lyricsFormats: List<LyricLanguageFormatDto>? = null,
)

internal data class LyricLanguageFormatDto(
    @SerializedName("language") val language: String?,
    @SerializedName("format") val format: String?,
)

internal data class RenditionLyricReplaceDto(
    @SerializedName("lyrics") val lyrics: String,
    @SerializedName("format") val format: String,
)

internal data class RenditionLyricWriteDto(
    @SerializedName("rendition_id") val renditionId: String?,
    @SerializedName("revision") val revision: Int?,
    @SerializedName("language") val language: String?,
    @SerializedName("lyrics") val lyrics: String?,
    @SerializedName("format") val format: String?,
    @SerializedName("lyrics_language") val lyricsLanguage: String?,
    @SerializedName("lyrics_translations") val lyricsTranslations: List<LyricsTranslationDto>?,
    @SerializedName("lyrics_formats") val lyricsFormats: List<LyricLanguageFormatDto>?,
)

internal data class LyricsTranslationDto(
    @SerializedName("language") val language: String?,
    @SerializedName("lyrics") val lyrics: String?,
)

internal data class LyricSourceLanguageRelationDto(
    @SerializedName("language") val language: String?,
    @SerializedName("relation") val relation: String?,
    @SerializedName("derived_from_language") val derivedFromLanguage: String?,
)

internal data class LyricSourceImageDto(
    @SerializedName("link_id") val linkId: String?,
    @SerializedName("source_page_id") val sourcePageId: String?,
    @SerializedName("image_asset_id") val imageAssetId: String?,
    @SerializedName("document_id") val documentId: String?,
    @SerializedName("document_title") val documentTitle: String?,
    @SerializedName("source_kind") val sourceKind: String?,
    @SerializedName("source_ref") val sourceRef: String?,
    @SerializedName("physical_page_number") val physicalPageNumber: Int?,
    @SerializedName("display_label") val displayLabel: String?,
    @SerializedName("display_order") val displayOrder: Int?,
    @SerializedName("width_px") val widthPx: Int?,
    @SerializedName("height_px") val heightPx: Int?,
    @SerializedName("render_dpi") val renderDpi: Int?,
    @SerializedName("owner_type") val ownerType: String?,
    @SerializedName("owner_id") val ownerId: String?,
    @SerializedName("language_relations") val languageRelations: List<LyricSourceLanguageRelationDto>?,
    @SerializedName("note") val note: String?,
)

internal data class LibraryAlbumDto(
    @SerializedName("id") val id: String?,
    @SerializedName("key") val key: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("song_count") val songCount: Int?,
    @SerializedName("cover_asset_id") val coverAssetId: String? = null,
)

internal data class WorkAliasDto(
    @SerializedName("namespace") val namespace: String?,
    @SerializedName("external_id") val externalId: String?,
)
internal data class WorkCreditDto(
    @SerializedName("id") val id: String?,
    @SerializedName("contributor_id") val contributorId: String?,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("position") val position: Int?,
)

internal data class WorkDto(
    @SerializedName("id") val id: String?,
    @SerializedName("canonical_title") val canonicalTitle: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("revision") val revision: Int?,
    @SerializedName("aliases") val aliases: List<WorkAliasDto>?,
    @SerializedName("credits") val credits: List<WorkCreditDto>?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("lyrics_source_images") val lyricsSourceImages: List<LyricSourceImageDto>? = null,
    // issue 107. Kept as raw JSON so an unexpected shape never fails the whole bundle.
    @SerializedName("lyrics") val lyrics: String? = null,
    @SerializedName("lyrics_language") val lyricsLanguage: String? = null,
    @SerializedName("lyrics_translations") val lyricsTranslations: JsonElement? = null,
    @SerializedName("number_meta") val numberMeta: JsonElement? = null,
    @SerializedName("tags") val tags: List<String>? = null,
)

/** `PATCH /v2/works/{id}/tags` body (issue 116): only the tag set, whole-array replace. */
/** issue 125: body of `PUT /v2/works/{id}/lyrics/{language}`. */
internal data class WorkLyricReplaceDto(
    @SerializedName("lyrics") val lyrics: String,
)

internal data class WorkTagsPatchDto(
    @SerializedName("tags") val tags: List<String>,
)

/** `GET /v2/library/search` response (issue 107). */
internal data class LibrarySearchDto(
    @SerializedName("query") val query: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("fallback") val fallback: String?,
    @SerializedName("items") val items: List<LibrarySearchItemDto>?,
    @SerializedName("tag_items") val tagItems: List<LibraryTagDto>? = null,
)

/** `GET /v2/library/tags` response (issue 121): every tag on active Works, most used first. */
internal data class LibraryTagListDto(
    @SerializedName("items") val items: List<LibraryTagDto>?,
)

internal data class LibraryTagDto(
    @SerializedName("tag_key") val tagKey: String?,
    @SerializedName("label") val label: String?,
    @SerializedName("work_count") val workCount: Int?,
)

internal data class LibraryTagWorksPageDto(
    @SerializedName("tag") val tag: LibraryTagDto?,
    @SerializedName("items") val items: List<LibrarySearchItemDto>?,
    @SerializedName("next_cursor") val nextCursor: String?,
)

internal data class LibrarySearchItemDto(
    @SerializedName("work_id") val workId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("match_field") val matchField: String? = null,
    @SerializedName("match_language") val matchLanguage: String? = null,
    @SerializedName("snippet") val snippet: String? = null,
    @SerializedName("matched_number") val matchedNumber: JsonElement? = null,
    @SerializedName("numbers") val numbers: JsonElement? = null,
    @SerializedName("has_lyrics") val hasLyrics: Boolean? = null,
    @SerializedName("default_score_id") val defaultScoreId: String? = null,
    @SerializedName("songs") val songs: List<LibrarySearchSongDto>? = null,
)

internal data class LibrarySearchSongDto(
    @SerializedName("rendition_id") val renditionId: String?,
    @SerializedName("album_id") val albumId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("track_no") val trackNo: Int? = null,
)

internal data class WorkBundleDto(
    @SerializedName("work") val work: WorkDto?,
    @SerializedName("arrangements") val arrangements: List<ArrangementDto>?,
    @SerializedName("bundle_version") val bundleVersion: Int?,
)

internal data class ArrangementDto(
    @SerializedName("id") val id: String?,
    @SerializedName("work_id") val workId: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("voicing") val voicing: String?,
    @SerializedName("key_signature") val keySignature: String?,
    @SerializedName("based_on_id") val basedOnId: String?,
    @SerializedName("preferred_score_id") val preferredScoreId: String?,
    @SerializedName("revision") val revision: Int?,
    @SerializedName("parts") val parts: List<PartDto>?,
    @SerializedName("scores") val scores: List<ScoreDto>?,
    @SerializedName("renditions") val renditions: List<RenditionDto>?,
)

internal data class PartDto(
    @SerializedName("id") val id: String?,
    @SerializedName("code") val code: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("display_order") val displayOrder: Int?,
    @SerializedName("midi_channel") val midiChannel: Int?,
)

internal data class ScoreDto(
    @SerializedName("id") val id: String?,
    @SerializedName("arrangement_id") val arrangementId: String?,
    @SerializedName("label") val label: String?,
    @SerializedName("origin") val origin: String?,
    @SerializedName("derived_from_revision_id") val derivedFromRevisionId: String?,
    @SerializedName("head_revision_id") val headRevisionId: String?,
    @SerializedName("published_revision_id") val publishedRevisionId: String?,
    @SerializedName("revision") val revision: Int?,
    @SerializedName("lyrics_source_images") val lyricsSourceImages: List<LyricSourceImageDto>? = null,
)

internal data class ScoreRevisionDto(
    @SerializedName("id") val id: String?,
    @SerializedName("score_id") val scoreId: String?,
    @SerializedName("revision_no") val revisionNo: Int?,
    @SerializedName("based_on_revision_id") val basedOnRevisionId: String?,
    @SerializedName("edit_message") val editMessage: String?,
    @SerializedName("assets") val assets: List<ScoreAssetDto>?,
    @SerializedName("created_at") val createdAt: String?,
)

internal data class ScoreAssetDto(
    @SerializedName("asset_id") val assetId: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("sha256") val sha256: String?,
    @SerializedName("byte_size") val byteSize: Long?,
    @SerializedName("media_type") val mediaType: String?,
)

internal data class RenditionDto(
    @SerializedName("id") val id: String?,
    @SerializedName("arrangement_id") val arrangementId: String?,
    @SerializedName("label") val label: String?,
    @SerializedName("kind") val kind: String?,
    @SerializedName("ensemble") val ensemble: String?,
    @SerializedName("recorded_at") val recordedAt: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("duration_ms") val durationMs: Long?,
    @SerializedName("revision") val revision: Int?,
    @SerializedName("assets") val assets: List<RenditionAssetDto>?,
    @SerializedName("lyrics_source_images") val lyricsSourceImages: List<LyricSourceImageDto>? = null,
)

internal data class RenditionAssetDto(
    @SerializedName("id") val id: String?,
    @SerializedName("asset_id") val assetId: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("part_id") val partId: String?,
    @SerializedName("codec_profile") val codecProfile: String?,
    @SerializedName("sha256") val sha256: String?,
    @SerializedName("byte_size") val byteSize: Long?,
    @SerializedName("media_type") val mediaType: String?,
)

internal data class PlaybackDto(
    @SerializedName("rendition_id") val renditionId: String?,
    @SerializedName("asset_id") val assetId: String?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("byte_size") val byteSize: Long?,
    @SerializedName("delivery") val delivery: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("cache_key") val cacheKey: String?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("supports_range") val supportsRange: Boolean?,
    @SerializedName("expires_at") val expiresAt: String?,
)

internal data class AssetDeliveryDto(
    @SerializedName("asset_id") val assetId: String?,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("byte_size") val byteSize: Long?,
    @SerializedName("sha256") val sha256: String?,
    @SerializedName("delivery") val delivery: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("cache_key") val cacheKey: String?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("supports_range") val supportsRange: Boolean?,
    @SerializedName("expires_at") val expiresAt: String?,
)

internal data class ChangesDto(
    @SerializedName("changes") val changes: List<ChangeDto>?,
    @SerializedName("next_cursor") val nextCursor: Long?,
    @SerializedName("has_more") val hasMore: Boolean?,
)

internal data class ChangeDto(
    @SerializedName("sequence") val sequence: Long?,
    @SerializedName("entity_type") val entityType: String?,
    @SerializedName("entity_id") val entityId: String?,
    @SerializedName("entity_revision") val entityRevision: Int?,
    @SerializedName("operation") val operation: String?,
    @SerializedName("work_ids") val workIds: List<String>?,
    @SerializedName("tombstone") val tombstone: Boolean?,
    @SerializedName("created_at") val createdAt: String?,
)
