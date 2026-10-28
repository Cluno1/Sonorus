package io.github.cluno1.sonorus.shared.presentation.components.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLyricsSourceLabelTest {
    @Test
    fun readsManagedMusicLibraryLanguageWithoutExposingInternalName() {
        assertEquals("en", managedLibraryLanguage("RHYTHM_LIBRARY|en"))
        assertEquals("en", managedLibraryLanguage("Catalog · en"))
        assertEquals("und", managedLibraryLanguage("Catalog"))
        assertNull(managedLibraryLanguage("LRCLib"))
    }

    @Test
    fun readsChineseLyricsSourceTags() {
        assertEquals(
            ChineseLyricsSourceParts(false, "旅人", "罗云熙", "86%", "tencent"),
            chineseLyricsSource("DEVICE_CNLYRICS|旅人|罗云熙|86%|tencent"),
        )
        assertEquals(true, chineseLyricsSource("DEVICE_CNLYRICS_SELECTED|旅人|罗云熙|86%|kugou")?.selected)
        assertEquals("netease", chineseLyricsSource("CNLYRICS|旅人|罗云熙|80%|netease")?.platform)
        assertNull(chineseLyricsSource("DEVICE_LRCLIB|旅人|罗云熙|86%"))
        assertNull(chineseLyricsSource("DEVICE_CNLYRICS|only-title"))
    }
}
