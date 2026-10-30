package org.usbrain.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.usbrain.project.data.AdminOverviewDto
import org.usbrain.project.data.AnalyticsDatasetDto
import org.usbrain.project.data.ApiException
import org.usbrain.project.data.Enrollment
import org.usbrain.project.data.SessionExpiredException
import org.usbrain.project.data.SessionUser
import org.usbrain.project.data.TokenResponse
import org.usbrain.project.data.UsBrainApi
import org.usbrain.project.security.AppLock
import org.usbrain.project.security.Biometrics
import org.usbrain.project.security.supportsAppLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// "Hoy" lo define el servidor (zona horaria de la sede) para que la app y la base coincidan
// en qué registros son de hoy; mientras no hay datos se usa el reloj del dispositivo.
private var serverToday: LocalDate? = null

@OptIn(ExperimentalTime::class)
fun todayDate(): LocalDate =
    serverToday ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

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

/** Segundos sin interacción antes del aviso de desconexión, y segundos para responder. */
const val IDLE_WARNING_SECONDS = 120
const val IDLE_COUNTDOWN_SECONDS = 60

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
 * (isPreset=true, catálogo en la tabla usb_widgets) o ser creada por el terapeuta desde cero
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

enum class Screen {
    // SPLASH: restaurando la sesión guardada al abrir la app. RECOVERY_CODES: se muestran
    // una sola vez, justo después de enrolar el autenticador.
    SPLASH, LOGIN, FIRST_PASSWORD_CHANGE, TWO_FACTOR, RECOVERY_CODES, FORGOT_PASSWORD,
    // Bloqueo estilo banco: crear el PIN, ofrecer biometría y la pantalla de desbloqueo.
    PIN_SETUP, BIOMETRIC_OFFER, LOCK,
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
    ADMIN_HOME, ADMIN_ANALYTICS, ADMIN_USERS, ADMIN_USER_EDIT, ADMIN_ROLES, ADMIN_ROLE_EDIT, ADMIN_AUDIT, ADMIN_PROFILE,
    // Desde el perfil de cualquier rol.
    CHANGE_PASSWORD,
}

data class PasswordAssessment(
    val score: Int,
    val label: String,
    val isValid: Boolean,
    val message: String?,
)

fun passwordCharacterCount(password: String): Int {
    var count = 0
    var index = 0
    while (index < password.length) {
        val character = password[index]
        if (character.code in 0xD800..0xDBFF &&
            index + 1 < password.length &&
            password[index + 1].code in 0xDC00..0xDFFF
        ) {
            index++
        }
        count++
        index++
    }
    return count
}

/** Los correos solo llevan letras, números, @ y punto (los crea la administración); el servidor valida lo mismo. */
fun sanitizeEmailInput(value: String): String =
    value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '@' || it == '.' }.take(254)

fun assessPassword(password: String, email: String, exactLength: Boolean = false): PasswordAssessment {
    if (password.isEmpty()) return PasswordAssessment(0, "Sin evaluar", false, null)
    val passwordLength = passwordCharacterCount(password)
    val characterGroups = listOf(
        password.any(Char::isLowerCase),
        password.any(Char::isUpperCase),
        password.any(Char::isDigit),
        password.any { !it.isLetterOrDigit() },
    ).count { it }
    val localPart = email.substringBefore('@')
    val containsEmailName = passwordCharacterCount(localPart) >= 4 &&
        password.contains(localPart, ignoreCase = true)
    val score = if (exactLength) {
        characterGroups.coerceAtMost(4)
    } else {
        (
            (if (passwordLength >= 10) 1 else 0) +
                (if (passwordLength >= 14) 1 else 0) +
                (if (characterGroups >= 3) 1 else 0) +
                (if (characterGroups == 4) 1 else 0)
            ).coerceAtMost(4)
    }
    val message = when {
        exactLength && passwordLength != 10 -> "La contraseña debe tener exactamente 10 caracteres."
        passwordLength < 10 -> "Usa al menos 10 caracteres."
        passwordLength > 128 -> "No puede superar 128 caracteres."
        characterGroups < 3 -> "Combina al menos 3: mayúsculas, minúsculas, números y símbolos."
        containsEmailName -> "No incluyas tu nombre de usuario del correo."
        else -> null
    }
    val label = when (score) {
        0, 1 -> "Débil"
        2 -> "Aceptable"
        3 -> "Buena"
        else -> "Fuerte"
    }
    return PasswordAssessment(score, label, message == null, message)
}

/** Pestañas dentro del detalle de una conducta: separan "gestionar actividades" de "ver la
 * evolución graficada" para no mezclar creación de datos con su análisis en la misma vista. */
enum class BehaviorTab { ACTIVIDADES, ANALISIS }

/** Un ítem de la barra de navegación inferior de un perfil. */
data class NavTab(val screen: Screen, val label: String, val glyph: String, val permission: String? = null)

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
            NavTab(Screen.ADMIN_ANALYTICS, "Análisis", "chart", permission = "analytics.view"),
            // Cada pestaña aparece solo si el rol tiene el permiso (roles dinámicos).
            NavTab(Screen.ADMIN_USERS, "Usuarios", "users", permission = "users.view"),
            NavTab(Screen.ADMIN_ROLES, "Roles", "shield", permission = "roles.manage"),
            NavTab(Screen.ADMIN_PROFILE, "Perfil", "person"),
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
    val therapistName: String = "",
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

/**
 * Estado de la app. Los datos vienen de la API (bootstrap) y cada acción de las pantallas
 * llama al servidor y luego recarga el bootstrap, así la vista siempre refleja la base.
 * Las pantallas siguen leyendo las mismas propiedades que en el prototipo.
 */
class PrototypeState(
    private val api: UsBrainApi,
    private val scope: CoroutineScope,
) {
    var screen by mutableStateOf(Screen.SPLASH)
    var role by mutableStateOf<Role?>(null)
    var meId by mutableStateOf("")
    var activePatientId by mutableStateOf("")
    var activeTaskId by mutableStateOf<String?>(null)
    var activeBehaviorId by mutableStateOf<String?>(null)
    // Pestaña activa del detalle de conducta; vive en el estado (no local a la pantalla) para
    // que el FAB de "nueva actividad" en el Scaffold superior sepa si debe mostrarse.
    var behaviorDetailTab by mutableStateOf(BehaviorTab.ACTIVIDADES)

    // --- Sesión y autenticación ---
    var sessionUser by mutableStateOf<SessionUser?>(null)
    var loginEmail by mutableStateOf("")
    var loginPassword by mutableStateOf("")
    var firstPassword by mutableStateOf("")
    var firstPasswordConfirmation by mutableStateOf("")
    // Cambio de contraseña desde el perfil.
    var changeCurrentPassword by mutableStateOf("")
    var changeNewPassword by mutableStateOf("")
    var changeConfirmPassword by mutableStateOf("")
    var changeCode by mutableStateOf("")
    var changeError by mutableStateOf<String?>(null)
    var changeInfo by mutableStateOf<String?>(null)
    private var changeReturnScreen: Screen? = null
    var authError by mutableStateOf<String?>(null)
    var challengeId by mutableStateOf<String?>(null)
    var enrollment by mutableStateOf<Enrollment?>(null)
    var mfaCode by mutableStateOf("")
    var useRecoveryCode by mutableStateOf(false)
    var recoveryCodes by mutableStateOf<List<String>>(emptyList())
    // Vista previa del terapeuta como paciente: solo lectura, con regreso a su rol.
    var previewing by mutableStateOf(false)

    // Operación en curso (deshabilita envíos dobles y muestra la barra de progreso).
    var busy by mutableStateOf(false)
    // Error general de una acción que no tiene un campo de error propio en su pantalla.
    var banner by mutableStateOf<String?>(null)
    var connectionError by mutableStateOf<String?>(null)
    // Mensaje de éxito en la pantalla de login (p. ej. "contraseña actualizada").
    var authInfo by mutableStateOf<String?>(null)

    // --- Permisos (roles dinámicos): la interfaz muestra acciones según permisos, no según el rol ---
    var permissions by mutableStateOf<Set<String>>(emptySet())
    fun can(permission: String) = permission in permissions
    val canEditCases: Boolean get() = can("case.edit.assigned") || can("case.edit.all")

    // --- Olvido de contraseña ---
    var forgotEmail by mutableStateOf("")
    var forgotResetId by mutableStateOf<String?>(null)
    var forgotInfo by mutableStateOf<String?>(null)
    var forgotCode by mutableStateOf("")
    var forgotUseRecovery by mutableStateOf(false)
    var forgotNewPassword by mutableStateOf("")
    var forgotConfirmPassword by mutableStateOf("")

    // --- PIN y biometría (bloqueo de la app) ---
    enum class PinStep { CURRENT, NEW, CONFIRM }
    var pinInput by mutableStateOf("")
    var pinStep by mutableStateOf(PinStep.NEW)
    var pinError by mutableStateOf<String?>(null)
    var pinChanging by mutableStateOf(false)
    var biometricEnabled by mutableStateOf(AppLock.biometricEnabled)
    val biometricAvailable: Boolean get() = Biometrics.isAvailable()
    private var pinFirst = ""
    private var pinReturnScreen: Screen? = null
    private var pendingUser: SessionUser? = null
    private var lockReturnScreen: Screen? = null
    private var lockNeedsRestore = false
    private var backgroundSince: Long? = null

    // --- Administración ---
    var adminData by mutableStateOf<AdminOverviewDto?>(null)
    var analyticsData by mutableStateOf<AnalyticsDatasetDto?>(null)
    var analyticsError by mutableStateOf<String?>(null)
    var adminError by mutableStateOf<String?>(null)
    var adminInfo by mutableStateOf<String?>(null)
    var editingUserId by mutableStateOf<String?>(null)
    var draftUserEmail by mutableStateOf("")
    var draftUserName by mutableStateOf("")
    var draftUserRole by mutableStateOf("")
    var draftUserStatus by mutableStateOf("ACTIVE")
    var draftUserPassword by mutableStateOf("")
    var draftUserTherapistId by mutableStateOf<String?>(null)
    var draftUserBirthDate by mutableStateOf("")
    var draftUserProgram by mutableStateOf("")
    var draftUserCondition by mutableStateOf("")
    var draftActionPassword by mutableStateOf("")
    // Permisos individuales del usuario en edición: permiso → GRANT | DENY (sin entrada = según su rol).
    val draftOverrides = mutableStateMapOf<String, String>()
    var draftOverrideReason by mutableStateOf("")
    var draftOverrideExpiry by mutableStateOf("")
    var editingRoleId by mutableStateOf<String?>(null)
    var draftRoleId by mutableStateOf("")
    var draftRoleName by mutableStateOf("")
    var draftRoleDescription by mutableStateOf("")
    var draftRoleProfile by mutableStateOf("THERAPIST")
    val draftRolePermissions = mutableStateListOf<String>()

    var taskInputValue by mutableStateOf(0.0)
    var taskObservationMinutes by mutableStateOf(0)
    var taskNote by mutableStateOf("")
    var taskEntryDate by mutableStateOf(todayDate())
    var taskEntryTime by mutableStateOf(LocalTime(9, 0))
    var taskError by mutableStateOf<String?>(null)

    var draftPatientId by mutableStateOf("")
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
    var diagnosisError by mutableStateOf<String?>(null)
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
    var lastAssignedTaskId by mutableStateOf<String?>(null)
    var lastSavedEntryDate by mutableStateOf<LocalDate?>(null)

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

    // --- Datos del servidor ---
    val patients = mutableStateListOf<Patient>()
    // Catálogo de mediciones: predefinidas + personalizadas creadas por los terapeutas.
    val widgets = mutableStateListOf<WidgetConfig>()
    fun widgetCatalog(): List<WidgetConfig> = widgets
    fun widget(id: String): WidgetConfig? = widgets.firstOrNull { it.id == id }
    // Catálogo editable de funciones conductuales (no un enum cerrado).
    val behavioralFunctions = mutableStateListOf<BehavioralFunctionOption>()
    // Catálogo de apoyo con códigos CIE-10 frecuentes; se puede asignar cualquier otro a mano.
    val cie10Catalog = mutableStateListOf<Cie10Diagnosis>()

    // Mutable a propósito: es el catálogo de roles/permisos del sistema, pensado para poder
    // registrar perfiles nuevos más adelante sin cambiar la navegación (ver [RoleProfile]).
    val roleProfiles = defaultRoleProfiles().toMutableStateList()
    fun roleProfile(role: Role): RoleProfile? = roleProfiles.firstOrNull { it.role == role }
    val currentProfile: RoleProfile? get() = role?.let { roleProfile(it) }

    val userName: String get() = sessionUser?.fullName.orEmpty()
    val userInitials: String
        get() = userName.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

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
    // Días seguidos con al menos un registro, contando hacia atrás desde hoy (o desde ayer si
    // hoy todavía no ha registrado nada, para no "romper" la racha a primera hora del día).
    fun streakDays(patient: Patient): Int {
        val days = patient.tasks.flatMap { task -> task.entries.map { it.date } + task.widgetEntries.map { it.date } }.toSet()
        var day = todayDate()
        if (day !in days) day = day.minus(1, DateTimeUnit.DAY)
        var streak = 0
        while (day in days) {
            streak++
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

    fun lastAssignedTask(): TrackTask? = lastAssignedTaskId?.let { id -> patients.flatMap { it.tasks }.firstOrNull { it.id == id } }

    // Funciones helper para las pantallas del paciente
    fun caseForPatient(patientId: String): String? = patient(patientId).case.id
    fun behaviorsForCase(caseId: String): List<Behavior> = patients.asSequence().flatMap { it.case.behaviorList.asSequence() }.filter { it.caseId == caseId }.toList()
    fun tasksForBehavior(behaviorId: String): List<TrackTask> = behavior(behaviorId).tasks.toList()
    fun recordsForBehavior(behaviorId: String): List<BehaviorRecord> = behavior(behaviorId).records
    fun progressForBehavior(behavior: Behavior): Behavior = behavior

    // ------------------------------------------------------------------ red

    /**
     * Ejecuta una acción contra la API: evita envíos dobles, y traduce los errores al
     * campo de la pantalla ([onError]) o, si la sesión terminó, devuelve al login.
     */
    private fun launch(onError: (String) -> Unit = { banner = it }, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        banner = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                endSession(e.message)
            } catch (e: ApiException) {
                onError(e.message)
            } catch (e: Exception) {
                onError("No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.")
            } finally {
                busy = false
            }
        }
    }

    /** Vuelve a pedir todos los datos del usuario y reemplaza los de la vista. */
    private suspend fun reload() {
        val data = api.bootstrap()
        serverToday = runCatching { LocalDate.parse(data.today) }.getOrNull()
        // Si el super admin cambió los permisos del rol, la interfaz se ajusta en la siguiente recarga.
        permissions = data.user.permissions.toSet()
        val widgetConfigs = data.catalogs.widgets.map { it.toWidgetConfig() }
        widgets.clear(); widgets.addAll(widgetConfigs)
        behavioralFunctions.clear()
        behavioralFunctions.addAll(data.catalogs.functions.map { BehavioralFunctionOption(it.id, it.label) })
        cie10Catalog.clear(); cie10Catalog.addAll(data.catalogs.cie10.map { Cie10Diagnosis(it.code, it.label) })
        patients.clear(); patients.addAll(data.toPatients(widgetConfigs))
    }

    // ------------------------------------------------------------------ sesión

    /** Al abrir la app: si hay sesión guardada se entra directo, como en la app de un banco. */
    fun start() {
        if (!api.hasStoredSession) {
            screen = Screen.LOGIN
            return
        }
        // Como en la app de un banco: la sesión guardada solo se usa después del PIN o la huella.
        if (supportsAppLock && AppLock.isConfigured) {
            lockNeedsRestore = true
            lockReturnScreen = null
            openLock()
            return
        }
        restoreSession()
    }

    private fun restoreSession() {
        screen = Screen.SPLASH
        connectionError = null
        scope.launch {
            try {
                val user = api.restoreSession()
                if (user == null) {
                    screen = Screen.LOGIN
                } else {
                    proceedAfterAuth(user)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                // La sesión guardada ya no vale: al login, diciendo por qué (p. ej. inactividad).
                endSession(e.message)
            } catch (e: Exception) {
                // Sin red no se borra la sesión: se ofrece reintentar.
                connectionError = "No se pudo conectar con el servidor. Revisa tu conexión."
            }
        }
    }

    fun submitLogin() {
        authError = null
        if (loginEmail.isBlank() || loginPassword.isBlank()) {
            authError = "Ingresa tu correo y tu contraseña."
            return
        }
        launch({ authError = it }) {
            val response = api.login(loginEmail, loginPassword)
            challengeId = response.challengeId
            enrollment = response.enrollment
            mfaCode = ""
            useRecoveryCode = false
            if (response.status == "password_change_required") {
                firstPassword = ""
                firstPasswordConfirmation = ""
                screen = Screen.FIRST_PASSWORD_CHANGE
            } else {
                loginPassword = ""
                screen = Screen.TWO_FACTOR
            }
        }
    }

    fun submitFirstPasswordChange() {
        val challenge = challengeId ?: return
        authError = null
        val assessment = assessPassword(firstPassword, loginEmail, exactLength = true)
        when {
            !assessment.isValid -> authError = assessment.message ?: "La contraseña no cumple los requisitos."
            firstPassword == loginPassword -> authError = "La nueva contraseña debe ser distinta de la temporal."
            firstPassword != firstPasswordConfirmation -> authError = "Las contraseñas no coinciden."
            else -> launch({ message ->
                authError = message
                if (message.contains("expiró", ignoreCase = true)) cancelLogin(message)
            }) {
                val response = api.changeInitialPassword(challenge, loginPassword, firstPassword)
                challengeId = response.challengeId
                enrollment = response.enrollment
                loginPassword = ""
                firstPassword = ""
                firstPasswordConfirmation = ""
                mfaCode = ""
                useRecoveryCode = false
                screen = Screen.TWO_FACTOR
            }
        }
    }

    fun submitMfa() {
        val challenge = challengeId ?: return
        authError = null
        val code = mfaCode.filter { !it.isWhitespace() }
        if (useRecoveryCode && code.length < 10 || !useRecoveryCode && code.length != 6) {
            authError = if (useRecoveryCode) "Escribe el código de recuperación completo." else "El código tiene 6 dígitos."
            return
        }
        launch({ message ->
            authError = message
            mfaCode = ""
        }) {
            val tokens: TokenResponse = try {
                if (useRecoveryCode) api.verifyRecoveryCode(challenge, code) else api.verifyMfa(challenge, code)
            } catch (e: ApiException) {
                if (e.code == "challenge_expired") cancelLogin(e.message)
                throw e
            }
            challengeId = null
            enrollment = null
            mfaCode = ""
            if (tokens.recoveryCodes.isNotEmpty()) {
                // Recién enroló: primero se muestran los códigos de recuperación, una sola vez.
                recoveryCodes = tokens.recoveryCodes
                sessionUser = tokens.user
                screen = Screen.RECOVERY_CODES
            } else {
                proceedAfterAuth(tokens.user)
            }
        }
    }

    fun confirmRecoveryCodesSaved() {
        val user = sessionUser ?: return
        recoveryCodes = emptyList()
        launch { proceedAfterAuth(user) }
    }

    fun cancelLogin(message: String? = null) {
        challengeId = null
        enrollment = null
        loginPassword = ""
        firstPassword = ""
        firstPasswordConfirmation = ""
        mfaCode = ""
        authError = message
        screen = Screen.LOGIN
    }

    private suspend fun enterApp(user: SessionUser) {
        idleLastActivityAt = nowMillis()
        idleWarningStartedAt = null
        idleSeconds = 0
        idleWarningVisible = false
        sessionUser = user
        reload()
        permissions = user.permissions.toSet()
        // La interfaz la decide el perfil del rol (un rol nuevo como "Supervisor" usa la de terapeuta).
        val userRole = Role.entries.firstOrNull { it.name == user.profile } ?: Role.PATIENT
        role = userRole
        previewing = false
        if (userRole == Role.PATIENT) {
            meId = user.id.toString()
            activePatientId = meId
        } else {
            activePatientId = patients.firstOrNull()?.id.orEmpty()
        }
        screen = roleProfile(userRole)?.homeScreen ?: Screen.LOGIN
    }

    fun logout(message: String? = null) {
        scope.launch {
            api.logout()
            endSession(message)
        }
    }

    // --- Inactividad: aviso a los 2 minutos sin interacción, con cuenta regresiva para cerrar ---
    var idleSeconds by mutableStateOf(0)
        private set
    var idleWarningVisible by mutableStateOf(false)
        private set
    var idleCountdown by mutableStateOf(IDLE_COUNTDOWN_SECONDS)
        private set
    private var idleLastActivityAt = 0L
    private var idleWarningStartedAt: Long? = null

    /** Cualquier interacción del usuario reinicia el conteo (salvo que el aviso ya esté abierto). */
    fun registerActivity() {
        if (!idleWarningVisible) {
            idleLastActivityAt = nowMillis()
            idleSeconds = 0
        }
    }

    /** Recalcula contra el reloj real para no perder tiempo cuando el navegador limita sus ticks. */
    fun idleTick() {
        val now = nowMillis()
        if (sessionUser == null || screen == Screen.LOGIN || screen == Screen.LOCK) {
            idleSeconds = 0
            idleWarningVisible = false
            idleLastActivityAt = now
            idleWarningStartedAt = null
            return
        }
        if (idleWarningVisible) {
            val warningStartedAt = idleWarningStartedAt ?: now.also { idleWarningStartedAt = it }
            val warningElapsed = ((now - warningStartedAt).coerceAtLeast(0L) / 1000).toInt()
            idleCountdown = (IDLE_COUNTDOWN_SECONDS - warningElapsed).coerceAtLeast(0)
            if (idleCountdown <= 0) {
                idleWarningVisible = false
                idleWarningStartedAt = null
                logout("Tu sesión se cerró por inactividad.")
            }
            return
        }
        idleSeconds = ((now - idleLastActivityAt).coerceAtLeast(0L) / 1000).toInt()
        if (idleSeconds >= IDLE_WARNING_SECONDS) {
            idleCountdown = IDLE_COUNTDOWN_SECONDS
            idleWarningStartedAt = now
            idleWarningVisible = true
        }
    }

    /** "Continuar": una llamada autenticada renueva la actividad en el servidor y el token. */
    fun continueSession() {
        idleWarningVisible = false
        idleWarningStartedAt = null
        idleLastActivityAt = nowMillis()
        idleSeconds = 0
        scope.launch {
            try {
                api.bootstrap()
            } catch (e: SessionExpiredException) {
                endSession(e.message)
            } catch (_: Exception) {
                // Un fallo de red momentáneo no cierra la sesión; la próxima acción lo reintenta.
            }
        }
    }

    fun logoutFromIdleWarning() {
        idleWarningVisible = false
        idleWarningStartedAt = null
        logout()
    }

    private fun endSession(message: String?) {
        idleWarningVisible = false
        idleWarningStartedAt = null
        idleSeconds = 0
        idleLastActivityAt = nowMillis()
        api.clearSession()
        role = null
        sessionUser = null
        pendingUser = null
        permissions = emptySet()
        previewing = false
        patients.clear()
        adminData = null
        changeInfo = null
        lockNeedsRestore = false
        lockReturnScreen = null
        authError = message
        screen = Screen.LOGIN
    }

    /** Después de contraseña + 2FA (o de restaurar la sesión): crear el PIN si falta, y entrar. */
    private suspend fun proceedAfterAuth(user: SessionUser) {
        idleLastActivityAt = nowMillis()
        idleWarningStartedAt = null
        idleSeconds = 0
        idleWarningVisible = false
        sessionUser = user
        permissions = user.permissions.toSet()
        if (supportsAppLock && !AppLock.hasPinFor(user.id)) {
            pendingUser = user
            startPinSetup(changing = false)
        } else {
            enterApp(user)
        }
    }

    // ------------------------------------------------------------------ olvido de contraseña

    fun openForgotPassword() {
        forgotEmail = loginEmail
        forgotResetId = null
        forgotInfo = null
        forgotCode = ""
        forgotUseRecovery = false
        forgotNewPassword = ""
        forgotConfirmPassword = ""
        authError = null
        authInfo = null
        screen = Screen.FORGOT_PASSWORD
    }

    fun submitForgotEmail() {
        authError = null
        if (forgotEmail.isBlank()) {
            authError = "Escribe tu correo institucional."
            return
        }
        launch({ authError = it }) {
            val response = api.forgotPassword(forgotEmail)
            forgotResetId = response.resetId
            forgotInfo = response.message
        }
    }

    fun submitResetPassword() {
        val resetId = forgotResetId ?: return
        authError = null
        val code = forgotCode.filter { !it.isWhitespace() }
        val passwordAssessment = assessPassword(forgotNewPassword, forgotEmail, exactLength = true)
        when {
            forgotUseRecovery && code.length < 10 -> authError = "Escribe el código de recuperación completo."
            !forgotUseRecovery && code.length != 6 -> authError = "El código tiene 6 dígitos."
            !passwordAssessment.isValid -> authError = passwordAssessment.message ?: "La contraseña no cumple los requisitos."
            forgotNewPassword != forgotConfirmPassword -> authError = "Las contraseñas no coinciden."
            else -> launch({ message -> authError = message; forgotCode = "" }) {
                try {
                    api.resetPassword(
                        resetId,
                        code.takeIf { !forgotUseRecovery },
                        code.takeIf { forgotUseRecovery },
                        forgotNewPassword,
                    )
                } catch (e: ApiException) {
                    if (e.code == "challenge_expired") forgotResetId = null
                    throw e
                }
                loginEmail = forgotEmail
                loginPassword = ""
                forgotNewPassword = ""
                forgotConfirmPassword = ""
                authInfo = "Contraseña actualizada. Inicia sesión con la nueva."
                screen = Screen.LOGIN
            }
        }
    }

    // ------------------------------------------------------------------ PIN y biometría

    fun startPasswordChange() {
        changeReturnScreen = screen
        changeCurrentPassword = ""
        changeNewPassword = ""
        changeConfirmPassword = ""
        changeCode = ""
        changeError = null
        changeInfo = null
        screen = Screen.CHANGE_PASSWORD
    }

    fun closePasswordChange() {
        changeCurrentPassword = ""
        changeNewPassword = ""
        changeConfirmPassword = ""
        changeCode = ""
        changeError = null
        screen = changeReturnScreen ?: currentProfile?.homeScreen ?: Screen.LOGIN
    }

    fun submitPasswordChange() {
        val email = sessionUser?.email.orEmpty()
        val code = changeCode.filter { it.isDigit() }
        val assessment = assessPassword(changeNewPassword, email, exactLength = true)
        changeError = when {
            changeCurrentPassword.isEmpty() -> "Escribe tu contraseña actual."
            !assessment.isValid -> assessment.message ?: "La contraseña no cumple los requisitos."
            changeNewPassword == changeCurrentPassword -> "La nueva contraseña debe ser distinta de la actual."
            changeNewPassword != changeConfirmPassword -> "Las contraseñas no coinciden."
            code.length != 6 -> "Escribe el código de 6 dígitos de tu app autenticadora."
            else -> null
        }
        if (changeError != null) return
        launch({ changeError = it }) {
            api.changePassword(changeCurrentPassword, changeNewPassword, code)
            closePasswordChange()
            changeInfo = "Contraseña actualizada. Se cerró la sesión en tus otros dispositivos."
        }
    }

    fun startPinSetup(changing: Boolean) {
        pinChanging = changing
        pinStep = if (changing) PinStep.CURRENT else PinStep.NEW
        pinReturnScreen = if (changing) screen else null
        pinInput = ""
        pinFirst = ""
        pinError = null
        screen = Screen.PIN_SETUP
    }

    fun pinDigit(digit: Char) {
        if (pinInput.length >= AppLock.PIN_LENGTH) return
        pinInput += digit
        if (pinInput.length == AppLock.PIN_LENGTH) {
            if (screen == Screen.LOCK) submitUnlock() else advancePinSetup()
        }
    }

    fun pinBackspace() {
        pinInput = pinInput.dropLast(1)
    }

    private fun advancePinSetup() {
        val pin = pinInput
        pinInput = ""
        pinError = null
        when (pinStep) {
            PinStep.CURRENT ->
                if (AppLock.verify(pin)) {
                    pinStep = PinStep.NEW
                } else if (AppLock.remainingAttempts == 0) {
                    lockedOut()
                } else {
                    pinError = "PIN incorrecto. Te quedan ${AppLock.remainingAttempts} intentos."
                }
            PinStep.NEW ->
                if (AppLock.isWeak(pin)) {
                    pinError = "Elige un PIN menos obvio: sin dígitos repetidos ni secuencias."
                } else {
                    pinFirst = pin
                    pinStep = PinStep.CONFIRM
                }
            PinStep.CONFIRM ->
                if (pin != pinFirst) {
                    pinError = "Los PIN no coinciden. Escríbelo de nuevo."
                    pinStep = PinStep.NEW
                } else {
                    val user = pendingUser ?: sessionUser ?: return
                    AppLock.setPin(user.id, pin)
                    if (pinChanging) {
                        banner = null
                        adminInfo = null
                        screen = pinReturnScreen ?: currentProfile?.homeScreen ?: Screen.LOGIN
                    } else if (Biometrics.isAvailable()) {
                        screen = Screen.BIOMETRIC_OFFER
                    } else {
                        finishOnboarding()
                    }
                }
        }
    }

    private fun finishOnboarding() {
        val user = pendingUser ?: return
        pendingUser = null
        launch { enterApp(user) }
    }

    /** Activa la huella o el rostro tras confirmarlos una vez (desde la bienvenida o el perfil). */
    fun enableBiometric(fromOnboarding: Boolean) {
        scope.launch {
            val ok = Biometrics.authenticate("Activar huella o rostro", "Confírmalo para usarlo al abrir USBrain")
            if (ok) {
                AppLock.biometricEnabled = true
                biometricEnabled = true
            }
            if (fromOnboarding) finishOnboarding()
        }
    }

    fun skipBiometric() = finishOnboarding()

    fun disableBiometric() {
        AppLock.biometricEnabled = false
        biometricEnabled = false
    }

    private fun openLock() {
        pinInput = ""
        pinError = null
        screen = Screen.LOCK
    }

    private fun submitUnlock() {
        if (AppLock.verify(pinInput)) {
            unlocked()
            return
        }
        pinInput = ""
        val left = AppLock.remainingAttempts
        if (left == 0) lockedOut() else pinError = "PIN incorrecto. Te quedan $left intentos."
    }

    fun unlockWithBiometric() {
        if (!AppLock.biometricEnabled || !Biometrics.isAvailable()) return
        scope.launch {
            if (Biometrics.authenticate("Desbloquear USBrain", "Usa tu huella o tu rostro")) {
                AppLock.resetFailures()
                unlocked()
            }
        }
    }

    private fun unlocked() {
        pinInput = ""
        pinError = null
        if (lockNeedsRestore) {
            lockNeedsRestore = false
            restoreSession()
        } else {
            screen = lockReturnScreen ?: currentProfile?.homeScreen ?: Screen.LOGIN
        }
        lockReturnScreen = null
    }

    // Demasiados intentos: se borran PIN y sesión; hay que entrar con contraseña y 2FA.
    private fun lockedOut() {
        AppLock.clear()
        biometricEnabled = false
        logout("Demasiados intentos con el PIN. Inicia sesión de nuevo.")
    }

    fun forgotPin() {
        AppLock.clear()
        biometricEnabled = false
        logout("Inicia sesión con tu contraseña y crea un PIN nuevo.")
    }

    /** La app pasó a segundo plano (la llama App.kt con el ciclo de vida). */
    fun onBackground() {
        if (role != null) backgroundSince = nowMillis()
    }

    /** Volvió a primer plano: tras [AppLock.LOCK_AFTER_SECONDS] se pide el PIN otra vez. */
    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (!supportsAppLock || role == null || !AppLock.isConfigured) return
        if (screen == Screen.LOCK || screen == Screen.PIN_SETUP || screen == Screen.BIOMETRIC_OFFER) return
        if (nowMillis() - since >= AppLock.LOCK_AFTER_SECONDS * 1000) {
            lockReturnScreen = screen
            lockNeedsRestore = false
            openLock()
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    // ------------------------------------------------------------------ administración

    /** Carga usuarios, roles, permisos y bitácora. Las pantallas de administración la llaman al abrirse. */
    fun loadAdmin() {
        adminError = null
        scope.launch {
            try {
                adminData = api.adminOverview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                endSession(e.message)
            } catch (e: ApiException) {
                adminError = e.message
            } catch (e: Exception) {
                adminError = "No se pudo conectar con el servidor."
            }
        }
    }

    fun loadAnalytics() {
        analyticsError = null
        scope.launch {
            try {
                analyticsData = api.adminAnalytics()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                endSession(e.message)
            } catch (e: ApiException) {
                analyticsError = e.message
            } catch (e: Exception) {
                analyticsError = "No se pudo obtener la exportación seudonimizada desde ORDS."
            }
        }
    }

    fun startNewUser() {
        editingUserId = null
        draftUserEmail = ""
        draftUserName = ""
        draftUserRole = adminData?.roles?.firstOrNull { it.id == "PATIENT" }?.id ?: ""
        draftUserStatus = "ACTIVE"
        draftUserPassword = ""
        draftUserTherapistId = adminData?.therapists?.firstOrNull()?.id
        draftUserBirthDate = ""
        draftUserProgram = ""
        draftUserCondition = ""
        draftActionPassword = ""
        adminError = null
        adminInfo = null
        screen = Screen.ADMIN_USER_EDIT
    }

    fun openUser(id: String) {
        val user = adminData?.users?.firstOrNull { it.id == id } ?: return
        editingUserId = id
        draftUserEmail = user.email
        draftUserName = user.fullName
        draftUserRole = user.role
        draftUserStatus = user.status
        draftUserTherapistId = user.therapistId
        draftUserPassword = ""
        draftActionPassword = ""
        // Las vencidas ya no aplican: al guardar se descartan.
        draftOverrides.clear()
        user.overrides.filter { it.active }.forEach { draftOverrides[it.permission] = it.effect }
        draftOverrideReason = ""
        draftOverrideExpiry = ""
        adminError = null
        adminInfo = null
        screen = Screen.ADMIN_USER_EDIT
    }

    fun draftRoleProfileOf(roleId: String): String? = adminData?.roles?.firstOrNull { it.id == roleId }?.profile

    fun saveUser() {
        adminError = null
        val editing = editingUserId
        val isPatientRole = draftRoleProfileOf(draftUserRole) == "PATIENT"
        if (editing == null) {
            val assessment = assessPassword(draftUserPassword, draftUserEmail)
            if (!assessment.isValid) {
                adminError = assessment.message ?: "La contraseña temporal no cumple los requisitos."
                return
            }
        }
        launch({ adminError = it }) {
            if (editing == null) {
                api.adminCreateUser(buildJsonObject {
                    put("email", draftUserEmail.trim())
                    put("full_name", draftUserName.trim())
                    put("role", draftUserRole)
                    put("password", draftUserPassword)
                    if (isPatientRole) {
                        draftUserTherapistId?.let { put("therapist_id", it) }
                        put("birth_date", draftUserBirthDate.trim())
                        put("program", draftUserProgram.trim())
                        put("condition", draftUserCondition.trim())
                    }
                })
                adminInfo = "Usuario creado. Comparte la contraseña inicial por un canal seguro."
            } else {
                api.adminUpdateUser(editing, buildJsonObject {
                    put("full_name", draftUserName.trim())
                    put("role", draftUserRole)
                    put("status", draftUserStatus)
                    if (isPatientRole) draftUserTherapistId?.let { put("therapist_id", it) }
                })
                adminInfo = "Cambios guardados."
            }
            adminData = api.adminOverview()
            screen = Screen.ADMIN_USERS
        }
    }

    /** unlock | reset_mfa | set_password */
    fun userAction(action: String) {
        val id = editingUserId ?: return
        adminError = null
        adminInfo = null
        if (action == "set_password") {
            val email = adminData?.users?.firstOrNull { it.id == id }?.email.orEmpty()
            val assessment = assessPassword(draftActionPassword, email)
            if (!assessment.isValid) {
                adminError = assessment.message ?: "La contraseña temporal no cumple los requisitos."
                return
            }
        }
        launch({ adminError = it }) {
            api.adminUserAction(id, action, draftActionPassword.takeIf { action == "set_password" })
            draftActionPassword = ""
            adminInfo = when (action) {
                "unlock" -> "Cuenta desbloqueada."
                "reset_mfa" -> "2FA restablecido: en su próximo ingreso configurará la app autenticadora otra vez."
                else -> "Contraseña restablecida y sesiones cerradas."
            }
            adminData = api.adminOverview()
        }
    }

    /** Cambia la excepción de un permiso; null vuelve a lo que diga el rol. */
    fun setOverride(permission: String, effect: String?) {
        if (effect == null) draftOverrides.remove(permission) else draftOverrides[permission] = effect
    }

    fun saveUserPermissions() {
        val id = editingUserId ?: return
        val existing = adminData?.users?.firstOrNull { it.id == id }?.overrides.orEmpty()
            .filter { it.active }.associateBy { it.permission }
        // Las que no cambian conservan su motivo y vencimiento; las nuevas usan los del formulario.
        val changed = draftOverrides.filter { (permission, effect) -> existing[permission]?.effect != effect }
        adminError = null
        adminInfo = null
        if (changed.isNotEmpty() && draftOverrideReason.isBlank()) {
            adminError = "Escribe el motivo de los permisos individuales que cambiaste."
            return
        }
        launch({ adminError = it }) {
            api.adminSetUserPermissions(id, buildJsonObject {
                put("overrides", buildJsonArray {
                    draftOverrides.forEach { (permission, effect) ->
                        val kept = existing[permission]?.takeIf { it.effect == effect }
                        add(buildJsonObject {
                            put("permission", permission)
                            put("effect", effect)
                            put("reason", kept?.reason ?: draftOverrideReason.trim())
                            (if (kept != null) kept.expiresAt else draftOverrideExpiry.ifBlank { null })
                                ?.let { put("expires_at", it) }
                        })
                    }
                })
            })
            draftOverrideReason = ""
            draftOverrideExpiry = ""
            adminInfo = "Permisos individuales guardados. Aplican de inmediato."
            adminData = api.adminOverview()
        }
    }

    fun startNewRole() {
        editingRoleId = null
        draftRoleId = ""
        draftRoleName = ""
        draftRoleDescription = ""
        draftRoleProfile = "THERAPIST"
        draftRolePermissions.clear()
        adminError = null
        adminInfo = null
        screen = Screen.ADMIN_ROLE_EDIT
    }

    fun openRole(id: String) {
        val role = adminData?.roles?.firstOrNull { it.id == id } ?: return
        editingRoleId = id
        draftRoleId = role.id
        draftRoleName = role.name
        draftRoleDescription = role.description.orEmpty()
        draftRoleProfile = role.profile
        draftRolePermissions.clear()
        draftRolePermissions.addAll(role.permissions)
        adminError = null
        adminInfo = null
        screen = Screen.ADMIN_ROLE_EDIT
    }

    fun toggleRolePermission(permission: String) {
        if (permission in draftRolePermissions) draftRolePermissions.remove(permission)
        else draftRolePermissions.add(permission)
    }

    fun saveRole() {
        adminError = null
        val editing = editingRoleId
        launch({ adminError = it }) {
            val body = buildJsonObject {
                if (editing == null) put("id", draftRoleId.trim().uppercase())
                put("name", draftRoleName.trim())
                put("description", draftRoleDescription.trim())
                put("profile", draftRoleProfile)
                put("permissions", JsonArray(draftRolePermissions.map { JsonPrimitive(it) }))
            }
            if (editing == null) api.adminCreateRole(body) else api.adminUpdateRole(editing, body)
            adminInfo = if (editing == null) "Rol creado." else "Rol actualizado. Los permisos aplican de inmediato."
            adminData = api.adminOverview()
            screen = Screen.ADMIN_ROLES
        }
    }

    fun deleteRole() {
        val id = editingRoleId ?: return
        adminError = null
        launch({ adminError = it }) {
            api.adminDeleteRole(id)
            adminInfo = "Rol borrado."
            adminData = api.adminOverview()
            screen = Screen.ADMIN_ROLES
        }
    }

    // ------------------------------------------------------------------ navegación

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

    fun openPatient(id: String) {
        activePatientId = id
        showNewDiagnosisForm = false
        draftDiagnosisCode = ""
        draftDiagnosisLabel = ""
        diagnosisError = null
        screen = Screen.THERAPIST_PATIENT
    }

    fun openBehaviorDetail(id: String) {
        activeBehaviorId = id
        behaviorDetailTab = BehaviorTab.ACTIVIDADES
        screen = Screen.THERAPIST_BEHAVIOR
    }

    fun openTaskDetail(id: String) { activeTaskId = id; screen = Screen.THERAPIST_TASK_DETAIL }

    fun previewAsPatient(id: String) {
        previewing = true
        role = Role.PATIENT; meId = id; activePatientId = id; screen = Screen.PATIENT_HOME
    }

    fun exitPreview() {
        val user = sessionUser ?: return
        val userRole = Role.entries.firstOrNull { it.name == user.role } ?: return
        previewing = false
        role = userRole
        screen = Screen.THERAPIST_PATIENT
    }

    // ------------------------------------------------------------------ registros del paciente

    fun saveTask(date: LocalDate = todayDate()) {
        val task = activeTask()
        val observationMinutes = taskObservationMinutes.takeIf {
            task.taskType == TaskType.REGISTRO_AUTONOMO && task.dimension == Dimension.FRECUENCIA && it > 0
        }
        taskError = null
        launch({ taskError = it }) {
            api.recordEntry(task.id, buildJsonObject {
                put("date", date.toString())
                put("value", taskInputValue)
                put("note", taskNote.trim())
                observationMinutes?.let { put("observation_minutes", it) }
            })
            reload()
            taskEntryDate = date
            lastSavedEntryDate = date
            screen = Screen.PATIENT_DONE
        }
    }

    // Análogo a saveTask() pero para actividades con widget configurable: construye el valor
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
        val value = when (widget.valueKind) {
            WidgetValueKind.NUMERIC -> buildJsonObject {
                put("value", draftWidgetNumericValue)
                draftWidgetObservationMinutes.takeIf { it > 0 }?.let { put("observation_minutes", it) }
            }
            WidgetValueKind.YES_NO -> buildJsonObject { put("value", draftWidgetBoolValue) }
            WidgetValueKind.TEXT -> buildJsonObject { put("value", draftWidgetTextValue.trim()) }
            WidgetValueKind.MULTI_SELECT -> buildJsonObject {
                put("values", JsonArray(draftWidgetSelectedOptions.map { JsonPrimitive(it) }))
            }
        }
        taskError = null
        launch({ taskError = it }) {
            api.recordWidgetEntry(task.id, buildJsonObject {
                put("date", date.toString())
                put("time", "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}")
                put("value", value)
                taskNote.trim().takeIf { it.isNotEmpty() }?.let { put("note", it) }
            })
            reload()
            taskEntryDate = date
            lastSavedEntryDate = date
            screen = Screen.PATIENT_DONE
        }
    }

    fun toggleWidgetOption(option: String) {
        if (draftWidgetSelectedOptions.contains(option)) draftWidgetSelectedOptions.remove(option)
        else draftWidgetSelectedOptions.add(option)
    }

    // ------------------------------------------------------------------ terapeuta

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
        val isMaladaptive = draftBehaviorType == BehaviorType.DESADAPTATIVA
        behaviorError = null
        launch({ behaviorError = it }) {
            val id = api.createBehavior(buildJsonObject {
                put("patient_id", draftPatientId)
                put("name", draftBehaviorName.trim())
                put("operational_definition", draftDefinition.trim())
                put("type", draftBehaviorType.name)
                if (isMaladaptive) {
                    put("function_id", draftBehavioralFunctionId)
                    draftReplacementBehaviorId?.let { put("replacement_behavior_id", it) }
                }
            })
            reload()
            activePatientId = draftPatientId
            activeBehaviorId = id
            behaviorDetailTab = BehaviorTab.ACTIVIDADES
            draftBaselineDimensions.clear()
            draftBaselineDimensions.add(Dimension.FRECUENCIA)
            screen = Screen.THERAPIST_BASELINE_SETUP
        }
    }

    fun configureBaseline() {
        val behaviorId = activeBehaviorId ?: return
        val selected = draftBaselineDimensions.distinct().ifEmpty { listOf(Dimension.FRECUENCIA) }
        launch {
            api.configureBaseline(behaviorId, selected.map { it.name })
            reload()
            screen = Screen.THERAPIST_BEHAVIOR
        }
    }

    fun closeBaseline(id: String) {
        launch {
            api.closeBaseline(id)
            reload()
        }
    }

    // Catálogo editable: registra una función conductual nueva y la deja seleccionada.
    fun createBehavioralFunction(label: String) {
        if (label.isBlank()) return
        launch({ behaviorError = it }) {
            val id = api.createBehavioralFunction(label.trim())
            reload()
            draftBehavioralFunctionId = id
            showNewFunctionForm = false
            draftNewFunctionLabel = ""
        }
    }

    // Asignar, no reemplazar: un paciente puede tener más de un código CIE-10 (comorbilidad).
    fun assignDiagnosis(patientId: String, code: String, label: String) {
        val trimmedCode = code.trim().uppercase()
        if (trimmedCode.isBlank()) {
            diagnosisError = "Escribe el código CIE-10."
            return
        }
        diagnosisError = null
        launch({ diagnosisError = it }) {
            api.assignDiagnosis(patientId, trimmedCode, label.trim().ifBlank { trimmedCode })
            reload()
            showNewDiagnosisForm = false
            draftDiagnosisCode = ""
            draftDiagnosisLabel = ""
        }
    }

    fun removeDiagnosis(patientId: String, code: String) {
        launch({ diagnosisError = it }) {
            api.removeDiagnosis(patientId, code)
            reload()
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

    // Crea una medición personalizada y la deja preseleccionada para la actividad en curso.
    fun saveCustomWidget() {
        if (draftCustomWidgetName.isBlank()) {
            customWidgetError = "Completa el nombre de la variable a medir."
            return
        }
        val options = draftCustomWidgetOptionsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (draftCustomWidgetValueKind == WidgetValueKind.MULTI_SELECT && options.isEmpty()) {
            customWidgetError = "Agrega al menos una opción, separadas por coma."
            return
        }
        customWidgetError = null
        launch({ customWidgetError = it }) {
            val id = api.createWidget(buildJsonObject {
                put("name", draftCustomWidgetName.trim())
                put("value_kind", draftCustomWidgetValueKind.name)
                draftCustomWidgetUnit.trim().takeIf { it.isNotEmpty() }?.let { put("unit", it) }
                draftCustomWidgetMin.replace(',', '.').toDoubleOrNull()?.let { put("min_value", it) }
                draftCustomWidgetMax.replace(',', '.').toDoubleOrNull()?.let { put("max_value", it) }
                put("options", JsonArray(options.map { JsonPrimitive(it) }))
                draftCustomWidgetDescription.trim().takeIf { it.isNotEmpty() }?.let { put("description", it) }
            })
            reload()
            draftSelectedWidgetId = id
            draftUseWidgetSystem = true
            screen = Screen.THERAPIST_NEW_TASK
        }
    }

    fun assign() {
        val behaviorId = draftBehaviorId
        if (behaviorId == null || draftTaskTitle.isBlank()) {
            assignError = "Completa el nombre de la actividad."
            return
        }
        val widgetId = if (draftUseWidgetSystem) draftSelectedWidgetId?.takeIf { widget(it) != null } else null
        if (draftUseWidgetSystem && widgetId == null) {
            assignError = "Selecciona un método de medición para la actividad."
            return
        }
        assignError = null
        launch({ assignError = it }) {
            val id = api.createTask(buildJsonObject {
                put("behavior_id", behaviorId)
                put("title", draftTaskTitle.trim())
                put("instructions", draftInstructions.trim())
                put("task_type", if (widgetId != null) TaskType.MEDICION_CONFIGURABLE.name else draftTaskType.name)
                put("dimension", draftTaskDimension.name)
                if (widgetId == null && draftTaskType != TaskType.REGISTRO_AUTONOMO) put("metric_unit", draftMetricUnit.name)
                widgetId?.let { put("widget_id", it) }
                put("assigned_date", draftAssignedDate.trim().ifBlank { "Hoy" })
                draftDueDate.trim().takeIf { it.isNotEmpty() }?.let { put("due_date", it) }
                put("is_recurring", draftIsRecurring)
                if (draftIsRecurring) put("frequency", draftFrequency)
                put("reminder_active", draftReminderActive)
                put("phase", draftTaskPhase.name)
                draftTherapeuticGoal.trim().takeIf { it.isNotEmpty() }?.let { put("therapeutic_goal", it) }
            })
            reload()
            lastAssignedTaskId = id
            activePatientId = draftPatientId
            activeBehaviorId = behaviorId
            screen = Screen.THERAPIST_ASSIGNED
        }
    }
}
