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
 * Makes alphaTab's Android lazy-render surface restore the partials of the current viewport.
 *
 * Invariants (issue 43 / 119):
 * - alphaTab's AlphaTabRenderSurface ignores every scroll while `_layoutDirty` is true and only
 *   clears it in onLayout(); partials outside the viewport are recycled and are re-requested only
 *   from onLayout(). If a layout never runs, scrolling back shows white forever.
 * - A requestLayout() can be lost: when it is issued while an ancestor already carries a pending
 *   layout flag that no traversal will service (e.g. a requestLayout made during a layout pass),
 *   View.requestLayout() stops propagating at that ancestor. So after the viewport settles this
 *   class does not ask for a layout — it runs the surface's measure + layout itself, and if an
 *   ancestor still claims a pending layout it asks the window root for a full traversal.
 * - Only this ScrollView's own scrollY changes start a settle check; the ViewTreeObserver fires
 *   for any scroll in the window and used to force a layout every 50 ms at a constant position.
 *
 * Why: issue 119 logs on iQOO Debug 82 showed the surface still `layoutRequested=true` when the
 * reader stopped scrolling, then no further partial requests: a white score at scrollY=0.
 *
 * Rejected: requestLayout()/invalidate() only (can be lost, the original fix); re-rendering the
 * whole score (reloads every partial, resets nothing useful); disabling lazy rendering (memory).
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
    private var lastCheckedScrollY = Int.MIN_VALUE

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
        // Window-wide callback: ignore it unless this score's own viewport actually moved.
        if (!started || monitoringSettle || scrollView.scrollY == lastCheckedScrollY) return
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
        lastCheckedScrollY = scrollView.scrollY
        val surfaceLayoutPending = renderSurface.isLayoutRequested
        val stuckAncestors = pendingLayoutAncestors()
        // Run alphaTab's onMeasure (marks its visible rect dirty) and onLayout (refreshes the
        // visible rect, requests missing partials, clears _layoutDirty) right now, with the
        // surface's current geometry, instead of waiting for a traversal that may never come.
        if (renderSurface.width > 0 && renderSurface.height > 0) {
            renderSurface.forceLayout()
            renderSurface.measure(
                View.MeasureSpec.makeMeasureSpec(renderSurface.measuredWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(renderSurface.measuredHeight, View.MeasureSpec.EXACTLY),
            )
            renderSurface.layout(renderSurface.left, renderSurface.top, renderSurface.right, renderSurface.bottom)
        }
        if (stuckAncestors > 0) {
            // A pending flag nobody will service blocks every later requestLayout(); a full
            // traversal from the window root lays those ancestors out and clears it.
            scrollView.rootView.parent?.requestLayout()
        }
        renderSurface.postInvalidateOnAnimation()
        // Bounded diagnostics: one line per settle / stop event, never per frame.
        Log.i(
            "ScoreRenderRecovery",
            "partial check ($reason): scrollY=$lastCheckedScrollY viewport=${scrollView.height} " +
                "surface=${renderSurface.width}x${renderSurface.height} " +
                "surfaceLayoutPending=$surfaceLayoutPending stuckAncestors=$stuckAncestors",
        )
    }

    /** Ancestors (up to the window root) still flagged for a layout after the viewport settled. */
    private fun pendingLayoutAncestors(): Int {
        var count = 0
        var parent = renderSurface.parent
        while (parent is View) {
            if (parent.isLayoutRequested) count++
            parent = parent.parent
        }
        return count
    }
}
