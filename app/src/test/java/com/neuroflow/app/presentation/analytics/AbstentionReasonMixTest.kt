package com.neuroflow.app.presentation.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbstentionReasonMixTest {

    @Test
    fun `abstention reason mix filters zero and sorts descending`() {
        val mix = abstentionReasonMix(
            freezeCount = 2,
            lowSamplesCount = 5,
            lowCoverageCount = 0,
            wakeVarianceCount = 1,
            divergenceCount = 3,
            otherCount = 0
        )

        assertEquals(
            listOf(
                "Low samples" to 5,
                "High divergence" to 3,
                "Freeze safety" to 2,
                "Wake variance" to 1
            ),
            mix
        )
    }

    @Test
    fun `abstention reason mix clamps negative values to zero`() {
        val mix = abstentionReasonMix(
            freezeCount = -2,
            lowSamplesCount = -1,
            lowCoverageCount = 0,
            wakeVarianceCount = 0,
            divergenceCount = 0,
            otherCount = 4
        )

        assertEquals(1, mix.size)
        assertTrue(mix.contains("Other" to 4))
    }
}
