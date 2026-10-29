package org.usbrain.project

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import org.usbrain.project.data.AnalyticsSeriesDto

/** Resultado exploratorio; no sustituye el análisis clínico ni un diseño experimental. */
data class HypothesisResult(
    val baselineMean: Double,
    val interventionMean: Double,
    val difference: Double,
    val percentChange: Double?,
    val statistic: Double,
    val pValue: Double,
    val effectSize: Double,
    val supportsExpectedDirection: Boolean,
)

fun analyzeHypothesis(series: AnalyticsSeriesDto): HypothesisResult? {
    val baseline = series.baseline.map { it.value }
    val intervention = series.intervention.map { it.value }
    if (baseline.size < 3 || intervention.size < 3) return null

    val meanA = baseline.average()
    val meanB = intervention.average()
    val varA = sampleVariance(baseline)
    val varB = sampleVariance(intervention)
    val standardError = sqrt(varA / baseline.size + varB / intervention.size)
    val statistic = if (standardError > 0.0) (meanB - meanA) / standardError else 0.0
    // Contraste unilateral de Welch en la dirección clínica esperada: una conducta
    // desadaptativa debe disminuir y una adaptativa debe aumentar.
    val pValue = if (series.expectedDirection == "INCREASE") {
        1.0 - normalCdf(statistic)
    } else {
        normalCdf(statistic)
    }.coerceIn(0.0, 1.0)
    val pooledDenominator = baseline.size + intervention.size - 2
    val pooledSd = if (pooledDenominator > 0) {
        sqrt(((baseline.size - 1) * varA + (intervention.size - 1) * varB) / pooledDenominator)
    } else 0.0
    val effect = if (pooledSd > 0.0) (meanB - meanA) / pooledSd else 0.0
    val expected = if (series.expectedDirection == "INCREASE") meanB > meanA else meanB < meanA

    return HypothesisResult(
        baselineMean = meanA,
        interventionMean = meanB,
        difference = meanB - meanA,
        percentChange = meanA.takeIf { it != 0.0 }?.let { (meanB - meanA) / abs(it) * 100.0 },
        statistic = statistic,
        pValue = pValue,
        effectSize = effect,
        supportsExpectedDirection = expected,
    )
}

private fun sampleVariance(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.average()
    return values.sumOf { (it - mean).pow(2) } / (values.size - 1)
}

/** CDF normal estándar usando la aproximación de Abramowitz y Stegun 7.1.26. */
private fun normalCdf(value: Double): Double {
    val x = abs(value)
    val t = 1.0 / (1.0 + 0.2316419 * x)
    val density = 0.3989422804014327 * exp(-x * x / 2.0)
    val tail = density * t * (
        0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429)))
    )
    return if (value >= 0.0) 1.0 - tail else tail
}

