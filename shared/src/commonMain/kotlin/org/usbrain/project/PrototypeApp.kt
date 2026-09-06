@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class,
)

package org.usbrain.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlin.math.roundToInt

@Composable
fun PrototypeApp(state: PrototypeState, dark: Boolean, onToggleTheme: () -> Unit, onReset: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BrandTopBar(state, dark, onToggleTheme, onReset) },
        bottomBar = { if (showsBottomBar(state)) BottomBar(state) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (state.screen) {
                Screen.LOGIN -> LoginScreen(state)
                Screen.TWO_FACTOR -> TwoFactorScreen(state)
                Screen.PATIENT_HOME -> PatientHomeScreen(state)
                Screen.PATIENT_TASK -> PatientTaskScreen(state)
                Screen.PATIENT_DONE -> PatientDoneScreen(state)
                Screen.PATIENT_PROGRESS -> PatientProgressScreen(state)
                Screen.PATIENT_PROFILE -> PatientProfileScreen(state)
                Screen.THERAPIST_HOME -> TherapistHomeScreen(state)
                Screen.THERAPIST_PATIENT -> TherapistPatientScreen(state)
                Screen.THERAPIST_NEW_BEHAVIOR -> TherapistNewBehaviorScreen(state)
                Screen.THERAPIST_NEW_TASK -> TherapistNewTaskScreen(state)
                Screen.THERAPIST_ASSIGNED -> TherapistAssignedScreen(state)
                Screen.THERAPIST_PROGRESS -> TherapistProgressScreen(state)
            }
        }
    }
}

private fun showsBottomBar(state: PrototypeState): Boolean {
    if (state.role == null) return false
    return state.screen !in listOf(
        Screen.LOGIN, Screen.TWO_FACTOR, Screen.PATIENT_TASK, Screen.PATIENT_DONE,
        Screen.THERAPIST_NEW_BEHAVIOR, Screen.THERAPIST_NEW_TASK, Screen.THERAPIST_ASSIGNED,
    )
}

/* ----------------------------- barras ----------------------------- */

@Composable
private fun BrandTopBar(state: PrototypeState, dark: Boolean, onToggleTheme: () -> Unit, onReset: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UsBrainMark(size = 30.dp)
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text("US", color = Brand.Orange, fontWeight = FontWeight.Bold)
                    Text("Brain", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    contextLabel(state.screen),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("Menú") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ir a sesión paciente") },
                        onClick = { state.jumpTo(Role.PATIENT); menu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Ir a sesión terapeuta") },
                        onClick = { state.jumpTo(Role.THERAPIST); menu = false },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (dark) "Tema claro" else "Tema oscuro") },
                        onClick = { onToggleTheme(); menu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Reiniciar demo") },
                        onClick = { onReset(); menu = false },
                    )
                    if (state.role != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Cerrar sesión") },
                            onClick = { state.logout(); menu = false },
                        )
                    }
                }
            }
        }
    }
}

private fun contextLabel(screen: Screen): String = when (screen) {
    Screen.LOGIN, Screen.TWO_FACTOR -> "Acceso"
    Screen.PATIENT_HOME, Screen.PATIENT_TASK, Screen.PATIENT_DONE,
    Screen.PATIENT_PROGRESS, Screen.PATIENT_PROFILE -> "Sesión: paciente"
    else -> "Sesión: terapeuta"
}

@Composable
private fun BottomBar(state: PrototypeState) {
    val items = if (state.role == Role.PATIENT) {
        listOf(
            Triple(Screen.PATIENT_HOME, "Inicio", "home"),
            Triple(Screen.PATIENT_PROGRESS, "Progreso", "chart"),
            Triple(Screen.PATIENT_PROFILE, "Perfil", "person"),
        )
    } else {
        listOf(
            Triple(Screen.THERAPIST_HOME, "Pacientes", "person"),
            Triple(Screen.THERAPIST_PROGRESS, "Progreso", "chart"),
        )
    }
    val current = state.screen
    NavigationBar {
        items.forEach { (target, label, glyph) ->
            val selected = current == target ||
                (target == Screen.PATIENT_HOME && current in listOf(Screen.PATIENT_TASK, Screen.PATIENT_DONE)) ||
                (target == Screen.THERAPIST_HOME && current == Screen.THERAPIST_PATIENT)
            NavigationBarItem(
                selected = selected,
                onClick = { state.screen = target },
                icon = { NavGlyph(glyph, selected) },
                label = { Text(label) },
            )
        }
    }
}

/* ----------------------------- utilidades UI ----------------------------- */

@Composable
private fun ScreenScaffold(content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 4.dp)) {
        // maxLines=1 evita que un pill se "rompa" a dos líneas si el contenedor lo exprime.
        Text(text, color = content, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
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
    Pill(phase.label, c.copy(alpha = 0.16f), c)
}

@Composable
private fun StatusPill(status: TaskStatus) {
    val s = LocalUsBrainSemantic.current
    if (status == TaskStatus.COMPLETADA) {
        Pill("Completada", s.success.copy(alpha = 0.16f), s.success)
    } else {
        Pill("Pendiente", s.warning.copy(alpha = 0.18f), s.warning)
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
    Dimension.FRECUENCIA -> "episodios"
}

@Composable
private fun TaskCard(task: TrackTask, onClick: (() -> Unit)? = null) {
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
                    "Llenar tarea →",
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
private fun TrendChart(behavior: Behavior, modifier: Modifier = Modifier) {
    val s = LocalUsBrainSemantic.current
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val gridColor = s.grid
    val baseC = s.baseline
    val intC = s.intervention
    val baseline = behavior.baselineEntries.map { it.value }
    val intervention = behavior.tasks.filter { it.taskType == TaskType.REGISTRO_AUTONOMO }.flatMap { task -> task.entries.filter { it.phase == Phase.INTERVENCION }.map { it.value } }
    val data = baseline + intervention
    val baselineDays = baseline.size
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        if (data.isEmpty()) return@Canvas
        val n = data.size
        val padL = 26f
        val padR = 8f
        val padT = 10f
        val padB = 8f
        val iw = size.width - padL - padR
        val ih = size.height - padT - padB
        val maxV = ((data.maxOrNull() ?: 1.0) * 1.15).coerceAtLeast(1.0)
        fun xAt(i: Int): Float = padL + if (n <= 1) 0f else i.toFloat() / (n - 1) * iw
        fun yAt(v: Double): Float = padT + ih - (v / maxV).toFloat() * ih
        for (g in 0..4) {
            val gy = padT + ih * g / 4f
            drawLine(gridColor, Offset(padL, gy), Offset(size.width - padR, gy), 1f)
        }
        if (baselineDays in 1 until n) {
            val splitX = (xAt(baselineDays - 1) + xAt(baselineDays)) / 2f
            drawRect(baseC.copy(alpha = 0.14f), Offset(padL, padT), Size(splitX - padL, ih))
            drawLine(
                intC, Offset(splitX, padT), Offset(splitX, padT + ih), 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }
        val path = Path()
        data.forEachIndexed { i, v -> if (i == 0) path.moveTo(xAt(i), yAt(v)) else path.lineTo(xAt(i), yAt(v)) }
        drawPath(
            path,
            Brush.linearGradient(listOf(baseC, intC)),
            style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        data.forEachIndexed { i, v ->
            val last = i == n - 1
            val c = if (i < baselineDays) baseC else intC
            drawCircle(c, if (last) 4.5f else 2.6f, Offset(xAt(i), yAt(v)))
            if (last) drawCircle(surface, 4.5f, Offset(xAt(i), yAt(v)), style = Stroke(2f))
        }
        listOf(0.0, maxV / 2, maxV).forEach { v ->
            val tl = measurer.measure(v.roundToInt().toString(), style = TextStyle(fontSize = 8.sp, color = axisColor))
            drawText(tl, topLeft = Offset(padL - tl.size.width - 4f, yAt(v) - tl.size.height / 2f))
        }
    }
}

@Composable
private fun ChartCard(behavior: Behavior) {
    val s = LocalUsBrainSemantic.current
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
                        "Tendencia · ${behavior.dimension.label} · ${behavior.type.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                PhasePill(if (behavior.baselineOpen) Phase.LINEA_BASE else Phase.INTERVENCION)
            }
            Spacer(Modifier.height(10.dp))
            TrendChart(behavior)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(s.baseline, "Línea base (${behavior.observationCount} obs.)")
                LegendDot(s.intervention, "Intervención")
            }
            Text(
                "Eje X: observaciones · Eje Y: ${axisUnit(behavior.dimension)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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
            if (state.loginRole == Role.PATIENT) "ana.torres@usbbog.edu.co" else "e.patino@usb.edu.co",
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleChoiceCard(Role.PATIENT, state.loginRole == Role.PATIENT, Modifier.weight(1f)) {
                state.loginRole = Role.PATIENT
            }
            RoleChoiceCard(Role.THERAPIST, state.loginRole == Role.THERAPIST, Modifier.weight(1f)) {
                state.loginRole = Role.THERAPIST
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
private fun RoleChoiceCard(role: Role, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val badge = if (role == Role.PATIENT) Brand.Violet else Brand.Navy
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    OutlinedCard(onClick = onClick, modifier = modifier, border = border) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(badge),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (role == Role.PATIENT) "P" else "T", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
                if (role == Role.PATIENT) "Paciente" else "Terapeuta",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (role == Role.PATIENT) {
                    "Consulta y llena las tareas que asigna tu terapeuta"
                } else {
                    "Define conductas y asigna tareas de seguimiento"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        TextButton(onClick = { state.screen = Screen.LOGIN }, modifier = Modifier.align(Alignment.Start)) {
            Text("← Volver")
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("2FA", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
private fun PatientHomeScreen(state: PrototypeState) {
    val p = state.me()
    val caseId = state.caseForPatient(p.id)
    val behaviors = if (caseId != null) state.behaviorsForCase(caseId) else emptyList()
    val done = p.tasks.filter { it.status == TaskStatus.COMPLETADA }
    ScreenScaffold {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("Hoy")
                Text("Hola, ${p.firstName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Avatar(p)
        }
        val pending = behaviors.flatMap { b -> state.tasksForBehavior(b.id).filter { it.status == TaskStatus.PENDIENTE } }
        Text(
            "Tienes ${pending.size} ${if (pending.size == 1) "tarea" else "tareas"} por registrar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${3 + done.size}", "días de racha", MaterialTheme.colorScheme.primary)
            StatTile("${done.size}/${behaviors.sumOf { state.tasksForBehavior(it.id).size }}", "registros esta semana", MaterialTheme.colorScheme.onSurface)
        }
        if (behaviors.isEmpty()) {
            Text(
                "Tu terapeuta aún no ha creado conductas para tu caso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            behaviors.forEach { b ->
                val behaviorPending = state.tasksForBehavior(b.id).filter { it.status == TaskStatus.PENDIENTE }
                val behaviorDone = state.tasksForBehavior(b.id).filter { it.status == TaskStatus.COMPLETADA }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("${b.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(if (b.type == BehaviorType.ADAPTATIVA) "Adaptativa" else "Desadaptativa", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        Pill(b.dimension.label, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (behaviorPending.isNotEmpty()) {
                        behaviorPending.forEach { t -> TaskCard(t) { state.openTask(t.id) } }
                    }
                    if (behaviorDone.isNotEmpty()) {
                        behaviorDone.forEach { t -> TaskCard(t) }
                    }
                }
            }
        }
        SectionLabel("Tu progreso")
        behaviors.forEach { b -> ChartCard(state.progressForBehavior(b)) }
        Text(
            "Cada tarea que registras agrega un punto a esta gráfica. Tu terapeuta la revisa antes de cada sesión.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PatientTaskScreen(state: PrototypeState) {
    val t = state.activeTask()
    ScreenScaffold {
        TextButton(onClick = { state.screen = Screen.PATIENT_HOME }, contentPadding = PaddingValues(0.dp)) {
            Text("← Mis tareas")
        }
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
                val measureLabel = if (t.taskType == TaskType.REGISTRO_AUTONOMO) "${t.dimension.label} (${t.dimension.unit})" else t.metricUnit?.label.orEmpty()
                Text(
                    "${t.taskType.label} · Medición: $measureLabel" + (t.dueDate?.let { " · Vence: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                        Dimension.FRECUENCIA -> Stepper(state.taskInputValue.toInt()) {
                            state.taskInputValue = it.coerceAtLeast(0).toDouble()
                        }
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
            label = { Text("¿Qué pasó? Nota para tu terapeuta (opcional)") },
            minLines = 2,
        )
        Button(onClick = { state.saveTask() }, Modifier.fillMaxWidth().height(50.dp)) { Text("Guardar registro") }
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
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

/* ----------------------------- terapeuta ----------------------------- */

@Composable
private fun TherapistHomeScreen(state: PrototypeState) {
    val patients = state.patients
    val totalPending = patients.sumOf { p -> p.tasks.count { it.status == TaskStatus.PENDIENTE } }
    ScreenScaffold {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                SectionLabel("Terapeuta")
                Text("José E. Patiño", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Navy),
                contentAlignment = Alignment.Center,
            ) {
                Text("JP", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
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

@Composable
private fun TherapistPatientScreen(state: PrototypeState) {
    val p = state.activePatient()
    ScreenScaffold {
        TextButton(onClick = { state.screen = Screen.THERAPIST_HOME }, contentPadding = PaddingValues(0.dp)) {
            Text("← Pacientes")
        }
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
            Pill(p.condition, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { state.startNewBehavior(p.id) }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Nueva conducta")
        }
        SectionLabel("Conductas del caso")
        p.behaviors.forEach { behavior ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(behavior.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(behavior.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PhasePill(if (behavior.baselineOpen) Phase.LINEA_BASE else Phase.INTERVENCION)
                    }
                    Text("${behavior.observationCount} observaciones de línea base · ${behavior.dimension.label}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (behavior.baselineOpen) "Línea base abierta: las nuevas tareas registrarán observaciones iniciales."
                        else "Línea base cerrada el ${behavior.baselineClosedOn}: los datos quedan como referencia fija.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (behavior.baselineOpen) {
                        OutlinedButton(onClick = { state.closeBaseline(behavior.id) }, Modifier.fillMaxWidth()) {
                            Text("Cerrar línea base")
                        }
                    }
                    Button(onClick = { state.startNewTask(p.id, behavior.id) }, Modifier.fillMaxWidth()) {
                        Text("Nueva tarea para esta conducta")
                    }
                    behavior.tasks.forEach { task -> TaskCard(task) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TherapistNewBehaviorScreen(state: PrototypeState) {
    ScreenScaffold {
        TextButton(onClick = { state.screen = Screen.THERAPIST_HOME }, contentPadding = PaddingValues(0.dp)) {
            Text("← Cancelar")
        }
        Text("Nueva conducta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    FilterChip(
                        selected = state.draftBehaviorType == type,
                        onClick = {
                            state.draftBehaviorType = type
                            if (type == BehaviorType.ADAPTATIVA) {
                                state.draftBehavioralFunction = BehavioralFunction.NO_APLICA
                                state.draftReplacementBehaviorId = null
                            }
                        },
                        label = { Text(type.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tint.copy(alpha = 0.18f), selectedLabelColor = tint),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                label = "Categoría",
                selectedLabel = state.categories.firstOrNull { it.id == state.draftCategoryId }?.name,
                placeholder = "Selecciona una categoría",
                options = state.categories.map { it.id to it.name } + ("__new__" to "+ Crear categoría nueva"),
                onSelect = { id -> if (id == "__new__") state.showNewCategoryForm = true else { state.draftCategoryId = id; state.showNewCategoryForm = false } },
            )
            if (state.showNewCategoryForm) {
                OutlinedTextField(state.draftNewCategoryName, { state.draftNewCategoryName = it }, Modifier.fillMaxWidth(), placeholder = { Text("Nombre de la categoría") }, singleLine = true)
                OutlinedTextField(state.draftNewCategoryDomain, { state.draftNewCategoryDomain = it }, Modifier.fillMaxWidth(), placeholder = { Text("Dominio (ej.: Autorregulación emocional)") }, singleLine = true)
                Button(onClick = {
                    val id = state.createCategory(state.draftNewCategoryName, state.draftNewCategoryDomain)
                    if (id != null) {
                        state.draftCategoryId = id
                        state.showNewCategoryForm = false
                        state.draftNewCategoryName = ""
                        state.draftNewCategoryDomain = ""
                    }
                }) { Text("Guardar categoría") }
            }
        }
        if (state.draftBehaviorType == BehaviorType.DESADAPTATIVA) {
            Column {
                FieldLabel("Función conductual")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BehavioralFunction.entries.forEach { fn ->
                        FilterChip(state.draftBehavioralFunction == fn, { state.draftBehavioralFunction = fn }, label = { Text(fn.label) })
                    }
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
private fun TherapistNewTaskScreen(state: PrototypeState) {
    ScreenScaffold {
        TextButton(onClick = { state.screen = Screen.THERAPIST_HOME }, contentPadding = PaddingValues(0.dp)) {
            Text("← Cancelar")
        }
        Text("Nueva actividad", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    FilterChip(
                        selected = state.draftTaskPhase == phase,
                        onClick = {
                            state.draftTaskPhase = phase
                            state.draftReminderActive = phase == Phase.INTERVENCION
                        },
                        label = { Text(phase.label) },
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
                placeholder = { Text("Qué debe hacer o anotar el paciente, y cómo…") },
                minLines = 3,
            )
        }
        Column {
            FieldLabel("Tipo de actividad")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskType.entries.forEach { type ->
                    FilterChip(state.draftTaskType == type, { state.draftTaskType = type }, label = { Text(type.label) })
                }
            }
        }
        if (state.draftTaskType == TaskType.REGISTRO_AUTONOMO) {
            Column {
                FieldLabel("Tipo de medición")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dimension.entries.forEach { dimension ->
                        FilterChip(state.draftTaskDimension == dimension, { state.draftTaskDimension = dimension }, label = { Text(dimension.label) })
                    }
                }
            }
        } else {
            Column {
                FieldLabel("Cómo se mide cumplirla")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricUnit.entries.forEach { unit ->
                        FilterChip(state.draftMetricUnit == unit, { state.draftMetricUnit = unit }, label = { Text(unit.label) })
                    }
                }
            }
        }
        Column {
            FieldLabel("Fecha de inicio")
            OutlinedTextField(state.draftAssignedDate, { state.draftAssignedDate = it }, Modifier.fillMaxWidth(), placeholder = { Text("Hoy") }, singleLine = true)
        }
        Column {
            FieldLabel("¿Se repite?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!state.draftIsRecurring, { state.draftIsRecurring = false }, label = { Text("Una sola vez") })
                FilterChip(state.draftIsRecurring, { state.draftIsRecurring = true }, label = { Text("Recurrente") })
            }
        }
        if (state.draftIsRecurring) {
            Column {
                FieldLabel("Frecuencia")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FRECUENCIAS.forEach { f ->
                        FilterChip(state.draftFrequency == f, { state.draftFrequency = f }, label = { Text(f) })
                    }
                }
            }
        }
        Column {
            FieldLabel("Hasta cuándo (opcional)")
            OutlinedTextField(
                state.draftDueDate,
                { state.draftDueDate = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("Vacío = sin fecha de cierre") },
                singleLine = true,
            )
        }
        Column {
            FieldLabel("Recordatorio")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!state.draftReminderActive, { state.draftReminderActive = false }, label = { Text("Desactivado") })
                FilterChip(state.draftReminderActive, { state.draftReminderActive = true }, label = { Text("Activado") })
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
            color = s.intervention.copy(alpha = 0.12f),
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
                val measure = if (t.taskType == TaskType.REGISTRO_AUTONOMO) "${t.dimension.label} (${t.dimension.unit})" else t.metricUnit?.label.orEmpty()
                Text("Tipo: ${t.taskType.label} · Mide: $measure", style = MaterialTheme.typography.bodySmall)
                val recurrence = if (t.isRecurring) "Recurrente (${t.frequency})" else "Una sola vez"
                Text("Fase: ${t.phase.label} · $recurrence", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = { state.openPatient(p.id) }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Ver ficha de ${p.firstName}")
        }
        OutlinedButton(onClick = { state.previewAsPatient(p.id) }, Modifier.fillMaxWidth()) {
            Text("Previsualizar como paciente")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TherapistProgressScreen(state: PrototypeState) {
    ScreenScaffold {
        Text("Análisis de tendencias", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Módulo de seguimiento · evolución de cada caso en plano cartesiano.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.patients.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(p, 30.dp)
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            ChartCard(p)
        }
        Spacer(Modifier.height(24.dp))
    }
}

