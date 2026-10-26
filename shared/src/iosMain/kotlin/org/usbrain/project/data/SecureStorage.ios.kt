@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package org.usbrain.project.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

// iPhone físico: la URL de local.properties (usbrain.ios.apiBaseUrl), con el nombre .local del
// Mac (ATS permite HTTP a dominios .local con NSAllowsLocalNetworking). Simulador: localhost.
actual val defaultApiBaseUrl: String = ApiConfig.IOS_BASE_URL ?: "http://localhost:8080/ords/usbrain/v1"

actual object SecureStorage {
    private const val SERVICE = "org.usbrain.session"

    actual fun get(key: String): String? = memScoped {
        val query = baseQuery(key)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    actual fun put(key: String, value: String?) {
        val deleteQuery = baseQuery(key)
        SecItemDelete(deleteQuery)
        CFRelease(deleteQuery)
        if (value == null) return

        @Suppress("CAST_NEVER_SUCCEEDS")
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val addQuery = baseQuery(key)
        CFDictionaryAddValue(addQuery, kSecValueData, CFBridgingRetain(data))
        CFDictionaryAddValue(addQuery, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(addQuery, null)
        CFRelease(addQuery)
    }

    private fun baseQuery(key: String): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(null, 6, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, CFBridgingRetain(SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(key))
        return query
    }
}
