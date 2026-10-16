package org.usbrain.project

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js

actual fun createPlatformHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
	HttpClient(Js, configure)
actual fun authBaseUrl(): String = "http://localhost:8082/ords/usbrain/usbrain"
