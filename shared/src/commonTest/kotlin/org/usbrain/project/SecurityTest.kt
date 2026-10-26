package org.usbrain.project

import org.usbrain.project.security.AppLock
import org.usbrain.project.security.Sha256
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityTest {

    @AfterTest
    fun cleanUp() = AppLock.clear()

    // Vectores oficiales de FIPS 180-4 / NIST.
    @Test
    fun sha256MatchesStandardVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(Sha256.digest(ByteArray(0))),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex(Sha256.digest("abc".encodeToByteArray())),
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex(Sha256.digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())),
        )
        // Un millón de "a": cruza muchos bloques de 64 bytes.
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            Sha256.hex(Sha256.digest(ByteArray(1_000_000) { 'a'.code.toByte() })),
        )
    }

    @Test
    fun weakPinsAreRejected() {
        listOf("000000", "111111", "123456", "654321", "234567", "987654").forEach {
            assertTrue(AppLock.isWeak(it), "$it debería ser débil")
        }
        listOf("246813", "193728", "500912").forEach { assertFalse(AppLock.isWeak(it), "$it no es débil") }
    }

    @Test
    fun pinIsStoredHashedAndCountsFailures() {
        AppLock.setPin(userId = 7, pin = "246813")
        assertTrue(AppLock.hasPinFor(7))
        assertFalse(AppLock.hasPinFor(8), "el PIN es de un usuario: otro usuario debe crear el suyo")

        assertFalse(AppLock.verify("246810"))
        assertFalse(AppLock.verify("12"))
        assertEquals(AppLock.MAX_ATTEMPTS - 2, AppLock.remainingAttempts)

        assertTrue(AppLock.verify("246813"))
        assertEquals(AppLock.MAX_ATTEMPTS, AppLock.remainingAttempts, "un acierto reinicia el contador")
    }

    @Test
    fun clearRemovesPinAndBiometricChoice() {
        AppLock.setPin(7, "246813")
        AppLock.biometricEnabled = true
        AppLock.clear()
        assertFalse(AppLock.isConfigured)
        assertFalse(AppLock.biometricEnabled)
    }
}
