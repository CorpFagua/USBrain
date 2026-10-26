@file:OptIn(ExperimentalForeignApi::class)

package org.usbrain.project.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import kotlin.coroutines.resume

actual val supportsAppLock: Boolean = true

actual object Biometrics {
    actual fun isAvailable(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)

    // Touch ID / Face ID. El texto de Face ID lo pide iOS en Info.plist (NSFaceIDUsageDescription).
    actual suspend fun authenticate(title: String, subtitle: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val context = LAContext()
            context.localizedFallbackTitle = "Usar PIN"
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, localizedReason = subtitle) { success, _ ->
                if (continuation.isActive) continuation.resume(success)
            }
            continuation.invokeOnCancellation { context.invalidate() }
        }
}
