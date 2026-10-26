package org.usbrain.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.usbrain.project.data.UsBrainApi

@Composable
fun App() {
    var themeOverride by remember { mutableStateOf<Boolean?>(null) }
    val dark = themeOverride ?: isSystemInDarkTheme()

    UsBrainTheme(darkTheme = dark) {
        val scope = rememberCoroutineScope()
        val state = remember { PrototypeState(UsBrainApi(deviceName = getPlatform().name), scope) }
        // Al abrir: si hay una sesión guardada en el dispositivo se entra sin pedir login.
        LaunchedEffect(Unit) { state.start() }
        // Como en un banco: si la app estuvo un rato en segundo plano, al volver pide el PIN.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.onBackground() }
        LifecycleEventEffect(Lifecycle.Event.ON_START) { state.onForeground() }
        PrototypeApp(
            state = state,
            dark = dark,
            onToggleTheme = { themeOverride = !dark },
        )
    }
}
