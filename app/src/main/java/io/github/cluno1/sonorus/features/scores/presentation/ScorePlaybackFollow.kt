@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package io.github.cluno1.sonorus.features.scores.presentation

import alphaTab.AlphaTabView
import alphaTab.IScrollHandler
import alphaTab.midi.MidiTickLookupFindBeatResultCursorMode
import alphaTab.rendering.utils.BeatBounds
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val SCORE_FOLLOW_VIEWPORT_FRACTION = 0.36
private const val MAX_SCORE_FOLLOW_ANIMATION_MS = 120_000L

internal fun scoreFollowScrollOffset(
    viewportHeightPx: Int,
    density: Float,
): Double = if (viewportHeightPx > 0 && density > 0f) {
    -(viewportHeightPx / density * SCORE_FOLLOW_VIEWPORT_FRACTION)
} else {
    0.0
}

/**
 * Extra scrollable space needed to keep the final system at the same reading position as
 * every other system. alphaTab 1.8.4's Android render surface ignores its own padding while
 * measuring, so this must be applied to the wrapper's minimum height instead.
 */
internal fun scoreFollowTailSpace(viewportHeightPx: Int): Int =
    if (viewportHeightPx > 0) {
        (viewportHeightPx * (1.0 - SCORE_FOLLOW_VIEWPORT_FRACTION)).roundToInt()
    } else {
        0
    }

/** A scroll this far from where the follow animation last put the view was made by the user. */
internal const val SCORE_FOLLOW_USER_SCROLL_TOLERANCE_PX = 3

/**
 * issue 119: true when the viewport is no longer where the follow animation left it, i.e. a drag,
 * fling or "back to top" moved it. The animation must then stop instead of fighting the user.
 */
internal fun scoreFollowUserMoved(lastApplied: Int?, actual: Int): Boolean =
    lastApplied != null && abs(actual - lastApplied) > SCORE_FOLLOW_USER_SCROLL_TOLERANCE_PX

/** Where the system animation should be after [elapsedFraction] of it, and how long remains. */
internal data class ScoreFollowSegment(val startScroll: Int, val durationMs: Long)

/**
 * issue 119: resuming mid-system (after a pause) continues from the beat's position inside the
 * system instead of jumping back to the system start and replaying the whole duration.
 */
internal fun scoreFollowSegment(
    systemStartScroll: Int,
    targetScroll: Int,
    systemDurationMs: Long,
    elapsedFraction: Double,
): ScoreFollowSegment {
    val fraction = elapsedFraction.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
    return ScoreFollowSegment(
        startScroll = (systemStartScroll + (targetScroll - systemStartScroll) * fraction).roundToInt(),
        durationMs = (systemDurationMs * (1.0 - fraction)).roundToLong().coerceAtLeast(0L),
    )
}

internal fun scoreFollowContentMinHeight(scoreHeightPx: Int, viewportHeightPx: Int): Int =
    scoreHeightPx.coerceAtLeast(0) + scoreFollowTailSpace(viewportHeightPx)

/**
 * Keeps the score moving continuously through a system without alphaTab's instant jump at
 * every line break. The next system naturally arrives at the same reading position because
 * the previous animation ends at the bottom of its system.
 *
 * issue 119 invariants:
 * - The animation never outlives playback: [haltForPause] runs on pause / stop / finish.
 * - The user always wins: each frame first checks that the viewport is still where the animation
 *   left it; a drag, fling or "back to top" stops the animation for the rest of the system.
 * - Every stop ends in [onAnimationSettled] so the lazy render surface re-checks its partials.
 * Why: a 10–120 s linear animation used to keep writing scrollY after pause and while the user
 * browsed back, which (together with alphaTab skipping scroll events while a layout is pending)
 * left the final viewport without restored partials — a white score at scrollY=0.
 */
internal class ScorePlaybackScrollHandler(
    private val displayView: AlphaTabView,
    private val scrollView: ScrollView,
    initiallyEnabled: Boolean = true,
    private val onAnimationSettled: (reason: String) -> Unit = {},
) : IScrollHandler {
    private val density = displayView.resources.displayMetrics.density
    private var scrollAnimator: ValueAnimator? = null
    private var lastAppliedScroll: Int? = null
    private var lastSystemIndex: Int? = null
    private var followEnabled = initiallyEnabled

    /** True while a follow animation is writing scrollY; such scrolls are not user settles. */
    val isAnimating: Boolean
        get() = scrollAnimator != null

    fun setEnabled(enabled: Boolean) {
        if (followEnabled == enabled) return
        followEnabled = enabled
        cancelAnimation("follow toggled")
        lastSystemIndex = null
    }

    /** Playback paused, stopped or finished: stop moving; resuming continues from the beat. */
    fun haltForPause() {
        cancelAnimation("playback halted")
        lastSystemIndex = null
    }

    /** The user took over the viewport; following resumes at the next system. */
    fun yieldToUser(reason: String) {
        cancelAnimation(reason)
    }

    override fun forceScrollTo(currentBeatBounds: BeatBounds) {
        if (!followEnabled) return
        cancelAnimation("seek")
        scrollView.scrollTo(0, systemStartScroll(currentBeatBounds))
        lastSystemIndex = null
    }

    override fun onBeatCursorUpdating(
        startBeat: BeatBounds,
        endBeat: BeatBounds?,
        cursorMode: MidiTickLookupFindBeatResultCursorMode,
        actualBeatCursorStartX: Double,
        actualBeatCursorEndX: Double,
        actualBeatCursorTransitionDuration: Double,
    ) {
        if (!followEnabled) return
        val system = startBeat.barBounds.masterBarBounds.staffSystemBounds ?: return
        val systemIndex = system.index.toInt()
        if (lastSystemIndex == systemIndex && actualBeatCursorTransitionDuration > 0.0) return

        val startScroll = systemStartScroll(startBeat)
        val systemDuration = systemDurationMillis(startBeat)
        val looksLikeSeek = abs(scrollView.scrollY - startScroll) > scrollView.height * 1.5
        val offset = scoreFollowScrollOffset(scrollView.height, density)
        val target = ((system.realBounds.y + system.realBounds.h + offset) * density)
            .roundToInt()
            .coerceAtLeast(0)
        val elapsedFraction = if (system.realBounds.w > 0.0) {
            (startBeat.realBounds.x - system.realBounds.x) / system.realBounds.w
        } else {
            0.0
        }
        val segment = scoreFollowSegment(startScroll, target, systemDuration, elapsedFraction)
        if (lastSystemIndex == null || systemDuration <= 0L || looksLikeSeek) {
            cancelAnimation(null)
            scrollView.scrollTo(0, if (systemDuration > 0L) segment.startScroll else startScroll)
        }

        lastSystemIndex = systemIndex
        if (systemDuration <= 0L || segment.durationMs <= 0L) return

        cancelAnimation(null)
        val animator = ValueAnimator.ofInt(scrollView.scrollY, target)
        animator.duration = segment.durationMs
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { update ->
            if (scrollAnimator !== update) return@addUpdateListener
            if (scoreFollowUserMoved(lastAppliedScroll, scrollView.scrollY)) {
                yieldToUser("user scrolled")
                return@addUpdateListener
            }
            scrollView.scrollTo(0, update.animatedValue as Int)
            // ScrollView clamps; remember where it really is so clamping never looks like a user.
            lastAppliedScroll = scrollView.scrollY
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (scrollAnimator !== animation) return
                scrollAnimator = null
                lastAppliedScroll = null
                onAnimationSettled("system finished")
            }
        })
        lastAppliedScroll = scrollView.scrollY
        scrollAnimator = animator
        animator.start()
    }

    override fun close() {
        val animator = scrollAnimator ?: return
        scrollAnimator = null
        lastAppliedScroll = null
        animator.cancel()
    }

    /** Cancels a running animation; a non-null [reason] logs it and asks for a partial re-check. */
    private fun cancelAnimation(reason: String?) {
        val animator = scrollAnimator ?: return
        scrollAnimator = null
        lastAppliedScroll = null
        animator.cancel()
        if (reason != null) {
            Log.i(SCORE_FOLLOW_TAG, "follow animation stopped: $reason at scrollY=${scrollView.scrollY}")
            onAnimationSettled(reason)
        }
    }

    private fun systemStartScroll(beatBounds: BeatBounds): Int {
        val offset = scoreFollowScrollOffset(scrollView.height, density)
        return ((beatBounds.barBounds.masterBarBounds.staffSystemBounds?.realBounds?.y
            ?: beatBounds.barBounds.masterBarBounds.realBounds.y) + offset)
            .times(density)
            .roundToInt()
            .coerceAtLeast(0)
    }

    private fun systemDurationMillis(beatBounds: BeatBounds): Long {
        val systemBars = beatBounds.barBounds.masterBarBounds.staffSystemBounds?.bars?.toList()
            ?: return 0L
        val score = displayView.api.score ?: return 0L
        val tickCache = displayView.api.tickCache ?: return 0L
        var duration = 0.0

        systemBars.forEach { barBounds ->
            val masterBar = score.masterBars[barBounds.index.toInt()]
            val lookup = tickCache.getMasterBar(masterBar)
            val tempoChanges = lookup.tempoChanges.toList()
            if (tempoChanges.isEmpty()) {
                duration += scoreTicksToMillis(lookup.end - lookup.start, lookup.tempo)
                return@forEach
            }

            var tempo = tempoChanges.first().tempo
            var tick = tempoChanges.first().tick
            tempoChanges.drop(1).forEach { change ->
                duration += scoreTicksToMillis(change.tick - tick, tempo)
                tempo = change.tempo
                tick = change.tick
            }
            duration += scoreTicksToMillis(lookup.end - tick, tempo)
        }

        val playbackSpeed = displayView.api.playbackSpeed.takeIf { it > 0.0 } ?: 1.0
        return (duration / playbackSpeed)
            .takeIf { it.isFinite() && it > 0.0 }
            ?.roundToLong()
            ?.coerceAtMost(MAX_SCORE_FOLLOW_ANIMATION_MS)
            ?: 0L
    }
}

private const val SCORE_FOLLOW_TAG = "ScoreFollow"

private fun scoreTicksToMillis(ticks: Double, tempo: Double): Double =
    if (ticks > 0.0 && tempo > 0.0) ticks * (60_000.0 / (tempo * 960.0)) else 0.0
