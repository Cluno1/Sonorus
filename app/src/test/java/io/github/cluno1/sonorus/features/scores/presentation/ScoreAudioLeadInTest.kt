package io.github.cluno1.sonorus.features.scores.presentation

import alphaTab.platform.android.AndroidAudioWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreAudioLeadInTest {
    @Test
    fun `起播静音为 0_8 秒且按立体声计算采样数`() {
        assertEquals(800, AndroidAudioWorker.leadInMillis())
        assertEquals(70_560, AndroidAudioWorker.leadInFloats(44_100))
    }
}
