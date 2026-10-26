package org.usbrain.project.data

/**
 * Almacén clave-valor para la sesión (refresh token e identificador del dispositivo).
 *   · Android: SharedPreferences cifradas con una llave AES-GCM del Android Keystore.
 *   · iOS: Keychain, accesible solo en este dispositivo y tras el primer desbloqueo.
 *   · Web: el refresh token vive en sessionStorage (se pierde al cerrar la pestaña);
 *     el id de dispositivo, en localStorage.
 */
expect object SecureStorage {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** URL base de la API para esta plataforma (emulador Android → 10.0.2.2). */
expect val defaultApiBaseUrl: String
