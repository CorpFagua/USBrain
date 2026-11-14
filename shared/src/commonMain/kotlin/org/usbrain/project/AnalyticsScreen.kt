@file:OptIn(
    androidx.compose.ui.text.ExperimentalTextApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package org.usbrain.project

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.usbrain.project.data.AnalyticsGroupDto
import org.usbrain.project.data.AnalyticsPointDto
import org.usbrain.project.data.AnalyticsSeriesDto
import org.usbrain.project.data.AnalyticsStudyDto

private enum class AnalyticsView(val label: String) {
    SESSIONS("Por día"), WEEKLY("Semanal"), MONTHLY("Mensual")
}

/**
 * Panorama general: cómo responde al tratamiento una conducta que se repite en varios
 * participantes (estudio de grupo). El caso único se analiza en la ficha de cada paciente.
 */
@Composable
internal fun AdminAnalyticsScreen(state: PrototypeState) {
    LaunchedEffect(Unit) { state.loadAnalytics() }
    val dataset = state.analyticsData
    val studies = dataset?.studies.orEmpty()
    val options = studies.map { "study:${it.id}" to "${it.behavior} · ${dimensionLabel(it.dimension)} · n=${it.participants.size}" }
    var selectedKey by remember(options) { mutableStateOf(options.firstOrNull()?.first) }

    ScreenScaffold {
        Text("Análisis estadístico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Panorama general por conducta: cómo cambia entre línea base (A1) e intervención (B1) en el grupo de participantes que la registra. El análisis de cada caso está en la ficha del paciente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.analyticsError != null -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No fue posible cargar el análisis", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text(state.analyticsError.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Text("Verifica Oracle, ORDS y que el backend haya sido instalado con los scripts de esta rama.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            dataset == null -> Text("Cargando exportación segura…", style = MaterialTheme.typography.bodySmall)
            else -> {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Conexión activa", fontWeight = FontWeight.SemiBold, color = LocalUsBrainSemantic.current.success)
                        Text(dataset.source, style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("Seudonimizado", LocalUsBrainSemantic.current.success)
                            Pill("${studies.size} conductas", MaterialTheme.colorScheme.primary)
                            Pill("${studies.sumOf { it.participants.size }} registros de participantes", MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "Sin nombres, correos, documentos, diagnósticos ni notas: cada participante llega solo como su media diaria en A1 y en B1.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (options.isEmpty()) {
                    Text("Se requieren al menos 3 participantes con 3 días en A1 y 3 en B1 en la misma conducta.")
                } else {
                    DropdownField(
                        label = "Conducta",
                        selectedLabel = options.firstOrNull { it.first == selectedKey }?.second,
                        placeholder = "Selecciona una conducta",
                        options = options,
                        onSelect = { selectedKey = it },
                        searchable = true,
                    )
                    studies.firstOrNull { "study:${it.id}" == selectedKey }?.let { StudyPanel(it) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StudyPanel(study: AnalyticsStudyDto) {
    val decrease = study.expectedDirection != "INCREASE"
    val pairs = remember(study) { study.participants.map { it.baselineMean to it.interventionMean } }
    val result = remember(study) { analyzeStudy(study) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(study.behavior, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${study.participants.size} participantes · ${dimensionLabel(study.dimension)} (${study.unit})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (decrease) "Conducta a reducir: se espera que B1 quede por debajo de A1."
                else "Conducta a fortalecer: se espera que B1 quede por encima de A1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    DescriptiveCard(study, pairs, decrease)
    TrajectoryCard(study)
    HypothesisCard(result, decrease, study.unit)
    GroupComparisonCard(
        "Comparación por programa académico",
        study.programGroups,
        "Diferencias descriptivas: no permiten afirmar que una carrera cause más o menos la conducta.",
    )
    GroupComparisonCard(
        "Comparación por rango de edad",
        study.ageGroups,
        "Los rangos sirven para explorar patrones; no identifican personas ni prueban causalidad.",
    )
}

/** describe() sobre una observación por participante: su media diaria en A1, en B1 y el cambio. */
@Composable
private fun DescriptiveCard(study: AnalyticsStudyDto, pairs: List<Pair<Double, Double>>, decrease: Boolean) {
    val a = remember(pairs) { describe(pairs.map { it.first }) }
    val b = remember(pairs) { describe(pairs.map { it.second }) }
    val d = remember(pairs) { describe(pairs.map { it.second - it.first }) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("1. Estadística descriptiva", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Cada participante aporta un valor por fase: su media diaria (${study.unit}). Equivale a describe() sobre las columnas A1, B1 y d = B1 - A1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (a == null || b == null || d == null) {
                Text("Sin participantes suficientes.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            DescribeTable(a, b, d)
            val improved = pairs.count { (x, y) -> if (decrease) y < x else y > x }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("${improved * 100 / pairs.size} %", "mejoró en la dirección esperada ($improved de ${pairs.size})", LocalUsBrainSemantic.current.success)
                StatTile(dec(signedValue(b.median - a.median)), "cambio de la mediana", MaterialTheme.colorScheme.primary)
                StatTile(
                    if (a.mean == 0.0) "—" else "${dec(signedValue((b.mean - a.mean) / abs(a.mean) * 100))} %",
                    "cambio de la media",
                    MaterialTheme.colorScheme.primary,
                )
            }
            Text("Distribución por fase", fontWeight = FontWeight.SemiBold)
            PhaseBoxPlot(a, b)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsLegend(LocalUsBrainSemantic.current.baseline, "A1 · línea base")
                AnalyticsLegend(LocalUsBrainSemantic.current.intervention, "B1 · intervención")
            }
            Text(
                "Caja: Q1 a Q3 · línea gruesa: mediana · rombo: media · bigotes: mínimo y máximo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DescribeTable(a: DescriptiveStats, b: DescriptiveStats, d: DescriptiveStats) {
    val rows: List<Pair<String, (DescriptiveStats) -> String>> = listOf(
        "n (count)" to { s -> s.count.toString() },
        "Media (mean)" to { s -> dec(s.mean) },
        "Desv. estándar (std)" to { s -> dec(s.std) },
        "Mínimo (min)" to { s -> dec(s.min) },
        "Q1 (25 %)" to { s -> dec(s.q1) },
        "Mediana (50 %)" to { s -> dec(s.median) },
        "Q3 (75 %)" to { s -> dec(s.q3) },
        "Máximo (max)" to { s -> dec(s.max) },
        "Rango intercuartílico" to { s -> dec(s.iqr) },
    )
    val stripe = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("", Modifier.weight(1.5f))
            listOf("A1", "B1", "d = B1 - A1").forEach {
                Text(it, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
            }
        }
        rows.forEachIndexed { index, (label, value) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .background(if (index % 2 == 0) stripe else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(label, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(a, b, d).forEach { stats ->
                    Text(value(stats), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/** Boxplot de A1 y B1 a partir de su describe(). */
@Composable
private fun PhaseBoxPlot(a: DescriptiveStats, b: DescriptiveStats) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val grid = LocalUsBrainSemantic.current.grid
    val baselineColor = LocalUsBrainSemantic.current.baseline
    val interventionColor = LocalUsBrainSemantic.current.intervention
    val low = min(0.0, min(a.min, b.min))
    val high = max(a.max, b.max).let { if (it <= low) low + 1.0 else low + (it - low) * 1.08 }
    Canvas(Modifier.fillMaxWidth().height(250.dp)) {
        val left = 40f
        val top = 10f
        val bottom = 30f
        val width = size.width - left
        val plotHeight = size.height - top - bottom
        val base = top + plotHeight
        fun y(value: Double) = base - ((value - low) / (high - low)).toFloat() * plotHeight
        for (step in 0..4) {
            val value = low + (high - low) * step / 4
            val gy = y(value)
            drawLine(grid, Offset(left, gy), Offset(size.width, gy), 1f)
            val label = measurer.measure(analyticsFmt(value), TextStyle(fontSize = 9.sp, color = axis))
            drawText(label, topLeft = Offset(left - label.size.width - 6f, gy - label.size.height / 2f))
        }
        val boxWidth = width * 0.22f
        val centerA = left + width * 0.3f
        val centerB = left + width * 0.7f

        fun drawBox(center: Float, stats: DescriptiveStats, color: Color, title: String) {
            val half = boxWidth / 2f
            drawLine(color, Offset(center, y(stats.max)), Offset(center, y(stats.q3)), 2f)
            drawLine(color, Offset(center, y(stats.q1)), Offset(center, y(stats.min)), 2f)
            drawLine(color, Offset(center - half / 2, y(stats.max)), Offset(center + half / 2, y(stats.max)), 2f)
            drawLine(color, Offset(center - half / 2, y(stats.min)), Offset(center + half / 2, y(stats.min)), 2f)
            val boxTop = y(stats.q3)
            val boxHeight = (y(stats.q1) - boxTop).coerceAtLeast(2f)
            drawRect(color.copy(alpha = 0.16f), Offset(center - half, boxTop), Size(boxWidth, boxHeight))
            drawRect(color, Offset(center - half, boxTop), Size(boxWidth, boxHeight), style = Stroke(2f))
            drawLine(color, Offset(center - half, y(stats.median)), Offset(center + half, y(stats.median)), 4f, cap = StrokeCap.Round)
            val my = y(stats.mean)
            val diamond = Path().apply {
                moveTo(center, my - 7f); lineTo(center + 7f, my); lineTo(center, my + 7f); lineTo(center - 7f, my); close()
            }
            drawPath(diamond, surface)
            drawPath(diamond, color, style = Stroke(2f))
            val label = measurer.measure(title, TextStyle(fontSize = 10.sp, color = axis, fontWeight = FontWeight.SemiBold))
            drawText(label, topLeft = Offset(center - label.size.width / 2f, base + 8f))
        }

        drawBox(centerA, a, baselineColor, "A1")
        drawBox(centerB, b, interventionColor, "B1")
    }
}

/** Promedio diario del grupo a lo largo de cada fase: describe la forma del cambio en el tiempo. */
@Composable
private fun TrajectoryCard(study: AnalyticsStudyDto) {
    val asSeries = remember(study) {
        AnalyticsSeriesDto(study.id, study.behavior, study.dimension, study.unit, study.expectedDirection, study.baseline, study.intervention)
    }
    var view by remember { mutableStateOf(AnalyticsView.SESSIONS) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Evolución del promedio del grupo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AnalyticsView.entries.forEach { option ->
                    FilterChip(selected = view == option, onClick = { view = option }, label = { Text(option.label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Día relativo de cada participante", style = MaterialTheme.typography.labelMedium)
                Text("Promedio del grupo · ${study.unit}", style = MaterialTheme.typography.labelMedium)
            }
            AnalyticsPhaseChart(asSeries, view)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsLegend(LocalUsBrainSemantic.current.baseline, "A1 · línea base")
                AnalyticsLegend(LocalUsBrainSemantic.current.intervention, "B1 · intervención")
            }
            Text(
                "Descriptivo: cada punto promedia a quienes registraron ese día. La prueba usa la media de cada participante.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** t pareada sobre los cambios B1 − A1, con los números que entran en cada fórmula. */
@Composable
private fun HypothesisCard(result: HypothesisResult?, decrease: Boolean, unit: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2. Prueba de hipótesis · t pareada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (decrease) "H0: el cambio medio d = B1 - A1 es >= 0.   H1: el cambio medio es < 0 (la conducta disminuye)."
                else "H0: el cambio medio d = B1 - A1 es <= 0.   H1: el cambio medio es > 0 (la conducta aumenta).",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Unilateral, alfa = 0,05. Cada participante es una observación, así que n = participantes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result == null) {
                Text("No hay datos suficientes: se necesitan al menos 3 participantes.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val n = result.baselineN
            val sd = result.standardError * sqrt(n.toDouble())
            val tail = if (decrease) "<=" else ">="
            Text("Números que intervienen", fontWeight = FontWeight.SemiBold)
            Column {
                FormulaRow("Participantes", "n = $n")
                FormulaRow("Cambio medio ($unit)", "media(d) = ${dec(result.difference)}")
                FormulaRow("Desviación de los cambios", "s(d) = ${dec(sd)}")
                FormulaRow("Error estándar", "EE = s(d) / raíz(n) = ${dec(sd)} / raíz($n) = ${dec(result.standardError)}")
                FormulaRow("Estadístico", "t = media(d) / EE = ${dec(result.difference)} / ${dec(result.standardError)} = ${dec(result.statistic)}")
                FormulaRow("Grados de libertad", "gl = n - 1 = ${analyticsFmt(result.degreesOfFreedom)}")
                FormulaRow("Valor crítico (alfa = 0,05)", "t crítico = ${dec(result.criticalT)}")
                FormulaRow("Valor p", "P(T $tail ${dec(result.statistic)}) = ${analyticsP(result.pValue)}")
                FormulaRow("IC 95 % del cambio", "media(d) ± t(0,975) · EE = [${dec(result.ciLow)} ; ${dec(result.ciHigh)}]")
                FormulaRow("Tamaño del efecto", "dz = media(d) / s(d) = ${dec(result.effectSize)} (${effectLabel(result.effectSize)})")
            }
            TDistributionChart(result, decrease)
            val significant = result.pValue < 0.05
            Text(
                if (significant) "Resultado: t = ${dec(result.statistic)} cae en la región de rechazo (p = ${analyticsP(result.pValue)} < 0,05): se rechaza H0 en la dirección esperada."
                else "Resultado: t = ${dec(result.statistic)} no alcanza el valor crítico (p = ${analyticsP(result.pValue)} >= 0,05): no hay evidencia suficiente para rechazar H0.",
                fontWeight = FontWeight.SemiBold,
                color = if (significant) LocalUsBrainSemantic.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!result.supportsExpectedDirection) {
                Text("La dirección observada no coincide con la esperada para esta conducta.", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Exploratorio: sin grupo control no permite atribuir el cambio solo al tratamiento.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormulaRow(label: String, formula: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formula, Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

/**
 * Distribución t con n − 1 gl en un plano cartesiano (t en X, densidad en Y). Se comparan dos
 * áreas bajo la curva: α (región de rechazo, desde el valor crítico) y p (desde la t observada).
 */
@Composable
private fun TDistributionChart(result: HypothesisResult, decrease: Boolean) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = LocalUsBrainSemantic.current.grid
    val alphaColor = MaterialTheme.colorScheme.primary
    val pColor = LocalUsBrainSemantic.current.intervention
    val surface = MaterialTheme.colorScheme.surface
    val df = result.degreesOfFreedom
    val critical = result.criticalT
    val span = ceil(max(4.0, abs(result.statistic) + 1.0)).coerceAtMost(12.0)
    val shown = result.statistic.coerceIn(-span, span)
    val tickStep = if (span > 6) 2 else 1
    Canvas(Modifier.fillMaxWidth().height(270.dp)) {
        val left = 40f
        val right = 10f
        val top = 40f
        val bottom = 56f
        val width = size.width - left - right
        val plotHeight = size.height - top - bottom
        val base = top + plotHeight
        val yTicks = ceil(studentTPdf(0.0, df) * 10).toInt()
        val yMax = yTicks / 10.0
        fun x(t: Double) = left + ((t + span) / (2 * span)).toFloat() * width
        fun yValue(density: Double) = base - (density / yMax).toFloat() * plotHeight
        fun y(t: Double) = yValue(studentTPdf(t, df))
        val small = TextStyle(fontSize = 9.sp, color = axis)

        // Plano: rejilla, eje Y (densidad) y eje X (t) con sus marcas.
        for (tick in 0..yTicks) {
            val gy = yValue(tick / 10.0)
            drawLine(grid, Offset(left, gy), Offset(size.width - right, gy), 1f)
            val label = measurer.measure(if (tick == 0) "0" else "0,$tick", small)
            drawText(label, topLeft = Offset(left - label.size.width - 6f, gy - label.size.height / 2f))
        }
        for (tick in (-span.toInt())..span.toInt() step tickStep) {
            val gx = x(tick.toDouble())
            drawLine(grid.copy(alpha = 0.55f), Offset(gx, top), Offset(gx, base), 1f)
            drawLine(axis, Offset(gx, base), Offset(gx, base + 4f), 1f)
            // La marca de la t observada ocupa este lugar del eje.
            if (abs(gx - x(shown)) < 16f) continue
            val label = measurer.measure(if (tick < 0) "-${-tick}" else tick.toString(), small)
            drawText(label, topLeft = Offset(gx - label.size.width / 2f, base + 6f))
        }
        drawLine(axis, Offset(left, top), Offset(left, base), 1.5f)
        drawLine(axis, Offset(left, base), Offset(size.width - right, base), 1.5f)
        val xTitle = measurer.measure("t", TextStyle(fontSize = 10.sp, color = axis, fontWeight = FontWeight.SemiBold))
        drawText(xTitle, topLeft = Offset(size.width - right - xTitle.size.width, base - xTitle.size.height - 2f))
        val yTitle = measurer.measure("densidad", small)
        drawText(yTitle, topLeft = Offset(0f, top - yTitle.size.height - 4f))

        val steps = 160
        fun area(from: Double, to: Double): Path = Path().apply {
            moveTo(x(from), base)
            for (i in 0..steps) {
                val t = from + (to - from) * i / steps
                lineTo(x(t), y(t))
            }
            lineTo(x(to), base)
            close()
        }
        fun tailFrom(start: Double) = if (decrease) -span to start else start to span

        // Área α (fija, 5 %) y área p (desde la t observada): si p queda dentro de α, se rechaza H0.
        val (alphaFrom, alphaTo) = tailFrom(critical)
        drawPath(area(alphaFrom, alphaTo), alphaColor.copy(alpha = 0.18f))
        val (pFrom, pTo) = tailFrom(shown)
        drawPath(area(pFrom, pTo), pColor.copy(alpha = 0.5f))

        val curve = Path().apply {
            moveTo(x(-span), y(-span))
            for (i in 0..steps * 2) lineTo(x(-span + 2 * span * i / (steps * 2)), y(-span + 2 * span * i / (steps * 2)))
        }
        drawPath(curve, axis, style = Stroke(2f, join = StrokeJoin.Round))
        drawLine(alphaColor, Offset(x(critical), top - 4f), Offset(x(critical), base), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
        drawLine(pColor, Offset(x(shown), top - 4f), Offset(x(shown), base), 2.5f)

        // Etiquetas sobre cada línea; si quedan cerca, la de α baja una fila.
        val bold = TextStyle(fontSize = 10.sp, color = axis, fontWeight = FontWeight.SemiBold)
        val pLabel = measurer.measure("p = ${analyticsP(result.pValue)}", bold)
        val alphaLabel = measurer.measure("alfa = 0,05", small)
        fun clampX(center: Float, w: Int) = (center - w / 2f).coerceIn(0f, size.width - w)
        val pX = clampX(x(shown), pLabel.size.width)
        val alphaX = clampX(x(critical), alphaLabel.size.width)
        val overlap = alphaX < pX + pLabel.size.width + 6f && pX < alphaX + alphaLabel.size.width + 6f
        drawText(pLabel, topLeft = Offset(pX, 0f))
        drawText(alphaLabel, topLeft = Offset(alphaX, if (overlap) pLabel.size.height + 2f else 0f))

        // Tu resultado como coordenada: (t observada, densidad) sobre la curva y su valor en el eje X.
        val point = Offset(x(shown), y(shown))
        drawCircle(surface, 8f, point)
        drawCircle(pColor, 6f, point)
        fun axisMarker(t: Double, color: Color, text: String, style: TextStyle, row: Int) {
            val mx = x(t)
            val marker = Path().apply { moveTo(mx, base + 2f); lineTo(mx + 6f, base + 11f); lineTo(mx - 6f, base + 11f); close() }
            drawPath(marker, color)
            val label = measurer.measure(text, style)
            drawText(label, topLeft = Offset(clampX(mx, label.size.width), base + 13f + row * (label.size.height + 1f)))
        }
        val markersOverlap = abs(x(shown) - x(critical)) < 90f
        axisMarker(critical, alphaColor, "crítico ${dec(critical)}", small, if (markersOverlap) 1 else 0)
        axisMarker(shown, pColor, "t = ${dec(result.statistic)}", bold, 0)
    }
    val significant = result.pValue < 0.05
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnalyticsLegend(alphaColor.copy(alpha = 0.35f), "Área alfa = 0,05 (región de rechazo)")
        AnalyticsLegend(pColor.copy(alpha = 0.6f), "Área p = ${analyticsP(result.pValue)} (desde la t observada)")
        AnalyticsLegend(pColor, "Tu resultado: t = ${dec(result.statistic)}")
    }
    if (shown != result.statistic) {
        Text(
            "La t observada (${dec(result.statistic)}) está fuera del rango dibujado; se marca en el borde.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        if (significant) "p = ${analyticsP(result.pValue)} < alfa = 0,05: el área p cabe dentro del área alfa, la t observada pasa el valor crítico."
        else "p = ${analyticsP(result.pValue)} >= alfa = 0,05: el área p es mayor que el área alfa, la t observada no llega al valor crítico.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GroupComparisonCard(title: String, groups: List<AnalyticsGroupDto>, note: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (groups.isEmpty()) {
                Text("No hay grupos con al menos 3 participantes; los grupos pequeños se ocultan para proteger la identidad.", style = MaterialTheme.typography.bodySmall)
            } else groups.sortedWith(compareBy({ it.sort }, { it.group })).forEach { CohortComparisonRow(it) }
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsLegend(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalyticsPhaseChart(series: AnalyticsSeriesDto, view: AnalyticsView) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = LocalUsBrainSemantic.current.grid
    val baselineColor = LocalUsBrainSemantic.current.baseline
    val interventionColor = LocalUsBrainSemantic.current.intervention
    val baseline = chartPoints(series.baseline, view)
    val intervention = chartPoints(series.intervention, view)
    val all = (baseline + intervention).sortedBy { it.day }
    val values = all.map { it.value }
    val minValue = min(0.0, values.minOrNull() ?: 0.0)
    val maxValue = max(1.0, values.maxOrNull() ?: 1.0)
    val firstDay = all.firstOrNull()?.day ?: 1
    val lastDay = all.lastOrNull()?.day ?: firstDay
    val dayRange = (lastDay - firstDay).coerceAtLeast(0)
    // Referencias intermedias para que una serie larga se pueda leer sin llenar el eje de texto.
    val xTicks = if (dayRange <= 7) all.map { it.day }.distinct()
    else (firstDay..lastDay step 7).toList().let { ticks -> (ticks + lastDay).distinct() }

    Canvas(Modifier.fillMaxWidth().height(245.dp)) {
        val left = 46f
        val right = 14f
        val top = 18f
        val bottom = 46f
        val width = size.width - left - right
        val chartHeight = size.height - top - bottom
        fun y(value: Double) = top + chartHeight - ((value - minValue) / (maxValue - minValue)).toFloat() * chartHeight
        fun xDay(day: Int): Float = left + if (lastDay == firstDay) width / 2f else (day - firstDay).toFloat() / (lastDay - firstDay) * width
        fun x(point: AnalyticsPointDto): Float = xDay(point.day)

        for (step in 0..4) {
            val gy = top + chartHeight * step / 4f
            drawLine(grid, Offset(left, gy), Offset(size.width - right, gy), 1f)
            val value = maxValue - (maxValue - minValue) * step / 4.0
            val label = measurer.measure(analyticsFmt(value), TextStyle(fontSize = 9.sp, color = axis))
            drawText(label, topLeft = Offset(left - label.size.width - 7f, gy - label.size.height / 2f))
        }

        // Las guías verticales convierten el tramo de 60 días en intervalos semanales legibles.
        xTicks.forEach { day ->
            val gx = xDay(day)
            drawLine(grid.copy(alpha = 0.55f), Offset(gx, top), Offset(gx, top + chartHeight), 1f)
            val label = measurer.measure("Día $day", TextStyle(fontSize = 9.sp, color = axis))
            drawText(label, topLeft = Offset(gx - label.size.width / 2f, size.height - bottom + 10f))
        }

        fun drawPhase(points: List<AnalyticsPointDto>, color: Color) {
            if (points.isEmpty()) return
            val path = Path()
            points.forEachIndexed { index, point ->
                val px = x(point)
                val py = y(point.value)
                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, color, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach { point -> drawCircle(color, 4f, Offset(x(point), y(point.value))) }
            if (points.size <= 8) points.forEach { point ->
                val valueLabel = measurer.measure(analyticsFmt(point.value), TextStyle(fontSize = 8.sp, color = color, fontWeight = FontWeight.SemiBold))
                drawText(valueLabel, topLeft = Offset(x(point) - valueLabel.size.width / 2f, y(point.value) - 18f))
            }
        }

        drawPhase(baseline, baselineColor)
        drawPhase(intervention, interventionColor)
        intervention.firstOrNull()?.let { start ->
            val dividerX = x(start) - 4f
            drawLine(axis.copy(alpha = 0.85f), Offset(dividerX, top), Offset(dividerX, top + chartHeight), 1.5f)
        }
    }
}

@Composable
private fun CohortComparisonRow(group: AnalyticsGroupDto) {
    val change = if (group.baselineMean == 0.0) null else (group.interventionMean - group.baselineMean) / group.baselineMean * 100
    Text(group.group, fontWeight = FontWeight.SemiBold)
    Text(
        "n=${group.participants} · A1 ${analyticsFmt(group.baselineMean)} · B1 ${analyticsFmt(group.interventionMean)} por día" +
            (change?.let { " · ${signed(it)}%" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun chartPoints(points: List<AnalyticsPointDto>, view: AnalyticsView): List<AnalyticsPointDto> =
    when (view) {
        AnalyticsView.WEEKLY -> aggregate(points, 7)
        AnalyticsView.MONTHLY -> aggregate(points, 30)
        AnalyticsView.SESSIONS -> summarize(points)
    }

private fun aggregate(points: List<AnalyticsPointDto>, days: Int): List<AnalyticsPointDto> =
    points.groupBy { (it.day - 1) / days }.entries.sortedBy { it.key }.map { (bucket, values) ->
        AnalyticsPointDto(session = bucket + 1, day = values[values.size / 2].day, value = values.map { it.value }.average())
    }

/** Máximo 18 puntos visuales por fase; conserva todos los valores para la prueba. */
private fun summarize(points: List<AnalyticsPointDto>): List<AnalyticsPointDto> {
    if (points.size <= 18) return points
    val chunk = ceil(points.size / 18.0).toInt()
    return points.chunked(chunk).map { group ->
        val representative = group[group.size / 2]
        AnalyticsPointDto(
            session = representative.session,
            day = representative.day,
            date = representative.date,
            value = group.map { it.value }.average(),
        )
    }
}

private fun dimensionLabel(value: String): String = when (value) {
    "FRECUENCIA" -> "Frecuencia"
    "DURACION" -> "Duración"
    "INTENSIDAD" -> "Intensidad"
    else -> value.lowercase().replaceFirstChar { it.uppercase() }
}

/** Umbrales de Cohen sobre |dz|. */
private fun effectLabel(value: Double): String = when {
    abs(value) >= 0.8 -> "grande"
    abs(value) >= 0.5 -> "mediano"
    abs(value) >= 0.2 -> "pequeño"
    else -> "trivial"
}

private fun analyticsFmt(value: Double): String =
    if (abs(value - value.toInt()) < 0.005) value.toInt().toString()
    else ((value * 100).toInt() / 100.0).toString()

/** Número con coma decimal, para las fórmulas y la tabla. */
private fun dec(value: String): String = value.replace('.', ',')
private fun dec(value: Double): String = dec(analyticsFmt(value))
private fun signedValue(value: Double): String = (if (value > 0) "+" else "") + analyticsFmt(value)

private fun analyticsP(value: Double): String = if (value < 0.001) "< 0,001" else analyticsFmt(value).replace('.', ',')
private fun signed(value: Double): String = (if (value > 0) "+" else "") + analyticsFmt(value).replace('.', ',')
