/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.features.scores.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class ScoreGlobalSettings(
    val playbackIndicatorMode: String = "LINE",
    val followScrollEnabled: Boolean = true,
    val metronomeEnabled: Boolean = false,
    val playbackEndBehavior: String = "PAUSE_AT_END",
    val notationLayout: String = "SEPARATE_PARTS",
    val partColorMode: String = "DEFAULT",
    /** "AUTO" follows the app language; otherwise a [ScoreLyricScript] name. */
    val lyricScript: String = ScoreLyricScript.AUTO,
)

internal data class ScoreScopedSettings(
    val customPlaybackBpm: Int? = null,
    val staffMode: String = "ALL_STAVES",
    val selectedTrackMask: Int = 1,
    val mutedTrackIndexesByVariant: Map<String, List<Int>> = emptyMap(),
)

internal object ScoreSettingsCodec {
    private val indicatorModes = setOf("LINE", "PULSE")
    private val endBehaviors = setOf("PAUSE_AT_END", "LOOP_CURRENT")
    private val notationLayouts = setOf("SEPARATE_PARTS", "MERGED_STAVES")
    private val partColorModes = setOf("DEFAULT", "ENHANCED")
    private val lyricScripts = setOf(ScoreLyricScript.AUTO) + ScoreLyricScript.entries.map { it.name }
    private val staffModes = setOf("ALL_STAVES", "SELECTED_PARTS")
    private val scoreVariants = setOf("OCR", "MIDI")

    fun encodeGlobal(settings: ScoreGlobalSettings): String = JsonObject().apply {
        addProperty("playbackIndicatorMode", settings.playbackIndicatorMode)
        addProperty("followScrollEnabled", settings.followScrollEnabled)
        addProperty("metronomeEnabled", settings.metronomeEnabled)
        addProperty("playbackEndBehavior", settings.playbackEndBehavior)
        addProperty("notationLayout", settings.notationLayout)
        addProperty("partColorMode", settings.partColorMode)
        addProperty("lyricScript", settings.lyricScript)
    }.toString()

    fun decodeGlobal(raw: String?): ScoreGlobalSettings {
        val json = raw.jsonObjectOrNull() ?: return ScoreGlobalSettings()
        return ScoreGlobalSettings(
            playbackIndicatorMode = json.string("playbackIndicatorMode")
                ?.takeIf(indicatorModes::contains)
                ?: "LINE",
            followScrollEnabled = json.boolean("followScrollEnabled") ?: true,
            metronomeEnabled = json.boolean("metronomeEnabled") ?: false,
            playbackEndBehavior = json.string("playbackEndBehavior")
                ?.takeIf(endBehaviors::contains)
                ?: "PAUSE_AT_END",
            notationLayout = json.string("notationLayout")
                ?.takeIf(notationLayouts::contains)
                ?: "SEPARATE_PARTS",
            partColorMode = json.string("partColorMode")
                ?.takeIf(partColorModes::contains)
                ?: "DEFAULT",
            lyricScript = json.string("lyricScript")
                ?.takeIf(lyricScripts::contains)
                ?: ScoreLyricScript.AUTO,
        )
    }

    fun encodeScoped(settings: ScoreScopedSettings): String = JsonObject().apply {
        settings.customPlaybackBpm?.let { addProperty("customPlaybackBpm", it) }
        addProperty("staffMode", settings.staffMode)
        addProperty("selectedTrackMask", settings.selectedTrackMask)
        add("mutedTrackIndexesByVariant", JsonObject().apply {
            settings.mutedTrackIndexesByVariant.forEach { (variant, indexes) ->
                add(variant, com.google.gson.JsonArray().apply {
                    indexes.forEach { add(it) }
                })
            }
        })
    }.toString()

    fun decodeScoped(raw: String?): ScoreScopedSettings {
        val json = raw.jsonObjectOrNull() ?: return ScoreScopedSettings()
        val mutedTracks = json.get("mutedTrackIndexesByVariant")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.entrySet()
            .orEmpty()
            .mapNotNull { (variant, value) ->
                if (variant !in scoreVariants || !value.isJsonArray) {
                    null
                } else {
                    variant to value.asJsonArray
                        .mapNotNull { element ->
                            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                                ?.let { runCatching { it.asInt }.getOrNull() }
                                ?.takeIf { index -> index in 0..30 }
                        }
                        .distinct()
                        .sorted()
                }
            }
            .toMap()
        return ScoreScopedSettings(
            customPlaybackBpm = json.int("customPlaybackBpm")?.takeIf { it in 30..300 },
            staffMode = json.string("staffMode")?.takeIf(staffModes::contains)
                ?: "ALL_STAVES",
            selectedTrackMask = json.int("selectedTrackMask")?.takeIf { it > 0 } ?: 1,
            mutedTrackIndexesByVariant = mutedTracks,
        )
    }

    private fun String?.jsonObjectOrNull(): JsonObject? = this
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject

    private fun JsonObject.string(name: String): String? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString

    private fun JsonObject.boolean(name: String): Boolean? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?.asBoolean

    private fun JsonObject.int(name: String): Int? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.let { runCatching { it.asInt }.getOrNull() }
}

internal class ScoreSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun loadGlobal(): ScoreGlobalSettings = ScoreSettingsCodec.decodeGlobal(
        preferences.getString(KEY_GLOBAL, null),
    )

    fun saveGlobal(settings: ScoreGlobalSettings) = preferences.edit(commit = true) {
        putString(KEY_GLOBAL, ScoreSettingsCodec.encodeGlobal(settings))
    }

    fun loadScore(scoreId: String): ScoreScopedSettings = ScoreSettingsCodec.decodeScoped(
        preferences.getString(scoreKey(scoreId), null),
    )

    fun saveScore(scoreId: String, settings: ScoreScopedSettings) {
        if (scoreId.isBlank()) return
        preferences.edit(commit = true) {
            putString(scoreKey(scoreId), ScoreSettingsCodec.encodeScoped(settings))
        }
    }

    private fun scoreKey(scoreId: String) = "score:${scoreId.trim()}"

    private companion object {
        const val PREFERENCES_NAME = "rhythm_score_settings_v1"
        const val KEY_GLOBAL = "global"
    }
}
