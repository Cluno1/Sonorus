package io.github.cluno1.sonorus.features.catalog.domain

data class WorkSummary(
    val id: String,
    val canonicalTitle: String,
    val language: String?,
    val status: String,
    val revision: Int,
    val aliases: List<WorkAlias>,
    val credits: List<WorkCredit>,
    val createdAt: String,
    val updatedAt: String,
    // issue 107: work-level lyrics for the lyrics detail page. Nullable lists keep bundles cached
    // by older app versions (deserialized by Gson without Kotlin defaults) readable.
    val lyrics: String? = null,
    val lyricsLanguage: String? = null,
    val lyricsTranslations: List<CatalogLyricsTranslation>? = emptyList(),
    val lyricsSourceImages: List<CatalogLyricSourceImage>? = emptyList(),
    val numberMeta: List<CatalogWorkNumber>? = emptyList(),
)

/** One hymnal number of a Work, e.g. key `ihope_num`, value 256. */
data class CatalogWorkNumber(
    val key: String,
    val value: Long,
)

enum class CatalogLibrarySearchType(val wireValue: String) {
    LYRICS("lyrics"),
    NUMBER("number"),
    SCORES("scores"),
    TAGS("tags"),
}

data class CatalogLibraryTag(
    val key: String,
    val label: String,
    val workCount: Int,
)

/** A playable release item of a Work returned by `/v2/library/search`. */
data class CatalogLibrarySearchSong(
    val renditionId: String,
    val albumId: String,
    val title: String,
    val trackNo: Int?,
)

data class CatalogLibrarySearchItem(
    val workId: String,
    val title: String,
    val matchField: String?,
    val matchLanguage: String?,
    val snippet: String?,
    val matchedNumber: CatalogWorkNumber?,
    val numbers: List<CatalogWorkNumber>,
    val hasLyrics: Boolean,
    val defaultScoreId: String?,
    val songs: List<CatalogLibrarySearchSong>,
)

data class CatalogLibrarySearchResult(
    val query: String,
    val type: CatalogLibrarySearchType,
    /** null, `opencc_t2s` or `opencc_s2t` when the server matched after a script conversion. */
    val fallback: String?,
    val items: List<CatalogLibrarySearchItem>,
    val tagItems: List<CatalogLibraryTag> = emptyList(),
)

data class CatalogTagWorksPage(
    val tag: CatalogLibraryTag,
    val items: List<CatalogLibrarySearchItem>,
    val nextCursor: String?,
)

data class WorkAlias(val namespace: String, val externalId: String)

data class WorkCredit(
    val id: String,
    val contributorId: String,
    val displayName: String,
    val role: String,
    val position: Int,
)

data class WorkBundle(
    val work: WorkSummary,
    val arrangements: List<Arrangement>,
    val bundleVersion: Int,
)

data class Arrangement(
    val id: String,
    val workId: String,
    val name: String,
    val voicing: String?,
    val keySignature: String?,
    val basedOnId: String?,
    val preferredScoreId: String?,
    val revision: Int,
    val parts: List<Part>,
    val scores: List<Score>,
    val renditions: List<Rendition>,
)

data class Part(
    val id: String,
    val code: String,
    val name: String,
    val displayOrder: Int,
    val midiChannel: Int?,
)

data class Score(
    val id: String,
    val arrangementId: String,
    val label: String,
    val origin: String,
    val derivedFromRevisionId: String?,
    val headRevisionId: String?,
    val publishedRevisionId: String?,
    val revision: Int,
)

data class ScoreRevision(
    val id: String,
    val scoreId: String,
    val revisionNo: Int,
    val basedOnRevisionId: String?,
    val editMessage: String?,
    val assets: List<ScoreRevisionAsset>,
    val createdAt: String,
) {
    val primaryMusicXml: ScoreRevisionAsset?
        get() = assets.singleOrNull { it.role == "primary_musicxml" }
}

data class ScoreRevisionAsset(
    val assetId: String,
    val role: String,
    val sha256: String,
    val byteSize: Long,
    val mediaType: String,
)

data class Rendition(
    val id: String,
    val arrangementId: String,
    val label: String,
    val kind: String,
    val ensemble: String?,
    val recordedAt: String?,
    val location: String?,
    val durationMs: Long?,
    val revision: Int,
    val assets: List<RenditionAsset>,
)

data class RenditionAsset(
    val id: String,
    val assetId: String,
    val role: String,
    val partId: String?,
    val codecProfile: String?,
    val sha256: String,
    val byteSize: Long,
    val mediaType: String,
)

data class PlaybackDescriptor(
    val renditionId: String,
    val assetId: String,
    val mediaType: String,
    val byteSize: Long,
    val delivery: String,
    val relativeUrl: String,
    val cacheKey: String,
    val etag: String,
    val supportsRange: Boolean,
    val expiresAt: String?,
)

data class AssetDeliveryDescriptor(
    val assetId: String,
    val mediaType: String,
    val byteSize: Long,
    val sha256: String,
    val delivery: String,
    val relativeUrl: String,
    val cacheKey: String,
    val etag: String,
    val supportsRange: Boolean,
    val expiresAt: String?,
)

data class CatalogPage(
    val items: List<WorkSummary>,
    val nextCursor: String?,
    val fromCache: Boolean = false,
)

data class CatalogChange(
    val sequence: Long,
    val entityType: String,
    val entityId: String,
    val entityRevision: Int,
    val operation: String,
    val workIds: List<String>,
    val tombstone: Boolean,
    val createdAt: String,
)

data class CatalogChanges(
    val changes: List<CatalogChange>,
    val nextCursor: Long,
    val hasMore: Boolean,
)

data class CatalogIssuedInvite(
    val inviteCode: String,
    val userId: String,
    val expiresAt: String,
)

/** A real-audio rendition projected by the server into the native Songs library. */
data class CatalogLibrarySong(
    val workId: String,
    val arrangementId: String,
    val renditionId: String,
    val renditionRevision: Int = 1,
    val albumId: String,
    val title: String,
    val artist: String?,
    val albumTitle: String,
    val durationMs: Long?,
    val trackNo: Int?,
    val coverUrl: String?,
    val lyrics: String?,
    val coverAssetId: String? = null,
    val lyricsLanguage: String? = null,
    val lyricsTranslations: List<CatalogLyricsTranslation>? = emptyList(),
    val lyricsFormats: List<CatalogLyricLanguageFormat>? = emptyList(),
    val lyricsSourceImages: List<CatalogLyricSourceImage>? = emptyList(),
)

data class CatalogLyricsTranslation(
    val language: String,
    val lyrics: String,
)

data class CatalogLyricLanguageFormat(
    val language: String,
    val format: String,
)

data class CatalogLyricsWriteResult(
    val renditionId: String,
    val renditionRevision: Int,
    val language: String,
    val lyrics: String,
    val format: String,
)

data class CatalogLyricsDraft(
    val namespace: String,
    val renditionId: String,
    val language: String,
    val lyrics: String,
    val format: String,
    val baseRevision: Int,
    val status: String,
    val updatedAtEpochMs: Long,
)

enum class CatalogLyricsSyncStatus {
    UNAVAILABLE,
    CLEAN,
    PENDING,
    SYNCING,
    SYNCED,
    CONFLICT,
    ERROR,
}

data class CatalogLyricsSyncState(
    val status: CatalogLyricsSyncStatus = CatalogLyricsSyncStatus.UNAVAILABLE,
    val message: String? = null,
    val currentRevision: Int? = null,
)

data class CatalogLyricSourceLanguageRelation(
    val language: String,
    val relation: String,
    val derivedFromLanguage: String? = null,
)

data class CatalogLyricSourceImage(
    val linkId: String,
    val sourcePageId: String,
    val imageAssetId: String,
    val documentId: String,
    val documentTitle: String,
    val sourceKind: String,
    val sourceRef: String?,
    val physicalPageNumber: Int,
    val displayLabel: String?,
    val displayOrder: Int,
    val widthPx: Int,
    val heightPx: Int,
    val renderDpi: Int,
    val ownerType: String,
    val ownerId: String,
    val languageRelations: List<CatalogLyricSourceLanguageRelation>,
    val note: String?,
)

/** A server-owned album/release. Work remains hidden from the native browsing surface. */
data class CatalogLibraryAlbum(
    val id: String,
    val key: String,
    val title: String,
    val artist: String?,
    val coverUrl: String?,
    val songCount: Int,
    val songs: List<CatalogLibrarySong> = emptyList(),
    val coverAssetId: String? = null,
)

data class CatalogScoreOption(
    val arrangementId: String,
    val arrangementName: String,
    val scoreId: String,
    val revisionId: String,
    val scoreLabel: String,
    val origin: String,
    val partCount: Int,
    val revisionNo: Int,
    val publishedAt: String,
    val preferred: Boolean,
)

data class CatalogLibraryScoreWork(
    val workId: String,
    val title: String,
    val artist: String?,
    val coverAssetId: String?,
    val coverUrl: String?,
    val defaultScoreId: String,
    val latestPublishedAt: String,
    val scoreCount: Int,
    val origins: List<String>,
    val scoreOptions: List<CatalogScoreOption>,
)

data class CatalogArtwork(
    val assetId: String,
    val mediaType: String,
    val sha256: String,
    val bytes: ByteArray,
)

data class CatalogLibrarySnapshot(
    val songs: List<CatalogLibrarySong>,
    val albums: List<CatalogLibraryAlbum>,
    val scoreWorks: List<CatalogLibraryScoreWork> = emptyList(),
    val fromCache: Boolean = false,
)

/** Stable projections consumed by Rhythm UI without turning catalog entities into legacy Songs. */
data class RhythmBrowseItem(
    val workId: String,
    val title: String,
    val subtitle: String,
)

data class RhythmNowPlayingItem(
    val workId: String,
    val arrangementId: String,
    val renditionId: String,
    val renditionRevision: Int = 1,
    val assetId: String?,
    val title: String,
    val subtitle: String,
    val lyrics: String? = null,
    val lyricsLanguage: String? = null,
    val lyricsTranslations: List<CatalogLyricsTranslation>? = emptyList(),
    val lyricsFormats: List<CatalogLyricLanguageFormat>? = emptyList(),
    val lyricsSourceImages: List<CatalogLyricSourceImage>? = emptyList(),
)

data class RhythmQueueEntry(
    val nowPlaying: RhythmNowPlayingItem,
    val playback: CatalogPlaybackItem,
)
