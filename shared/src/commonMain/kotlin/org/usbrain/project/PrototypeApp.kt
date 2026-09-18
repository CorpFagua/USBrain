@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class,
)

package org.usbrain.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

@Composable
fun PrototypeApp(state: PrototypeState, dark: Boolean, onToggleTheme: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (showsBottomBar(state)) BottomBar(state) },
        floatingActionButton = {
            // "Crear" siempre como botón flotante en las listas de este flujo (conductas,
            // actividades); el resto de acciones va como botón normal dentro del detalle.
            val behaviorId = state.activeBehaviorId
            if (state.screen == Screen.THERAPIST_PATIENT) {
                FloatingActionButton(onClick = { state.startNewBehavior(state.activePatientId) }) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            } else if (state.screen == Screen.THERAPIST_BEHAVIOR && behaviorId != null && state.behaviorDetailTab == BehaviorTab.ACTIVIDADES) {
                // Solo en la pestaña de "Actividades": en "Análisis" no hay nada que crear.
                FloatingActionButton(onClick = { state.startNewTask(state.activePatientId, behaviorId) }) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (state.screen) {
                Screen.LOGIN -> LoginScreen(state)
                Screen.TWO_FACTOR -> TwoFactorScreen(state)
                Screen.PATIENT_HOME -> PatientHomeScreen(state, dark, onToggleTheme)
                Screen.PATIENT_BEHAVIORS -> PatientBehaviorsScreen(state)
                Screen.PATIENT_BEHAVIOR -> PatientBehaviorScreen(state)
                Screen.PATIENT_TASK -> PatientTaskScreen(state)
                Screen.PATIENT_TASK_HISTORY -> PatientTaskHistoryScreen(state)
                Screen.PATIENT_DONE -> PatientDoneScreen(state)
                Screen.PATIENT_PROGRESS -> PatientProgressScreen(state)
                Screen.PATIENT_PROFILE -> PatientProfileScreen(state)
                Screen.THERAPIST_HOME -> TherapistHomeScreen(state, dark, onToggleTheme)
                Screen.THERAPIST_PATIENT -> TherapistPatientScreen(state)
                Screen.THERAPIST_PATIENT_INFO -> TherapistPatientInfoScreen(state)
                Screen.THERAPIST_BEHAVIOR -> TherapistBehaviorScreen(state)
                Screen.THERAPIST_TASK_DETAIL -> TherapistTaskDetailScreen(state)
                Screen.THERAPIST_NEW_BEHAVIOR -> TherapistNewBehaviorScreen(state)
                Screen.THERAPIST_BASELINE_SETUP -> TherapistBaselineSetupScreen(state)
                Screen.THERAPIST_NEW_TASK -> TherapistNewTaskScreen(state)
                Screen.THERAPIST_NEW_WIDGET -> TherapistNewWidgetScreen(state)
                Screen.THERAPIST_ASSIGNED -> TherapistAssignedScreen(state)
                Screen.THERAPIST_PROFILE -> TherapistProfileScreen(state)
                Screen.ADMIN_HOME -> AdminHomeScreen(state, dark, onToggleTheme)
            }
        }
    }
}

private fun showsBottomBar(state: PrototypeState): Boolean {
    if (state.role == null) return false
    return state.screen !in listOf(
        Screen.LOGIN, Screen.TWO_FACTOR,
        // Al entrar a una conducta quedas dentro de ese flujo (Historial → conducta → tarea /
        // historial de registros): las tabs se ocultan hasta volver con el botón atrás.
        // PATIENT_BEHAVIORS (la pestaña "Historial" en sí) sí muestra las tabs, por eso no está en esta lista.
        Screen.PATIENT_BEHAVIOR, Screen.PATIENT_TASK, Screen.PATIENT_TASK_HISTORY, Screen.PATIENT_DONE,
        // Al entrar a un paciente quedas dentro de ese flujo (paciente → conducta →
        // actividad): las tabs se ocultan hasta que vuelves a "Pacientes" con el botón atrás.
        Screen.THERAPIST_PATIENT, Screen.THERAPIST_PATIENT_INFO, Screen.THERAPIST_BEHAVIOR, Screen.THERAPIST_TASK_DETAIL,
        Screen.THERAPIST_NEW_BEHAVIOR, Screen.THERAPIST_BASELINE_SETUP, Screen.THERAPIST_NEW_TASK, Screen.THERAPIST_ASSIGNED,
    )
}

/* ----------------------------- barras ----------------------------- */

/** Botón circular flotante (sin caja rectangular) para acciones puntuales del header de cada pantalla. */
@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(42.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** Header de las pantallas de inicio: avatar + saludo a la izquierda, acciones (tema/cerrar sesión) a la derecha. */
@Composable
private fun HomeHeader(
    greeting: String,
    name: String,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onLogout: () -> Unit,
    avatar: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            avatar()
            Column {
                Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
        Box {
            CircleIconButton(onClick = { menu = true }) { Text("⋯", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (dark) "Tema claro" else "Tema oscuro") },
                    onClick = { onToggleTheme(); menu = false },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Cerrar sesión") },
                    onClick = { onLogout(); menu = false },
                )
            }
        }
    }
}

/** Header de las pantallas secundarias: botón circular de volver + título, sin barra ni caja de fondo. */
@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIconButton(onClick = onBack) { NavGlyph("back", selected = false) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * Pantalla "dueña" de un sub-flujo, para resaltar el tab correcto en la barra inferior aunque
 * estemos en una pantalla que no es en sí un tab (p. ej. la ficha de un paciente cuelga de
 * "Pacientes"). Es la única pieza que conoce esa jerarquía; los tabs en sí salen de [RoleProfile].
 */
private fun parentTab(screen: Screen): Screen = when (screen) {
    Screen.PATIENT_TASK, Screen.PATIENT_DONE -> Screen.PATIENT_HOME
    // Conducta y su historial cuelgan de la pestaña "Historial", no de "Inicio".
    Screen.PATIENT_BEHAVIOR, Screen.PATIENT_TASK_HISTORY -> Screen.PATIENT_BEHAVIORS
    Screen.THERAPIST_PATIENT, Screen.THERAPIST_PATIENT_INFO, Screen.THERAPIST_BEHAVIOR, Screen.THERAPIST_TASK_DETAIL,
    Screen.THERAPIST_NEW_BEHAVIOR, Screen.THERAPIST_NEW_TASK, Screen.THERAPIST_ASSIGNED -> Screen.THERAPIST_HOME
    else -> screen
}

// Sin barra que los conecte ni texto: cada tab es su propio círculo flotando sobre el
// fondo, solo con el ícono (el label de NavTab queda como descripción para accesibilidad).
@Composable
private fun BottomBar(state: PrototypeState) {
    val tabs = state.currentProfile?.tabs ?: return
    val current = parentTab(state.screen)
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (target, label, glyph) ->
            val selected = current == target
            Surface(
                onClick = { state.screen = target },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 6.dp,
                modifier = Modifier.size(50.dp).semantics { contentDescription = label },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NavGlyph(glyph, selected)
                }
            }
        }
    }
}

/* ----------------------------- utilidades UI ----------------------------- */

@Composable
private fun ScreenScaffold(content: @Composable ColumnScope.() -> Unit) {
    // Sin statusBarsPadding(): Scaffold ya reserva ese espacio en `inner` al no haber topBar;
    // duplicarlo aquí dejaba un hueco enorme y vacío arriba de cada pantalla.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** Chip seleccionable en forma de píldora completa (redondeo total, no la esquina M3 por defecto). */
@Composable
private fun TabChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    // Sin relleno tonal: fondo blanco/negro real siempre, y borde+texto del color de acento
    // solo cuando está seleccionado, para que el color se lea como contraste real y no un lavado.
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = accent,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp,
        ),
    )
}

@Composable
// Sin relleno de color: fondo neutro (blanco/negro real) + borde y texto del color de acento,
// para que el contraste sea real en vez de un lavado pálido del color semántico.
private fun Pill(text: String, accent: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, accent, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        // maxLines=1 evita que un pill se "rompa" a dos líneas si el contenedor lo exprime.
        Text(text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
    }
}

/** Desplegable simple: botón con la selección actual + menú. `options` es lista de (id, etiqueta). */
@Composable
private fun DropdownField(
    label: String,
    selectedLabel: String?,
    placeholder: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedLabel ?: placeholder, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, optionLabel) ->
                    DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onSelect(id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun PhasePill(phase: Phase) {
    val s = LocalUsBrainSemantic.current
    val c = if (phase == Phase.LINEA_BASE) s.baseline else s.intervention
    Pill(phase.label, c)
}

@Composable
private fun StatusPill(status: TaskStatus) {
    val s = LocalUsBrainSemantic.current
    if (status == TaskStatus.COMPLETADA) {
        Pill("Completada", s.success)
    } else {
        Pill("Pendiente", s.warning)
    }
}

@Composable
private fun Avatar(patient: Patient, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.33f)).background(Color(patient.colorArgb)),
        contentAlignment = Alignment.Center,
    ) {
        Text(patient.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp)
    }
}

@Composable
private fun RowScope.StatTile(value: String, label: String, accent: Color) {
    ElevatedCard(Modifier.weight(1f)) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun fmt(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else ((v * 10).roundToInt() / 10.0).toString()

private fun unitShort(d: Dimension): String = when (d) {
    Dimension.INTENSIDAD -> "/10"
    Dimension.DURACION -> "min"
    Dimension.FRECUENCIA -> "episodios"
}

// Registro autónomo mide la dimensión de la conducta; el resto de tipos usa su propia unidad_metrica.
private fun taskUnitLabel(task: TrackTask): String = if (task.taskType == TaskType.REGISTRO_AUTONOMO) {
    unitShort(task.dimension)
} else when (task.metricUnit) {
    MetricUnit.MINUTOS -> "min"
    MetricUnit.REPETICIONES -> "rep"
    MetricUnit.ESCALA_1_5 -> "/5"
    MetricUnit.SOLO_COMPLETADO, null -> ""
}

private fun axisUnit(d: Dimension): String = when (d) {
    Dimension.INTENSIDAD -> "malestar 0–10"
    Dimension.DURACION -> "minutos"
    Dimension.FRECUENCIA -> "veces que se repite en el día"
}

@Composable
private fun TaskCard(task: TrackTask, pendingCta: String = "Llenar tarea →", onClick: (() -> Unit)? = null) {
    val s = LocalUsBrainSemantic.current
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val measure = if (task.taskType == TaskType.REGISTRO_AUTONOMO) task.dimension.label else task.metricUnit?.label.orEmpty()
            SectionLabel("${task.taskType.label} · $measure", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusPill(task.status)
        }
        Spacer(Modifier.height(8.dp))
        Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            task.instructions,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                task.dueDate?.let { "Vence · $it" } ?: "Sin fecha límite",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (task.status == TaskStatus.COMPLETADA) {
                val last = task.entries.lastOrNull()
                val completedLabel = if (task.taskType != TaskType.REGISTRO_AUTONOMO && task.metricUnit == MetricUnit.SOLO_COMPLETADO) {
                    "✓ Completada"
                } else {
                    "✓ " + (last?.let { "${fmt(it.value)} ${taskUnitLabel(task)}" } ?: "registrada")
                }
                Text(
                    completedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = s.success,
                    fontWeight = FontWeight.SemiBold,
                )
            } else if (onClick != null) {
                Text(
                    pendingCta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), border = border) {
            Column(Modifier.padding(14.dp), content = inner)
        }
    } else {
        OutlinedCard(modifier = Modifier.fillMaxWidth(), border = border) {
            Column(Modifier.padding(14.dp), content = inner)
        }
    }
}

@Composable
private fun TrendChart(behavior: Behavior, dimension: Dimension, phase: Phase, yMax: Double, modifier: Modifier = Modifier) {
    val s = LocalUsBrainSemantic.current
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val gridColor = s.grid
    val baseC = s.baseline
    val intC = s.intervention
    val series = behavior.chartValuesFor(dimension)
        .filter { it.first.phase == phase }
        .map { it.first.date to it.second }
        .sortedBy { it.first }
    val dataPoints = series.map { it.second }
    val lineColor = Color(0xFF008DD2)
    val pointColor = Color(0xFFF44336)
    Canvas(modifier.fillMaxWidth().height(190.dp)) {
        if (dataPoints.isEmpty()) return@Canvas
        val n = dataPoints.size
        val padL = 26f
        val padR = 8f
        val padT = 10f
        val padB = 38f
        val iw = size.width - padL - padR
        val ih = size.height - padT - padB
            val maxV = yMax.coerceAtLeast(1.0)
        fun xAt(i: Int): Float = padL + if (n <= 1) 0f else i.toFloat() / (n - 1) * iw
        fun yAt(v: Double): Float = padT + ih - (v / maxV).toFloat() * ih
        val gridSteps = maxV.toInt()
        for (g in 0..gridSteps) {
            val gy = padT + ih * g / gridSteps
            drawLine(gridColor, Offset(padL, gy), Offset(size.width - padR, gy), 1f)
            val value = maxV * (gridSteps - g) / gridSteps
            val label = measurer.measure(fmt(value), style = TextStyle(fontSize = 9.sp, color = axisColor))
            drawText(label, topLeft = Offset(padL - label.size.width - 6f, gy - label.size.height / 2f))
        }
        val path = Path()
        dataPoints.forEachIndexed { i, v -> if (i == 0) path.moveTo(xAt(i), yAt(v)) else path.lineTo(xAt(i), yAt(v)) }
        drawPath(
            path,
            lineColor,
            style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        dataPoints.forEachIndexed { i, v ->
            val last = i == n - 1
            drawCircle(pointColor, if (last) 3.4f else 2.2f, Offset(xAt(i), yAt(v)))
            if (last) drawCircle(surface, 4.5f, Offset(xAt(i), yAt(v)), style = Stroke(2f))
        }
        series.forEachIndexed { index, point ->
            val dayName = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")[point.first.dayOfWeek.ordinal]
            val label = measurer.measure(dayName, style = TextStyle(fontSize = 9.sp, color = axisColor, fontWeight = FontWeight.SemiBold))
            drawText(label, topLeft = Offset(xAt(index) - label.size.width / 2f, size.height - padB + 9f))
        }
    }
}

// La gráfica pertenece a la conducta, no a una actividad puntual: `dimension` decide qué
// variable de las que mide la conducta se está mostrando (frecuencia, duración, intensidad…),
// y es intercambiable cuando la conducta tiene más de una. Por defecto usa la dimensión
// principal de la conducta para las vistas que no ofrecen selector propio.
@Composable
private fun ChartCard(behavior: Behavior, dimension: Dimension = behavior.dimension) {
    val s = LocalUsBrainSemantic.current
    val baselineRecords = behavior.baselineEntries.filter { it.dimension == dimension }
    val interventionRecords = behavior.recordsFor(dimension).filter { it.phase == Phase.INTERVENCION }
    val yMax = behavior.chartValuesFor(dimension).maxOfOrNull { it.second }?.let { kotlin.math.ceil(it) } ?: 1.0
    val expectedDirection = if (behavior.type == BehaviorType.ADAPTATIVA) "Se espera aumento" else "Se espera reducción"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        behavior.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tendencia · ${dimension.label} · ${behavior.type.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                PhasePill(if (behavior.baselineOpen) Phase.LINEA_BASE else Phase.INTERVENCION)
            }
            Spacer(Modifier.height(10.dp))
            PhaseChartSection("Línea base de conducta", baselineRecords.isNotEmpty()) {
                TrendChart(behavior, dimension, Phase.LINEA_BASE, yMax)
            }
            Spacer(Modifier.height(14.dp))
            PhaseChartSection("Conducta en intervención", interventionRecords.isNotEmpty()) {
                TrendChart(behavior, dimension, Phase.INTERVENCION, yMax)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(s.baseline, "Línea base (${behavior.observationCountFor(dimension)} obs.)")
                LegendDot(s.intervention, "Intervención")
            }
            Text(
                "Eje X: día de observación · Eje Y: ${axisUnit(dimension)} · $expectedDirection",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            val baselineAverage = baselineRecords.map { it.valueForChart() }.takeIf { it.isNotEmpty() }?.average()
            val interventionAverage = interventionRecords.map { it.valueForChart() }.takeIf { it.isNotEmpty() }?.average()
            if (baselineAverage != null && interventionAverage != null) {
                val change = interventionAverage - baselineAverage
                val direction = if (change < 0) "disminuyó" else if (change > 0) "aumentó" else "se mantuvo"
                Text(
                    "Lectura inicial: el promedio ${direction} de ${fmt(baselineAverage)} a ${fmt(interventionAverage)} entre línea base e intervención.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// Gráfica simple X=fecha/Y=valor para actividades con widget numérico configurable: no
// distingue fases de línea base/intervención (eso es exclusivo del flujo legacy por Dimension).
@Composable
private fun WidgetTrendChart(task: TrackTask, widget: WidgetConfig, series: List<Pair<LocalDate, Double>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = LocalUsBrainSemantic.current.grid
    val surface = MaterialTheme.colorScheme.surface
    val lineColor = Color(0xFF008DD2)
    val pointColor = Color(0xFFF44336)
    val minV = widget.minValue ?: 0.0
    val maxV = (widget.maxValue ?: series.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(minV + 1.0)
    Canvas(modifier.fillMaxWidth().height(190.dp)) {
        if (series.isEmpty()) return@Canvas
        val n = series.size
        val padL = 26f
        val padR = 8f
        val padT = 10f
        val padB = 24f
        val iw = size.width - padL - padR
        val ih = size.height - padT - padB
        fun xAt(i: Int): Float = padL + if (n <= 1) 0f else i.toFloat() / (n - 1) * iw
        fun yAt(v: Double): Float = padT + ih - ((v - minV) / (maxV - minV)).toFloat() * ih
        for (g in 0..3) {
            val gy = padT + ih * g / 3
            drawLine(gridColor, Offset(padL, gy), Offset(size.width - padR, gy), 1f)
            val value = minV + (maxV - minV) * (3 - g) / 3
            val label = measurer.measure(fmt(value), style = TextStyle(fontSize = 9.sp, color = axisColor))
            drawText(label, topLeft = Offset(padL - label.size.width - 6f, gy - label.size.height / 2f))
        }
        val path = Path()
        series.forEachIndexed { i, (_, v) -> if (i == 0) path.moveTo(xAt(i), yAt(v)) else path.lineTo(xAt(i), yAt(v)) }
        drawPath(path, lineColor, style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        series.forEachIndexed { i, (_, v) ->
            val last = i == n - 1
            drawCircle(pointColor, if (last) 3.4f else 2.2f, Offset(xAt(i), yAt(v)))
            if (last) drawCircle(surface, 4.5f, Offset(xAt(i), yAt(v)), style = Stroke(2f))
            val dayLabel = spanishWeekday(series[i].first)
            val label = measurer.measure(dayLabel, style = TextStyle(fontSize = 9.sp, color = axisColor))
            drawText(label, topLeft = Offset(xAt(i) - label.size.width / 2f, size.height - 17f))
        }
    }
    if (series.isNotEmpty()) {
        Text(
            "${spanishWeekday(series.first().first)} → ${spanishWeekday(series.last().first)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WidgetChartCard(task: TrackTask, widget: WidgetConfig) {
    var period by remember(task.id) { mutableStateOf<Int?>(null) }
    val allSeries = task.numericWidgetSeries()
    val latest = allSeries.maxOfOrNull { it.first }
    val series = allSeries.filter { period == null || latest == null || (latest.toEpochDays() - it.first.toEpochDays()) <= period!! }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(task.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tendencia · ${widget.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Dirección esperada: ${expectedWidgetDirection(widget)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(null to "Todo", 7 to "7 días", 14 to "14 días", 30 to "30 días").forEach { (days, label) ->
                    TabChip(period == days, { period = days }, label)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (series.isEmpty()) {
                Text(
                    "Aún no hay registros para esta actividad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WidgetTrendChart(task, widget, series)
                Text("Escala estable: ${fmt(widget.minValue ?: 0.0)} a ${fmt(widget.maxValue ?: series.maxOf { it.second })}${widget.unit?.let { " $it" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Contexto de cada registro", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                task.widgetEntries.filter { entry -> series.any { it.first == entry.date } }.sortedByDescending { it.date }.forEach { entry ->
                    val context = entry.note?.takeIf { it.isNotBlank() } ?: "Sin contexto añadido"
                    Text("${entry.date.dayOfMonth}/${entry.date.monthNumber} · ${widgetValueSummary(entry.value)} · $context", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Eje X: fecha del registro · Eje Y: ${widget.name}${widget.unit?.let { " ($it)" } ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PhaseChartSection(title: String, hasRecords: Boolean, chart: @Composable () -> Unit) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    if (hasRecords) {
        chart()
    } else {
        Text(
            "Aún no hay observaciones en esta fase.",
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChartCard(patient: Patient) {
    patient.behaviors.forEach { behavior -> ChartCard(behavior) }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ----------------------------- acceso ----------------------------- */

@Composable
private fun LoginScreen(state: PrototypeState) {
    val email = remember(state.loginRole) {
        mutableStateOf(
            when (state.loginRole) {
                Role.PATIENT -> "ana.torres@usbbog.edu.co"
                Role.THERAPIST -> "e.patino@usb.edu.co"
                Role.ADMIN -> "admin@usbbog.edu.co"
            },
        )
    }
    val pass = remember(state.loginRole) { mutableStateOf("claveDemo2026") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        UsBrainMark(size = 60.dp)
        Spacer(Modifier.height(12.dp))
        Row {
            Text("US", color = Brand.Orange, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text("Brain", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
        }
        Text(
            "REGISTRA · ENTIENDE · TRANSFORMA",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(28.dp))
        SectionLabel("Ingresa como", Modifier.fillMaxWidth().padding(bottom = 8.dp))
        // Los perfiles disponibles salen de state.roleProfiles: agregar un rol nuevo al catálogo
        // lo hace aparecer aquí solo, sin tocar esta pantalla.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.roleProfiles.forEach { profile ->
                RoleChoiceCard(profile, state.loginRole == profile.role) { state.loginRole = profile.role }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            email.value, { email.value = it }, Modifier.fillMaxWidth(),
            label = { Text("Correo institucional") }, singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            pass.value, { pass.value = it }, Modifier.fillMaxWidth(),
            label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = { state.screen = Screen.TWO_FACTOR }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Iniciar sesión")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "A continuación se solicita un código de verificación (2FA), requisito de seguridad para los datos clínicos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Universidad de San Buenaventura · Bogotá",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleChoiceCard(profile: RoleProfile, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), border = border) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(profile.badgeColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(profile.displayName.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TwoFactorScreen(state: PrototypeState) {
    val digits = remember { mutableStateListOf("3", "0", "4", "9", "1", "7") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircleIconButton(onClick = { state.screen = Screen.LOGIN }) { NavGlyph("back", selected = false) }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text("2FA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Verificación en dos pasos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ingresa el código de 6 dígitos enviado a tu correo institucional.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            digits.forEachIndexed { i, d ->
                OutlinedTextField(
                    value = d,
                    onValueChange = { v -> digits[i] = v.filter { it.isDigit() }.take(1) },
                    modifier = Modifier.width(42.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Código de demostración precargado · 304 917",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = { state.verify() }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Verificar e ingresar")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { state.screen = Screen.LOGIN }, Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}

/* ----------------------------- paciente ----------------------------- */

@Composable
private fun PatientHomeScreen(state: PrototypeState, dark: Boolean, onToggleTheme: () -> Unit) {
    val p = state.me()
    val caseId = state.caseForPatient(p.id)
    val behaviors = if (caseId != null) state.behaviorsForCase(caseId) else emptyList()
    val done = p.tasks.filter { it.status == TaskStatus.COMPLETADA }
    val pending = behaviors.flatMap { b -> state.tasksForBehavior(b.id).filter { it.status == TaskStatus.PENDIENTE } }
    ScreenScaffold {
        HomeHeader(
            greeting = "Hola 👋",
            name = p.firstName,
            dark = dark,
            onToggleTheme = onToggleTheme,
            onLogout = { state.logout() },
            avatar = { Avatar(p) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${3 + done.size}", "días de racha", MaterialTheme.colorScheme.primary)
            StatTile("${pending.size}", if (pending.size == 1) "tarea pendiente" else "tareas pendientes", MaterialTheme.colorScheme.onSurface)
        }
        // Inicio es solo la bandeja de hoy: qué falta por registrar. El resto —conductas,
        // historial de registros, gráficas— vive en sus propias pestañas (Historial, Progreso),
        // no apilado aquí.
        SectionLabel("Tareas de hoy")
        if (pending.isEmpty()) {
            Text(
                if (behaviors.isEmpty()) "Tu terapeuta aún no ha creado conductas para tu caso." else "Estás al día: no tienes tareas pendientes por ahora.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            pending.forEach { t -> TaskCard(t) { state.openTask(t.id) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientBehaviorsScreen(state: PrototypeState) {
    val p = state.me()
    val behaviors = state.behaviorsFor(p.id)
    ScreenScaffold {
        Text("Historial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Toca una conducta para ver todas sus tareas y el historial de tus registros.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (behaviors.isEmpty()) {
            Text(
                "Tu terapeuta aún no ha creado conductas para tu caso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            behaviors.forEach { b -> BehaviorRow(b) { state.openPatientBehavior(b.id) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientBehaviorScreen(state: PrototypeState) {
    val behavior = state.activeBehavior()
    ScreenScaffold {
        BackHeader(behavior.name) { state.screen = Screen.PATIENT_BEHAVIORS }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhasePill(if (behavior.baselineOpen) Phase.LINEA_BASE else Phase.INTERVENCION)
            Pill(behavior.type.label, MaterialTheme.colorScheme.onSurfaceVariant)
            Pill(behavior.dimension.label, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            behavior.operationalDefinition,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Pendientes y ya registradas juntas, pero cada una lleva a la pantalla que le
        // corresponde: llenar el formulario, o revisar el historial completo de esa tarea
        // (una tarea recurrente puede tener muchos registros a lo largo del tiempo).
        SectionLabel("Tareas de esta conducta")
        if (behavior.tasks.isEmpty()) {
            Text(
                "Todavía no hay tareas asignadas para esta conducta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            behavior.tasks.forEach { t ->
                if (t.status == TaskStatus.PENDIENTE) {
                    TaskCard(t) { state.openTask(t.id, behavior.id) }
                } else {
                    TaskCard(t, pendingCta = "Ver historial →") { state.openTaskHistory(t.id, behavior.id) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientTaskHistoryScreen(state: PrototypeState) {
    val task = state.activeTask()
    ScreenScaffold {
        BackHeader(task.title) { state.screen = Screen.PATIENT_BEHAVIOR }
        Text(task.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (task.widgetConfig != null) {
            Text(
                "${task.taskType.label} · Medición: ${task.widgetConfig.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionLabel("Historial de registros")
            WidgetTimeline(task)
            Spacer(Modifier.height(24.dp))
            return@ScreenScaffold
        }
        val measureLabel = if (task.taskType == TaskType.REGISTRO_AUTONOMO) "${task.dimension.label} (${task.dimension.unit})" else task.metricUnit?.label.orEmpty()
        Text(
            "${task.taskType.label} · Medición: $measureLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Una tarea recurrente acumula varios registros en el tiempo; este es el único lugar
        // donde el paciente puede revisarlos todos, en vez de solo ver el último al guardarlo.
        SectionLabel("Historial de registros")
        if (task.entries.isEmpty()) {
            Text(
                "Aún no hay registros para esta tarea.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            task.entries.reversed().forEach { entry -> TaskEntryRow(entry, task) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientTaskScreen(state: PrototypeState) {
    val t = state.activeTask()
    ScreenScaffold {
        BackHeader("Mis tareas") { state.screen = Screen.PATIENT_HOME }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Indicaciones del terapeuta", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PhasePill(t.phase)
                }
                Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(t.instructions, style = MaterialTheme.typography.bodyMedium)
                state.taskError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                val measureLabel = when {
                    t.widgetConfig != null -> t.widgetConfig.name + (t.widgetConfig.unit?.let { " ($it)" } ?: "")
                    t.taskType == TaskType.REGISTRO_AUTONOMO -> "${t.dimension.label} (${t.dimension.unit})"
                    else -> t.metricUnit?.label.orEmpty()
                }
                Text(
                    "${t.taskType.label} · Medición: $measureLabel" + (t.dueDate?.let { " · Vence: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                t.therapeuticGoal?.let {
                    Text("Objetivo: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (t.widgetConfig != null) {
            Text(t.widgetConfig.description ?: "Completa el registro para esta actividad.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WidgetEntryForm(state, t.widgetConfig)
                }
            }
            OutlinedTextField(
                state.taskNote,
                { state.taskNote = it },
                Modifier.fillMaxWidth(),
                label = { Text("Contexto o nota para tu terapeuta (opcional)") },
                minLines = 2,
            )
            DateField(state)
            TimeField(state)
            Button(onClick = { state.saveWidgetEntry() }, Modifier.fillMaxWidth().height(50.dp)) { Text("Guardar mi registro") }
            Text(
                "Se marcará la actividad como completada y se enviará a tu línea de tiempo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        } else {
            val hint = if (t.taskType == TaskType.REGISTRO_AUTONOMO) t.dimension.hint else when (t.metricUnit) {
                MetricUnit.SOLO_COMPLETADO -> "Marca esta actividad como completada."
                MetricUnit.MINUTOS -> "¿Cuántos minutos dedicaste?"
                MetricUnit.REPETICIONES -> "¿Cuántas repeticiones hiciste?"
                MetricUnit.ESCALA_1_5 -> "¿Cómo te fue? Escala de 1 a 5"
                null -> ""
            }
            Text(hint, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Registro autónomo mide la conducta (dimensión); el resto usa su propia unidad_metrica.
                    if (t.taskType == TaskType.REGISTRO_AUTONOMO) {
                        when (t.dimension) {
                            Dimension.FRECUENCIA -> FrequencyObservationFields(state)
                            Dimension.DURACION -> MinutesField(state)
                            Dimension.INTENSIDAD -> ScaleSlider(state, 0f, 10f, 9, "0 · nada", "10 · máximo")
                        }
                    } else {
                        when (t.metricUnit) {
                            MetricUnit.SOLO_COMPLETADO -> Text(
                                "Esta actividad se marca como completada al guardar, sin valor numérico.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            MetricUnit.MINUTOS -> MinutesField(state)
                            MetricUnit.REPETICIONES -> Stepper(state.taskInputValue.toInt()) {
                                state.taskInputValue = it.coerceAtLeast(0).toDouble()
                            }
                            MetricUnit.ESCALA_1_5 -> ScaleSlider(state, 1f, 5f, 3, "1 · bajo", "5 · alto")
                            null -> {}
                        }
                    }
                }
            }
            OutlinedTextField(
                state.taskNote,
                { state.taskNote = it },
                Modifier.fillMaxWidth(),
                label = { Text("Contexto o nota para tu terapeuta (opcional)") },
                minLines = 2,
            )
            DateField(state)
            Button(onClick = { state.saveTask(state.taskEntryDate) }, Modifier.fillMaxWidth().height(50.dp)) { Text("Guardar mi registro") }
            Text(
                "Se marcará la tarea como completada y se enviará a tu gráfica de progreso.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = { onChange(value - 1) },
            modifier = Modifier.size(52.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("−", fontSize = 22.sp) }
        Text(
            "$value",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 88.dp),
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.size(52.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text("+", fontSize = 22.sp) }
    }
}

@Composable
private fun DateField(state: PrototypeState) {
    OutlinedTextField(
        value = "${state.taskEntryDate.dayOfMonth.toString().padStart(2, '0')}/${state.taskEntryDate.monthNumber.toString().padStart(2, '0')}/${state.taskEntryDate.year}",
        onValueChange = { value ->
            val cleaned = value.filter { it.isDigit() || it == '/' }
            val parts = cleaned.split('/').filter { it.isNotBlank() }
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull() ?: state.taskEntryDate.dayOfMonth
                val month = parts[1].toIntOrNull() ?: state.taskEntryDate.monthNumber
                val year = parts[2].toIntOrNull() ?: state.taskEntryDate.year
                runCatching { state.taskEntryDate = LocalDate(year, month, day) }
            }
        },
        Modifier.fillMaxWidth(),
        label = { Text("Fecha de registro") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun MinutesField(state: PrototypeState) {
    val txt = remember { mutableStateOf(if (state.taskInputValue > 0) fmt(state.taskInputValue) else "") }
    OutlinedTextField(
        txt.value,
        { v ->
            val digits = v.filter { it.isDigit() }.take(4)
            txt.value = digits
            state.taskInputValue = digits.toDoubleOrNull() ?: 0.0
        },
        Modifier.fillMaxWidth(),
        label = { Text("Minutos") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun TimeField(state: PrototypeState) {
    OutlinedTextField(
        value = "${state.taskEntryTime.hour.toString().padStart(2, '0')}:${state.taskEntryTime.minute.toString().padStart(2, '0')}",
        onValueChange = { value ->
            val parts = value.filter { it.isDigit() || it == ':' }.split(':').filter { it.isNotBlank() }
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: state.taskEntryTime.hour
                val minute = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: state.taskEntryTime.minute
                runCatching { state.taskEntryTime = LocalTime(hour, minute) }
            }
        },
        Modifier.fillMaxWidth(),
        label = { Text("Hora de registro") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

// Formulario dinámico según el WidgetValueKind del método de medición elegido por el terapeuta.
@Composable
private fun WidgetEntryForm(state: PrototypeState, widget: WidgetConfig) {
    Text(
        measurementExample(widget),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (widget.valueKind) {
        WidgetValueKind.NUMERIC -> {
            val min = (widget.minValue ?: 0.0).toFloat()
            val max = (widget.maxValue ?: 10.0).toFloat()
            Text(
                "${fmt(state.draftWidgetNumericValue)}${widget.unit?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (widget.minValue != null && widget.maxValue != null) {
                Text(
                    "${fmt(widget.minValue)} = mínimo · ${fmt((widget.minValue + widget.maxValue) / 2)} = punto medio · ${fmt(widget.maxValue)} = máximo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = state.draftWidgetNumericValue.toFloat().coerceIn(min, max),
                onValueChange = { state.draftWidgetNumericValue = it.toDouble().toInt().toDouble() },
                valueRange = min..max,
                steps = ((max - min).toInt() - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(min.toDouble()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(max.toDouble()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WidgetValueKind.YES_NO -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip(state.draftWidgetBoolValue, { state.draftWidgetBoolValue = true }, "Sí")
                TabChip(!state.draftWidgetBoolValue, { state.draftWidgetBoolValue = false }, "No")
            }
        }
        WidgetValueKind.TEXT -> {
            OutlinedTextField(
                state.draftWidgetTextValue,
                { state.draftWidgetTextValue = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("Escribe tu observación…") },
                minLines = 3,
            )
        }
        WidgetValueKind.MULTI_SELECT -> {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                widget.options.forEach { option ->
                    val selected = state.draftWidgetSelectedOptions.contains(option)
                    TabChip(selected, { state.toggleWidgetOption(option) }, option)
                }
            }
        }
    }
}

private fun spanishWeekday(date: LocalDate): String = when (date.dayOfWeek.name) {
    "MONDAY" -> "lunes"
    "TUESDAY" -> "martes"
    "WEDNESDAY" -> "miércoles"
    "THURSDAY" -> "jueves"
    "FRIDAY" -> "viernes"
    "SATURDAY" -> "sábado"
    "SUNDAY" -> "domingo"
    else -> "día"
}

@Composable
private fun CalendarField(value: String, placeholder: String, onDateSelected: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(value.ifBlank { placeholder }, modifier = Modifier.fillMaxWidth())
    }
    if (open) {
        val picker = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                        onDateSelected("${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}")
                    }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state = picker) }
    }
}

private fun expectedWidgetDirection(widget: WidgetConfig): String = when (widget.id) {
    "w-escala", "w-frecuencia", "w-duracion" -> "reducción"
    "w-cumplimiento", "cw-control-emocional-demo" -> "aumento"
    else -> "observar y comparar"
}

private fun measurementExample(widget: WidgetConfig): String = when (widget.id) {
    "w-frecuencia" -> "Ejemplo: hoy ocurrió 5 veces. Registra el total del día."
    "w-escala" -> "Ejemplo: 1 = nada de ansiedad, 5 = moderada, 10 = máxima."
    "w-duracion" -> "Ejemplo: la crisis duró 20 minutos."
    "w-cumplimiento" -> "Ejemplo: realizaste la estrategia en el 80% de las ocasiones."
    "w-si-no" -> "Ejemplo: selecciona Sí si la conducta ocurrió hoy."
    "w-seleccion" -> "Ejemplo: selecciona todas las emociones presentes, no solo una."
    else -> widget.description ?: "Completa este registro con lo que observaste hoy."
}

// Muestra en orden cronológico los registros de una actividad con widget configurable: sirve
// tanto para las que producen un valor numérico como para las cualitativas (texto, etc.).
@Composable
private fun WidgetTimeline(task: TrackTask) {
    if (task.widgetEntries.isEmpty()) {
        Text(
            "Aún no hay registros para esta actividad.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    task.widgetEntries.sortedByDescending { it.date }.forEach { entry ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${entry.date.dayOfMonth.toString().padStart(2, '0')}/${entry.date.monthNumber.toString().padStart(2, '0')}/${entry.date.year} · " +
                        "${entry.time.hour.toString().padStart(2, '0')}:${entry.time.minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(widgetValueSummary(entry.value), style = MaterialTheme.typography.bodyMedium)
                entry.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun widgetValueSummary(value: WidgetValue): String = when (value) {
    is WidgetValue.Numeric -> fmt(value.value) + (value.observationMinutes?.let { " (en $it min)" } ?: "")
    is WidgetValue.YesNo -> if (value.value) "Sí" else "No"
    is WidgetValue.Text -> value.value
    is WidgetValue.MultiSelect -> value.values.joinToString(", ").ifBlank { "Sin selección" }
}

@Composable
private fun FrequencyObservationFields(state: PrototypeState) {
    Stepper(state.taskInputValue.toInt()) {
        state.taskInputValue = it.coerceAtLeast(0).toDouble()
    }
    val minutes = remember { mutableStateOf(if (state.taskObservationMinutes > 0) state.taskObservationMinutes.toString() else "") }
    OutlinedTextField(
        value = minutes.value,
        onValueChange = { value ->
            val digits = value.filter { it.isDigit() }.take(4)
            minutes.value = digits
            state.taskObservationMinutes = digits.toIntOrNull() ?: 0
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Minutos observados") },
        supportingText = { Text("Permite comparar episodios/minuto entre sesiones.") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun ScaleSlider(state: PrototypeState, min: Float, max: Float, steps: Int, minLabel: String, maxLabel: String) {
    Text(
        "${state.taskInputValue.toInt()}",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Slider(
        value = state.taskInputValue.toFloat().coerceIn(min, max),
        onValueChange = { state.taskInputValue = it.toDouble() },
        valueRange = min..max,
        steps = steps,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(minLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(maxLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PatientDoneScreen(state: PrototypeState) {
    val p = state.me()
    val t = state.activeTask()
    val entry = t.entries.last()
    ScreenScaffold {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SuccessBadge() }
        Text(
            "Registro guardado",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            "Gracias, ${p.firstName}. Tu terapeuta ya puede verlo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                val summary = if (t.taskType != TaskType.REGISTRO_AUTONOMO && t.metricUnit == MetricUnit.SOLO_COMPLETADO) {
                    "Completada"
                } else {
                    "${fmt(entry.value)} ${taskUnitLabel(t)}"
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        "“${entry.note}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        if (t.feedsSeries) {
            ChartCard(p)
            Text(
                "El último punto (destacado) es el que acabas de registrar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = { state.screen = Screen.PATIENT_HOME }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Volver a mis tareas")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientProgressScreen(state: PrototypeState) {
    val p = state.me()
    val behaviors = state.behaviorsFor(p.id)
    val adaptive = behaviors.filter { it.type == BehaviorType.ADAPTATIVA }
    val maladaptive = behaviors.filter { it.type == BehaviorType.DESADAPTATIVA }
    ScreenScaffold {
        Text("Mi progreso", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (behaviors.isEmpty()) {
            Text(
                "Tu terapeuta aún no ha creado conductas para tu caso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (adaptive.isNotEmpty()) {
                SectionLabel("Conductas adaptativas")
                adaptive.forEach { b -> ChartCard(state.progressForBehavior(b)) }
            }
            if (maladaptive.isNotEmpty()) {
                SectionLabel("Conductas desadaptativas")
                maladaptive.forEach { b -> ChartCard(state.progressForBehavior(b)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientProfileScreen(state: PrototypeState) {
    val p = state.me()
    ScreenScaffold {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(p, 64.dp)
            Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                p.program,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Terapeuta", "José E. Patiño")
                InfoRow("Condición en seguimiento", p.condition)
                InfoRow("Fase actual", p.phase.label)
                InfoRow("Autenticación", "2FA activada ✓")
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Text(
                "Tus datos de conducta son información clínica sensible. Solo tú y tu terapeuta asignado pueden verlos " +
                    "(Ley 1581/2012).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
        OutlinedButton(onClick = { state.logout() }, Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagnosisRow(dx: Cie10Diagnosis, onRemove: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dx.code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(dx.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRemove) { Text("Quitar") }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

/* ----------------------------- terapeuta ----------------------------- */

@Composable
private fun TherapistHomeScreen(state: PrototypeState, dark: Boolean, onToggleTheme: () -> Unit) {
    val patients = state.patients
    val totalPending = patients.sumOf { p -> p.tasks.count { it.status == TaskStatus.PENDIENTE } }
    ScreenScaffold {
        HomeHeader(
            greeting = "Hola 👋",
            name = "José E. Patiño",
            dark = dark,
            onToggleTheme = onToggleTheme,
            onLogout = { state.logout() },
            avatar = {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Navy),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("JP", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
        )
        Text(
            "${patients.size} pacientes activos · $totalPending tareas pendientes de registro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionLabel("Mis pacientes")
        patients.forEach { p -> PatientRow(p) { state.openPatient(p.id) } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TherapistProfileScreen(state: PrototypeState) {
    ScreenScaffold {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(64.dp * 0.33f)).background(Brand.Navy),
                contentAlignment = Alignment.Center,
            ) {
                Text("JP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Text("José E. Patiño", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Terapeuta · Universidad de San Buenaventura",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Pacientes activos", "${state.patients.size}")
                InfoRow("Autenticación", "2FA activada ✓")
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Text(
                "Los datos clínicos de tus pacientes son información sensible. Solo tú y cada paciente asignado " +
                    "pueden verlos (Ley 1581/2012).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
        OutlinedButton(onClick = { state.logout() }, Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientRow(p: Patient, onClick: () -> Unit) {
    val pending = p.tasks.count { it.status == TaskStatus.PENDIENTE }
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(p)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${p.condition} · ${p.behavior}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PhasePill(p.phase)
                Text(
                    "$pending pend.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ----------------------------- administrador ----------------------------- */

@Composable
private fun AdminHomeScreen(state: PrototypeState, dark: Boolean, onToggleTheme: () -> Unit) {
    val patients = state.patients
    val totalPending = patients.sumOf { p -> p.tasks.count { it.status == TaskStatus.PENDIENTE } }
    ScreenScaffold {
        HomeHeader(
            greeting = "Hola 👋",
            name = "Administrador",
            dark = dark,
            onToggleTheme = onToggleTheme,
            onLogout = { state.logout() },
            avatar = {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Orange),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AD", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
        )
        Text(
            "Panel del sistema · ${state.roleProfiles.size} perfiles configurados · $totalPending tareas pendientes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${patients.size}", "pacientes activos", MaterialTheme.colorScheme.primary)
            StatTile("${state.roleProfiles.size}", "perfiles del sistema", MaterialTheme.colorScheme.onSurface)
        }
        SectionLabel("Perfiles y permisos")
        state.roleProfiles.forEach { profile -> RoleProfileCard(profile) }
        SectionLabel("Pacientes en el sistema")
        // El administrador hereda la ficha del terapeuta: mismo permiso de lectura sobre el caso.
        patients.forEach { p -> PatientRow(p) { state.openPatient(p.id) } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoleProfileCard(profile: RoleProfile) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(profile.badgeColor)),
                contentAlignment = Alignment.Center,
            ) {
                Text(profile.displayName.first().uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Pill("${profile.tabs.size} tabs", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TherapistPatientScreen(state: PrototypeState) {
    val p = state.activePatient()
    ScreenScaffold {
        BackHeader("Pacientes") { state.screen = state.currentProfile?.homeScreen ?: Screen.THERAPIST_HOME }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(p, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${p.age} años · ${p.program}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhasePill(p.phase)
            Pill(p.condition, MaterialTheme.colorScheme.onSurfaceVariant)
            p.diagnoses.forEach { dx -> Pill(dx.code, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Los datos administrativos del paciente (diagnóstico CIE-10, etc.) se gestionan en su
        // propia pantalla: aquí solo se ve un resumen (los códigos como pill arriba) para que
        // esta vista se mantenga enfocada en las conductas del caso.
        OutlinedButton(onClick = { state.screen = Screen.THERAPIST_PATIENT_INFO }, Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (p.diagnoses.isEmpty()) "Asignar diagnóstico (CIE-10)" else "Gestionar datos del paciente")
                NavGlyph("forward", selected = false)
            }
        }
        SectionLabel("Conductas del caso")
        if (p.behaviors.isEmpty()) {
            Text(
                "Aún no hay conductas definidas para este caso. Usa el botón + para crear la primera.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            p.behaviors.forEach { behavior -> BehaviorRow(behavior) { state.openBehaviorDetail(behavior.id) } }
        }
        Spacer(Modifier.height(72.dp)) // deja aire para que el FAB no tape la última tarjeta
    }
}

@Composable
private fun BehaviorRow(behavior: Behavior, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(behavior.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(behavior.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PhasePill(if (behavior.baselineOpen) Phase.LINEA_BASE else Phase.INTERVENCION)
            }
            Text(
                "${behavior.tasks.size} ${if (behavior.tasks.size == 1) "actividad" else "actividades"} · ${behavior.dimension.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TherapistPatientInfoScreen(state: PrototypeState) {
    val p = state.activePatient()
    ScreenScaffold {
        BackHeader("Datos del paciente") { state.screen = Screen.THERAPIST_PATIENT }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(p, 48.dp)
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${p.age} años · ${p.program}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Condición en seguimiento", p.condition)
                InfoRow("Fase actual", p.phase.label)
                InfoRow("Conductas activas", "${p.behaviors.size}")
            }
        }
        // Diagnóstico por CIE-10: nulo por defecto (lista vacía), el terapeuta lo asigna cuando
        // corresponde y puede agregar más de un código si hay comorbilidad.
        SectionLabel("Diagnóstico (CIE-10)")
        if (p.diagnoses.isEmpty()) {
            Text(
                "Sin diagnóstico asignado todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            p.diagnoses.forEach { dx -> DiagnosisRow(dx) { state.removeDiagnosis(p.id, dx.code) } }
        }
        DropdownField(
            label = "Agregar diagnóstico",
            selectedLabel = null,
            placeholder = if (p.diagnoses.isEmpty()) "Selecciona un código CIE-10" else "Agregar otro código",
            options = state.cie10Catalog.map { it.code to "${it.code} · ${it.label}" } + ("__custom__" to "+ Escribir otro código"),
            onSelect = { code ->
                if (code == "__custom__") {
                    state.showNewDiagnosisForm = true
                } else {
                    val entry = state.cie10Catalog.first { it.code == code }
                    state.assignDiagnosis(p.id, entry.code, entry.label)
                }
            },
        )
        if (state.showNewDiagnosisForm) {
            OutlinedTextField(
                state.draftDiagnosisCode, { state.draftDiagnosisCode = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Código (ej.: F41.1)") }, singleLine = true,
            )
            OutlinedTextField(
                state.draftDiagnosisLabel, { state.draftDiagnosisLabel = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Descripción del diagnóstico") }, singleLine = true,
            )
            Button(onClick = {
                if (state.assignDiagnosis(p.id, state.draftDiagnosisCode, state.draftDiagnosisLabel)) {
                    state.showNewDiagnosisForm = false
                    state.draftDiagnosisCode = ""
                    state.draftDiagnosisLabel = ""
                }
            }) { Text("Guardar diagnóstico") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Selector de pestañas tipo segmented control: un riel en píldora con el segmento activo
 * resaltado por relleno + borde de acento, justo encima del panel de contenido.
 *
 * Se abandonó el intento anterior de "pestaña-carpeta fundida con el panel + vidrio esmerilado":
 * la costura entre pestaña y panel era frágil (ya rompió una vez, dejando las pestañas
 * invisibles) y el efecto de vidrio, sobre los fondos planos de esta app, no se leía bien. Este
 * patrón es más simple, más robusto y dice con claridad cuál pestaña está activa.
 */
@Composable
private fun <T> TabbedPanel(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val segmentShape = RoundedCornerShape(50)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(segmentShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { (value, label) ->
                val isSelected = selected == value
                Box(
                    Modifier
                        .weight(1f)
                        .clip(segmentShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        // El color del borde —no el relleno— es lo que marca cuál está
                        // seleccionada, siguiendo el mismo lenguaje del resto de la app.
                        .border(
                            if (isSelected) 1.5.dp else 0.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            segmentShape,
                        )
                        .clickable { onSelect(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun TherapistBehaviorScreen(state: PrototypeState) {
    val behavior = state.activeBehavior()
    val patient = state.activePatient()
    ScreenScaffold {
        BackHeader(behavior.name) { state.screen = Screen.THERAPIST_PATIENT }
        Text(
            "Paciente: ${patient.name}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhasePill(if (behavior.baselineOpen) Phase.LINEA_BASE else Phase.INTERVENCION)
            Pill(behavior.type.label, MaterialTheme.colorScheme.onSurfaceVariant)
            Pill(behavior.dimension.label, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "${behavior.observationCount} observaciones de línea base · " + if (behavior.baselineOpen) {
                "línea base abierta: las nuevas actividades registrarán observaciones iniciales."
            } else {
                "línea base cerrada el ${behavior.baselineClosedOn}: los datos quedan como referencia fija."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (behavior.baselineOpen) {
            OutlinedButton(onClick = { state.closeBaseline(behavior.id) }, Modifier.fillMaxWidth()) {
                Text("Cerrar línea base")
            }
        }
        // Pestañas: "Actividades" (donde vive el botón + de crear) y "Análisis gráfico" (la
        // evolución de la conducta) separan gestión de datos y su lectura visual, en vez de
        // apilar todo en una sola pantalla larga. El selector y su contenido comparten una
        // sola caja redondeada, como una unión continua entre el botón y el panel.
        TabbedPanel(
            options = listOf(BehaviorTab.ACTIVIDADES to "Actividades", BehaviorTab.ANALISIS to "Análisis gráfico"),
            selected = state.behaviorDetailTab,
            onSelect = { state.behaviorDetailTab = it },
        ) {
            when (state.behaviorDetailTab) {
                BehaviorTab.ACTIVIDADES -> {
                    if (behavior.tasks.isEmpty()) {
                        Text(
                            "Todavía no hay actividades para esta conducta. Usa el botón + para crear la primera.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        behavior.tasks.forEach { task -> TaskCard(task, onClick = { state.openTaskDetail(task.id) }, pendingCta = "Ver detalle →") }
                    }
                }
                BehaviorTab.ANALISIS -> {
                    // La gráfica es de la conducta (no de una actividad puntual): en caso único
                    // cada variable evaluada —frecuencia, duración, intensidad…— es una serie
                    // propia, así que cuando la conducta mide más de una se ofrecen como
                    // pestañas intercambiables. Las actividades con widget configurable se
                    // muestran como opciones adicionales, cada una con su propia gráfica o timeline.
                    val dims = behavior.dimensions
                    val widgetActivities = behavior.widgetActivities
                    var selectedDimension by remember(behavior.id) { mutableStateOf(dims.firstOrNull()) }
                    // Si la conducta tiene actividades configurables, mostrar la primera gráfica
                    // de tarea al entrar; antes quedaba seleccionada siempre la dimensión clásica
                    // y parecía que la tarea no tenía datos.
                    var selectedWidgetTaskId by remember(behavior.id) { mutableStateOf(widgetActivities.firstOrNull()?.id) }
                    if (dims.size > 1 || widgetActivities.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dims.forEach { d ->
                                TabChip(selectedWidgetTaskId == null && selectedDimension == d, { selectedDimension = d; selectedWidgetTaskId = null }, d.label)
                            }
                            widgetActivities.forEach { w ->
                                TabChip(selectedWidgetTaskId == w.id, { selectedWidgetTaskId = w.id }, w.title)
                            }
                        }
                    }
                    val activeWidgetTask = widgetActivities.firstOrNull { it.id == selectedWidgetTaskId }
                    when {
                        activeWidgetTask != null -> {
                            val widget = activeWidgetTask.widgetConfig!!
                            if (widget.valueKind == WidgetValueKind.NUMERIC) {
                                WidgetChartCard(activeWidgetTask, widget)
                            } else {
                                SectionLabel("Línea de tiempo · ${widget.name}")
                                WidgetTimeline(activeWidgetTask)
                            }
                        }
                        selectedDimension != null -> ChartCard(behavior, selectedDimension!!)
                        else -> Text(
                            "Todavía no hay actividades de medición para esta conducta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(72.dp)) // deja aire para que el FAB no tape el panel
    }
}

@Composable
private fun TherapistTaskDetailScreen(state: PrototypeState) {
    val task = state.activeTask()
    val behavior = state.activeBehavior()
    ScreenScaffold {
        BackHeader(task.title) { state.screen = Screen.THERAPIST_BEHAVIOR }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("${task.taskType.label} · ${behavior.name}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusPill(task.status)
        }
        Text(task.instructions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val measureLabel = when {
            task.widgetConfig != null -> task.widgetConfig.name + (task.widgetConfig.unit?.let { " ($it)" } ?: "")
            task.taskType == TaskType.REGISTRO_AUTONOMO -> "${task.dimension.label} (${task.dimension.unit})"
            else -> task.metricUnit?.label.orEmpty()
        }
        Text(
            "Fase: ${task.phase.label} · Medición: $measureLabel" + (task.dueDate?.let { " · Vence: $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        task.therapeuticGoal?.let {
            Text("Objetivo terapéutico: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (task.feedsSeries) {
            // La gráfica vive a nivel de conducta (ver TherapistBehaviorScreen): esta actividad
            // solo aporta observaciones a la dimensión "${task.dimension.label}" de esa serie.
            Text(
                "Esta actividad alimenta la gráfica de \"${behavior.name}\" en su dimensión ${task.dimension.label}; consulta la evolución completa en el detalle de la conducta.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionLabel("Historial de registros")
        if (task.widgetConfig != null) {
            WidgetTimeline(task)
        } else if (task.entries.isEmpty()) {
            Text(
                "Aún no hay registros para esta actividad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            task.entries.reversed().forEach { entry -> TaskEntryRow(entry, task) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TaskEntryRow(entry: TaskEntry, task: TrackTask) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${entry.date.dayOfMonth}/${entry.date.monthNumber}/${entry.date.year}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val label = if (task.taskType != TaskType.REGISTRO_AUTONOMO && task.metricUnit == MetricUnit.SOLO_COMPLETADO) {
                "Completada"
            } else if (task.taskType == TaskType.REGISTRO_AUTONOMO && task.dimension == Dimension.FRECUENCIA && entry.observationMinutes != null) {
                "${fmt(entry.value)} episodios en ${entry.observationMinutes} min (${fmt(entry.value / entry.observationMinutes)} ep/min)"
            } else {
                "${fmt(entry.value)} ${taskUnitLabel(task)}"
            }
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TherapistBaselineSetupScreen(state: PrototypeState) {
    val behavior = state.activeBehavior()
    ScreenScaffold {
        BackHeader("Línea base") { state.screen = Screen.THERAPIST_BEHAVIOR }
        Text(
            "Define qué variables vas a medir durante la línea base de \"${behavior.name}\" antes de registrar observaciones.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Dimensiones a registrar")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dimension.entries.forEach { dimension ->
                    TabChip(
                        selected = state.draftBaselineDimensions.contains(dimension),
                        onClick = {
                            if (state.draftBaselineDimensions.contains(dimension)) {
                                state.draftBaselineDimensions.remove(dimension)
                            } else {
                                state.draftBaselineDimensions.add(dimension)
                            }
                        },
                        label = dimension.label,
                    )
                }
            }
        }
        Button(onClick = { state.configureBaseline() }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Guardar línea base")
        }
        TextButton(onClick = { state.screen = Screen.THERAPIST_BEHAVIOR }, Modifier.fillMaxWidth()) {
            Text("Configurar después")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TherapistNewBehaviorScreen(state: PrototypeState) {
    ScreenScaffold {
        BackHeader("Nueva conducta") { state.screen = Screen.THERAPIST_PATIENT }
        Text(
            "Primero define la conducta del caso. Después podrás revisar su línea base y asignar tareas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            FieldLabel("Consultante")
            // Fijo por contexto: la conducta se crea siempre dentro de la historia de un paciente, no se puede cambiar aquí.
            val patient = state.patient(state.draftPatientId)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(patient, 36.dp)
                Column {
                    Text(patient.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(patient.condition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column {
            FieldLabel("Nombre de la conducta")
            OutlinedTextField(state.draftBehaviorName, { state.draftBehaviorName = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ej.: Participar en clase a pesar de la ansiedad") }, singleLine = true)
        }
        Column {
            FieldLabel("Definición operacional")
            OutlinedTextField(state.draftDefinition, { state.draftDefinition = it }, Modifier.fillMaxWidth(), placeholder = { Text("Describe cuándo se considera observada: observable y medible, sin interpretación") }, minLines = 3)
        }
        Column {
            FieldLabel("Naturaleza")
            val s = LocalUsBrainSemantic.current
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BehaviorType.entries.forEach { type ->
                    val tint = if (type == BehaviorType.ADAPTATIVA) s.success else MaterialTheme.colorScheme.error
                    TabChip(
                        selected = state.draftBehaviorType == type,
                        onClick = {
                            state.draftBehaviorType = type
                            if (type == BehaviorType.ADAPTATIVA) {
                                state.draftBehavioralFunctionId = NO_APLICA_FUNCTION_ID
                                state.showNewFunctionForm = false
                                state.draftReplacementBehaviorId = null
                            }
                        },
                        label = type.label,
                        accent = tint,
                    )
                }
            }
        }
        if (state.draftBehaviorType == BehaviorType.DESADAPTATIVA) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Función conductual")
                // Catálogo editable como la categoría: si el análisis funcional del caso no
                // encaja en el catálogo semilla, "+ Nueva función" la registra al vuelo.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.behavioralFunctions.forEach { fn ->
                        TabChip(
                            state.draftBehavioralFunctionId == fn.id,
                            { state.draftBehavioralFunctionId = fn.id; state.showNewFunctionForm = false },
                            fn.label,
                        )
                    }
                    TabChip(state.showNewFunctionForm, { state.showNewFunctionForm = !state.showNewFunctionForm }, "+ Nueva función")
                }
                if (state.showNewFunctionForm) {
                    OutlinedTextField(
                        state.draftNewFunctionLabel, { state.draftNewFunctionLabel = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text("Nombre de la función (ej.: Autorregulación)") }, singleLine = true,
                    )
                    Button(onClick = {
                        val id = state.createBehavioralFunction(state.draftNewFunctionLabel)
                        if (id != null) {
                            state.draftBehavioralFunctionId = id
                            state.showNewFunctionForm = false
                            state.draftNewFunctionLabel = ""
                        }
                    }) { Text("Guardar función") }
                }
            }
            val replacementOptions = state.adaptiveBehaviorsFor(state.draftPatientId)
            DropdownField(
                label = "Conducta que reemplaza",
                selectedLabel = replacementOptions.firstOrNull { it.id == state.draftReplacementBehaviorId }?.name,
                placeholder = if (replacementOptions.isEmpty()) "Aún no hay conductas adaptativas para este paciente" else "Ninguna por ahora",
                options = listOf("" to "Ninguna por ahora") + replacementOptions.map { it.id to it.name },
                onSelect = { id -> state.draftReplacementBehaviorId = id.ifBlank { null } },
            )
        }
        state.behaviorError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { state.saveBehavior() }, Modifier.fillMaxWidth().height(50.dp)) { Text("Guardar conducta") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TherapistNewWidgetScreen(state: PrototypeState) {
    ScreenScaffold {
        BackHeader("Medición personalizada") { state.screen = Screen.THERAPIST_NEW_TASK }
        Text(
            "Define tu propia variable: nombre, tipo de respuesta, rango, unidad y descripción.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            FieldLabel("Nombre de la variable")
            OutlinedTextField(state.draftCustomWidgetName, { state.draftCustomWidgetName = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ej.: Nivel de rabietas") }, singleLine = true)
        }
        Column {
            FieldLabel("Tipo de respuesta")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetValueKind.entries.forEach { kind ->
                    TabChip(state.draftCustomWidgetValueKind == kind, { state.draftCustomWidgetValueKind = kind }, kind.label)
                }
            }
        }
        if (state.draftCustomWidgetValueKind == WidgetValueKind.NUMERIC) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("Valor mínimo")
                    OutlinedTextField(state.draftCustomWidgetMin, { state.draftCustomWidgetMin = it }, Modifier.fillMaxWidth(), placeholder = { Text("0") }, singleLine = true)
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("Valor máximo")
                    OutlinedTextField(state.draftCustomWidgetMax, { state.draftCustomWidgetMax = it }, Modifier.fillMaxWidth(), placeholder = { Text("10") }, singleLine = true)
                }
            }
            Column {
                FieldLabel("Unidad de medida")
                OutlinedTextField(state.draftCustomWidgetUnit, { state.draftCustomWidgetUnit = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ej.: episodios, minutos, /10") }, singleLine = true)
            }
        }
        if (state.draftCustomWidgetValueKind == WidgetValueKind.MULTI_SELECT) {
            Column {
                FieldLabel("Opciones (separadas por coma)")
                OutlinedTextField(state.draftCustomWidgetOptionsText, { state.draftCustomWidgetOptionsText = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ej.: Ansiedad, Tristeza, Irritabilidad") }, minLines = 2)
            }
        }
        Column {
            FieldLabel("Descripción")
            OutlinedTextField(state.draftCustomWidgetDescription, { state.draftCustomWidgetDescription = it }, Modifier.fillMaxWidth(), placeholder = { Text("Para qué sirve y cómo interpretarla…") }, minLines = 2)
        }
        state.customWidgetError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = { state.saveCustomWidget() }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Guardar medición")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TherapistNewTaskScreen(state: PrototypeState) {
    ScreenScaffold {
        BackHeader("Nueva actividad") { state.screen = Screen.THERAPIST_BEHAVIOR }
        Text(
            "Se agrega sobre la conducta actual; puedes crear las actividades que necesites.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            FieldLabel("Conducta")
            // Fijo por contexto: la actividad se crea siempre dentro de una conducta, no se puede cambiar aquí.
            val patient = state.patient(state.draftPatientId)
            val behavior = state.behavior(state.draftBehaviorId!!)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(patient, 36.dp)
                Column {
                    Text(behavior.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("${patient.name} · ${behavior.type.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column {
            FieldLabel("Fase de la actividad")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Phase.entries.forEach { phase ->
                    TabChip(
                        selected = state.draftTaskPhase == phase,
                        onClick = {
                            state.draftTaskPhase = phase
                            state.draftReminderActive = phase == Phase.INTERVENCION
                        },
                        label = phase.label,
                    )
                }
            }
        }
        Column {
            FieldLabel("Nombre de la actividad")
            OutlinedTextField(state.draftTaskTitle, { state.draftTaskTitle = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ej.: Registrar cada episodio de rascado") }, singleLine = true)
        }
        Column {
            FieldLabel("Instrucciones")
            OutlinedTextField(
                state.draftInstructions,
                { state.draftInstructions = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("Qué debe hacer o anotar el consultante, y cómo…") },
                minLines = 3,
            )
        }
        Column {
            FieldLabel("Objetivo terapéutico")
            OutlinedTextField(
                state.draftTherapeuticGoal,
                { state.draftTherapeuticGoal = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("Qué se espera lograr con esta actividad…") },
                minLines = 2,
            )
        }
        Column {
            FieldLabel("Método de medición")
            Text("Medición", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (state.draftUseWidgetSystem) {
            Column {
                FieldLabel("Elige o crea la variable a medir")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.widgetCatalog().forEach { widget ->
                        TabChip(state.draftSelectedWidgetId == widget.id, { state.draftSelectedWidgetId = widget.id }, widget.name)
                    }
                }
                OutlinedButton(onClick = { state.startCustomWidget() }, Modifier.fillMaxWidth()) {
                    Text("+ Crear medición personalizada")
                }
                state.draftSelectedWidgetId?.let { state.widget(it) }?.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column {
            FieldLabel("Fecha de inicio")
            CalendarField(state.draftAssignedDate, "Seleccionar fecha de inicio") { state.draftAssignedDate = it }
        }
        Column {
            FieldLabel("Hasta cuándo (opcional)")
            CalendarField(state.draftDueDate, "Sin fecha de cierre") { state.draftDueDate = it }
        }
        Column {
            FieldLabel("Recordatorio")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip(!state.draftReminderActive, { state.draftReminderActive = false }, "Desactivado")
                TabChip(state.draftReminderActive, { state.draftReminderActive = true }, "Activado")
            }
        }
        state.assignError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = { state.assign() }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Guardar actividad")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TherapistAssignedScreen(state: PrototypeState) {
    val p = state.patient(state.draftPatientId)
    val t = p.tasks.last()
    val s = LocalUsBrainSemantic.current
    ScreenScaffold {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SuccessBadge() }
        Text(
            "Tarea asignada",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            "Se agregó a la lista de ${p.name} y recibirá una notificación.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, s.intervention),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Así se verá la tarea en la app del paciente:",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
        TaskCard(t)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionLabel("Resumen de la asignación")
                Text("Conducta: ${p.behavior}", style = MaterialTheme.typography.bodySmall)
                val measure = when {
                    t.widgetConfig != null -> t.widgetConfig.name + (t.widgetConfig.unit?.let { " ($it)" } ?: "")
                    t.taskType == TaskType.REGISTRO_AUTONOMO -> "${t.dimension.label} (${t.dimension.unit})"
                    else -> t.metricUnit?.label.orEmpty()
                }
                Text("Tipo: ${t.taskType.label} · Mide: $measure", style = MaterialTheme.typography.bodySmall)
                val recurrence = if (t.isRecurring) "Recurrente (${t.frequency})" else "Una sola vez"
                Text("Fase: ${t.phase.label} · $recurrence", style = MaterialTheme.typography.bodySmall)
            }
        }
        // Va directo al detalle de la conducta (no solo a la ficha del paciente): ahí es
        // donde vive la actividad recién asignada, en línea con el flujo de drill-down.
        Button(onClick = { state.openBehaviorDetail(t.behaviorId) }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Ver ficha de ${p.firstName}")
        }
        OutlinedButton(onClick = { state.previewAsPatient(p.id) }, Modifier.fillMaxWidth()) {
            Text("Previsualizar como paciente")
        }
        Spacer(Modifier.height(24.dp))
    }
}

