package io.github.cluno1.sonorus.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCapabilitiesTest {
    @Test
    fun `device public metadata initializes metadata network client in catalog-only builds`() {
        assertTrue(
            ProductCapabilities.shouldInitializeMetadataNetworkClient(
                thirdPartyMusicServices = false,
                devicePublicMetadata = true
            )
        )
    }

    @Test
    fun `metadata network client stays disabled when no network metadata capability exists`() {
        assertFalse(
            ProductCapabilities.shouldInitializeMetadataNetworkClient(
                thirdPartyMusicServices = false,
                devicePublicMetadata = false
            )
        )
    }

    @Test
    fun `LAN Subsonic enables streaming shell without broad third party services`() {
        assertTrue(
            ProductCapabilities.allowsStreamingMode(
                thirdPartyMusicServices = false,
                lanSubsonicOnly = true,
            ),
        )
    }

    @Test
    fun `streaming shell stays disabled when both capabilities are off`() {
        assertFalse(
            ProductCapabilities.allowsStreamingMode(
                thirdPartyMusicServices = false,
                lanSubsonicOnly = false,
            ),
        )
    }

    @Test
    fun `existing LAN streaming preference migrates to the unified library`() {
        assertEquals("LOCAL", ProductCapabilities.acceptedAppMode("STREAMING", true, true))
        assertEquals("LOCAL", ProductCapabilities.acceptedAppMode("streaming", true, true))
        assertEquals("LOCAL", ProductCapabilities.acceptedAppMode("LOCAL", true, true))
    }

    @Test
    fun `standalone provider mode remains available to other product builds`() {
        assertEquals("STREAMING", ProductCapabilities.acceptedAppMode("STREAMING", true, false))
        assertEquals("LOCAL", ProductCapabilities.acceptedAppMode("STREAMING", false, false))
        assertEquals("LOCAL", ProductCapabilities.acceptedAppMode(null, true, false))
    }
}
