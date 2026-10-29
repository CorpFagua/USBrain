package org.usbrain.project.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

object SessionKeys {
    const val REFRESH_TOKEN = "refresh_token"
    const val DEVICE_ID = "device_id"
}

/** Error con el formato de la API: {"error": slug, "message": texto para el usuario}. */
class ApiException(val status: Int, val code: String, override val message: String) : Exception(message)

/** La sesión terminó (expiró, se revocó o se detectó reutilización): hay que volver al login. */
class SessionExpiredException(message: String = "Tu sesión expiró. Inicia sesión de nuevo.") : Exception(message)

/**
 * Cliente de la API de USBrain. Guarda el access token solo en memoria y el refresh
 * token en [SecureStorage]; renueva la sesión sola cuando el access token vence.
 */
class UsBrainApi(
    private val baseUrl: String = defaultApiBaseUrl,
    private val deviceName: String = "USBrain",
) {
    private val http = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json(ApiJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
        defaultRequest {
            // Stage por ngrok gratuito: sin este encabezado, ngrok responde a los navegadores con
            // una página de aviso en vez de la API. En local no tiene efecto (ORDS lo ignora).
            header("ngrok-skip-browser-warning", "true")
        }
    }

    private var accessToken: String? = null
    // Un solo refresh a la vez: dos en paralelo con el mismo token se interpretan como robo.
    private val refreshMutex = Mutex()

    val deviceId: String by lazy {
        SecureStorage.get(SessionKeys.DEVICE_ID) ?: newDeviceId().also { SecureStorage.put(SessionKeys.DEVICE_ID, it) }
    }

    val hasStoredSession: Boolean get() = SecureStorage.get(SessionKeys.REFRESH_TOKEN) != null

    // ------------------------------------------------------------ autenticación

    suspend fun login(email: String, password: String): LoginResponse =
        send<LoginResponse> {
            post(url("auth/login")) {
                json(buildJsonObject {
                    put("email", email.trim())
                    put("password", password)
                    put("device_id", deviceId)
                    put("device_name", deviceName)
                })
            }
        }

    suspend fun changeInitialPassword(
        challengeId: String,
        currentPassword: String,
        newPassword: String,
    ): LoginResponse =
        send<LoginResponse> {
            post(url("auth/password/first-login")) {
                json(buildJsonObject {
                    put("challenge_id", challengeId)
                    put("current_password", currentPassword)
                    put("new_password", newPassword)
                })
            }
        }

    suspend fun verifyMfa(challengeId: String, code: String): TokenResponse =
        send<TokenResponse> {
            post(url("auth/mfa/verify")) {
                json(buildJsonObject { put("challenge_id", challengeId); put("code", code) })
            }
        }.also(::storeTokens)

    suspend fun verifyRecoveryCode(challengeId: String, recoveryCode: String): TokenResponse =
        send<TokenResponse> {
            post(url("auth/mfa/recovery")) {
                json(buildJsonObject { put("challenge_id", challengeId); put("recovery_code", recoveryCode) })
            }
        }.also(::storeTokens)

    /** Olvido de contraseña, paso 1: siempre responde igual, exista o no el correo. */
    suspend fun forgotPassword(email: String): ForgotPasswordResponse =
        send<ForgotPasswordResponse> {
            post(url("auth/password/forgot")) {
                json(buildJsonObject {
                    put("email", email.trim())
                    put("device_id", deviceId)
                    put("device_name", deviceName)
                })
            }
        }

    /** Paso 2: el código de la app autenticadora (o uno de recuperación) prueba la identidad. */
    suspend fun resetPassword(resetId: String, code: String?, recoveryCode: String?, newPassword: String) {
        send<JsonObject> {
            post(url("auth/password/reset")) {
                json(buildJsonObject {
                    put("reset_id", resetId)
                    code?.let { put("code", it) }
                    recoveryCode?.let { put("recovery_code", it) }
                    put("new_password", newPassword)
                })
            }
        }
    }

    /**
     * Al abrir la app: si hay un refresh token guardado, recupera la sesión sin pedir login.
     * Si el servidor la cerró (inactividad, otro dispositivo…) lanza [SessionExpiredException] con el motivo.
     */
    suspend fun restoreSession(): SessionUser? {
        if (!hasStoredSession) return null
        return refresh(staleToken = null).user
    }

    /** Cambio de contraseña con sesión iniciada: exige la actual y el código 2FA; cierra los demás dispositivos. */
    suspend fun changePassword(currentPassword: String, newPassword: String, code: String) {
        postForId("auth/password", buildJsonObject {
            put("current_password", currentPassword)
            put("new_password", newPassword)
            put("code", code)
        }, expectId = false)
    }

    suspend fun logout() {
        try {
            authorized<JsonObject> { token -> http.post(url("auth/logout")) { bearer(token) } }
        } catch (_: Exception) {
            // Aunque el servidor no responda, la sesión local se borra igual.
        } finally {
            clearSession()
        }
    }

    fun clearSession() {
        accessToken = null
        SecureStorage.put(SessionKeys.REFRESH_TOKEN, null)
    }

    // ------------------------------------------------------------ datos clínicos

    suspend fun bootstrap(): BootstrapDto = authorized { token -> http.get(url("app/bootstrap")) { bearer(token) } }

    suspend fun createBehavior(body: JsonObject): String = postForId("app/behaviors", body)

    suspend fun configureBaseline(behaviorId: String, dimensions: List<String>) {
        postForId("app/behaviors/${behaviorId.encodeURLPathPart()}/baseline", buildJsonObject {
            put("dimensions", kotlinx.serialization.json.JsonArray(dimensions.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }, expectId = false)
    }

    suspend fun closeBaseline(behaviorId: String) {
        postForId("app/behaviors/${behaviorId.encodeURLPathPart()}/close-baseline", JsonObject(emptyMap()), expectId = false)
    }

    suspend fun createTask(body: JsonObject): String = postForId("app/tasks", body)

    suspend fun recordEntry(taskId: String, body: JsonObject): String =
        postForId("app/tasks/${taskId.encodeURLPathPart()}/entries", body)

    suspend fun recordWidgetEntry(taskId: String, body: JsonObject): String =
        postForId("app/tasks/${taskId.encodeURLPathPart()}/widget-entries", body)

    suspend fun createWidget(body: JsonObject): String = postForId("app/widgets", body)

    suspend fun createBehavioralFunction(label: String): String =
        postForId("app/behavioral-functions", buildJsonObject { put("label", label) })

    suspend fun assignDiagnosis(patientId: String, code: String, label: String) {
        postForId("app/patients/${patientId.encodeURLPathPart()}/diagnoses",
            buildJsonObject { put("code", code); put("label", label) }, expectId = false)
    }

    suspend fun removeDiagnosis(patientId: String, code: String) {
        authorized<JsonObject> { token ->
            http.delete(url("app/patients/${patientId.encodeURLPathPart()}/diagnoses/${code.encodeURLPathPart()}")) {
                bearer(token)
            }
        }
    }

    // ------------------------------------------------------------ administración

    suspend fun adminOverview(): AdminOverviewDto =
        authorized { token -> http.get(url("admin/overview")) { bearer(token) } }

    suspend fun adminCreateUser(body: JsonObject): String = postForId("admin/users", body)

    suspend fun adminUpdateUser(userId: String, body: JsonObject) {
        postForId("admin/users/${userId.encodeURLPathPart()}", body, expectId = false)
    }

    suspend fun adminUserAction(userId: String, action: String, password: String? = null) {
        postForId("admin/users/${userId.encodeURLPathPart()}/actions", buildJsonObject {
            put("action", action)
            password?.let { put("password", it) }
        }, expectId = false)
    }

    /** Reemplaza las excepciones individuales del usuario: {"overrides": [...]}. */
    suspend fun adminSetUserPermissions(userId: String, body: JsonObject) {
        postForId("admin/users/${userId.encodeURLPathPart()}/permissions", body, expectId = false)
    }

    suspend fun adminCreateRole(body: JsonObject) {
        postForId("admin/roles", body, expectId = false)
    }

    suspend fun adminUpdateRole(roleId: String, body: JsonObject) {
        postForId("admin/roles/${roleId.encodeURLPathPart()}", body, expectId = false)
    }

    suspend fun adminDeleteRole(roleId: String) {
        authorized<JsonObject> { token ->
            http.delete(url("admin/roles/${roleId.encodeURLPathPart()}")) { bearer(token) }
        }
    }

    // ------------------------------------------------------------ internos

    private suspend fun postForId(path: String, body: JsonObject, expectId: Boolean = true): String {
        val response = authorized<JsonObject> { token -> http.post(url(path)) { bearer(token); json(body) } }
        return if (expectId) ApiJson.decodeFromJsonElement(IdResponse.serializer(), response).id else ""
    }

    /** Ejecuta una petición autenticada; si el access token venció, renueva y reintenta una vez. */
    private suspend inline fun <reified T> authorized(crossinline request: suspend (String) -> HttpResponse): T {
        val token = accessToken ?: refresh(staleToken = null).accessToken
        val response = request(token)
        if (response.status.value == 401) {
            val fresh = refresh(staleToken = token).accessToken
            return decode(request(fresh))
        }
        return decode(response)
    }

    private suspend fun refresh(staleToken: String?): TokenResponse = refreshMutex.withLock {
        // Si otra petición ya renovó mientras esperábamos el turno, no se vuelve a renovar.
        val current = accessToken
        if (staleToken != null && current != null && current != staleToken) {
            return@withLock lastTokens ?: error("sin tokens")
        }
        val refreshToken = SecureStorage.get(SessionKeys.REFRESH_TOKEN) ?: run {
            clearSession()
            throw SessionExpiredException()
        }
        val response = http.post(url("auth/refresh")) {
            json(buildJsonObject { put("refresh_token", refreshToken); put("device_id", deviceId) })
        }
        if (response.status.value in 400..499) {
            clearSession()
            // El servidor explica el motivo (p. ej. se inició sesión en otro dispositivo).
            val message = runCatching { ApiJson.decodeFromString(ApiErrorBody.serializer(), response.bodyAsText()).message }
                .getOrNull()
            throw if (message.isNullOrBlank()) SessionExpiredException() else SessionExpiredException(message)
        }
        decode<TokenResponse>(response).also(::storeTokens)
    }

    private var lastTokens: TokenResponse? = null

    private fun storeTokens(tokens: TokenResponse) {
        lastTokens = tokens
        accessToken = tokens.accessToken
        SecureStorage.put(SessionKeys.REFRESH_TOKEN, tokens.refreshToken)
    }

    private suspend inline fun <reified T> send(request: HttpClient.() -> HttpResponse): T = decode(http.request())

    private suspend inline fun <reified T> decode(response: HttpResponse): T {
        if (response.status.isSuccess()) return response.body()
        val error = runCatching { ApiJson.decodeFromString(ApiErrorBody.serializer(), response.bodyAsText()) }
            .getOrDefault(ApiErrorBody(message = "El servidor respondió ${response.status.value}."))
        if (response.status.value == 401 && error.error in setOf("session_expired", "session_replaced", "token_expired", "unauthorized")) {
            clearSession()
            throw SessionExpiredException(error.message.ifBlank { "Tu sesión expiró. Inicia sesión de nuevo." })
        }
        throw ApiException(response.status.value, error.error, error.message)
    }

    private fun url(path: String) = "$baseUrl/$path"

    private fun HttpRequestBuilder.bearer(token: String) = header(HttpHeaders.Authorization, "Bearer $token")

    private fun HttpRequestBuilder.json(body: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun newDeviceId(): String =
        "usb-" + (1..24).map { "0123456789abcdefghijklmnopqrstuvwxyz"[Random.nextInt(36)] }.joinToString("")
}
