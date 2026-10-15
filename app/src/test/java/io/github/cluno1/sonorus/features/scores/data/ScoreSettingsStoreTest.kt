/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.scores.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreSettingsStoreTest {
    @Test
    fun `global settings survive codec round trip`() {
        val settings = ScoreGlobalSettings(
            playbackIndicatorMode = "PULSE",
            followScrollEnabled = false,
            metronomeEnabled = true,
            playbackEndBehavior = "LOOP_CURRENT",
            notationLayout = "MERGED_STAVES",
            partColorMode = "ENHANCED",
        )

        assertEquals(settings, ScoreSettingsCodec.decodeGlobal(ScoreSettingsCodec.encodeGlobal(settings)))
    }

    @Test
    fun `invalid global settings fall back independently`() {
        val restored = ScoreSettingsCodec.decodeGlobal(
            """{
                "playbackIndicatorMode":"unknown",
                "followScrollEnabled":false,
                "metronomeEnabled":"not-a-boolean",
                "playbackEndBehavior":"bad",
                "notationLayout":"MERGED_STAVES",
                "partColorMode":"bad"
            }""".trimIndent(),
        )

        assertEquals("LINE", restored.playbackIndicatorMode)
        assertEquals(false, restored.followScrollEnabled)
        assertEquals(false, restored.metronomeEnabled)
        assertEquals("PAUSE_AT_END", restored.playbackEndBehavior)
        assertEquals("MERGED_STAVES", restored.notationLayout)
        assertEquals("DEFAULT", restored.partColorMode)
    }

    @Test
    fun `legacy global settings keep metronome off`() {
        val restored = ScoreSettingsCodec.decodeGlobal(
            """{"followScrollEnabled":false}""",
        )

        assertEquals(false, restored.metronomeEnabled)
    }

    @Test
    fun `scoped settings round trip and sanitize unsafe values`() {
        val settings = ScoreScopedSettings(
            customPlaybackBpm = 75,
            staffMode = "SELECTED_PARTS",
            selectedTrackMask = 10,
            mutedTrackIndexesByVariant = mapOf("OCR" to listOf(1, 3)),
        )
        assertEquals(settings, ScoreSettingsCodec.decodeScoped(ScoreSettingsCodec.encodeScoped(settings)))

        val invalid = ScoreSettingsCodec.decodeScoped(
            """{
                "customPlaybackBpm":999,
                "staffMode":"bad",
                "selectedTrackMask":0,
                "mutedTrackIndexesByVariant":{
                    "OCR":[-1,0,0,31,2],
                    "unknown":[1]
                }
            }""".trimIndent(),
        )
        assertNull(invalid.customPlaybackBpm)
        assertEquals("ALL_STAVES", invalid.staffMode)
        assertEquals(1, invalid.selectedTrackMask)
        assertEquals(mapOf("OCR" to listOf(0, 2)), invalid.mutedTrackIndexesByVariant)
    }

    @Test
    fun `lyric script setting round trips and falls back to auto`() {
        val settings = ScoreGlobalSettings(lyricScript = "ZH_HANT")
        assertEquals(settings, ScoreSettingsCodec.decodeGlobal(ScoreSettingsCodec.encodeGlobal(settings)))
        assertEquals("AUTO", ScoreSettingsCodec.decodeGlobal("""{"lyricScript":"EN"}""").lyricScript)
        assertEquals("AUTO", ScoreSettingsCodec.decodeGlobal("""{"partColorMode":"ENHANCED"}""").lyricScript)
    }
}
