package org.usbrain.project

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import org.usbrain.project.data.AnalyticsSeriesDto
import org.usbrain.project.data.AnalyticsStudyDto

/** Qué prueba produjo el resultado: cambia cómo se lee n y el tamaño del efecto. */
enum class HypothesisMethod(val label: String) {
    /** Un caso: días de línea base contra días de intervención del mismo paciente. */
    WELCH("t de Welch (un caso, A1 vs B1)"),
    /** Un estudio: cada participante aporta su cambio entre fases. */
    PAIRED("t pareada (grupo, n = participantes)"),
}

/** Resultado exploratorio; no sustituye el análisis clínico ni un diseño experimental. */
data class HypothesisResult(
    val method: HypothesisMethod,
    /** Días por fase (Welch) o participantes (pareada). */
    val baselineN: Int,
    val interventionN: Int,
    val baselineMean: Double,
    val interventionMean: Double,
    val difference: Double,
    val percentChange: Double?,
    val statistic: Double,
    val degreesOfFreedom: Double,
    /** Unilateral, en la dirección clínica esperada. */
    val pValue: Double,
    /** d de Cohen (Welch, con desviación combinada) o dz (pareada, con la desviación de las diferencias). */
    val effectSize: Double,
    val supportsExpectedDirection: Boolean,
    /** Denominador del estadístico: cuánto variaría la diferencia de medias solo por azar. */
    val standardError: Double,
    /** Intervalo de confianza del 95 % para la diferencia (B1 − A1). */
    val ciLow: Double,
    val ciHigh: Double,
    /** Valor de t a partir del cual se rechaza H₀ con α = 0,05 (unilateral, en la dirección esperada). */
    val criticalT: Double,
)

/**
 * Caso único: compara los totales diarios de A1 (línea base) con los de B1 (intervención).
 * Contraste unilateral: una conducta desadaptativa debe disminuir y una adaptativa aumentar.
 */
fun analyzeHypothesis(series: AnalyticsSeriesDto): HypothesisResult? =
    welchTest(series.baseline.map { it.value }, series.intervention.map { it.value }, series.expectedDirection)

fun welchTest(baseline: List<Double>, intervention: List<Double>, expectedDirection: String): HypothesisResult? {
    if (baseline.size < 3 || intervention.size < 3) return null
    val nA = baseline.size
    val nB = intervention.size
    val meanA = baseline.average()
    val meanB = intervention.average()
    val varA = sampleVariance(baseline)
    val varB = sampleVariance(intervention)
    val seA = varA / nA
    val seB = varB / nB
    val standardError = sqrt(seA + seB)
    val statistic = if (standardError > 0.0) (meanB - meanA) / standardError else 0.0
    // Grados de libertad de Welch–Satterthwaite: con varianzas distintas no son nA + nB − 2.
    val df = if (seA + seB > 0.0) {
        (seA + seB).pow(2) / (seA.pow(2) / (nA - 1) + seB.pow(2) / (nB - 1))
    } else (nA + nB - 2).toDouble()
    val pooledSd = sqrt(((nA - 1) * varA + (nB - 1) * varB) / (nA + nB - 2))
    val margin = studentTQuantile(0.975, df) * standardError
    return HypothesisResult(
        method = HypothesisMethod.WELCH,
        baselineN = nA,
        interventionN = nB,
        baselineMean = meanA,
        interventionMean = meanB,
        difference = meanB - meanA,
        percentChange = percentChange(meanA, meanB),
        statistic = statistic,
        degreesOfFreedom = df,
        pValue = oneSidedP(statistic, df, expectedDirection, standardError > 0.0, meanB - meanA),
        effectSize = if (pooledSd > 0.0) (meanB - meanA) / pooledSd else 0.0,
        supportsExpectedDirection = matchesDirection(meanB - meanA, expectedDirection),
        standardError = standardError,
        ciLow = meanB - meanA - margin,
        ciHigh = meanB - meanA + margin,
        criticalT = criticalT(df, expectedDirection),
    )
}

/**
 * Estudio de grupo: cada participante aporta su media diaria en A1 y en B1. La prueba se hace
 * sobre las diferencias (B1 − A1), así cada persona pesa lo mismo y n = participantes.
 */
fun analyzeStudy(study: AnalyticsStudyDto): HypothesisResult? =
    pairedTest(study.participants.map { it.baselineMean to it.interventionMean }, study.expectedDirection)

fun pairedTest(pairs: List<Pair<Double, Double>>, expectedDirection: String): HypothesisResult? {
    if (pairs.size < 3) return null
    val n = pairs.size
    val differences = pairs.map { (a, b) -> b - a }
    val meanDiff = differences.average()
    val sdDiff = sqrt(sampleVariance(differences))
    val standardError = sdDiff / sqrt(n.toDouble())
    val statistic = if (standardError > 0.0) meanDiff / standardError else 0.0
    val df = (n - 1).toDouble()
    val meanA = pairs.map { it.first }.average()
    val meanB = pairs.map { it.second }.average()
    val margin = studentTQuantile(0.975, df) * standardError
    return HypothesisResult(
        method = HypothesisMethod.PAIRED,
        baselineN = n,
        interventionN = n,
        baselineMean = meanA,
        interventionMean = meanB,
        difference = meanDiff,
        percentChange = percentChange(meanA, meanB),
        statistic = statistic,
        degreesOfFreedom = df,
        pValue = oneSidedP(statistic, df, expectedDirection, standardError > 0.0, meanDiff),
        effectSize = if (sdDiff > 0.0) meanDiff / sdDiff else 0.0,
        supportsExpectedDirection = matchesDirection(meanDiff, expectedDirection),
        standardError = standardError,
        ciLow = meanDiff - margin,
        ciHigh = meanDiff + margin,
        criticalT = criticalT(df, expectedDirection),
    )
}

/** Media e intervalo del 95 % de una lista de valores (para dibujar cada fase). */
fun meanWithCi(values: List<Double>): Triple<Double, Double, Double>? {
    if (values.size < 2) return null
    val mean = values.average()
    val margin = studentTQuantile(0.975, (values.size - 1).toDouble()) * sqrt(sampleVariance(values) / values.size)
    return Triple(mean, mean - margin, mean + margin)
}

private fun criticalT(df: Double, expectedDirection: String): Double =
    if (expectedDirection == "INCREASE") studentTQuantile(0.95, df) else studentTQuantile(0.05, df)

private fun percentChange(meanA: Double, meanB: Double): Double? =
    meanA.takeIf { it != 0.0 }?.let { (meanB - meanA) / abs(it) * 100.0 }

private fun matchesDirection(difference: Double, expectedDirection: String): Boolean =
    if (expectedDirection == "INCREASE") difference > 0 else difference < 0

/** P(T ≤ t) si se espera disminución, P(T ≥ t) si se espera aumento. */
private fun oneSidedP(statistic: Double, df: Double, expectedDirection: String, hasVariance: Boolean, difference: Double): Double {
    // Sin variación en los datos la t no está definida: la diferencia es exacta o no hay ninguna.
    if (!hasVariance) return if (matchesDirection(difference, expectedDirection)) 0.0 else 1.0
    val lower = studentTCdf(statistic, df)
    return (if (expectedDirection == "INCREASE") 1.0 - lower else lower).coerceIn(0.0, 1.0)
}

private fun sampleVariance(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val mean = values.average()
    return values.sumOf { (it - mean).pow(2) } / (values.size - 1)
}

/**
 * Función de distribución de la t de Student con df grados de libertad.
 * Usa la beta incompleta regularizada: P(T ≤ t) = 1 − ½·I_x(df/2, ½) con x = df / (df + t²) para t ≥ 0.
 */
internal fun studentTCdf(t: Double, df: Double): Double {
    val x = df / (df + t * t)
    val tail = 0.5 * regularizedIncompleteBeta(x, df / 2.0, 0.5)
    return if (t >= 0.0) 1.0 - tail else tail
}

/** Densidad de la t de Student: la "campana" de valores de t que daría el azar si H₀ fuera cierta. */
fun studentTPdf(t: Double, df: Double): Double =
    exp(lnGamma((df + 1) / 2) - lnGamma(df / 2) - 0.5 * ln(df * kotlin.math.PI) - (df + 1) / 2 * ln(1 + t * t / df))

/** Valor de t con probabilidad acumulada p (inversa de la CDF, por bisección). */
fun studentTQuantile(p: Double, df: Double): Double {
    var low = -60.0
    var high = 60.0
    repeat(100) {
        val mid = (low + high) / 2
        if (studentTCdf(mid, df) < p) low = mid else high = mid
    }
    return (low + high) / 2
}

/** I_x(a, b) por fracción continua (Numerical Recipes, «betai»). */
private fun regularizedIncompleteBeta(x: Double, a: Double, b: Double): Double {
    if (x <= 0.0) return 0.0
    if (x >= 1.0) return 1.0
    val front = exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1.0 - x))
    return if (x < (a + 1.0) / (a + b + 2.0)) {
        front * betaContinuedFraction(x, a, b) / a
    } else {
        1.0 - front * betaContinuedFraction(1.0 - x, b, a) / b
    }
}

private fun betaContinuedFraction(x: Double, a: Double, b: Double): Double {
    val tiny = 1e-300
    var c = 1.0
    var d = 1.0 - (a + b) * x / (a + 1.0)
    if (abs(d) < tiny) d = tiny
    d = 1.0 / d
    var h = d
    for (m in 1..300) {
        val m2 = 2 * m
        var aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2))
        d = 1.0 + aa * d
        if (abs(d) < tiny) d = tiny
        c = 1.0 + aa / c
        if (abs(c) < tiny) c = tiny
        d = 1.0 / d
        h *= d * c
        aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1))
        d = 1.0 + aa * d
        if (abs(d) < tiny) d = tiny
        c = 1.0 + aa / c
        if (abs(c) < tiny) c = tiny
        d = 1.0 / d
        val delta = d * c
        h *= delta
        if (abs(delta - 1.0) < 1e-12) break
    }
    return h
}

/** ln Γ(z) por la aproximación de Lanczos (g = 7, 9 coeficientes). */
private fun lnGamma(z: Double): Double {
    val g = 7.0
    val coefficients = doubleArrayOf(
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012,
        9.9843695780195716e-6, 1.5056327351493116e-7,
    )
    val x = z - 1.0
    var sum = coefficients[0]
    for (i in 1 until coefficients.size) sum += coefficients[i] / (x + i)
    val t = x + g + 0.5
    return 0.5 * ln(2.0 * kotlin.math.PI) + (x + 0.5) * ln(t) - t + ln(sum)
}
