package org.xmtp.android.library

import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.MessageV1
import org.xmtp.android.library.messages.SealedInvitation
import org.xmtp.android.library.messages.SignedPublicKeyBundle
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.createRandom
import org.xmtp.android.library.messages.toSignedPublicKeyBundle
import org.xmtp.proto.message.contents.Invitation
import java.util.Date

data class Conversations(
    var client: Client,
    var conversations: MutableList<Conversation> = mutableListOf()
) {

    public fun newConversation(peerAddress: String, context: Invitation.InvitationV1.Context? = null) : Conversation {
        if (peerAddress.lowercase() == client.address?.lowercase()) {
            throw IllegalArgumentException("Recipient is sender")
        }
        val existingConversation = conversations.firstOrNull { it.peerAddress == peerAddress }
        if (existingConversation != null) {
            return existingConversation
        }
        val contact = client.contacts.find(peerAddress) ?: throw IllegalArgumentException("Recipient not on network")
        // See if we have an existing v1 convo
        if (context?.conversationId == null || context.conversationId == "") {
            val invitationPeers = listIntroductionPeers()
            val peerSeenAt = invitationPeers[peerAddress]
            if (peerSeenAt != null) {
                val conversation: Conversation = Conversation.v1(
                    ConversationV1(
                        client = client,
                        peerAddress = peerAddress,
                        sentAt = peerSeenAt
                    )
                )
                conversations.add(conversation)
                return conversation
            }
        }
        // If the contact is v1, start a v1 conversation
        if (Conversation.v1 = contact.version && context?.conversationId == null || context?.conversationId == "") {
            val conversation: Conversation = .v1(ConversationV1(client = client, peerAddress = peerAddress, sentAt = Date()))
            conversations.add(conversation)
            return conversation
        }
        // See if we have a v2 conversation
        for (sealedInvitation in listInvitations()) {
            if (!sealedInvitation.involves(contact)) {
                continue
            }
            val invite = sealedInvitation.v1.getInvitation(viewer = client.keys)
            if (invite.context.conversationID == context?.conversationId && invite.context.conversationID != "") {
                val conversation: Conversation = .v2(ConversationV2(topic = invite.topic, keyMaterial = invite.aes256GcmHkdfSha256.keyMaterial, context = invite.context, peerAddress = peerAddress, client = client, header = sealedInvitation.v1.header))
                conversations.add(conversation)
                return conversation
            }
        }
        // We don't have an existing conversation, make a v2 one
        val recipient = contact.toSignedPublicKeyBundle()
        val invitation = Invitation.InvitationV1.newBuilder().build().createRandom(context)
        val sealedInvitation = sendInvitation(recipient = recipient, invitation = invitation, created = Date())
        val conversationV2 = ConversationV2.create(client = client, invitation = invitation, header = sealedInvitation.v1.header)
        val conversation: Conversation = Conversation.v2(conversationV2)
        conversations.add(conversation)
        return conversation
    }

    public fun stream() : ThrowingStream<Conversation, Error> =
        ThrowingStream { continuation  ->
            Task {
                var streamedConversationTopics: Set<String> = listOf()
                for (envelope in client.subscribe(topics = listOf(.userIntro(client.address), .userInvite(client.address)))) {
                if (envelope.contentTopic == Topic.userIntro(client.address).description) {
                    val messageV1 = MessageV1.fromBytes(envelope.message)
                    val senderAddress = messageV1.header.sender.walletAddress
                    val recipientAddress = messageV1.header.recipient.walletAddress
                    val peerAddress = if (client.address == senderAddress) recipientAddress else senderAddress
                    val conversationV1 = ConversationV1(client = client, peerAddress = peerAddress, sentAt = messageV1.sentAt)
                    if (streamedConversationTopics.contains(conversationV1.topic.description)) {
                        continue
                    }
                    streamedConversationTopics.insert(conversationV1.topic.description)
                    continuation.yield(Conversation.v1(conversationV1))
                }
                if (envelope.contentTopic == Topic.userInvite(client.address).description) {
                    val sealedInvitation = SealedInvitation(serializedData = envelope.message)
                    val unsealed = sealedInvitation.v1.getInvitation(viewer = client.keys)
                    val conversationV2 = ConversationV2.create(client = client, invitation = unsealed, header = sealedInvitation.v1.header)
                    if (streamedConversationTopics.contains(conversationV2.topic)) {
                        continue
                    }
                    streamedConversationTopics.insert(conversationV2.topic)
                    continuation.yield(Conversation.v2(conversationV2))
                }
            }
            }
        }

    public fun list() : List<Conversation> {
        var conversations: List<Conversation> = listOf()
        do {
            val seenPeers = listIntroductionPeers()
            for ((peerAddress, sentAt) in seenPeers) {
                conversations.add(Conversation.v1(ConversationV1(client = client, peerAddress = peerAddress, sentAt = sentAt)))
            }
        } catch {
            print("Error loading introduction peers: ${error}")
        }
        val invitations = listInvitations()
        for (sealedInvitation in invitations) {
            do {
                val unsealed = sealedInvitation.v1.getInvitation(viewer = client.keys)
                val conversation = ConversationV2.create(client = client, invitation = unsealed, header = sealedInvitation.v1.header)
                conversations.add(Conversation.v2(conversation))
            } catch {
                print("Error loading invitations: ${error}")
            }
        }
        return conversations.filter { it.peerAddress != client.address }
    }

    fun listIntroductionPeers() : Map<String, Date> {
        val envelopes = client.apiClient.query(topics = listOf(.userIntro(client.address)), pagination = null).envelopes
        val messages = envelopes.compactMap { envelope  ->
            do {
                val message = MessageV1.fromBytes(envelope.message)
                // Attempt to decrypt, just to make sure we can
                message.decrypt(with = client.privateKeyBundleV1)
                return@compactMap message
            } catch {
                return@compactMap null
            }
        }
        var seenPeers: Map<String, Date> = mapOf()
        for (message in messages) {
            val recipientAddress = message.recipientAddress
            val senderAddress = message.senderAddress
            if (recipientAddress == null || senderAddress == null) {
                continue
            }
            val sentAt = message.sentAt
            val peerAddress = if (recipientAddress == client.address) senderAddress else recipientAddress
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

    fun listInvitations() : List<SealedInvitation> {
        val envelopes = client.apiClient.query(topics = listOf(userInvite(client.address))).envelopes
        return envelopes.compactMap { envelope  ->
            try { SealedInvitation(envelope.message) } catch (e: Throwable) { null }
        }
    }

    fun sendInvitation(recipient: SignedPublicKeyBundle, invitation: InvitationV1, created: Date) : SealedInvitation {
        val sealed = SealedInvitation.createV1(sender = client.keys, recipient = recipient, created = created, invitation = invitation)
        val peerAddress = recipient.walletAddress
    }
}
