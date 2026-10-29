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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.datetime.LocalDate
import org.usbrain.project.data.AnalyticsPointDto
import org.usbrain.project.data.AnalyticsProgramGroupDto
import org.usbrain.project.data.AnalyticsSeriesDto

private enum class AnalyticsView(val label: String) {
    SESSIONS("Por sesión"), WEEKLY("Semanal"), MONTHLY("Mensual"), CALENDAR("Calendario")
}

@Composable
internal fun AdminAnalyticsScreen(state: PrototypeState) {
    LaunchedEffect(Unit) { state.loadAnalytics() }
    val dataset = state.analyticsData
    // La API puede traer una serie por paciente. Para análisis poblacional se
    // presenta una única frecuencia total por variable y no una lista repetida.
    val series = aggregateSeriesForPicker(dataset?.series.orEmpty())
    var selectedId by remember(series) { mutableStateOf(series.firstOrNull()?.id) }
    val selected = series.firstOrNull { it.id == selectedId } ?: series.firstOrNull()

    ScreenScaffold {
        Text("Análisis estadístico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Comparación exploratoria entre línea base e intervención con datos mínimos seudonimizados.",
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
                        Text(
                            "APEX y ORDS consultan el mismo esquema Oracle; no se necesita una segunda conexión ni importar archivos manualmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("Seudonimizado", LocalUsBrainSemantic.current.success)
                            Pill("${series.size} variables analizables", MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "Excluidos: nombres, correos, documentos, diagnósticos, notas y fechas exactas.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (series.isEmpty()) {
                    Text("Se requieren al menos 3 registros de línea base y 3 de intervención para formar una serie.")
                } else {
                    DropdownField(
                        label = "Variable para analizar",
                        selectedLabel = selected?.let { "${it.behavior} · ${dimensionLabel(it.dimension)}" },
                        placeholder = "Selecciona una serie",
                        options = series.map { it.id to "${it.behavior} · ${dimensionLabel(it.dimension)}" },
                        onSelect = { selectedId = it },
                        searchable = true,
                    )
                    selected?.let { AnalyticsSeriesPanel(it) }
                    GlobalAnalyticsPanel(
                        series = series,
                        groups = dataset.programGroups,
                        ageGroups = dataset.ageGroups,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AnalyticsSeriesPanel(series: AnalyticsSeriesDto) {
    val result = remember(series) { analyzeHypothesis(series) }
    var view by remember { mutableStateOf(AnalyticsView.SESSIONS) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(series.behavior, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Variable medida: ${dimensionLabel(series.dimension)} (${series.unit})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AnalyticsView.entries.forEach { option ->
                    FilterChip(selected = view == option, onClick = { view = option }, label = { Text(option.label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Días consecutivos del seguimiento", style = MaterialTheme.typography.labelMedium)
                Text("Medición: total diario · ${series.unit}", style = MaterialTheme.typography.labelMedium)
            }
            if (view == AnalyticsView.CALENDAR) {
                AnalyticsCalendar(series)
            } else {
                AnalyticsPhaseChart(series, view)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsLegend(LocalUsBrainSemantic.current.baseline, "Línea base · n=${series.baseline.size}")
                AnalyticsLegend(LocalUsBrainSemantic.current.intervention, "Intervención · n=${series.intervention.size}")
            }
            Text(
                "La visualización puede agrupar registros para facilitar la lectura; la prueba utiliza todos los valores originales.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Prueba de hipótesis exploratoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val decreaseExpected = series.expectedDirection != "INCREASE"
            Text(
                if (decreaseExpected) "H₀: μ con tratamiento ≥ μ sin tratamiento." else "H₀: μ con intervención ≤ μ de línea base.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (decreaseExpected) "H₁: μ con tratamiento < μ sin tratamiento (disminuyen los episodios diarios)."
                else "H₁: μ con intervención > μ de línea base.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (result == null) {
                Text("No hay observaciones suficientes para calcular la comparación.", color = MaterialTheme.colorScheme.error)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(analyticsFmt(result.baselineMean), "promedio base", LocalUsBrainSemantic.current.baseline)
                    StatTile(analyticsFmt(result.interventionMean), "promedio intervención", LocalUsBrainSemantic.current.intervention)
                    StatTile(analyticsP(result.pValue), "valor p", MaterialTheme.colorScheme.primary)
                }
                val significant = result.pValue < 0.05
                Text(
                    if (significant) "Resultado: se rechaza H₀ con α = 0,05 en la dirección esperada."
                    else "Resultado: no hay evidencia suficiente para rechazar H₀ con α = 0,05.",
                    fontWeight = FontWeight.SemiBold,
                    color = if (significant) LocalUsBrainSemantic.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Cambio medio: ${signed(result.difference)} ${series.unit} · " +
                        "cambio relativo: ${result.percentChange?.let { signed(it) + "%" } ?: "no calculable"} · " +
                        "tamaño del efecto d: ${analyticsFmt(result.effectSize)}.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (result.supportsExpectedDirection) "La dirección observada coincide con la esperada para esta conducta."
                    else "La dirección observada no coincide con la esperada para esta conducta.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Advertencia metodológica: es una comparación demostrativa de dos medias. Las observaciones de series temporales pueden estar autocorrelacionadas; el resultado no demuestra causalidad ni reemplaza el análisis visual y clínico del profesional.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun AnalyticsPhaseChart(series: AnalyticsSeriesDto, view: AnalyticsView, compact: Boolean = false) {
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
    val xTicks = when {
        dayRange <= 7 -> all.map { it.day }.distinct()
        dayRange <= 31 -> (firstDay..lastDay step 7).toList().let { ticks ->
            (ticks + lastDay).distinct()
        }
        else -> (firstDay..lastDay step 7).toList().let { ticks ->
            (ticks + lastDay).distinct()
        }
    }

    Canvas(Modifier.fillMaxWidth().height(if (compact) 175.dp else 245.dp)) {
        val left = 46f
        val right = 14f
        val top = 18f
        val bottom = 46f
        val width = size.width - left - right
        val chartHeight = size.height - top - bottom
        fun y(value: Double) = top + chartHeight - ((value - minValue) / (maxValue - minValue)).toFloat() * chartHeight
        fun x(point: AnalyticsPointDto): Float {
            return left + if (lastDay == firstDay) width / 2f else (point.day - firstDay).toFloat() / (lastDay - firstDay) * width
        }
        fun xDay(day: Int): Float = left + if (lastDay == firstDay) width / 2f else (day - firstDay).toFloat() / (lastDay - firstDay) * width

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
private fun AnalyticsCalendar(series: AnalyticsSeriesDto) {
    val all = series.baseline.map { Triple(it, "Base", LocalUsBrainSemantic.current.baseline) } +
        series.intervention.map { Triple(it, "Interv.", LocalUsBrainSemantic.current.intervention) }
    val dated = all.mapNotNull { row -> row.first.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it to row } }
    val months = dated.map { it.first.year to it.first.monthNumber }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    var selectedMonth by remember(series) { mutableStateOf(months.firstOrNull()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Seleccionar periodo", fontWeight = FontWeight.SemiBold)
        if (dated.isEmpty()) {
            Text("Por privacidad, los casos reales usan un calendario relativo.", style = MaterialTheme.typography.labelSmall)
            RelativeCalendar(all)
        } else selectedMonth?.let { month ->
            val index = months.indexOf(month)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FilterChip(selected = false, enabled = index > 0, onClick = { selectedMonth = months[index - 1] }, label = { Text("‹") })
                Text("${monthName(month.second)} de ${month.first}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                FilterChip(selected = false, enabled = index < months.lastIndex, onClick = { selectedMonth = months[index + 1] }, label = { Text("›") })
            }
            val monthRows = dated.filter { it.first.year == month.first && it.first.monthNumber == month.second }
            DatedMonthGrid(month.first, month.second, monthRows)
        }
    }
}

@Composable
private fun RelativeCalendar(all: List<Triple<AnalyticsPointDto, String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        all.forEach { (point, phase, color) ->
            Column(Modifier.size(70.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.13f)).padding(7.dp)) {
                Text("Día ${point.day}", style = MaterialTheme.typography.labelSmall)
                Text(analyticsFmt(point.value), fontWeight = FontWeight.Bold, color = color)
                Text(phase, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DatedMonthGrid(year: Int, month: Int, rows: List<Pair<LocalDate, Triple<AnalyticsPointDto, String, Color>>>) {
    val byDay = rows.associateBy { it.first.dayOfMonth }
    val first = LocalDate(year, month, 1)
    val days = daysInMonth(year, month)
    val leading = first.dayOfWeek.ordinal
    Row(Modifier.fillMaxWidth()) {
        listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold) }
    }
    (List(leading) { 0 } + (1..days).toList()).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (week + List(7 - week.size) { 0 }).forEach { day ->
                val record = byDay[day]
                Column(
                    Modifier.weight(1f).padding(2.dp).height(56.dp).clip(RoundedCornerShape(9.dp))
                        .background(record?.second?.third?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(5.dp),
                ) {
                    if (day > 0) Text(day.toString(), style = MaterialTheme.typography.labelMedium)
                    record?.let { Text(analyticsFmt(it.second.first.value), fontWeight = FontWeight.Bold, color = it.second.third) }
                }
            }
        }
    }
}

@Composable
private fun GlobalAnalyticsPanel(
    series: List<AnalyticsSeriesDto>,
    groups: List<AnalyticsProgramGroupDto>,
    ageGroups: List<AnalyticsProgramGroupDto>,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        OutlinedButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Minimizar vista global" else "Vista global")
        }
    }
    if (!expanded) return
    Text("Vista global", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Todas las gráficas conservan sus fases, dimensión y unidad de medida.", style = MaterialTheme.typography.bodySmall)
    series.forEach { item ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.behavior, fontWeight = FontWeight.Bold)
                Text("${dimensionLabel(item.dimension)} · ${item.unit}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                AnalyticsPhaseChart(item, AnalyticsView.SESSIONS, compact = true)
            }
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Comparación por programa académico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (groups.isEmpty()) {
                Text("Todavía no hay grupos con al menos 3 participantes y datos en ambas fases. Se ocultan los grupos pequeños para proteger la identidad.", style = MaterialTheme.typography.bodySmall)
            } else groups.forEach { group ->
                CohortComparisonRow(group)
            }
            Text("Estas diferencias son descriptivas: no permiten afirmar que una carrera cause más o menos estrés.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Frecuencia de episodios por rango de edad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Cohorte ficticia de 50 pacientes · promedio diario de episodios", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ageGroups.isEmpty()) {
                Text("Aún no hay datos suficientes de la cohorte para agrupar por edad.", style = MaterialTheme.typography.bodySmall)
            } else ageGroups.forEach { group ->
                CohortComparisonRow(group)
            }
            Text("Los rangos de edad y programas sirven para explorar patrones. No identifican personas ni prueban causalidad.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Consolida las series homónimas para evitar exponer un menú por paciente. */
private fun aggregateSeriesForPicker(source: List<AnalyticsSeriesDto>): List<AnalyticsSeriesDto> =
    source.groupBy { it.behavior to it.dimension }.values.map { matching ->
        if (matching.size == 1) matching.first() else {
            val first = matching.first()
            first.copy(
                id = "total:${first.behavior}:${first.dimension}",
                behavior = "Frecuencia total de ${first.behavior.lowercase()}",
                unit = "episodios diarios (total)",
                baseline = mergeDailyPoints(matching.flatMap { it.baseline }),
                intervention = mergeDailyPoints(matching.flatMap { it.intervention }),
            )
        }
    }.sortedBy { it.behavior }

private fun mergeDailyPoints(points: List<AnalyticsPointDto>): List<AnalyticsPointDto> =
    points.groupBy { it.day }.entries.sortedBy { it.key }.mapIndexed { index, (day, daily) ->
        AnalyticsPointDto(
            session = index + 1,
            day = day,
            date = daily.firstOrNull()?.date,
            value = daily.sumOf { it.value },
        )
    }

@Composable
private fun CohortComparisonRow(group: AnalyticsProgramGroupDto) {
    val change = if (group.baselineMean == 0.0) null else (group.interventionMean - group.baselineMean) / group.baselineMean * 100
    Text(group.program, fontWeight = FontWeight.SemiBold)
    Text(
        "n=${group.participants} · sin tratamiento ${analyticsFmt(group.baselineMean)} · con tratamiento ${analyticsFmt(group.interventionMean)} episodios/día" +
            (change?.let { " · ${signed(it)}%" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun chartPoints(points: List<AnalyticsPointDto>, view: AnalyticsView): List<AnalyticsPointDto> =
    when (view) {
        AnalyticsView.WEEKLY -> aggregate(points, 7)
        AnalyticsView.MONTHLY -> aggregate(points, 30)
        else -> summarize(points)
    }

private fun aggregate(points: List<AnalyticsPointDto>, days: Int): List<AnalyticsPointDto> =
    points.groupBy { (it.day - 1) / days }.entries.sortedBy { it.key }.map { (bucket, values) ->
        AnalyticsPointDto(session = bucket + 1, day = values[values.size / 2].day, value = values.map { it.value }.average())
    }

private fun monthName(month: Int): String = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")[month - 1]
private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
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

private fun analyticsFmt(value: Double): String =
    if (abs(value - value.toInt()) < 0.005) value.toInt().toString()
    else ((value * 100).toInt() / 100.0).toString()

private fun analyticsP(value: Double): String = if (value < 0.001) "< 0,001" else analyticsFmt(value).replace('.', ',')
private fun signed(value: Double): String = (if (value > 0) "+" else "") + analyticsFmt(value).replace('.', ',')

