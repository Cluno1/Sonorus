package io.github.cluno1.sonorus.features.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPlaybackPolicyTest {
    private val renditionId = "11111111-1111-4111-8111-111111111111"
    private val assetId = "22222222-2222-4222-8222-222222222222"
    private val mediaId = "rhythm-catalog:rendition:$renditionId:asset:$assetId"
    private val cacheKey = "rhythm:asset:$assetId:${"a".repeat(64)}"

    @Test
    fun acceptsBackendManagedRenditionAsset() {
        assertTrue(
            CatalogPlaybackPolicy.allows(
                mediaId,
                "https://music.example/v2/assets/$assetId/content",
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
    }

    @Test
    fun rejectsDeviceAndArbitraryNetworkSources() {
        assertFalse(
            CatalogPlaybackPolicy.allows(
                mediaId,
                "content://media/audio/42",
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
        assertFalse(
            CatalogPlaybackPolicy.allows(
                mediaId,
                "file:///sdcard/song.mp3",
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
        assertFalse(
            CatalogPlaybackPolicy.allows(
                mediaId,
                "https://unrelated.example/song.mp3",
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
    }

    @Test
    fun acceptsOnlyMatchingMediaStoreAudioIdentityForDeviceSongs() {
        assertTrue(
            CatalogPlaybackPolicy.allowsDeviceMediaStoreItem(
                "42",
                "content://media/external/audio/media/42",
            ),
        )
        assertFalse(
            CatalogPlaybackPolicy.allowsDeviceMediaStoreItem(
                "41",
                "content://media/external/audio/media/42",
            ),
        )
        assertFalse(CatalogPlaybackPolicy.allowsDeviceMediaStoreItem("42", "file:///sdcard/song.mp3"))
        assertFalse(CatalogPlaybackPolicy.allowsDeviceMediaStoreItem("42", "content://other/audio/media/42"))
        assertFalse(
            CatalogPlaybackPolicy.allowsDeviceMediaStoreItem(
                "42",
                "content://media/external/audio/media/42?redirect=https://evil.example",
            ),
        )
    }

    @Test
    fun rejectsMismatchedOrUnmanagedAssetIdentity() {
        val otherAsset = "33333333-3333-4333-8333-333333333333"
        assertFalse(
            CatalogPlaybackPolicy.allows(
                mediaId,
                "https://music.example/v2/assets/$otherAsset/content",
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
        assertFalse(
            CatalogPlaybackPolicy.allows(
                "local-song-id",
                "https://music.example/v2/assets/$assetId/content",
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
        assertFalse(
            CatalogPlaybackPolicy.allows(
                mediaId,
                "https://evil.example/v2/assets/$assetId/content",
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
    }

    @Test
    fun acceptsExplicitRealAudioFormatsAndParameters() {
        assertTrue(CatalogPlaybackPolicy.isPlayableMediaType("audio/mpeg"))
        assertTrue(CatalogPlaybackPolicy.isPlayableMediaType("audio/mp4; codecs=mp4a.40.2"))
        assertTrue(CatalogPlaybackPolicy.isPlayableMediaType("audio/flac"))
        assertTrue(CatalogPlaybackPolicy.isPlayableMediaType("audio/ogg; codecs=opus"))
        assertTrue(CatalogPlaybackPolicy.isPlayableMediaType("audio/wav"))
    }

    @Test
    fun rejectsMidiAndGenericOrUnknownAudioTypes() {
        assertFalse(CatalogPlaybackPolicy.isPlayableMediaType("audio/midi"))
        assertFalse(CatalogPlaybackPolicy.isPlayableMediaType("audio/x-midi"))
        assertFalse(CatalogPlaybackPolicy.isPlayableMediaType("application/x-midi"))
        assertFalse(CatalogPlaybackPolicy.isPlayableMediaType("audio/*"))
        assertFalse(CatalogPlaybackPolicy.isPlayableMediaType("audio/unknown"))
        assertFalse(CatalogPlaybackPolicy.isPlayableMediaType(null))
        assertFalse(
            CatalogPlaybackPolicy.allows(
                mediaId,
                "https://music.example/v2/assets/$assetId/content",
                cacheKey,
                "audio/midi",
                "https://music.example",
            ),
        )
    }

    @Test
    fun acceptsOnlyValidatedMusicXmlMediaTypes() {
        assertTrue(CatalogPlaybackPolicy.isMusicXmlMediaType("application/vnd.recordare.musicxml+xml"))
        assertTrue(CatalogPlaybackPolicy.isMusicXmlMediaType("application/vnd.recordare.musicxml+xml; charset=utf-8"))
        assertFalse(CatalogPlaybackPolicy.isMusicXmlMediaType("application/vnd.recordare.musicxml"))
        assertFalse(CatalogPlaybackPolicy.isMusicXmlMediaType("application/xml"))
        assertFalse(CatalogPlaybackPolicy.isMusicXmlMediaType("text/html"))
        assertFalse(CatalogPlaybackPolicy.isMusicXmlMediaType(null))
    }

    @Test
    fun renditionRequiresPlayableRoleAndRealAudioMime() {
        val mp3Master = RenditionAsset(
            id = "33333333-3333-4333-8333-333333333333",
            assetId = assetId,
            role = "master",
            partId = null,
            codecProfile = "mp3",
            sha256 = "a".repeat(64),
            byteSize = 1024,
            mediaType = "audio/mpeg",
        )
        assertTrue(CatalogPlaybackPolicy.isPlayableRenditionAsset(mp3Master))
        assertFalse(CatalogPlaybackPolicy.isPlayableRenditionAsset(mp3Master.copy(role = "source")))
        assertFalse(CatalogPlaybackPolicy.isPlayableRenditionAsset(mp3Master.copy(mediaType = "audio/midi")))
    }
    private val cosSignedUrl =
        "https://bible-1328751369.cos.ap-guangzhou.myqcloud.com/music/221.mp3" +
            "?q-sign-algorithm=sha1&q-ak=AKIDx&q-sign-time=1;2&q-key-time=1;2" +
            "&q-header-list=&q-url-param-list=&q-signature=deadbeef"

    @Test
    fun acceptsBackendSignedObjectStoreUrl() {
        assertTrue(
            CatalogPlaybackPolicy.allows(
                mediaId,
                cosSignedUrl,
                cacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
        assertTrue(CatalogPlaybackPolicy.isSignedObjectStoreUrl(cosSignedUrl))
    }

    @Test
    fun rejectsUnsignedOrNonCosObjectStoreUrl() {
        // 同为 COS 域名但缺签名查询串 -> 视为任意外链，拒绝。
        val unsigned = "https://bible-1328751369.cos.ap-guangzhou.myqcloud.com/music/221.mp3"
        assertFalse(CatalogPlaybackPolicy.isSignedObjectStoreUrl(unsigned))
        assertFalse(
            CatalogPlaybackPolicy.allows(mediaId, unsigned, cacheKey, "audio/mpeg", "https://music.example"),
        )
        // 冒充 COS 的其它域名 -> 拒绝。
        val fakeHost =
            "https://evil.example/music/221.mp3?q-sign-algorithm=sha1&q-signature=deadbeef"
        assertFalse(CatalogPlaybackPolicy.isSignedObjectStoreUrl(fakeHost))
        assertFalse(
            CatalogPlaybackPolicy.allows(mediaId, fakeHost, cacheKey, "audio/mpeg", "https://music.example"),
        )
    }

    @Test
    fun imageDeliveryAllowsSignedHttpsCustomDomainWithoutRelaxingUploadPolicy() {
        val customPreview =
            "https://images.sonorous.example/assets/photo?q-sign-algorithm=sha1&q-signature=deadbeef"
        assertTrue(CatalogPlaybackPolicy.isSignedImageDeliveryUrl(customPreview))
        assertFalse(CatalogPlaybackPolicy.isSignedObjectStoreUrl(customPreview))
        assertFalse(
            CatalogPlaybackPolicy.isSignedImageDeliveryUrl(
                "https://images.sonorous.example/assets/photo",
            ),
        )
        assertFalse(
            CatalogPlaybackPolicy.isSignedImageDeliveryUrl(
                "http://images.sonorous.example/assets/photo?q-sign-algorithm=sha1&q-signature=x",
            ),
        )
        assertFalse(
            CatalogPlaybackPolicy.isSignedImageDeliveryUrl(
                "https://user@images.sonorous.example/assets/photo?q-sign-algorithm=sha1&q-signature=x",
            ),
        )
    }

    @Test
    fun automaticArtworkAllowsOnlyCatalogOriginOrSignedCos() {
        val origin = "http://10.88.0.1:8010"

        assertEquals(
            "http://10.88.0.1:8010/v2/assets/11111111-1111-4111-8111-111111111111/content",
            CatalogPlaybackPolicy.resolveAutomaticArtworkUrl(
                "/v2/assets/11111111-1111-4111-8111-111111111111/content",
                origin,
            ),
        )
        assertEquals(
            cosSignedUrl,
            CatalogPlaybackPolicy.resolveAutomaticArtworkUrl(cosSignedUrl, origin),
        )
        assertNull(
            CatalogPlaybackPolicy.resolveAutomaticArtworkUrl("https://images.example/cover.jpg", origin),
        )
        assertNull(
            CatalogPlaybackPolicy.resolveAutomaticArtworkUrl(
                "https://bible-1328751369.cos.ap-guangzhou.myqcloud.com/cover.jpg",
                origin,
            ),
        )
    }

    @Test
    fun rejectsSignedUrlWhenCacheIdentityMismatches() {
        val otherAssetCacheKey = "rhythm:asset:33333333-3333-4333-8333-333333333333:${"a".repeat(64)}"
        assertFalse(
            CatalogPlaybackPolicy.allows(
                mediaId,
                cosSignedUrl,
                otherAssetCacheKey,
                "audio/mpeg",
                "https://music.example",
            ),
        )
    }

    @Test
    fun acceptsOnlyMatchingDeferredRenditionIdentity() {
        val uri = CatalogPlaybackPolicy.deferredUri(renditionId)
        assertTrue(
            CatalogPlaybackPolicy.allowsDeferred(
                "rhythm-catalog:rendition:$renditionId",
                uri,
                "audio/mpeg",
            )
        )
        assertFalse(
            CatalogPlaybackPolicy.allowsDeferred(
                "rhythm-catalog:rendition:33333333-3333-4333-8333-333333333333",
                uri,
                "audio/mpeg",
            )
        )
        assertFalse(
            CatalogPlaybackPolicy.allowsDeferred(
                "rhythm-catalog:rendition:$renditionId",
                "https://evil.example/song.mp3",
                "audio/mpeg",
            )
        )
    }

    @Test
    fun mediaSessionAcceptsStrictDeferredItemButRejectsArbitraryUri() {
        val deferredMediaId = "rhythm-catalog:rendition:$renditionId"
        assertTrue(
            CatalogPlaybackPolicy.allowsMediaSessionItem(
                mediaId = deferredMediaId,
                uri = CatalogPlaybackPolicy.deferredUri(renditionId),
                customCacheKey = null,
                mediaType = "audio/mpeg",
                trustedServerUrl = "https://music.example",
            )
        )
        assertFalse(
            CatalogPlaybackPolicy.allowsMediaSessionItem(
                mediaId = deferredMediaId,
                uri = "content://media/audio/42",
                customCacheKey = null,
                mediaType = "audio/mpeg",
                trustedServerUrl = "https://music.example",
            )
        )
        assertFalse(
            CatalogPlaybackPolicy.allowsMediaSessionItem(
                mediaId = deferredMediaId,
                uri = "rhythm-catalog://rendition/33333333-3333-4333-8333-333333333333",
                customCacheKey = null,
                mediaType = "audio/mpeg",
                trustedServerUrl = "https://music.example",
            )
        )
    }
}
