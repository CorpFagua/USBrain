package org.usbrain.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    var themeOverride by remember { mutableStateOf<Boolean?>(null) }
    val dark = themeOverride ?: isSystemInDarkTheme()

    UsBrainTheme(darkTheme = dark) {
        val state = remember { PrototypeState() }
        PrototypeApp(
            state = state,
            dark = dark,
            onToggleTheme = { themeOverride = !dark },
        )
    }
}
