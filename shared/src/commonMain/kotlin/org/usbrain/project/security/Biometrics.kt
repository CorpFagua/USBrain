package org.usbrain.project.security

/**
 * Desbloqueo biométrico del sistema (huella o rostro):
 *   · Android: BiometricPrompt (androidx.biometric).
 *   · iOS: LocalAuthentication (Touch ID / Face ID).
 *   · Web: no disponible; el bloqueo con PIN tampoco aplica (la sesión ya dura lo que la pestaña).
 * La biometría solo reemplaza escribir el PIN: nunca es el único factor de la cuenta.
 */
expect object Biometrics {
    /** ¿El dispositivo tiene biometría configurada y disponible? */
    fun isAvailable(): Boolean

    /** Muestra el diálogo del sistema; true si el usuario se autenticó. */
    suspend fun authenticate(title: String, subtitle: String): Boolean
}

/** ¿Esta plataforma usa bloqueo con PIN? (Android e iOS sí; la web no.) */
expect val supportsAppLock: Boolean
