package org.xmtp.android.library

import uniffi.xmtpv3.FfiConsent
import uniffi.xmtpv3.FfiConsentEntityType
import uniffi.xmtpv3.FfiConsentState
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
                else -> FfiConsentState.UNKNOWN
            }
        }

        fun fromFfiConsentState(option: FfiConsentState): ConsentState {
            return when (option) {
                FfiConsentState.ALLOWED -> ALLOWED
                FfiConsentState.DENIED -> DENIED
                else -> UNKNOWN
            }
        }
    }
}

enum class EntryType {
    ADDRESS,
    GROUP_ID,
    INBOX_ID;

    companion object {
        fun toFfiConsentEntityType(option: EntryType): FfiConsentEntityType {
            return when (option) {
                ADDRESS -> FfiConsentEntityType.ADDRESS
                GROUP_ID -> FfiConsentEntityType.CONVERSATION_ID
                INBOX_ID -> FfiConsentEntityType.INBOX_ID
            }
        }

        fun fromFfiConsentEntityType(option: FfiConsentEntityType): EntryType {
            return when (option) {
                FfiConsentEntityType.ADDRESS -> ADDRESS
                FfiConsentEntityType.CONVERSATION_ID -> GROUP_ID
                FfiConsentEntityType.INBOX_ID -> INBOX_ID
            }
        }
    }
}

data class ConsentListEntry(
    val value: String,
    val entryType: EntryType,
    val consentType: ConsentState,
) {
    companion object {
        fun address(
            address: String,
            type: ConsentState = ConsentState.UNKNOWN,
        ): ConsentListEntry {
            return ConsentListEntry(address, EntryType.ADDRESS, type)
        }

        fun groupId(
            groupId: String,
            type: ConsentState = ConsentState.UNKNOWN,
        ): ConsentListEntry {
            return ConsentListEntry(groupId, EntryType.GROUP_ID, type)
        }

        fun inboxId(
            inboxId: String,
            type: ConsentState = ConsentState.UNKNOWN,
        ): ConsentListEntry {
            return ConsentListEntry(inboxId, EntryType.INBOX_ID, type)
        }
    }

    val key: String
        get() = "${entryType.name}-$value"
}

class ConsentList(
    val client: Client,
    private val ffiClient: FfiXmtpClient
) {
    suspend fun setConsentState(entries: List<ConsentListEntry>) {
        ffiClient.setConsentStates(entries.map { it.toFfiConsent() })
    }

    private fun ConsentListEntry.toFfiConsent(): FfiConsent {
        return FfiConsent(
            EntryType.toFfiConsentEntityType(entryType),
            ConsentState.toFfiConsentState(consentType),
            value
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

    suspend fun groupState(groupId: String): ConsentState {
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

data class PrivatePreferences(
    var client: Client,
    private val ffiClient: FfiXmtpClient,
    var consentList: ConsentList = ConsentList(client, ffiClient),
)
