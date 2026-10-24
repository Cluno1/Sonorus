package io.github.cluno1.sonorus.features.scores.presentation

import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView

/** Frames the viewport must keep the same position before it counts as settled. */
internal const val SCORE_SCROLL_SETTLE_FRAMES = 3

/**
 * Detects the frame where a scrolling viewport has stopped moving.
 *
 * issue 119: one unchanged frame is not enough — a slow linear follow animation moves less than
 * a pixel per frame, so integer scrollY often repeats once while still moving. That fired the
 * recovery mid-animation and not when the user really stopped.
 */
internal class ScoreScrollSettleDetector(private val requiredStableFrames: Int = SCORE_SCROLL_SETTLE_FRAMES) {
    private var previousPosition: Int? = null
    private var stableFrames = 0

    fun observe(position: Int): Boolean {
        if (previousPosition == position) {
            stableFrames++
            if (stableFrames >= requiredStableFrames - 1) {
                reset()
                return true
            }
            return false
        }
        previousPosition = position
        stableFrames = 0
        return false
    }

    fun reset() {
        previousPosition = null
        stableFrames = 0
    }
}

/**
 * Gives alphaTab's Android lazy-render surface one final layout pass after a fling settles.
 *
 * AlphaTab recycles bitmap partials outside the viewport. During a fast reverse fling its
 * single scroll listener can coalesce the final scroll event while a previous layout is still
 * pending, leaving the final viewport without a request to restore its recycled partials.
 */
internal class ScoreScrollRenderRecovery(
    private val renderSurface: View,
    private val scrollView: ScrollView,
    // issue 119: scrolls written by the playback follow animation are not a user settle; the
    // follow handler calls requestCheck() itself when its animation stops.
    private val programmaticScrollActive: () -> Boolean = { false },
) : ViewTreeObserver.OnScrollChangedListener {
    private val settleDetector = ScoreScrollSettleDetector()
    private var started = false
    private var monitoringSettle = false

    private val settleRunnable = object : Runnable {
        override fun run() {
            if (!started || !scrollView.isAttachedToWindow || programmaticScrollActive()) {
                monitoringSettle = false
                settleDetector.reset()
                return
            }
            if (settleDetector.observe(scrollView.scrollY)) {
                monitoringSettle = false
                forceVisiblePartialCheck("scroll settled")
            } else {
                scrollView.postOnAnimation(this)
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        scrollView.viewTreeObserver.addOnScrollChangedListener(this)
    }

    override fun onScrollChanged() {
        if (!started || monitoringSettle) return
        monitoringSettle = true
        settleDetector.reset()
        scrollView.postOnAnimation(settleRunnable)
    }

    /** issue 119: re-check the visible partials now (follow animation stopped, playback paused). */
    fun requestCheck(reason: String) {
        if (!started) return
        scrollView.postOnAnimation { if (started) forceVisiblePartialCheck(reason) }
    }

    fun close() {
        if (!started) return
        started = false
        monitoringSettle = false
        settleDetector.reset()
        scrollView.removeCallbacks(settleRunnable)
        val observer = scrollView.viewTreeObserver
        if (observer.isAlive) observer.removeOnScrollChangedListener(this)
    }

    private fun forceVisiblePartialCheck(reason: String) {
        if (!renderSurface.isAttachedToWindow) return
        // Bounded diagnostics: one line per settle / stop event, never per frame.
        Log.i(
            "ScoreRenderRecovery",
            "partial check ($reason): scrollY=${scrollView.scrollY} viewport=${scrollView.height} " +
                "surface=${renderSurface.width}x${renderSurface.height} layoutRequested=${renderSurface.isLayoutRequested}",
        )
        // forceLayout guarantees AlphaTabRenderSurface.onMeasure() refreshes its private
        // visible-rect dirty flag before onLayout() requests any missing lazy partials.
        renderSurface.forceLayout()
        renderSurface.requestLayout()
        renderSurface.postInvalidateOnAnimation()
    }
}
