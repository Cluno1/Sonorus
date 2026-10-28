package io.github.cluno1.sonorus.features.catalog.domain

import java.io.IOException

/** Catalog failures may cross OkHttp interceptor boundaries and must remain transport-safe. */
sealed class CatalogFailure(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class NotConfigured : CatalogFailure("私有作品库尚未配置")
    class InvalidCredentials : CatalogFailure("私有作品库凭据已失效")
    class AdminInvalidCredentials : CatalogFailure("Administrator username or password is incorrect")
    class Forbidden : CatalogFailure("当前凭据无权访问此内容")
    class StaleRevision(val currentRevision: Int?) : CatalogFailure("服务器歌词已被其他设备修改")
    class Unreachable(cause: Throwable? = null) : CatalogFailure("无法连接私有作品库", cause)
    class Server(val statusCode: Int) : CatalogFailure("服务器暂时不可用（$statusCode）")
    class InvalidData(detail: String, cause: Throwable? = null) : CatalogFailure("服务器数据无效：$detail", cause)
    /** issue 124: the room expired (lazily removed the day after its date) or was deleted. */
    class RoomUnavailable : CatalogFailure("房间已不存在或已过期")
    /** issue 124: the Work is already in the room; [addedBy] is who put it there. */
    class RoomSongAlreadyAdded(val addedBy: CatalogRoomUser?) : CatalogFailure("这首歌已在房间里")
}

data class CatalogConnection(
    val serverUrl: String,
    val configured: Boolean,
    val deviceRegistered: Boolean,
    val reenrollmentRequired: Boolean = false,
    val draftNamespace: String = "",
    val userId: String? = null,
    val deviceId: String? = null,
)

interface CatalogRepository {
    /**
     * issue 116: replaces the whole tag set of one Work (`[]` clears it) for any enrolled device.
     * Guarded by the Work revision; a stale revision fails with [CatalogFailure.StaleRevision].
     * A backend without the endpoint answers 404 ([CatalogFailure.Server]).
     */
    /**
     * issue 125: replaces a Work's plain lyrics of [language] (the default language, or one of its
     * translations) for any enrolled device. Guarded by the Work revision like [replaceWorkTags].
     */
    suspend fun replaceWorkLyrics(
        workId: String,
        language: String,
        lyrics: String,
        expectedRevision: Int,
    ): Result<WorkSummary>

    suspend fun replaceWorkTags(
        workId: String,
        tags: List<String>,
        expectedRevision: Int,
    ): Result<WorkSummary> = Result.failure(CatalogFailure.Server(404))

    /** issue 121: every existing Work tag, most used first; falls back to the cached list offline. */
    suspend fun listLibraryTags(): Result<List<CatalogLibraryTag>> = Result.failure(CatalogFailure.Server(404))

    /** issue 121: the last tag catalog fetched, for an instant first frame. */
    fun cachedLibraryTags(): List<CatalogLibraryTag> = emptyList()

    fun connection(): CatalogConnection
    fun cachedWorks(): List<WorkSummary>
    fun cachedBundle(workId: String): WorkBundle?
    fun cachedLibrary(): CatalogLibrarySnapshot?
    suspend fun enrollDevice(serverUrl: String, inviteCode: String): Result<Unit>
    suspend fun issueInvite(
        serverUrl: String,
        username: String,
        password: String,
        userId: String,
        displayName: String? = null,
        replaceExistingDevice: Boolean = false,
    ): Result<CatalogIssuedInvite>
    suspend fun authenticateAdministrator(username: String, password: String): Result<List<CatalogAdminDevice>>
    suspend fun setDeviceAdministrator(deviceId: String, enabled: Boolean): Result<List<CatalogAdminDevice>>
    suspend fun getAdminDashboard(): Result<CatalogAdminDashboard>
    suspend fun issueInviteAsAdministrator(
        userId: String,
        displayName: String? = null,
        replaceExistingDevice: Boolean = false,
    ): Result<CatalogIssuedInvite>
    suspend fun setChorusAutomaticApproval(enabled: Boolean): Result<ChorusModerationSettings>
    suspend fun moderateChorusTrack(
        trackId: String,
        revision: Int,
        publish: Boolean,
        reason: String? = null,
    ): Result<ChorusTrack>
    fun clearConnection()
    suspend fun listWorks(query: String? = null, cursor: String? = null, limit: Int = 50): Result<CatalogPage>
    suspend fun getWorkBundle(workId: String, forceRefresh: Boolean = false): Result<WorkBundle>
    suspend fun getScoreRevision(revisionId: String): Result<ScoreRevision>
    suspend fun getPlayback(renditionId: String, prefer: String = "stream"): Result<PlaybackDescriptor>
    suspend fun getLibrary(forceRefresh: Boolean = false): Result<CatalogLibrarySnapshot>
    suspend fun getLibraryAlbum(albumId: String, forceRefresh: Boolean = false): Result<CatalogLibraryAlbum>
    suspend fun searchLibrary(
        query: String,
        type: CatalogLibrarySearchType,
        limit: Int = 50,
    ): Result<CatalogLibrarySearchResult>
    suspend fun listTagWorks(tagKey: String, cursor: String? = null, limit: Int = 50): Result<CatalogTagWorksPage>

    /** issue 127: public-platform lyrics search; implementations without a server simply fail. */
    suspend fun searchExternalLyrics(query: String, limit: Int = 20): Result<CatalogExternalLyricsSearchResult> =
        Result.failure(UnsupportedOperationException("external lyrics are not supported"))

    suspend fun getExternalLyrics(
        source: CatalogExternalLyricsSource,
        externalId: String,
        title: String? = null,
    ): Result<CatalogExternalLyrics> =
        Result.failure(UnsupportedOperationException("external lyrics are not supported"))
    suspend fun downloadArtwork(assetId: String): Result<CatalogArtwork>
    suspend fun downloadAsset(assetId: String, expectedSha256: String, expectedSize: Long): Result<ByteArray>
    suspend fun cachePlaybackAndLatestScore(
        workId: String,
        arrangementId: String,
        renditionId: String,
    ): Result<Unit>
    suspend fun syncChanges(): Result<CatalogChanges>
    suspend fun replaceRenditionLyrics(
        renditionId: String,
        language: String,
        lyrics: String,
        format: String,
        expectedRevision: Int,
        idempotencyKey: String,
    ): Result<CatalogLyricsWriteResult>
    suspend fun getChorus(workId: String): Result<ChorusCatalog>
    suspend fun getChorusProject(projectId: String): Result<ChorusProject>
    suspend fun uploadChorusTrack(projectId: String, upload: ChorusTrackUpload): Result<ChorusTrack>
    suspend fun updateChorusTrackAlignment(
        trackId: String,
        revision: Int,
        offsetMs: Long,
        anchors: List<ChorusSyncAnchor>,
    ): Result<ChorusTrack>
    suspend fun submitChorusTrack(trackId: String): Result<ChorusTrack>
    suspend fun withdrawChorusTrack(trackId: String): Result<ChorusTrack>
    suspend fun resolveChorusMix(
        projectId: String,
        chorusTimelineId: String,
        trackIds: List<String>,
    ): Result<ChorusMix>
    suspend fun getChorusMix(mixId: String): Result<ChorusMix>
}
