@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package io.github.cluno1.sonorus.features.scores.data

import android.content.Context
import alphaTab.PlayerMode
import alphaTab.Settings
import alphaTab.core.ecmaScript.Uint8Array
import alphaTab.importer.ScoreLoader
import alphaTab.model.Score
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BundledScoreLoader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val assets = context.applicationContext.assets
    private val revisionStore = ScoreRevisionStore(context)

    suspend fun loadVariant(
        variant: BundledScoreVariant,
        canonicalMusicXml: ByteArray
    ): LoadedScore = withContext(ioDispatcher) {
        loadVariantNow(variant, canonicalMusicXml)
    }

    suspend fun saveWorkingCopy(
        variant: BundledScoreVariant,
        canonicalMusicXml: ByteArray
    ) = withContext(ioDispatcher) {
        // Parsing both projections before writing prevents a malformed draft replacing the last
        // known-good working copy.
        loadVariantNow(variant, canonicalMusicXml)
        revisionStore.saveWorkingCopy(variant, canonicalMusicXml)
    }

    suspend fun loadSoundFont(): ByteArray = withContext(ioDispatcher) {
        ScoreAssetReader.read {
            assets.open(SOUND_FONT_ASSET)
        }
    }

    private fun loadVariantNow(
        variant: BundledScoreVariant,
        canonicalMusicXml: ByteArray
    ): LoadedScore {
        val displayBytes = when (variant) {
            BundledScoreVariant.OCR -> canonicalMusicXml
            BundledScoreVariant.MIDI ->
                ScoreDisplaySanitizer.keepFirstVisibleMetronome(canonicalMusicXml)
        }
        return LoadedScore(
            displayScore = ScoreLoader.loadScoreFromBytes(
                Uint8Array(displayBytes.toUByteArray()),
                scoreSettings()
            ),
            playbackScore = ScoreLoader.loadScoreFromBytes(
                Uint8Array(canonicalMusicXml.toUByteArray()),
                scoreSettings()
            ),
            canonicalMusicXml = canonicalMusicXml,
            displayMusicXml = displayBytes
        )
    }

    private fun scoreSettings() = Settings().apply {
        player.playerMode = PlayerMode.Disabled
        player.enableCursor = false
        player.enableUserInteraction = false
    }

    private companion object {
        const val SOUND_FONT_ASSET = "sonivox.sf2"
    }
}

internal enum class BundledScoreVariant(val assetName: String) {
    OCR("gmusic_321_rev1_ocr.musicxml"),
    MIDI("gmusic_321_midi_20210702.musicxml")
}

internal class LoadedScore(
    val displayScore: Score,
    val playbackScore: Score,
    val canonicalMusicXml: ByteArray,
    private val displayMusicXml: ByteArray,
    private val lyricRoot: LoadedScore? = null,
    val lyricScript: ScoreLyricScript? = null,
) {
    /** Chinese lyric scripts present in this score; empty for untagged or lyric-free files. */
    val availableLyricScripts: Set<ScoreLyricScript> by lazy {
        lyricRoot?.availableLyricScripts
            ?: MusicXmlLyricLanguageProjector.availableScripts(displayMusicXml)
    }
    private val lyricVariants = mutableMapOf<ScoreLyricScript, LoadedScore>()

    /**
     * Returns a display variant showing only [script] lyrics. The playback score and canonical
     * bytes are shared with the original, so switching scripts never restarts playback.
     * Parses MusicXML: call off the main thread.
     */
    fun withLyricScript(script: ScoreLyricScript): LoadedScore {
        val base = lyricRoot ?: this
        if (script == lyricScript || base.availableLyricScripts.isEmpty()) return this
        return synchronized(base.lyricVariants) {
            base.lyricVariants.getOrPut(script) {
                val projected = MusicXmlLyricLanguageProjector.project(base.displayMusicXml, script)
                if (projected === base.displayMusicXml) {
                    base
                } else {
                    LoadedScore(
                        displayScore = ScoreLoader.loadScoreFromBytes(
                            Uint8Array(projected.toUByteArray()),
                            Settings().apply {
                                player.playerMode = PlayerMode.Disabled
                                player.enableCursor = false
                                player.enableUserInteraction = false
                            },
                        ),
                        playbackScore = base.playbackScore,
                        canonicalMusicXml = base.canonicalMusicXml,
                        displayMusicXml = projected,
                        lyricRoot = base,
                        lyricScript = script,
                    )
                }
            }
        }
    }

    val displayPartLabels: List<String> by lazy {
        ChoirScoreDisplayProjector.trackLabels(displayMusicXml)
    }
    val displayPartColorIndexesByTrack: Map<Int, List<Int>> by lazy {
        ChoirScoreDisplayProjector.trackColorIndexes(displayMusicXml)
            .mapIndexed { trackIndex, colorIndex -> trackIndex to listOf(colorIndex) }
            .toMap()
    }
    val fallbackMergedDisplayScore: MergedDisplayScore by lazy {
        MergedDisplayScore(
            score = displayScore,
            sourcePartIndexesByTrack = displayScore.tracks.toList().associate { track ->
                val trackIndex = track.index.toInt()
                trackIndex to listOf(trackIndex)
            },
            colorPartIndexesByTrack = displayPartColorIndexesByTrack,
        )
    }
    private val mergedDisplayScores = mutableMapOf<Int, MergedDisplayScore>()
    private val lyricCarriedScores = mutableMapOf<Int, MergedDisplayScore?>()

    /**
     * Separate-parts view showing only [visibleTrackIndexes]: when the lyric-carrying part is
     * hidden, returns a display score whose top visible part carries the lyrics; null when the
     * normal display score already shows them. Track indexes are unchanged.
     */
    suspend fun loadLyricCarriedDisplayScore(visibleTrackIndexes: Set<Int>): MergedDisplayScore? =
        withContext(Dispatchers.Default) {
            val mask = visibleTrackIndexes.fold(0) { acc, index -> acc or (1 shl index) }
            synchronized(lyricCarriedScores) {
                if (lyricCarriedScores.containsKey(mask)) return@withContext lyricCarriedScores[mask]
            }
            val projected = MusicXmlLyricCarrierProjector.project(displayMusicXml, visibleTrackIndexes)
            val result = if (projected === displayMusicXml) {
                null
            } else {
                val score = ScoreLoader.loadScoreFromBytes(
                    Uint8Array(projected.toUByteArray()),
                    Settings().apply {
                        player.playerMode = PlayerMode.Disabled
                        player.enableCursor = false
                        player.enableUserInteraction = false
                    },
                )
                MergedDisplayScore(
                    score = score,
                    sourcePartIndexesByTrack = score.tracks.toList().associate { track ->
                        track.index.toInt() to listOf(track.index.toInt())
                    },
                    colorPartIndexesByTrack = displayPartColorIndexesByTrack,
                )
            }
            synchronized(lyricCarriedScores) { lyricCarriedScores[mask] = result }
            result
        }

    suspend fun loadMergedDisplayScore(selectedTrackIndexes: Set<Int>): MergedDisplayScore =
        withContext(Dispatchers.Default) {
            val selectionMask = selectedTrackIndexes.fold(0) { mask, index ->
                mask or (1 shl index)
            }
            synchronized(mergedDisplayScores) {
                mergedDisplayScores[selectionMask]
            } ?: ChoirScoreDisplayProjector.projectWithMapping(
                // Keep words visible when the lyric-carrying part is not among the merged parts.
                source = MusicXmlLyricCarrierProjector.project(displayMusicXml, selectedTrackIndexes),
                selectedTrackIndexes = selectedTrackIndexes,
            ).let { projection ->
                val score = ScoreLoader.loadScoreFromBytes(
                    Uint8Array(projection.musicXml.toUByteArray()),
                    Settings().apply {
                        player.playerMode = PlayerMode.Disabled
                        player.enableCursor = false
                        player.enableUserInteraction = false
                    },
                )
                MergedDisplayScore(
                    score = score,
                    sourcePartIndexesByTrack = projection.groups.mapIndexed { trackIndex, group ->
                        trackIndex to group.sourcePartIndexes
                    }.toMap(),
                    colorPartIndexesByTrack = projection.groups.mapIndexed { trackIndex, group ->
                        trackIndex to group.colorPartIndexes
                    }.toMap(),
                )
            }.also { mergedScore ->
                synchronized(mergedDisplayScores) {
                    mergedDisplayScores[selectionMask] = mergedScore
                }
            }
        }
}

internal data class MergedDisplayScore(
    val score: Score,
    val sourcePartIndexesByTrack: Map<Int, List<Int>>,
    val colorPartIndexesByTrack: Map<Int, List<Int>>,
)
