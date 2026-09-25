/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRoutePolicyTest {
    @Test
    fun `LAN build admits only Subsonic setup among streaming routes`() {
        assertTrue(ProductRoutePolicy.allowsInitialNavigationRoute("library?tab=songs", true, true))
        assertTrue(ProductRoutePolicy.allowsInitialNavigationRoute("streaming_service_setup/SUBSONIC", true, true))
        assertFalse(ProductRoutePolicy.allowsInitialNavigationRoute("streaming_service_setup/JELLYFIN", true, true))
        assertFalse(ProductRoutePolicy.allowsInitialNavigationRoute("streaming_artist/42", true, true))
        assertFalse(ProductRoutePolicy.allowsInitialNavigationRoute("streaming_go_settings", true, true))
    }

    @Test
    fun `catalog only build without LAN capability blocks every streaming route`() {
        assertFalse(ProductRoutePolicy.allowsInitialNavigationRoute("streaming_service_setup/SUBSONIC", true, false))
        assertTrue(ProductRoutePolicy.allowsInitialNavigationRoute("settings", true, false))
    }

    @Test
    fun `LAN settings admits only dedicated gateway pane from blocked provider settings`() {
        assertTrue(ProductRoutePolicy.allowsSettingsSubroute("lan_subsonic_settings", true, true))
        assertFalse(ProductRoutePolicy.allowsSettingsSubroute("go_settings", true, true))
        assertFalse(ProductRoutePolicy.allowsSettingsSubroute("api_management_settings", true, true))
    }
}
