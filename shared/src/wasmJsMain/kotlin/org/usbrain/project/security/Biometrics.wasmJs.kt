package org.usbrain.project.security

// En el navegador la sesión ya dura lo que la pestaña (sessionStorage): no hay PIN ni biometría.
actual val supportsAppLock: Boolean = false

actual object Biometrics {
    actual fun isAvailable(): Boolean = false

    actual suspend fun authenticate(title: String, subtitle: String): Boolean = false
}
