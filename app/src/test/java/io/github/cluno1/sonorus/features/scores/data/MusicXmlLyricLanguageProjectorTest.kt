package io.github.cluno1.sonorus.features.scores.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class MusicXmlLyricLanguageProjectorTest {

    private fun scoreXml(notes: String, defaults: String = "") = """
        <?xml version="1.0" encoding="UTF-8"?>
        <score-partwise version="4.0">$defaults<part-list><score-part id="P1"><part-name>S</part-name></score-part></part-list>
        <part id="P1"><measure number="1">$notes</measure></part></score-partwise>
    """.trimIndent().toByteArray()

    private fun note(vararg lyrics: String) =
        "<note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type>${lyrics.joinToString("")}</note>"

    private fun lyric(number: Int, text: String, lang: String? = null, langOnText: Boolean = false): String {
        val lyricLang = if (lang != null && !langOnText) " xml:lang=\"$lang\"" else ""
        val textLang = if (lang != null && langOnText) " xml:lang=\"$lang\"" else ""
        return "<lyric number=\"$number\" name=\"$number\"$lyricLang><syllabic>single</syllabic><text$textLang>$text</text></lyric>"
    }

    private fun lyrics(bytes: ByteArray): List<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))
        val nodes = document.getElementsByTagName("lyric")
        return (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("number") to element.getElementsByTagName("text").item(0).textContent
        }
    }

    private val bilingual = scoreXml(
        note(
            lyric(1, "每", "zh-Hant"), lyric(2, "求", "zh-Hant"),
            lyric(3, "每", "zh-Hans"), lyric(4, "求", "zh-Hans"),
        ) + note(
            lyric(1, "逢", "zh-Hant"), lyric(2, "主", "zh-Hant"),
            lyric(3, "逢", "zh-Hans"), lyric(4, "主", "zh-Hans"),
        ),
    )

    @Test
    fun `untagged lyrics pass through byte identical`() {
        val source = scoreXml(note(lyric(1, "每"), lyric(2, "求")))
        assertSame(source, MusicXmlLyricLanguageProjector.project(source, ScoreLyricScript.ZH_HANS))
        assertEquals(emptySet<ScoreLyricScript>(), MusicXmlLyricLanguageProjector.availableScripts(source))
    }

    @Test
    fun `detects both scripts`() {
        assertEquals(
            setOf(ScoreLyricScript.ZH_HANT, ScoreLyricScript.ZH_HANS),
            MusicXmlLyricLanguageProjector.availableScripts(bilingual),
        )
    }

    @Test
    fun `keeps selected script and renumbers verses from one`() {
        val hans = MusicXmlLyricLanguageProjector.project(bilingual, ScoreLyricScript.ZH_HANS)
        assertEquals(listOf("1" to "每", "2" to "求", "1" to "逢", "2" to "主"), lyrics(hans))
        val hant = MusicXmlLyricLanguageProjector.project(bilingual, ScoreLyricScript.ZH_HANT)
        assertEquals(listOf("1" to "每", "2" to "求", "1" to "逢", "2" to "主"), lyrics(hant))
    }

    @Test
    fun `reads language from text element and region subtags`() {
        val source = scoreXml(
            note(lyric(1, "榮", "zh-TW", langOnText = true), lyric(2, "荣", "zh-CN", langOnText = true)),
        )
        assertEquals(listOf("1" to "荣"), lyrics(MusicXmlLyricLanguageProjector.project(source, ScoreLyricScript.ZH_HANS)))
    }

    @Test
    fun `reads language from lyric-language defaults`() {
        val source = scoreXml(
            note(lyric(1, "榮"), lyric(2, "荣")),
            defaults = "<defaults><lyric-language number=\"1\" xml:lang=\"zh-Hant\"/><lyric-language number=\"2\" xml:lang=\"zh-Hans\"/></defaults>",
        )
        assertEquals(listOf("1" to "榮"), lyrics(MusicXmlLyricLanguageProjector.project(source, ScoreLyricScript.ZH_HANT)))
    }

    @Test
    fun `keeps lyrics in other languages`() {
        val source = scoreXml(note(lyric(1, "榮", "zh-Hant"), lyric(2, "荣", "zh-Hans"), lyric(3, "When", "en")))
        assertEquals(
            listOf("1" to "荣", "2" to "When"),
            lyrics(MusicXmlLyricLanguageProjector.project(source, ScoreLyricScript.ZH_HANS)),
        )
    }

    @Test
    fun `projection does not change note count`() {
        fun noteCount(bytes: ByteArray) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes)).getElementsByTagName("note").length
        val projected = MusicXmlLyricLanguageProjector.project(bilingual, ScoreLyricScript.ZH_HANT)
        assertEquals(noteCount(bilingual), noteCount(projected))
    }

    @Test
    fun `maps language tags and locales to scripts`() {
        assertEquals(ScoreLyricScript.ZH_HANT, ScoreLyricScript.fromLanguageTag("zh-Hant"))
        assertEquals(ScoreLyricScript.ZH_HANT, ScoreLyricScript.fromLanguageTag("zh_HK"))
        assertEquals(ScoreLyricScript.ZH_HANS, ScoreLyricScript.fromLanguageTag("zh-Hans-CN"))
        assertNull(ScoreLyricScript.fromLanguageTag("zh"))
        assertNull(ScoreLyricScript.fromLanguageTag("en"))
        assertEquals(ScoreLyricScript.ZH_HANT, ScoreLyricScript.forLocale(Locale.forLanguageTag("zh-TW")))
        assertEquals(ScoreLyricScript.ZH_HANT, ScoreLyricScript.forLocale(Locale.forLanguageTag("zh-Hant-SG")))
        assertEquals(ScoreLyricScript.ZH_HANS, ScoreLyricScript.forLocale(Locale.forLanguageTag("zh-CN")))
        assertEquals(ScoreLyricScript.ZH_HANS, ScoreLyricScript.forLocale(Locale.ENGLISH))
        assertArrayEquals(arrayOf("ZH_HANT", "ZH_HANS"), ScoreLyricScript.entries.map { it.name }.toTypedArray())
    }
}
