package io.github.cluno1.sonorus.shared.presentation.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupUpdatePromptPolicyTest {
    private val today = 20_376L

    private fun prompt(
        lastShown: Long = StartupUpdatePromptPolicy.NEVER_SHOWN,
        candidate: Int = 101,
        installed: Int = 100,
        minSdk: Int = 26,
        deviceSdk: Int = 35,
    ) = StartupUpdatePromptPolicy.shouldPrompt(lastShown, today, candidate, installed, minSdk, deviceSdk)

    @Test
    fun `prompts when a newer build exists and nothing was shown before`() {
        assertTrue(prompt())
    }

    @Test
    fun `does not prompt twice on the same local day`() {
        assertFalse(prompt(lastShown = today))
        assertFalse(StartupUpdatePromptPolicy.canPromptToday(today, today))
    }

    @Test
    fun `prompts again on the next day after a skip`() {
        assertTrue(prompt(lastShown = today - 1))
    }

    @Test
    fun `prompts when the clock moved backwards`() {
        assertTrue(prompt(lastShown = today + 3))
    }

    @Test
    fun `ignores same or older builds`() {
        assertFalse(prompt(candidate = 100))
        assertFalse(prompt(candidate = 99))
    }

    @Test
    fun `ignores builds the device cannot install`() {
        assertFalse(prompt(minSdk = 36, deviceSdk = 35))
    }
}
