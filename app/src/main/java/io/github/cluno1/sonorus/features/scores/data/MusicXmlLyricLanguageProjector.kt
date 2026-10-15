package io.github.cluno1.sonorus.features.scores.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Chinese script a reader wants to see under the notes. */
internal enum class ScoreLyricScript {
    ZH_HANT,
    ZH_HANS;

    companion object {
        /** Persisted setting value meaning "follow the app language". */
        const val AUTO = "AUTO"

        fun fromSetting(value: String?): ScoreLyricScript? =
            entries.firstOrNull { it.name == value }

        /** Traditional for zh-Hant / Taiwan / Hong Kong / Macau UI locales, otherwise Simplified. */
        fun forLocale(locale: Locale): ScoreLyricScript {
            val isChinese = locale.language.equals("zh", ignoreCase = true)
            val traditional = isChinese && (
                locale.script.equals("Hant", ignoreCase = true) ||
                    locale.country.uppercase(Locale.ROOT) in TRADITIONAL_REGIONS
                )
            return if (traditional) ZH_HANT else ZH_HANS
        }

        /** Maps a BCP 47 tag such as zh-Hant, zh-TW, zh-Hans or zh-CN to a script, else null. */
        fun fromLanguageTag(tag: String?): ScoreLyricScript? {
            val parts = tag?.trim()?.takeIf(String::isNotEmpty)
                ?.replace('_', '-')
                ?.split('-')
                ?.map { it.lowercase(Locale.ROOT) }
                ?: return null
            if (parts.first() !in CHINESE_LANGUAGES) return null
            val subtags = parts.drop(1)
            return when {
                "hant" in subtags -> ZH_HANT
                "hans" in subtags -> ZH_HANS
                subtags.any { it.uppercase(Locale.ROOT) in TRADITIONAL_REGIONS } -> ZH_HANT
                subtags.any { it.uppercase(Locale.ROOT) in SIMPLIFIED_REGIONS } -> ZH_HANS
                else -> null
            }
        }

        private val CHINESE_LANGUAGES = setOf("zh", "cmn", "yue")
        private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
        private val SIMPLIFIED_REGIONS = setOf("CN", "SG")
    }
}

/**
 * Display-only lyric filter. A MusicXML file may carry the same verses in Traditional and
 * Simplified Chinese, told apart by `xml:lang` on `<lyric>`, on its `<text>`, or through
 * `<defaults><lyric-language number=… xml:lang=…/>`. Only the selected script is kept and the
 * remaining verses are renumbered 1..N so alphaTab lays them out without gaps.
 *
 * Lyrics without a recognised Chinese script tag are always kept. When nothing needs to change
 * the original bytes are returned unchanged. Playback, the source map and editing all work on
 * the canonical bytes and never see this projection.
 */
internal object MusicXmlLyricLanguageProjector {

    fun availableScripts(source: ByteArray): Set<ScoreLyricScript> {
        if (!source.containsAscii(LANG_MARKER)) return emptySet()
        val document = runCatching { parse(source) }.getOrNull() ?: return emptySet()
        val defaults = defaultLyricLanguages(document)
        return document.getElementsByTagName("lyric").elements()
            .mapNotNullTo(linkedSetOf()) { scriptOf(it, defaults) }
    }

    fun project(source: ByteArray, script: ScoreLyricScript): ByteArray {
        if (!source.containsAscii(LANG_MARKER)) return source
        val document = parse(source)
        val defaults = defaultLyricLanguages(document)
        val lyrics = document.getElementsByTagName("lyric").elements()
        val removed = lyrics.filter { lyric ->
            val lyricScript = scriptOf(lyric, defaults)
            lyricScript != null && lyricScript != script
        }
        if (removed.isEmpty()) return source

        removed.forEach { it.parentNode.removeChild(it) }
        val kept = document.getElementsByTagName("lyric").elements()
        renumber(kept)
        // Verse numbers changed, so number-based language defaults no longer apply.
        document.getElementsByTagName("lyric-language").elements().forEach {
            it.parentNode.removeChild(it)
        }
        return serialize(document)
    }

    private fun renumber(lyrics: List<Element>) {
        val firstSeen = linkedMapOf<String, Int>()
        lyrics.forEachIndexed { index, lyric ->
            firstSeen.putIfAbsent(lyric.verseKey(), index)
        }
        val ordered = firstSeen.keys.sortedWith(
            compareBy<String>({ it.toIntOrNull() ?: Int.MAX_VALUE }, { firstSeen.getValue(it) }),
        )
        val newNumbers = ordered.withIndex().associate { (index, key) -> key to (index + 1).toString() }
        lyrics.forEach { lyric ->
            val oldKey = lyric.verseKey()
            val newNumber = newNumbers.getValue(oldKey)
            if (lyric.hasAttribute("name") && lyric.getAttribute("name") == lyric.getAttribute("number")) {
                lyric.setAttribute("name", newNumber)
            }
            lyric.setAttribute("number", newNumber)
        }
    }

    private fun Element.verseKey(): String = getAttribute("number").ifBlank { "1" }

    private fun scriptOf(lyric: Element, defaults: Map<String, ScoreLyricScript>): ScoreLyricScript? {
        ScoreLyricScript.fromLanguageTag(lyric.getAttribute(XML_LANG))?.let { return it }
        lyric.directElements().firstOrNull { it.tagName == "text" }
            ?.let { ScoreLyricScript.fromLanguageTag(it.getAttribute(XML_LANG)) }
            ?.let { return it }
        return defaults[lyric.verseKey()] ?: defaults[lyric.getAttribute("name")]
    }

    private fun defaultLyricLanguages(document: Document): Map<String, ScoreLyricScript> =
        document.getElementsByTagName("lyric-language").elements()
            .flatMap { language ->
                val script = ScoreLyricScript.fromLanguageTag(language.getAttribute(XML_LANG))
                    ?: return@flatMap emptyList()
                listOf(language.getAttribute("number"), language.getAttribute("name"))
                    .filter(String::isNotBlank)
                    .map { it to script }
            }
            .toMap()

    private fun ByteArray.containsAscii(marker: ByteArray): Boolean {
        if (marker.isEmpty() || size < marker.size) return false
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

    private fun Element.directElements(): List<Element> = buildList {
        for (index in 0 until childNodes.length) {
            val node = childNodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = buildList {
        for (index in 0 until length) {
            val node = item(index)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private const val XML_LANG = "xml:lang"
    private val LANG_MARKER = XML_LANG.toByteArray(Charsets.US_ASCII)
    private const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
    private const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
    private const val ACCESS_EXTERNAL_STYLESHEET =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet"
}
