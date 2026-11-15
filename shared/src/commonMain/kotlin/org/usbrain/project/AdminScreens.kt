@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.usbrain.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.usbrain.project.data.AdminRoleDto
import org.usbrain.project.data.AdminUserDto
import org.usbrain.project.data.PermissionDto
import org.usbrain.project.data.UserOverrideDto

private val PROFILE_LABELS = mapOf("PATIENT" to "Paciente", "THERAPIST" to "Terapeuta", "ADMIN" to "Administración")

@Composable
private fun AdminMessages(state: PrototypeState) {
    state.adminInfo?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = LocalUsBrainSemantic.current.success)
    }
    state.adminError?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/* ----------------------------- panel ----------------------------- */

@Composable
internal fun AdminHomeScreen(state: PrototypeState, dark: Boolean, onToggleTheme: () -> Unit) {
    LaunchedEffect(Unit) { state.loadAdmin() }
    val data = state.adminData
    ScreenScaffold {
        HomeHeader(
            greeting = "Hola 👋",
            name = state.userName.ifBlank { "Administrador" },
            dark = dark,
            onToggleTheme = onToggleTheme,
            onLogout = { state.logout() },
            avatar = {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brand.Orange),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.userInitials, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
        )
        Text(
            state.sessionUser?.roleName.orEmpty().ifBlank { "Administración" } + " · panel del sistema",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${data?.users?.count { it.status == "ACTIVE" } ?: "–"}", "usuarios activos", MaterialTheme.colorScheme.primary)
            StatTile("${data?.roles?.size ?: "–"}", "roles", MaterialTheme.colorScheme.onSurface)
            StatTile("${state.patients.size}", "pacientes", MaterialTheme.colorScheme.onSurface)
        }
        AdminMessages(state)
        if (state.can("settings.clinical")) ClinicalSettingsCard(state)
        if (state.can("audit.view") && data != null) {
            SectionLabel("Actividad de seguridad reciente")
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (data.events.isEmpty()) {
                        Text("Sin eventos todavía.", style = MaterialTheme.typography.bodySmall)
                    }
                    data.events.take(12).forEach { e ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(eventLabel(e.type), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(e.email, e.ip).joinToString(" · ").ifBlank { "—" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                e.at.take(16).replace('T', ' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (state.patients.isNotEmpty()) {
            SectionLabel("Pacientes en el sistema")
            state.patients.forEach { p -> PatientRow(p) { state.openPatient(p.id) } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Parámetros del flujo clínico; los aplica el servidor al cambiar de fase. */
@Composable
private fun ClinicalSettingsCard(state: PrototypeState) {
    LaunchedEffect(state.minPhaseObservations) { state.draftMinPhaseObservations = state.minPhaseObservations.toString() }
    SectionLabel("Parámetros clínicos")
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mínimo de observaciones por fase", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Días con registro que necesita cada dimensión medida para que el terapeuta pueda terminar una fase (A1, B1, A2…). Entre 3 y 30; el análisis estadístico pide al menos 3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    state.draftMinPhaseObservations,
                    { state.draftMinPhaseObservations = it.filter(Char::isDigit).take(2) },
                    Modifier.weight(1f),
                    label = { Text("Días") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(onClick = { state.saveClinicalSettings() }, enabled = !state.busy) { Text("Guardar") }
            }
            state.settingsInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalUsBrainSemantic.current.success) }
            state.settingsError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun eventLabel(type: String): String = when (type) {
    "LOGIN_SUCCESS" -> "Inicio de sesión"
    "LOGIN_BAD_PASSWORD" -> "Contraseña incorrecta"
    "LOGIN_UNKNOWN_EMAIL" -> "Intento con correo inexistente"
    "MFA_BAD_CODE" -> "Código 2FA incorrecto"
    "MFA_ENROLLED" -> "2FA activado"
    "ACCOUNT_LOCKED" -> "Cuenta bloqueada por intentos"
    "SESSION_REVOKED" -> "Sesión cerrada"
    "REFRESH_REUSE_DETECTED" -> "Posible robo de sesión detectado"
    "PASSWORD_RESET_REQUESTED" -> "Solicitud de cambio de contraseña"
    "PASSWORD_RESET_DONE" -> "Contraseña restablecida"
    "PASSWORD_CHANGED" -> "Contraseña cambiada"
    "ADMIN_USER_CREATED" -> "Usuario creado"
    "ADMIN_USER_UPDATED" -> "Usuario modificado"
    "ADMIN_USER_PERMISSIONS" -> "Permisos individuales modificados"
    "SETTINGS_UPDATED" -> "Parámetros clínicos modificados"
    "ROLE_CREATED" -> "Rol creado"
    "ROLE_UPDATED" -> "Rol modificado"
    "ROLE_DELETED" -> "Rol borrado"
    else -> type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/* ----------------------------- usuarios ----------------------------- */

private enum class UserStatusFilter(val label: String) { ACTIVE("Activos"), INACTIVE("Inactivos"), ALL("Todos") }

private const val USERS_PAGE = 50

/** Quita tildes y mayúsculas para que "patino" encuentre a "Patiño". */
private fun searchable(text: String): String = text.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')

@Composable
internal fun AdminUsersScreen(state: PrototypeState) {
    LaunchedEffect(Unit) { state.loadAdmin() }
    val users = state.adminData?.users.orEmpty()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(UserStatusFilter.ACTIVE) }
    val words = searchable(query).split(' ').filter { it.isNotBlank() }
    val matching = users.filter { user ->
        val byStatus = when (filter) {
            UserStatusFilter.ACTIVE -> user.status == "ACTIVE"
            UserStatusFilter.INACTIVE -> user.status != "ACTIVE"
            UserStatusFilter.ALL -> true
        }
        val text = searchable("${user.fullName} ${user.email} ${user.roleName} ${user.role}")
        byStatus && words.all { it in text }
    }
    // De a 50: con los participantes sintéticos la lista pasa de 200 y se dibujaría lenta.
    var shown by remember(query, filter) { mutableStateOf(USERS_PAGE) }
    ScreenScaffold {
        Text("Usuarios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (state.can("users.manage")) "Toca un usuario para cambiar su rol, su estado o restablecer su acceso."
            else "Consulta de usuarios (solo lectura).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            query, { query = it.take(80) }, Modifier.fillMaxWidth(),
            label = { Text("Buscar por nombre, correo o rol") }, singleLine = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserStatusFilter.entries.forEach { option ->
                val count = users.count {
                    when (option) {
                        UserStatusFilter.ACTIVE -> it.status == "ACTIVE"
                        UserStatusFilter.INACTIVE -> it.status != "ACTIVE"
                        UserStatusFilter.ALL -> true
                    }
                }
                TabChip(filter == option, { filter = option }, "${option.label} ($count)")
            }
        }
        AdminMessages(state)
        if (state.adminData == null && state.adminError == null) Text("Cargando…", style = MaterialTheme.typography.bodySmall)
        if (state.adminData != null) {
            Text(
                if (matching.isEmpty()) "Ningún usuario coincide con la búsqueda."
                else "Mostrando ${minOf(shown, matching.size)} de ${matching.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        matching.take(shown).forEach { user ->
            UserRow(user, enabled = state.can("users.manage") && !user.synthetic) { state.openUser(user.id) }
        }
        if (matching.size > shown) {
            OutlinedButton(onClick = { shown += USERS_PAGE }, Modifier.fillMaxWidth()) {
                Text("Mostrar ${minOf(USERS_PAGE, matching.size - shown)} más")
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun UserRow(user: AdminUserDto, enabled: Boolean, onClick: () -> Unit) {
    val s = LocalUsBrainSemantic.current
    OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(user.roleName.ifBlank { user.role }, MaterialTheme.colorScheme.primary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    user.status != "ACTIVE" -> Pill("Deshabilitado", MaterialTheme.colorScheme.error)
                    user.locked -> Pill("Bloqueado", MaterialTheme.colorScheme.error)
                    else -> Pill("Activo", s.success)
                }
                if (user.synthetic) {
                    Pill("Participante sintético · solo lectura", Brand.Orange)
                } else {
                    Pill(if (user.mfaEnabled) "2FA activo" else "Sin 2FA", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                user.therapistName?.let { Pill("Terapeuta: $it", MaterialTheme.colorScheme.onSurfaceVariant) }
                user.overrides.count { it.active }.takeIf { it > 0 }?.let {
                    Pill(if (it == 1) "1 permiso individual" else "$it permisos individuales", Brand.Orange)
                }
            }
        }
    }
}

@Composable
internal fun AdminUserEditScreen(state: PrototypeState) {
    var initialPasswordVisible by remember { mutableStateOf(false) }
    var actionPasswordVisible by remember { mutableStateOf(false) }
    val data = state.adminData
    val editing = state.editingUserId
    val user = data?.users?.firstOrNull { it.id == editing }
    val roles = data?.roles.orEmpty()
    val isPatientRole = state.draftRoleProfileOf(state.draftUserRole) == "PATIENT"
    val initialPasswordAssessment = assessPassword(state.draftUserPassword, state.draftUserEmail)
    val actionPasswordAssessment = assessPassword(state.draftActionPassword, user?.email.orEmpty())
    val canCreateSuperAdmin = state.sessionUser?.role == "SUPERADMIN"
    ScreenScaffold {
        BackHeader(if (editing == null) "Nuevo usuario" else "Editar usuario") { state.screen = Screen.ADMIN_USERS }
        if (editing == null) {
            OutlinedTextField(
                state.draftUserEmail, { state.draftUserEmail = sanitizeEmailInput(it) }, Modifier.fillMaxWidth(),
                label = { Text("Correo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        } else {
            Text(state.draftUserEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(state.draftUserName, { state.draftUserName = it }, Modifier.fillMaxWidth(), label = { Text("Nombre completo") }, singleLine = true)
        DropdownField(
            label = "Rol",
            selectedLabel = roles.firstOrNull { it.id == state.draftUserRole }?.let { "${it.name} · ${PROFILE_LABELS[it.profile]}" },
            placeholder = "Elige un rol",
            // Un paciente solo cambia a otros roles de paciente, y viceversa (lo valida también el servidor).
            options = roles
                .filter { user == null || (it.profile == "PATIENT") == user.isPatient }
                .filter { it.id != "SUPERADMIN" || canCreateSuperAdmin }
                .map { it.id to "${it.name} · ${PROFILE_LABELS[it.profile]}" },
            onSelect = { state.draftUserRole = it },
        )
        if (isPatientRole) {
            DropdownField(
                label = "Terapeuta asignado",
                selectedLabel = data?.therapists?.firstOrNull { it.id == state.draftUserTherapistId }?.fullName,
                placeholder = "Elige un terapeuta",
                options = data?.therapists.orEmpty().map { it.id to it.fullName },
                onSelect = { state.draftUserTherapistId = it },
            )
            if (editing == null) {
                CalendarField(
                    value = state.draftUserBirthDate,
                    placeholder = "Seleccionar fecha de nacimiento",
                    onDateSelected = { state.draftUserBirthDate = it },
                )
                OutlinedTextField(state.draftUserProgram, { state.draftUserProgram = it }, Modifier.fillMaxWidth(),
                    label = { Text("Programa y semestre") }, singleLine = true)
                OutlinedTextField(state.draftUserCondition, { state.draftUserCondition = it }, Modifier.fillMaxWidth(),
                    label = { Text("Condición en seguimiento") }, singleLine = true)
            }
        }
        if (editing == null) {
            OutlinedTextField(
                state.draftUserPassword, { state.draftUserPassword = it }, Modifier.fillMaxWidth(),
                label = { Text("Contraseña temporal") }, singleLine = true,
                visualTransformation = if (initialPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordVisibilityToggle(initialPasswordVisible) {
                        initialPasswordVisible = !initialPasswordVisible
                    }
                },
            )
            PasswordStrengthIndicator(initialPasswordAssessment)
            if (state.draftUserPassword.isNotEmpty() && !initialPasswordAssessment.isValid) {
                Text(
                    initialPasswordAssessment.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "En su primer ingreso deberá cambiar esta contraseña antes de configurar 2FA. Comparte la contraseña temporal por un canal seguro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FieldLabel("Estado")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip(state.draftUserStatus == "ACTIVE", { state.draftUserStatus = "ACTIVE" }, "Activo")
                TabChip(state.draftUserStatus == "DISABLED", { state.draftUserStatus = "DISABLED" }, "Deshabilitado", MaterialTheme.colorScheme.error)
            }
        }
        AdminMessages(state)
        Button(
            onClick = { state.saveUser() },
            Modifier.fillMaxWidth().height(50.dp),
            enabled = !state.busy && (editing != null || initialPasswordAssessment.isValid),
        ) {
            Text(if (editing == null) "Crear usuario" else "Guardar cambios")
        }
        if (user != null) {
            SectionLabel("Acceso")
            if (user.locked) {
                OutlinedButton(onClick = { state.userAction("unlock") }, Modifier.fillMaxWidth()) { Text("Desbloquear cuenta") }
            }
            OutlinedButton(onClick = { state.userAction("reset_mfa") }, Modifier.fillMaxWidth()) {
                Text("Restablecer 2FA (perdió el teléfono)")
            }
            OutlinedTextField(
                state.draftActionPassword, { state.draftActionPassword = it }, Modifier.fillMaxWidth(),
                label = { Text("Nueva contraseña temporal") }, singleLine = true,
                visualTransformation = if (actionPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordVisibilityToggle(actionPasswordVisible) {
                        actionPasswordVisible = !actionPasswordVisible
                    }
                },
            )
            PasswordStrengthIndicator(actionPasswordAssessment)
            if (state.draftActionPassword.isNotEmpty() && !actionPasswordAssessment.isValid) {
                Text(
                    actionPasswordAssessment.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(
                onClick = { state.userAction("set_password") },
                Modifier.fillMaxWidth(),
                enabled = actionPasswordAssessment.isValid,
            ) { Text("Restablecer contraseña") }
            // El super administrador ya tiene todo, y nadie ajusta sus propios permisos (lo valida también el servidor).
            val role = roles.firstOrNull { it.id == user.role }
            if (state.can("users.permissions") && role != null && !role.isLocked && user.id != state.sessionUser?.id?.toString()) {
                UserPermissionsSection(state, user, role)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Excepciones sobre el rol para ESTE usuario: otorgar o quitar un permiso sin cambiarle el rol. */
@Composable
private fun UserPermissionsSection(state: PrototypeState, user: AdminUserDto, role: AdminRoleDto) {
    val existing = user.overrides.filter { it.active }.associateBy { it.permission }
    val changed = state.draftOverrides.any { (permission, effect) -> existing[permission]?.effect != effect } ||
        existing.keys.any { it !in state.draftOverrides }
    val (included, notIncluded) = state.adminData?.permissions.orEmpty().partition { it.id in role.permissions }
    SectionLabel("Permisos individuales")
    Text(
        "Ajusta permisos solo para ${user.fullName}, sin cambiar su rol ni afectar a los demás usuarios con el rol ${role.name}. " +
            "Aplica de inmediato.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PermissionGroup(
        title = "Ya incluidos en su rol ${role.name} (${included.size})",
        hint = "Los tiene por su rol. Puedes quitarle alguno solo a esta persona.",
        permissions = included,
        fromRole = true,
        state = state,
        existing = existing,
    )
    PermissionGroup(
        title = "No incluidos en su rol (${notIncluded.size})",
        hint = "Su rol no los da. Puedes otorgarle alguno solo a esta persona.",
        permissions = notIncluded,
        fromRole = false,
        state = state,
        existing = existing,
    )
    OutlinedTextField(
        state.draftOverrideReason, { state.draftOverrideReason = it }, Modifier.fillMaxWidth(),
        label = { Text("Motivo (obligatorio para los cambios)") }, minLines = 2,
    )
    FieldLabel("Vence (opcional, válido hasta ese día inclusive)")
    CalendarField(
        value = state.draftOverrideExpiry,
        placeholder = "Sin vencimiento",
        onDateSelected = { state.draftOverrideExpiry = it },
    )
    if (state.draftOverrideExpiry.isNotBlank()) {
        OutlinedButton(onClick = { state.draftOverrideExpiry = "" }, Modifier.fillMaxWidth()) { Text("Quitar vencimiento") }
    }
    Button(
        onClick = { state.saveUserPermissions() },
        Modifier.fillMaxWidth().height(50.dp),
        enabled = !state.busy && changed,
    ) { Text("Guardar permisos individuales") }
}

@Composable
private fun PermissionGroup(
    title: String,
    hint: String,
    permissions: List<PermissionDto>,
    fromRole: Boolean,
    state: PrototypeState,
    existing: Map<String, UserOverrideDto>,
) {
    if (permissions.isEmpty()) return
    val s = LocalUsBrainSemantic.current
    // Solo se ofrece la excepción que cambia algo: quitar lo que el rol da, otorgar lo que no da.
    val overrideEffect = if (fromRole) "DENY" else "GRANT"
    FieldLabel(title)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    permissions.forEach { permission ->
        val overridden = state.draftOverrides[permission.id] == overrideEffect
        val hasIt = fromRole != overridden
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hasIt) "✓" else "✕",
                        color = if (hasIt) s.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(permission.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(permission.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (permission.sensitive) Pill("Sensible", MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (fromRole) "Incluido en el rol" else "No incluido en el rol",
                        if (fromRole) s.success else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (overridden) {
                        Pill(if (fromRole) "Quitado solo a esta persona" else "Otorgado solo a esta persona",
                            if (fromRole) MaterialTheme.colorScheme.error else Brand.Orange)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabChip(!overridden, { state.setOverride(permission.id, null) },
                        if (fromRole) "Mantener (según rol)" else "No otorgar (según rol)",
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    TabChip(overridden, { state.setOverride(permission.id, overrideEffect) },
                        if (fromRole) "Quitar a esta persona" else "Otorgar a esta persona",
                        if (fromRole) MaterialTheme.colorScheme.error else Brand.Orange)
                }
                existing[permission.id]?.takeIf { overridden && it.effect == overrideEffect }?.let { o ->
                    Text(
                        listOfNotNull(
                            o.reason,
                            o.expiresAt?.let { "vence $it" } ?: "sin vencimiento",
                            o.grantedBy?.let { "por $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/* ----------------------------- roles ----------------------------- */

@Composable
internal fun AdminRolesScreen(state: PrototypeState) {
    LaunchedEffect(Unit) { state.loadAdmin() }
    val roles = state.adminData?.roles.orEmpty()
    ScreenScaffold {
        Text("Roles y permisos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Cada rol es un conjunto de permisos y define qué interfaz ve. Los cambios aplican de inmediato.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminMessages(state)
        roles.forEach { role -> RoleRow(role) { state.openRole(role.id) } }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun RoleRow(role: AdminRoleDto, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(role.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (role.isLocked) Pill("Bloqueado", MaterialTheme.colorScheme.onSurfaceVariant)
            }
            role.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(PROFILE_LABELS[role.profile] ?: role.profile, MaterialTheme.colorScheme.primary)
                Pill("${role.permissions.size} permisos", MaterialTheme.colorScheme.onSurfaceVariant)
                Pill("${role.userCount} usuarios", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun AdminRoleEditScreen(state: PrototypeState) {
    val data = state.adminData
    val editing = state.editingRoleId
    val role = data?.roles?.firstOrNull { it.id == editing }
    val locked = role?.isLocked == true
    ScreenScaffold {
        BackHeader(if (editing == null) "Nuevo rol" else role?.name ?: "Rol") { state.screen = Screen.ADMIN_ROLES }
        if (locked) {
            Text(
                "Este rol tiene todos los permisos y no se puede modificar: garantiza que siempre exista alguien que administre el sistema.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editing == null) {
            OutlinedTextField(
                state.draftRoleId, { state.draftRoleId = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' } },
                Modifier.fillMaxWidth(), label = { Text("Código (ej.: SUPERVISOR)") }, singleLine = true,
            )
        }
        OutlinedTextField(state.draftRoleName, { state.draftRoleName = it }, Modifier.fillMaxWidth(),
            label = { Text("Nombre") }, singleLine = true, enabled = !locked)
        OutlinedTextField(state.draftRoleDescription, { state.draftRoleDescription = it }, Modifier.fillMaxWidth(),
            label = { Text("Descripción") }, minLines = 2, enabled = !locked)
        FieldLabel("Interfaz que ve")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PROFILE_LABELS.forEach { (id, label) ->
                TabChip(state.draftRoleProfile == id, { if (!locked) state.draftRoleProfile = id }, label)
            }
        }
        SectionLabel("Permisos")
        data?.permissions.orEmpty().groupBy { it.category }.forEach { (category, permissions) ->
            FieldLabel(category)
            permissions.forEach { permission ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !locked) { state.toggleRolePermission(permission.id) },
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(
                        checked = permission.id in state.draftRolePermissions,
                        onCheckedChange = { state.toggleRolePermission(permission.id) },
                        enabled = !locked,
                    )
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        Text(permission.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        permission.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        AdminMessages(state)
        if (!locked) {
            Button(onClick = { state.saveRole() }, Modifier.fillMaxWidth().height(50.dp), enabled = !state.busy) {
                Text(if (editing == null) "Crear rol" else "Guardar rol")
            }
        }
        if (role != null && !role.isSystem) {
            OutlinedButton(onClick = { state.deleteRole() }, Modifier.fillMaxWidth()) {
                Text("Borrar rol", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* ----------------------------- perfil ----------------------------- */

@Composable
internal fun AdminProfileScreen(state: PrototypeState) {
    // Los nombres legibles de los permisos vienen del catálogo de la administración.
    LaunchedEffect(Unit) { if (state.adminData == null) state.loadAdmin() }
    val user = state.sessionUser
    val names = state.adminData?.permissions.orEmpty().associate { it.id to it.name }
    ScreenScaffold {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(21.dp)).background(Brand.Orange),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.userInitials, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
            Text(state.userName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${user?.roleName.orEmpty().ifBlank { "Administración" }} · Universidad de San Buenaventura",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Correo", user?.email.orEmpty())
                InfoRow("Rol", user?.roleName.orEmpty().ifBlank { user?.role.orEmpty() })
                InfoRow("Permisos", "${state.permissions.size}")
            }
        }
        SectionLabel("Lo que puedes hacer")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.permissions.sorted().forEach { id -> Pill(names[id] ?: id, MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SecuritySettingsCard(state)
        OutlinedButton(onClick = { state.logout() }, Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
        Spacer(Modifier.height(24.dp))
    }
}
