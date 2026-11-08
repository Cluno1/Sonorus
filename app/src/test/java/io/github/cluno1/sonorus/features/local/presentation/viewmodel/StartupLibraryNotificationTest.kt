package io.github.cluno1.sonorus.features.local.presentation.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupLibraryNotificationTest {
    @Test
    fun `普通启动资料库未变化时不弹资料库设置通知`() {
        assertFalse(startupNeedsLibrarySetupNotification(cachedSongCount = 120, libraryStale = false))
    }

    @Test
    fun `资料库为空或已变化需要重扫时才弹通知`() {
        assertTrue(startupNeedsLibrarySetupNotification(cachedSongCount = 0, libraryStale = false))
        assertTrue(startupNeedsLibrarySetupNotification(cachedSongCount = 120, libraryStale = true))
    }
}
