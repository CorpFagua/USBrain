package org.usbrain.project

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

// 10.0.2.2 permite que el emulador Android acceda al localhost del computador.
actual fun authBaseUrl(): String = "http://10.0.2.2:8080/ords/usbrain/usbrain"