package org.usbrain.project.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Teléfono físico: la URL de local.properties (usbrain.android.apiBaseUrl).
// Emulador: llega al localhost del computador por 10.0.2.2.
actual val defaultApiBaseUrl: String = ApiConfig.ANDROID_BASE_URL ?: "http://10.0.2.2:8080/ords/usbrain/v1"

@SuppressLint("StaticFieldLeak")
actual object SecureStorage {
    private const val KEY_ALIAS = "usbrain_session_key"
    private const val PREFS = "usbrain_session"
    private var prefs: SharedPreferences? = null
    // Sin init(context) (pruebas en JVM) se guarda solo en memoria; la app siempre llama init.
    private val memory = mutableMapOf<String, String>()

    /** Se llama desde MainActivity antes de mostrar la app. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    actual fun get(key: String): String? {
        val preferences = prefs ?: return memory[key]
        val stored = preferences.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }.getOrElse {
            // Llave rotada o dato corrupto: se descarta y se pide login de nuevo.
            put(key, null)
            null
        }
    }

    actual fun put(key: String, value: String?) {
        val preferences = prefs ?: run {
            if (value == null) memory.remove(key) else memory[key] = value
            return
        }
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, encrypt(value))
        editor.apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val payload = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, payload, 0, 12))
        return cipher.doFinal(payload, 12, payload.size - 12).decodeToString()
    }
}
