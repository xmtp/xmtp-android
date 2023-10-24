package org.xmtp.android.library

import kotlinx.coroutines.runBlocking
import org.xmtp.android.library.messages.ContactBundle
import org.xmtp.android.library.messages.ContactBundleBuilder
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.walletAddress
import java.util.Date

enum class AllowState {
    ALLOWED, BLOCKED, UNKNOWN
}

data class AllowListEntry(
    val value: String,
    val entryType: EntryType,
    val permissionType: AllowState,
) {
    enum class EntryType {
        ADDRESS
    }

    companion object {
        fun address(address: String, type: AllowState = AllowState.UNKNOWN): AllowListEntry {
            return AllowListEntry(address, EntryType.ADDRESS, type)
        }
    }

    val key: String
        get() = "${entryType.name}-$value"
}

class AllowList {
    private val entries: MutableMap<String, AllowState> = mutableMapOf()

    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        suspend fun load(client: Client): AllowList {
            val publicKey =
                client.privateKeyBundleV1.identityKey.publicKey.secp256K1Uncompressed.bytes
            val privateKey = client.privateKeyBundleV1.identityKey.secp256K1.bytes

            val identifier = uniffi.xmtp_dh.generatePrivatePreferencesTopicIdentifier(
                privateKey.toByteArray().toUByteArray().toList()
            )
            val envelopes = client.query(Topic.allowList(identifier))
            val allowList = AllowList()

            for (envelope in envelopes.envelopesList) {
                val payload = uniffi.xmtp_dh.eciesDecryptK256Sha3256(
                    publicKey.toByteArray().toUByteArray().toList(),
                    privateKey.toByteArray().toUByteArray().toList(),
                    envelope.message.toByteArray().toUByteArray().toList()
                )

                val entry = JSON.decodeFromString(AllowListEntry.serializer(), String(payload))

                allowList.entries[entry.key] = entry.permissionType
            }

            return allowList
        }

        @OptIn(ExperimentalUnsignedTypes::class)
        suspend fun publish(entry: AllowListEntry, client: Client) {
            val payload = JSON.encodeToString(entry)

            val publicKey =
                client.privateKeyBundleV1.identityKey.publicKey.secp256K1Uncompressed.bytes
            val privateKey = client.privateKeyBundleV1.identityKey.secp256K1.bytes
            val identifier = uniffi.xmtp_dh.generatePrivatePreferencesTopicIdentifier(
                privateKey.toByteArray().toUByteArray().toList()
            )

            val message = uniffi.xmtp_dh.eciesDecryptK256Sha3256(
                publicKey.toByteArray().toUByteArray().toList(),
                privateKey.toByteArray().toUByteArray().toList(),
                payload.toByteArray().toUByteArray().toList()
            )

            val envelope = EnvelopeBuilder.buildFromTopic(
                Topic.allowList(identifier),
                Date(),
                ByteArray(message.size) { message[it].toByte() })

            client.publish(listOf(envelope))
        }
    }

    fun allow(address: String): AllowListEntry {
        entries[AllowListEntry.address(address).key] = AllowState.ALLOWED

        return AllowListEntry.address(address, AllowState.ALLOWED)
    }

    fun block(address: String): AllowListEntry {
        entries[AllowListEntry.address(address).key] = AllowState.BLOCKED

        return AllowListEntry.address(address, AllowState.BLOCKED)
    }

    fun state(address: String): AllowState {
        val state = entries[AllowListEntry.address(address).key]

        return state ?: AllowState.UNKNOWN
    }
}

data class Contacts(
    var client: Client,
    val knownBundles: MutableMap<String, ContactBundle> = mutableMapOf(),
    val hasIntroduced: MutableMap<String, Boolean> = mutableMapOf(),
) {

    var allowList: AllowList = AllowList()

    suspend fun refreshAllowList() {
        allowList = AllowList.load(client)
    }

    fun isAllowed(address: String): Boolean {
        return allowList.state(address) == AllowState.ALLOWED
    }

    fun isBlocked(address: String): Boolean {
        return allowList.state(address) == AllowState.BLOCKED
    }

    suspend fun allow(addresses: List<String>) {
        for (address in addresses) {
            AllowList.publish(allowList.allow(address), client)
        }
    }

    suspend fun block(addresses: List<String>) {
        for (address in addresses) {
            AllowList.publish(allowList.block(address), client)
        }
    }

    fun has(peerAddress: String): Boolean =
        knownBundles[peerAddress] != null

    fun needsIntroduction(peerAddress: String): Boolean =
        hasIntroduced[peerAddress] != true

    fun find(peerAddress: String): ContactBundle? {
        val knownBundle = knownBundles[peerAddress]
        if (knownBundle != null) {
            return knownBundle
        }
        val response = runBlocking { client.query(topic = Topic.contact(peerAddress)) }

        if (response.envelopesList.isNullOrEmpty()) return null

        for (envelope in response.envelopesList) {
            val contactBundle = ContactBundleBuilder.buildFromEnvelope(envelope)
            knownBundles[peerAddress] = contactBundle
            val address = contactBundle.walletAddress
            if (address == peerAddress) {
                return contactBundle
            }
        }

        return null
    }
}
