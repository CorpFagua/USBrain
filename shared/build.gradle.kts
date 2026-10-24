import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// URL de la API para un teléfono físico, leída de local.properties (no se versiona):
//   usbrain.android.apiBaseUrl=http://<IP del Mac>:8080/ords/usbrain/v1
//   usbrain.ios.apiBaseUrl=http://<nombre-del-Mac>.local:8080/ords/usbrain/v1
//   usbrain.web.apiBaseUrl=http://localhost:8080/ords/usbrain/v1   (si tu ORDS usa otro puerto)
// Sin esas líneas se usan las del emulador (10.0.2.2) y el simulador / navegador (localhost:8080).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val androidApiBaseUrl: String = localProperties.getProperty("usbrain.android.apiBaseUrl").orEmpty().trim()
val iosApiBaseUrl: String = localProperties.getProperty("usbrain.ios.apiBaseUrl").orEmpty().trim()
val webApiBaseUrl: String = localProperties.getProperty("usbrain.web.apiBaseUrl").orEmpty().trim()
val generateApiConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/usbrain/kotlin")
    // Copias locales: la acción no puede referenciar el script (caché de configuración).
    val androidUrl = androidApiBaseUrl
    val iosUrl = iosApiBaseUrl
    val webUrl = webApiBaseUrl
    inputs.property("androidApiBaseUrl", androidUrl)
    inputs.property("iosApiBaseUrl", iosUrl)
    inputs.property("webApiBaseUrl", webUrl)
    outputs.dir(outputDir)
    doLast {
        fun literal(value: String) = if (value.isEmpty()) "null" else "\"$value\""
        val file = outputDir.get().file("org/usbrain/project/data/ApiConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// Archivo generado por shared/build.gradle.kts desde local.properties. No editar.
            |package org.usbrain.project.data
            |
            |internal object ApiConfig {
            |    val ANDROID_BASE_URL: String? = ${literal(androidUrl)}
            |    val IOS_BASE_URL: String? = ${literal(iosUrl)}
            |    val WEB_BASE_URL: String? = ${literal(webUrl)}
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    compilerOptions {
        // SecureStorage es un `expect object` (una implementación por plataforma).
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "usbrain.js"
                // ORDS (la API) ya ocupa el 8080: el servidor de desarrollo web usa el 8081.
                devServer = (devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer())
                    .apply { port = 8081 }
            }
        }
        binaries.executable()
    }

    android {
       namespace = "org.usbrain.project.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.ktor.client.cio)
            implementation(libs.androidx.biometric)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonMain {
            kotlin.srcDir(generateApiConfig)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.qrose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}