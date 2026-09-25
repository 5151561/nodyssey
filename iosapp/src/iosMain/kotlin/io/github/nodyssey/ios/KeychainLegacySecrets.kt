@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.nodyssey.data.security.SecretCipher
import io.github.plaza.core.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Security.SecItemCopyMatching
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData

/**
 * The secrets earlier builds kept in the Keychain, readable and nothing more.
 *
 * Those builds stored an image host's token or the proxy password as a Keychain item and left only a
 * handle in DataStore — `enc1:<uuid>`, marked like ciphertext so the shared code took it for one. The
 * item was `WhenUnlockedThisDeviceOnly`, which kept it out of every backup. The Keychain was dropped
 * because of how this app reaches a phone rather than because of anything in that code:
 *
 * - **A self-signed install cannot use it.** Every Keychain call needs an `application-identifier`
 *   entitlement, which comes from a provisioning profile at signing time. The `.ipa` this repository
 *   publishes is unsigned, and an ad-hoc signature — `ldid -S`, or TrollStore installing it as it is
 *   — supplies no entitlement, so `SecItemAdd` answered `errSecMissingEntitlement` (-34018) and no
 *   credential could be saved at all.
 * - **A re-signed install cannot read what the previous one wrote.** Items are scoped to the access
 *   group, so a re-sign under another team or bundle id found every handle in DataStore pointing at
 *   an item it could not see, and read every stored secret back blank.
 *
 * Those stores now hold the secret as typed, in a directory kept out of backup — see
 * `PlaintextSecretCipher` and `createPreferenceDataStore(keepOutOfBackup = true)`. What is left here is
 * the read, for `LegacySecretMigration` to carry each old item across on the first launch that can
 * reach it. The items themselves stay in the Keychain: nothing here writes or deletes, and a few dozen
 * unreachable bytes were already the cost the old design accepted for every overwritten setting.
 *
 * Delete this once no install older than the build that stopped writing handles can still be updated
 * in place — until then it is the one way their saved credentials survive the update.
 */
object KeychainLegacySecrets {
    /** The service every item was filed under, so they could be told from anyone else's. */
    private const val SERVICE = "io.github.nodyssey.secrets"

    /** The secret behind a handle an earlier build stored, or empty when the Keychain has none for it. */
    fun read(stored: String): String {
        if (!SecretCipher.isEncrypted(stored)) return ""
        val data = load(stored.removePrefix(SecretCipher.MARKER)) ?: return ""
        return data.toByteArray().decodeToString()
    }

    private fun load(handle: String): NSData? =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val query =
                mapOf<Any?, Any?>(
                    keyClass to valueClassGenericPassword,
                    keyService to SERVICE,
                    keyAccount to handle,
                    keyReturnData to true,
                )
            val found = asDictionary(query) { SecItemCopyMatching(it, result.ptr) == errSecSuccess }
            // Outliving the query dictionary is fine and is the point of `kSecReturnData`: what
            // `result` holds came back owned, which is the reference `CFBridgingRelease` consumes.
            if (!found) null else CFBridgingRelease(result.value) as? NSData
        }
}

/**
 * Hands [entries] to [use] as a `CFDictionary`, and releases it afterwards.
 *
 * `CFBridgingRetain` hands ownership *out* — it is `__bridge_retained`, and the +1 it returns is the
 * caller's to give back. Kotlin has no ARC to do that at the end of a scope, so a dictionary not
 * released here is one leaked per read.
 */
private inline fun <R> asDictionary(entries: Map<Any?, Any?>, use: (CFDictionaryRef) -> R): R {
    val dictionary = CFBridgingRetain(entries) as CFDictionaryRef
    return try {
        use(dictionary)
    } finally {
        CFRelease(dictionary)
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
private val keyReturnData = bridged(kSecReturnData)

// A *value* rather than a key, and bridged for the same reason: what goes into the dictionary has to
// be an Objective-C object either way. A raw `CFStringRef` here compiles — the map is `Map<Any?, Any?>`
// — and matches nothing.
private val valueClassGenericPassword = bridged(kSecClassGenericPassword)
