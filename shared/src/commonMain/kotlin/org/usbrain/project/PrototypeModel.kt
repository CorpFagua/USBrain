package org.usbrain.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList

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
// Catálogo dinámico (mismo patrón que BehaviorCategory más abajo): el terapeuta puede registrar
// una función conductual nueva si el catálogo semilla no cubre el caso, sin tocar código.
data class BehavioralFunctionOption(val id: String, val label: String)
const val NO_APLICA_FUNCTION_ID = "fn-no-aplica"

enum class TaskType(val label: String) {
    REGISTRO_AUTONOMO("Registro autónomo"),
    EJERCICIO_PRACTICA("Ejercicio / práctica"),
    PSICOEDUCATIVA("Psicoeducativa"),
    CONDUCTUAL("Conductual"),
}

// Solo aplica cuando el tipo de actividad no es registro autónomo (ese usa la dimensión de la conducta).
enum class MetricUnit(val label: String) {
    SOLO_COMPLETADO("Solo completado (sí/no)"),
    MINUTOS("Minutos"),
    REPETICIONES("Repeticiones"),
    ESCALA_1_5("Escala 1–5"),
}

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
    THERAPIST_NEW_BEHAVIOR, THERAPIST_NEW_TASK, THERAPIST_ASSIGNED, THERAPIST_PROFILE,
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

data class TaskEntry(val date: String, val value: Double, val note: String, val phase: Phase)

// Catálogo global (CATEGORIA_CONDUCTA): la naturaleza no vive aquí, es solo el ejemplo típico del catálogo semilla.
data class BehaviorCategory(val id: String, val name: String, val domain: String)

data class BehaviorRecord(
    val id: String,
    val taskId: String,
    val behaviorId: String,
    val date: String,
    val value: Double,
    val note: String,
    val phase: Phase,
    val dimension: Dimension,
)

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
) {
    var status by mutableStateOf(status)
    val entries = entries.toMutableStateList()
    // Solo el registro autónomo mide la conducta; el resto tiene su propia métrica y no alimenta la serie.
    val feedsSeries: Boolean get() = taskType == TaskType.REGISTRO_AUTONOMO
}

class Behavior(
    val id: String,
    val caseId: String,
    val name: String,
    val operationalDefinition: String,
    val type: BehaviorType,
    val categoryId: String,
    baselineOpenInitially: Boolean,
    behavioralFunctionId: String = NO_APLICA_FUNCTION_ID,
    replacementBehaviorId: String? = null,
    baselineEntries: List<BehaviorRecord> = emptyList(),
    tasks: List<TrackTask> = emptyList(),
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

    // El tipo de medición ya no se fija en la conducta: se elige por tarea de registro autónomo.
    // Se conserva como "dimensión por defecto" (la primera declarada) para vistas que solo
    // necesitan un rótulo rápido (tarjetas, badges); la gráfica real usa `dimensions`/`recordsFor`.
    val dimension: Dimension
        get() = tasks.firstOrNull { it.taskType == TaskType.REGISTRO_AUTONOMO }?.dimension
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
            val fromTasks = tasks.filter { it.taskType == TaskType.REGISTRO_AUTONOMO }.map { it.dimension }
            val fromBaseline = baselineEntries.map { it.dimension }
            return (fromTasks + fromBaseline).distinct().ifEmpty { listOf(Dimension.FRECUENCIA) }
        }

    val records: List<BehaviorRecord>
        get() = tasks.filter { it.taskType == TaskType.REGISTRO_AUTONOMO }.flatMap { task -> task.entries.filter { it.phase == Phase.INTERVENCION }.map { entry ->
            BehaviorRecord("${task.id}-${entry.date}", task.id, id, entry.date, entry.value, entry.note, entry.phase, task.dimension)
        } } + baselineEntries

    // Filtra la serie a una sola dimensión: es lo que realmente debe alimentar una gráfica,
    // para no mezclar, por ejemplo, "episodios" con "minutos" en la misma línea de tendencia.
    fun recordsFor(dimension: Dimension): List<BehaviorRecord> = records.filter { it.dimension == dimension }

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
    var taskNote by mutableStateOf("")

    var draftPatientId by mutableStateOf("ana")
    var draftBehaviorId by mutableStateOf<String?>(null)
    var draftBehaviorName by mutableStateOf("")
    var draftDefinition by mutableStateOf("")
    var draftBehaviorType by mutableStateOf(BehaviorType.DESADAPTATIVA)
    var draftCategoryId by mutableStateOf<String?>(null)
    var draftBehavioralFunctionId by mutableStateOf(NO_APLICA_FUNCTION_ID)
    var draftReplacementBehaviorId by mutableStateOf<String?>(null)
    var draftNewCategoryName by mutableStateOf("")
    var draftNewCategoryDomain by mutableStateOf("")
    var showNewCategoryForm by mutableStateOf(false)
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
    var draftIsRecurring by mutableStateOf(true)
    var draftFrequency by mutableStateOf(FRECUENCIAS.first())
    var draftDueDate by mutableStateOf("")
    var draftReminderActive by mutableStateOf(false)
    var draftInstructions by mutableStateOf("")
    var assignError by mutableStateOf<String?>(null)
    var behaviorError by mutableStateOf<String?>(null)

    private var nextId = 10
    val patients = samplePatients().toMutableStateList()
    val categories = seedCategories().toMutableStateList()
    // Mismo espíritu que `categories`: catálogo semilla editable, no un enum cerrado, para poder
    // registrar una función conductual nueva cuando el análisis funcional del caso lo requiera.
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

    fun createCategory(name: String, domain: String): String? {
        if (name.isBlank()) return null
        val id = "cat${nextId++}"
        categories.add(BehaviorCategory(id, name.trim(), domain.trim()))
        return id
    }

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
        // Solo completado no pide un valor numérico: se guarda como "cumplida" (1.0).
        taskInputValue = if (task.taskType != TaskType.REGISTRO_AUTONOMO && task.metricUnit == MetricUnit.SOLO_COMPLETADO) 1.0 else 0.0
        taskNote = ""
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

    fun saveTask() {
        val behavior = activeBehavior()
        val task = activeTask()
        val entry = TaskEntry("Hoy", taskInputValue, taskNote.trim(), task.phase)
        task.status = TaskStatus.COMPLETADA
        task.entries.add(entry)
        if (task.taskType == TaskType.REGISTRO_AUTONOMO && task.phase == Phase.LINEA_BASE && behavior.baselineOpen) {
            behavior.baselineEntries.add(BehaviorRecord("r${nextId++}", task.id, behavior.id, entry.date, entry.value, entry.note, Phase.LINEA_BASE, task.dimension))
        }
        screen = Screen.PATIENT_DONE
    }

    fun startNewBehavior(patientId: String) {
        // Siempre se entra desde la ficha de un paciente: no hay atajo global que permita crear sin ese contexto.
        draftPatientId = patientId
        draftBehaviorName = ""
        draftDefinition = ""
        draftBehaviorType = BehaviorType.DESADAPTATIVA
        draftCategoryId = null
        draftBehavioralFunctionId = NO_APLICA_FUNCTION_ID
        draftReplacementBehaviorId = null
        draftNewCategoryName = ""
        draftNewCategoryDomain = ""
        showNewCategoryForm = false
        draftNewFunctionLabel = ""
        showNewFunctionForm = false
        behaviorError = null
        screen = Screen.THERAPIST_NEW_BEHAVIOR
    }

    fun saveBehavior() {
        if (draftBehaviorName.isBlank() || draftDefinition.isBlank() || draftCategoryId == null) {
            behaviorError = "Completa el nombre, la definición operacional y la categoría de la conducta."
            return
        }
        val patient = patient(draftPatientId)
        val id = "b${nextId++}"
        val isMaladaptive = draftBehaviorType == BehaviorType.DESADAPTATIVA
        patient.case.behaviorList.add(
            Behavior(
                id, patient.case.id, draftBehaviorName.trim(), draftDefinition.trim(),
                draftBehaviorType, draftCategoryId!!, true,
                behavioralFunctionId = if (isMaladaptive) draftBehavioralFunctionId else NO_APLICA_FUNCTION_ID,
                replacementBehaviorId = if (isMaladaptive) draftReplacementBehaviorId else null,
            )
        )
        activePatientId = patient.id
        activeBehaviorId = id
        behaviorDetailTab = BehaviorTab.ACTIVIDADES
        // Directo al detalle de la conducta recién creada: es donde ahora vive "Nueva actividad".
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
        draftIsRecurring = true
        draftFrequency = FRECUENCIAS.first()
        draftDueDate = ""
        draftReminderActive = suggestedPhase == Phase.INTERVENCION
        draftInstructions = ""
        assignError = null
        screen = Screen.THERAPIST_NEW_TASK
    }

    fun assign() {
        val selected = draftBehaviorId?.let { runCatching { behavior(it) }.getOrNull() }
        if (selected == null || draftTaskTitle.isBlank()) {
            assignError = "Completa el nombre de la actividad."
            return
        }
        selected.tasks.add(
            TrackTask(
                "t${nextId++}", selected.id, draftTaskTitle.trim(), draftInstructions.trim(),
                draftTaskType, draftTaskDimension,
                metricUnit = if (draftTaskType == TaskType.REGISTRO_AUTONOMO) null else draftMetricUnit,
                assignedDate = draftAssignedDate.trim().ifBlank { "Hoy" },
                isRecurring = draftIsRecurring,
                frequency = if (draftIsRecurring) draftFrequency else null,
                dueDate = draftDueDate.trim().ifBlank { null },
                reminderActive = draftReminderActive,
                phase = draftTaskPhase,
                status = TaskStatus.PENDIENTE,
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

// Catálogo semilla sugerido para el perfil de pacientes con TAS; el profesional puede ampliarlo.
private fun seedCategories(): List<BehaviorCategory> = listOf(
    BehaviorCategory("cat-autolesion", "Autolesión", "Autorregulación emocional"),
    BehaviorCategory("cat-agresion", "Agresión física o verbal", "Interacción social"),
    BehaviorCategory("cat-estereotipia", "Estereotipia motora", "Regulación sensorial"),
    BehaviorCategory("cat-fuga", "Fuga / elopement", "Seguridad"),
    BehaviorCategory("cat-rabieta", "Rabieta / desregulación", "Autorregulación emocional"),
    BehaviorCategory("cat-comunicacion", "Comunicación funcional", "Comunicación"),
    BehaviorCategory("cat-habilidades-sociales", "Habilidades sociales", "Interacción social"),
    BehaviorCategory("cat-autocuidado", "Autocuidado (AVD)", "Independencia funcional"),
    BehaviorCategory("cat-atencion-academica", "Atención y tarea académica", "Desempeño académico"),
)

private fun samplePatients(): List<Patient> {
    val anxiety = Behavior(
        "b-ana-anxiety", "case-ana", "Episodios de ansiedad en clase",
        "Episodios observables de ansiedad durante actividades académicas.", BehaviorType.DESADAPTATIVA, "cat-rabieta", false,
        baselineEntries = listOf(
            BehaviorRecord("r-a1", "t-a-base", "b-ana-anxiety", "Día 1", 5.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA),
            BehaviorRecord("r-a2", "t-a-base", "b-ana-anxiety", "Día 2", 6.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA),
            BehaviorRecord("r-a3", "t-a-base", "b-ana-anxiety", "Día 3", 5.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA),
        ),
        tasks = listOf(
            TrackTask("t1", "b-ana-anxiety", "Registrar episodios de ansiedad del día", "Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado durante las clases.", TaskType.REGISTRO_AUTONOMO, Dimension.FRECUENCIA, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.PENDIENTE),
            TrackTask("t2", "b-ana-anxiety", "Escala de malestar antes de exponer en clase", "Justo antes de tu presentación, registra tu nivel de malestar de 0 (nada) a 10 (máximo).", TaskType.REGISTRO_AUTONOMO, Dimension.INTENSIDAD, null, "Hoy", true, "Personalizada", "Mié 3 sep", true, Phase.INTERVENCION, TaskStatus.PENDIENTE),
            TrackTask("t0", "b-ana-anxiety", "Registrar episodios de ansiedad del día", "Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado.", TaskType.REGISTRO_AUTONOMO, Dimension.FRECUENCIA, null, "Ayer", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.COMPLETADA, listOf(TaskEntry("Ayer", 3.0, "Solo en la clase de la tarde.", Phase.INTERVENCION))),
        ),
    )
    val participation = Behavior(
        "b-ana-participation", "case-ana", "Participar en clase a pesar de la ansiedad",
        "Intervenciones o participaciones realizadas durante la clase aunque exista ansiedad.",
        BehaviorType.ADAPTATIVA, "cat-habilidades-sociales", false,
        baselineEntries = listOf(
            BehaviorRecord("r-p1", "t-p-base", "b-ana-participation", "Día 1", 1.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA),
            BehaviorRecord("r-p2", "t-p-base", "b-ana-participation", "Día 2", 2.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA),
            BehaviorRecord("r-p3", "t-p-base", "b-ana-participation", "Día 3", 1.0, "", Phase.LINEA_BASE, Dimension.FRECUENCIA),
        ),
        tasks = listOf(
            TrackTask("t3", "b-ana-participation", "Registrar participaciones en clase", "Cada día, cuenta cuántas veces participaste en clase aunque sintieras ansiedad.", TaskType.REGISTRO_AUTONOMO, Dimension.FRECUENCIA, null, "Hoy", true, "Diaria", null, true, Phase.INTERVENCION, TaskStatus.PENDIENTE),
        ),
    )
    val sleep = Behavior("b-dan-sleep", "case-daniel", "Minutos para conciliar el sueño", "Tiempo entre apagar la luz y quedarse dormido.", BehaviorType.DESADAPTATIVA, "cat-rabieta", true)
    sleep.tasks.add(TrackTask("d1", sleep.id, "Tiempo que tardaste en dormirte", "Cada mañana estima cuántos minutos pasaron desde que apagaste la luz hasta quedarte dormido.", TaskType.REGISTRO_AUTONOMO, Dimension.DURACION, null, "Hoy", true, "Diaria", null, false, Phase.LINEA_BASE, TaskStatus.PENDIENTE))
    return listOf(
        Patient(
            "ana", "Ana Torres", 20, "Psicología · 4.º sem", 0xFF7B66C2L, "Ansiedad social",
            SingleCase("case-ana", "ana", "Caso de ansiedad social", listOf(anxiety, participation)),
            diagnoses = listOf(Cie10Diagnosis("F40.1", "Fobia social")),
        ),
        Patient(
            "daniel", "Daniel Ruiz", 23, "Ing. Industrial · 8.º sem", 0xFFFF6B56L, "Insomnio de conciliación",
            SingleCase("case-daniel", "daniel", "Caso de insomnio", listOf(sleep)),
            diagnoses = listOf(Cie10Diagnosis("F51.0", "Insomnio no orgánico")),
        ),
    )
}

// Catálogo semilla (mismo espíritu que seedCategories): el terapeuta puede agregar funciones
// nuevas desde el formulario de conducta cuando el análisis funcional del caso lo requiera.
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
