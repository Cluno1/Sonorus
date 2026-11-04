@file:OptIn(kotlin.contracts.ExperimentalContracts::class, ExperimentalUnsignedTypes::class)

package io.github.cluno1.sonorus.features.scores.presentation

import alphaTab.model.Beat
import alphaTab.rendering.utils.BoundsLookup
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min

/** Pulses around active note heads without covering or changing their original glyphs. */
internal class ScorePlaybackOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val playbackLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 29, 72)
        style = Paint.Style.FILL
    }
    private val pulseRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 29, 72)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val animatedMarker = RectF()
    private var activeBeats: List<Beat> = emptyList()
    private var markers: List<RectF> = emptyList()
    private var mode = ScorePlaybackIndicatorMode.LINE
    private var playbackLineVisible = false
    private var playbackLineX = 0f
    private var playbackLineTop = 0f
    private var playbackLineHeight = 0f
    private var pulseProgress = 0f
    private var playbackLineAnimator: ValueAnimator? = null
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 520L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = DecelerateInterpolator()
        addUpdateListener { animation ->
            pulseProgress = animation.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun setMode(mode: ScorePlaybackIndicatorMode) {
        if (this.mode == mode) return
        this.mode = mode
        if (mode != ScorePlaybackIndicatorMode.LINE) {
            playbackLineAnimator?.cancel()
        }
        updatePulseAnimation()
        invalidate()
    }

    fun placePlaybackLine(x: Double, y: Double, height: Double) {
        playbackLineAnimator?.cancel()
        playbackLineAnimator = null
        playbackLineX = (x * density).toFloat()
        playbackLineTop = (y * density).toFloat()
        playbackLineHeight = (height * density).toFloat()
        playbackLineVisible = true
        bringToFront()
        invalidate()
    }

    fun animatePlaybackLine(targetX: Double, durationMillis: Long) {
        val targetPx = (targetX * density).toFloat()
        playbackLineAnimator?.cancel()
        if (durationMillis <= 0L || !targetPx.isFinite()) {
            if (targetPx.isFinite()) playbackLineX = targetPx
            invalidate()
            return
        }
        playbackLineAnimator = ValueAnimator.ofFloat(playbackLineX, targetPx).apply {
            duration = durationMillis
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                playbackLineX = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun hidePlaybackLine() {
        playbackLineAnimator?.cancel()
        playbackLineAnimator = null
        playbackLineVisible = false
        invalidate()
    }

    fun showBeats(beats: List<Beat>, lookup: BoundsLookup?) {
        activeBeats = beats
        updateMarkers(lookup)
    }

    fun refresh(lookup: BoundsLookup?) {
        updateMarkers(lookup)
    }

    /** The printed bar number sits just above the left edge of its first staff. */
    fun barIndexAt(xPx: Float, yPx: Float, lookup: BoundsLookup?): Int? {
        if (lookup?.isFinished != true) return null
        val x = xPx / density
        val y = yPx / density
        return lookup.staffSystems.toList().asSequence()
            .flatMap { it.bars.toList().asSequence() }
            .firstOrNull { bar ->
                val bounds = bar.visualBounds
                x >= bounds.x - 8.0 &&
                    x <= min(bounds.x + 80.0, bounds.x + bounds.w) &&
                    y >= bounds.y - 28.0 && y <= bounds.y + 18.0
            }?.index?.toInt()
    }

    private fun updateMarkers(lookup: BoundsLookup?) {
        markers = if (lookup?.isFinished == true) {
            activeBeats.flatMap { beat ->
                lookup.findBeats(beat)?.toList().orEmpty().flatMap { beatBounds ->
                    beatBounds.notes?.toList().orEmpty().map { noteBounds ->
                        val noteHead = noteBounds.noteHeadBounds
                        val centerX = (noteHead.x + noteHead.w / 2.0) * density
                        val centerY = (noteHead.y + noteHead.h / 2.0) * density
                        // Keep the ring outside the glyph. The minimum size also handles
                        // alphaTab glyphs which report a point-sized note-head bound.
                        val ringGap = 2.5 * density
                        val halfWidth = max(noteHead.w * density / 2.0, 5.0 * density) + ringGap
                        val halfHeight = max(noteHead.h * density / 2.0, 3.5 * density) + ringGap
                        RectF(
                            (centerX - halfWidth).toFloat(),
                            (centerY - halfHeight).toFloat(),
                            (centerX + halfWidth).toFloat(),
                            (centerY + halfHeight).toFloat(),
                        )
                    }
                }
            }
        } else {
            emptyList()
        }
        updatePulseAnimation()
        bringToFront()
        invalidate()
    }

    private fun updatePulseAnimation() {
        if (markers.isEmpty() || mode != ScorePlaybackIndicatorMode.PULSE) {
            pulseAnimator.cancel()
            pulseProgress = 0f
        } else if (isAttachedToWindow && !pulseAnimator.isRunning) {
            pulseAnimator.start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePulseAnimation()
    }

    override fun onDetachedFromWindow() {
        playbackLineAnimator?.cancel()
        playbackLineAnimator = null
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == ScorePlaybackIndicatorMode.LINE && playbackLineVisible) {
            val halfWidth = max(1f, density * 0.75f)
            canvas.drawRect(
                playbackLineX - halfWidth,
                playbackLineTop,
                playbackLineX + halfWidth,
                playbackLineTop + playbackLineHeight,
                playbackLinePaint,
            )
            return
        }
        val expansion = pulseProgress * 2.5f * density
        pulseRingPaint.alpha = (220f - pulseProgress * 80f).toInt()
        markers.forEach { marker ->
            animatedMarker.set(
                marker.left - expansion,
                marker.top - expansion,
                marker.right + expansion,
                marker.bottom + expansion,
            )
            canvas.drawOval(animatedMarker, pulseRingPaint)
        }
    }
}
