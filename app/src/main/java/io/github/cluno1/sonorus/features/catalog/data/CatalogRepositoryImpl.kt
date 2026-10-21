package io.github.cluno1.sonorus.features.catalog.data

import android.content.Context
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogCache
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogOfflineCache
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogApiClient
import io.github.cluno1.sonorus.features.catalog.data.remote.AdminDeviceDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ChorusModerationRequestDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ChorusModerationSettingsPatchDto
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogDeviceAuthClient
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogDtoMapper
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import io.github.cluno1.sonorus.features.catalog.data.remote.ChorusAlignmentPatchDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ChorusDtoMapper
import io.github.cluno1.sonorus.features.catalog.data.remote.ChorusMixResolveDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ChorusSyncAnchorDto
import io.github.cluno1.sonorus.features.catalog.data.remote.ChorusTrackCreateDto
import io.github.cluno1.sonorus.features.catalog.domain.CatalogChanges
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAdminDashboard
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAdminDevice
import io.github.cluno1.sonorus.features.catalog.domain.CatalogArtwork
import io.github.cluno1.sonorus.features.catalog.domain.CatalogConnection
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogIssuedInvite
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsWriteResult
import io.github.cluno1.sonorus.features.catalog.domain.ChorusCatalog
import io.github.cluno1.sonorus.features.catalog.domain.ChorusMix
import io.github.cluno1.sonorus.features.catalog.domain.ChorusModerationItem
import io.github.cluno1.sonorus.features.catalog.domain.ChorusModerationSettings
import io.github.cluno1.sonorus.features.catalog.domain.ChorusPlayback
import io.github.cluno1.sonorus.features.catalog.domain.ChorusProject
import io.github.cluno1.sonorus.features.catalog.domain.ChorusSyncAnchor
import io.github.cluno1.sonorus.features.catalog.domain.ChorusTrack
import io.github.cluno1.sonorus.features.catalog.domain.ChorusTrackUpload
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryAlbum
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySnapshot
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchType
import io.github.cluno1.sonorus.features.catalog.domain.CatalogTagWorksPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackPolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRepository
import io.github.cluno1.sonorus.features.catalog.domain.PlaybackDescriptor
import io.github.cluno1.sonorus.features.catalog.domain.ScoreRevision
import io.github.cluno1.sonorus.features.catalog.domain.WorkBundle
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import android.net.Uri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class CatalogRepositoryImpl(context: Context) : CatalogRepository {
    private val applicationContext = context.applicationContext
    private val credentials = CatalogCredentialsStore(context)
    private val cache = CatalogCache(context)
    private val queueStore = CatalogQueueStore(context)
    private val offlineCache = CatalogOfflineCache(context)
    @Volatile private var passwordAdminToken: String? = null

    override fun connection(): CatalogConnection {
        val server = credentials.loadServerUrl().orEmpty()
        val reenrollmentRequired = credentials.isReenrollmentRequired()
        val deviceRegistered = credentials.loadDevice() != null && !reenrollmentRequired
        return CatalogConnection(
            server,
            server.isNotEmpty() &&
                (credentials.loadDevice() != null || !credentials.loadToken().isNullOrEmpty()),
            deviceRegistered,
            reenrollmentRequired,
            if (server.isEmpty()) "" else "$server|${credentials.loadDeviceId() ?: "legacy"}",
            credentials.loadDevice()?.userId,
            credentials.loadDeviceId(),
        )
    }

    override fun cachedWorks(): List<WorkSummary> = cache.loadWorks()
    override fun cachedBundle(workId: String): WorkBundle? = cache.loadBundle(workId)
    override fun cachedLibrary(): CatalogLibrarySnapshot? = cache.loadLibrary()

    override suspend fun enrollDevice(serverUrl: String, inviteCode: String): Result<Unit> = guarded {
        val normalized = CatalogEndpoint.normalize(serverUrl)
        val oldUrl = credentials.loadServerUrl()
        val oldIdentity = credentials.loadDeviceId() ?: credentials.loadToken()
        CatalogDeviceAuthClient(normalized, credentials).enroll(inviteCode)
        val newIdentity = credentials.loadDeviceId()
        if (oldUrl != null && (oldUrl != normalized || oldIdentity != newIdentity)) {
            cache.clearSession()
            queueStore.clear()
        }
    }

    override suspend fun issueInvite(
        serverUrl: String,
        username: String,
        password: String,
        userId: String,
        displayName: String?,
        replaceExistingDevice: Boolean,
    ): Result<CatalogIssuedInvite> = guarded {
        val normalized = CatalogEndpoint.normalize(serverUrl)
        CatalogDeviceAuthClient(normalized, credentials).issueInvite(
            username,
            password,
            userId,
            displayName,
            replaceExistingDevice,
        )
    }

    override suspend fun authenticateAdministrator(
        username: String,
        password: String,
    ): Result<List<CatalogAdminDevice>> = guarded {
        val server = credentials.loadServerUrl() ?: throw CatalogFailure.NotConfigured()
        val session = CatalogDeviceAuthClient(server, credentials)
            .authenticateAdministrator(username, password)
        passwordAdminToken = session.accessToken
        session.devices.items.orEmpty().map(::adminDevice)
    }

    override suspend fun setDeviceAdministrator(
        deviceId: String,
        enabled: Boolean,
    ): Result<List<CatalogAdminDevice>> = guarded {
        val id = validUuid(deviceId)
        val passwordToken = passwordAdminToken
        val items = if (passwordToken != null) {
            val server = credentials.loadServerUrl() ?: throw CatalogFailure.NotConfigured()
            CatalogDeviceAuthClient(server, credentials)
                .setAdministrator(passwordToken, id, enabled)
                .items
                .orEmpty()
        } else {
            val api = client().adminApi
            val changed = if (enabled) {
                api.grantAdministrator(id)
            } else {
                api.revokeAdministrator(id)
            }
            changed.bodyOrThrow()
            api.devices().bodyOrThrow().items.orEmpty()
        }
        if (enabled && id == credentials.loadDeviceId()) passwordAdminToken = null
        items.map(::adminDevice)
    }

    override suspend fun getAdminDashboard(): Result<CatalogAdminDashboard> = guarded {
        val api = client().adminApi
        val devices = api.devices().bodyOrThrow().items.orEmpty().map(::adminDevice)
        val settings = api.moderationSettings().bodyOrThrow().let {
            ChorusModerationSettings(
                automaticApproval = requireNotNull(it.automaticApproval),
                updatedBy = it.updatedBy.orEmpty(),
                updatedAt = it.updatedAt,
            )
        }
        val pendingTracks = api.moderationTracks().bodyOrThrow().items.orEmpty().map { item ->
            ChorusModerationItem(
                workId = requireNotNull(item.workId),
                projectTitle = requireNotNull(item.projectTitle),
                track = ChorusDtoMapper.track(requireNotNull(item.track)),
            )
        }
        CatalogAdminDashboard(devices, settings, pendingTracks)
    }

    override suspend fun issueInviteAsAdministrator(
        userId: String,
        displayName: String?,
        replaceExistingDevice: Boolean,
    ): Result<CatalogIssuedInvite> = guarded {
        client().adminApi.createInvite(
            io.github.cluno1.sonorus.features.catalog.data.remote.InviteRequest(
                userId = userId.trim(),
                displayName = displayName?.trim()?.takeIf(String::isNotEmpty),
                replaceExistingDevice = replaceExistingDevice,
            ),
        ).bodyOrThrow().let {
            CatalogIssuedInvite(
                inviteCode = requireNotNull(it.inviteCode),
                userId = requireNotNull(it.userId),
                expiresAt = requireNotNull(it.expiresAt),
            )
        }
    }

    override suspend fun setChorusAutomaticApproval(
        enabled: Boolean,
    ): Result<ChorusModerationSettings> = guarded {
        client().adminApi.updateModerationSettings(
            ChorusModerationSettingsPatchDto(enabled),
        ).bodyOrThrow().let {
            ChorusModerationSettings(
                automaticApproval = requireNotNull(it.automaticApproval),
                updatedBy = it.updatedBy.orEmpty(),
                updatedAt = it.updatedAt,
            )
        }
    }

    override suspend fun moderateChorusTrack(
        trackId: String,
        revision: Int,
        publish: Boolean,
        reason: String?,
    ): Result<ChorusTrack> = guarded {
        ChorusDtoMapper.track(
            client().adminApi.moderateTrack(
                trackId = validUuid(trackId),
                ifMatch = "\"rev-$revision\"",
                body = ChorusModerationRequestDto(
                    status = if (publish) "published" else "rejected",
                    reason = reason?.trim()?.takeIf(String::isNotEmpty),
                ),
            ).bodyOrThrow(),
        )
    }

    override fun clearConnection() {
        passwordAdminToken = null
        credentials.clear()
        cache.clearSession()
        queueStore.clear()
    }

    override suspend fun listWorks(query: String?, cursor: String?, limit: Int): Result<CatalogPage> = guarded {
        require(limit in 1..200) { "limit must be in 1..200" }
        val page = CatalogDtoMapper.page(client().api.works(query?.trim()?.takeIf { it.isNotEmpty() }, cursor, limit).bodyOrThrow())
        if (query.isNullOrBlank() && cursor == null) cache.saveWorks(page.items)
        page
    }.recoverCatching { error ->
        if (query.isNullOrBlank() && cursor == null && error is CatalogFailure.Unreachable) {
            val cached = cache.loadWorks()
            if (cached.isNotEmpty()) CatalogPage(cached, null, fromCache = true) else throw error
        } else throw error
    }

    override suspend fun getWorkBundle(workId: String, forceRefresh: Boolean): Result<WorkBundle> = guarded {
        val id = validUuid(workId)
        val etag = if (forceRefresh) null else cache.bundleEtag(id)
        val response = client().api.workBundle(id, etag)
        if (response.code() == 304) {
            cache.loadBundle(id) ?: throw CatalogFailure.InvalidData("服务器返回 304，但本地没有作品详情缓存")
        } else {
            val bundle = CatalogDtoMapper.bundle(response.bodyOrThrow())
            require(bundle.work.id == id) { "bundle work id does not match request" }
            cache.saveBundle(bundle, response.headers()["ETag"])
            bundle
        }
    }.recoverCatching { error ->
        if (error is CatalogFailure.Unreachable) cache.loadBundle(workId) ?: throw error else throw error
    }

    override suspend fun getScoreRevision(revisionId: String): Result<ScoreRevision> = guarded {
        val id = validUuid(revisionId)
        CatalogDtoMapper.scoreRevision(client().api.scoreRevision(id).bodyOrThrow()).also {
            require(it.id == id) { "score revision id does not match request" }
            cache.saveScoreRevision(it)
        }
    }.recoverCatching { error ->
        if (error is CatalogFailure.Unreachable) cache.loadScoreRevision(revisionId) ?: throw error else throw error
    }

    override suspend fun getPlayback(renditionId: String, prefer: String): Result<PlaybackDescriptor> = guarded {
        val id = validUuid(renditionId)
        val apiClient = client()
        val descriptor = CatalogDtoMapper.playback(apiClient.api.playback(id, prefer).bodyOrThrow())
        require(descriptor.renditionId == id) { "playback rendition id does not match request" }
        val absoluteUrl = if (descriptor.delivery == "signed_url") {
            // issue 11：signed_url 已是后端签发的 COS 绝对签名 URL，不做后端同源解析，
            // 由 CatalogPlaybackPolicy 校验其为 *.myqcloud.com 且带签名的受信 URL。
            descriptor.relativeUrl
        } else {
            apiClient.resolveAssetUrl(descriptor.relativeUrl).toString()
        }
        require(
            CatalogPlaybackPolicy.allows(
                mediaId = "rhythm-catalog:rendition:${descriptor.renditionId}:asset:${descriptor.assetId}",
                uri = absoluteUrl,
                customCacheKey = descriptor.cacheKey,
                mediaType = descriptor.mediaType,
                trustedServerUrl = connection().serverUrl,
            ),
        ) { "playback descriptor failed managed asset policy" }
        descriptor.copy(relativeUrl = absoluteUrl)
    }

    override suspend fun getLibrary(forceRefresh: Boolean): Result<CatalogLibrarySnapshot> = guarded {
        val api = client().api
        val songs = mutableListOf<io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySong>()
        var songCursor: String? = null
        val songCursorGuard = CatalogPaginationCursorGuard("library songs")
        do {
            val page = CatalogDtoMapper.librarySongs(api.librarySongs(songCursor).bodyOrThrow())
            songs += page.first
            songCursor = songCursorGuard.advance(page.second)
        } while (songCursor != null)

        val albums = mutableListOf<CatalogLibraryAlbum>()
        var albumCursor: String? = null
        val albumCursorGuard = CatalogPaginationCursorGuard("library albums")
        do {
            val page = CatalogDtoMapper.libraryAlbums(api.libraryAlbums(albumCursor).bodyOrThrow())
            albums += page.first
            albumCursor = albumCursorGuard.advance(page.second)
        } while (albumCursor != null)

        require(songs.distinctBy { it.renditionId }.size == songs.size) { "library contains duplicate rendition_id" }
        require(albums.distinctBy { it.id }.size == albums.size) { "library contains duplicate album id" }
        require(songs.all { song -> albums.any { it.id == song.albumId } }) { "library song refers to an unknown album" }

        val detailedAlbums = albums.map { summary ->
            val detail = CatalogDtoMapper.libraryAlbumDetail(api.libraryAlbum(summary.id).bodyOrThrow())
            require(detail.id == summary.id) { "album detail id does not match request" }
            require(detail.key == summary.key && detail.title == summary.title) {
                "album detail identity does not match album list"
            }
            detail
        }
        val detailSongs = detailedAlbums.flatMap { it.songs }
        require(detailSongs.map { it.renditionId }.toSet() == songs.map { it.renditionId }.toSet()) {
            "album details and songs list do not describe the same renditions"
        }
        val scoreWorks = mutableListOf<io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryScoreWork>()
        var scoreCursor: String? = null
        val scoreCursorGuard = CatalogPaginationCursorGuard("library score works")
        do {
            val page = CatalogDtoMapper.libraryScoreWorks(api.libraryScoreWorks(scoreCursor).bodyOrThrow())
            scoreWorks += page.first
            scoreCursor = scoreCursorGuard.advance(page.second)
        } while (scoreCursor != null)
        require(scoreWorks.distinctBy { it.workId }.size == scoreWorks.size) {
            "library contains duplicate score work ids"
        }
        val snapshot = CatalogLibrarySnapshot(songs, detailedAlbums, scoreWorks)
        cache.saveLibrary(snapshot)
        snapshot
    }.recoverCatching { error ->
        if (error is CatalogFailure.Unreachable) cache.loadLibrary() ?: throw error else throw error
    }

    override suspend fun getLibraryAlbum(albumId: String, forceRefresh: Boolean): Result<CatalogLibraryAlbum> = guarded {
        val id = validUuid(albumId)
        if (!forceRefresh) cache.loadLibraryAlbum(id)?.let { return@guarded it }
        CatalogDtoMapper.libraryAlbumDetail(client().api.libraryAlbum(id).bodyOrThrow()).also {
            require(it.id == id) { "album detail id does not match request" }
            cache.saveLibraryAlbum(it)
        }
    }.recoverCatching { error ->
        if (error is CatalogFailure.Unreachable) cache.loadLibraryAlbum(albumId) ?: throw error else throw error
    }

    override suspend fun searchLibrary(
        query: String,
        type: CatalogLibrarySearchType,
        limit: Int,
    ): Result<CatalogLibrarySearchResult> = guarded {
        val trimmed = query.trim()
        require(trimmed.length in 1..100) { "search query must be 1..100 characters" }
        require(limit in 1..100) { "limit must be in 1..100" }
        // Search results are live server data; there is deliberately no offline cache fallback.
        CatalogDtoMapper.librarySearch(
            client().api.librarySearch(trimmed, type.wireValue, limit).bodyOrThrow(),
            requestedType = type,
        )
    }

    override suspend fun listTagWorks(tagKey: String, cursor: String?, limit: Int): Result<CatalogTagWorksPage> = guarded {
        val key = tagKey.trim()
        require(key.isNotEmpty() && key.length <= 100) { "tag key must be 1..100 characters" }
        require(limit in 1..100) { "limit must be in 1..100" }
        CatalogDtoMapper.tagWorksPage(client().api.tagWorks(key, cursor, limit).bodyOrThrow()).also {
            require(it.tag.key == key) { "tag page key does not match request" }
        }
    }

    override suspend fun downloadArtwork(assetId: String): Result<CatalogArtwork> = guarded {
        val id = validUuid(assetId)
        val apiClient = client()
        val descriptor = CatalogDtoMapper.artworkDelivery(
            apiClient.api.assetDelivery(id).bodyOrThrow(),
        )
        require(descriptor.assetId == id) { "artwork delivery id does not match request" }
        val absoluteUrl = when (descriptor.delivery) {
            "signed_url" -> descriptor.relativeUrl.also {
                require(CatalogPlaybackPolicy.isSignedObjectStoreUrl(it)) {
                    "signed artwork delivery is not a trusted COS URL"
                }
            }
            else -> apiClient.resolveAssetUrl(descriptor.relativeUrl).toString()
        }
        val bytes = apiClient.api.deliveredAsset(absoluteUrl).bodyOrThrow().use { it.bytes() }
        require(bytes.size.toLong() == descriptor.byteSize) { "artwork byte size mismatch" }
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(actualHash == descriptor.sha256) { "artwork SHA-256 mismatch" }
        CatalogArtwork(
            assetId = descriptor.assetId,
            mediaType = descriptor.mediaType,
            sha256 = descriptor.sha256,
            bytes = bytes,
        )
    }

    override suspend fun downloadAsset(
        assetId: String,
        expectedSha256: String,
        expectedSize: Long,
    ): Result<ByteArray> = downloadScoreAsset(
        assetId = assetId,
        expectedSha256 = expectedSha256,
        expectedSize = expectedSize,
        revisionId = null,
    )

    private suspend fun downloadScoreAsset(
        assetId: String,
        expectedSha256: String,
        expectedSize: Long,
        revisionId: String?,
    ): Result<ByteArray> = guarded {
        val id = validUuid(assetId)
        require(expectedSha256.matches(Regex("^[0-9a-fA-F]{64}$"))) { "expected SHA-256 is invalid" }
        require(expectedSize > 0) { "expected size must be positive" }
        val namespace = cacheNamespace()
        offlineCache.readAsset(namespace, id, expectedSha256, expectedSize)?.let { return@guarded it }
        val apiClient = client()
        val descriptor = CatalogDtoMapper.assetDelivery(apiClient.api.assetDelivery(id).bodyOrThrow())
        require(descriptor.assetId == id) { "asset delivery id does not match request" }
        require(descriptor.sha256.equals(expectedSha256, ignoreCase = true)) { "asset delivery SHA-256 mismatch" }
        require(descriptor.byteSize == expectedSize) { "asset delivery size mismatch" }
        val absoluteUrl = when (descriptor.delivery) {
            "signed_url" -> descriptor.relativeUrl.also {
                require(CatalogPlaybackPolicy.isSignedObjectStoreUrl(it)) { "signed asset delivery is not a trusted COS URL" }
            }
            else -> apiClient.resolveAssetUrl(descriptor.relativeUrl).toString()
        }
        apiClient.api.deliveredAsset(absoluteUrl).bodyOrThrow().use { body ->
            offlineCache.store(
                namespace = namespace,
                kind = CatalogOfflineCache.KIND_SCORE,
                assetId = id,
                sha256 = expectedSha256,
                byteSize = expectedSize,
                mediaType = descriptor.mediaType,
                revisionId = revisionId,
                input = body.byteStream(),
            )
        }
        offlineCache.readAsset(namespace, id, expectedSha256, expectedSize)
            ?: throw IOException("cached score could not be read after download")
    }

    override suspend fun cachePlaybackAndLatestScore(
        workId: String,
        arrangementId: String,
        renditionId: String,
    ): Result<Unit> = guarded {
        val validWorkId = validUuid(workId)
        val validArrangementId = validUuid(arrangementId)
        val validRenditionId = validUuid(renditionId)
        val descriptor = getPlayback(validRenditionId).getOrThrow()
        cachePlayback(descriptor)

        val bundle = getWorkBundle(validWorkId, forceRefresh = true).getOrThrow()
        val arrangement = bundle.arrangements.firstOrNull { it.id == validArrangementId }
            ?: throw CatalogFailure.InvalidData("播放条目对应的编曲不存在")
        val score = arrangement.preferredScoreId
            ?.let { preferred -> arrangement.scores.firstOrNull { it.id == preferred } }
            ?.takeIf { it.headRevisionId != null || it.publishedRevisionId != null }
            ?: arrangement.scores.firstOrNull { it.headRevisionId != null || it.publishedRevisionId != null }
            ?: return@guarded Unit
        val revisionId = score.headRevisionId ?: score.publishedRevisionId ?: return@guarded Unit
        val revision = getScoreRevision(revisionId).getOrThrow()
        val asset = revision.primaryMusicXml
            ?: throw CatalogFailure.InvalidData("最新谱面修订没有 primary_musicxml")
        downloadScoreAsset(
            assetId = asset.assetId,
            expectedSha256 = asset.sha256,
            expectedSize = asset.byteSize,
            revisionId = revision.id,
        ).getOrThrow()
    }

    private suspend fun cachePlayback(descriptor: PlaybackDescriptor) {
        val hash = descriptor.cacheKey.substringAfterLast(':')
        require(hash.matches(Regex("^[0-9a-f]{64}$"))) { "playback cache key hash is invalid" }
        val namespace = cacheNamespace()
        if (offlineCache.findAudio(namespace, descriptor.renditionId)?.let {
                it.assetId == descriptor.assetId &&
                    it.sha256 == hash &&
                    it.byteSize == descriptor.byteSize
            } == true
        ) return
        val apiClient = client()
        apiClient.api.deliveredAsset(descriptor.relativeUrl).bodyOrThrow().use { body ->
            offlineCache.store(
                namespace = namespace,
                kind = CatalogOfflineCache.KIND_AUDIO,
                assetId = descriptor.assetId,
                sha256 = hash,
                byteSize = descriptor.byteSize,
                mediaType = descriptor.mediaType,
                renditionId = descriptor.renditionId,
                input = body.byteStream(),
            )
        }
    }

    override suspend fun syncChanges(): Result<CatalogChanges> = guarded {
        var cursor = cache.syncCursor()
        val all = mutableListOf<io.github.cluno1.sonorus.features.catalog.domain.CatalogChange>()
        var hasMore: Boolean
        do {
            val page = CatalogDtoMapper.changes(client().api.changes(cursor).bodyOrThrow())
            require(page.nextCursor >= cursor) { "sync cursor moved backwards" }
            all += page.changes
            cursor = page.nextCursor
            hasMore = page.hasMore
        } while (hasMore)
        val tombstonedWorks = all.filter { it.tombstone && it.entityType == "work" }.map { it.entityId }.toSet()
        cache.removeWorks(tombstonedWorks)
        cache.saveSyncCursor(cursor)
        CatalogChanges(all, cursor, false)
    }

    override suspend fun replaceRenditionLyrics(
        renditionId: String,
        language: String,
        lyrics: String,
        format: String,
        expectedRevision: Int,
        idempotencyKey: String,
    ): Result<CatalogLyricsWriteResult> = guarded {
        val id = validUuid(renditionId)
        val normalizedLanguage = io.github.cluno1.sonorus.features.catalog.domain
            .normalizeCatalogLyricsLanguageTag(language)
        require(lyrics.isNotBlank()) { "lyrics must not be blank" }
        require(lyrics.length <= 2 * 1024 * 1024) { "lyrics are too large" }
        require(format in setOf("plain", "lrc", "enhanced_lrc", "ttml", "word_by_word_json")) {
            "lyrics format is unsupported"
        }
        require(expectedRevision > 0) { "rendition revision must be positive" }
        require(idempotencyKey.isNotBlank()) { "idempotency key must not be blank" }
        val response = client().lyricsWriteApi.replaceRenditionLyrics(
            renditionId = id,
            language = normalizedLanguage,
            ifMatch = "\"rev-$expectedRevision\"",
            idempotencyKey = idempotencyKey,
            body = io.github.cluno1.sonorus.features.catalog.data.remote.RenditionLyricReplaceDto(
                lyrics = lyrics,
                format = format,
            ),
        )
        if (response.code() == 412) {
            val currentRevision = Regex("\\\"current_etag\\\"\\s*:\\s*\\\"rev-(\\d+)\\\"")
                .find(response.errorBody()?.string().orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            throw CatalogFailure.StaleRevision(currentRevision)
        }
        CatalogDtoMapper.renditionLyricsWrite(response.bodyOrThrow()).also {
            require(it.renditionId == id) { "lyrics write rendition id does not match request" }
            require(it.language.equals(normalizedLanguage, ignoreCase = true)) {
                "lyrics write language does not match request"
            }
        }
    }

    override suspend fun getChorus(workId: String): Result<ChorusCatalog> = guarded {
        val id = validUuid(workId)
        ChorusDtoMapper.catalog(client().chorusApi.workChorus(id).bodyOrThrow()).also {
            require(it.workId == id) { "chorus Work id does not match request" }
        }
    }

    override suspend fun getChorusProject(projectId: String): Result<ChorusProject> = guarded {
        val id = validUuid(projectId)
        ChorusDtoMapper.project(client().chorusApi.project(id).bodyOrThrow()).also {
            require(it.id == id) { "chorus project id does not match request" }
        }
    }

    override suspend fun uploadChorusTrack(
        projectId: String,
        upload: ChorusTrackUpload,
    ): Result<ChorusTrack> = guarded {
        val id = validUuid(projectId)
        require(upload.file.isFile && upload.file.length() in 1..100L * 1024 * 1024) {
            "录音文件不存在或超过 100 MiB"
        }
        require(upload.sha256.matches(Regex("^[0-9a-f]{64}$"))) { "录音 SHA-256 无效" }
        val digest = MessageDigest.getInstance("SHA-256")
        upload.file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        require(actualSha256 == upload.sha256) { "录音 SHA-256 与文件内容不一致" }
        val apiClient = client()
        val created = apiClient.chorusApi.createTrack(
            projectId = id,
            idempotencyKey = UUID.randomUUID().toString(),
            body = ChorusTrackCreateDto(
                chorusTimelineId = validUuid(upload.chorusTimelineId),
                partId = upload.partId,
                contributionKind = upload.contributionKind,
                displayLabel = upload.displayLabel,
                takeNo = upload.takeNo,
                sha256 = upload.sha256,
                byteSize = upload.file.length(),
                mediaType = upload.mediaType,
                originalFilename = upload.file.name,
                durationMs = upload.durationMs,
                initialAnchors = upload.initialAnchors.map { it.toDto() },
            ),
        ).bodyOrThrow()
        val track = ChorusDtoMapper.track(requireNotNull(created.track) {
            "chorus track create response has no track"
        })
        when (created.uploadStatus) {
            "reused" -> Unit
            "upload_required" -> {
                val target = requireNotNull(created.upload?.url) {
                    "chorus upload target is missing"
                }
                if (target.startsWith("https://")) {
                    apiClient.uploadChorusToSignedUrl(target, upload.file, upload.mediaType)
                } else {
                    require(target == "/v2/chorus-tracks/${track.id}/content") {
                        "chorus upload target changed its resource"
                    }
                    apiClient.chorusApi.uploadTrackContent(
                        track.id,
                        upload.sha256,
                        upload.file.asRequestBody(upload.mediaType.toMediaType()),
                    ).bodyOrThrow()
                }
            }
            else -> error("chorus upload status is invalid")
        }
        ChorusDtoMapper.track(
            apiClient.chorusApi.completeTrack(
                track.id,
                UUID.randomUUID().toString(),
            ).bodyOrThrow(),
        )
    }

    override suspend fun updateChorusTrackAlignment(
        trackId: String,
        revision: Int,
        offsetMs: Long,
        anchors: List<ChorusSyncAnchor>,
    ): Result<ChorusTrack> = guarded {
        require(revision > 0 && anchors.isNotEmpty()) { "对齐锚点无效" }
        ChorusDtoMapper.track(
            client().chorusApi.updateAlignment(
                validUuid(trackId),
                "\"rev-$revision\"",
                ChorusAlignmentPatchDto(offsetMs, anchors.map { it.toDto() }),
            ).bodyOrThrow(),
        )
    }

    override suspend fun submitChorusTrack(trackId: String): Result<ChorusTrack> = guarded {
        ChorusDtoMapper.track(
            client().chorusApi.submitTrack(
                validUuid(trackId),
                UUID.randomUUID().toString(),
            ).bodyOrThrow(),
        )
    }

    override suspend fun withdrawChorusTrack(trackId: String): Result<ChorusTrack> = guarded {
        ChorusDtoMapper.track(client().chorusApi.withdrawTrack(validUuid(trackId)).bodyOrThrow())
    }

    override suspend fun resolveChorusMix(
        projectId: String,
        chorusTimelineId: String,
        trackIds: List<String>,
    ): Result<ChorusMix> = guarded {
        require(trackIds.isNotEmpty() && trackIds.size <= 50) { "请选择 1 至 50 条合唱音轨" }
        val ids = trackIds.map(::validUuid).distinct()
        require(ids.size == trackIds.size) { "合唱音轨不能重复选择" }
        val apiClient = client()
        ChorusDtoMapper.mix(
            apiClient.chorusApi.resolveMix(
                validUuid(projectId),
                UUID.randomUUID().toString(),
                ChorusMixResolveDto(validUuid(chorusTimelineId), ids),
            ).bodyOrThrow(),
        ).resolvePlayback(apiClient)
    }

    override suspend fun getChorusMix(mixId: String): Result<ChorusMix> = guarded {
        val apiClient = client()
        ChorusDtoMapper.mix(
            apiClient.chorusApi.mix(validUuid(mixId)).bodyOrThrow(),
        ).resolvePlayback(apiClient)
    }

    private fun ChorusSyncAnchor.toDto() = ChorusSyncAnchorDto(
        anchorOrder = anchorOrder,
        scoreTick = scoreTick,
        mediaMs = mediaMs,
        confidence = confidence,
        source = source,
    )

    private suspend fun ChorusMix.resolvePlayback(apiClient: CatalogApiClient): ChorusMix {
        val item = playback ?: return this
        val resolved = when {
            item.url.startsWith("https://") -> item.url.also {
                require(CatalogPlaybackPolicy.isSignedObjectStoreUrl(it)) {
                    "chorus mix delivery is not a trusted signed COS URL"
                }
            }
            else -> cacheAuthenticatedChorusMix(apiClient, item)
        }
        return copy(playback = item.copy(url = resolved))
    }

    private suspend fun cacheAuthenticatedChorusMix(
        apiClient: CatalogApiClient,
        playback: ChorusPlayback,
    ): String {
        val directory = File(applicationContext.cacheDir, "chorus-mixes").apply { mkdirs() }
        val destination = File(directory, "${playback.sha256}.m4a")
        if (!destination.isFile || destination.length() != playback.byteSize) {
            val absoluteUrl = apiClient.resolveAssetUrl(playback.url).toString()
            val temporary = File(directory, ".${playback.sha256}.${UUID.randomUUID()}.part")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                apiClient.api.deliveredAsset(absoluteUrl).bodyOrThrow().use { response ->
                    response.byteStream().use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var size = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                size += read
                                require(size <= playback.byteSize) { "chorus mix exceeds declared size" }
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                require(temporary.length() == playback.byteSize) { "chorus mix byte size mismatch" }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == playback.sha256) { "chorus mix SHA-256 mismatch" }
                destination.delete()
                require(temporary.renameTo(destination)) { "could not commit chorus mix cache" }
            } finally {
                temporary.delete()
            }
        }
        return Uri.fromFile(destination).toString()
    }

    private fun client(): CatalogApiClient {
        val server = credentials.loadServerUrl() ?: throw CatalogFailure.NotConfigured()
        if (credentials.isReenrollmentRequired()) throw CatalogFailure.InvalidCredentials()
        if (credentials.loadDevice() == null && credentials.loadToken() == null) {
            throw CatalogFailure.NotConfigured()
        }
        return CatalogApiClient(server, credentials)
    }

    private fun adminDevice(dto: AdminDeviceDto): CatalogAdminDevice = CatalogAdminDevice(
        deviceId = requireNotNull(dto.deviceId),
        userId = requireNotNull(dto.userId),
        displayName = dto.displayName,
        applicationId = requireNotNull(dto.applicationId),
        status = requireNotNull(dto.status),
        isAdministrator = requireNotNull(dto.isAdministrator),
        createdAt = requireNotNull(dto.createdAt),
        lastSeenAt = dto.lastSeenAt,
    )

    private fun cacheNamespace(): String {
        val server = credentials.loadServerUrl() ?: throw CatalogFailure.NotConfigured()
        val identity = credentials.loadDeviceId() ?: credentials.loadToken()
            ?: throw CatalogFailure.NotConfigured()
        return CatalogOfflineCache.namespace(server, identity)
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (error: Throwable) {
            if (error is CatalogFailure.InvalidCredentials) {
                credentials.markReenrollmentRequired()
            }
            when (error) {
                is CatalogFailure -> Result.failure(error)
                is IOException -> Result.failure(CatalogFailure.Unreachable(error))
                is IllegalArgumentException, is IllegalStateException ->
                    Result.failure(CatalogFailure.InvalidData(error.message ?: "未知数据错误", error))
                else -> Result.failure(error)
            }
        }
    }

    private fun validUuid(value: String): String = runCatching { java.util.UUID.fromString(value).toString() }
        .getOrElse { throw CatalogFailure.InvalidData("ID 不是有效 UUID") }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw httpFailure(code())
        return body() ?: throw CatalogFailure.InvalidData("服务器返回空响应")
    }

    private fun <T> requireSuccessful(response: Response<T>, allowUnauthenticated: Boolean = false) {
        if (!response.isSuccessful) {
            if (allowUnauthenticated && response.code() == 401) return
            throw httpFailure(response.code())
        }
    }

    private fun httpFailure(code: Int): CatalogFailure = when (code) {
        401 -> CatalogFailure.InvalidCredentials()
        403 -> CatalogFailure.Forbidden()
        412 -> CatalogFailure.StaleRevision(null)
        else -> CatalogFailure.Server(code)
    }
}
