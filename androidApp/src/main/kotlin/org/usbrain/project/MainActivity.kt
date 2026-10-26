package org.usbrain.project

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import org.usbrain.project.data.SecureStorage
import org.usbrain.project.security.Biometrics

// FragmentActivity (en vez de ComponentActivity) porque BiometricPrompt la necesita.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // La sesión (refresh token) se guarda cifrada con una llave del Android Keystore.
        SecureStorage.init(applicationContext)
        Biometrics.attach(this)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}