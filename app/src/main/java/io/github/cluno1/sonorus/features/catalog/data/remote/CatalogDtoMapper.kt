package io.github.cluno1.sonorus.features.catalog.data.remote

import io.github.cluno1.sonorus.features.catalog.domain.Arrangement
import io.github.cluno1.sonorus.features.catalog.domain.AssetDeliveryDescriptor
import io.github.cluno1.sonorus.features.catalog.domain.CatalogChange
import io.github.cluno1.sonorus.features.catalog.domain.CatalogChanges
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryAlbum
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryScoreWork
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceImage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricSourceLanguageRelation
import io.github.cluno1.sonorus.features.catalog.domain.CatalogScoreOption
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsTranslation
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricLanguageFormat
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsWriteResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackPolicy
import io.github.cluno1.sonorus.features.catalog.domain.Part
import io.github.cluno1.sonorus.features.catalog.domain.PlaybackDescriptor
import io.github.cluno1.sonorus.features.catalog.domain.Rendition
import io.github.cluno1.sonorus.features.catalog.domain.RenditionAsset
import io.github.cluno1.sonorus.features.catalog.domain.Score
import io.github.cluno1.sonorus.features.catalog.domain.ScoreRevision
import io.github.cluno1.sonorus.features.catalog.domain.ScoreRevisionAsset
import io.github.cluno1.sonorus.features.catalog.domain.WorkAlias
import io.github.cluno1.sonorus.features.catalog.domain.WorkBundle
import io.github.cluno1.sonorus.features.catalog.domain.WorkCredit
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
import io.github.cluno1.sonorus.features.catalog.domain.normalizeCatalogLyricsLanguageTag
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchItem
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchSong
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchType
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryTag
import io.github.cluno1.sonorus.features.catalog.domain.CatalogTagWorksPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogWorkNumber
import com.google.gson.JsonElement
import java.util.Locale
import java.util.UUID

internal object CatalogDtoMapper {
    private val sha256 = Regex("^[0-9a-fA-F]{64}$")
    private val lyricFormats = setOf("plain", "lrc", "enhanced_lrc", "ttml", "word_by_word_json")

    fun page(dto: WorkPageDto): CatalogPage = CatalogPage(
        items = dto.items.required("items").map(::work),
        nextCursor = dto.nextCursor,
    )

    fun librarySongs(dto: LibrarySongPageDto): Pair<List<CatalogLibrarySong>, String?> =
        dto.items.required("items").map(::librarySong) to dto.nextCursor

    fun libraryAlbums(dto: LibraryAlbumPageDto): Pair<List<CatalogLibraryAlbum>, String?> =
        dto.items.required("items").map(::libraryAlbum) to dto.nextCursor

    fun libraryScoreWorks(dto: LibraryScoreWorkPageDto): Pair<List<CatalogLibraryScoreWork>, String?> =
        dto.items.required("items").map { item ->
            val options = item.scoreOptions.required("score_work.score_options").map { option ->
                CatalogScoreOption(
                    arrangementId = uuid(option.arrangementId, "score_option.arrangement_id"),
                    arrangementName = text(option.arrangementName, "score_option.arrangement_name"),
                    scoreId = uuid(option.scoreId, "score_option.score_id"),
                    revisionId = uuid(option.revisionId, "score_option.revision_id"),
                    scoreLabel = text(option.scoreLabel, "score_option.score_label"),
                    origin = text(option.origin, "score_option.origin"),
                    partCount = nonNegative(option.partCount, "score_option.part_count"),
                    revisionNo = positive(option.revisionNo, "score_option.revision_no"),
                    publishedAt = text(option.publishedAt, "score_option.published_at"),
                    preferred = option.preferred.required("score_option.preferred"),
                )
            }
            val defaultScoreId = uuid(item.defaultScoreId, "score_work.default_score_id")
            require(options.isNotEmpty()) { "score work has no options" }
            require(options.any { it.scoreId == defaultScoreId }) { "default score is not an option" }
            require(options.distinctBy { it.scoreId }.size == options.size) { "score work has duplicate score ids" }
            CatalogLibraryScoreWork(
                workId = uuid(item.workId, "score_work.work_id"),
                title = text(item.title, "score_work.title"),
                artist = item.artist,
                coverAssetId = optionalUuid(item.coverAssetId, "score_work.cover_asset_id"),
                coverUrl = item.coverUrl,
                defaultScoreId = defaultScoreId,
                latestPublishedAt = text(item.latestPublishedAt, "score_work.latest_published_at"),
                scoreCount = positive(item.scoreCount, "score_work.score_count"),
                origins = item.origins.required("score_work.origins").map { text(it, "score_work.origin") },
                scoreOptions = options,
            ).also { require(it.scoreCount == options.size) { "score_count does not match options" } }
        } to dto.nextCursor

    fun libraryAlbumDetail(dto: LibraryAlbumDetailDto): CatalogLibraryAlbum {
        val album = libraryAlbum(dto.album.required("album"))
        val songs = dto.songs.required("songs").map(::librarySong)
        require(songs.all { it.albumId == album.id }) { "album detail contains a song from another album" }
        require(album.songCount == songs.size) { "album song_count does not match songs" }
        return album.copy(songs = songs)
    }

    fun work(dto: WorkDto): WorkSummary = WorkSummary(
        id = uuid(dto.id, "work.id"),
        canonicalTitle = text(dto.canonicalTitle, "work.canonical_title"),
        language = dto.language,
        status = text(dto.status, "work.status"),
        revision = positive(dto.revision, "work.revision"),
        aliases = dto.aliases.required("work.aliases").map {
            WorkAlias(text(it.namespace, "alias.namespace"), text(it.externalId, "alias.external_id"))
        },
        credits = dto.credits.required("work.credits").map {
            WorkCredit(
                id = uuid(it.id, "credit.id"),
                contributorId = uuid(it.contributorId, "credit.contributor_id"),
                displayName = text(it.displayName, "credit.display_name"),
                role = text(it.role, "credit.role"),
                position = positive(it.position, "credit.position"),
            )
        },
        createdAt = text(dto.createdAt, "work.created_at"),
        updatedAt = text(dto.updatedAt, "work.updated_at"),
        // issue 107 fields are display-only: malformed entries are dropped instead of failing
        // the bundle that score and playback screens depend on.
        lyrics = dto.lyrics?.takeIf(String::isNotBlank),
        lyricsLanguage = dto.lyricsLanguage?.let {
            runCatching { normalizeCatalogLyricsLanguageTag(it) }.getOrNull()
        },
        lyricsTranslations = workLyricsTranslations(dto.lyricsTranslations),
        lyricsSourceImages = dto.lyricsSourceImages.orEmpty()
            .mapNotNull { runCatching { lyricSourceImage(it) }.getOrNull() }
            .distinctBy { it.sourcePageId },
        numberMeta = workNumbers(dto.numberMeta),
        tags = dto.tags.orEmpty().map(String::trim).filter(String::isNotEmpty),
    )

    fun librarySearch(
        dto: LibrarySearchDto,
        requestedType: CatalogLibrarySearchType,
    ): CatalogLibrarySearchResult = CatalogLibrarySearchResult(
        query = dto.query.orEmpty(),
        type = CatalogLibrarySearchType.entries.firstOrNull { it.wireValue == dto.type } ?: requestedType,
        fallback = dto.fallback?.trim()?.takeIf(String::isNotEmpty),
        items = dto.items.required("items")
            .mapNotNull { runCatching { librarySearchItem(it) }.getOrNull() }
            .distinctBy { it.workId },
        tagItems = (if (requestedType == CatalogLibrarySearchType.TAGS) {
            dto.tagItems.required("tag_items")
        } else {
            dto.tagItems.orEmpty()
        })
            .mapNotNull { runCatching { libraryTag(it) }.getOrNull() }
            .distinctBy { it.key },
    )

    fun tagWorksPage(dto: LibraryTagWorksPageDto): CatalogTagWorksPage = CatalogTagWorksPage(
        tag = libraryTag(dto.tag.required("tag")),
        items = dto.items.required("items")
            .mapNotNull { runCatching { librarySearchItem(it) }.getOrNull() }
            .distinctBy { it.workId },
        nextCursor = dto.nextCursor?.takeIf(String::isNotBlank),
    )

    private fun libraryTag(dto: LibraryTagDto) = CatalogLibraryTag(
        key = text(dto.tagKey, "tag.tag_key"),
        label = text(dto.label, "tag.label"),
        workCount = nonNegative(dto.workCount, "tag.work_count"),
    )

    private fun librarySearchItem(dto: LibrarySearchItemDto) = CatalogLibrarySearchItem(
        workId = uuid(dto.workId, "search_item.work_id"),
        title = text(dto.title, "search_item.title"),
        matchField = dto.matchField?.trim()?.takeIf(String::isNotEmpty),
        matchLanguage = dto.matchLanguage?.let {
            runCatching { normalizeCatalogLyricsLanguageTag(it) }.getOrNull()
        },
        snippet = dto.snippet?.trim()?.takeIf(String::isNotEmpty),
        matchedNumber = workNumber(dto.matchedNumber),
        numbers = workNumbers(dto.numbers),
        hasLyrics = dto.hasLyrics ?: false,
        defaultScoreId = dto.defaultScoreId?.let {
            runCatching { uuid(it, "search_item.default_score_id") }.getOrNull()
        },
        songs = dto.songs.orEmpty().mapNotNull { song ->
            runCatching {
                CatalogLibrarySearchSong(
                    renditionId = uuid(song.renditionId, "search_song.rendition_id"),
                    albumId = uuid(song.albumId, "search_song.album_id"),
                    title = text(song.title, "search_song.title"),
                    trackNo = song.trackNo?.takeIf { it > 0 },
                )
            }.getOrNull()
        }.distinctBy { it.renditionId },
    )

    /** Accepts `[{"key": "ihope_num", "value": 256}]` and the stored `[{"ihope_num": 256}]` form. */
    internal fun workNumbers(element: JsonElement?): List<CatalogWorkNumber> {
        if (element == null || !element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull(::workNumber).distinctBy { it.key }
    }

    internal fun workNumber(element: JsonElement?): CatalogWorkNumber? {
        if (element == null || !element.isJsonObject) return null
        val json = element.asJsonObject
        if (json.has("key")) {
            val key = json.get("key").primitiveText()?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val value = json.get("value").primitiveLong() ?: return null
            return CatalogWorkNumber(key, value)
        }
        val entry = json.entrySet().singleOrNull() ?: return null
        val key = entry.key.trim().takeIf(String::isNotEmpty) ?: return null
        val value = entry.value.primitiveLong() ?: return null
        return CatalogWorkNumber(key, value)
    }

    /** Accepts a `[{"language", "lyrics"}]` list or a `{"language": "lyrics"}` object. */
    internal fun workLyricsTranslations(element: JsonElement?): List<CatalogLyricsTranslation> {
        val pairs: List<Pair<String?, String?>> = when {
            element == null -> emptyList()
            element.isJsonArray -> element.asJsonArray.mapNotNull { item ->
                if (!item.isJsonObject) return@mapNotNull null
                val json = item.asJsonObject
                json.get("language").primitiveText() to json.get("lyrics").primitiveText()
            }
            element.isJsonObject -> element.asJsonObject.entrySet().map { entry ->
                entry.key to entry.value.primitiveText()
            }
            else -> emptyList()
        }
        return pairs.mapNotNull { (language, lyrics) ->
            val tag = language?.let {
                runCatching { normalizeCatalogLyricsLanguageTag(it) }.getOrNull()
            } ?: return@mapNotNull null
            val text = lyrics?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            CatalogLyricsTranslation(tag, text)
        }.distinctBy { it.language.lowercase(Locale.ROOT) }
    }

    private fun JsonElement?.primitiveText(): String? =
        this?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonElement?.primitiveLong(): Long? =
        primitiveText()?.trim()?.toLongOrNull()?.takeIf { it >= 0 }

    fun bundle(dto: WorkBundleDto): WorkBundle {
        val mappedWork = work(dto.work.required("work"))
        val arrangements = dto.arrangements.required("arrangements").map { arrangementDto ->
            val arrangement = Arrangement(
                id = uuid(arrangementDto.id, "arrangement.id"),
                workId = uuid(arrangementDto.workId, "arrangement.work_id"),
                name = text(arrangementDto.name, "arrangement.name"),
                voicing = arrangementDto.voicing,
                keySignature = arrangementDto.keySignature,
                basedOnId = optionalUuid(arrangementDto.basedOnId, "arrangement.based_on_id"),
                preferredScoreId = optionalUuid(arrangementDto.preferredScoreId, "arrangement.preferred_score_id"),
                revision = positive(arrangementDto.revision, "arrangement.revision"),
                parts = arrangementDto.parts.required("arrangement.parts").map(::part),
                scores = arrangementDto.scores.required("arrangement.scores").map(::score),
                renditions = arrangementDto.renditions.required("arrangement.renditions").map(::rendition),
            )
            require(arrangement.workId == mappedWork.id) { "arrangement does not belong to work" }
            require(arrangement.scores.all { it.arrangementId == arrangement.id }) { "score does not belong to arrangement" }
            require(arrangement.renditions.all { it.arrangementId == arrangement.id }) { "rendition does not belong to arrangement" }
            val partIds = arrangement.parts.map { it.id }.toSet()
            require(arrangement.renditions.flatMap { it.assets }.all { it.partId == null || it.partId in partIds }) {
                "rendition asset refers to an unknown part"
            }
            arrangement
        }
        return WorkBundle(mappedWork, arrangements, nonNegative(dto.bundleVersion, "bundle_version"))
    }

    fun scoreRevision(dto: ScoreRevisionDto): ScoreRevision {
        val result = ScoreRevision(
            id = uuid(dto.id, "score_revision.id"),
            scoreId = uuid(dto.scoreId, "score_revision.score_id"),
            revisionNo = positive(dto.revisionNo, "score_revision.revision_no"),
            basedOnRevisionId = optionalUuid(dto.basedOnRevisionId, "score_revision.based_on_revision_id"),
            editMessage = dto.editMessage,
            assets = dto.assets.required("score_revision.assets").map {
                ScoreRevisionAsset(
                    assetId = uuid(it.assetId, "score_asset.asset_id"),
                    role = text(it.role, "score_asset.role"),
                    sha256 = hash(it.sha256, "score_asset.sha256"),
                    byteSize = positiveLong(it.byteSize, "score_asset.byte_size"),
                    mediaType = text(it.mediaType, "score_asset.media_type"),
                )
            },
            createdAt = text(dto.createdAt, "score_revision.created_at"),
        )
        require(result.assets.count { it.role == "primary_musicxml" } == 1) {
            "score revision must contain exactly one primary_musicxml"
        }
        return result
    }

    fun playback(dto: PlaybackDto): PlaybackDescriptor {
        val assetId = uuid(dto.assetId, "playback.asset_id")
        val normalizedHash = dto.cacheKey?.substringAfterLast(':')?.let { hash(it, "playback.cache_key hash") }
            ?: error("playback.cache_key is missing")
        val expectedCacheKey = "rhythm:asset:$assetId:$normalizedHash"
        require(dto.cacheKey == expectedCacheKey) { "playback.cache_key does not match asset identity" }
        val delivery = text(dto.delivery, "playback.delivery")
        require(delivery == "authenticated_url" || delivery == "signed_url") {
            "playback.delivery is not a supported delivery mode"
        }
        val mediaType = text(dto.mediaType, "playback.media_type")
        require(CatalogPlaybackPolicy.isPlayableMediaType(mediaType)) {
            "playback.media_type is not an allowed real-audio format"
        }
        return PlaybackDescriptor(
            renditionId = uuid(dto.renditionId, "playback.rendition_id"),
            assetId = assetId,
            mediaType = mediaType,
            byteSize = positiveLong(dto.byteSize, "playback.byte_size"),
            delivery = delivery,
            relativeUrl = text(dto.url, "playback.url"),
            cacheKey = expectedCacheKey,
            etag = text(dto.etag, "playback.etag"),
            supportsRange = dto.supportsRange.required("playback.supports_range"),
            expiresAt = dto.expiresAt,
        )
    }

    fun assetDelivery(dto: AssetDeliveryDto): AssetDeliveryDescriptor =
        delivery(dto).also {
            require(CatalogPlaybackPolicy.isMusicXmlMediaType(it.mediaType)) {
                "asset_delivery.media_type is not an allowed MusicXML format"
            }
        }

    fun artworkDelivery(dto: AssetDeliveryDto): AssetDeliveryDescriptor =
        delivery(dto).also {
            require(it.mediaType.substringBefore(';').trim().startsWith("image/")) {
                "asset_delivery.media_type is not an image"
            }
        }

    private fun delivery(dto: AssetDeliveryDto): AssetDeliveryDescriptor {
        val assetId = uuid(dto.assetId, "asset_delivery.asset_id")
        val normalizedHash = hash(dto.sha256, "asset_delivery.sha256")
        val expectedCacheKey = "rhythm:asset:$assetId:$normalizedHash"
        require(dto.cacheKey == expectedCacheKey) { "asset_delivery.cache_key does not match asset identity" }
        val delivery = text(dto.delivery, "asset_delivery.delivery")
        require(delivery == "authenticated_url" || delivery == "signed_url") {
            "asset_delivery.delivery is not supported"
        }
        val mediaType = text(dto.mediaType, "asset_delivery.media_type")
        return AssetDeliveryDescriptor(
            assetId = assetId,
            mediaType = mediaType,
            byteSize = positiveLong(dto.byteSize, "asset_delivery.byte_size"),
            sha256 = normalizedHash,
            delivery = delivery,
            relativeUrl = text(dto.url, "asset_delivery.url"),
            cacheKey = expectedCacheKey,
            etag = text(dto.etag, "asset_delivery.etag"),
            supportsRange = dto.supportsRange.required("asset_delivery.supports_range"),
            expiresAt = dto.expiresAt,
        )
    }

    fun changes(dto: ChangesDto): CatalogChanges = CatalogChanges(
        changes = dto.changes.required("changes").map {
            CatalogChange(
                sequence = positiveLong(it.sequence, "change.sequence"),
                entityType = text(it.entityType, "change.entity_type"),
                entityId = uuid(it.entityId, "change.entity_id"),
                entityRevision = nonNegative(it.entityRevision, "change.entity_revision"),
                operation = text(it.operation, "change.operation"),
                workIds = it.workIds.required("change.work_ids").map { id -> uuid(id, "change.work_id") },
                tombstone = it.tombstone.required("change.tombstone"),
                createdAt = text(it.createdAt, "change.created_at"),
            )
        },
        nextCursor = nonNegativeLong(dto.nextCursor, "next_cursor"),
        hasMore = dto.hasMore.required("has_more"),
    )

    fun renditionLyricsWrite(dto: RenditionLyricWriteDto): CatalogLyricsWriteResult =
        CatalogLyricsWriteResult(
            renditionId = uuid(dto.renditionId, "rendition_lyrics.rendition_id"),
            renditionRevision = positive(dto.revision, "rendition_lyrics.revision"),
            language = normalizeCatalogLyricsLanguageTag(
                text(dto.language, "rendition_lyrics.language"),
            ),
            lyrics = text(dto.lyrics, "rendition_lyrics.lyrics"),
            format = lyricFormat(dto.format, "rendition_lyrics.format"),
        )

    private fun part(dto: PartDto) = Part(
        uuid(dto.id, "part.id"), text(dto.code, "part.code"), text(dto.name, "part.name"),
        positive(dto.displayOrder, "part.display_order"), dto.midiChannel?.also { require(it in 1..16) },
    )

    private fun score(dto: ScoreDto) = Score(
        uuid(dto.id, "score.id"), uuid(dto.arrangementId, "score.arrangement_id"),
        text(dto.label, "score.label"), text(dto.origin, "score.origin"),
        optionalUuid(dto.derivedFromRevisionId, "score.derived_from_revision_id"),
        optionalUuid(dto.headRevisionId, "score.head_revision_id"),
        optionalUuid(dto.publishedRevisionId, "score.published_revision_id"),
        positive(dto.revision, "score.revision"),
    )

    private fun rendition(dto: RenditionDto) = Rendition(
        id = uuid(dto.id, "rendition.id"),
        arrangementId = uuid(dto.arrangementId, "rendition.arrangement_id"),
        label = text(dto.label, "rendition.label"), kind = text(dto.kind, "rendition.kind"),
        ensemble = dto.ensemble, recordedAt = dto.recordedAt, location = dto.location,
        durationMs = dto.durationMs?.also { require(it >= 0) },
        revision = positive(dto.revision, "rendition.revision"),
        assets = dto.assets.required("rendition.assets").map {
            RenditionAsset(
                id = uuid(it.id, "rendition_asset.id"),
                assetId = uuid(it.assetId, "rendition_asset.asset_id"),
                role = text(it.role, "rendition_asset.role"),
                partId = optionalUuid(it.partId, "rendition_asset.part_id"),
                codecProfile = it.codecProfile,
                sha256 = hash(it.sha256, "rendition_asset.sha256"),
                byteSize = positiveLong(it.byteSize, "rendition_asset.byte_size"),
                mediaType = text(it.mediaType, "rendition_asset.media_type"),
            )
        },
    )

    private fun librarySong(dto: LibrarySongDto): CatalogLibrarySong {
        val lyrics = dto.lyrics?.takeIf { it.isNotBlank() }
        val lyricsLanguage = lyrics?.let {
            normalizeCatalogLyricsLanguageTag(dto.lyricsLanguage ?: "und")
        }
        val lyricsTranslations = dto.lyricsTranslations.orEmpty().map { translation ->
            CatalogLyricsTranslation(
                language = normalizeCatalogLyricsLanguageTag(
                    text(translation.language, "library_song.lyrics_translation.language"),
                ),
                lyrics = text(translation.lyrics, "library_song.lyrics_translation.lyrics"),
            )
        }
        val languages = buildList {
            lyricsLanguage?.let(::add)
            addAll(lyricsTranslations.map(CatalogLyricsTranslation::language))
        }
        require(languages.distinctBy(String::lowercase).size == languages.size) {
            "library_song lyrics languages must be unique"
        }
        require(lyrics != null || lyricsTranslations.isEmpty()) {
            "library_song translations require default lyrics"
        }
        val formats = dto.lyricsFormats.orEmpty().map { item ->
            CatalogLyricLanguageFormat(
                language = normalizeCatalogLyricsLanguageTag(
                    text(item.language, "library_song.lyrics_format.language"),
                ),
                format = lyricFormat(item.format, "library_song.lyrics_format.format"),
            )
        }
        require(formats.distinctBy { it.language.lowercase() }.size == formats.size) {
            "library_song lyric formats must have unique languages"
        }
        val sourceImages = dto.lyricsSourceImages.orEmpty().map(::lyricSourceImage)
        require(sourceImages.distinctBy { it.sourcePageId }.size == sourceImages.size) {
            "library_song lyric source pages must be unique"
        }
        dto.lyricSourceCount?.let { count ->
            require(count == sourceImages.size) {
                "library_song lyric_source_count does not match lyrics_source_images"
            }
        }

        return CatalogLibrarySong(
            workId = uuid(dto.workId, "library_song.work_id"),
            arrangementId = uuid(dto.arrangementId, "library_song.arrangement_id"),
            renditionId = uuid(dto.renditionId, "library_song.rendition_id"),
            renditionRevision = positive(dto.renditionRevision ?: 1, "library_song.rendition_revision"),
            albumId = uuid(dto.albumId, "library_song.album_id"),
            title = text(dto.title, "library_song.title"),
            artist = dto.artist?.trim()?.takeIf { it.isNotEmpty() },
            albumTitle = text(dto.albumTitle, "library_song.album_title"),
            durationMs = dto.durationMs?.also {
                require(it >= 0) { "library_song.duration_ms must not be negative" }
            },
            trackNo = dto.trackNo?.also { require(it > 0) { "library_song.track_no must be positive" } },
            coverUrl = dto.coverUrl?.trim()?.takeIf { it.isNotEmpty() },
            lyrics = lyrics,
            coverAssetId = optionalUuid(dto.coverAssetId, "library_song.cover_asset_id"),
            lyricsLanguage = lyricsLanguage,
            lyricsTranslations = lyricsTranslations,
            lyricsFormats = formats,
            lyricsSourceImages = sourceImages,
        )
    }

    private fun lyricSourceImage(dto: LyricSourceImageDto): CatalogLyricSourceImage {
        val ownerType = text(dto.ownerType, "lyric_source.owner_type")
        require(ownerType in setOf("work", "score", "rendition")) {
            "lyric_source.owner_type is unsupported"
        }
        val sourceKind = text(dto.sourceKind, "lyric_source.source_kind")
        require(sourceKind in setOf("pdf", "scan", "photo", "booklet", "web")) {
            "lyric_source.source_kind is unsupported"
        }
        return CatalogLyricSourceImage(
            linkId = uuid(dto.linkId, "lyric_source.link_id"),
            sourcePageId = uuid(dto.sourcePageId, "lyric_source.source_page_id"),
            imageAssetId = uuid(dto.imageAssetId, "lyric_source.image_asset_id"),
            documentId = uuid(dto.documentId, "lyric_source.document_id"),
            documentTitle = text(dto.documentTitle, "lyric_source.document_title"),
            sourceKind = sourceKind,
            sourceRef = dto.sourceRef?.trim()?.takeIf(String::isNotEmpty),
            physicalPageNumber = positive(
                dto.physicalPageNumber,
                "lyric_source.physical_page_number",
            ),
            displayLabel = dto.displayLabel?.trim()?.takeIf(String::isNotEmpty),
            displayOrder = positive(dto.displayOrder, "lyric_source.display_order"),
            widthPx = positive(dto.widthPx, "lyric_source.width_px"),
            heightPx = positive(dto.heightPx, "lyric_source.height_px"),
            renderDpi = positive(dto.renderDpi, "lyric_source.render_dpi"),
            ownerType = ownerType,
            ownerId = uuid(dto.ownerId, "lyric_source.owner_id"),
            languageRelations = dto.languageRelations.orEmpty().map { relation ->
                CatalogLyricSourceLanguageRelation(
                    language = normalizeCatalogLyricsLanguageTag(
                        text(relation.language, "lyric_source.language"),
                    ),
                    relation = text(relation.relation, "lyric_source.relation").also {
                        require(it in setOf("printed", "transcribed", "translated", "transliterated", "converted")) {
                            "lyric_source.relation is unsupported"
                        }
                    },
                    derivedFromLanguage = relation.derivedFromLanguage?.let(
                        ::normalizeCatalogLyricsLanguageTag,
                    ),
                )
            },
            note = dto.note?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun libraryAlbum(dto: LibraryAlbumDto) = CatalogLibraryAlbum(
        id = uuid(dto.id, "library_album.id"),
        key = text(dto.key, "library_album.key"),
        title = text(dto.title, "library_album.title"),
        artist = dto.artist?.trim()?.takeIf { it.isNotEmpty() },
        coverUrl = dto.coverUrl?.trim()?.takeIf { it.isNotEmpty() },
        songCount = nonNegative(dto.songCount, "library_album.song_count"),
        coverAssetId = optionalUuid(dto.coverAssetId, "library_album.cover_asset_id"),
    )

    private fun uuid(value: String?, field: String): String = text(value, field).also {
        runCatching { UUID.fromString(it) }.getOrElse { throw IllegalArgumentException("$field is not a UUID") }
    }.lowercase()
    private fun optionalUuid(value: String?, field: String) = value?.let { uuid(it, field) }
    private fun hash(value: String?, field: String) = text(value, field).also {
        require(sha256.matches(it)) { "$field is not SHA-256" }
    }.lowercase()
    private fun lyricFormat(value: String?, field: String) = text(value, field).also {
        require(it in lyricFormats) { "$field is unsupported" }
    }
    private fun text(value: String?, field: String) = value?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("$field is missing")
    private fun positive(value: Int?, field: String) = value.required(field).also { require(it > 0) { "$field must be positive" } }
    private fun nonNegative(value: Int?, field: String) = value.required(field).also { require(it >= 0) { "$field must not be negative" } }
    private fun positiveLong(value: Long?, field: String) = value.required(field).also { require(it > 0) { "$field must be positive" } }
    private fun nonNegativeLong(value: Long?, field: String) = value.required(field).also { require(it >= 0) { "$field must not be negative" } }
    private fun <T> T?.required(field: String): T = this ?: throw IllegalArgumentException("$field is missing")
}
