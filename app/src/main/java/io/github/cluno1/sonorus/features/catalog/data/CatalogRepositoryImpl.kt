package io.github.cluno1.sonorus.features.catalog.data

import android.content.Context
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogCache
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueStore
import io.github.cluno1.sonorus.features.catalog.data.local.CatalogOfflineCache
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogApiClient
import io.github.cluno1.sonorus.features.catalog.data.remote.AdminDeviceDto
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogDeviceAuthClient
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogDtoMapper
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogEndpoint
import io.github.cluno1.sonorus.features.catalog.domain.CatalogChanges
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAdminDashboard
import io.github.cluno1.sonorus.features.catalog.domain.CatalogAdminDevice
import io.github.cluno1.sonorus.features.catalog.domain.CatalogArtwork
import io.github.cluno1.sonorus.features.catalog.domain.CatalogConnection
import io.github.cluno1.sonorus.features.catalog.domain.CatalogFailure
import io.github.cluno1.sonorus.features.catalog.domain.CatalogIssuedInvite
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLyricsWriteResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryAlbum
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySnapshot
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyrics
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsMatch
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSearchResult
import io.github.cluno1.sonorus.features.catalog.domain.CatalogExternalLyricsSource
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogExternalLyricsMapper
import io.github.cluno1.sonorus.features.catalog.domain.CatalogLibrarySearchType
import io.github.cluno1.sonorus.features.catalog.domain.CatalogTagWorksPage
import io.github.cluno1.sonorus.features.catalog.domain.CatalogPlaybackPolicy
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRepository
import io.github.cluno1.sonorus.features.catalog.domain.PlaybackDescriptor
import io.github.cluno1.sonorus.features.catalog.domain.ScoreRevision
import io.github.cluno1.sonorus.features.catalog.domain.WorkBundle
import io.github.cluno1.sonorus.features.catalog.domain.WorkSummary
import io.github.cluno1.sonorus.features.catalog.data.remote.CatalogRoomMapper
import io.github.cluno1.sonorus.features.catalog.data.remote.RoomConflictDto
import io.github.cluno1.sonorus.features.catalog.data.remote.RoomCreateDto
import io.github.cluno1.sonorus.features.catalog.data.remote.RoomItemCreateDto
import io.github.cluno1.sonorus.features.catalog.data.remote.RoomSectionPatchDto
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoom
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSectionOrder
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomSummary
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomTemplate
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomUser
import io.github.cluno1.sonorus.features.catalog.domain.CatalogRoomsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class CatalogRepositoryImpl(context: Context) : CatalogRepository, CatalogRoomsRepository {
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
        CatalogAdminDashboard(devices)
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

    override suspend fun searchExternalLyrics(
        query: String,
        limit: Int,
    ): Result<CatalogExternalLyricsSearchResult> = guarded {
        val trimmed = query.trim()
        require(trimmed.length in 1..100) { "search query must be 1..100 characters" }
        require(limit in 1..50) { "limit must be in 1..50" }
        // Live third-party data (issue 127): never cached on the device.
        CatalogExternalLyricsMapper.search(client().api.externalLyricsSearch(trimmed, limit).bodyOrThrow())
    }

    override suspend fun matchExternalLyrics(
        title: String,
        artist: String?,
        album: String?,
        durationSeconds: Int?,
        limit: Int,
    ): Result<List<CatalogExternalLyricsMatch>> = guarded {
        val trimmed = title.trim()
        require(trimmed.length in 1..200) { "title must be 1..200 characters" }
        require(limit in 1..10) { "limit must be in 1..10" }
        CatalogExternalLyricsMapper.match(
            client().api.externalLyricsMatch(
                title = trimmed,
                artist = artist?.trim()?.take(200)?.takeIf(String::isNotEmpty),
                album = album?.trim()?.take(200)?.takeIf(String::isNotEmpty),
                durationSeconds = durationSeconds?.takeIf { it in 1..86_400 },
                limit = limit,
            ).bodyOrThrow(),
        )
    }

    override suspend fun getExternalLyrics(
        source: CatalogExternalLyricsSource,
        externalId: String,
        title: String?,
    ): Result<CatalogExternalLyrics> = guarded {
        val id = externalId.trim()
        require(id.matches(Regex("^[A-Za-z0-9]{1,64}$"))) { "invalid external lyrics id" }
        CatalogExternalLyricsMapper.lyrics(
            client().api.externalLyrics(source.wireValue, id, title?.trim()?.take(200)?.takeIf(String::isNotEmpty))
                .bodyOrThrow(),
        ).also { require(it.source == source && it.externalId == id) { "external lyrics do not match request" } }
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

    override suspend fun replaceWorkLyrics(
        workId: String,
        language: String,
        lyrics: String,
        expectedRevision: Int,
    ): Result<WorkSummary> = guarded {
        val id = validUuid(workId)
        require(expectedRevision > 0) { "work revision must be positive" }
        require(lyrics.isNotBlank()) { "lyrics must not be blank" }
        val tag = io.github.cluno1.sonorus.features.catalog.domain.normalizeCatalogLyricsLanguageTag(language)
        val response = client().workLyricsWriteApi.replaceWorkLyrics(
            workId = id,
            language = tag,
            ifMatch = "\"rev-$expectedRevision\"",
            body = io.github.cluno1.sonorus.features.catalog.data.remote.WorkLyricReplaceDto(lyrics),
        )
        if (response.code() == 412) {
            android.util.Log.w("CatalogRepository", "Catalog work lyrics edit rejected as stale (work=$id, expected=$expectedRevision)")
            throw CatalogFailure.StaleRevision(null)
        }
        val work = CatalogDtoMapper.work(response.bodyOrThrow())
        require(work.id == id) { "lyrics write work id does not match request" }
        // Same as tags: keep the cached bundle in step; the dropped ETag makes the next bundle
        // request revalidate against the server.
        cache.loadBundle(id)?.let { cache.saveBundle(it.copy(work = work), null) }
        android.util.Log.i("CatalogRepository", "Catalog work lyrics replaced (work=$id, language=$tag, revision=${work.revision})")
        work
    }

    override suspend fun replaceWorkTags(
        workId: String,
        tags: List<String>,
        expectedRevision: Int,
    ): Result<WorkSummary> = guarded {
        val id = validUuid(workId)
        require(expectedRevision > 0) { "work revision must be positive" }
        val response = client().workTagsWriteApi.replaceWorkTags(
            workId = id,
            ifMatch = "\"rev-$expectedRevision\"",
            body = io.github.cluno1.sonorus.features.catalog.data.remote.WorkTagsPatchDto(tags),
        )
        if (response.code() == 412) {
            val currentRevision = Regex("\\\"current_etag\\\"\\s*:\\s*\\\"rev-(\\d+)\\\"")
                .find(response.errorBody()?.string().orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            throw CatalogFailure.StaleRevision(currentRevision)
        }
        val work = CatalogDtoMapper.work(response.bodyOrThrow())
        require(work.id == id) { "tag write work id does not match request" }
        // Keep the cached bundle in step so lyrics pages show the saved tags at once; the ETag is
        // dropped so the next bundle request revalidates against the server.
        cache.loadBundle(id)?.let { cache.saveBundle(it.copy(work = work), null) }
        android.util.Log.i("CatalogRepository", "Catalog work tags replaced (work=$id, revision=${work.revision}, count=${work.tags.orEmpty().size})")
        work
    }

    override fun cachedLibraryTags(): List<io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryTag> =
        cache.loadLibraryTags()

    override suspend fun listLibraryTags():
        Result<List<io.github.cluno1.sonorus.features.catalog.domain.CatalogLibraryTag>> = guarded {
        CatalogDtoMapper.libraryTags(client().api.libraryTags().bodyOrThrow()).also(cache::saveLibraryTags)
    }.recoverCatching { error ->
        if (error is CatalogFailure.Unreachable) cache.loadLibraryTags() else throw error
    }

    override suspend fun listRooms(workId: String?): Result<List<CatalogRoomSummary>> = guarded {
        CatalogRoomMapper.rooms(client().roomsApi.rooms(workId?.let(::validUuid)).roomBodyOrThrow())
    }

    override suspend fun room(roomId: String): Result<CatalogRoom> = guarded {
        CatalogRoomMapper.room(client().roomsApi.room(validUuid(roomId)).roomBodyOrThrow())
    }

    override suspend fun listRoomTemplates(): Result<List<CatalogRoomTemplate>> = guarded {
        CatalogRoomMapper.templates(client().roomsApi.templates().roomBodyOrThrow())
    }

    override suspend fun listRoomUsers(): Result<List<CatalogRoomUser>> = guarded {
        CatalogRoomMapper.users(client().roomsApi.users().roomBodyOrThrow())
    }

    override suspend fun createRoom(
        name: String,
        serviceDate: java.time.LocalDate,
        idempotencyKey: String,
        templateId: String?,
    ): Result<CatalogRoom> = guarded {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 60) { "room name must be 1..60 characters" }
        val room = CatalogRoomMapper.room(
            client().roomsApi.createRoom(
                idempotencyKey, RoomCreateDto(trimmed, serviceDate.toString(), templateId?.let(::validUuid)),
            )
                .roomBodyOrThrow(),
        )
        android.util.Log.i(ROOMS_TAG, "room created (room=${room.id}, date=${room.serviceDate})")
        room
    }

    override suspend fun updateRoomSection(
        roomId: String, sectionId: String, name: String?, assigneeUserId: String?,
        expectedRevision: Int,
    ): Result<CatalogRoom> = guarded {
        require(expectedRevision > 0) { "room revision must be positive" }
        val response = client().roomsApi.updateSection(
            validUuid(roomId), validUuid(sectionId), "\"rev-$expectedRevision\"",
            RoomSectionPatchDto(name?.trim(), assigneeUserId),
        )
        if (response.code() == 412) throw CatalogFailure.StaleRevision(null)
        CatalogRoomMapper.room(response.roomBodyOrThrow())
    }

    override suspend fun addRoomSong(
        roomId: String,
        workId: String,
        sectionId: String?,
        idempotencyKey: String,
    ): Result<CatalogRoom> = guarded {
        val response = client().roomsApi.addSong(
            validUuid(roomId),
            idempotencyKey,
            RoomItemCreateDto(validUuid(workId), sectionId?.let(::validUuid)),
        )
        if (response.code() == 409) {
            val conflict = runCatching {
                com.google.gson.Gson().fromJson(response.errorBody()?.string().orEmpty(), RoomConflictDto::class.java)
            }.getOrNull()
            throw CatalogFailure.RoomSongAlreadyAdded(
                conflict?.addedBy?.let { runCatching { CatalogRoomMapper.user(it, "added_by") }.getOrNull() },
            )
        }
        val room = CatalogRoomMapper.room(response.roomBodyOrThrow())
        android.util.Log.i(ROOMS_TAG, "room song added (room=${room.id}, work=$workId, revision=${room.revision})")
        room
    }

    override suspend fun removeRoomSong(roomId: String, itemId: String): Result<CatalogRoom> = guarded {
        val room = CatalogRoomMapper.room(
            client().roomsApi.removeSong(validUuid(roomId), validUuid(itemId)).roomBodyOrThrow(),
        )
        android.util.Log.i(ROOMS_TAG, "room song removed (room=${room.id}, item=$itemId, revision=${room.revision})")
        room
    }

    override suspend fun reorderRoom(
        roomId: String,
        order: List<CatalogRoomSectionOrder>,
        expectedRevision: Int,
    ): Result<CatalogRoom> = guarded {
        require(expectedRevision > 0) { "room revision must be positive" }
        val response = client().roomsApi.reorder(
            validUuid(roomId),
            "\"rev-$expectedRevision\"",
            CatalogRoomMapper.order(order),
        )
        if (response.code() == 412) {
            android.util.Log.w(ROOMS_TAG, "room reorder rejected as stale (room=$roomId, expected=$expectedRevision)")
            throw CatalogFailure.StaleRevision(null)
        }
        val room = CatalogRoomMapper.room(response.roomBodyOrThrow())
        android.util.Log.i(ROOMS_TAG, "room reordered (room=${room.id}, revision=${room.revision})")
        room
    }

    override suspend fun deleteRoom(roomId: String): Result<Unit> = guarded {
        val response = client().roomsApi.deleteRoom(validUuid(roomId))
        if (!response.isSuccessful) throw roomFailure(response.code())
        android.util.Log.i(ROOMS_TAG, "room deleted (room=$roomId)")
    }

    private fun <T> Response<T>.roomBodyOrThrow(): T {
        if (!isSuccessful) throw roomFailure(code())
        return body() ?: throw CatalogFailure.InvalidData("服务器返回空响应")
    }

    /** A room that expired or was deleted answers 404; everything else keeps the shared mapping. */
    private fun roomFailure(code: Int): CatalogFailure {
        if (code == 403 || code >= 500) android.util.Log.w(ROOMS_TAG, "room request failed (status=$code)")
        return if (code == 404) CatalogFailure.RoomUnavailable() else httpFailure(code)
    }

    private fun client(): CatalogApiClient {
        val server = credentials.loadServerUrl() ?: throw CatalogFailure.NotConfigured()
        // Device credentials go on: CatalogDeviceAuthClient.proof() retries their existing session.
        if (credentials.isReenrollmentRequired() && credentials.loadDevice() == null) {
            throw CatalogFailure.InvalidCredentials()
        }
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
            // Device auth marks only a rejected session refresh inside proof(); a 401 seen here is
            // otherwise retried by the next proof() instead of blocking every later request.
            if (error is CatalogFailure.InvalidCredentials && credentials.loadDevice() == null) {
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

private const val ROOMS_TAG = "CatalogRooms"
