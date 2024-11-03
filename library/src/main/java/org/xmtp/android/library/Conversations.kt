package org.xmtp.android.library

import android.util.Log
import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.xmtp.android.library.ConsentState.Companion.toFfiConsentState
import org.xmtp.android.library.GRPCApiClient.Companion.makeQueryRequest
import org.xmtp.android.library.Util.Companion.envelopeFromFFi
import org.xmtp.android.library.libxmtp.MessageV3
import org.xmtp.android.library.messages.DecryptedMessage
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.EnvelopeBuilder
import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.MessageV1Builder
import org.xmtp.android.library.messages.Pagination
import org.xmtp.android.library.messages.SealedInvitation
import org.xmtp.android.library.messages.SealedInvitationBuilder
import org.xmtp.android.library.messages.SignedPublicKeyBundle
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.createDeterministic
import org.xmtp.android.library.messages.decrypt
import org.xmtp.android.library.messages.getInvitation
import org.xmtp.android.library.messages.header
import org.xmtp.android.library.messages.involves
import org.xmtp.android.library.messages.recipientAddress
import org.xmtp.android.library.messages.senderAddress
import org.xmtp.android.library.messages.sentAt
import org.xmtp.android.library.messages.toSignedPublicKeyBundle
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.keystore.api.v1.Keystore
import org.xmtp.proto.keystore.api.v1.Keystore.GetConversationHmacKeysResponse.HmacKeyData
import org.xmtp.proto.keystore.api.v1.Keystore.GetConversationHmacKeysResponse.HmacKeys
import org.xmtp.proto.keystore.api.v1.Keystore.TopicMap.TopicData
import org.xmtp.proto.message.contents.Contact
import org.xmtp.proto.message.contents.Invitation
import uniffi.xmtpv3.FfiConversation
import uniffi.xmtpv3.FfiConversationCallback
import uniffi.xmtpv3.FfiConversations
import uniffi.xmtpv3.FfiCreateGroupOptions
import uniffi.xmtpv3.FfiDirection
import uniffi.xmtpv3.FfiEnvelope
import uniffi.xmtpv3.FfiGroupPermissionsOptions
import uniffi.xmtpv3.FfiListConversationsOptions
import uniffi.xmtpv3.FfiListMessagesOptions
import uniffi.xmtpv3.FfiMessage
import uniffi.xmtpv3.FfiMessageCallback
import uniffi.xmtpv3.FfiPermissionPolicySet
import uniffi.xmtpv3.FfiSubscribeException
import uniffi.xmtpv3.FfiV2SubscribeRequest
import uniffi.xmtpv3.FfiV2Subscription
import uniffi.xmtpv3.FfiV2SubscriptionCallback
import uniffi.xmtpv3.NoPointer
import uniffi.xmtpv3.org.xmtp.android.library.libxmtp.GroupPermissionPreconfiguration
import uniffi.xmtpv3.org.xmtp.android.library.libxmtp.PermissionPolicySet
import java.util.Date
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

data class Conversations(
    var client: Client,
    var conversationsByTopic: MutableMap<String, Conversation> = mutableMapOf(),
    private val libXMTPConversations: FfiConversations? = null,
) {

    companion object {
        private const val TAG = "CONVERSATIONS"
    }

    enum class ConversationOrder {
        CREATED_AT,
        LAST_MESSAGE;
    }

    suspend fun fromWelcome(envelopeBytes: ByteArray): Conversation {
        val conversation = libXMTPConversations?.processStreamedWelcomeMessage(envelopeBytes)
            ?: throw XMTPException("Client does not support Groups")
        if (conversation.groupMetadata().conversationType() == "dm") {
            return Conversation.Dm(Dm(client, conversation))
        } else {
            return Conversation.Group(Group(client, conversation))
        }
    }

    suspend fun newGroup(
        accountAddresses: List<String>,
        permissions: GroupPermissionPreconfiguration = GroupPermissionPreconfiguration.ALL_MEMBERS,
        groupName: String = "",
        groupImageUrlSquare: String = "",
        groupDescription: String = "",
        groupPinnedFrameUrl: String = "",
    ): Group {
        return newGroupInternal(
            accountAddresses,
            GroupPermissionPreconfiguration.toFfiGroupPermissionOptions(permissions),
            groupName,
            groupImageUrlSquare,
            groupDescription,
            groupPinnedFrameUrl,
            null
        )
    }

    suspend fun newGroupCustomPermissions(
        accountAddresses: List<String>,
        permissionPolicySet: PermissionPolicySet,
        groupName: String = "",
        groupImageUrlSquare: String = "",
        groupDescription: String = "",
        groupPinnedFrameUrl: String = "",
    ): Group {
        return newGroupInternal(
            accountAddresses,
            FfiGroupPermissionsOptions.CUSTOM_POLICY,
            groupName,
            groupImageUrlSquare,
            groupDescription,
            groupPinnedFrameUrl,
            PermissionPolicySet.toFfiPermissionPolicySet(permissionPolicySet)
        )
    }

    private suspend fun newGroupInternal(
        accountAddresses: List<String>,
        permissions: FfiGroupPermissionsOptions,
        groupName: String,
        groupImageUrlSquare: String,
        groupDescription: String,
        groupPinnedFrameUrl: String,
        permissionsPolicySet: FfiPermissionPolicySet?,
    ): Group {
        if (accountAddresses.size == 1 &&
            accountAddresses.first().lowercase() == client.address.lowercase()
        ) {
            throw XMTPException("Recipient is sender")
        }
        val falseAddresses =
            if (accountAddresses.isNotEmpty()) client.canMessageV3(accountAddresses)
                .filter { !it.value }.map { it.key } else emptyList()
        if (falseAddresses.isNotEmpty()) {
            throw XMTPException("${falseAddresses.joinToString()} not on network")
        }

        val group =
            libXMTPConversations?.createGroup(
                accountAddresses,
                opts = FfiCreateGroupOptions(
                    permissions = permissions,
                    groupName = groupName,
                    groupImageUrlSquare = groupImageUrlSquare,
                    groupDescription = groupDescription,
                    groupPinnedFrameUrl = groupPinnedFrameUrl,
                    customPermissionPolicySet = permissionsPolicySet
                )
            ) ?: throw XMTPException("Client does not support Groups")
        client.contacts.allowGroups(groupIds = listOf(group.id().toHex()))

        return Group(client, group)
    }

    // Sync from the network the latest list of conversations
    suspend fun syncConversations() {
        libXMTPConversations?.sync()
    }

    // Sync all existing local conversation data from the network (Note: call syncConversations() first to get the latest list of conversations)
    suspend fun syncAllConversations(): UInt? {
        return libXMTPConversations?.syncAllConversations()
    }

    suspend fun newConversation(peerAddress: String): Conversation {
        val dm = findOrCreateDm(peerAddress)
        return Conversation.Dm(dm)
    }

    suspend fun findOrCreateDm(peerAddress: String): Dm {
        if (peerAddress.lowercase() == client.address.lowercase()) {
            throw XMTPException("Recipient is sender")
        }
        val falseAddresses =
            client.canMessageV3(listOf(peerAddress)).filter { !it.value }.map { it.key }
        if (falseAddresses.isNotEmpty()) {
            throw XMTPException("${falseAddresses.joinToString()} not on network")
        }
        var dm = client.findDm(peerAddress)
        if (dm == null) {
            val dmConversation = libXMTPConversations?.createDm(peerAddress.lowercase())
                ?: throw XMTPException("Client does not support V3 Dms")
            dm = Dm(client, dmConversation)
            client.contacts.allowGroups(groupIds = listOf(dm.id))
        }
        return dm
    }

    suspend fun listGroups(
        after: Date? = null,
        before: Date? = null,
        limit: Int? = null,
    ): List<Group> {
        val ffiGroups = libXMTPConversations?.listGroups(
            opts = FfiListConversationsOptions(
                after?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                before?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                limit?.toLong()
            )
        ) ?: throw XMTPException("Client does not support V3 dms")

        return ffiGroups.map {
            Group(client, it)
        }
    }

    suspend fun listDms(
        after: Date? = null,
        before: Date? = null,
        limit: Int? = null,
    ): List<Dm> {
        val ffiDms = libXMTPConversations?.listDms(
            opts = FfiListConversationsOptions(
                after?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                before?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                limit?.toLong()
            )
        ) ?: throw XMTPException("Client does not support V3 dms")

        return ffiDms.map {
            Dm(client, it)
        }
    }

    suspend fun list(
        after: Date? = null,
        before: Date? = null,
        limit: Int? = null,
        order: ConversationOrder = ConversationOrder.CREATED_AT,
        consentState: ConsentState? = null,
    ): List<Conversation> {
        if (client.hasV2Client)
            throw XMTPException("Only supported for V3 only clients.")

        val ffiConversations = libXMTPConversations?.list(
            FfiListConversationsOptions(
                after?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                before?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                limit?.toLong()
            )
        ) ?: throw XMTPException("Client does not support V3 dms")

        val filteredConversations = filterByConsentState(ffiConversations, consentState)
        val sortedConversations = sortConversations(filteredConversations, order)

        return sortedConversations.map { it.toConversation() }
    }

    private fun sortConversations(
        conversations: List<FfiConversation>,
        order: ConversationOrder,
    ): List<FfiConversation> {
        return when (order) {
            ConversationOrder.LAST_MESSAGE -> {
                conversations.map { conversation ->
                    val message =
                        conversation.findMessages(
                            FfiListMessagesOptions(
                                null,
                                null,
                                1,
                                null,
                                FfiDirection.DESCENDING
                            )
                        )
                            .firstOrNull()
                    conversation to message?.sentAtNs
                }.sortedByDescending {
                    it.second ?: 0L
                }.map {
                    it.first
                }
            }

            ConversationOrder.CREATED_AT -> conversations
        }
    }

    private fun filterByConsentState(
        conversations: List<FfiConversation>,
        consentState: ConsentState?,
    ): List<FfiConversation> {
        return consentState?.let { state ->
            conversations.filter { it.consentState() == toFfiConsentState(state) }
        } ?: conversations
    }

    private fun FfiConversation.toConversation(): Conversation {
        return if (groupMetadata().conversationType() == "dm") {
            Conversation.Dm(Dm(client, this))
        } else {
            Conversation.Group(Group(client, this))
        }
    }

    fun getHmacKeys(
        request: Keystore.GetConversationHmacKeysRequest? = null,
    ): Keystore.GetConversationHmacKeysResponse {
        val thirtyDayPeriodsSinceEpoch = (Date().time / 1000 / 60 / 60 / 24 / 30).toInt()
        val hmacKeysResponse = Keystore.GetConversationHmacKeysResponse.newBuilder()

        var topics = conversationsByTopic

        if (!request?.topicsList.isNullOrEmpty()) {
            topics = topics.filter {
                request!!.topicsList.contains(it.key)
            }.toMutableMap()
        }
        // TODO
//        topics.iterator().forEach {
//            val conversation = it.value
//            val hmacKeys = HmacKeys.newBuilder()
//            if (conversation.keyMaterial != null) {
//                (thirtyDayPeriodsSinceEpoch - 1..thirtyDayPeriodsSinceEpoch + 1).iterator()
//                    .forEach { value ->
//                        val info = "$value-${client.address}"
//                        val hmacKey =
//                            Crypto.deriveKey(
//                                conversation.keyMaterial!!,
//                                ByteArray(0),
//                                info.toByteArray(Charsets.UTF_8),
//                            )
//                        val hmacKeyData = HmacKeyData.newBuilder()
//                        hmacKeyData.hmacKey = hmacKey.toByteString()
//                        hmacKeyData.thirtyDayPeriodsSinceEpoch = value
//                        hmacKeys.addValues(hmacKeyData)
//                    }
//                hmacKeysResponse.putHmacKeys(conversation.topic, hmacKeys.build())
//            }
//        }
        return hmacKeysResponse.build()
    }

    private suspend fun handleConsentProof(
        consentProof: Invitation.ConsentProofPayload,
        peerAddress: String,
    ) {
        val signature = consentProof.signature
        val timestamp = consentProof.timestamp

        if (!KeyUtil.validateConsentSignature(signature, client.address, peerAddress, timestamp)) {
            return
        }
        val contacts = client.contacts
        contacts.refreshConsentList()
        if (contacts.consentList.state(peerAddress) == ConsentState.UNKNOWN) {
            contacts.allow(listOf(peerAddress))
        }
    }

    fun stream(/*Maybe Put a way to specify group, dm, or both?*/): Flow<Conversation> = callbackFlow {
        val conversationCallback = object : FfiConversationCallback {
            override fun onConversation(conversation: FfiConversation) {
                if (conversation.groupMetadata().conversationType() == "dm") {
                    trySend(Conversation.Dm(Dm(client, conversation)))
                } else {
                    trySend(Conversation.Group(Group(client, conversation)))
                }
            }
            override fun onError(error: FfiSubscribeException) {
                Log.e("XMTP Conversation stream", error.message.toString())
            }
        }
        val stream = libXMTPConversations?.stream(conversationCallback)
            ?: throw XMTPException("Client does not support Groups")
        awaitClose { stream.end() }
    }

    fun streamAllMessages(/*Maybe Put a way to specify group, dm, or both?*/): Flow<DecodedMessage> = callbackFlow {
        val messageCallback = object : FfiMessageCallback {
            override fun onMessage(message: FfiMessage) {
                val conversation = client.findConversation(message.convoId.toHex())
                val decodedMessage = MessageV3(client, message).decodeOrNull()
                when (conversation?.version) {
                    Conversation.Version.DM -> {
                        decodedMessage?.let { trySend(it) }
                    }
                    else -> {
                        decodedMessage?.let { trySend(it) }
                    }
                }
            }

            override fun onError(error: FfiSubscribeException) {
                Log.e("XMTP all message stream", error.message.toString())
            }
        }

        val stream = libXMTPConversations?.streamAllMessages(messageCallback)
            ?: throw XMTPException("Client does not support Groups")

        awaitClose { stream.end() }
    }

    fun streamAllDecryptedMessages(): Flow<DecryptedMessage> = callbackFlow {
        val messageCallback = object : FfiMessageCallback {
            override fun onMessage(message: FfiMessage) {
                val conversation = client.findConversation(message.convoId.toHex())
                val decryptedMessage = MessageV3(client, message).decryptOrNull()

                when (conversation?.version) {
                    Conversation.Version.DM -> {
                        decryptedMessage?.let { trySend(it) }
                    }

                    else -> {
                        decryptedMessage?.let { trySend(it) }
                    }
                }
            }

            override fun onError(error: FfiSubscribeException) {
                Log.e("XMTP all message stream", error.message.toString())
            }
        }

        val stream = libXMTPConversations?.streamAllMessages(messageCallback)
            ?: throw XMTPException("Client does not support Groups")

        awaitClose { stream.end() }
    }
}