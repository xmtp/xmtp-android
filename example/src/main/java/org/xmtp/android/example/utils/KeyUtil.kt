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
        const val LEGACY_PREFS_NAME = "EncryptionPref" // Old name for migration
        const val SETTINGS_PREFS = "SettingsPref"
        const val KEY_ENVIRONMENT = "xmtp_environment"
        const val KEY_HIDE_DELETED_MESSAGES = "hide_deleted_messages"
        const val KEY_MIGRATION_COMPLETE = "migration_complete"

        // Key prefixes
        const val PREFIX_DB_KEY = "xmtp-dev-"
        const val PREFIX_WALLET_KEY = "xmtp-wallet-"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            // Migrate from legacy prefs if needed
            migrateLegacyPrefsIfNeeded(prefs)
            prefs
        } catch (e: Exception) {
            Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular prefs")
            // Fallback to regular SharedPreferences if encryption fails (e.g., on some devices)
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Migrate keys from the legacy SharedPreferences name to the new one.
     * This prevents data loss for existing users when the prefs filename was changed.
     */
    private fun migrateLegacyPrefsIfNeeded(newPrefs: SharedPreferences) {
        // Check if migration is already complete
        if (newPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
            return
        }

        try {
            // Try to open the legacy prefs
            val legacyPrefs =
                EncryptedSharedPreferences.create(
                    context,
                    LEGACY_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )

            // Copy all entries from legacy to new
            val allEntries = legacyPrefs.all
            if (allEntries.isNotEmpty()) {
                Timber.d("Migrating ${allEntries.size} keys from legacy prefs")
                val editor = newPrefs.edit()
                for ((key, value) in allEntries) {
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, value as Set<String>)
                        }
                    }
                }
                editor.putBoolean(KEY_MIGRATION_COMPLETE, true)
                editor.apply()
                Timber.d("Migration complete")
            } else {
                // No legacy data, mark migration as complete
                newPrefs.edit().putBoolean(KEY_MIGRATION_COMPLETE, true).apply()
            }
        } catch (e: Exception) {
            // Legacy prefs don't exist or can't be read - that's fine for new users
            Timber.d("No legacy prefs to migrate: ${e.message}")
            newPrefs.edit().putBoolean(KEY_MIGRATION_COMPLETE, true).apply()
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
        encryptedPrefs
            .edit()
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
        encryptedPrefs
            .edit()
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
    fun retrieveEnvironment(): String? = settingsPrefs.getString(KEY_ENVIRONMENT, null)

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
    fun getHideDeletedMessages(): Boolean = settingsPrefs.getBoolean(KEY_HIDE_DELETED_MESSAGES, false)
}
