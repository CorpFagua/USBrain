package org.usbrain.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.usbrain.project.security.AppLock
import org.usbrain.project.security.supportsAppLock

/* ------------------------- contraseña temporal ------------------------- */

@Composable
internal fun FirstPasswordChangeScreen(state: PrototypeState) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    val assessment = assessPassword(state.firstPassword, state.loginEmail, exactLength = true)
    val isSameAsTemporary = state.firstPassword.isNotEmpty() && state.firstPassword == state.loginPassword
    ScreenScaffold {
        BackHeader("Crea una contraseña nueva") { state.cancelLogin() }
        Text(
            "Por seguridad, antes de configurar la verificación en dos pasos debes reemplazar la contraseña temporal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            state.firstPassword,
            { state.firstPassword = it },
            Modifier.fillMaxWidth(),
            label = { Text("Nueva contraseña") },
            singleLine = true,
            isError = state.firstPassword.isNotEmpty() && (!assessment.isValid || isSameAsTemporary),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible }
            },
        )
        Text(
            "Caracteres: ${passwordCharacterCount(state.firstPassword)}/10",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordStrengthIndicator(assessment)
        if (state.firstPassword.isNotEmpty() && (!assessment.isValid || isSameAsTemporary)) {
            Text(
                if (isSameAsTemporary) "La nueva contraseña debe ser distinta de la temporal."
                else assessment.message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            state.firstPasswordConfirmation,
            { state.firstPasswordConfirmation = it },
            Modifier.fillMaxWidth(),
            label = { Text("Confirma la nueva contraseña") },
            singleLine = true,
            isError = state.firstPasswordConfirmation.isNotEmpty() &&
                state.firstPassword != state.firstPasswordConfirmation,
            visualTransformation = if (confirmationVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                PasswordVisibilityToggle(confirmationVisible) { confirmationVisible = !confirmationVisible }
            },
        )
        Text(
            "Requisitos: exactamente 10 caracteres, al menos 3 tipos de caracteres (mayúsculas, minúsculas, números y símbolos) y no incluir el nombre de usuario del correo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.authError?.let { ErrorText(it) }
        Button(
            onClick = { state.submitFirstPasswordChange() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !state.busy && assessment.isValid && !isSameAsTemporary &&
                state.firstPassword == state.firstPasswordConfirmation,
        ) {
            Text(if (state.busy) "Actualizando…" else "Guardar y continuar a 2FA")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun PasswordStrengthIndicator(assessment: PasswordAssessment) {
    val strengthColor = when (assessment.score) {
        0, 1 -> MaterialTheme.colorScheme.error
        2 -> Brand.Orange
        3 -> Brand.Violet
        else -> LocalUsBrainSemantic.current.success
    }
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { index ->
                Surface(
                    modifier = Modifier.weight(1f).height(7.dp),
                    color = if (index < assessment.score) strengthColor
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ) {}
            }
        }
        Text(
            "Seguridad: ${assessment.label}",
            style = MaterialTheme.typography.labelMedium,
            color = strengthColor,
        )
    }
}

/* ----------------------------- olvido de contraseña ----------------------------- */

@Composable
internal fun ForgotPasswordScreen(state: PrototypeState) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmationVisible by remember { mutableStateOf(false) }
    val resetId = state.forgotResetId
    ScreenScaffold {
        BackHeader("Recuperar contraseña") { state.screen = Screen.LOGIN }
        if (resetId == null) {
            Text(
                "Escribe tu correo institucional. Para confirmar que eres tú usaremos el código de tu app autenticadora " +
                    "(o uno de tus códigos de recuperación).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                state.forgotEmail, { state.forgotEmail = sanitizeEmailInput(it) }, Modifier.fillMaxWidth(),
                label = { Text("Correo institucional") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            state.authError?.let { ErrorText(it) }
            Button(onClick = { state.submitForgotEmail() }, Modifier.fillMaxWidth().height(50.dp), enabled = !state.busy) {
                Text("Continuar")
            }
        } else {
            state.forgotInfo?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                state.forgotNewPassword, { state.forgotNewPassword = it }, Modifier.fillMaxWidth(),
                label = { Text("Nueva contraseña") }, singleLine = true,
                isError = state.forgotNewPassword.isNotEmpty() &&
                    !assessPassword(state.forgotNewPassword, state.forgotEmail, exactLength = true).isValid,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible }
                },
            )
            val passwordAssessment = assessPassword(
                state.forgotNewPassword,
                state.forgotEmail,
                exactLength = true,
            )
            Text(
                "Caracteres: ${passwordCharacterCount(state.forgotNewPassword)}/10",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PasswordStrengthIndicator(passwordAssessment)
            if (state.forgotNewPassword.isNotEmpty() && !passwordAssessment.isValid) {
                Text(
                    passwordAssessment.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                state.forgotConfirmPassword, { state.forgotConfirmPassword = it }, Modifier.fillMaxWidth(),
                label = { Text("Repite la nueva contraseña") }, singleLine = true,
                isError = state.forgotConfirmPassword.isNotEmpty() &&
                    state.forgotNewPassword != state.forgotConfirmPassword,
                visualTransformation = if (confirmationVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    PasswordVisibilityToggle(confirmationVisible) { confirmationVisible = !confirmationVisible }
                },
            )
            Text(
                "Usa exactamente 10 caracteres, combina al menos 3 tipos (mayúsculas, minúsculas, números y símbolos) y no incluyas el usuario del correo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                state.forgotCode,
                { v -> state.forgotCode = if (state.forgotUseRecovery) v.uppercase().take(11) else v.filter { it.isDigit() }.take(6) },
                Modifier.fillMaxWidth(),
                label = { Text(if (state.forgotUseRecovery) "Código de recuperación" else "Código de 6 dígitos") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (state.forgotUseRecovery) KeyboardType.Ascii else KeyboardType.NumberPassword),
            )
            TextButton(onClick = { state.forgotUseRecovery = !state.forgotUseRecovery; state.forgotCode = "" }) {
                Text(if (state.forgotUseRecovery) "Usar el código de la app autenticadora" else "No tengo mi teléfono: usar un código de recuperación")
            }
            state.authError?.let { ErrorText(it) }
            Button(
                onClick = { state.submitResetPassword() },
                Modifier.fillMaxWidth().height(50.dp),
                enabled = !state.busy &&
                    passwordAssessment.isValid &&
                    state.forgotNewPassword == state.forgotConfirmPassword,
            ) {
                Text("Cambiar contraseña")
            }
            Text(
                "Por seguridad, al cambiarla se cerrarán tus sesiones abiertas en otros dispositivos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "¿Perdiste el teléfono y los códigos de recuperación? Pide al administrador que restablezca tu acceso.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/* ----------------------------- PIN ----------------------------- */

/** Seis puntos que se llenan + teclado numérico, como en la app de un banco. */
@Composable
private fun PinPad(
    state: PrototypeState,
    showBiometric: Boolean = false,
    onBiometric: () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.semantics {
        contentDescription = "${state.pinInput.length} de ${AppLock.PIN_LENGTH} dígitos"
    }) {
        repeat(AppLock.PIN_LENGTH) { i ->
            val filled = i < state.pinInput.length
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    state.pinError?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(16.dp))
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { key ->
                    when (key) {
                        "bio" -> if (showBiometric) PinKey("Huella", "Desbloquear con huella o rostro", small = true, onClick = onBiometric) else Spacer(Modifier.size(68.dp))
                        "del" -> PinKey("⌫", "Borrar", onClick = { state.pinBackspace() })
                        else -> PinKey(key, key, onClick = { state.pinDigit(key.first()) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, description: String, small: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(68.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = if (small) 12.sp else 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun PinSetupScreen(state: PrototypeState) {
    val (title, subtitle) = when (state.pinStep) {
        PrototypeState.PinStep.CURRENT -> "Escribe tu PIN actual" to "Para cambiarlo, primero confirma el que usas hoy."
        PrototypeState.PinStep.NEW -> "Crea tu PIN de 6 dígitos" to
            "Lo pediremos cada vez que abras USBrain, como en la app de tu banco. No uses fechas ni secuencias."
        PrototypeState.PinStep.CONFIRM -> "Confirma tu PIN" to "Escríbelo una vez más."
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.pinChanging) {
            Box(Modifier.fillMaxWidth()) {
                CircleIconButton(onClick = { state.screen = state.currentProfile?.homeScreen ?: Screen.LOGIN }) {
                    NavGlyph("back", selected = false)
                }
            }
        } else {
            Spacer(Modifier.height(24.dp))
        }
        UsBrainMark(size = 52.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        PinPad(state)
    }
}

@Composable
internal fun BiometricOfferScreen(state: PrototypeState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        SuccessBadge()
        Text("PIN creado", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "¿Quieres desbloquear USBrain con tu huella o tu rostro? Es opcional: el PIN siempre seguirá funcionando.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { state.enableBiometric(fromOnboarding = true) }, Modifier.fillMaxWidth().height(50.dp)) {
            Text("Usar huella o rostro")
        }
        OutlinedButton(onClick = { state.skipBiometric() }, Modifier.fillMaxWidth()) { Text("Ahora no") }
        Text(
            "Puedes cambiarlo cuando quieras en tu perfil, en Seguridad.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LockScreen(state: PrototypeState) {
    val canUseBiometric = state.biometricEnabled && state.biometricAvailable
    // Si la huella está activada, se ofrece de una vez al mostrar la pantalla.
    LaunchedEffect(Unit) { if (canUseBiometric) state.unlockWithBiometric() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        UsBrainMark(size = 52.dp)
        Spacer(Modifier.height(8.dp))
        Text("Ingresa tu PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            state.sessionUser?.fullName?.let { "Hola, ${it.substringBefore(' ')}" } ?: "Tu sesión está protegida.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        PinPad(state, showBiometric = canUseBiometric, onBiometric = { state.unlockWithBiometric() })
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { state.forgotPin() }) { Text("¿Olvidaste tu PIN? Entra con tu contraseña") }
    }
}

/* ----------------------------- ajustes de seguridad (perfil) ----------------------------- */

@Composable
internal fun SecuritySettingsCard(state: PrototypeState) {
    SectionLabel("Seguridad")
    state.changeInfo?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = LocalUsBrainSemantic.current.success)
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Contraseña", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pide tu contraseña actual y el código de tu app autenticadora.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { state.startPasswordChange() }) { Text("Cambiar") }
            }
            if (supportsAppLock) PinSettingsRows(state)
            InfoRow("Verificación en dos pasos", "Activada ✓")
        }
    }
}

@Composable
private fun PinSettingsRows(state: PrototypeState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("PIN de acceso", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Se pide al abrir la app y tras ${AppLock.LOCK_AFTER_SECONDS} s en segundo plano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { state.startPinSetup(changing = true) }) { Text("Cambiar") }
    }
    if (state.biometricAvailable) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Huella o rostro", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Desbloquea sin escribir el PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.biometricEnabled,
                onCheckedChange = { on -> if (on) state.enableBiometric(fromOnboarding = false) else state.disableBiometric() },
            )
        }
    }
}

/* ----------------------------- cambio de contraseña (perfil) ----------------------------- */

@Composable
internal fun ChangePasswordScreen(state: PrototypeState) {
    var currentVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    val assessment = assessPassword(state.changeNewPassword, state.sessionUser?.email.orEmpty(), exactLength = true)
    val sameAsCurrent = state.changeNewPassword.isNotEmpty() && state.changeNewPassword == state.changeCurrentPassword
    val mismatch = state.changeConfirmPassword.isNotEmpty() && state.changeNewPassword != state.changeConfirmPassword
    ScreenScaffold {
        BackHeader("Cambiar contraseña") { state.closePasswordChange() }
        Text(
            "Por seguridad te pedimos tu contraseña actual y el código de tu app autenticadora. " +
                "Al cambiarla se cierra la sesión en tus otros dispositivos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            state.changeCurrentPassword, { state.changeCurrentPassword = it }, Modifier.fillMaxWidth(),
            label = { Text("Contraseña actual") }, singleLine = true,
            visualTransformation = if (currentVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { PasswordVisibilityToggle(currentVisible) { currentVisible = !currentVisible } },
        )
        OutlinedTextField(
            state.changeNewPassword, { state.changeNewPassword = it }, Modifier.fillMaxWidth(),
            label = { Text("Nueva contraseña") }, singleLine = true,
            isError = state.changeNewPassword.isNotEmpty() && (!assessment.isValid || sameAsCurrent),
            visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { PasswordVisibilityToggle(newVisible) { newVisible = !newVisible } },
        )
        Text(
            "Caracteres: ${passwordCharacterCount(state.changeNewPassword)}/10",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordStrengthIndicator(assessment)
        if (state.changeNewPassword.isNotEmpty() && (!assessment.isValid || sameAsCurrent)) {
            Text(
                if (sameAsCurrent) "La nueva contraseña debe ser distinta de la actual." else assessment.message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            state.changeConfirmPassword, { state.changeConfirmPassword = it }, Modifier.fillMaxWidth(),
            label = { Text("Confirma la nueva contraseña") }, singleLine = true,
            isError = mismatch,
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { PasswordVisibilityToggle(confirmVisible) { confirmVisible = !confirmVisible } },
        )
        if (mismatch) {
            Text("Las contraseñas no coinciden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            state.changeCode, { state.changeCode = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(),
            label = { Text("Código de la app autenticadora") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Text(
            "Requisitos: exactamente 10 caracteres, al menos 3 tipos de caracteres (mayúsculas, minúsculas, números y símbolos) y no incluir el nombre de usuario del correo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.changeError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { state.submitPasswordChange() },
            Modifier.fillMaxWidth().height(50.dp),
            enabled = !state.busy && assessment.isValid && !sameAsCurrent && !mismatch &&
                state.changeConfirmPassword.isNotEmpty() && state.changeCurrentPassword.isNotEmpty() && state.changeCode.length == 6,
        ) { Text("Cambiar contraseña") }
    }
}
