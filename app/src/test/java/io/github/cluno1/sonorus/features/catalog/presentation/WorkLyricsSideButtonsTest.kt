package io.github.cluno1.sonorus.features.catalog.presentation

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** issue 170: the Work lyrics page (opened from search → lyrics) keeps its top bar for the title. */
class WorkLyricsSideButtonsTest {

    private val screen = File("src/main/java/io/github/cluno1/sonorus/features/catalog/presentation/CatalogWorkLyricsScreen.kt")

    @Test
    fun topBarHoldsNoLanguageOrSourceImageButton() {
        val text = screen.readText()
        val start = text.indexOf("TopAppBar(")
        val end = text.indexOf("{ innerPadding ->")
        assertTrue("TopAppBar block not found", start in 0 until end)
        val topBar = text.substring(start, end)
        assertFalse("language menu is back in the top bar", topBar.contains("LyricsLanguageSideButton"))
        assertFalse("source image toggle is back in the top bar", topBar.contains("CatalogLyricSourceToggleButton"))
        assertFalse("top bar actions squeeze the title again", topBar.contains("actions = {"))
    }

    @Test
    fun lyricsClearTheSideColumnButtonByButton() {
        assertEquals(0.dp, workLyricsSideColumnClearance(smallButtons = 0, hasPlayButton = false))
        assertEquals(52.dp, workLyricsSideColumnClearance(smallButtons = 1, hasPlayButton = false))
        // language + source images + room + edit + score, and the play FAB.
        assertEquals(52.dp * 5 + 68.dp, workLyricsSideColumnClearance(smallButtons = 5, hasPlayButton = true))
    }
}
