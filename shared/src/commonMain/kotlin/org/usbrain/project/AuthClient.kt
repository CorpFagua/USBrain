package org.usbrain.project

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

expect fun authBaseUrl(): String

/** URL del endpoint ORDS, sin credenciales de Oracle en la app. */
object AuthConfig {
    val baseUrl: String get() = authBaseUrl()
}

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(
    val userId: String,
    val role: String,
    val token: String,
)

class AuthException(val statusCode: Int, message: String) : Exception(message)

expect fun createPlatformHttpClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient

class AuthClient(private val baseUrl: String = AuthConfig.baseUrl) {
    private val client = createPlatformHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun login(email: String, password: String): LoginResponse {
        val requestBody = json.encodeToString(LoginRequest(email.trim(), password))
        val response = client.post("${baseUrl.trimEnd('/')}/login") {
            setBody(TextContent(requestBody, ContentType.Application.Json))
        }
        if (response.status != HttpStatusCode.OK) {
            throw AuthException(response.status.value, response.bodyAsText())
        }
        return json.decodeFromString<LoginResponse>(response.bodyAsText())
    }
}
