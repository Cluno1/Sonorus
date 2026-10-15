/*
 * SPDX-FileCopyrightText: 2026 Sonorus contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.cluno1.sonorus.shared.presentation.viewmodel

/**
 * Decides whether the app-open update prompt may appear.
 *
 * The prompt is limited to one appearance per local calendar day, whether the user upgrades or
 * skips. Day boundaries use epoch days in the device's local time zone so "once per day" matches
 * what the user sees on the clock rather than a rolling 24-hour window.
 */
internal object StartupUpdatePromptPolicy {
    /** Stored value meaning the prompt has never been shown. */
    const val NEVER_SHOWN: Long = Long.MIN_VALUE

    /** A network check is only worth doing when today's prompt has not been used yet. */
    fun canPromptToday(lastShownEpochDay: Long, todayEpochDay: Long): Boolean =
        lastShownEpochDay != todayEpochDay

    fun shouldPrompt(
        lastShownEpochDay: Long,
        todayEpochDay: Long,
        candidateVersionCode: Int,
        installedVersionCode: Int,
        candidateMinimumSdk: Int,
        deviceSdk: Int,
    ): Boolean = canPromptToday(lastShownEpochDay, todayEpochDay) &&
        candidateVersionCode > installedVersionCode &&
        candidateMinimumSdk <= deviceSdk

    /**
     * Foreground returns re-check at most this often, so switching apps repeatedly does not
     * hammer the update service while no update exists.
     */
    const val RESUME_RECHECK_INTERVAL_MS: Long = 30L * 60L * 1000L
}
