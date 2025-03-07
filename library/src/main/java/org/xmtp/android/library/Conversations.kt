package org.xmtp.android.library

import android.util.Log
import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import org.xmtp.android.library.libxmtp.GroupPermissionPreconfiguration
import org.xmtp.android.library.libxmtp.Message
import org.xmtp.android.library.libxmtp.DisappearingMessageSettings
import org.xmtp.android.library.libxmtp.Identity
import org.xmtp.android.library.messages.Topic
import org.xmtp.proto.keystore.api.v1.Keystore
import org.xmtp.android.library.libxmtp.PermissionPolicySet
import uniffi.xmtpv3.FfiConversation
import uniffi.xmtpv3.FfiConversationCallback
import uniffi.xmtpv3.FfiConversationListItem
import uniffi.xmtpv3.FfiConversationType
import uniffi.xmtpv3.FfiConversations
import uniffi.xmtpv3.FfiCreateDmOptions
import uniffi.xmtpv3.FfiCreateGroupOptions
import uniffi.xmtpv3.FfiGroupPermissionsOptions
import uniffi.xmtpv3.FfiListConversationsOptions
import uniffi.xmtpv3.FfiMessage
import uniffi.xmtpv3.FfiMessageCallback
import uniffi.xmtpv3.FfiMessageDisappearingSettings
import uniffi.xmtpv3.FfiPermissionPolicySet
import uniffi.xmtpv3.FfiSubscribeException
import uniffi.xmtpv3.FfiXmtpClient
import java.util.Date
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

data class Conversations(
    var client: Client,
    private val ffiConversations: FfiConversations,
    private val ffiClient: FfiXmtpClient,
) {

    enum class ConversationType {
        ALL,
        GROUPS,
        DMS;
    }

    fun findGroup(groupId: String): Group? {
        return try {
            Group(client, ffiClient.conversation(groupId.hexToByteArray()))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findConversation(conversationId: String): Conversation? {
        return try {
            val conversation = ffiClient.conversation(conversationId.hexToByteArray())
            when (conversation.conversationType()) {
                FfiConversationType.GROUP -> Conversation.Group(Group(client, conversation))
                FfiConversationType.DM -> Conversation.Dm(Dm(client, conversation))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findConversationByTopic(topic: String): Conversation? {
        val regex = """/xmtp/mls/1/g-(.*?)/proto""".toRegex()
        val matchResult = regex.find(topic)
        val conversationId = matchResult?.groupValues?.get(1) ?: ""
        return try {
            val conversation = ffiClient.conversation(conversationId.hexToByteArray())
            when (conversation.conversationType()) {
                FfiConversationType.GROUP -> Conversation.Group(Group(client, conversation))
                FfiConversationType.DM -> Conversation.Dm(Dm(client, conversation))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun findDmByInboxId(inboxId: String): Dm? {
        return try {
            Dm(client, ffiClient.dmConversation(inboxId))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun findDmByIdentity(identity: Identity): Dm? {
        val inboxId =
            client.inboxIdFromIdentity(identity)
                ?: throw XMTPException("No inboxId present")
        return findDmByInboxId(inboxId)
    }

    fun findMessage(messageId: String): Message? {
        return try {
            Message.create(ffiClient.message(messageId.hexToByteArray()))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fromWelcome(envelopeBytes: ByteArray): Conversation {
        val conversation = ffiConversations.processStreamedWelcomeMessage(envelopeBytes)
        return when (conversation.conversationType()) {
            FfiConversationType.DM -> Conversation.Dm(Dm(client, conversation))
            else -> Conversation.Group(Group(client, conversation))
        }
    }

    suspend fun newGroupWithIdentities(
        identities: List<Identity>,
        permissions: GroupPermissionPreconfiguration = GroupPermissionPreconfiguration.ALL_MEMBERS,
        groupName: String = "",
        groupImageUrlSquare: String = "",
        groupDescription: String = "",
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Group {
        return newGroupInternalWithIdentities(
            identities,
            GroupPermissionPreconfiguration.toFfiGroupPermissionOptions(permissions),
            groupName,
            groupImageUrlSquare,
            groupDescription,
            null,
            disappearingMessageSettings?.let {
                FfiMessageDisappearingSettings(
                    it.disappearStartingAtNs,
                    it.retentionDurationInNs
                )
            },
        )
    }

    suspend fun newGroupCustomPermissionsWithIdentities(
        identities: List<Identity>,
        permissionPolicySet: PermissionPolicySet,
        groupName: String = "",
        groupImageUrlSquare: String = "",
        groupDescription: String = "",
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Group {
        return newGroupInternalWithIdentities(
            identities,
            FfiGroupPermissionsOptions.CUSTOM_POLICY,
            groupName,
            groupImageUrlSquare,
            groupDescription,
            PermissionPolicySet.toFfiPermissionPolicySet(permissionPolicySet),
            disappearingMessageSettings?.let {
                FfiMessageDisappearingSettings(
                    it.disappearStartingAtNs,
                    it.retentionDurationInNs
                )
            }
        )
    }

    private suspend fun newGroupInternalWithIdentities(
        identities: List<Identity>,
        permissions: FfiGroupPermissionsOptions,
        groupName: String,
        groupImageUrlSquare: String,
        groupDescription: String,
        permissionsPolicySet: FfiPermissionPolicySet?,
        messageDisappearingSettings: FfiMessageDisappearingSettings?,
    ): Group {
        val group =
            ffiConversations.createGroup(
                identities.map { it.ffiIdentifier },
                opts = FfiCreateGroupOptions(
                    permissions = permissions,
                    groupName = groupName,
                    groupImageUrlSquare = groupImageUrlSquare,
                    groupDescription = groupDescription,
                    customPermissionPolicySet = permissionsPolicySet,
                    messageDisappearingSettings = messageDisappearingSettings
                )
            )
        return Group(client, group)
    }

    suspend fun newGroup(
        inboxIds: List<String>,
        permissions: GroupPermissionPreconfiguration = GroupPermissionPreconfiguration.ALL_MEMBERS,
        groupName: String = "",
        groupImageUrlSquare: String = "",
        groupDescription: String = "",
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Group {
        return newGroupInternal(
            inboxIds,
            GroupPermissionPreconfiguration.toFfiGroupPermissionOptions(permissions),
            groupName,
            groupImageUrlSquare,
            groupDescription,
            null,
            disappearingMessageSettings?.let {
                FfiMessageDisappearingSettings(
                    it.disappearStartingAtNs,
                    it.retentionDurationInNs
                )
            }
        )
    }

    suspend fun newGroupCustomPermissions(
        inboxIds: List<String>,
        permissionPolicySet: PermissionPolicySet,
        groupName: String = "",
        groupImageUrlSquare: String = "",
        groupDescription: String = "",
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Group {
        return newGroupInternal(
            inboxIds,
            FfiGroupPermissionsOptions.CUSTOM_POLICY,
            groupName,
            groupImageUrlSquare,
            groupDescription,
            PermissionPolicySet.toFfiPermissionPolicySet(permissionPolicySet),
            disappearingMessageSettings?.let {
                FfiMessageDisappearingSettings(
                    it.disappearStartingAtNs,
                    it.retentionDurationInNs
                )
            }
        )
    }

    private suspend fun newGroupInternal(
        inboxIds: List<String>,
        permissions: FfiGroupPermissionsOptions,
        groupName: String,
        groupImageUrlSquare: String,
        groupDescription: String,
        permissionsPolicySet: FfiPermissionPolicySet?,
        messageDisappearingSettings: FfiMessageDisappearingSettings?,
    ): Group {
        val group =
            ffiConversations.createGroupWithInboxIds(
                inboxIds,
                opts = FfiCreateGroupOptions(
                    permissions = permissions,
                    groupName = groupName,
                    groupImageUrlSquare = groupImageUrlSquare,
                    groupDescription = groupDescription,
                    customPermissionPolicySet = permissionsPolicySet,
                    messageDisappearingSettings = messageDisappearingSettings
                )
            )
        return Group(client, group)
    }

    // Sync from the network the latest list of conversations
    suspend fun sync() {
        ffiConversations.sync()
    }

    // Sync all new and existing conversations data from the network
    suspend fun syncAllConversations(consentStates: List<ConsentState>? = null): UInt {
        return ffiConversations.syncAllConversations(
            consentStates?.let { states ->
                states.map { ConsentState.toFfiConsentState(it) }
            }
        )
    }

    suspend fun newConversationWithIdentity(
        peerIdentity: Identity,
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Conversation {
        val dm = findOrCreateDmWithIdentity(peerIdentity, disappearingMessageSettings)
        return Conversation.Dm(dm)
    }

    suspend fun findOrCreateDmWithIdentity(
        peerIdentity: Identity,
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Dm {
        val dmConversation = ffiConversations.findOrCreateDm(
            peerIdentity.ffiIdentifier,
            opts = FfiCreateDmOptions(
                disappearingMessageSettings?.let {
                    FfiMessageDisappearingSettings(
                        it.disappearStartingAtNs,
                        it.retentionDurationInNs
                    )
                }
            )
        )
        return Dm(client, dmConversation)
    }

    suspend fun newConversation(
        peerInboxId: String,
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Conversation {
        val dm = findOrCreateDm(peerInboxId, disappearingMessageSettings)
        return Conversation.Dm(dm)
    }

    suspend fun findOrCreateDm(
        peerInboxId: String,
        disappearingMessageSettings: DisappearingMessageSettings? = null,
    ): Dm {
        val dmConversation = ffiConversations.findOrCreateDmByInboxId(
            peerInboxId.lowercase(),
            opts = FfiCreateDmOptions(
                disappearingMessageSettings?.let {
                    FfiMessageDisappearingSettings(
                        it.disappearStartingAtNs,
                        it.retentionDurationInNs
                    )
                }
            )
        )
        return Dm(client, dmConversation)
    }

    fun listGroups(
        after: Date? = null,
        before: Date? = null,
        limit: Int? = null,
        consentStates: List<ConsentState>? = null,
    ): List<Group> {
        val ffiGroups = ffiConversations.listGroups(
            opts = FfiListConversationsOptions(
                after?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                before?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                limit?.toLong(),
                consentStates?.let { states ->
                    states.map { ConsentState.toFfiConsentState(it) }
                },
                false
            )
        )

        return ffiGroups.map {
            Group(client, it.conversation(), it.lastMessage())
        }
    }

    fun listDms(
        after: Date? = null,
        before: Date? = null,
        limit: Int? = null,
        consentStates: List<ConsentState>? = null,
    ): List<Dm> {
        val ffiDms = ffiConversations.listDms(
            opts = FfiListConversationsOptions(
                after?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                before?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                limit?.toLong(),
                consentStates?.let { states ->
                    states.map { ConsentState.toFfiConsentState(it) }
                },
                false
            )
        )

        return ffiDms.map {
            Dm(client, it.conversation(), it.lastMessage())
        }
    }

    suspend fun list(
        after: Date? = null,
        before: Date? = null,
        limit: Int? = null,
        consentStates: List<ConsentState>? = null,
    ): List<Conversation> {
        val ffiConversation = ffiConversations.list(
            FfiListConversationsOptions(
                after?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                before?.time?.nanoseconds?.toLong(DurationUnit.NANOSECONDS),
                limit?.toLong(),
                consentStates?.let { states ->
                    states.map { ConsentState.toFfiConsentState(it) }
                },
                false
            )
        )

        return ffiConversation.map { it.toConversation() }
    }

    private suspend fun FfiConversationListItem.toConversation(): Conversation {
        return when (conversation().conversationType()) {
            FfiConversationType.DM -> Conversation.Dm(
                Dm(
                    client,
                    conversation(),
                    lastMessage()
                )
            )

            else -> Conversation.Group(Group(client, conversation(), lastMessage()))
        }
    }

    fun stream(type: ConversationType = ConversationType.ALL): Flow<Conversation> =
        callbackFlow {
            val conversationCallback = object : FfiConversationCallback {
                override fun onConversation(conversation: FfiConversation) {
                    launch(Dispatchers.IO) {
                        when (conversation.conversationType()) {
                            FfiConversationType.DM -> trySend(
                                Conversation.Dm(
                                    Dm(
                                        client,
                                        conversation
                                    )
                                )
                            )

                            else -> trySend(Conversation.Group(Group(client, conversation)))
                        }
                    }
                }

                override fun onError(error: FfiSubscribeException) {
                    Log.e("XMTP Conversation stream", error.message.toString())
                }
            }

            val stream = when (type) {
                ConversationType.ALL -> ffiConversations.stream(conversationCallback)
                ConversationType.GROUPS -> ffiConversations.streamGroups(conversationCallback)
                ConversationType.DMS -> ffiConversations.streamDms(conversationCallback)
            }

            awaitClose { stream.end() }
        }

    fun streamAllMessages(type: ConversationType = ConversationType.ALL): Flow<Message> =
        callbackFlow {
            val messageCallback = object : FfiMessageCallback {
                override fun onMessage(message: FfiMessage) {
                    val decodedMessage = Message.create(message)
                    decodedMessage?.let { trySend(it) }
                }

                override fun onError(error: FfiSubscribeException) {
                    Log.e("XMTP all message stream", error.message.toString())
                }
            }

            val stream = when (type) {
                ConversationType.ALL -> ffiConversations.streamAllMessages(messageCallback)
                ConversationType.GROUPS -> ffiConversations.streamAllGroupMessages(messageCallback)
                ConversationType.DMS -> ffiConversations.streamAllDmMessages(messageCallback)
            }

            awaitClose { stream.end() }
        }

    fun getHmacKeys(): Keystore.GetConversationHmacKeysResponse {
        val hmacKeysResponse = Keystore.GetConversationHmacKeysResponse.newBuilder()
        val conversations = ffiConversations.getHmacKeys()
        conversations.iterator().forEach {
            val hmacKeys = Keystore.GetConversationHmacKeysResponse.HmacKeys.newBuilder()
            it.value.forEach { key ->
                val hmacKeyData = Keystore.GetConversationHmacKeysResponse.HmacKeyData.newBuilder()
                hmacKeyData.hmacKey = key.key.toByteString()
                hmacKeyData.thirtyDayPeriodsSinceEpoch = key.epoch.toInt()
                hmacKeys.addValues(hmacKeyData)
            }
            hmacKeysResponse.putHmacKeys(
                Topic.groupMessage(it.key.toHex()).description,
                hmacKeys.build()
            )
        }
        return hmacKeysResponse.build()
    }
}
