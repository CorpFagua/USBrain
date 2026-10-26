@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.usbrain.project.data

// usbrain.web.apiBaseUrl en local.properties si tu ORDS no está en el puerto 8080.
actual val defaultApiBaseUrl: String = ApiConfig.WEB_BASE_URL ?: "http://localhost:8080/ords/usbrain/v1"

// En el navegador no hay un almacén equivalente al Keystore/Keychain: el refresh token
// va en sessionStorage (dura lo que la pestaña) para limitar su exposición.
private fun storageGet(session: Boolean, key: String): String? =
    js("(session ? window.sessionStorage : window.localStorage).getItem(key)")

private fun storageSet(session: Boolean, key: String, value: String): Unit =
    js("(session ? window.sessionStorage : window.localStorage).setItem(key, value)")

private fun storageRemove(session: Boolean, key: String): Unit =
    js("(session ? window.sessionStorage : window.localStorage).removeItem(key)")

actual object SecureStorage {
    private fun isSessionScoped(key: String) = key != SessionKeys.DEVICE_ID

    actual fun get(key: String): String? = storageGet(isSessionScoped(key), key)

    actual fun put(key: String, value: String?) {
        if (value == null) storageRemove(isSessionScoped(key), key) else storageSet(isSessionScoped(key), key, value)
    }
}
