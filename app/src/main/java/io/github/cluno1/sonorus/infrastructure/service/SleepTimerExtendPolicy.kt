package io.github.cluno1.sonorus.infrastructure.service

/**
 * issue 180: when a volume change may extend the sleep timer.
 *
 * Invariant: the countdown only moves forward. A volume change extends it only while the timer is
 * fading out (its last [FADE_WINDOW_MS]), and then by [EXTEND_MS] from now — the "I'm still
 * listening" gesture the extension was meant for.
 *
 * Why: the service restarted the whole duration on every STREAM_MUSIC volume broadcast. Those also
 * come from Bluetooth volume sync, output switches, the app's own volume guard and some ROMs on screen
 * on, so a 30-minute timer kept jumping back to 30:00 while the screen was off and never ran out;
 * short timers usually finished before such an event, which hid the bug.
 *
 * Silent failure if changed: widening the window back to "any time" brings the endless timer back
 * with no error anywhere; extending by the full duration makes one late tap cost the whole timer again.
 */
internal object SleepTimerExtendPolicy {
    /** The fade-out lasts this long at the end of a fading timer. */
    const val FADE_WINDOW_MS = 10_000L

    /** How long a volume change during the fade-out keeps playing. */
    const val EXTEND_MS = 5 * 60_000L

    fun shouldExtend(fadeOut: Boolean, remainingMs: Long): Boolean =
        fadeOut && remainingMs in 1..FADE_WINDOW_MS
}
