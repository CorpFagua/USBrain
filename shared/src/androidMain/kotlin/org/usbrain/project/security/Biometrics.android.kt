package org.usbrain.project.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

actual val supportsAppLock: Boolean = true

actual object Biometrics {
    // Huella o rostro de cualquier nivel; solo reemplaza escribir el PIN.
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
    private var activityRef: WeakReference<FragmentActivity>? = null

    /** MainActivity se registra al crearse: BiometricPrompt necesita la actividad visible. */
    fun attach(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    actual fun isAvailable(): Boolean {
        val activity = activityRef?.get() ?: return false
        return BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    actual suspend fun authenticate(title: String, subtitle: String): Boolean {
        val activity = activityRef?.get() ?: return false
        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    // Cancelado, "Usar PIN" o demasiados intentos: se vuelve al teclado del PIN.
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    // onAuthenticationFailed = un dedo no reconocido; el diálogo sigue abierto.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Usar PIN")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
