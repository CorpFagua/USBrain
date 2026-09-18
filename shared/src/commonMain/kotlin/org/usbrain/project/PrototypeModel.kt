package org.usbrain.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

fun todayDate(): LocalDate = LocalDate(2026, 9, 16)

enum class Role { PATIENT, THERAPIST, ADMIN }

enum class Dimension(val label: String, val unit: String, val hint: String) {
    FRECUENCIA("Frecuencia", "episodios", "¿Cuántas veces ocurrió hoy la conducta?"),
    DURACION("Duración", "minutos", "¿Cuánto duró cada ocurrencia?"),
    INTENSIDAD("Intensidad", "/ 10", "¿Qué tan intensa fue? Escala de 0 a 10"),
}

enum class Phase(val label: String) { LINEA_BASE("Línea base"), INTERVENCION("Intervención") }
enum class BehaviorType(val label: String) { ADAPTATIVA("Adaptativa"), DESADAPTATIVA("Desadaptativa") }
enum class TaskStatus { PENDIENTE, COMPLETADA }

// Solo aplica a conductas desadaptativas; NO_APLICA es el valor por defecto sin análisis funcional.
// Catálogo dinámico: el terapeuta puede registrar una función conductual nueva si el catálogo
// semilla no cubre el caso, sin tocar código.
data class BehavioralFunctionOption(val id: String, val label: String)
const val NO_APLICA_FUNCTION_ID = "fn-no-aplica"

enum class TaskType(val label: String) {
    REGISTRO_AUTONOMO("Registro autónomo"),
    EJERCICIO_PRACTICA("Ejercicio / práctica"),
    PSICOEDUCATIVA("Psicoeducativa"),
    CONDUCTUAL("Conductual"),
    // Actividad cuya medición se define por un WidgetConfig en vez de Dimension/MetricUnit fijos.
    MEDICION_CONFIGURABLE("Medición"),
}

// Solo aplica cuando el tipo de actividad no es registro autónomo (ese usa la dimensión de la conducta).
enum class MetricUnit(val label: String) {
    SOLO_COMPLETADO("Solo completado (sí/no)"),
    MINUTOS("Minutos"),
    REPETICIONES("Repeticiones"),
    ESCALA_1_5("Escala 1–5"),
}

// --- Sistema de widgets de medición configurables (paralelo a Dimension/MetricUnit) ---
// El terapeuta elige el tipo de respuesta con el que se registrará la conducta u observación:
// numérica (escalas, frecuencia, duración), sí/no, texto libre o selección múltiple.
enum class WidgetValueKind(val label: String) {
    NUMERIC("Numérica"),
    YES_NO("Sí / No"),
    TEXT("Texto abierto"),
    MULTI_SELECT("Selección múltiple"),
}

/**
 * Definición de una variable de medición configurable. Puede venir de un catálogo predefinido
 * (isPreset=true, ver [presetWidgetCatalog]) o ser creada por el terapeuta desde cero
 * (isPreset=false), definiendo nombre, tipo de respuesta, rango, unidad y descripción.
 */
data class WidgetConfig(
    val id: String,
    val name: String,
    val valueKind: WidgetValueKind,
    val unit: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val options: List<String> = emptyList(),
    val description: String? = null,
    val isPreset: Boolean = false,
)

// Un valor registrado, con la forma que corresponde al WidgetValueKind del widget medido.
sealed class WidgetValue {
    data class Numeric(val value: Double, val observationMinutes: Int? = null) : WidgetValue()
    data class YesNo(val value: Boolean) : WidgetValue()
    data class Text(val value: String) : WidgetValue()
    data class MultiSelect(val values: List<String>) : WidgetValue()
}

// Un registro del consultante para una actividad con widget configurable: guarda actividad,
// variable medida, fecha, hora y valor obtenido, para poder graficar evolución en el tiempo.
data class WidgetEntry(
    val id: String,
    val activityId: String,
    val widgetId: String,
    val date: LocalDate,
    val time: LocalTime,
    val value: WidgetValue,
    val note: String? = null,
)

// Catálogo semilla de mediciones listas para usar; el terapeuta puede tomarlas tal cual o crear
// una personalizada (ver PrototypeState.customWidgets/saveCustomWidget).
fun presetWidgetCatalog(): List<WidgetConfig> = listOf(
    WidgetConfig("w-escala", "Escala de ansiedad (1–10)", WidgetValueKind.NUMERIC, unit = "/10", minValue = 1.0, maxValue = 10.0, description = "Intensidad subjetiva de la conducta o emoción en una escala de 1 a 10.", isPreset = true),
    WidgetConfig("w-frecuencia", "Frecuencia de la conducta", WidgetValueKind.NUMERIC, unit = "episodios", minValue = 0.0, description = "Cuántas veces ocurrió la conducta en el período observado.", isPreset = true),
    WidgetConfig("w-duracion", "Duración de la conducta", WidgetValueKind.NUMERIC, unit = "minutos", minValue = 0.0, description = "Cuánto duró cada ocurrencia de la conducta, en minutos.", isPreset = true),
    WidgetConfig("w-cumplimiento", "Cumplimiento de la estrategia", WidgetValueKind.NUMERIC, unit = "%", minValue = 0.0, maxValue = 100.0, description = "Porcentaje de realización de la estrategia terapéutica indicada.", isPreset = true),
    WidgetConfig("w-si-no", "Ocurrencia (sí/no)", WidgetValueKind.YES_NO, description = "¿Ocurrió la conducta en el período observado?", isPreset = true),
    WidgetConfig("w-seleccion", "Síntomas o emociones presentes", WidgetValueKind.MULTI_SELECT, options = listOf("Ansiedad", "Tristeza", "Irritabilidad", "Evitación", "Insomnio", "Otro"), description = "Selecciona todos los síntomas o emociones presentes.", isPreset = true),
    WidgetConfig("w-texto", "Observación cualitativa", WidgetValueKind.TEXT, description = "Campo abierto para observaciones del consultante.", isPreset = true),
)

enum class Screen {
    LOGIN, TWO_FACTOR,
    // PATIENT_HOME es solo "tareas de hoy". PATIENT_BEHAVIORS es la pestaña "Historial": lista
    // las conductas del caso; tocar una lleva a PATIENT_BEHAVIOR (todas sus tareas), y de ahí a
    // PATIENT_TASK_HISTORY (todos los registros de esa tarea — puede tener varios). Nada de eso
    // se mezcla en Inicio.
    PATIENT_HOME, PATIENT_BEHAVIORS, PATIENT_BEHAVIOR, PATIENT_TASK, PATIENT_TASK_HISTORY, PATIENT_DONE, PATIENT_PROGRESS, PATIENT_PROFILE,
    // El terapeuta no tiene una pantalla de "progreso" aparte: al trabajar con diseño de caso
    // único, el análisis gráfico vive como pestaña dentro del detalle de la conducta
    // (THERAPIST_BEHAVIOR). THERAPIST_PATIENT_INFO es la ficha administrativa del paciente
    // (diagnóstico CIE-10, datos de contacto…), separada de THERAPIST_PATIENT para que esa
    // pantalla se mantenga enfocada solo en las conductas del caso.
    THERAPIST_HOME, THERAPIST_PATIENT, THERAPIST_PATIENT_INFO, THERAPIST_BEHAVIOR, THERAPIST_TASK_DETAIL,
    THERAPIST_NEW_BEHAVIOR, THERAPIST_BASELINE_SETUP, THERAPIST_NEW_TASK, THERAPIST_NEW_WIDGET, THERAPIST_ASSIGNED, THERAPIST_PROFILE,
    ADMIN_HOME,
}

/** Pestañas dentro del detalle de una conducta: separan "gestionar actividades" de "ver la
 * evolución graficada" para no mezclar creación de datos con su análisis en la misma vista. */
enum class BehaviorTab { ACTIVIDADES, ANALISIS }

/** Un ítem de la barra de navegación inferior de un perfil. */
data class NavTab(val screen: Screen, val label: String, val glyph: String)

/**
 * Perfil de acceso: define qué pantalla de inicio y qué tabs ve un rol al iniciar sesión.
 * Vive como lista mutable en [PrototypeState] (`roleProfiles`) para que la navegación de
 * toda la app se resuelva a partir de estos datos y no de `if/else` por rol; así, registrar
 * un perfil nuevo (o ajustar uno existente) no requiere tocar las pantallas ni la barra inferior.
 */
data class RoleProfile(
    val role: Role,
    val displayName: String,
    val description: String,
    val badgeColor: Long,
    val homeScreen: Screen,
    val tabs: List<NavTab>,
)

private fun defaultRoleProfiles(): List<RoleProfile> = listOf(
    RoleProfile(
        role = Role.PATIENT,
        displayName = "Paciente",
        description = "Consulta y llena las tareas que asigna tu terapeuta",
        badgeColor = 0xFF7B66C2, // Brand.Violet
        homeScreen = Screen.PATIENT_HOME,
        tabs = listOf(
            NavTab(Screen.PATIENT_HOME, "Inicio", "home"),
            NavTab(Screen.PATIENT_BEHAVIORS, "Historial", "list"),
            NavTab(Screen.PATIENT_PROGRESS, "Progreso", "chart"),
            NavTab(Screen.PATIENT_PROFILE, "Perfil", "person"),
        ),
    ),
    RoleProfile(
        role = Role.THERAPIST,
        displayName = "Terapeuta",
        description = "Define conductas y asigna tareas de seguimiento",
        badgeColor = 0xFF0F2540, // Brand.Navy
        homeScreen = Screen.THERAPIST_HOME,
        // Sin tab de "Progreso" agregado: en caso único el análisis es por paciente/actividad,
        // no un tablero global — se llega a él entrando al paciente → conducta → actividad.
        tabs = listOf(
            NavTab(Screen.THERAPIST_HOME, "Pacientes", "home"),
            NavTab(Screen.THERAPIST_PROFILE, "Perfil", "person"),
        ),
    ),
    RoleProfile(
        role = Role.ADMIN,
        displayName = "Administrador",
        description = "Gestiona perfiles, permisos y accesos del sistema",
        badgeColor = 0xFFFF7A00, // Brand.Orange
        homeScreen = Screen.ADMIN_HOME,
        tabs = listOf(
            NavTab(Screen.ADMIN_HOME, "Panel", "home"),
        ),
    ),
)

data class TaskEntry(
    val date: LocalDate,
    val value: Double,
    val note: String,
    val phase: Phase,
    val observationMinutes: Int? = null,
)

data class BehaviorRecord(
    val id: String,
    val taskId: String,
    val behaviorId: String,
    val date: LocalDate,
    val value: Double,
    val note: String,
    val phase: Phase,
    val dimension: Dimension,
    val observationMinutes: Int? = null,
)

fun BehaviorRecord.valueForChart(): Double =
    if (dimension == Dimension.FRECUENCIA && observationMinutes != null && observationMinutes > 0) {
        value / observationMinutes
    } else {
        value
    }

class TrackTask(
    val id: String,
    val behaviorId: String,
    val title: String,
    val instructions: String,
    val taskType: TaskType,
    val dimension: Dimension,
    val metricUnit: MetricUnit?,
    val assignedDate: String,
    val isRecurring: Boolean,
    val frequency: String?,
    val dueDate: String?,
    val reminderActive: Boolean,
    val phase: Phase,
    status: TaskStatus,
    entries: List<TaskEntry> = emptyList(),
    // Sistema nuevo y opcional: cuando está presente, esta actividad se mide con un widget
    // configurable (widgetEntries) en vez de Dimension/MetricUnit (entries); ambos caminos
    // conviven sin mezclarse.
    val widgetConfig: WidgetConfig? = null,
    val therapeuticGoal: String? = null,
    widgetEntries: List<WidgetEntry> = emptyList(),
) {
    var status by mutableStateOf(status)
    val entries = entries.toMutableStateList()
    val widgetEntries = widgetEntries.toMutableStateList()
    // Solo el registro autónomo mide la conducta; el resto tiene su propia métrica y no alimenta la serie.
    val feedsSeries: Boolean get() = taskType == TaskType.REGISTRO_AUTONOMO
    // Análogo a feedsSeries pero para el sistema de widgets configurables.
    val feedsWidgetSeries: Boolean get() = widgetConfig != null

    // Puntos (fecha, valor) ordenados cronológicamente, para graficar widgets numéricos.
    fun numericWidgetSeries(): List<Pair<LocalDate, Double>> = widgetEntries
        .mapNotNull { entry -> (entry.value as? WidgetValue.Numeric)?.let { entry.date to it.value } }
        .sortedBy { it.first }
}

class Behavior(
    val id: String,
    val caseId: String,
    val name: String,
    val operationalDefinition: String,
    val type: BehaviorType,
    baselineOpenInitially: Boolean,
    behavioralFunctionId: String = NO_APLICA_FUNCTION_ID,
    replacementBehaviorId: String? = null,
    baselineEntries: List<BehaviorRecord> = emptyList(),
    tasks: List<TrackTask> = emptyList(),
    baselineDimensions: List<Dimension> = baselineEntries.map { it.dimension }.distinct().ifEmpty { listOf(Dimension.FRECUENCIA) },
) {
    var baselineOpen by mutableStateOf(baselineOpenInitially)
    var baselineClosedOn by mutableStateOf<String?>(if (baselineOpenInitially) null else "Demo")
    // Mutable para permitir enlazar la conducta de reemplazo más adelante, aunque este formulario no se reabra.
    // Referencia al catálogo dinámico `PrototypeState.behavioralFunctions` por id, no un enum
    // cerrado, para poder registrar funciones nuevas sin tocar código.
    var behavioralFunctionId by mutableStateOf(behavioralFunctionId)
    var replacementBehaviorId by mutableStateOf(replacementBehaviorId)
    val baselineEntries = baselineEntries.toMutableStateList()
    val tasks = tasks.toMutableStateList()
    val baselineDimensions = baselineDimensions.toMutableStateList()

    // El tipo de medición ya no se fija en la conducta: se elige por tarea de registro autónomo.
    // Se conserva como "dimensión por defecto" (la primera declarada) para vistas que solo
    // necesitan un rótulo rápido (tarjetas, badges); la gráfica real usa `dimensions`/`recordsFor`.
    val dimension: Dimension
        get() = tasks.firstOrNull { it.taskType == TaskType.REGISTRO_AUTONOMO && it.widgetConfig == null }?.dimension
            ?: baselineEntries.firstOrNull()?.dimension
            ?: Dimension.FRECUENCIA

    // Todas las variables que efectivamente se están midiendo para esta conducta: una misma
    // conducta puede tener más de una actividad de registro autónomo, cada una evaluando un
    // aspecto distinto del mismo episodio (p. ej. frecuencia Y duración). En diseño de caso
    // único cada dimensión se grafica por separado —no se mezclan escalas en una sola serie—,
    // así que la UI debe ofrecerlas como opciones intercambiables. Ver "Variables graficadas en
    // diseño de caso único" en el README para la fundamentación.
    val dimensions: List<Dimension>
        get() {
            // Los tasks con widgetConfig no participan de la serie legacy por Dimension.
            val fromTasks = tasks.filter { it.taskType == TaskType.REGISTRO_AUTONOMO && it.widgetConfig == null }.map { it.dimension }
            val fromBaseline = baselineEntries.map { it.dimension }
            return (fromTasks + fromBaseline).distinct().ifEmpty { listOf(Dimension.FRECUENCIA) }
        }

    val records: List<BehaviorRecord>
        get() = tasks.filter { it.taskType == TaskType.REGISTRO_AUTONOMO && it.widgetConfig == null }.flatMap { task -> task.entries.filter { it.phase == Phase.INTERVENCION }.map { entry ->
            BehaviorRecord("${task.id}-${entry.date}", task.id, id, entry.date, entry.value, entry.note, entry.phase, task.dimension, entry.observationMinutes)
        } } + baselineEntries

    // Actividades de esta conducta que usan el sistema nuevo de widgets configurables.
    val widgetActivities: List<TrackTask> get() = tasks.filter { it.widgetConfig != null }

    // Filtra la serie a una sola dimensión: es lo que realmente debe alimentar una gráfica,
    // para no mezclar, por ejemplo, "episodios" con "minutos" en la misma línea de tendencia.
    fun recordsFor(dimension: Dimension): List<BehaviorRecord> = records.filter { it.dimension == dimension }

    fun chartValuesFor(dimension: Dimension): List<Pair<BehaviorRecord, Double>> =
        recordsFor(dimension).sortedBy { it.date }.map { it to it.valueForChart() }

    val observationCount: Int get() = baselineEntries.size
    fun observationCountFor(dimension: Dimension): Int = baselineEntries.count { it.dimension == dimension }
}

class SingleCase(
    val id: String,
    val patientId: String,
    val title: String,
    behaviors: List<Behavior>,
) {
    val behaviorList = behaviors.toMutableStateList()
    val phase: Phase get() = if (behaviorList.any { it.baselineOpen }) Phase.LINEA_BASE else Phase.INTERVENCION
}

// Diagnóstico por código CIE-10 (Clasificación Internacional de Enfermedades, OMS — cap. V,
// trastornos mentales y del comportamiento). Nulo mientras no se asigne ninguno: un paciente
// puede empezar sin diagnóstico y el terapeuta se lo asigna cuando corresponda; puede tener más
// de uno a la vez si hay comorbilidad, por eso vive como lista y no como campo único.
data class Cie10Diagnosis(val code: String, val label: String)

class Patient(
    val id: String,
    val name: String,
    val age: Int,
    val program: String,
    val colorArgb: Long,
    val condition: String,
    val case: SingleCase,
    diagnoses: List<Cie10Diagnosis> = emptyList(),
) {
    val diagnoses = diagnoses.toMutableStateList()
    val behaviors: List<Behavior> get() = case.behaviorList
    val tasks: List<TrackTask> get() = case.behaviorList.flatMap { it.tasks }
    val behavior: String get() = case.behaviorList.firstOrNull()?.name ?: "Sin conducta"
    val dimension: Dimension get() = case.behaviorList.firstOrNull()?.dimension ?: Dimension.FRECUENCIA
    val phase: Phase get() = case.phase
    val baselineDays: Int get() = case.behaviorList.firstOrNull()?.observationCount ?: 0
    val series: List<Double> get() = case.behaviorList.firstOrNull()?.records?.map { it.value } ?: emptyList()
    val firstName: String get() = name.substringBefore(' ')
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

val FRECUENCIAS = listOf("Diaria", "N veces por semana", "Semanal", "Personalizada")

/** Estado en memoria del prototipo. Se recrea al "Reiniciar demo". */
class PrototypeState {
    var screen by mutableStateOf(Screen.LOGIN)
    var role by mutableStateOf<Role?>(null)
    var loginRole by mutableStateOf(Role.PATIENT)
    var meId by mutableStateOf("ana")
    var activePatientId by mutableStateOf("ana")
    var activeTaskId by mutableStateOf<String?>(null)
    var activeBehaviorId by mutableStateOf<String?>(null)
    // Pestaña activa del detalle de conducta; vive en el estado (no local a la pantalla) para
    // que el FAB de "nueva actividad" en el Scaffold superior sepa si debe mostrarse.
    var behaviorDetailTab by mutableStateOf(BehaviorTab.ACTIVIDADES)

    var taskInputValue by mutableStateOf(0.0)
    var taskObservationMinutes by mutableStateOf(0)
    var taskNote by mutableStateOf("")
    var taskEntryDate by mutableStateOf(todayDate())
    var taskEntryTime by mutableStateOf(LocalTime(9, 0))
    var taskError by mutableStateOf<String?>(null)

    var draftPatientId by mutableStateOf("ana")
    var draftBehaviorId by mutableStateOf<String?>(null)
    var draftBehaviorName by mutableStateOf("")
    var draftDefinition by mutableStateOf("")
    var draftBehaviorType by mutableStateOf(BehaviorType.DESADAPTATIVA)
    var draftBehavioralFunctionId by mutableStateOf(NO_APLICA_FUNCTION_ID)
    var draftReplacementBehaviorId by mutableStateOf<String?>(null)
    var draftNewFunctionLabel by mutableStateOf("")
    var showNewFunctionForm by mutableStateOf(false)
    var draftDiagnosisCode by mutableStateOf("")
    var draftDiagnosisLabel by mutableStateOf("")
    var showNewDiagnosisForm by mutableStateOf(false)
    var draftTaskTitle by mutableStateOf("")
    var draftTaskType by mutableStateOf(TaskType.REGISTRO_AUTONOMO)
    var draftTaskDimension by mutableStateOf(Dimension.FRECUENCIA)
    var draftMetricUnit by mutableStateOf(MetricUnit.SOLO_COMPLETADO)
    var draftTaskPhase by mutableStateOf(Phase.LINEA_BASE)
    var draftAssignedDate by mutableStateOf("Hoy")
    var draftBaselineDimensions = mutableStateListOf<Dimension>()
    var draftIsRecurring by mutableStateOf(false)
    var draftFrequency by mutableStateOf(FRECUENCIAS.first())
    var draftDueDate by mutableStateOf("")
    var draftReminderActive by mutableStateOf(false)
    var draftInstructions by mutableStateOf("")
    var assignError by mutableStateOf<String?>(null)
    var behaviorError by mutableStateOf<String?>(null)

    // --- Sistema de widgets de medición configurables (nuevo, en paralelo a Dimension/MetricUnit) ---
    var draftUseWidgetSystem by mutableStateOf(true)
    var draftSelectedWidgetId by mutableStateOf<String?>(null)
    var draftTherapeuticGoal by mutableStateOf("")

    // Draft para crear una medición personalizada (WidgetConfig hecho por el terapeuta).
    var draftCustomWidgetName by mutableStateOf("")
    var draftCustomWidgetValueKind by mutableStateOf(WidgetValueKind.NUMERIC)
    var draftCustomWidgetUnit by mutableStateOf("")
    var draftCustomWidgetMin by mutableStateOf("")
    var draftCustomWidgetMax by mutableStateOf("")
    var draftCustomWidgetOptionsText by mutableStateOf("")
    var draftCustomWidgetDescription by mutableStateOf("")
    var customWidgetError by mutableStateOf<String?>(null)

    // Draft de captura del consultante al responder un registro de widget configurable.
    var draftWidgetNumericValue by mutableStateOf(0.0)
    var draftWidgetObservationMinutes by mutableStateOf(0)
    var draftWidgetBoolValue by mutableStateOf(true)
    var draftWidgetTextValue by mutableStateOf("")
    val draftWidgetSelectedOptions = mutableStateListOf<String>()

    private var nextId = 10
    val patients = samplePatients().toMutableStateList()
    // Mediciones personalizadas creadas por el terapeuta; se suman al catálogo predefinido para
    // poder reutilizarse en cualquier actividad/consultante posterior.
    val customWidgets = mutableStateListOf<WidgetConfig>()
    fun widgetCatalog(): List<WidgetConfig> = presetWidgetCatalog() + customWidgets
    fun widget(id: String): WidgetConfig? = widgetCatalog().firstOrNull { it.id == id }
    // Catálogo semilla editable, no un enum cerrado, para poder registrar una función conductual
    // nueva cuando el análisis funcional del caso lo requiera.
    val behavioralFunctions = seedBehavioralFunctions().toMutableStateList()
    // Catálogo de apoyo (no exhaustivo) con códigos CIE-10 frecuentes en consulta psicológica;
    // el terapeuta puede asignar cualquier otro código a mano si no está en la lista.
    val cie10Catalog = seedCie10().toMutableStateList()

    // Mutable a propósito: es el catálogo de roles/permisos del sistema, pensado para poder
    // registrar perfiles nuevos más adelante sin cambiar la navegación (ver [RoleProfile]).
    val roleProfiles = defaultRoleProfiles().toMutableStateList()
    fun roleProfile(role: Role): RoleProfile? = roleProfiles.firstOrNull { it.role == role }
    val currentProfile: RoleProfile? get() = role?.let { roleProfile(it) }

    fun patient(id: String): Patient = patients.first { it.id == id }
    fun me(): Patient = patient(meId)
    fun activePatient(): Patient = patient(activePatientId)
    fun activeCase(): SingleCase = activePatient().case
    fun behavior(id: String): Behavior = patients.asSequence().flatMap { it.case.behaviorList.asSequence() }.first { it.id == id }
    fun activeBehavior(): Behavior = behavior(activeBehaviorId ?: error("No hay conducta activa"))
    fun activeTask(): TrackTask = activeBehavior().tasks.first { it.id == activeTaskId }
    fun behaviorsFor(patientId: String): List<Behavior> = patient(patientId).case.behaviorList
    fun tasksFor(patientId: String): List<TrackTask> = behaviorsFor(patientId).flatMap { it.tasks }
    fun adaptiveBehaviorsFor(patientId: String): List<Behavior> = behaviorsFor(patientId).filter { it.type == BehaviorType.ADAPTATIVA }

    fun createBehavioralFunction(label: String): String? {
        if (label.isBlank()) return null
        val id = "fn${nextId++}"
        behavioralFunctions.add(BehavioralFunctionOption(id, label.trim()))
        return id
    }

    // Asignar, no reemplazar: un paciente puede tener más de un código CIE-10 (comorbilidad).
    // Si el código ya estaba asignado no se duplica.
    fun assignDiagnosis(patientId: String, code: String, label: String): Boolean {
        val trimmedCode = code.trim().uppercase()
        if (trimmedCode.isBlank()) return false
        val p = patient(patientId)
        if (p.diagnoses.none { it.code == trimmedCode }) {
            p.diagnoses.add(Cie10Diagnosis(trimmedCode, label.trim().ifBlank { trimmedCode }))
        }
        return true
    }

    fun removeDiagnosis(patientId: String, code: String) {
        patient(patientId).diagnoses.removeAll { it.code == code }
    }
    
    // Funciones helper para las pantallas del paciente
    fun caseForPatient(patientId: String): String? = patient(patientId).case.id
    fun behaviorsForCase(caseId: String): List<Behavior> = patients.asSequence().flatMap { it.case.behaviorList.asSequence() }.filter { it.caseId == caseId }.toList()
    fun tasksForBehavior(behaviorId: String): List<TrackTask> = behavior(behaviorId).tasks.toList()
    fun recordsForBehavior(behaviorId: String): List<BehaviorRecord> {
        val b = behavior(behaviorId)
        return b.records
    }
    fun progressForBehavior(behavior: Behavior): Behavior = behavior

    fun verify() {
        role = loginRole
        if (loginRole == Role.PATIENT) { meId = "ana"; activePatientId = "ana" }
        screen = roleProfile(loginRole)?.homeScreen ?: Screen.LOGIN
    }

    fun logout() { role = null; screen = Screen.LOGIN }

    fun openTask(id: String, behaviorId: String? = null) {
        activeTaskId = id
        activeBehaviorId = behaviorId ?: activePatient().behaviors.first { behavior -> behavior.tasks.any { it.id == id } }.id
        val task = activeTask()
        taskEntryDate = todayDate()
        taskEntryTime = LocalTime(9, 0)
        // Solo completado no pide un valor numérico: se guarda como "cumplida" (1.0).
        taskInputValue = if (task.taskType != TaskType.REGISTRO_AUTONOMO && task.metricUnit == MetricUnit.SOLO_COMPLETADO) 1.0 else 0.0
        taskObservationMinutes = 0
        taskNote = ""
        taskError = null
        // Reinicia los campos de captura del widget configurable según su tipo de respuesta.
        val widget = task.widgetConfig
        if (widget != null) {
            draftWidgetNumericValue = widget.minValue ?: 0.0
            draftWidgetObservationMinutes = 0
            draftWidgetBoolValue = true
            draftWidgetTextValue = ""
            draftWidgetSelectedOptions.clear()
        }
        screen = Screen.PATIENT_TASK
    }

    fun openPatientBehavior(id: String) { activeBehaviorId = id; screen = Screen.PATIENT_BEHAVIOR }

    // El historial (todos los registros de una tarea) solo se llega a él desde el detalle de su
    // conducta, para que "volver" tenga un único destino y no haya que recordar de dónde se entró.
    fun openTaskHistory(id: String, behaviorId: String) {
        activeTaskId = id
        activeBehaviorId = behaviorId
        screen = Screen.PATIENT_TASK_HISTORY
    }

    fun saveTask(date: LocalDate = todayDate()) {
        val behavior = activeBehavior()
        val task = activeTask()
        if (behavior.recordsFor(task.dimension).any { it.date == date && it.phase == task.phase }) {
            taskError = "Ya existe un registro de esta tarea para el ${date.dayOfMonth}/${date.monthNumber}/${date.year}."
            return
        }
        val observationMinutes = taskObservationMinutes.takeIf {
            task.taskType == TaskType.REGISTRO_AUTONOMO && task.dimension == Dimension.FRECUENCIA && it > 0
        }
        val entry = TaskEntry(date, taskInputValue, taskNote.trim(), task.phase, observationMinutes)
        task.status = TaskStatus.COMPLETADA
        task.entries.add(entry)
        if (task.taskType == TaskType.REGISTRO_AUTONOMO && task.phase == Phase.LINEA_BASE && behavior.baselineOpen) {
            behavior.baselineEntries.add(BehaviorRecord("r${nextId++}", task.id, behavior.id, entry.date, entry.value, entry.note, Phase.LINEA_BASE, task.dimension, entry.observationMinutes))
        }
        taskEntryDate = date
        screen = Screen.PATIENT_DONE
    }

    // Análogo a saveTask() pero para actividades con widget configurable: construye el WidgetValue
    // según el tipo de respuesta del widget y lo guarda con fecha y hora del registro.
    fun saveWidgetEntry(date: LocalDate = taskEntryDate, time: LocalTime = taskEntryTime) {
        val task = activeTask()
        val widget = task.widgetConfig ?: return
        if (widget.valueKind == WidgetValueKind.MULTI_SELECT && draftWidgetSelectedOptions.isEmpty()) {
            taskError = "Selecciona al menos una opción antes de guardar."
            return
        }
        if (widget.valueKind == WidgetValueKind.NUMERIC) {
            val value = draftWidgetNumericValue
            if (widget.minValue != null && value < widget.minValue || widget.maxValue != null && value > widget.maxValue) {
                taskError = "El valor debe estar entre ${widget.minValue ?: "el mínimo"} y ${widget.maxValue ?: "el máximo"}."
                return
            }
        }
        if (task.widgetEntries.any { it.date == date }) {
            taskError = "Ya existe un registro de esta actividad para el ${date.dayOfMonth}/${date.monthNumber}/${date.year}."
            return
        }
        val value = when (widget.valueKind) {
            WidgetValueKind.NUMERIC -> WidgetValue.Numeric(draftWidgetNumericValue, draftWidgetObservationMinutes.takeIf { it > 0 })
            WidgetValueKind.YES_NO -> WidgetValue.YesNo(draftWidgetBoolValue)
            WidgetValueKind.TEXT -> WidgetValue.Text(draftWidgetTextValue.trim())
            WidgetValueKind.MULTI_SELECT -> WidgetValue.MultiSelect(draftWidgetSelectedOptions.toList())
        }
        task.status = TaskStatus.COMPLETADA
        task.widgetEntries.add(WidgetEntry("we${nextId++}", task.id, widget.id, date, time, value, taskNote.trim().ifBlank { null }))
        taskEntryDate = date
        screen = Screen.PATIENT_DONE
    }

    fun toggleWidgetOption(option: String) {
        if (draftWidgetSelectedOptions.contains(option)) draftWidgetSelectedOptions.remove(option)
        else draftWidgetSelectedOptions.add(option)
    }

    fun startNewBehavior(patientId: String) {
        // Siempre se entra desde la ficha de un paciente: no hay atajo global que permita crear sin ese contexto.
        draftPatientId = patientId
        draftBehaviorName = ""
        draftDefinition = ""
        draftBehaviorType = BehaviorType.DESADAPTATIVA
        draftBehavioralFunctionId = NO_APLICA_FUNCTION_ID
        draftReplacementBehaviorId = null
        draftNewFunctionLabel = ""
        showNewFunctionForm = false
        behaviorError = null
        screen = Screen.THERAPIST_NEW_BEHAVIOR
    }

    fun saveBehavior() {
        if (draftBehaviorName.isBlank() || draftDefinition.isBlank()) {
            behaviorError = "Completa el nombre y la definición operacional de la conducta."
            return
        }
        val patient = patient(draftPatientId)
        val id = "b${nextId++}"
        val isMaladaptive = draftBehaviorType == BehaviorType.DESADAPTATIVA
        patient.case.behaviorList.add(
            Behavior(
                id, patient.case.id, draftBehaviorName.trim(), draftDefinition.trim(),
                draftBehaviorType, true,
                behavioralFunctionId = if (isMaladaptive) draftBehavioralFunctionId else NO_APLICA_FUNCTION_ID,
                replacementBehaviorId = if (isMaladaptive) draftReplacementBehaviorId else null,
            )
        )
        activePatientId = patient.id
        activeBehaviorId = id
        behaviorDetailTab = BehaviorTab.ACTIVIDADES
        draftBaselineDimensions.clear()
        draftBaselineDimensions.addAll(listOf(Dimension.FRECUENCIA))
        screen = Screen.THERAPIST_BASELINE_SETUP
    }

    fun configureBaseline() {
        val behavior = activeBehavior()
        val selected: List<Dimension> = draftBaselineDimensions.distinct().ifEmpty { listOf(Dimension.FRECUENCIA) }
        behavior.baselineDimensions.clear()
        behavior.baselineDimensions.addAll(selected)
        selected.forEach { dimension ->
            val taskTitle = when (dimension) {
                Dimension.FRECUENCIA -> "Registrar frecuencia diaria"
                Dimension.DURACION -> "Registrar duración diaria"
                Dimension.INTENSIDAD -> "Registrar intensidad diaria"
                else -> "Registrar conducta diaria"
            }
            val taskInstructions = when (dimension) {
                Dimension.FRECUENCIA -> "Cuenta cuántas veces ocurrió la conducta en el día."
                Dimension.DURACION -> "Anota cuántos minutos duró la conducta en el día."
                Dimension.INTENSIDAD -> "Valora la intensidad de la conducta en una escala del 0 al 10."
                else -> "Registra la conducta del día."
            }
            behavior.tasks.add(
                TrackTask(
                    "t${nextId++}", behavior.id, taskTitle, taskInstructions,
                    TaskType.REGISTRO_AUTONOMO, dimension, null, "Hoy", true, "Diaria",
                    null, false, Phase.LINEA_BASE, TaskStatus.PENDIENTE,
                ),
            )
        }
        screen = Screen.THERAPIST_BEHAVIOR
    }

    fun openPatient(id: String) {
        activePatientId = id
        showNewDiagnosisForm = false
        draftDiagnosisCode = ""
        draftDiagnosisLabel = ""
        screen = Screen.THERAPIST_PATIENT
    }
    fun openBehaviorDetail(id: String) {
        activeBehaviorId = id
        behaviorDetailTab = BehaviorTab.ACTIVIDADES
        screen = Screen.THERAPIST_BEHAVIOR
    }
    fun openTaskDetail(id: String) { activeTaskId = id; screen = Screen.THERAPIST_TASK_DETAIL }

    fun closeBaseline(id: String) {
        val behavior = behavior(id)
        if (behavior.baselineOpen) {
            behavior.baselineOpen = false
            behavior.baselineClosedOn = "Hoy"
        }
    }

    fun startNewTask(patientId: String, behaviorId: String) {
        // Siempre se entra desde la conducta del paciente: id_paciente e id_conducta viajan de contexto.
        draftPatientId = patientId
        draftBehaviorId = behaviorId
        draftTaskTitle = ""
        draftTaskType = TaskType.REGISTRO_AUTONOMO
        // Sugerido: si ya hay tareas de registro autónomo, se mantiene su tipo de medición; si no, Frecuencia.
        draftTaskDimension = behavior(behaviorId).dimension
        draftMetricUnit = MetricUnit.SOLO_COMPLETADO
        val suggestedPhase = if (behavior(behaviorId).baselineOpen) Phase.LINEA_BASE else Phase.INTERVENCION
        draftTaskPhase = suggestedPhase
        draftAssignedDate = "Hoy"
        draftIsRecurring = false
        draftFrequency = FRECUENCIAS.first()
        draftDueDate = ""
        draftReminderActive = suggestedPhase == Phase.INTERVENCION
        draftInstructions = ""
        draftUseWidgetSystem = true
        draftSelectedWidgetId = null
        draftTherapeuticGoal = ""
        assignError = null
        screen = Screen.THERAPIST_NEW_TASK
    }

    fun startCustomWidget() {
        draftCustomWidgetName = ""
        draftCustomWidgetValueKind = WidgetValueKind.NUMERIC
        draftCustomWidgetUnit = ""
        draftCustomWidgetMin = ""
        draftCustomWidgetMax = ""
        draftCustomWidgetOptionsText = ""
        draftCustomWidgetDescription = ""
        customWidgetError = null
        screen = Screen.THERAPIST_NEW_WIDGET
    }

    // Crea una medición personalizada a partir del draft y la deja preseleccionada para la
    // actividad que se está armando en THERAPIST_NEW_TASK.
    fun saveCustomWidget(): String? {
        if (draftCustomWidgetName.isBlank()) {
            customWidgetError = "Completa el nombre de la variable a medir."
            return null
        }
        val options = draftCustomWidgetOptionsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (draftCustomWidgetValueKind == WidgetValueKind.MULTI_SELECT && options.isEmpty()) {
            customWidgetError = "Agrega al menos una opción, separadas por coma."
            return null
        }
        val id = "cw${nextId++}"
        customWidgets.add(
            WidgetConfig(
                id = id,
                name = draftCustomWidgetName.trim(),
                valueKind = draftCustomWidgetValueKind,
                unit = draftCustomWidgetUnit.trim().ifBlank { null },
                minValue = draftCustomWidgetMin.toDoubleOrNull(),
                maxValue = draftCustomWidgetMax.toDoubleOrNull(),
                options = options,
                description = draftCustomWidgetDescription.trim().ifBlank { null },
                isPreset = false,
            )
        )
        draftSelectedWidgetId = id
        draftUseWidgetSystem = true
        screen = Screen.THERAPIST_NEW_TASK
        return id
    }

    fun assign() {
        val selected = draftBehaviorId?.let { runCatching { behavior(it) }.getOrNull() }
        if (selected == null || draftTaskTitle.isBlank()) {
            assignError = "Completa el nombre de la actividad."
            return
        }
        val resolvedWidget = if (draftUseWidgetSystem) draftSelectedWidgetId?.let { widget(it) } else null
        if (draftUseWidgetSystem && resolvedWidget == null) {
            assignError = "Selecciona un método de medición para la actividad."
            return
        }
        selected.tasks.add(
            TrackTask(
                "t${nextId++}", selected.id, draftTaskTitle.trim(), draftInstructions.trim(),
                taskType = if (resolvedWidget != null) TaskType.MEDICION_CONFIGURABLE else draftTaskType,
                dimension = draftTaskDimension,
                metricUnit = if (resolvedWidget != null || draftTaskType == TaskType.REGISTRO_AUTONOMO) null else draftMetricUnit,
                assignedDate = draftAssignedDate.trim().ifBlank { "Hoy" },
                isRecurring = draftIsRecurring,
                frequency = if (draftIsRecurring) draftFrequency else null,
                dueDate = draftDueDate.trim().ifBlank { null },
                reminderActive = draftReminderActive,
                phase = draftTaskPhase,
                status = TaskStatus.PENDIENTE,
                widgetConfig = resolvedWidget,
                therapeuticGoal = draftTherapeuticGoal.trim().ifBlank { null },
            )
        )
        // fase_actual de la conducta avanza sola si la fase elegida es más avanzada; nunca se toca a mano.
        if (draftTaskPhase == Phase.INTERVENCION) closeBaseline(selected.id)
        activePatientId = draftPatientId
        activeBehaviorId = selected.id
        screen = Screen.THERAPIST_ASSIGNED
    }

    fun previewAsPatient(id: String) {
        role = Role.PATIENT; meId = id; activePatientId = id; screen = Screen.PATIENT_HOME
    }
}

private fun samplePatients(): List<Patient> {
    val anxiety = Behavior(
        "b-ana-anxiety", "case-ana", "Episodios de ansiedad en clase",
        "Episodios observables de ansiedad durante actividades académicas.", BehaviorType.DESADAPTATIVA, false,
        baselineEntries = listOf(
            BehaviorRecord("r-a1", "t-a-base", "b-ana-anxiety", LocalDate(2026, 9, 1), 5.0, "Cinco episodios en 45 minutos.", Phase.LINEA_BASE, Dimension.FRECUENCIA, 45),
            BehaviorRecord("r-a2", "t-a-base", "b-ana-anxiety", LocalDate(2026, 9, 2), 6.0, "Seis episodios en 45 minutos.", Phase.LINEA_BASE, Dimension.FRECUENCIA, 45),
            BehaviorRecord("r-a3", "t-a-base", "b-ana-anxiety", LocalDate(2026, 9, 3), 5.0, "Cinco episodios en 45 minutos.", Phase.LINEA_BASE, Dimension.FRECUENCIA, 45),
            BehaviorRecord("r-a4", "t-a-base", "b-ana-anxiety", LocalDate(2026, 9, 4), 4.0, "Cuatro episodios en 30 minutos.", Phase.LINEA_BASE, Dimension.FRECUENCIA, 30),
        ),
        tasks = listOf(
            TrackTask("t1", "b-ana-anxiety", "Registrar episodios de ansiedad del día", "Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado durante las clases.", TaskType.REGISTRO_AUTONOMO, Dimension.FRECUENCIA, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.PENDIENTE),
            TrackTask("t2", "b-ana-anxiety", "Escala de malestar antes de exponer en clase", "Justo antes de tu presentación, registra tu nivel de malestar de 0 (nada) a 10 (máximo).", TaskType.REGISTRO_AUTONOMO, Dimension.INTENSIDAD, null, "Hoy", true, "Personalizada", "Mié 3 sep", true, Phase.INTERVENCION, TaskStatus.PENDIENTE),
            TrackTask("t0", "b-ana-anxiety", "Registrar episodios de ansiedad del día", "Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado.", TaskType.REGISTRO_AUTONOMO, Dimension.FRECUENCIA, null, "Ayer", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.COMPLETADA, listOf(
                TaskEntry(LocalDate(2026, 9, 5), 3.0, "Solo en la clase de la tarde.", Phase.INTERVENCION, 45),
                TaskEntry(LocalDate(2026, 9, 6), 3.0, "La sesión observada duró una hora.", Phase.INTERVENCION, 60),
                TaskEntry(LocalDate(2026, 9, 7), 2.0, "Dos episodios breves.", Phase.INTERVENCION, 45),
                TaskEntry(LocalDate(2026, 9, 8), 2.0, "Mejor regulación durante la clase.", Phase.INTERVENCION, 60),
            )),
        ),
    )
    // Actividades demostrativas de cada medición: aparecen desde el primer inicio para que
    // terapeuta y consultante puedan recorrer el flujo completo y ver series temporales.
    val intensityWidget = presetWidgetCatalog().first { it.id == "w-escala" }
    anxiety.tasks.add(
        TrackTask("t-intensity-demo", anxiety.id, "Medir intensidad de ansiedad", "Después de cada clase, indica la intensidad percibida de ansiedad (1 = mínima, 10 = máxima).", TaskType.MEDICION_CONFIGURABLE, Dimension.INTENSIDAD, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.COMPLETADA, widgetConfig = intensityWidget, therapeuticGoal = "Observar si la ansiedad disminuye con la práctica de regulación.", widgetEntries = listOf(
            WidgetEntry("we-i1", "t-intensity-demo", intensityWidget.id, LocalDate(2026, 9, 7), LocalTime(16, 0), WidgetValue.Numeric(9.0), "Ansiedad alta antes de la exposición."),
            WidgetEntry("we-i2", "t-intensity-demo", intensityWidget.id, LocalDate(2026, 9, 8), LocalTime(16, 0), WidgetValue.Numeric(8.0), null),
            WidgetEntry("we-i3", "t-intensity-demo", intensityWidget.id, LocalDate(2026, 9, 9), LocalTime(16, 0), WidgetValue.Numeric(6.0), "Usé respiración diafragmática."),
            WidgetEntry("we-i4", "t-intensity-demo", intensityWidget.id, LocalDate(2026, 9, 10), LocalTime(16, 0), WidgetValue.Numeric(4.0), "Pude participar con menos malestar."),
        )),
    )
    val regulation = Behavior(
        "b-ana-regulation", "case-ana", "Regular la ansiedad durante la exposición",
        "Usar una estrategia de respiración y continuar la exposición sin abandonar la actividad.",
        BehaviorType.ADAPTATIVA, false,
        baselineEntries = listOf(
            BehaviorRecord("r-rb1", "t-regulation-base", "b-ana-regulation", LocalDate(2026, 9, 1), 2.0, "Participó 2 veces sin estrategia definida.", Phase.LINEA_BASE, Dimension.FRECUENCIA),
            BehaviorRecord("r-rb2", "t-regulation-base", "b-ana-regulation", LocalDate(2026, 9, 2), 1.0, "Participó una vez.", Phase.LINEA_BASE, Dimension.FRECUENCIA),
            BehaviorRecord("r-rb3", "t-regulation-base", "b-ana-regulation", LocalDate(2026, 9, 3), 2.0, "Abandonó la exposición después de dos intentos.", Phase.LINEA_BASE, Dimension.FRECUENCIA),
            BehaviorRecord("r-rb4", "t-regulation-base", "b-ana-regulation", LocalDate(2026, 9, 4), 1.0, "Necesitó retirarse del salón.", Phase.LINEA_BASE, Dimension.FRECUENCIA),
        ),
        tasks = listOf(
            TrackTask("t-regulation-intervention", "b-ana-regulation", "Registrar participaciones usando respiración", "Después de cada clase, cuenta cuántas veces participaste y lograste permanecer en la actividad usando respiración.", TaskType.REGISTRO_AUTONOMO, Dimension.FRECUENCIA, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.COMPLETADA, entries = listOf(
                TaskEntry(LocalDate(2026, 9, 5), 2.0, "Usé respiración antes de responder.", Phase.INTERVENCION),
                TaskEntry(LocalDate(2026, 9, 6), 3.0, "Permanecí toda la clase.", Phase.INTERVENCION),
                TaskEntry(LocalDate(2026, 9, 7), 4.0, "Participé sin abandonar el salón.", Phase.INTERVENCION),
                TaskEntry(LocalDate(2026, 9, 8), 5.0, "Me sentí capaz de continuar pese a la ansiedad.", Phase.INTERVENCION),
            )),
        ),
    )
    val durationWidget = presetWidgetCatalog().first { it.id == "w-duracion" }
    anxiety.tasks.add(
        TrackTask("t-duration-demo", anxiety.id, "Registrar duración de la crisis", "Anota cuántos minutos duró cada episodio de ansiedad.", TaskType.MEDICION_CONFIGURABLE, Dimension.DURACION, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.COMPLETADA, widgetConfig = durationWidget, therapeuticGoal = "Reducir el tiempo de recuperación después de un episodio.", widgetEntries = listOf(
            WidgetEntry("we-d1", "t-duration-demo", durationWidget.id, LocalDate(2026, 9, 7), LocalTime(17, 0), WidgetValue.Numeric(30.0), null),
            WidgetEntry("we-d2", "t-duration-demo", durationWidget.id, LocalDate(2026, 9, 8), LocalTime(17, 0), WidgetValue.Numeric(22.0), null),
            WidgetEntry("we-d3", "t-duration-demo", durationWidget.id, LocalDate(2026, 9, 9), LocalTime(17, 0), WidgetValue.Numeric(12.0), "La crisis terminó más rápido."),
        )),
    )
    val complianceWidget = presetWidgetCatalog().first { it.id == "w-cumplimiento" }
    anxiety.tasks.add(
        TrackTask("t-compliance-demo", anxiety.id, "Cumplir la estrategia de respiración", "Registra qué porcentaje de las veces realizaste la estrategia acordada.", TaskType.MEDICION_CONFIGURABLE, Dimension.FRECUENCIA, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.COMPLETADA, widgetConfig = complianceWidget, therapeuticGoal = "Conocer la adherencia a la estrategia terapéutica.", widgetEntries = listOf(
            WidgetEntry("we-c1", "t-compliance-demo", complianceWidget.id, LocalDate(2026, 9, 7), LocalTime(20, 0), WidgetValue.Numeric(40.0), null),
            WidgetEntry("we-c2", "t-compliance-demo", complianceWidget.id, LocalDate(2026, 9, 8), LocalTime(20, 0), WidgetValue.Numeric(65.0), null),
            WidgetEntry("we-c3", "t-compliance-demo", complianceWidget.id, LocalDate(2026, 9, 9), LocalTime(20, 0), WidgetValue.Numeric(90.0), "La estrategia ya forma parte de mi rutina."),
        )),
    )
    val emotionalControlWidget = WidgetConfig("cw-control-emocional-demo", "Control emocional", WidgetValueKind.NUMERIC, unit = "/7", minValue = 1.0, maxValue = 7.0, description = "Ejemplo personalizado: 1 = poco control y 7 = alto control.")
    anxiety.tasks.add(
        TrackTask("t-custom-demo", anxiety.id, "Valorar control emocional", "Al finalizar el día, califica tu control emocional de 1 (poco) a 7 (alto).", TaskType.MEDICION_CONFIGURABLE, Dimension.INTENSIDAD, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.COMPLETADA, widgetConfig = emotionalControlWidget, therapeuticGoal = "Personalizar la evaluación cuando una escala estándar no describe el caso.", widgetEntries = listOf(
            WidgetEntry("we-e1", "t-custom-demo", emotionalControlWidget.id, LocalDate(2026, 9, 7), LocalTime(21, 0), WidgetValue.Numeric(2.0), null),
            WidgetEntry("we-e2", "t-custom-demo", emotionalControlWidget.id, LocalDate(2026, 9, 8), LocalTime(21, 0), WidgetValue.Numeric(4.0), null),
            WidgetEntry("we-e3", "t-custom-demo", emotionalControlWidget.id, LocalDate(2026, 9, 9), LocalTime(21, 0), WidgetValue.Numeric(6.0), "Pude identificar y regular mis emociones."),
        )),
    )
    val participation = Behavior(
        "b-ana-participation", "case-ana", "Participar en clase a pesar de la ansiedad",
        "Intervenciones o participaciones realizadas durante la clase aunque exista ansiedad.",
        BehaviorType.ADAPTATIVA, false,
        baselineEntries = listOf(
            BehaviorRecord("r-p1", "t-p-base", "b-ana-participation", LocalDate(2026, 9, 1), 1.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA, 45),
            BehaviorRecord("r-p2", "t-p-base", "b-ana-participation", LocalDate(2026, 9, 2), 2.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA, 45),
            BehaviorRecord("r-p3", "t-p-base", "b-ana-participation", LocalDate(2026, 9, 3), 1.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA, 45),
        ),
        tasks = listOf(
            TrackTask("t3", "b-ana-participation", "Registrar participaciones en clase", "Cada día, cuenta cuántas veces participaste en clase aunque sintieras ansiedad.", TaskType.REGISTRO_AUTONOMO, Dimension.FRECUENCIA, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.PENDIENTE),
        ),
    )
    val sleep = Behavior("b-dan-sleep", "case-daniel", "Minutos para conciliar el sueño", "Tiempo entre apagar la luz y quedarse dormido.", BehaviorType.DESADAPTATIVA, true)
    sleep.tasks.add(TrackTask("d1", sleep.id, "Tiempo que tardaste en dormirte", "Cada mañana estima cuántos minutos pasaron desde que apagaste la luz hasta quedarte dormido.", TaskType.REGISTRO_AUTONOMO, Dimension.DURACION, null, "Hoy", true, "Diaria", null, false, Phase.LINEA_BASE, TaskStatus.PENDIENTE))
    return listOf(
        Patient(
            "ana", "Ana Torres", 20, "Psicología · 4.º sem", 0xFF7B66C2L, "Ansiedad social",
            SingleCase("case-ana", "ana", "Caso de ansiedad social", listOf(anxiety, participation, regulation)),
            diagnoses = listOf(Cie10Diagnosis("F40.1", "Fobia social")),
        ),
        Patient(
            "daniel", "Daniel Ruiz", 23, "Ing. Industrial · 8.º sem", 0xFFFF6B56L, "Insomnio de conciliación",
            SingleCase("case-daniel", "daniel", "Caso de insomnio", listOf(sleep)),
            diagnoses = listOf(Cie10Diagnosis("F51.0", "Insomnio no orgánico")),
        ),
    )
}

// Catálogo semilla: el terapeuta puede agregar funciones nuevas desde el formulario de conducta
// cuando el análisis funcional del caso lo requiera.
private fun seedBehavioralFunctions(): List<BehavioralFunctionOption> = listOf(
    BehavioralFunctionOption(NO_APLICA_FUNCTION_ID, "No aplica"),
    BehavioralFunctionOption("fn-atencion", "Atención"),
    BehavioralFunctionOption("fn-escape", "Escape / evitación"),
    BehavioralFunctionOption("fn-tangible", "Tangible"),
    BehavioralFunctionOption("fn-sensorial", "Sensorial"),
    BehavioralFunctionOption("fn-comunicacion", "Comunicación"),
)

// Catálogo de apoyo, no exhaustivo: códigos CIE-10 (cap. V, OMS — trastornos mentales y del
// comportamiento) frecuentes en consulta psicológica universitaria. El terapeuta puede asignar
// cualquier otro código a mano si el caso no está cubierto por esta lista.
private fun seedCie10(): List<Cie10Diagnosis> = listOf(
    Cie10Diagnosis("F41.1", "Trastorno de ansiedad generalizada"),
    Cie10Diagnosis("F41.0", "Trastorno de pánico"),
    Cie10Diagnosis("F40.1", "Fobia social"),
    Cie10Diagnosis("F43.1", "Trastorno de estrés postraumático"),
    Cie10Diagnosis("F32.9", "Episodio depresivo, no especificado"),
    Cie10Diagnosis("F51.0", "Insomnio no orgánico"),
    Cie10Diagnosis("F90.0", "Trastorno de la actividad y la atención"),
    Cie10Diagnosis("F84.0", "Autismo infantil"),
)
