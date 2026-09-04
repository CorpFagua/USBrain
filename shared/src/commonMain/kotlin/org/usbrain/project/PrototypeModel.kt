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

enum class TaskStatus { PENDIENTE, COMPLETADA }

enum class Screen {
    LOGIN, TWO_FACTOR,
    PATIENT_HOME, PATIENT_TASK, PATIENT_DONE, PATIENT_PROGRESS, PATIENT_PROFILE,
    THERAPIST_HOME, THERAPIST_PATIENT, THERAPIST_NEW_TASK, THERAPIST_ASSIGNED, THERAPIST_PROGRESS,
}

data class TaskEntry(val date: String, val value: Double, val note: String)

class TrackTask(
    val id: String,
    val title: String,
    val instructions: String,
    val dimension: Dimension,
    val cadence: String,
    val due: String,
    val phase: Phase,
    status: TaskStatus,
    val feedsSeries: Boolean,
    entries: List<TaskEntry> = emptyList(),
) {
    var status by mutableStateOf(status)
    val entries = entries.toMutableStateList()
}

class Patient(
    val id: String,
    val name: String,
    val age: Int,
    val program: String,
    val colorArgb: Long,
    val condition: String,
    behavior: String,
    dimension: Dimension,
    phase: Phase,
    val baselineDays: Int,
    series: List<Double>,
    tasks: List<TrackTask>,
) {
    var behavior by mutableStateOf(behavior)
    var dimension by mutableStateOf(dimension)
    var phase by mutableStateOf(phase)
    val series = series.toMutableStateList()
    val tasks = tasks.toMutableStateList()

    val firstName: String get() = name.substringBefore(' ')
    val initials: String
        get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

val CADENCES = listOf("Diario", "2 veces al día", "Cada sesión", "Semanal")

/** Estado en memoria del prototipo. Se recrea al "Reiniciar demo". */
class PrototypeState {
    var screen by mutableStateOf(Screen.LOGIN)
    var role by mutableStateOf<Role?>(null)
    var loginRole by mutableStateOf(Role.PATIENT)
    var meId by mutableStateOf("ana")
    var activePatientId by mutableStateOf("ana")
    var activeTaskId by mutableStateOf<String?>(null)

    var taskInputValue by mutableStateOf(0.0)
    var taskNote by mutableStateOf("")

    var draftPatientId by mutableStateOf("ana")
    var draftBehavior by mutableStateOf("")
    var draftDimension by mutableStateOf(Dimension.FRECUENCIA)
    var draftCadence by mutableStateOf(CADENCES.first())
    var draftPhase by mutableStateOf(Phase.INTERVENCION)
    var draftDue by mutableStateOf("")
    var draftInstructions by mutableStateOf("")
    var assignError by mutableStateOf<String?>(null)

    private var nextId = 1

    val patients = samplePatients().toMutableStateList()

    fun patient(id: String): Patient = patients.first { it.id == id }
    fun me(): Patient = patient(meId)
    fun activePatient(): Patient = patient(activePatientId)
    fun activeTask(): TrackTask = me().tasks.first { it.id == activeTaskId }

    fun verify() {
        role = loginRole
        if (loginRole == Role.PATIENT) {
            meId = "ana"; activePatientId = "ana"; screen = Screen.PATIENT_HOME
        } else {
            screen = Screen.THERAPIST_HOME
        }
    }

    fun logout() {
        role = null
        screen = Screen.LOGIN
    }

    fun jumpTo(target: Role) {
        role = target
        if (target == Role.PATIENT) {
            meId = "ana"; activePatientId = "ana"; screen = Screen.PATIENT_HOME
        } else {
            screen = Screen.THERAPIST_HOME
        }
    }

    fun openTask(id: String) {
        activeTaskId = id
        taskInputValue = 0.0
        taskNote = ""
        screen = Screen.PATIENT_TASK
    }

    fun saveTask() {
        val t = activeTask()
        t.status = TaskStatus.COMPLETADA
        t.entries.add(TaskEntry("Hoy", taskInputValue, taskNote.trim()))
        if (t.feedsSeries) me().series.add(taskInputValue)
        screen = Screen.PATIENT_DONE
    }

    fun openPatient(id: String) {
        activePatientId = id
        screen = Screen.THERAPIST_PATIENT
    }

    fun startNewTask(patientId: String?) {
        draftPatientId = patientId ?: patients.first().id
        draftBehavior = ""
        draftDimension = Dimension.FRECUENCIA
        draftCadence = CADENCES.first()
        draftPhase = Phase.INTERVENCION
        draftDue = ""
        draftInstructions = ""
        assignError = null
        screen = Screen.THERAPIST_NEW_TASK
    }

    fun assign() {
        if (draftBehavior.isBlank() || draftInstructions.isBlank()) {
            assignError = "Completa la conducta y las indicaciones para el paciente."
            return
        }
        assignError = null
        val p = patient(draftPatientId)
        p.behavior = draftBehavior.trim()
        p.dimension = draftDimension
        p.phase = draftPhase
        val clean = draftBehavior.trim()
        p.tasks.add(
            TrackTask(
                id = "n${nextId++}",
                title = if (clean.length > 44) clean.take(42) + "…" else clean,
                instructions = draftInstructions.trim(),
                dimension = draftDimension,
                cadence = draftCadence,
                due = draftDue.trim().ifBlank { "Sin fecha definida" },
                phase = draftPhase,
                status = TaskStatus.PENDIENTE,
                feedsSeries = true,
            ),
        )
        screen = Screen.THERAPIST_ASSIGNED
    }

    fun previewAsPatient(id: String) {
        role = Role.PATIENT
        meId = id
        activePatientId = id
        screen = Screen.PATIENT_HOME
    }
}

private fun samplePatients(): List<Patient> = listOf(
    Patient(
        id = "ana", name = "Ana Torres", age = 20, program = "Psicología · 4.º sem",
        colorArgb = 0xFF7B66C2L, condition = "Ansiedad social",
        behavior = "Episodios de ansiedad en clase",
        dimension = Dimension.FRECUENCIA, phase = Phase.INTERVENCION, baselineDays = 7,
        series = listOf(5.0, 6.0, 5.0, 7.0, 6.0, 6.0, 5.0, 4.0, 4.0, 3.0, 4.0, 3.0),
        tasks = listOf(
            TrackTask(
                "t1", "Registrar episodios de ansiedad del día",
                "Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado durante las clases.",
                Dimension.FRECUENCIA, "Diario", "Hoy, 21:00", Phase.INTERVENCION, TaskStatus.PENDIENTE, true,
            ),
            TrackTask(
                "t2", "Escala de malestar antes de exponer en clase",
                "Justo antes de tu presentación, registra tu nivel de malestar de 0 (nada) a 10 (máximo).",
                Dimension.INTENSIDAD, "Cada sesión", "Mié 3 sep", Phase.INTERVENCION, TaskStatus.PENDIENTE, false,
            ),
            TrackTask(
                "t0", "Registrar episodios de ansiedad del día",
                "Al terminar el día, anota cuántas veces sentiste un episodio de ansiedad marcado.",
                Dimension.FRECUENCIA, "Diario", "Ayer, 21:00", Phase.INTERVENCION, TaskStatus.COMPLETADA, true,
                listOf(TaskEntry("Ayer", 3.0, "Solo en la clase de la tarde.")),
            ),
        ),
    ),
    Patient(
        id = "daniel", name = "Daniel Ruiz", age = 23, program = "Ing. Industrial · 8.º sem",
        colorArgb = 0xFFFF6B56L, condition = "Insomnio de conciliación",
        behavior = "Minutos para conciliar el sueño",
        dimension = Dimension.DURACION, phase = Phase.LINEA_BASE, baselineDays = 14,
        series = listOf(52.0, 60.0, 45.0, 58.0, 50.0, 63.0, 48.0, 55.0),
        tasks = listOf(
            TrackTask(
                "d1", "Tiempo que tardaste en dormirte",
                "Cada mañana estima cuántos minutos pasaron desde que apagaste la luz hasta quedarte dormido.",
                Dimension.DURACION, "Diario", "Mañana, 08:00", Phase.LINEA_BASE, TaskStatus.PENDIENTE, true,
            ),
        ),
    ),
)
