package org.usbrain.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.usbrain.project.data.AnalyticsParticipantDto
import org.usbrain.project.data.AnalyticsPointDto
import org.usbrain.project.data.AnalyticsSeriesDto
import org.usbrain.project.data.AnalyticsStudyDto

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
        assertEquals(HypothesisMethod.WELCH, result.method)
        assertTrue(result.pValue < 0.05)
        assertTrue(result.difference < 0)
        assertTrue(result.supportsExpectedDirection)
    }

    @Test
    fun studentTCdfMatchesTables() {
        assertEquals(0.5, studentTCdf(0.0, 10.0), 1e-9)
        // t(4) = 2,776 deja 0,975 a la izquierda (valor crítico de tablas para α = 0,025).
        assertEquals(0.975, studentTCdf(2.776, 4.0), 0.0005)
        assertEquals(0.025, studentTCdf(-2.776, 4.0), 0.0005)
    }

    @Test
    fun welchUsesStudentTWithSatterthwaiteDegreesOfFreedom() {
        // Con 3 días por fase la curva normal daría p = 0,110: la t correcta es más exigente.
        val result = assertNotNull(welchTest(listOf(6.0, 5.0, 7.0), listOf(4.0, 6.0, 5.0), "DECREASE"))
        assertEquals(4.0, result.degreesOfFreedom, 0.01)
        assertEquals(0.1439, result.pValue, 0.002)
        assertFalse(result.pValue < 0.05)
    }

    @Test
    fun pairedTestUsesOneObservationPerParticipant() {
        val study = AnalyticsStudyDto(
            id = "s", code = "DEMO", behavior = "Ansiedad", dimension = "FRECUENCIA", unit = "episodios por día",
            participants = listOf(7.0 to 3.0, 6.0 to 4.0, 8.0 to 5.0, 5.0 to 5.0)
                .map { (a, b) -> AnalyticsParticipantDto(a, b) },
        )
        val result = assertNotNull(analyzeStudy(study))
        assertEquals(HypothesisMethod.PAIRED, result.method)
        assertEquals(4, result.baselineN)
        assertEquals(3.0, result.degreesOfFreedom, 1e-9)
        assertEquals(-2.25, result.difference, 1e-9)
        assertEquals(0.039, result.pValue, 0.002)
    }

    @Test
    fun needsAtLeastThreeValuesPerSide() {
        assertNull(welchTest(listOf(1.0, 2.0), listOf(1.0, 2.0, 3.0), "DECREASE"))
        assertNull(pairedTest(listOf(1.0 to 0.0, 2.0 to 1.0), "DECREASE"))
    }

    @Test
    fun increaseIsTestedInTheOtherDirection() {
        val result = assertNotNull(welchTest(listOf(1.0, 2.0, 1.0, 2.0), listOf(4.0, 5.0, 4.0, 6.0), "INCREASE"))
        assertTrue(result.pValue < 0.05)
        assertTrue(result.supportsExpectedDirection)
    }
}
