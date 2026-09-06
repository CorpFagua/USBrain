package org.usbrain.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList

enum class Role { PATIENT, THERAPIST }

enum class Dimension(val label: String, val unit: String, val hint: String) {
    FRECUENCIA("Frecuencia", "episodios", "¿Cuántas veces ocurrió hoy la conducta?"),
    DURACION("Duración", "minutos", "¿Cuánto duró cada ocurrencia?"),
    INTENSIDAD("Intensidad", "/ 10", "¿Qué tan intensa fue? Escala de 0 a 10"),
}

enum class Phase(val label: String) { LINEA_BASE("Línea base"), INTERVENCION("Intervención") }
enum class BehaviorType(val label: String) { ADAPTATIVA("Adaptativa"), DESADAPTATIVA("Desadaptativa") }
enum class TaskStatus { PENDIENTE, COMPLETADA }

// Solo aplica a conductas desadaptativas; NO_APLICA es el valor por defecto sin análisis funcional.
enum class BehavioralFunction(val label: String) {
    ATENCION("Atención"),
    ESCAPE_EVITACION("Escape / evitación"),
    TANGIBLE("Tangible"),
    SENSORIAL("Sensorial"),
    COMUNICACION("Comunicación"),
    NO_APLICA("No aplica"),
}

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
    PATIENT_HOME, PATIENT_TASK, PATIENT_DONE, PATIENT_PROGRESS, PATIENT_PROFILE,
    THERAPIST_HOME, THERAPIST_PATIENT, THERAPIST_NEW_BEHAVIOR, THERAPIST_NEW_TASK,
    THERAPIST_ASSIGNED, THERAPIST_PROGRESS,
}

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
    behavioralFunction: BehavioralFunction = BehavioralFunction.NO_APLICA,
    replacementBehaviorId: String? = null,
    baselineEntries: List<BehaviorRecord> = emptyList(),
    tasks: List<TrackTask> = emptyList(),
) {
    var baselineOpen by mutableStateOf(baselineOpenInitially)
    var baselineClosedOn by mutableStateOf<String?>(if (baselineOpenInitially) null else "Demo")
    // Mutable para permitir enlazar la conducta de reemplazo más adelante, aunque este formulario no se reabra.
    var behavioralFunction by mutableStateOf(behavioralFunction)
    var replacementBehaviorId by mutableStateOf(replacementBehaviorId)
    val baselineEntries = baselineEntries.toMutableStateList()
    val tasks = tasks.toMutableStateList()

    // El tipo de medición ya no se fija en la conducta: se elige por tarea de registro autónomo.
    val dimension: Dimension
        get() = tasks.firstOrNull { it.taskType == TaskType.REGISTRO_AUTONOMO }?.dimension
            ?: baselineEntries.firstOrNull()?.dimension
            ?: Dimension.FRECUENCIA

    val records: List<BehaviorRecord>
        get() = tasks.filter { it.taskType == TaskType.REGISTRO_AUTONOMO }.flatMap { task -> task.entries.filter { it.phase == Phase.INTERVENCION }.map { entry ->
            BehaviorRecord("${task.id}-${entry.date}", task.id, id, entry.date, entry.value, entry.note, entry.phase, task.dimension)
        } } + baselineEntries

    val observationCount: Int get() = baselineEntries.size
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

class Patient(
    val id: String,
    val name: String,
    val age: Int,
    val program: String,
    val colorArgb: Long,
    val condition: String,
    val case: SingleCase,
) {
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

    var taskInputValue by mutableStateOf(0.0)
    var taskNote by mutableStateOf("")

    var draftPatientId by mutableStateOf("ana")
    var draftBehaviorId by mutableStateOf<String?>(null)
    var draftBehaviorName by mutableStateOf("")
    var draftDefinition by mutableStateOf("")
    var draftBehaviorType by mutableStateOf(BehaviorType.DESADAPTATIVA)
    var draftCategoryId by mutableStateOf<String?>(null)
    var draftBehavioralFunction by mutableStateOf(BehavioralFunction.NO_APLICA)
    var draftReplacementBehaviorId by mutableStateOf<String?>(null)
    var draftNewCategoryName by mutableStateOf("")
    var draftNewCategoryDomain by mutableStateOf("")
    var showNewCategoryForm by mutableStateOf(false)
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
        if (loginRole == Role.PATIENT) {
            meId = "ana"; activePatientId = "ana"; screen = Screen.PATIENT_HOME
        } else screen = Screen.THERAPIST_HOME
    }

    fun logout() { role = null; screen = Screen.LOGIN }

    fun jumpTo(target: Role) {
        role = target
        if (target == Role.PATIENT) {
            meId = "ana"; activePatientId = "ana"; screen = Screen.PATIENT_HOME
        } else screen = Screen.THERAPIST_HOME
    }

    fun openTask(id: String, behaviorId: String? = null) {
        activeTaskId = id
        activeBehaviorId = behaviorId ?: activePatient().behaviors.first { behavior -> behavior.tasks.any { it.id == id } }.id
        val task = activeTask()
        // Solo completado no pide un valor numérico: se guarda como "cumplida" (1.0).
        taskInputValue = if (task.taskType != TaskType.REGISTRO_AUTONOMO && task.metricUnit == MetricUnit.SOLO_COMPLETADO) 1.0 else 0.0
        taskNote = ""
        screen = Screen.PATIENT_TASK
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
        draftBehavioralFunction = BehavioralFunction.NO_APLICA
        draftReplacementBehaviorId = null
        draftNewCategoryName = ""
        draftNewCategoryDomain = ""
        showNewCategoryForm = false
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
                behavioralFunction = if (isMaladaptive) draftBehavioralFunction else BehavioralFunction.NO_APLICA,
                replacementBehaviorId = if (isMaladaptive) draftReplacementBehaviorId else null,
            )
        )
        activePatientId = patient.id
        activeBehaviorId = id
        screen = Screen.THERAPIST_PATIENT
    }

    fun openPatient(id: String) { activePatientId = id; screen = Screen.THERAPIST_PATIENT }
    fun openBehavior(id: String) { activeBehaviorId = id; screen = Screen.THERAPIST_PATIENT }

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
        Patient("ana", "Ana Torres", 20, "Psicología · 4.º sem", 0xFF7B66C2L, "Ansiedad social", SingleCase("case-ana", "ana", "Caso de ansiedad social", listOf(anxiety, participation))),
        Patient("daniel", "Daniel Ruiz", 23, "Ing. Industrial · 8.º sem", 0xFFFF6B56L, "Insomnio de conciliación", SingleCase("case-daniel", "daniel", "Caso de insomnio", listOf(sleep))),
    )
}
