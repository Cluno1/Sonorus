package io.github.cluno1.sonorus.features.scores.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.roundToLong

/**
 * Display-only lyric carrier. Hymn lyrics are attached to one part (usually the melody), so
 * hiding that part also hides the words. When the parts carrying lyrics are all hidden, this
 * copies their lyrics onto the top-most visible part, note by note at identical onsets, so the
 * reader still sees the words under whatever voice they chose. Notes in the visible part that
 * start at a time where the lyric part has no syllable stay without lyrics.
 *
 * Returns the original bytes when nothing needs to change. Playback never sees this projection.
 */
internal object MusicXmlLyricCarrierProjector {

    fun project(source: ByteArray, visiblePartIndexes: Set<Int>): ByteArray {
        if (visiblePartIndexes.isEmpty() || !source.containsAscii(LYRIC_MARKER)) return source
        val document = parse(source)
        val parts = document.documentElement.directElements().filter { it.tagName == "part" }
        val lyricParts = parts.indices.filter { index -> parts[index].hasLyrics() }
        if (lyricParts.isEmpty() || lyricParts.any { it in visiblePartIndexes }) return source
        val target = visiblePartIndexes.filter { it in parts.indices }.minOrNull() ?: return source

        // Take each onset's lyrics from the highest lyric part that has them there.
        val lyricsByOnset = linkedMapOf<Onset, List<Element>>()
        lyricParts.sorted().forEach { partIndex ->
            onsetNotes(parts[partIndex]).forEach { (onset, note) ->
                val lyrics = note.directElements().filter { it.tagName == "lyric" }
                if (lyrics.isNotEmpty()) lyricsByOnset.putIfAbsent(onset, lyrics)
            }
        }
        var copied = 0
        onsetNotes(parts[target]).forEach { (onset, note) ->
            val lyrics = lyricsByOnset[onset] ?: return@forEach
            val anchor = note.directElements().firstOrNull { it.tagName == "play" || it.tagName == "listen" }
            lyrics.forEach { lyric ->
                val clone = document.importNode(lyric, true)
                if (anchor != null) note.insertBefore(clone, anchor) else note.appendChild(clone)
            }
            copied++
        }
        return if (copied == 0) source else serialize(document)
    }

    private data class Onset(val measure: Int, val position: Long)

    /** First sounding non-chord note at each onset, in document order. */
    private fun onsetNotes(part: Element): List<Pair<Onset, Element>> {
        val result = mutableListOf<Pair<Onset, Element>>()
        val seen = mutableSetOf<Onset>()
        var divisions = 1L
        part.directElements().filter { it.tagName == "measure" }.forEachIndexed { measureIndex, measure ->
            var position = 0L // in divisions
            var lastStart = 0L
            measure.directElements().forEach { element ->
                when (element.tagName) {
                    "attributes" -> element.childText("divisions")?.toLongOrNull()?.takeIf { it > 0 }?.let { divisions = it }
                    "backup" -> position -= element.childText("duration")?.toLongOrNull() ?: 0L
                    "forward" -> position += element.childText("duration")?.toLongOrNull() ?: 0L
                    "note" -> {
                        val isChord = element.directElements().any { it.tagName == "chord" }
                        val isGrace = element.directElements().any { it.tagName == "grace" }
                        val duration = element.childText("duration")?.toLongOrNull() ?: 0L
                        val start = if (isChord) lastStart else position
                        val isRest = element.directElements().any { it.tagName == "rest" }
                        if (!isChord && !isRest && !isGrace) {
                            // Normalise to 1/960 quarter so parts with different divisions agree.
                            val onset = Onset(measureIndex, (start * 960.0 / divisions).roundToLong())
                            if (seen.add(onset)) result += onset to element
                        }
                        if (!isChord && !isGrace) {
                            lastStart = position
                            position += duration
                        }
                    }
                }
            }
        }
        return result
    }

    private fun Element.hasLyrics(): Boolean = getElementsByTagName("lyric").length > 0

    private fun Element.childText(name: String): String? =
        directElements().firstOrNull { it.tagName == name }?.textContent?.trim()

    private fun Element.directElements(): List<Element> = buildList {
        for (index in 0 until childNodes.length) {
            val node = childNodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun ByteArray.containsAscii(marker: ByteArray): Boolean {
        outer@ for (start in 0..size - marker.size) {
            for (offset in marker.indices) {
                if (this[start + offset] != marker[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private fun parse(source: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(source))
    }

    private fun serialize(document: Document): ByteArray {
        val output = ByteArrayOutputStream()
        val factory = TransformerFactory.newInstance().apply {
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_STYLESHEET, "") }
        }
        factory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private val LYRIC_MARKER = "<lyric".toByteArray(Charsets.US_ASCII)
    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
    private const val ACCESS_EXTERNAL_STYLESHEET =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet"
}
