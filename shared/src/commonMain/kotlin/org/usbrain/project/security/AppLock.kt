package org.usbrain.project.security

import org.usbrain.project.data.SecureStorage
import kotlin.random.Random

/**
 * Bloqueo de la app con PIN (y biometría opcional), como en la app de un banco.
 *
 * - El PIN es por dispositivo y por usuario: se guarda solo su hash (SHA-256 iterado con
 *   una sal aleatoria) en SecureStorage, que ya está cifrado con el Keystore / Keychain.
 * - El PIN nunca viaja al servidor. Su función es que nadie que tome el teléfono
 *   desbloqueado pueda usar la sesión guardada.
 * - Tras [MAX_ATTEMPTS] fallos seguidos se borra la sesión y hay que volver a entrar con
 *   contraseña y 2FA (lo decide PrototypeState).
 */
object AppLock {
    const val PIN_LENGTH = 6
    const val MAX_ATTEMPTS = 5
    /** Segundos en segundo plano antes de volver a pedir el PIN. */
    const val LOCK_AFTER_SECONDS = 60L
    private const val ITERATIONS = 20_000

    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_OWNER = "pin_owner"
    private const val KEY_FAILS = "pin_fails"
    private const val KEY_BIOMETRIC = "pin_biometric"

    /** ¿Este dispositivo tiene un PIN guardado (de cualquier usuario)? */
    val isConfigured: Boolean get() = SecureStorage.get(KEY_HASH) != null

    fun hasPinFor(userId: Long): Boolean = isConfigured && SecureStorage.get(KEY_OWNER) == userId.toString()

    val failures: Int get() = SecureStorage.get(KEY_FAILS)?.toIntOrNull() ?: 0
    val remainingAttempts: Int get() = (MAX_ATTEMPTS - failures).coerceAtLeast(0)

    var biometricEnabled: Boolean
        get() = SecureStorage.get(KEY_BIOMETRIC) == "1"
        set(value) = SecureStorage.put(KEY_BIOMETRIC, if (value) "1" else null)

    fun isValidFormat(pin: String) = pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    /** PIN demasiado obvio (todos iguales o secuencia), como rechazan los bancos. */
    fun isWeak(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val ascending = "0123456789012345"
        val descending = "9876543210987654"
        return ascending.contains(pin) || descending.contains(pin)
    }

    fun setPin(userId: Long, pin: String) {
        require(isValidFormat(pin)) { "El PIN debe tener $PIN_LENGTH dígitos" }
        val salt = Random.nextBytes(16)
        SecureStorage.put(KEY_SALT, Sha256.hex(salt))
        SecureStorage.put(KEY_HASH, Sha256.hex(derive(pin, salt)))
        SecureStorage.put(KEY_OWNER, userId.toString())
        SecureStorage.put(KEY_FAILS, null)
    }

    /** Verifica el PIN. Un fallo suma al contador; un acierto lo reinicia. */
    fun verify(pin: String): Boolean {
        val stored = SecureStorage.get(KEY_HASH) ?: return false
        val salt = SecureStorage.get(KEY_SALT)?.let(::fromHex) ?: return false
        val ok = isValidFormat(pin) && constantTimeEquals(Sha256.hex(derive(pin, salt)), stored)
        SecureStorage.put(KEY_FAILS, if (ok) null else (failures + 1).toString())
        return ok
    }

    /** Un desbloqueo biométrico exitoso también reinicia el contador de fallos. */
    fun resetFailures() = SecureStorage.put(KEY_FAILS, null)

    fun clear() {
        listOf(KEY_HASH, KEY_SALT, KEY_OWNER, KEY_FAILS, KEY_BIOMETRIC).forEach { SecureStorage.put(it, null) }
    }

    // SHA-256 encadenado: encarece probar los 1.000.000 de PINes si alguien extrae el hash.
    internal fun derive(pin: String, salt: ByteArray): ByteArray {
        var value = Sha256.digest(salt + pin.encodeToByteArray())
        repeat(ITERATIONS - 1) { value = Sha256.digest(value + salt) }
        return value
    }

    private fun fromHex(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
