package org.usbrain.project.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Contrato JSON de la API de ORDS (ver backend/README.md). Los nombres siguen el
 * snake_case del servidor; los arreglos vacíos llegan como null y se convierten en
 * listas vacías gracias a `coerceInputValues`.
 */
val ApiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class ApiErrorBody(val error: String = "unknown", val message: String = "")

// ---------------------------------------------------------------- autenticación

@Serializable
data class LoginResponse(
    val status: String,
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("expires_in") val expiresIn: Int = 300,
    val enrollment: Enrollment? = null,
)

@Serializable
data class Enrollment(
    val secret: String,
    val issuer: String,
    val account: String,
    @SerialName("otpauth_uri") val otpauthUri: String,
)

@Serializable
data class SessionUser(
    val id: Long,
    val email: String,
    @SerialName("full_name") val fullName: String,
    // role = id del rol (dinámico); profile = la interfaz que ve (PATIENT, THERAPIST, ADMIN).
    val role: String,
    @SerialName("role_name") val roleName: String = "",
    val profile: String = "PATIENT",
    val permissions: List<String> = emptyList(),
    @SerialName("mfa_enabled") val mfaEnabled: Boolean = false,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("session_id") val sessionId: Long,
    val user: SessionUser,
    @SerialName("recovery_codes") val recoveryCodes: List<String> = emptyList(),
    @SerialName("recovery_codes_remaining") val recoveryCodesRemaining: Int? = null,
)

@Serializable
data class IdResponse(val id: String)

// ---------------------------------------------------------------- bootstrap

@Serializable
data class BootstrapDto(
    val today: String,
    val user: BootstrapUser,
    val patients: List<PatientDto> = emptyList(),
    val catalogs: CatalogsDto = CatalogsDto(),
)

@Serializable
data class BootstrapUser(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    @SerialName("role_name") val roleName: String = "",
    val profile: String = "PATIENT",
    val permissions: List<String> = emptyList(),
)

@Serializable
data class CatalogsDto(
    val widgets: List<WidgetDto> = emptyList(),
    val functions: List<FunctionDto> = emptyList(),
    val cie10: List<DiagnosisDto> = emptyList(),
)

@Serializable
data class WidgetDto(
    val id: String,
    val name: String,
    @SerialName("value_kind") val valueKind: String,
    val unit: String? = null,
    @SerialName("min_value") val minValue: Double? = null,
    @SerialName("max_value") val maxValue: Double? = null,
    val options: List<String> = emptyList(),
    val description: String? = null,
    @SerialName("is_preset") val isPreset: Boolean = false,
)

@Serializable
data class FunctionDto(val id: String, val label: String)

@Serializable
data class DiagnosisDto(val code: String, val label: String)

@Serializable
data class PatientDto(
    val id: String,
    val name: String,
    val email: String = "",
    val age: Int = 0,
    val program: String = "",
    val color: Long = 0xFF7B66C2,
    val condition: String = "",
    @SerialName("therapist_name") val therapistName: String? = null,
    val diagnoses: List<DiagnosisDto> = emptyList(),
    val case: CaseDto? = null,
)

@Serializable
data class CaseDto(
    val id: String,
    val title: String,
    val behaviors: List<BehaviorDto> = emptyList(),
)

@Serializable
data class BehaviorDto(
    val id: String,
    @SerialName("case_id") val caseId: String,
    val name: String,
    @SerialName("operational_definition") val operationalDefinition: String,
    val type: String,
    @SerialName("baseline_open") val baselineOpen: Boolean,
    @SerialName("baseline_closed_on") val baselineClosedOn: String? = null,
    @SerialName("function_id") val functionId: String = "fn-no-aplica",
    @SerialName("replacement_behavior_id") val replacementBehaviorId: String? = null,
    @SerialName("baseline_dimensions") val baselineDimensions: List<String> = emptyList(),
    @SerialName("baseline_entries") val baselineEntries: List<BaselineEntryDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
)

@Serializable
data class BaselineEntryDto(
    val id: String,
    @SerialName("task_id") val taskId: String,
    val date: String,
    val value: Double,
    val note: String = "",
    val dimension: String,
    @SerialName("observation_minutes") val observationMinutes: Int? = null,
)

@Serializable
data class TaskDto(
    val id: String,
    @SerialName("behavior_id") val behaviorId: String,
    val title: String,
    val instructions: String = "",
    @SerialName("task_type") val taskType: String,
    val dimension: String = "FRECUENCIA",
    @SerialName("metric_unit") val metricUnit: String? = null,
    @SerialName("widget_id") val widgetId: String? = null,
    @SerialName("therapeutic_goal") val therapeuticGoal: String? = null,
    @SerialName("assigned_date") val assignedDate: String = "Hoy",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("is_recurring") val isRecurring: Boolean = false,
    val frequency: String? = null,
    @SerialName("reminder_active") val reminderActive: Boolean = false,
    val phase: String,
    val status: String = "PENDIENTE",
    val entries: List<EntryDto> = emptyList(),
    @SerialName("widget_entries") val widgetEntries: List<WidgetEntryDto> = emptyList(),
)

@Serializable
data class EntryDto(
    val date: String,
    val value: Double,
    val note: String = "",
    val phase: String,
    @SerialName("observation_minutes") val observationMinutes: Int? = null,
)

@Serializable
data class WidgetEntryDto(
    val id: String,
    @SerialName("widget_id") val widgetId: String,
    val date: String,
    val time: String = "09:00",
    @SerialName("numeric_value") val numericValue: Double? = null,
    @SerialName("observation_minutes") val observationMinutes: Int? = null,
    @SerialName("bool_value") val boolValue: Boolean? = null,
    @SerialName("text_value") val textValue: String? = null,
    @SerialName("selected_options") val selectedOptions: List<String> = emptyList(),
    val note: String? = null,
)

// ---------------------------------------------------------------- olvido de contraseña

@Serializable
data class ForgotPasswordResponse(
    @SerialName("reset_id") val resetId: String,
    @SerialName("expires_in") val expiresIn: Int = 300,
    val message: String = "",
)

// ---------------------------------------------------------------- administración

@Serializable
data class AdminOverviewDto(
    val users: List<AdminUserDto> = emptyList(),
    val roles: List<AdminRoleDto> = emptyList(),
    val permissions: List<PermissionDto> = emptyList(),
    val therapists: List<TherapistOptionDto> = emptyList(),
    val events: List<AuditEventDto> = emptyList(),
)

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    @SerialName("role_name") val roleName: String = "",
    val profile: String = "PATIENT",
    val status: String = "ACTIVE",
    @SerialName("mfa_enabled") val mfaEnabled: Boolean = false,
    val locked: Boolean = false,
    @SerialName("last_login_at") val lastLoginAt: String? = null,
    @SerialName("is_patient") val isPatient: Boolean = false,
    @SerialName("therapist_id") val therapistId: String? = null,
    @SerialName("therapist_name") val therapistName: String? = null,
    // Permisos efectivos (rol + excepciones vigentes) y las excepciones individuales.
    val permissions: List<String> = emptyList(),
    val overrides: List<UserOverrideDto> = emptyList(),
)

/** Excepción individual sobre el rol: GRANT (otorga) o DENY (quita) un permiso a un solo usuario. */
@Serializable
data class UserOverrideDto(
    val permission: String,
    val effect: String,
    val reason: String = "",
    // Último día válido (AAAA-MM-DD); null = sin vencimiento.
    @SerialName("expires_at") val expiresAt: String? = null,
    val active: Boolean = true,
    @SerialName("granted_by") val grantedBy: String? = null,
)

@Serializable
data class AdminRoleDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val profile: String,
    @SerialName("is_system") val isSystem: Boolean = false,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("user_count") val userCount: Int = 0,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class PermissionDto(
    val id: String,
    val name: String,
    val category: String,
    val description: String? = null,
    val sensitive: Boolean = false,
)

@Serializable
data class TherapistOptionDto(val id: String, @SerialName("full_name") val fullName: String)

@Serializable
data class AuditEventDto(
    val at: String,
    val type: String,
    val email: String? = null,
    @SerialName("actor_email") val actorEmail: String? = null,
    @SerialName("target_email") val targetEmail: String? = null,
    val detail: String? = null,
    val ip: String? = null,
)

@Serializable
data class AnalyticsDatasetDto(
    val status: String = "DISCONNECTED",
    val source: String = "",
    @SerialName("generated_at") val generatedAt: String = "",
    val privacy: AnalyticsPrivacyDto = AnalyticsPrivacyDto(),
    val series: List<AnalyticsSeriesDto> = emptyList(),
    @SerialName("program_groups") val programGroups: List<AnalyticsProgramGroupDto> = emptyList(),
    @SerialName("age_groups") val ageGroups: List<AnalyticsProgramGroupDto> = emptyList(),
)

@Serializable
data class AnalyticsPrivacyDto(
    val pseudonymized: Boolean = false,
    val fields: List<String> = emptyList(),
    val excluded: List<String> = emptyList(),
)

@Serializable
data class AnalyticsSeriesDto(
    val id: String,
    val behavior: String,
    val dimension: String,
    val unit: String,
    @SerialName("expected_direction") val expectedDirection: String = "DECREASE",
    val baseline: List<AnalyticsPointDto> = emptyList(),
    val intervention: List<AnalyticsPointDto> = emptyList(),
)

@Serializable
data class AnalyticsPointDto(
    val session: Int,
    val value: Double,
    /** Día relativo al inicio de la serie; nunca es una fecha real. */
    val day: Int = session,
    /** Fecha del registro diario, necesaria para construir la serie cronológica autorizada. */
    val date: String? = null,
)

@Serializable
data class AnalyticsProgramGroupDto(
    val program: String,
    val dimension: String,
    val unit: String,
    @SerialName("participants") val participants: Int,
    @SerialName("baseline_mean") val baselineMean: Double,
    @SerialName("intervention_mean") val interventionMean: Double,
)
