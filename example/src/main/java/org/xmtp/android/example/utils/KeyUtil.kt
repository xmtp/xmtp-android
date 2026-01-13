package org.xmtp.android.example.utils

import android.accounts.AccountManager
import android.content.Context
import android.util.Base64.NO_WRAP
import android.util.Base64.decode
import android.util.Base64.encodeToString
import org.xmtp.android.example.R

class KeyUtil(
    val context: Context,
) {
    private val PREFS_NAME = "EncryptionPref"
    private val PRIVATE_KEY_PREFS = "PrivateKeyPref"
    private val SETTINGS_PREFS = "SettingsPref"
    private val KEY_ENVIRONMENT = "xmtp_environment"
    private val KEY_HIDE_DELETED_MESSAGES = "hide_deleted_messages"

    fun loadKeys(): String? {
        val accountManager = AccountManager.get(context)
        val accounts =
            accountManager.getAccountsByType(context.getString(R.string.account_type))
        val account = accounts.firstOrNull() ?: return null
        return accountManager.getPassword(account)
    }

    fun storeKey(
        address: String,
        dbEncryptionKey: ByteArray?,
    ) {
        val alias = "xmtp-dev-${address.lowercase()}"

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(alias, encodeToString(dbEncryptionKey, NO_WRAP))
        editor.apply()
    }

    fun retrieveKey(address: String): ByteArray? {
        val alias = "xmtp-dev-${address.lowercase()}"

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyString = prefs.getString(alias, null)
        return if (keyString != null) {
            decode(keyString, NO_WRAP)
        } else {
            null
        }
    }

    // Store the wallet private key for signing
    fun storePrivateKey(
        address: String,
        privateKeyBytes: ByteArray,
    ) {
        val alias = "xmtp-wallet-${address.lowercase()}"
        val prefs = context.getSharedPreferences(PRIVATE_KEY_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(alias, encodeToString(privateKeyBytes, NO_WRAP)).apply()
    }

    // Retrieve the wallet private key for signing
    fun retrievePrivateKey(address: String): ByteArray? {
        val alias = "xmtp-wallet-${address.lowercase()}"
        val prefs = context.getSharedPreferences(PRIVATE_KEY_PREFS, Context.MODE_PRIVATE)
        val keyString = prefs.getString(alias, null)
        return if (keyString != null) {
            decode(keyString, NO_WRAP)
        } else {
            null
        }
    }

    // Clear the wallet private key
    fun clearPrivateKey(address: String) {
        val alias = "xmtp-wallet-${address.lowercase()}"
        val prefs = context.getSharedPreferences(PRIVATE_KEY_PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(alias).apply()
    }

    // Store the selected environment
    fun storeEnvironment(environment: String) {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ENVIRONMENT, environment).apply()
    }

    // Retrieve the selected environment
    fun retrieveEnvironment(): String? {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ENVIRONMENT, null)
    }

    // Clear the environment setting
    fun clearEnvironment() {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ENVIRONMENT).apply()
    }

    // Store hide deleted messages setting
    fun setHideDeletedMessages(hide: Boolean) {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HIDE_DELETED_MESSAGES, hide).apply()
    }

    // Retrieve hide deleted messages setting
    fun getHideDeletedMessages(): Boolean {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_DELETED_MESSAGES, false)
    }
}
