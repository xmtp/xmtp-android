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

    suspend fun conversationFromWelcome(envelopeBytes: ByteArray): Conversation {
        val conversation = libXMTPConversations?.processStreamedWelcomeMessage(envelopeBytes)
            ?: throw XMTPException("Client does not support Groups")
        if (conversation.groupMetadata().conversationType() == "dm") {
            return Conversation.Dm(Dm(client, conversation))
        } else {
            return Conversation.Group(Group(client, conversation))
        }
    }

    suspend fun fromWelcome(envelopeBytes: ByteArray): Group {
        val group = libXMTPConversations?.processStreamedWelcomeMessage(envelopeBytes)
            ?: throw XMTPException("Client does not support Groups")
        return Group(client, group)
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

    // Sync from the network the latest list of groups
    @Deprecated("Sync now includes DMs and Groups", replaceWith = ReplaceWith("syncConversations"))
    suspend fun syncGroups() {
        libXMTPConversations?.sync()
    }

    // Sync from the network the latest list of conversations
    suspend fun syncConversations() {
        libXMTPConversations?.sync()
    }

    // Sync all existing local conversation data from the network (Note: call syncConversations() first to get the latest list of conversations)
    suspend fun syncAllConversations(): UInt? {
        return libXMTPConversations?.syncAllConversations()
    }

    // Sync all existing local groups data from the network (Note: call syncGroups() first to get the latest list of groups)
    @Deprecated(
        "Sync now includes DMs and Groups",
        replaceWith = ReplaceWith("syncAllConversations")
    )
    suspend fun syncAllGroups(): UInt? {
        return libXMTPConversations?.syncAllConversations()
    }

    suspend fun findOrCreateDm(peerAddress: String): Dm {
        if (client.hasV2Client) throw XMTPException("Only supported for V3 only clients.")
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

    /**
     * This creates a new [Conversation] using a specified address
     * @param peerAddress The address of the client that you want to start a new conversation
     * @param context Context of the invitation.
     * @return New [Conversation] using the address and according to that address is able to find
     * the topics if exists for that new conversation.
     */
    suspend fun newConversation(
        peerAddress: String,
        context: Invitation.InvitationV1.Context? = null,
        consentProof: Invitation.ConsentProofPayload? = null,
    ): Conversation {
        if (peerAddress.lowercase() == client.address.lowercase()) {
            throw XMTPException("Recipient is sender")
        }
        val existingConversation = conversationsByTopic.values.firstOrNull {
            it.peerAddress == peerAddress && it.conversationId == context?.conversationId
        }
        if (existingConversation != null) {
            return existingConversation
        }
        val contact = client.contacts.find(peerAddress)
            ?: throw XMTPException("Recipient not on network")
        // See if we have an existing v1 convo
        if (context?.conversationId.isNullOrEmpty()) {
            val invitationPeers = listIntroductionPeers()
            val peerSeenAt = invitationPeers[peerAddress]
            if (peerSeenAt != null) {
                val conversation = Conversation.V1(
                    ConversationV1(
                        client = client,
                        peerAddress = peerAddress,
                        sentAt = peerSeenAt,
                    ),
                )
                conversationsByTopic[conversation.topic] = conversation
                return conversation
            }
        }

        // If the contact is v1, start a v1 conversation
        if (Contact.ContactBundle.VersionCase.V1 == contact.versionCase && context?.conversationId.isNullOrEmpty()) {
            val conversation = Conversation.V1(
                ConversationV1(
                    client = client,
                    peerAddress = peerAddress,
                    sentAt = Date(),
                ),
            )
            conversationsByTopic[conversation.topic] = conversation
            return conversation
        }
        // See if we have a v2 conversation
        for (sealedInvitation in listInvitations()) {
            if (!sealedInvitation.involves(contact)) {
                continue
            }
            val invite = sealedInvitation.v1.getInvitation(viewer = client.keys)
            if (invite.context.conversationId == context?.conversationId && invite.context.conversationId != "") {
                val conversation = Conversation.V2(
                    ConversationV2(
                        topic = invite.topic,
                        keyMaterial = invite.aes256GcmHkdfSha256.keyMaterial.toByteArray(),
                        context = invite.context,
                        peerAddress = peerAddress,
                        client = client,
                        header = sealedInvitation.v1.header,
                        consentProof = if (invite.hasConsentProof()) invite.consentProof else null
                    ),
                )
                conversationsByTopic[conversation.topic] = conversation
                return conversation
            }
        }
        // We don't have an existing conversation, make a v2 one
        val recipient = contact.toSignedPublicKeyBundle()
        val invitation = Invitation.InvitationV1.newBuilder().build()
            .createDeterministic(client.keys, recipient, context, consentProof)
        val sealedInvitation =
            sendInvitation(recipient = recipient, invitation = invitation, created = Date())
        val conversationV2 = ConversationV2.create(
            client = client,
            invitation = invitation,
            header = sealedInvitation.v1.header,
        )
        client.contacts.allow(addresses = listOf(peerAddress))
        val conversation = Conversation.V2(conversationV2)
        conversationsByTopic[conversation.topic] = conversation
        return conversation
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
        if (client.hasV2Client) throw XMTPException("Only supported for V3 only clients.")
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

    suspend fun listConversations(
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

    /**
     * Get the list of conversations that current user has
     * @return The list of [Conversation] that the current [Client] has.
     */
    suspend fun list(includeGroups: Boolean = false): List<Conversation> {
        val newConversations = mutableListOf<Conversation>()
        val mostRecent = conversationsByTopic.values.maxOfOrNull { it.createdAt }
        val pagination = Pagination(after = mostRecent)
        val seenPeers = listIntroductionPeers(pagination = pagination)
        for ((peerAddress, sentAt) in seenPeers) {
            newConversations.add(
                Conversation.V1(
                    ConversationV1(
                        client = client,
                        peerAddress = peerAddress,
                        sentAt = sentAt,
                    ),
                ),
            )
        }
        val invitations = listInvitations(pagination = pagination)
        for (sealedInvitation in invitations) {
            try {
                val newConversation = Conversation.V2(conversation(sealedInvitation))
                newConversations.add(newConversation)
                val consentProof = newConversation.consentProof
                if (consentProof != null) {
                    handleConsentProof(consentProof, newConversation.peerAddress)
                }
            } catch (e: Exception) {
                Log.d(TAG, e.message.toString())
            }
        }

        conversationsByTopic += newConversations.filter {
            it.peerAddress != client.address && Topic.isValidTopic(it.topic)
        }.map { Pair(it.topic, it) }

        if (includeGroups) {
            syncConversations()
            val groups = listGroups()
            conversationsByTopic += groups.map { Pair(it.topic, Conversation.Group(it)) }
        }
        return conversationsByTopic.values.sortedByDescending { it.createdAt }
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

        topics.iterator().forEach {
            val conversation = it.value
            val hmacKeys = HmacKeys.newBuilder()
            if (conversation.keyMaterial != null) {
                (thirtyDayPeriodsSinceEpoch - 1..thirtyDayPeriodsSinceEpoch + 1).iterator()
                    .forEach { value ->
                        val info = "$value-${client.address}"
                        val hmacKey =
                            Crypto.deriveKey(
                                conversation.keyMaterial!!,
                                ByteArray(0),
                                info.toByteArray(Charsets.UTF_8),
                            )
                        val hmacKeyData = HmacKeyData.newBuilder()
                        hmacKeyData.hmacKey = hmacKey.toByteString()
                        hmacKeyData.thirtyDayPeriodsSinceEpoch = value
                        hmacKeys.addValues(hmacKeyData)
                    }
                hmacKeysResponse.putHmacKeys(conversation.topic, hmacKeys.build())
            }
        }
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

    fun stream(): Flow<Conversation> = callbackFlow {
        val streamedConversationTopics: MutableSet<String> = mutableSetOf()
        val subscriptionCallback = object : FfiV2SubscriptionCallback {
            override fun onMessage(message: FfiEnvelope) {
                val envelope = envelopeFromFFi(message)
                if (envelope.contentTopic == Topic.userIntro(client.address).description) {
                    val conversationV1 = fromIntro(envelope = envelope)
                    if (!streamedConversationTopics.contains(conversationV1.topic)) {
                        streamedConversationTopics.add(conversationV1.topic)
                        trySend(conversationV1)
                    }
                }

                if (envelope.contentTopic == Topic.userInvite(client.address).description) {
                    val conversationV2 = fromInvite(envelope = envelope)
                    if (!streamedConversationTopics.contains(conversationV2.topic)) {
                        streamedConversationTopics.add(conversationV2.topic)
                        trySend(conversationV2)
                    }
                }
            }
        }

        val stream = client.subscribe2(
            FfiV2SubscribeRequest(
                listOf(
                    Topic.userIntro(client.address).description,
                    Topic.userInvite(client.address).description
                )
            ),
            subscriptionCallback
        )

        awaitClose { launch { stream.end() } }
    }

    fun streamAll(): Flow<Conversation> {
        return merge(streamGroupConversations(), stream())
    }

    fun streamConversations(): Flow<Conversation> = callbackFlow {
        if (client.hasV2Client) throw XMTPException("Only supported for V3 only clients.")
        val conversationCallback = object : FfiConversationCallback {
            override fun onConversation(conversation: FfiConversation) {
                if (conversation.groupMetadata().conversationType() == "dm") {
                    trySend(Conversation.Dm(Dm(client, conversation)))
                } else {
                    trySend(Conversation.Group(Group(client, conversation)))
                }
            }
        }
        val stream = libXMTPConversations?.stream(conversationCallback)
            ?: throw XMTPException("Client does not support Groups")
        awaitClose { stream.end() }
    }

    private fun streamGroupConversations(): Flow<Conversation> = callbackFlow {
        val groupCallback = object : FfiConversationCallback {
            override fun onConversation(conversation: FfiConversation) {
                trySend(Conversation.Group(Group(client, conversation)))
            }
        }

        val stream = libXMTPConversations?.streamGroups(groupCallback)
            ?: throw XMTPException("Client does not support Groups")
        awaitClose { stream.end() }
    }

    fun streamGroups(): Flow<Group> = callbackFlow {
        val groupCallback = object : FfiConversationCallback {
            override fun onConversation(conversation: FfiConversation) {
                trySend(Group(client, conversation))
            }
        }
        val stream = libXMTPConversations?.streamGroups(groupCallback)
            ?: throw XMTPException("Client does not support Groups")
        awaitClose { stream.end() }
    }

    fun streamAllMessages(includeGroups: Boolean = false): Flow<DecodedMessage> {
        return if (includeGroups) {
            merge(streamAllV2Messages(), streamAllGroupMessages())
        } else {
            streamAllV2Messages()
        }
    }

    fun streamAllDecryptedMessages(includeGroups: Boolean = false): Flow<DecryptedMessage> {
        return if (includeGroups) {
            merge(streamAllV2DecryptedMessages(), streamAllGroupDecryptedMessages())
        } else {
            streamAllV2DecryptedMessages()
        }
    }

    fun streamAllGroupMessages(): Flow<DecodedMessage> = callbackFlow {
        val messageCallback = object : FfiMessageCallback {
            override fun onMessage(message: FfiMessage) {
                val decodedMessage = MessageV3(client, message).decodeOrNull()
                decodedMessage?.let {
                    trySend(it)
                }
            }
        }
        val stream = libXMTPConversations?.streamAllGroupMessages(messageCallback)
            ?: throw XMTPException("Client does not support Groups")
        awaitClose { stream.end() }
    }

    fun streamAllGroupDecryptedMessages(): Flow<DecryptedMessage> = callbackFlow {
        val messageCallback = object : FfiMessageCallback {
            override fun onMessage(message: FfiMessage) {
                val decryptedMessage = MessageV3(client, message).decryptOrNull()
                decryptedMessage?.let {
                    trySend(it)
                }
            }
        }
        val stream = libXMTPConversations?.streamAllGroupMessages(messageCallback)
            ?: throw XMTPException("Client does not support Groups")
        awaitClose { stream.end() }
    }

    fun streamAllConversationMessages(): Flow<DecodedMessage> = callbackFlow {
        if (client.hasV2Client) throw XMTPException("Only supported for V3 only clients.")
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
        }

        val stream = libXMTPConversations?.streamAllMessages(messageCallback)
            ?: throw XMTPException("Client does not support Groups")

        awaitClose { stream.end() }
    }

    fun streamAllConversationDecryptedMessages(): Flow<DecryptedMessage> = callbackFlow {
        if (client.hasV2Client) throw XMTPException("Only supported for V3 only clients.")
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
        }

        val stream = libXMTPConversations?.streamAllMessages(messageCallback)
            ?: throw XMTPException("Client does not support Groups")

        awaitClose { stream.end() }
    }

    // ------- V1 V2 to be deprecated ------

    /**
     *  @return This lists messages sent to the [Conversation].
     *  This pulls messages from multiple conversations in a single call.
     *  @see Conversation.messages
     */
    suspend fun listBatchMessages(
        topics: List<Pair<String, Pagination?>>,
    ): List<DecodedMessage> {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. The local database handles persistence of messages. Use listConversations order lastMessage")

        val requests = topics.map { (topic, page) ->
            makeQueryRequest(topic = topic, pagination = page)
        }

        // The maximum number of requests permitted in a single batch call.
        val maxQueryRequestsPerBatch = 50
        val messages: MutableList<DecodedMessage> = mutableListOf()
        val batches = requests.chunked(maxQueryRequestsPerBatch)
        for (batch in batches) {
            messages.addAll(
                client.batchQuery(batch).responsesOrBuilderList.flatMap { res ->
                    res.envelopesList.mapNotNull { envelope ->
                        val conversation = conversationsByTopic[envelope.contentTopic]
                        if (conversation == null) {
                            Log.d(TAG, "discarding message, unknown conversation $envelope")
                            return@mapNotNull null
                        }
                        val msg = conversation.decodeOrNull(envelope)
                        msg
                    }
                },
            )
        }
        return messages
    }

    /**
     *  @return This lists messages sent to the [Conversation] when the messages are encrypted.
     *  This pulls messages from multiple conversations in a single call.
     *  @see listBatchMessages
     */
    suspend fun listBatchDecryptedMessages(
        topics: List<Pair<String, Pagination?>>,
    ): List<DecryptedMessage> {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. The local database handles persistence of messages. Use listConversations order lastMessage")

        val requests = topics.map { (topic, page) ->
            makeQueryRequest(topic = topic, pagination = page)
        }

        // The maximum number of requests permitted in a single batch call.
        val maxQueryRequestsPerBatch = 50
        val messages: MutableList<DecryptedMessage> = mutableListOf()
        val batches = requests.chunked(maxQueryRequestsPerBatch)
        for (batch in batches) {
            messages.addAll(
                client.batchQuery(batch).responsesOrBuilderList.flatMap { res ->
                    res.envelopesList.mapNotNull { envelope ->
                        val conversation = conversationsByTopic[envelope.contentTopic]
                        if (conversation == null) {
                            Log.d(TAG, "discarding message, unknown conversation $envelope")
                            return@mapNotNull null
                        }
                        try {
                            val msg = conversation.decrypt(envelope)
                            msg
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decrypting message: $envelope", e)
                            null
                        }
                    }
                },
            )
        }
        return messages
    }

    fun importTopicData(data: TopicData): Conversation {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. The local database handles persistence.")
        val conversation: Conversation
        if (!data.hasInvitation()) {
            val sentAt = Date(data.createdNs / 1_000_000)
            conversation = Conversation.V1(
                ConversationV1(
                    client,
                    data.peerAddress,
                    sentAt,
                ),
            )
        } else {
            conversation = Conversation.V2(
                ConversationV2(
                    topic = data.invitation.topic,
                    keyMaterial = data.invitation.aes256GcmHkdfSha256.keyMaterial.toByteArray(),
                    context = data.invitation.context,
                    peerAddress = data.peerAddress,
                    client = client,
                    createdAtNs = data.createdNs,
                    header = Invitation.SealedInvitationHeaderV1.getDefaultInstance(),
                    consentProof = if (data.invitation.hasConsentProof()) data.invitation.consentProof else null
                ),
            )
        }
        conversationsByTopic[conversation.topic] = conversation
        return conversation
    }

    /**
     * This method creates a new conversation from an invitation.
     * @param envelope Object that contains the information of the current [Client] such as topic
     * and timestamp.
     * @return [Conversation] from an invitation suing the current [Client].
     */
    fun fromInvite(envelope: Envelope): Conversation {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. Use conversationFromWelcome.")
        val sealedInvitation = Invitation.SealedInvitation.parseFrom(envelope.message)
        val unsealed = sealedInvitation.v1.getInvitation(viewer = client.keys)
        return Conversation.V2(
            ConversationV2.create(
                client = client,
                invitation = unsealed,
                header = sealedInvitation.v1.header,
            ),
        )
    }

    /**
     * This method creates a new conversation from an Intro.
     * @param envelope Object that contains the information of the current [Client] such as topic
     * and timestamp.
     * @return [Conversation] from an Intro suing the current [Client].
     */
    fun fromIntro(envelope: Envelope): Conversation {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. Use conversationFromWelcome.")
        val messageV1 = MessageV1Builder.buildFromBytes(envelope.message.toByteArray())
        val senderAddress = messageV1.header.sender.walletAddress
        val recipientAddress = messageV1.header.recipient.walletAddress
        val peerAddress = if (client.address == senderAddress) recipientAddress else senderAddress
        return Conversation.V1(
            ConversationV1(
                client = client,
                peerAddress = peerAddress,
                sentAt = messageV1.sentAt,
            ),
        )
    }

    fun conversation(sealedInvitation: SealedInvitation): ConversationV2 {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. Use client.findDm to find the dm.")
        val unsealed = sealedInvitation.v1.getInvitation(viewer = client.keys)
        return ConversationV2.create(
            client = client,
            invitation = unsealed,
            header = sealedInvitation.v1.header,
        )
    }

    /**
     * Send an invitation from the current [Client] to the specified recipient (Client)
     * @param recipient The public key of the client that you want to send the invitation
     * @param invitation Invitation object that will be send
     * @param created Specified date creation for this invitation.
     * @return [SealedInvitation] with the specified information.
     */
    suspend fun sendInvitation(
        recipient: SignedPublicKeyBundle,
        invitation: InvitationV1,
        created: Date,
    ): SealedInvitation {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. Use newConversation to create welcome.")
        client.keys.let {
            val sealed = SealedInvitationBuilder.buildFromV1(
                sender = it,
                recipient = recipient,
                created = created,
                invitation = invitation,
            )
            val peerAddress = recipient.walletAddress

            client.publish(
                envelopes = listOf(
                    EnvelopeBuilder.buildFromTopic(
                        topic = Topic.userInvite(
                            client.address,
                        ),
                        timestamp = created,
                        message = sealed.toByteArray(),
                    ),
                    EnvelopeBuilder.buildFromTopic(
                        topic = Topic.userInvite(
                            peerAddress,
                        ),
                        timestamp = created,
                        message = sealed.toByteArray(),
                    ),
                ),
            )
            return sealed
        }
    }

    /**
     * Get the stream of all messages of the current [Client]
     * @return Flow object of [DecodedMessage] that represents all the messages of the
     * current [Client] as userInvite and userIntro
     */
    private fun streamAllV2Messages(): Flow<DecodedMessage> = callbackFlow {
        val topics = mutableListOf(
            Topic.userInvite(client.address).description,
            Topic.userIntro(client.address).description,
        )

        for (conversation in list()) {
            topics.add(conversation.topic)
        }

        val subscriptionRequest = FfiV2SubscribeRequest(topics)
        var stream = FfiV2Subscription(NoPointer)

        val subscriptionCallback = object : FfiV2SubscriptionCallback {
            override fun onMessage(message: FfiEnvelope) {
                when {
                    conversationsByTopic.containsKey(message.contentTopic) -> {
                        val conversation = conversationsByTopic[message.contentTopic]
                        val decoded = conversation?.decode(envelopeFromFFi(message))
                        decoded?.let { trySend(it) }
                    }

                    message.contentTopic.startsWith("/xmtp/0/invite-") -> {
                        val conversation = fromInvite(envelope = envelopeFromFFi(message))
                        conversationsByTopic[conversation.topic] = conversation
                        topics.add(conversation.topic)
                        subscriptionRequest.contentTopics = topics
                        launch { stream.update(subscriptionRequest) }
                    }

                    message.contentTopic.startsWith("/xmtp/0/intro-") -> {
                        val conversation = fromIntro(envelope = envelopeFromFFi(message))
                        conversationsByTopic[conversation.topic] = conversation
                        val decoded = conversation.decode(envelopeFromFFi(message))
                        trySend(decoded)
                        topics.add(conversation.topic)
                        subscriptionRequest.contentTopics = topics
                        launch { stream.update(subscriptionRequest) }
                    }

                    else -> {}
                }
            }
        }

        stream = client.subscribe2(subscriptionRequest, subscriptionCallback)

        awaitClose { launch { stream.end() } }
    }

    private fun streamAllV2DecryptedMessages(): Flow<DecryptedMessage> = callbackFlow {
        val topics = mutableListOf(
            Topic.userInvite(client.address).description,
            Topic.userIntro(client.address).description,
        )

        for (conversation in list()) {
            topics.add(conversation.topic)
        }

        val subscriptionRequest = FfiV2SubscribeRequest(topics)
        var stream = FfiV2Subscription(NoPointer)

        val subscriptionCallback = object : FfiV2SubscriptionCallback {
            override fun onMessage(message: FfiEnvelope) {
                when {
                    conversationsByTopic.containsKey(message.contentTopic) -> {
                        val conversation = conversationsByTopic[message.contentTopic]
                        val decrypted = conversation?.decrypt(envelopeFromFFi(message))
                        decrypted?.let { trySend(it) }
                    }

                    message.contentTopic.startsWith("/xmtp/0/invite-") -> {
                        val conversation = fromInvite(envelope = envelopeFromFFi(message))
                        conversationsByTopic[conversation.topic] = conversation
                        topics.add(conversation.topic)
                        subscriptionRequest.contentTopics = topics
                        launch { stream.update(subscriptionRequest) }
                    }

                    message.contentTopic.startsWith("/xmtp/0/intro-") -> {
                        val conversation = fromIntro(envelope = envelopeFromFFi(message))
                        conversationsByTopic[conversation.topic] = conversation
                        val decrypted = conversation.decrypt(envelopeFromFFi(message))
                        trySend(decrypted)
                        topics.add(conversation.topic)
                        subscriptionRequest.contentTopics = topics
                        launch { stream.update(subscriptionRequest) }
                    }

                    else -> {}
                }
            }
        }

        stream = client.subscribe2(subscriptionRequest, subscriptionCallback)

        awaitClose { launch { stream.end() } }
    }

    /**
     * Get the list of invitations using the data sent [pagination]
     * @param pagination Information of the topics, ranges (dates), etc.
     * @return List of [SealedInvitation] that are inside of the range specified by [pagination]
     */
    private suspend fun listInvitations(pagination: Pagination? = null): List<SealedInvitation> {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. Use conversationFromWelcome.")
        val apiClient = client.apiClient ?: throw XMTPException("V2 only function")
        val envelopes =
            apiClient.envelopes(Topic.userInvite(client.address).description, pagination)
        return envelopes.map { envelope ->
            SealedInvitation.parseFrom(envelope.message)
        }
    }

    private suspend fun listIntroductionPeers(pagination: Pagination? = null): Map<String, Date> {
        if (!client.hasV2Client) throw XMTPException("Not supported for V3. Use conversationFromWelcome.")
        val apiClient = client.apiClient ?: throw XMTPException("V2 only function")
        val envelopes = apiClient.queryTopic(
            topic = Topic.userIntro(client.address),
            pagination = pagination,
        ).envelopesList
        val messages = envelopes.mapNotNull { envelope ->
            try {
                val message = MessageV1Builder.buildFromBytes(envelope.message.toByteArray())
                // Attempt to decrypt, just to make sure we can
                message.decrypt(client.privateKeyBundleV1)
                message
            } catch (e: Exception) {
                Log.d(TAG, e.message.toString())
                null
            }
        }
        val seenPeers: MutableMap<String, Date> = mutableMapOf()
        for (message in messages) {
            val recipientAddress = message.recipientAddress
            val senderAddress = message.senderAddress
            val sentAt = message.sentAt
            val peerAddress =
                if (recipientAddress == client.address) senderAddress else recipientAddress
            val existing = seenPeers[peerAddress]
            if (existing == null) {
                seenPeers[peerAddress] = sentAt
                continue
            }
            if (existing > sentAt) {
                seenPeers[peerAddress] = sentAt
            }
        }
        return seenPeers
    }
}
