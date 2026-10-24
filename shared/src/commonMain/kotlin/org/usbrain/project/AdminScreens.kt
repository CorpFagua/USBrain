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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.usbrain.project.data.AdminRoleDto
import org.usbrain.project.data.AdminUserDto

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
    "ROLE_CREATED" -> "Rol creado"
    "ROLE_UPDATED" -> "Rol modificado"
    "ROLE_DELETED" -> "Rol borrado"
    else -> type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/* ----------------------------- usuarios ----------------------------- */

@Composable
internal fun AdminUsersScreen(state: PrototypeState) {
    LaunchedEffect(Unit) { state.loadAdmin() }
    val users = state.adminData?.users.orEmpty()
    ScreenScaffold {
        Text("Usuarios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (state.can("users.manage")) "Toca un usuario para cambiar su rol, su estado o restablecer su acceso."
            else "Consulta de usuarios (solo lectura).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AdminMessages(state)
        if (state.adminData == null && state.adminError == null) Text("Cargando…", style = MaterialTheme.typography.bodySmall)
        users.forEach { user -> UserRow(user, enabled = state.can("users.manage")) { state.openUser(user.id) } }
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
                Pill(if (user.mfaEnabled) "2FA activo" else "Sin 2FA", MaterialTheme.colorScheme.onSurfaceVariant)
                user.therapistName?.let { Pill("Terapeuta: $it", MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
internal fun AdminUserEditScreen(state: PrototypeState) {
    val data = state.adminData
    val editing = state.editingUserId
    val user = data?.users?.firstOrNull { it.id == editing }
    val roles = data?.roles.orEmpty()
    val isPatientRole = state.draftRoleProfileOf(state.draftUserRole) == "PATIENT"
    ScreenScaffold {
        BackHeader(if (editing == null) "Nuevo usuario" else "Editar usuario") { state.screen = Screen.ADMIN_USERS }
        if (editing == null) {
            OutlinedTextField(
                state.draftUserEmail, { state.draftUserEmail = it.trim() }, Modifier.fillMaxWidth(),
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
                OutlinedTextField(state.draftUserBirthDate, { state.draftUserBirthDate = it }, Modifier.fillMaxWidth(),
                    label = { Text("Fecha de nacimiento (AAAA-MM-DD)") }, singleLine = true)
                OutlinedTextField(state.draftUserProgram, { state.draftUserProgram = it }, Modifier.fillMaxWidth(),
                    label = { Text("Programa y semestre") }, singleLine = true)
                OutlinedTextField(state.draftUserCondition, { state.draftUserCondition = it }, Modifier.fillMaxWidth(),
                    label = { Text("Condición en seguimiento") }, singleLine = true)
            }
        }
        if (editing == null) {
            OutlinedTextField(
                state.draftUserPassword, { state.draftUserPassword = it }, Modifier.fillMaxWidth(),
                label = { Text("Contraseña inicial (mínimo 10 caracteres)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(
                "En su primer ingreso configurará su app autenticadora (2FA) y su PIN.",
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
        Button(onClick = { state.saveUser() }, Modifier.fillMaxWidth().height(50.dp), enabled = !state.busy) {
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
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedButton(
                onClick = { state.userAction("set_password") },
                Modifier.fillMaxWidth(),
                enabled = state.draftActionPassword.length >= 10,
            ) { Text("Restablecer contraseña") }
        }
        Spacer(Modifier.height(24.dp))
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
