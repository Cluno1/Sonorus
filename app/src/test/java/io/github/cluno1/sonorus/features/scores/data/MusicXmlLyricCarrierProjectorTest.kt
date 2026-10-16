package io.github.cluno1.sonorus.features.scores.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class MusicXmlLyricCarrierProjectorTest {
    private fun note(dur: Int, lyric: String? = null, chord: Boolean = false) =
        "<note>${if (chord) "<chord/>" else ""}<pitch><step>C</step><octave>4</octave></pitch><duration>$dur</duration>" +
            (lyric?.let { "<lyric number=\"1\"><syllabic>single</syllabic><text>$it</text></lyric>" } ?: "") + "</note>"

    private val score = """<?xml version="1.0" encoding="UTF-8"?><score-partwise version="4.0"><part-list/>
        <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes>${note(2, "每")}${note(1, "逢")}${note(1, "思")}</measure></part>
        <part id="P2"><measure number="1"><attributes><divisions>2</divisions></attributes>${note(4)}${note(4)}</measure></part>
        </score-partwise>""".toByteArray()

    private fun lyricsOfPart(bytes: ByteArray, index: Int): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val part = doc.getElementsByTagName("part").item(index) as Element
        val lyrics = part.getElementsByTagName("text")
        return (0 until lyrics.length).map { lyrics.item(it).textContent }
    }

    @Test
    fun `unchanged when the lyric part is visible`() {
        assertSame(score, MusicXmlLyricCarrierProjector.project(score, setOf(0, 1)))
    }

    @Test
    fun `copies lyrics to the visible part at matching onsets`() {
        val projected = MusicXmlLyricCarrierProjector.project(score, setOf(1))
        // P2 notes start at beats 0 and 2, matching "每" and "逢"; "思" (beat 3) has no P2 onset.
        assertEquals(listOf("每", "逢"), lyricsOfPart(projected, 1))
        assertEquals(listOf("每", "逢", "思"), lyricsOfPart(projected, 0))
    }
}
