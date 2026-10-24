package io.github.cluno1.sonorus.features.scores.presentation

import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView

/** Frames the viewport must keep the same position before it counts as settled. */
internal const val SCORE_SCROLL_SETTLE_FRAMES = 3

/**
 * Consecutive scrolling frames the surface may still report a requested layout before it counts
 * as lost. A healthy request made during a frame's scroll dispatch is serviced by that frame's
 * traversal, so it is never seen pending on two frames in a row.
 */
internal const val SCORE_STUCK_LAYOUT_FRAMES = 2

/**
 * Decides, frame by frame while the viewport moves, when a pending surface layout is stuck.
 *
 * issue 119: emulator logs showed the surface `layoutRequested=true` on every frame of a manual
 * scroll after pausing. alphaTab then ignores all scroll events, so the score below the fold stayed
 * white until the scroll settled. Repairing as soon as the request is seen stuck makes the next
 * partials load while the reader is still dragging.
 */
internal class ScoreStuckLayoutDetector(private val requiredPendingFrames: Int = SCORE_STUCK_LAYOUT_FRAMES) {
    private var pendingFrames = 0

    fun observe(layoutRequested: Boolean): Boolean {
        if (!layoutRequested) {
            pendingFrames = 0
            return false
        }
        pendingFrames++
        if (pendingFrames >= requiredPendingFrames) {
            pendingFrames = 0
            return true
        }
        return false
    }

    fun reset() {
        pendingFrames = 0
    }
}

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
 * - While the reader scrolls, a surface layout request still pending on two consecutive frames is
 *   run directly as well, so missing partials load during the drag, not only after it stops.
 *   Healthy requests are serviced within their frame and never trigger this.
 *
 * Why: issue 119 logs on iQOO Debug 82 showed the surface still `layoutRequested=true` when the
 * reader stopped scrolling, then no further partial requests: a white score at scrollY=0. With
 * only the settle check (Debug 84) the score came back, but anything scrolled into view during the
 * drag stayed white until the finger stopped.
 *
 * Rejected: requestLayout()/invalidate() only (can be lost, the original fix); re-rendering the
 * whole score (reloads every partial, resets nothing useful); disabling lazy rendering (memory);
 * "render the whole score once" on back-to-top — alphaTab's onLayout() recycles the bitmap of
 * every partial outside the viewport on the very next layout, so nothing off-screen can be kept,
 * and a full-height bitmap set is tens of MB (1078x19545 ARGB ≈ 84 MB for one hymn).
 */
internal class ScoreScrollRenderRecovery(
    private val renderSurface: View,
    private val scrollView: ScrollView,
    // issue 119: scrolls written by the playback follow animation are not a user settle; the
    // follow handler calls requestCheck() itself when its animation stops.
    private val programmaticScrollActive: () -> Boolean = { false },
) : ViewTreeObserver.OnScrollChangedListener {
    private val settleDetector = ScoreScrollSettleDetector()
    private val stuckLayoutDetector = ScoreStuckLayoutDetector()
    private var inScrollRepairs = 0
    private var started = false
    private var monitoringSettle = false
    private var lastCheckedScrollY = Int.MIN_VALUE

    private val settleRunnable = object : Runnable {
        override fun run() {
            if (!started || !scrollView.isAttachedToWindow || programmaticScrollActive()) {
                monitoringSettle = false
                settleDetector.reset()
                stuckLayoutDetector.reset()
                return
            }
            if (stuckLayoutDetector.observe(renderSurface.isLayoutRequested)) {
                inScrollRepairs++
                runSurfaceLayout()
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
        stuckLayoutDetector.reset()
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
        stuckLayoutDetector.reset()
        scrollView.removeCallbacks(settleRunnable)
        val observer = scrollView.viewTreeObserver
        if (observer.isAlive) observer.removeOnScrollChangedListener(this)
    }

    private fun forceVisiblePartialCheck(reason: String) {
        if (!renderSurface.isAttachedToWindow) return
        lastCheckedScrollY = scrollView.scrollY
        val surfaceLayoutPending = renderSurface.isLayoutRequested
        val stuckAncestors = pendingLayoutAncestors()
        runSurfaceLayout()
        if (stuckAncestors > 0) {
            // A pending flag nobody will service blocks every later requestLayout(); a full
            // traversal from the window root lays those ancestors out and clears it.
            scrollView.rootView.parent?.requestLayout()
        }
        renderSurface.postInvalidateOnAnimation()
        // Bounded diagnostics: one line per settle / stop event, never per frame; in-scroll
        // repairs are only counted here.
        Log.i(
            "ScoreRenderRecovery",
            "partial check ($reason): scrollY=$lastCheckedScrollY viewport=${scrollView.height} " +
                "surface=${renderSurface.width}x${renderSurface.height} " +
                "surfaceLayoutPending=$surfaceLayoutPending stuckAncestors=$stuckAncestors " +
                "inScrollRepairs=$inScrollRepairs",
        )
        inScrollRepairs = 0
    }

    /**
     * Runs alphaTab's onMeasure (marks its visible rect dirty) and onLayout (refreshes the visible
     * rect, requests missing partials, clears _layoutDirty) right now, with the surface's current
     * geometry, instead of waiting for a traversal that may never come.
     */
    private fun runSurfaceLayout() {
        if (renderSurface.width <= 0 || renderSurface.height <= 0) return
        renderSurface.forceLayout()
        renderSurface.measure(
            View.MeasureSpec.makeMeasureSpec(renderSurface.measuredWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(renderSurface.measuredHeight, View.MeasureSpec.EXACTLY),
        )
        renderSurface.layout(renderSurface.left, renderSurface.top, renderSurface.right, renderSurface.bottom)
        renderSurface.postInvalidateOnAnimation()
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
