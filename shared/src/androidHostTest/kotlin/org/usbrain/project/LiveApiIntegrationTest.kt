package org.usbrain.project

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.usbrain.project.data.UsBrainApi
import org.usbrain.project.data.SecureStorage
import org.usbrain.project.data.SessionKeys
import org.usbrain.project.security.AppLock
import java.io.File
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Recorre la app real (el mismo PrototypeState que usan las pantallas) contra ORDS:
 * login → enrolamiento 2FA → terapeuta crea conducta, línea base y actividad →
 * paciente registra → la gráfica recibe la observación → la sesión persiste al reabrir.
 *
 * Solo corre con el backend levantado:  backend/tests/client_integration.sh
 * (que deja a los usuarios e2e.* sin 2FA y ejecuta esta prueba con USBRAIN_LIVE_API=1).
 */
class LiveApiIntegrationTest {

    private val enabled = System.getenv("USBRAIN_LIVE_API") == "1"
    private val PIN = "246813"
    private val env: Map<String, String> by lazy {
        val file = listOf(File("../backend/.env"), File("backend/.env")).first { it.exists() }
        file.readLines().filter { "=" in it && !it.startsWith("#") }
            .associate { it.substringBefore("=") to it.substringAfter("=") }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun clearPreviousDeviceSession() {
        AppLock.clear()
        SecureStorage.put(SessionKeys.REFRESH_TOKEN, null)
    }

    private fun newState() = PrototypeState(
        UsBrainApi(baseUrl = "http://localhost:8080/ords/usbrain/v1", deviceName = "Prueba JVM"),
        scope,
    )

    private suspend fun PrototypeState.awaitIdle() {
        delay(50)
        withTimeout(20_000) { while (busy) delay(50) }
    }

    /** Inicia sesión y enrola el autenticador (los usuarios e2e llegan sin 2FA). */
    private suspend fun PrototypeState.loginAndEnroll(email: String) {
        loginEmail = email
        loginPassword = env.getValue("DEV_SEED_PASSWORD")
        submitLogin()
        awaitIdle()
        assertNull(authError, "login de $email")
        assertEquals(Screen.TWO_FACTOR, screen)
        val enrollment = assertNotNull(enrollment, "$email debe llegar sin 2FA (corre client_integration.sh)")
        assertTrue(enrollment.otpauthUri.startsWith("otpauth://totp/USBrain:"))

        mfaCode = "000000"
        submitMfa()
        awaitIdle()
        assertEquals("Código incorrecto o vencido.", authError)

        mfaCode = totp(enrollment.secret)
        submitMfa()
        awaitIdle()
        assertEquals(Screen.RECOVERY_CODES, screen, "verificación TOTP: $authError")
        assertEquals(10, recoveryCodes.size)
        confirmRecoveryCodesSaved()
        awaitIdle()
        createPin()
    }

    /** Como en un banco: tras el 2FA se exige crear un PIN (aquí no hay biometría disponible). */
    private suspend fun PrototypeState.createPin() {
        assertEquals(Screen.PIN_SETUP, screen, "tras el 2FA se pide crear el PIN")
        typePin("123456")
        assertTrue(pinError.orEmpty().contains("menos obvio"), "un PIN en secuencia se rechaza")
        typePin(PIN)
        assertEquals(PrototypeState.PinStep.CONFIRM, pinStep)
        typePin("999999")
        assertEquals("Los PIN no coinciden. Escríbelo de nuevo.", pinError)
        typePin(PIN)
        typePin(PIN)
        awaitIdle()
        delay(300)
    }

    private fun PrototypeState.typePin(pin: String) = pin.forEach { pinDigit(it) }

    @Test
    fun therapistBuildsTheCaseAndPatientRecords() = runBlocking {
        if (!enabled) return@runBlocking

        // --- Terapeuta ---------------------------------------------------------
        val therapist = newState()
        therapist.start()
        assertEquals(Screen.LOGIN, therapist.screen)
        therapist.loginAndEnroll("e2e.therapist@usbrain.test")
        assertEquals(Role.THERAPIST, therapist.role)
        assertEquals(Screen.THERAPIST_HOME, therapist.screen)
        assertEquals("Terapeuta E2E", therapist.userName)
        val patient = therapist.patients.single()
        assertTrue(therapist.widgets.any { it.id == "w-escala" }, "catálogo de mediciones")

        therapist.openPatient(patient.id)
        therapist.startNewBehavior(patient.id)
        therapist.draftBehaviorName = "Morderse las uñas"
        therapist.draftDefinition = "Lleva los dedos a la boca y muerde."
        therapist.saveBehavior()
        therapist.awaitIdle()
        assertNull(therapist.behaviorError)
        assertEquals(Screen.THERAPIST_BASELINE_SETUP, therapist.screen)

        therapist.draftBaselineDimensions.add(Dimension.DURACION)
        therapist.configureBaseline()
        therapist.awaitIdle()
        val behavior = therapist.activeBehavior()
        assertEquals(listOf(Dimension.FRECUENCIA, Dimension.DURACION), behavior.tasks.map { it.dimension })
        assertTrue(behavior.baselineOpen)

        therapist.assignDiagnosis(patient.id, "f41.1", "Ansiedad generalizada")
        therapist.awaitIdle()
        assertEquals("F41.1", therapist.patient(patient.id).diagnoses.single().code)

        therapist.startNewTask(patient.id, behavior.id)
        therapist.draftTaskTitle = "Intensidad del impulso"
        therapist.draftSelectedWidgetId = "w-escala"
        therapist.assign()
        therapist.awaitIdle()
        assertNull(therapist.assignError)
        assertEquals(Screen.THERAPIST_ASSIGNED, therapist.screen)
        assertEquals("Intensidad del impulso", therapist.lastAssignedTask()?.title)

        therapist.logout()
        delay(500)
        assertEquals(Screen.LOGIN, therapist.screen)

        // --- Paciente ----------------------------------------------------------
        val patientApp = newState()
        patientApp.loginAndEnroll("e2e.patient@usbrain.test")
        assertEquals(Role.PATIENT, patientApp.role)
        assertEquals(Screen.PATIENT_HOME, patientApp.screen)
        val myBehavior = patientApp.me().behaviors.single { it.name == "Morderse las uñas" }
        val frequencyTask = myBehavior.tasks.first { it.dimension == Dimension.FRECUENCIA && it.widgetConfig == null }
        assertEquals(TaskStatus.PENDIENTE, frequencyTask.status)

        patientApp.openTask(frequencyTask.id, myBehavior.id)
        patientApp.taskInputValue = 7.0
        patientApp.taskObservationMinutes = 70
        patientApp.saveTask()
        patientApp.awaitIdle()
        assertNull(patientApp.taskError)
        assertEquals(Screen.PATIENT_DONE, patientApp.screen)
        assertEquals(TaskStatus.COMPLETADA, patientApp.activeTask().status)
        assertEquals(1, patientApp.streakDays(patientApp.me()))

        // El mismo día otra vez: el servidor lo rechaza y el mensaje llega a la pantalla.
        patientApp.openTask(frequencyTask.id, myBehavior.id)
        patientApp.taskInputValue = 2.0
        patientApp.saveTask()
        patientApp.awaitIdle()
        assertTrue(patientApp.taskError.orEmpty().startsWith("Ya existe un registro"), patientApp.taskError)

        // La intervención con la escala configurable (creada por el terapeuta).
        val scaleTask = patientApp.activeBehavior().tasks.single { it.widgetConfig?.id == "w-escala" }
        patientApp.openTask(scaleTask.id, myBehavior.id)
        patientApp.draftWidgetNumericValue = 6.0
        patientApp.saveWidgetEntry()
        patientApp.awaitIdle()
        assertNull(patientApp.taskError)
        assertEquals(6.0, patientApp.activeTask().numericWidgetSeries().single().second)

        // Con la línea base abierta, la actividad nueva se propuso en fase de línea base (como en el
        // prototipo), así que la observación cuenta como línea base: 7 episodios / 70 min = 0,1.
        // El cierre de la línea base al crear una actividad de intervención lo cubre app_e2e.py.
        val updated = patientApp.activeBehavior()
        assertTrue(updated.baselineOpen)
        assertEquals(Phase.LINEA_BASE, scaleTask.phase)
        assertEquals(1, updated.observationCount)
        assertEquals(0.1, updated.chartValuesFor(Dimension.FRECUENCIA).single().second)

        // Sesión persistente: una app "nueva" en el mismo dispositivo entra sin login.
        // …pero primero pide el PIN, como la app de un banco.
        val reopened = newState()
        reopened.start()
        assertEquals(Screen.LOCK, reopened.screen, "al reabrir, la sesión guardada se protege con el PIN")
        reopened.typePin("111222")
        assertEquals("PIN incorrecto. Te quedan 4 intentos.", reopened.pinError)
        reopened.typePin(PIN)
        withTimeout(20_000) { while (reopened.screen == Screen.LOCK || reopened.screen == Screen.SPLASH) delay(50) }
        assertEquals(Screen.PATIENT_HOME, reopened.screen)
        assertEquals(Role.PATIENT, reopened.role)

        reopened.logout()
        delay(500)
        val afterLogout = newState()
        afterLogout.start()
        assertEquals(Screen.LOGIN, afterLogout.screen)
    }

    @Test
    fun superadminManagesRolesFromTheApp() = runBlocking {
        if (!enabled) return@runBlocking
        val app = newState()
        app.start()
        app.loginAndEnroll("e2e.superadmin@usbrain.test")
        assertEquals(Role.ADMIN, app.role, "el super admin usa la interfaz de administración")
        assertEquals(Screen.ADMIN_HOME, app.screen)
        assertTrue(app.can("roles.manage") && app.can("users.manage"), "trae sus permisos del servidor")
        val visibleTabs = app.currentProfile!!.tabs.filter { it.permission == null || app.can(it.permission) }
        assertEquals(listOf("Panel", "Usuarios", "Roles", "Perfil"), visibleTabs.map { it.label })

        app.loadAdmin()
        withTimeout(20_000) { while (app.adminData == null && app.adminError == null) delay(50) }
        assertNull(app.adminError)
        assertTrue(app.adminData!!.permissions.size >= 11)

        // Crear un rol desde la pantalla de roles.
        app.screen = Screen.ADMIN_ROLES
        app.startNewRole()
        app.draftRoleId = "APP_E2E"
        app.draftRoleName = "Rol creado desde la app"
        app.draftRoleProfile = "THERAPIST"
        app.toggleRolePermission("case.view.all")
        app.toggleRolePermission("catalog.manage")
        app.saveRole()
        app.awaitIdle()
        assertNull(app.adminError)
        assertEquals(Screen.ADMIN_ROLES, app.screen)
        val created = app.adminData!!.roles.single { it.id == "APP_E2E" }
        assertEquals(listOf("case.view.all", "catalog.manage"), created.permissions)

        // Editarlo y borrarlo.
        app.openRole("APP_E2E")
        app.toggleRolePermission("catalog.manage")
        app.saveRole()
        app.awaitIdle()
        assertEquals(listOf("case.view.all"), app.adminData!!.roles.single { it.id == "APP_E2E" }.permissions)
        app.openRole("APP_E2E")
        app.deleteRole()
        app.awaitIdle()
        assertTrue(app.adminData!!.roles.none { it.id == "APP_E2E" })

        // El rol SUPERADMIN está bloqueado: el servidor rechaza cambiarlo y el mensaje llega a la pantalla.
        app.openRole("SUPERADMIN")
        app.toggleRolePermission("audit.view")
        app.saveRole()
        app.awaitIdle()
        assertEquals("El rol de super administrador no se puede modificar.", app.adminError)

        app.logout()
        delay(500)
    }

    private fun totp(secret: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val key = mutableListOf<Byte>()
        secret.uppercase().forEach { c ->
            buffer = (buffer shl 5) or alphabet.indexOf(c)
            bits += 5
            if (bits >= 8) {
                key += ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
            }
        }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 1000 / 30).array())
        val offset = hash.last().toInt() and 0x0F
        val binary = ByteBuffer.wrap(hash, offset, 4).int and 0x7FFFFFFF
        return (binary % 1_000_000).toString().padStart(6, '0')
    }
}
