package org.usbrain.project

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.usbrain.project.data.AnalyticsPointDto
import org.usbrain.project.data.AnalyticsSeriesDto

class AnalyticsTest {
    @Test
    fun detectsLargeExpectedReduction() {
        val series = AnalyticsSeriesDto(
            id = "demo",
            behavior = "Ansiedad",
            dimension = "INTENSIDAD",
            unit = "escala 0-10",
            expectedDirection = "DECREASE",
            baseline = listOf(9.0, 8.0, 9.0, 8.0, 9.0).mapIndexed { i, v -> AnalyticsPointDto(i + 1, v) },
            intervention = listOf(5.0, 4.0, 3.0, 4.0, 3.0).mapIndexed { i, v -> AnalyticsPointDto(i + 1, v) },
        )

        val result = assertNotNull(analyzeHypothesis(series))
        assertTrue(result.pValue < 0.05)
        assertTrue(result.difference < 0)
        assertTrue(result.supportsExpectedDirection)
    }
}

