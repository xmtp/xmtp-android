package org.xmtp.android.library

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import uniffi.xmtpv3.FfiConsent
import uniffi.xmtpv3.FfiConsentCallback
import uniffi.xmtpv3.FfiConsentEntityType
import uniffi.xmtpv3.FfiConsentState
import uniffi.xmtpv3.FfiDeviceSyncKind
import uniffi.xmtpv3.FfiPreferenceCallback
import uniffi.xmtpv3.FfiPreferenceUpdate
import uniffi.xmtpv3.FfiSubscribeException
import uniffi.xmtpv3.FfiXmtpClient

enum class ConsentState {
    ALLOWED,
    DENIED,
    UNKNOWN;

    companion object {
        fun toFfiConsentState(option: ConsentState): FfiConsentState {
            return when (option) {
                ALLOWED -> FfiConsentState.ALLOWED
                DENIED -> FfiConsentState.DENIED
                UNKNOWN -> FfiConsentState.UNKNOWN
            }
        }

        fun fromFfiConsentState(option: FfiConsentState): ConsentState {
            return when (option) {
                FfiConsentState.ALLOWED -> ALLOWED
                FfiConsentState.DENIED -> DENIED
                FfiConsentState.UNKNOWN -> UNKNOWN
            }
        }
    }
}

enum class EntryType {
    ADDRESS,
    CONVERSATION_ID,
    INBOX_ID;

    companion object {
        fun toFfiConsentEntityType(option: EntryType): FfiConsentEntityType {
            return when (option) {
                ADDRESS -> FfiConsentEntityType.ADDRESS
                CONVERSATION_ID -> FfiConsentEntityType.CONVERSATION_ID
                INBOX_ID -> FfiConsentEntityType.INBOX_ID
            }
        }

        fun fromFfiConsentEntityType(option: FfiConsentEntityType): EntryType {
            return when (option) {
                FfiConsentEntityType.ADDRESS -> ADDRESS
                FfiConsentEntityType.CONVERSATION_ID -> CONVERSATION_ID
                FfiConsentEntityType.INBOX_ID -> INBOX_ID
            }
        }
    }
}

enum class PreferenceType {
    HMAC_KEYS;
}

data class ConsentRecord(
    val value: String,
    val entryType: EntryType,
    val consentType: ConsentState,
) {
    companion object {
        fun address(
            address: String,
            type: ConsentState = ConsentState.UNKNOWN,
        ): ConsentRecord {
            return ConsentRecord(address, EntryType.ADDRESS, type)
        }

        fun conversationId(
            groupId: String,
            type: ConsentState = ConsentState.UNKNOWN,
        ): ConsentRecord {
            return ConsentRecord(groupId, EntryType.CONVERSATION_ID, type)
        }

        fun inboxId(
            inboxId: String,
            type: ConsentState = ConsentState.UNKNOWN,
        ): ConsentRecord {
            return ConsentRecord(inboxId, EntryType.INBOX_ID, type)
        }
    }

    val key: String
        get() = "${entryType.name}-$value"
}

data class PrivatePreferences(
    var client: Client,
    private val ffiClient: FfiXmtpClient,
) {
    suspend fun syncConsent() {
        ffiClient.sendSyncRequest(FfiDeviceSyncKind.CONSENT)
    }

    suspend fun streamPreferenceUpdates(): Flow<PreferenceType> = callbackFlow {
        val preferenceCallback = object : FfiPreferenceCallback {
            override fun onPreferenceUpdate(preference: List<FfiPreferenceUpdate>) {
                preference.iterator().forEach {
                    when (it) {
                        is FfiPreferenceUpdate.Hmac -> trySend(PreferenceType.HMAC_KEYS)
                    }
                }
            }

            override fun onError(error: FfiSubscribeException) {
                Log.e("XMTP preference update stream", error.message.toString())
            }
        }

        val stream = ffiClient.conversations().streamPreferences(preferenceCallback)

        awaitClose { stream.end() }
    }

    suspend fun streamConsent(): Flow<ConsentRecord> = callbackFlow {
        val consentCallback = object : FfiConsentCallback {
            override fun onConsentUpdate(consent: List<FfiConsent>) {
                consent.iterator().forEach {
                    trySend(it.fromFfiConsent())
                }
            }

            override fun onError(error: FfiSubscribeException) {
                Log.e("XMTP consent stream", error.message.toString())
            }
        }

        val stream = ffiClient.conversations().streamConsent(consentCallback)

        awaitClose { stream.end() }
    }

    suspend fun setConsentState(entries: List<ConsentRecord>) {
        ffiClient.setConsentStates(entries.map { it.toFfiConsent() })
    }

    private fun ConsentRecord.toFfiConsent(): FfiConsent {
        return FfiConsent(
            EntryType.toFfiConsentEntityType(entryType),
            ConsentState.toFfiConsentState(consentType),
            value
        )
    }

    private fun FfiConsent.fromFfiConsent(): ConsentRecord {
        return ConsentRecord(
            entity,
            EntryType.fromFfiConsentEntityType(entityType),
            ConsentState.fromFfiConsentState(state),
        )
    }

    suspend fun addressState(address: String): ConsentState {
        return ConsentState.fromFfiConsentState(
            ffiClient.getConsentState(
                FfiConsentEntityType.ADDRESS,
                address
            )
        )
    }

    suspend fun conversationState(groupId: String): ConsentState {
        return ConsentState.fromFfiConsentState(
            ffiClient.getConsentState(
                FfiConsentEntityType.CONVERSATION_ID,
                groupId
            )
        )
    }

    suspend fun inboxIdState(inboxId: String): ConsentState {
        return ConsentState.fromFfiConsentState(
            ffiClient.getConsentState(
                FfiConsentEntityType.INBOX_ID,
                inboxId
            )
        )
    }
}
