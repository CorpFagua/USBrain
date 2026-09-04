package org.usbrain.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform