package org.xmtp.android.example.utils

import android.accounts.AccountManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64.NO_WRAP
import android.util.Base64.decode
import android.util.Base64.encodeToString
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.xmtp.android.example.R
import timber.log.Timber

class KeyUtil(
    private val context: Context,
) {
    private companion object {
        const val ENCRYPTED_PREFS_NAME = "EncryptedKeyPref"
        const val SETTINGS_PREFS = "SettingsPref"
        const val KEY_ENVIRONMENT = "xmtp_environment"
        const val KEY_HIDE_DELETED_MESSAGES = "hide_deleted_messages"

        // Key prefixes
        const val PREFIX_DB_KEY = "xmtp-dev-"
        const val PREFIX_WALLET_KEY = "xmtp-wallet-"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular prefs")
            // Fallback to regular SharedPreferences if encryption fails (e.g., on some devices)
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val settingsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    }

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
        val alias = "$PREFIX_DB_KEY${address.lowercase()}"
        encryptedPrefs.edit()
            .putString(alias, encodeToString(dbEncryptionKey, NO_WRAP))
            .apply()
    }

    fun retrieveKey(address: String): ByteArray? {
        val alias = "$PREFIX_DB_KEY${address.lowercase()}"
        val keyString = encryptedPrefs.getString(alias, null)
        return keyString?.let {
            try {
                decode(it, NO_WRAP)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decode key")
                null
            }
        }
    }

    /**
     * Store the wallet private key for signing.
     * Uses EncryptedSharedPreferences backed by Android Keystore.
     */
    fun storePrivateKey(
        address: String,
        privateKeyBytes: ByteArray,
    ) {
        val alias = "$PREFIX_WALLET_KEY${address.lowercase()}"
        encryptedPrefs.edit()
            .putString(alias, encodeToString(privateKeyBytes, NO_WRAP))
            .apply()
    }

    /**
     * Retrieve the wallet private key for signing.
     * Uses EncryptedSharedPreferences backed by Android Keystore.
     */
    fun retrievePrivateKey(address: String): ByteArray? {
        val alias = "$PREFIX_WALLET_KEY${address.lowercase()}"
        val keyString = encryptedPrefs.getString(alias, null)
        return keyString?.let {
            try {
                decode(it, NO_WRAP)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decode private key")
                null
            }
        }
    }

    /**
     * Clear the wallet private key.
     */
    fun clearPrivateKey(address: String) {
        val alias = "$PREFIX_WALLET_KEY${address.lowercase()}"
        encryptedPrefs.edit().remove(alias).apply()
    }

    /**
     * Store the selected environment.
     */
    fun storeEnvironment(environment: String) {
        settingsPrefs.edit().putString(KEY_ENVIRONMENT, environment).apply()
    }

    /**
     * Retrieve the selected environment.
     */
    fun retrieveEnvironment(): String? {
        return settingsPrefs.getString(KEY_ENVIRONMENT, null)
    }

    /**
     * Clear the environment setting.
     */
    fun clearEnvironment() {
        settingsPrefs.edit().remove(KEY_ENVIRONMENT).apply()
    }

    /**
     * Store hide deleted messages setting.
     */
    fun setHideDeletedMessages(hide: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_HIDE_DELETED_MESSAGES, hide).apply()
    }

    /**
     * Retrieve hide deleted messages setting.
     */
    fun getHideDeletedMessages(): Boolean {
        return settingsPrefs.getBoolean(KEY_HIDE_DELETED_MESSAGES, false)
    }
}
