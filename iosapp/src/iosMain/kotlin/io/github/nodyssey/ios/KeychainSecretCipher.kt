@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.security.SecretCipher
import io.github.plaza.core.toByteArray
import io.github.plaza.core.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * [SecretCipher] on the Keychain — and it stores rather than encrypts, which is the whole design.
 *
 * The Android side has a Keystore key and runs AES over the secret, because Android has no
 * general-purpose secret *store*: the key can be protected but the ciphertext still has to live in
 * DataStore. Apple has the store itself, so the honest implementation of this interface here is to
 * put the secret in the Keychain and leave a handle in DataStore. What the caller gets back from
 * [encrypt] is `enc1:<uuid>` — a name, not a ciphertext — and nothing about the contract notices:
 * it asks for a string that can be written down and read back, and says nothing about the algorithm.
 *
 * That is also why this is *stronger* than the Android half rather than a substitute for it.
 * `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` keeps the item out of every backup and off every
 * other device, which is exactly the exposure the interface's KDoc describes.
 *
 * **One cost, stated:** overwriting a setting leaves the previous item behind, because [encrypt]
 * mints a new handle and has no way to know which old one it replaced — the interface hands it a
 * string and nothing else. They are a few dozen bytes each and they are unreachable, and the
 * alternative is a wider interface that every platform would have to answer for so that this one
 * could tidy up.
 */
class KeychainSecretCipher(
    private val service: String = KEYCHAIN_SERVICE,
) : SecretCipher {
    override fun encrypt(plaintext: String): String? {
        if (plaintext.isEmpty()) return ""
        val handle = NSUUID().UUIDString()
        val added = store(handle, plaintext.encodeToByteArray().toNSData())
        return if (added) SecretCipher.MARKER + handle else null
    }

    override fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        val handle = stored.removePrefix(SecretCipher.MARKER)
        val data = load(handle) ?: return ""
        return data.toByteArray().decodeToString()
    }

    private fun store(handle: String, data: NSData): Boolean {
        // An item under this name cannot already exist — the handle is freshly minted — but a delete
        // first is what makes the add idempotent if it ever does, and it costs one syscall.
        SecItemDelete(query(handle) as CFDictionaryRef)
        val attributes =
            CFBridgingRetain(
                mapOf<Any?, Any?>(
                    keyClass to valueClassGenericPassword,
                    keyService to service,
                    keyAccount to handle,
                    keyValueData to data,
                    keyAccessible to valueAccessible,
                ),
            ) as CFDictionaryRef
        return SecItemAdd(attributes, null) == errSecSuccess
    }

    private fun load(handle: String): NSData? =
        memScoped {
            val query =
                CFBridgingRetain(
                    mapOf<Any?, Any?>(
                        keyClass to valueClassGenericPassword,
                        keyService to service,
                        keyAccount to handle,
                        keyReturnData to true,
                    ),
                ) as CFDictionaryRef
            val result = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return@memScoped null
            CFBridgingRelease(result.value) as? NSData
        }

    private fun query(handle: String): CFDictionaryRef =
        CFBridgingRetain(
            mapOf<Any?, Any?>(
                keyClass to valueClassGenericPassword,
                keyService to service,
                keyAccount to handle,
            ),
        ) as CFDictionaryRef

    private companion object {
        /** The service every item of this app's is filed under, so they can be told from anyone else's. */
        const val KEYCHAIN_SERVICE = "io.github.nodyssey.secrets"
    }
}

/*
 * The Security framework's keys are `CFString` constants, and a Kotlin map has to be keyed by
 * something ObjC-shaped.
 *
 * The retain is what makes the release inside `CFBridgingRelease` balance: these are process
 * constants this file never owned a reference to, and consuming one would be an over-release of a
 * global — the kind that crashes something else, later, somewhere unrelated.
 */
private fun bridged(constant: CFStringRef?): NSString = CFBridgingRelease(CFRetain(constant)) as NSString

private val keyClass = bridged(kSecClass)
private val keyService = bridged(kSecAttrService)
private val keyAccount = bridged(kSecAttrAccount)
private val keyValueData = bridged(kSecValueData)
private val keyReturnData = bridged(kSecReturnData)
private val keyAccessible = bridged(kSecAttrAccessible)
private val valueAccessible = bridged(kSecAttrAccessibleWhenUnlockedThisDeviceOnly)

// A *value* rather than a key, and bridged for the same reason: what goes into the dictionary has to
// be an Objective-C object either way. A raw `CFStringRef` here compiles — the map is `Map<Any?, Any?>`
// — and stores nothing, which is the shape of failure this whole file is trying not to have.
private val valueClassGenericPassword = bridged(kSecClassGenericPassword)
