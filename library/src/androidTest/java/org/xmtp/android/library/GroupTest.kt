package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.Conversations.ConversationFilterType
import org.xmtp.android.library.codecs.ContentTypeGroupUpdated
import org.xmtp.android.library.codecs.ContentTypeReaction
import org.xmtp.android.library.codecs.GroupUpdatedCodec
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionCodec
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.libxmtp.DecodedMessage
import org.xmtp.android.library.libxmtp.DecodedMessage.MessageDeliveryStatus
import org.xmtp.android.library.libxmtp.DisappearingMessageSettings
import org.xmtp.android.library.libxmtp.GroupPermissionPreconfiguration
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PermissionOption
import org.xmtp.android.library.libxmtp.PublicIdentity
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.mls.message.contents.TranscriptMessages
import uniffi.xmtpv3.GenericException
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class GroupTest {
    private lateinit var alixWallet: PrivateKeyBuilder
    private lateinit var boWallet: PrivateKeyBuilder
    private lateinit var alix: PrivateKey
    private lateinit var alixClient: Client
    private lateinit var bo: PrivateKey
    private lateinit var boClient: Client
    private lateinit var caroWallet: PrivateKeyBuilder
    private lateinit var caro: PrivateKey
    private lateinit var caroClient: Client
    private lateinit var fixtures: Fixtures

    @Before
    fun setUp() {
        fixtures = fixtures()
        alixWallet = fixtures.alixAccount
        alix = fixtures.alix
        boWallet = fixtures.boAccount
        bo = fixtures.bo
        caroWallet = fixtures.caroAccount
        caro = fixtures.caro

        alixClient = fixtures.alixClient
        boClient = fixtures.boClient
        caroClient = fixtures.caroClient
    }

    @Test
    fun testCanCreateAGroupWithDefaultPermissions() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(listOf(alixClient.inboxId))
        }
        runBlocking {
            alixClient.conversations.sync()
            boGroup.sync()
        }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }
        assert(boGroup.id.isNotEmpty())
        assert(alixGroup.id.isNotEmpty())

        runBlocking {
            alixGroup.addMembers(listOf(caroClient.inboxId))
            boGroup.sync()
        }
        assertEquals(runBlocking { alixGroup.members().size }, 3)
        assertEquals(runBlocking { boGroup.members().size }, 3)

        // All members also defaults remove to admin only now.
        assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.removeMembers(listOf(caroClient.inboxId))
                boGroup.sync()
            }
        }

        assertEquals(runBlocking { alixGroup.members().size }, 3)
        assertEquals(runBlocking { boGroup.members().size }, 3)

        assertEquals(boGroup.permissionPolicySet().addMemberPolicy, PermissionOption.Allow)
        assertEquals(alixGroup.permissionPolicySet().addMemberPolicy, PermissionOption.Allow)
        assertEquals(boGroup.isSuperAdmin(boClient.inboxId), true)
        assertEquals(boGroup.isSuperAdmin(alixClient.inboxId), false)
        assertEquals(alixGroup.isSuperAdmin(boClient.inboxId), true)
        assertEquals(alixGroup.isSuperAdmin(alixClient.inboxId), false)
        // can not fetch creator ID. See https://github.com/xmtp/libxmtp/issues/788
//       assert(boGroup.isCreator())
        assert(!runBlocking { alixGroup.isCreator() })
    }

    @Test
    fun testCanCreateAGroupWithAdminPermissions() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(alixClient.inboxId),
                permissions = GroupPermissionPreconfiguration.ADMIN_ONLY
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }
        assert(boGroup.id.isNotEmpty())
        assert(alixGroup.id.isNotEmpty())

        runBlocking {
            assertEquals(
                boClient.preferences.conversationState(boGroup.id),
                ConsentState.ALLOWED
            )
            assertEquals(
                alixClient.preferences.conversationState(alixGroup.id),
                ConsentState.UNKNOWN
            )
        }

        runBlocking {
            boGroup.addMembers(listOf(caroClient.inboxId))
            alixGroup.sync()
        }

        assertEquals(runBlocking { alixGroup.members().size }, 3)
        assertEquals(runBlocking { boGroup.members().size }, 3)

        assertThrows(XMTPException::class.java) {
            runBlocking { alixGroup.removeMembers(listOf(caroClient.inboxId)) }
        }
        runBlocking { boGroup.sync() }

        assertEquals(runBlocking { alixGroup.members().size }, 3)
        assertEquals(runBlocking { boGroup.members().size }, 3)
        runBlocking {
            boGroup.removeMembers(listOf(caroClient.inboxId))
            alixGroup.sync()
        }

        assertEquals(runBlocking { alixGroup.members().size }, 2)
        assertEquals(runBlocking { boGroup.members().size }, 2)

        assertThrows(XMTPException::class.java) {
            runBlocking { alixGroup.addMembers(listOf(caroClient.inboxId)) }
        }
        runBlocking { boGroup.sync() }

        assertEquals(runBlocking { alixGroup.members().size }, 2)
        assertEquals(runBlocking { boGroup.members().size }, 2)

        assertEquals(boGroup.permissionPolicySet().addMemberPolicy, PermissionOption.Admin)
        assertEquals(alixGroup.permissionPolicySet().addMemberPolicy, PermissionOption.Admin)
        assertEquals(boGroup.isSuperAdmin(boClient.inboxId), true)
        assertEquals(boGroup.isSuperAdmin(alixClient.inboxId), false)
        assertEquals(alixGroup.isSuperAdmin(boClient.inboxId), true)
        assertEquals(alixGroup.isSuperAdmin(alixClient.inboxId), false)
        // can not fetch creator ID. See https://github.com/xmtp/libxmtp/issues/788
//       assert(boGroup.isCreator())
        assert(!runBlocking { alixGroup.isCreator() })
    }

    @Test
    fun testCanCreateAGroupWithInboxIdsDefaultPermissions() {
        val boGroup = runBlocking {
            boClient.conversations.newGroupWithIdentities(
                listOf(
                    PublicIdentity(
                        IdentityKind.ETHEREUM,
                        alix.walletAddress
                    )
                )
            )
        }
        runBlocking {
            alixClient.conversations.sync()
            boGroup.sync()
        }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }
        assert(boGroup.id.isNotEmpty())
        assert(alixGroup.id.isNotEmpty())

        runBlocking {
            alixGroup.addMembers(listOf(caroClient.inboxId))
            boGroup.sync()
        }
        assertEquals(runBlocking { alixGroup.members().size }, 3)
        assertEquals(runBlocking { boGroup.members().size }, 3)

        // All members also defaults remove to admin only now.
        assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.removeMembers(listOf(caroClient.inboxId))
                boGroup.sync()
            }
        }

        assertEquals(runBlocking { alixGroup.members().size }, 3)
        assertEquals(runBlocking { boGroup.members().size }, 3)

        assertEquals(boGroup.permissionPolicySet().addMemberPolicy, PermissionOption.Allow)
        assertEquals(alixGroup.permissionPolicySet().addMemberPolicy, PermissionOption.Allow)
        assertEquals(boGroup.isSuperAdmin(boClient.inboxId), true)
        assertEquals(boGroup.isSuperAdmin(alixClient.inboxId), false)
        assertEquals(alixGroup.isSuperAdmin(boClient.inboxId), true)
        assertEquals(alixGroup.isSuperAdmin(alixClient.inboxId), false)
        assert(!runBlocking { alixGroup.isCreator() })
    }

    @Test
    fun testCanListGroupMembers() {
        val group = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        assertEquals(
            runBlocking { group.members().map { it.inboxId }.sorted() },
            listOf(
                caroClient.inboxId,
                alixClient.inboxId,
                boClient.inboxId
            ).sorted()
        )

        assertEquals(
            runBlocking { group.peerInboxIds().map { it }.sorted() },
            listOf(
                caroClient.inboxId,
                alixClient.inboxId,
            ).sorted()
        )
    }

    @Test
    fun testGroupMetadata() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(alixClient.inboxId),
                groupName = "Starting Name",
                groupImageUrlSquare = "startingurl.com"
            )
        }
        runBlocking {
            assertEquals("Starting Name", boGroup.name)
            assertEquals("startingurl.com", boGroup.imageUrl)
            boGroup.updateName("This Is A Great Group")
            boGroup.updateImageUrl("thisisanewurl.com")
            boGroup.sync()
            alixClient.conversations.sync()
        }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }
        runBlocking { alixGroup.sync() }
        assertEquals("This Is A Great Group", boGroup.name)
        assertEquals("This Is A Great Group", alixGroup.name)
        assertEquals("thisisanewurl.com", boGroup.imageUrl)
        assertEquals("thisisanewurl.com", alixGroup.imageUrl)
    }

    @Test
    fun testCanAddGroupMembers() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        val result = runBlocking { group.addMembers(listOf(caroClient.inboxId)) }
        assertEquals(caroClient.inboxId, result.addedMembers.first())
        assertEquals(
            runBlocking { group.members().map { it.inboxId }.sorted() },
            listOf(
                caroClient.inboxId,
                alixClient.inboxId,
                boClient.inboxId
            ).sorted()
        )
    }

    @Test
    fun testCannotStartGroupOrAddMembersWithAddressWhenExpectingInboxId() {
        assertThrows("Invalid inboxId", XMTPException::class.java) {
            runBlocking { boClient.conversations.newGroup(listOf(alix.walletAddress)) }
        }
        val group = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        assertThrows("Invalid inboxId", XMTPException::class.java) {
            runBlocking { group.addMembers(listOf(caro.walletAddress)) }
        }
        assertThrows("Invalid inboxId", XMTPException::class.java) {
            runBlocking { group.removeMembers(listOf(alix.walletAddress)) }
        }
    }

    @Test
    fun testCanRemoveGroupMembers() {
        val group = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        runBlocking { group.removeMembers(listOf(caroClient.inboxId)) }
        assertEquals(
            runBlocking { group.members().map { it.inboxId }.sorted() },
            listOf(
                alixClient.inboxId,
                boClient.inboxId
            ).sorted()
        )
    }

    @Test
    fun testCanRemoveGroupMembersWhenNotCreator() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        runBlocking {
            boGroup.addAdmin(alixClient.inboxId)
            alixClient.conversations.sync()
        }
        val group = runBlocking {
            alixClient.conversations.sync()
            alixClient.conversations.listGroups().first()
        }
        runBlocking {
            group.removeMembers(listOf(caroClient.inboxId))
            group.sync()
            boGroup.sync()
        }
        assertEquals(
            runBlocking { boGroup.members().map { it.inboxId }.sorted() },
            listOf(
                alixClient.inboxId,
                boClient.inboxId
            ).sorted()
        )
    }

    @Test
    fun testCanAddGroupMemberIds() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        val result = runBlocking {
            group.addMembersByIdentity(
                listOf(
                    PublicIdentity(
                        IdentityKind.ETHEREUM,
                        caro.walletAddress
                    )
                )
            )
        }
        assertEquals(caroClient.inboxId, result.addedMembers.first())
        assertEquals(
            runBlocking { group.members().map { it.inboxId }.sorted() },
            listOf(
                caroClient.inboxId,
                alixClient.inboxId,
                boClient.inboxId
            ).sorted()
        )
    }

    @Test
    fun testCanRemoveGroupMemberIds() {
        val group = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        runBlocking {
            group.removeMembersByIdentity(
                listOf(
                    PublicIdentity(
                        IdentityKind.ETHEREUM,
                        caro.walletAddress
                    )
                )
            )
        }
        assertEquals(
            runBlocking { group.members().map { it.inboxId }.sorted() },
            listOf(
                alixClient.inboxId,
                boClient.inboxId
            ).sorted()
        )
    }

    @Test
    fun testMessageTimeIsCorrect() {
        val alixGroup = runBlocking { alixClient.conversations.newGroup(listOf(boClient.inboxId)) }
        runBlocking { alixGroup.send("Hello") }
        assertEquals(runBlocking { alixGroup.messages() }.size, 2)
        runBlocking { alixGroup.sync() }
        val message2 = runBlocking { alixGroup.messages().last() }
        runBlocking { alixGroup.sync() }
        val message3 = runBlocking { alixGroup.messages().last() }
        assertEquals(message3.id, message2.id)
        assertEquals(message3.sentAtNs, message2.sentAtNs)
    }

    @Test
    fun testIsActiveReturnsCorrectly() {
        val group = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        runBlocking { caroClient.conversations.sync() }
        val caroGroup = runBlocking { caroClient.conversations.listGroups().first() }
        runBlocking { caroGroup.sync() }
        assert(caroGroup.isActive())
        assert(group.isActive())
        runBlocking {
            group.removeMembers(listOf(caroClient.inboxId))
            caroGroup.sync()
        }
        assert(group.isActive())
        assert(!caroGroup.isActive())
    }

    @Test
    fun testAddedByAddress() {
        runBlocking {
            alixClient.conversations.newGroup(
                listOf(
                    boClient.inboxId,
                )
            )
        }
        runBlocking { boClient.conversations.sync() }
        val boGroup = runBlocking { boClient.conversations.listGroups().first() }
        assertEquals(boGroup.addedByInboxId(), alixClient.inboxId)
    }

    @Test
    fun testCanListGroups() {
        runBlocking {
            boClient.conversations.newGroup(listOf(alixClient.inboxId))
            boClient.conversations.newGroup(listOf(caroClient.inboxId))
            boClient.conversations.sync()
        }
        val groups = runBlocking { boClient.conversations.listGroups() }
        assertEquals(groups.size, 2)
    }

    @Test
    fun testCanListGroupsAndConversations() {
        runBlocking {
            boClient.conversations.newGroup(listOf(alixClient.inboxId))
            boClient.conversations.newGroup(listOf(caroClient.inboxId))
            boClient.conversations.newConversation(alixClient.inboxId)
            boClient.conversations.sync()
        }
        val convos = runBlocking { boClient.conversations.list() }
        assertEquals(convos.size, 3)
    }

    @Test
    fun testCannotSendMessageToGroupMemberNotOnV3() {
        val chuxAccount = PrivateKeyBuilder()
        val chux: PrivateKey = chuxAccount.getPrivateKey()

        assertThrows(GenericException::class.java) {
            runBlocking {
                boClient.conversations.newGroupWithIdentities(
                    listOf(
                        PublicIdentity(
                            IdentityKind.ETHEREUM,
                            chux.walletAddress
                        )
                    )
                )
            }
        }
    }

    @Test
    fun testCanStartEmptyGroupChat() {
        val group = runBlocking { boClient.conversations.newGroup(listOf()) }
        assert(group.id.isNotEmpty())
    }

    @Test
    fun testGroupStartsWithAllowedState() {
        runBlocking {
            val group = boClient.conversations.newGroup(listOf(alixClient.inboxId))
            group.send("howdy")
            group.send("gm")
            group.sync()
            assertEquals(group.consentState(), ConsentState.ALLOWED)
            assertEquals(
                boClient.preferences.conversationState(group.id),
                ConsentState.ALLOWED
            )
        }
    }

    @Test
    fun testCanStreamAndUpdateNameWithoutForkingGroup() {
        val firstMsgCheck = 2
        val secondMsgCheck = 5
        var messageCallbacks = 0

        val job = CoroutineScope(Dispatchers.IO).launch {
            boClient.conversations.streamAllMessages().collect { _ ->
                messageCallbacks++
            }
        }
        Thread.sleep(1000)

        val alixGroup = runBlocking { alixClient.conversations.newGroup(listOf(boClient.inboxId)) }

        runBlocking {
            alixGroup.send("hello1")
            alixGroup.updateName("hello")
            boClient.conversations.sync()
        }

        val boGroups = runBlocking { boClient.conversations.listGroups() }
        assertEquals(boGroups.size, 1)
        val boGroup = boGroups[0]
        runBlocking {
            boGroup.sync()
        }

        val boMessages1 = runBlocking { boGroup.messages() }
        assertEquals(boMessages1.size, firstMsgCheck)

        runBlocking {
            boGroup.send("hello2")
            boGroup.send("hello3")
            alixGroup.sync()
        }
        Thread.sleep(1000)
        val alixMessages = runBlocking { alixGroup.messages() }
        assertEquals(alixMessages.size, secondMsgCheck)
        runBlocking {
            alixGroup.send("hello4")
            boGroup.sync()
        }

        val boMessages2 = runBlocking { boGroup.messages() }
        assertEquals(boMessages2.size, secondMsgCheck)

        Thread.sleep(1000)

        assertEquals(secondMsgCheck, messageCallbacks)
        job.cancel()
    }

    @Test
    fun testsCanListGroupsFiltered() {
        runBlocking { boClient.conversations.findOrCreateDm(caroClient.inboxId) }
        runBlocking { boClient.conversations.newGroup(listOf(caroClient.inboxId)) }
        val group =
            runBlocking { boClient.conversations.newGroup(listOf(caroClient.inboxId)) }
        assertEquals(runBlocking { boClient.conversations.listGroups().size }, 2)
        assertEquals(
            runBlocking { boClient.conversations.listGroups(consentStates = listOf(ConsentState.ALLOWED)).size },
            2
        )
        runBlocking { group.updateConsentState(ConsentState.DENIED) }
        assertEquals(
            runBlocking { boClient.conversations.listGroups(consentStates = listOf(ConsentState.ALLOWED)).size },
            1
        )
        assertEquals(
            runBlocking { boClient.conversations.listGroups(consentStates = listOf(ConsentState.DENIED)).size },
            1
        )
        assertEquals(
            runBlocking {
                boClient.conversations.listGroups(
                    consentStates = listOf(
                        ConsentState.ALLOWED,
                        ConsentState.DENIED
                    )
                ).size
            },
            2
        )
        assertEquals(runBlocking { boClient.conversations.listGroups().size }, 1)
    }

    @Test
    fun testCanListGroupsOrder() {
        val dm = runBlocking { boClient.conversations.findOrCreateDm(caroClient.inboxId) }
        val group1 =
            runBlocking { boClient.conversations.newGroup(listOf(caroClient.inboxId)) }
        val group2 =
            runBlocking { boClient.conversations.newGroup(listOf(caroClient.inboxId)) }
        runBlocking { dm.send("Howdy") }
        runBlocking { group2.send("Howdy") }
        runBlocking { boClient.conversations.syncAllConversations() }
        val conversations = runBlocking { boClient.conversations.listGroups() }
        assertEquals(conversations.size, 2)
        assertEquals(conversations.map { it.id }, listOf(group2.id, group1.id))
    }

    @Test
    fun testCanSendMessageToGroup() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        runBlocking { group.send("howdy") }
        val messageId = runBlocking { group.send("gm") }
        runBlocking { group.sync() }
        assertEquals(runBlocking { group.messages() }.first().body, "gm")
        assertEquals(runBlocking { group.messages() }.first().id, messageId)
        assertEquals(
            runBlocking { group.messages() }.first().deliveryStatus,
            MessageDeliveryStatus.PUBLISHED
        )
        assertEquals(runBlocking { group.messages() }.size, 3)

        runBlocking { alixClient.conversations.sync() }
        val sameGroup = runBlocking { alixClient.conversations.listGroups().last() }
        runBlocking { sameGroup.sync() }
        assertEquals(runBlocking { sameGroup.messages() }.size, 2)
        assertEquals(runBlocking { sameGroup.messages() }.first().body, "gm")
    }

    @Test
    fun testCanListGroupMessages() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        runBlocking {
            group.send("howdy")
            group.send("gm")
        }

        assertEquals(runBlocking { group.messages() }.size, 3)
        assertEquals(
            runBlocking { group.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED) }.size,
            3
        )
        runBlocking { group.sync() }
        assertEquals(runBlocking { group.messages() }.size, 3)
        assertEquals(
            runBlocking { group.messages(deliveryStatus = MessageDeliveryStatus.UNPUBLISHED) }.size,
            0
        )
        assertEquals(
            runBlocking { group.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED) }.size,
            3
        )

        runBlocking { alixClient.conversations.sync() }
        val sameGroup = runBlocking { alixClient.conversations.listGroups().last() }
        runBlocking { sameGroup.sync() }
        assertEquals(
            runBlocking { sameGroup.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED) }.size,
            2
        )
    }

    @Test
    fun testCanListGroupMessagesAfter() {
        val group = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        val messageId = runBlocking {
            group.send("howdy")
            group.send("gm")
        }
        val message = boClient.conversations.findMessage(messageId)
        assertEquals(runBlocking { group.messages() }.size, 3)
        assertEquals(runBlocking { group.messages(afterNs = message?.sentAtNs) }.size, 0)
        runBlocking {
            group.send("howdy")
            group.send("gm")
        }
        assertEquals(runBlocking { group.messages() }.size, 5)
        assertEquals(runBlocking { group.messages(afterNs = message?.sentAtNs) }.size, 2)

        runBlocking { alixClient.conversations.sync() }
        val sameGroup = runBlocking { alixClient.conversations.listGroups().last() }
        runBlocking { sameGroup.sync() }
        assertEquals(runBlocking { sameGroup.messages() }.size, 4)
        assertEquals(runBlocking { sameGroup.messages(afterNs = message?.sentAtNs) }.size, 2)
    }

    @Test
    fun testCanSendContentTypesToGroup() {
        Client.register(codec = ReactionCodec())

        val group = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        runBlocking { group.send("gm") }
        runBlocking { group.sync() }
        val messageToReact = runBlocking { group.messages() }[0]

        val reaction = Reaction(
            reference = messageToReact.id,
            action = ReactionAction.Added,
            content = "U+1F603",
            schema = ReactionSchema.Unicode
        )

        runBlocking {
            group.send(
                content = reaction,
                options = SendOptions(contentType = ContentTypeReaction)
            )
        }
        runBlocking { group.sync() }

        val messages = runBlocking { group.messages() }
        assertEquals(messages.size, 3)
        val content: Reaction? = messages.first().content()
        assertEquals("U+1F603", content?.content)
        assertEquals(messageToReact.id, content?.reference)
        assertEquals(ReactionAction.Added, content?.action)
        assertEquals(ReactionSchema.Unicode, content?.schema)
    }

    @Test
    fun testCanStreamGroupMessages() = kotlinx.coroutines.test.runTest {
        Client.register(codec = GroupUpdatedCodec())
        val membershipChange = TranscriptMessages.GroupUpdated.newBuilder().build()

        val group = boClient.conversations.newGroup(listOf(alixClient.inboxId))
        alixClient.conversations.sync()
        val alixGroup = alixClient.conversations.listGroups().first()
        group.streamMessages().test {
            alixGroup.send("hi")
            assertEquals("hi", awaitItem().body)
            alixGroup.send(
                content = membershipChange,
                options = SendOptions(contentType = ContentTypeGroupUpdated),
            )
            alixGroup.send("hi again")
            assertEquals("hi again", awaitItem().body)
        }
    }

    @Test
    fun testCanStreamAllGroupMessages() {
        val group = runBlocking { caroClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        val conversation =
            runBlocking { caroClient.conversations.newConversation(alixClient.inboxId) }

        runBlocking { alixClient.conversations.sync() }

        val allMessages = mutableListOf<DecodedMessage>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                alixClient.conversations.streamAllMessages(type = ConversationFilterType.GROUPS)
                    .collect { message ->
                        allMessages.add(message)
                    }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(2500)
        runBlocking { conversation.send(text = "conversation message") }
        for (i in 0 until 2) {
            runBlocking {
                group.send(text = "Message $i")
            }
            Thread.sleep(100)
        }
        assertEquals(2, allMessages.size)

        val caroGroup =
            runBlocking { caroClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        Thread.sleep(2500)

        for (i in 0 until 2) {
            runBlocking { caroGroup.send(text = "Message $i") }
            Thread.sleep(100)
        }

        assertEquals(4, allMessages.size)

        job.cancel()
    }

    @Test
    fun testCanStreamGroups() = kotlinx.coroutines.test.runTest {
        boClient.conversations.stream(type = ConversationFilterType.GROUPS).test {
            val group =
                alixClient.conversations.newGroup(listOf(boClient.inboxId))
            assertEquals(group.id, awaitItem().id)
            val group2 =
                caroClient.conversations.newGroup(listOf(boClient.inboxId))
            assertEquals(group2.id, awaitItem().id)
        }
    }

    @Test
    fun testCanStreamGroupsAndConversations() {
        val allMessages = mutableListOf<String>()

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                alixClient.conversations.stream()
                    .collect { message ->
                        allMessages.add(message.topic)
                    }
            } catch (e: Exception) {
            }
        }
        Thread.sleep(2500)

        runBlocking {
            alixClient.conversations.newConversation(boClient.inboxId)
            Thread.sleep(2500)
            caroClient.conversations.newGroup(listOf(alixClient.inboxId))
        }

        Thread.sleep(2500)

        assertEquals(2, allMessages.size)

        job.cancel()
    }

    @Test
    fun testGroupConsent() {
        runBlocking {
            val group =
                boClient.conversations.newGroup(
                    listOf(
                        alixClient.inboxId,
                        caroClient.inboxId
                    )
                )
            assertEquals(
                boClient.preferences.conversationState(group.id),
                ConsentState.ALLOWED
            )
            assertEquals(group.consentState(), ConsentState.ALLOWED)

            boClient.preferences.setConsentState(
                listOf(
                    ConsentRecord(
                        group.id,
                        EntryType.CONVERSATION_ID,
                        ConsentState.DENIED
                    )
                )
            )
            assertEquals(
                boClient.preferences.conversationState(group.id),
                ConsentState.DENIED
            )
            assertEquals(group.consentState(), ConsentState.DENIED)

            group.updateConsentState(ConsentState.ALLOWED)
            assertEquals(
                boClient.preferences.conversationState(group.id),
                ConsentState.ALLOWED
            )
            assertEquals(group.consentState(), ConsentState.ALLOWED)
        }
    }

    @Test
    fun testCanAllowAndDenyInboxId() {
        runBlocking {
            val boGroup = boClient.conversations.newGroup(listOf(alixClient.inboxId))
            assertEquals(
                boClient.preferences.inboxIdState(alixClient.inboxId),
                ConsentState.UNKNOWN
            )
            boClient.preferences.setConsentState(
                listOf(
                    ConsentRecord(
                        alixClient.inboxId,
                        EntryType.INBOX_ID,
                        ConsentState.ALLOWED
                    )
                )
            )
            var alixMember = boGroup.members().firstOrNull { it.inboxId == alixClient.inboxId }
            assertEquals(alixMember!!.consentState, ConsentState.ALLOWED)

            assertEquals(
                boClient.preferences.inboxIdState(alixClient.inboxId),
                ConsentState.ALLOWED
            )

            boClient.preferences.setConsentState(
                listOf(
                    ConsentRecord(
                        alixClient.inboxId,
                        EntryType.INBOX_ID,
                        ConsentState.DENIED
                    )
                )
            )
            alixMember = boGroup.members().firstOrNull { it.inboxId == alixClient.inboxId }
            assertEquals(alixMember!!.consentState, ConsentState.DENIED)

            assertEquals(
                boClient.preferences.inboxIdState(alixClient.inboxId),
                ConsentState.DENIED
            )
        }
    }

    @Test
    fun testCanFetchGroupById() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = alixClient.conversations.findGroup(boGroup.id)

        assertEquals(alixGroup?.id, boGroup.id)
    }

    @Test
    fun testCanFetchMessageById() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        val boMessageId = runBlocking { boGroup.send("Hello") }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = alixClient.conversations.findGroup(boGroup.id)
        runBlocking { alixGroup?.sync() }
        val alixMessage = alixClient.conversations.findMessage(boMessageId)

        assertEquals(alixMessage?.id, boMessageId)
    }

    @Test
    fun testUnpublishedMessages() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                )
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup: Group = alixClient.conversations.findGroup(boGroup.id)!!
        runBlocking { assertEquals(alixGroup.consentState(), ConsentState.UNKNOWN) }
        val preparedMessageId = runBlocking { alixGroup.prepareMessage("Test text") }
        assertEquals(runBlocking { alixGroup.messages() }.size, 1)
        assertEquals(
            runBlocking { alixGroup.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED) }.size,
            0
        )
        assertEquals(
            runBlocking { alixGroup.messages(deliveryStatus = MessageDeliveryStatus.UNPUBLISHED) }.size,
            1
        )

        runBlocking {
            alixGroup.publishMessages()
            alixGroup.sync()
        }
        runBlocking { assertEquals(alixGroup.consentState(), ConsentState.ALLOWED) }
        assertEquals(
            runBlocking { alixGroup.messages(deliveryStatus = MessageDeliveryStatus.PUBLISHED) }.size,
            1
        )
        assertEquals(
            runBlocking { alixGroup.messages(deliveryStatus = MessageDeliveryStatus.UNPUBLISHED) }.size,
            0
        )
        assertEquals(runBlocking { alixGroup.messages() }.size, 1)

        val message = runBlocking { alixGroup.messages() }.first()

        assertEquals(preparedMessageId, message.id)
    }

    @Test
    fun testSyncsAllGroupsInParallel() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                )
            )
        }
        val boGroup2 = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                )
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup: Group = alixClient.conversations.findGroup(boGroup.id)!!
        val alixGroup2: Group = alixClient.conversations.findGroup(boGroup2.id)!!
        var numGroups: UInt?

        assertEquals(runBlocking { alixGroup.messages() }.size, 0)
        assertEquals(runBlocking { alixGroup2.messages() }.size, 0)

        runBlocking {
            boGroup.send("hi")
            boGroup2.send("hi")
            numGroups = alixClient.conversations.syncAllConversations()
        }

        assertEquals(runBlocking { alixGroup.messages() }.size, 1)
        assertEquals(runBlocking { alixGroup2.messages() }.size, 1)
        assertEquals(numGroups, 3u)

        runBlocking {
            boGroup2.removeMembers(listOf(alixClient.inboxId))
            boGroup.send("hi")
            boGroup.send("hi")
            boGroup2.send("hi")
            boGroup2.send("hi")
            numGroups = alixClient.conversations.syncAllConversations()
            Thread.sleep(2000)
        }

        assertEquals(runBlocking { alixGroup.messages() }.size, 3)
        assertEquals(runBlocking { alixGroup2.messages() }.size, 2)
        // First syncAllGroups after remove includes the group you're removed from
        assertEquals(numGroups, 3u)

        runBlocking {
            numGroups = alixClient.conversations.syncAllConversations()
        }
        // Next syncAllGroups will not include the inactive group
        assertEquals(numGroups, 2u)
    }

    @Test
    fun testGroupDisappearingMessages() = runBlocking {
        val initialSettings = DisappearingMessageSettings(
            1_000_000_000,
            1_000_000_000 // 1s duration
        )

        // Create group with disappearing messages enabled
        val boGroup = boClient.conversations.newGroup(
            listOf(alixClient.inboxId),
            disappearingMessageSettings = initialSettings
        )
        boGroup.send("howdy")
        alixClient.conversations.syncAllConversations()

        val alixGroup = alixClient.conversations.findGroup(boGroup.id)

        // Validate messages exist and settings are applied
        assertEquals(boGroup.messages().size, 2) // memberAdd, howdy
        assertEquals(alixGroup?.messages()?.size, 1) // howdy
        assertNotNull(boGroup.disappearingMessageSettings)
        assertEquals(boGroup.disappearingMessageSettings!!.retentionDurationInNs, 1_000_000_000)
        assertEquals(boGroup.disappearingMessageSettings!!.disappearStartingAtNs, 1_000_000_000)
        Thread.sleep(5000)
        // Validate messages are deleted
        assertEquals(boGroup.messages().size, 1) // memberAdd
        assertEquals(alixGroup?.messages()?.size, 0)

        // Set message disappearing settings to null
        boGroup.updateDisappearingMessageSettings(null)
        boGroup.sync()
        alixGroup!!.sync()

        assertNull(boGroup.disappearingMessageSettings)
        assertNull(alixGroup.disappearingMessageSettings)
        assert(!boGroup.isDisappearingMessagesEnabled)
        assert(!alixGroup.isDisappearingMessagesEnabled)

        // Send messages after disabling disappearing settings
        boGroup.send("message after disabling disappearing")
        alixGroup.send("another message after disabling")
        boGroup.sync()

        Thread.sleep(1000)

        // Ensure messages persist
        assertEquals(
            boGroup.messages().size,
            5
        ) // memberAdd, disappearing settings 1, disappearing settings 2, boMessage, alixMessage
        assertEquals(
            alixGroup.messages().size,
            4
        ) // disappearing settings 1, disappearing settings 2, boMessage, alixMessage

        // Re-enable disappearing messages
        val updatedSettings = DisappearingMessageSettings(
            boGroup.messages().first().sentAtNs + 1_000_000_000, // 1s from now
            1_000_000_000 // 1s duration
        )
        boGroup.updateDisappearingMessageSettings(updatedSettings)
        boGroup.sync()
        alixGroup.sync()

        Thread.sleep(1000)

        assertEquals(
            boGroup.disappearingMessageSettings!!.disappearStartingAtNs,
            updatedSettings.disappearStartingAtNs
        )
        assertEquals(
            alixGroup.disappearingMessageSettings!!.disappearStartingAtNs,
            updatedSettings.disappearStartingAtNs
        )

        // Send new messages
        boGroup.send("this will disappear soon")
        alixGroup.send("so will this")
        boGroup.sync()

        assertEquals(
            boGroup.messages().size,
            9
        ) // memberAdd, disappearing settings 3, disappearing settings 4, boMessage, alixMessage, disappearing settings 5, disappearing settings 6, boMessage2, alixMessage2
        assertEquals(
            alixGroup.messages().size,
            8
        ) // disappearing settings 3, disappearing settings 4, boMessage, alixMessage, disappearing settings 5, disappearing settings 6, boMessage2, alixMessage2

        Thread.sleep(6000) // Wait for messages to disappear

        // Validate messages were deleted
        assertEquals(
            boGroup.messages().size,
            7
        ) // memberAdd, disappearing settings 3, disappearing settings 4, boMessage, alixMessage, disappearing settings 5, disappearing settings 6
        assertEquals(
            alixGroup.messages().size,
            6
        ) // disappearing settings 3, disappearing settings 4, boMessage, alixMessage, disappearing settings 5, disappearing settings 6

        // Final validation that settings persist
        assertEquals(
            boGroup.disappearingMessageSettings!!.retentionDurationInNs,
            updatedSettings.retentionDurationInNs
        )
        assertEquals(
            alixGroup.disappearingMessageSettings!!.retentionDurationInNs,
            updatedSettings.retentionDurationInNs
        )
        assert(boGroup.isDisappearingMessagesEnabled)
        assert(alixGroup.isDisappearingMessagesEnabled)
    }

    @Test
    fun testGroupPausedForVersionReturnsNone() = runBlocking {
        val boGroup = boClient.conversations.newGroup(
            listOf(alixClient.inboxId)
        )
        val pausedForVersionGroup = boGroup.pausedForVersion()
        assertNull(pausedForVersionGroup)

        val boDm = boClient.conversations.newConversation(alixClient.inboxId)
        val pausedForVersionDm = boDm.pausedForVersion()
        assertNull(pausedForVersionDm)
    }

    @Test
    fun shouldCreateGroupWith100Members() = runBlocking {
        // Assuming `creator` is already initialized with a valid client
        val memberInboxIds: List<String> = listOf(
            "a5402cdaea8ca241516574d4b855dbb82cfedd29e5f3ba2b997f6bee321879fe",
            "449c0564b394bf12313bda4f0527a5d500afb1d7802250bc382d346ced3f63eb",
            "cb7213baf2dc77488a597f2ad47136fe1612fb686221903d7f09f155fe1cf2f5",
            "b164a6401398ac50ad9c80f1530201d4dbd2b0771ea26d697c0e43a358b79f59",
            "13ccdd297ed5e9de229e298ab35b6f3b07cc6d318850bbf5e3cd162aa8c3dc57",
            "362e88663aab240a6c5b0c9c6b91429b520065ccc0eace63ecaf63dbda7d0fd5",
            "ad83a9c6b7dfce4d149d25e4e7a49afe3d2cdcddc39d7172ee58ef0c877d2083",
            "3ced945619024e549d8f68013b52beb3025a46258d4db0366e33c6dfd988d648",
            "bdc8cbca0e1cc7e5061786b5b9ea08ba4162176bfb57f3352871858b49d9944b",
            "ae2dc1e82588406e94a8a3e6305348a792dd9e1da380e55f3eca4ca1d4d6eca7",
            "d2d09efdbb9745055e674d7342206064bd26ed005f63e703c8e7fd32d85056b7",
            "9318e4dbfe3cedee8ef30846fc7139db9f07cce98133e99e09bba9ce62100598",
            "52e3a85bfa32eef5241791fae8d063bab87d6cb00f56e8aa8b12bf39fe134cc3",
            "ff799dac7b3ff81eeea68c70fb4b5a1b983dc00d4d2166e316564c535846127c",
            "745a6f4594e2e9ab330413c348bb575dd7a3f218106aae904dd9e28052a7c54c",
            "05c27875753e186806d3cb81887c61e738553212747afd662d1976482f97918c",
            "c5685a3c02632f997726d85a638603f0a14df8cdf92dd54b236286e082fe47e8",
            "fd8099ee99ded1d9d7a0cd616777ca2d659ae61254a9c461cb10e7c95c5c648b",
            "d9d57ad4f250fbc0643be31ba73871a8dd8e1907b7444c1a0c782fdebdcc85e6",
            "f3fab3ade9dc20c0c40e698d2186df3a97fbcfdf366667368d9efd4989e021e1",
            "45ed19f2d73c2ae97e05855a6b66ae9ad85949bca8bbde45e58cbb9c196bc34d",
            "17dcbd10cfcb031ec9c9234da7031cc47ebc1654bcd04c695d4aadc78cc9970f",
            "de53ae7fd35d14f0003573e5c81e3c4fba68cae6fd7213b14ff3b22f1cdbdeeb",
            "ee9da51dc15f2583ca8da4bb5e3ec94062ac8c4bd4cbce75dcc7543fc05a1201",
            "50b78ee7af7d0fbea662195801e7938720ad77ec45a16b2c689589625ee9f99e",
            "2ecb781a8a1d40f79f572cd641af2dc628be65d4716cf944756080de659b274c",
            "e4a64fa1526733738acbee231694413b873607b4f6c1fb93948ecca8aceb3169",
            "7965768326ea73f4c9f39518973e6a6a52e40ef222c7a8cfdaedb580d1ac248a",
            "d10120164994364d1ff550c207ef6932d11413098d87197e7068b1bf6ef0ea2a",
            "72e82033f2680118eaef87d71985e03313bc4f87bf78215fcd6209234ac9d30b",
            "447fc8b4ddcb2ccade18c7eec11fe7c35fbebfa9b8b50d53bf8399fb164ced08",
            "704db06e0c9737f03ad4dc5e43964fc0b1e9f1f7854ddacc51157939c9f11acc",
            "c629f1a95b6dba1c1560cb25ebff516d7af88c9110428dc127fdda648f7f2d03",
            "7b981d61e81257e41bb3621805cc6093b285015be3328e8e55655438baff7f86",
            "c7bec585096130789929134f0e61350c7c2e67813d53b043b6e47f6fd632a323",
            "8cc567d47183bdb434a31128c4f596d0d53f3b989e89043f9e7f5e50869b29c5",
            "9df13ae3a3ee78a3bfe6180878f3b71ad33be0b1e7ca5e5e70a9a62d3e654aca",
            "69d9a01fd0644334e351c7cb3e478b158bfc0fd5b5ff4f312fe3c9a76b56b6b9",
            "329f0d5ebf19f091fb021a691cc46cd90c662c3709c842dfee09c1c6c79be534",
            "45952bdd7823e1848caf3f0ca4eba3b8f2257c643d83deda000ccebae6e09072",
            "92f8cfa1945dbacef811df12ba9ce9a8ed760465a2c86d5f2ab181272ecde427",
            "3cf48c2a821fe510e7cfd8fb7182c0f9d948aeca9528ebe55204f84ee70fc4c3",
            "6ba9d2b4fa96b31fb3c6c758f6954820a51ad13f27991c3fe0305c57d413c0d7",
            "d861bf75c33750dc36fdb7c5807f6ed32fb5f06cb55b8bf8d4f36289cdef8c1b",
            "632bb2ba56a9bf81a958d3c8d1a2887985be5c36e4d59664bbbc31846249c752",
            "93c43d86a296fe059c649a12a5ff23e35b8c1164cebbae6bfd719654dac749ca",
            "20f7d2f52711b38fa7b1e9b6384ed60ca7b54f571757f57997f1a1137e932daa",
            "1380dbd62c0d71467c5aa26a729c4bb992ba4cc609732356230ab3b83270034b",
            "6c4329f7433fa1d6151857a2554299eda8b8e5eef2cb852de498eb833f458484",
            "2e30172482bdd19d0de2294add0a5f08ad20b1ca92d325bcba766cf95d9b80b0",
            "6657fbd29163412678f5ebf95e104702081da36d14b7ba0384445366686874ff",
            "abdc994e308d6275edaedbc2d3a73f6dc73805f67290342dd4b5bee63d2b554a",
            "9dced083b0ad5cc0632c5d1aa73c7843b18cadfed834ccc57272472f9f1a38f0",
            "43d134bedc4926944bab919448b6b0eefafd8ed1512d737f8d0d7d5200efbf08",
            "c27b2fd327de00c06b0f4660e801d30259b7587f7924b7b0284bcfb2a3ecc226",
            "b449da4e9b99a56998207836e0bfa41fe674831afb8d81c666e58a20dff78dba",
            "11b7edac0f0d15e3abb401f1b9cc8ab19962c08847c8ad87be8218a38f85aad2",
            "c1d8e412a545808b919275de225048c0cfa1a348f512a9c8b437f11c3338aff6",
            "7b81382be474999dc39ad4454e95964205efff51c22df9c42edc7809cac2ca47",
            "3283670a95a1be33daa2c2de2a4464bb4aeda6445047d5daf8fe1e5dd1124221",
            "8615b67271c122768ae9e36ca3e16ba396d9098c757559b54d52fca66c943b6f",
            "ca687fef8d62419452ac2ce3777069e1b51f48216932cf58862b4270272a8ec9",
            "d80b283013e7d0690ff5584f88793a23961a6f8ddf866f6c0265e4c8a10ee8cc",
            "626f0034bbedb4254cad7fd30076f51c44145f47a12326ccf9ad5d07ac57b51d",
            "33100643fc9ce601575289840da42e47dba66fe81b2faf45560b6ce70e45e832",
            "39e70674eced8a45713b8d079027759a9d6f594d89aa4c91aabe44d4d731fd5f",
            "d61b4072898803204bffd12703659fa57d27cd9537df0a0f165bd9e720a69f50",
            "401660e813f6d1d0e7c33b1c63ea3670c8b5bd47d6475add336c80a79e0cf59a",
            "3e12e72ab71eae02d7b78aee62bf58f6d84977525de42c4dd4eb5988c9c9578b",
            "209e6e83fe709b5d1ded8793118bc88cb07adf9e6bb99c246d57d35fdd2b8141",
            "88ada15cce3801cf485968ec50ec416a2ca25ef8ff27f81d210352e8faa47453",
            "ebd4bc360d33c3b991e140e3062820197f305134ea9d96fe7b49fbed2baa8109",
            "17d6110563fe45f60015ddc59d876bd57a667d52d535707f66393af40769f1eb",
            "bf9449f3cb45cf0d80706a795508da02ffa5fc6422a41ac87a8da6a4d281a37d",
            "f2b60c9e056b7c075bbb118f30a427206764d0319cc56007e4b64c255aef5f98",
            "cfe6195db05d4bd082c205f4d5c1c99dcb8ff56982d73b3d0845a95df9fbce66",
            "9dc0108c27447db3b40fb4cf2ca1795ca94f05ff38fa1c285dad0d4425492c8e",
            "8a81a4e6582073f646230ecda59122848de7189a9e3da94b2cb77bca3414073e",
            "1bc8e8b1a0018fb39aac7a0a50e43b60d255e3b30569b28d0b554cc49d36c1ac",
            "20909f862378ace5554c21c41f2740f9bc1a98900481fe80ced6a8f11e071717",
            "e65edaf00809caa1eb88ac48bca58b3f6fb5e559b4dec44a8fa2ea31e5a4e65d",
            "9e029f5760fc450631dfa7cbe695d57989c3a1aac073b1d3523f1c30640576bb",
            "9fdfe1798507c88ca0dd3be22183807782c5a7aaaf5bf73b18a824bb31a91967",
            "5aa51a78a62cdfbc34a54fb0ffbd92bce2e9e53c51d47f5b6d6c261e54a4aeb1",
            "e2de8cadf8ce5638c81c0ec6bcc22490f25d341c82e9e849773a42ed08b08451",
            "3d289370f899e8bb663d5b3c5bf125c4391c6e9cc0a98afa75eb5a4e4061a071",
            "dc6ece50803b3b314d1bde48774abe5d9665ab9e12047f94360429593116d068",
            "ebe7a84b5818440e243afde7fa4eb80a8aee7e6c44994667216f1a4765d5993b",
            "7e33fe6c470a645410c94ad724b359628a6c54411aa12e2c12fbe0e992c791f7",
            "9aba16cf1e8b6800dd66b182927eaa801361e33e2d11a43841ece7c232cc6201",
            "e7b5513d262271b78779d201ab898bab5c6522fdee36cccef8eb0d2c6dcf70c8",
            "eb39750c9a7451d234e9c142417d2a02d55bf216ae4755715546b28e1e2be1b8",
            "aa9295dffd8cca0a66c366ec4ed58b7cb4a26130b6d3530acf4f17b103f79b91",
            "b1516a8420138ff552aeab8158e56d21927d929ab9a6cccd37bf28f5c2dc8d47",
            "1b238d8ffc61610f13d1590aa0d8d2338cd614c10fddb26014632cab2b966415",
            "38830d6b7c45a2f9a6d3ded20f5bbbd23946ca41877f80a0d207637db9ae7be7",
            "08004d3c7c82fb48b02314b1c84d7e5688e57a75a01231ddd6c2ebf2810a5b23",
            "05f91bda4a72e972a54d37459670731e84e7d8d4d7b99e100dde7894bd3228b0",
            "4512e2a52de3847d0e298fbb0463b16b3d080f9f4d1999a2f7bfcaf7542a8f02",
            "692136ce3fabf871f4bb67b704b943f3dcab6338879090cea8468ca648e8c722",
            "c7cedc9721a73d39aacce79b11cfdc3d57299b4c9c50340f05dcdc4b8d20d5d8",
            "40c7bea64d4a1c55c4e78c8fb216139d4d1db0d2754029608417dfd50c34a75a",
            "269868e4f3822ca6060a23e7753fd4fea17de026e7f1b41de6d342246062aac2",
            "04d1111e510aa1e2adff1220f52f538a4edb4d2d555035616b34dadb3842c277",
            "40113e7ae5cc167ae29f8ae00c51f10366407ebdbd57ead8ac69592a5f5a47ab",
            "4959b8b51ea4777930311ae73e0aa13c87e42fc412270da5f868decb0d838c29",
            "fce305c744f3857be626f55402e9c776cee61bdf918f56c311512b0605b0d323",
            "9de9250937407b1552e12c9a8259d23f1fa538a9760a93d9272cc8e0752a0150",
            "41155506348ae57f7ac9b1db5c5f8d2ed6dc64c7e579b7e4e28de504b1951c1b",
            "ab09e95b4414f7841bfef0f995c9430edeb9352425d0b148b8540e84b2c9b525",
            "5c6893f74e78ed544b2ea08b57a4216224a6a1f25258d8b61896fddbcbb5f9c7",
            "7655e71f8c68dabd32d6d205412a3d6f7117744a2b2dd960138bd833ac53be25",
            "c1dadad97678016529dd81e28f0679a3f13f349672239c9f4aa86b7144edb0df",
            "7fe1058e348977897db6449df58b7efc388dfa9f088429d305535e8c974b1279",
            "3573eeb7840309ce095c1c0a4eb872aea443b8f30463a4e774e5b80e74e9777c",
            "740511fd464273669290eedeb091450980bce2b9ea32e08f096b53fa38752a88",
            "70dfd8915a68bae280920585ce0fde6dc0f6bdb0966ac642fc050ff7298cb389",
            "640b75b40293e5b8f2a5ca534d96bb5ba6c5c131da898c35d7f4b979aa96a270",
            "9b4d4660ec6ca4ae32106f04bcc283260de74d25ee69747f8584b56766b660ed",
            "55f49bc8268c5f59f02799d2d7c0e2e8f7dc06a5ea25b942ddbba0d11fb1b7e0",
            "33c577257f9f05e40faccdf35756fefc4f769d1ee5b383b3c257686ceea196be",
            "e751aea64ef7e6137a081226c842f46e04b7cc93dc1d0d8e38e1e66fdfb8d719",
            "899e4e81109b167a6bf1cc467facf2f3d131e10267fb88bdf5d495914f95b54a",
            "c0577656558de1581c9ae7241e3623ad081032e79e31ef626de451659b09384c",
            "306ff3e3d8099392e6e87335eedc7853d2b0263a7233e1e8eb435352c5f95ffe",
            "d4b5b0547871c721ef2ffb5478732d65712a2413b1de3d41601497b72822c089",
            "39eeab33a93eaa53cf09d1fa122fabef684310aeb1e3cde1555f71bd4d0f0f86",
            "e30c60bdf905234fca590fd6eeee78f4ae7a4dd89030262baa9b35d9a07d2117",
            "449c0ab2cbc78bd859aace02ff9d5f4c108d4df7a8d10049f1db3ac70b007a55",
            "14a2796033fb380ab2c8aff05db521576a63dcebe034ba7d6f5f7cce735aaeea",
            "8c7b602a9883dec1e3782b6f48f3970825bdc775528d0b6a846009668e914ad2",
            "d6fc9367b68bff7baa46e0121a12778c4f9e31c80eae44da71624f8bbf1ca597",
            "5c33e1631e97986a2ddf421875ad4df6c60c901f313e81c3851da34a3ceac1d4",
            "e86e80a4fd1b34eeef8487809abe66f2b2eca0211487cc257e3fb6009544766b",
            "0e95dd2c29d3ec71044aef9628ca1c55b722bdac472418121bc7d74fa9a789b7",
            "a5c50050a04f85bde0252f06ea340d9d173f9d13c7384e1391959cf3923f714b",
            "8ac0b5e5bcdf10cdfa00a66756cfeb78a560422cf618946d977cd721744e0d9d",
            "9ef742ba86c55ea4bf95c7ed21dcce4bcb94a00f43690f6ffe9aeafc6a3e078d",
            "0401a399b4db508db0a9fe09fe92ed7f5fbc1a813baad4247a14281cabb5b3d1",
            "fdabc08be83c197b4f81904ff6231bb7a4f5c6c8bf4efa9733874ea6cff8b9bc",
            "3dc9899312f47fbc6b621f375379c7ac9b89c48b61e9e7e2e40249ff658a7e0f",
            "14c62dec2551f260e5a4f64bba9feab70f73e33184f93e4507c39a2514f83621",
            "af5f8e0be8d51259eca34c4ccac8bccf111b6b6c6a2416165c5453a702ffea80",
            "45efe6bda465476ee7d0d454406a8dec4104f806b5c4e189183cda1167218710",
            "6e85355662205967ab2465c87a1288edfd05b671e77f7ac65d8a8629e0768e0e",
            "7275f907f45e22a38cc0369a4c81b3906d8c02233722c69f4cd5ee8e5512ae78",
            "450e3f5480a1165dd49f2413f19aafeee2d412d885df7ef9ed9a411b948df9fd",
            "68333fe79dbfe07a68d854199c651774abbdbd5b5bddc363b79a2d4cfa2e80e9",
            "97f4d51d9b55d8af652a2669ffee04e445b508077612226d53b76090858c86fa",
            "ace506b6c95468a8e05c822c778756c4cc157b44053d4b515d8720b54f9d8a4a",
            "4ecdce4c704a178c9a861b1065eceaa3c522afccca7db497c22b6e293d19f9ca",
            "eedc5d524f4f861f547d9c57e0fca32bc64cc23b0237e6f9dbcbabc72cbcb33f",
            "69d2c242c0b83cf0197b04355d2e6f26275b9b7711d8d37245e9e4b8e8f7d240",
            "21a4ea45027f2780affe9dad949c944a3561bd9aaebbdf2081970238c66403a8",
            "cd2d4eb0555704807defd73fde4cc5fb3a0e0f0e15c199702d6b651e13d305bb",
            "b950aeaf2f897040f53ef15391a25501b8d69bbf69f5a87af1fc0ab5f7e26602",
            "9c92d52e577f908d4e411a4d04e3230bc3ec8b06acd11c6b988497a325626e20",
            "9b4e0648d2603a46acbd34904b6b69bb44795a2729bdf0d2d4a6822c045a44c9",
            "e09309c64fe7914eb5a4f00b3963d4397f759d2ea6f87c18ab80254f6a4e4ec6",
            "5774bef27710d436da5099781a69bbcef682003206479ebec045ae94a1d8685e",
            "932be8eaf4bca21568cd29b59a689202f66e883a71a193590a3adfc5997cb835",
            "a9d88e2ec1ac5f53de0ee1ae62b9dc37e43f0a2f72c4a2296f5957dec5a5765b",
            "6218e0ecd692252f4e1b4daa54661b061a35c04d407d102f9cbf046d71973e15",
            "997484720d3cacd39bc50acb3081f5f6ba4e7ace0bc013236ac36b54ddf62438",
            "251a9feae0f1b3034cb6a3e5c0fe84ea0b88e50912e10cf51ed50befc82a51c9",
            "68280285cb0f0fbc0eda0fab661593129c1b31854ad4b5938a878d51d4a14f32",
            "6ab62a33a31924e67a0d1c94571c665fa72565a4bd3a1377e8293e9e2329703f",
            "ef13e23c4748d58f9af88bb60fc0d2dd32266001f28e5de26c2dcb58efc9fa0d",
            "d4d41f71cb51b1d87b9cdc443cb491b6b42d4961b3a70b6e166aa53509acc10e",
            "4ba00825dde67083b7376fabd3419a1d85bd8f448a1dbdf5ec3be1b4b9302ffa",
            "6aea363b6dc6323aa282d941e1d861941a17491cc86acd8b7eb7ecc06dbf7e1b",
            "6907ddbba589df3fbff13e8687a2c7f8a3969162aba2b7e13165b9673f39221c",
            "a018a2d55c5f3629c593b6db345e3051a39577a8ab88df50116bfe6fd2a097cd",
            "dcad0daf72f44eceb21875d5697703d0bdf12e29921f7780f66de7fb92571a73",
            "8df5a1a8c13138ef56e684ae10788b35e0e11f74e183206c43ca7ffe7dbba94d",
            "5fa262b0f806ad3dbefce597e62a0b87a8ee24b519a3354e380a619f7a1a6979",
            "3baa7b65d98a0e603d05ffe6d22dc78e0ec9e6d86c4e354fcdfd9866a60aa546",
            "93ecdb07d14e31381abc6a23111aa2d4e26ae31911489d7b23d9b7e15fc5ff4b",
            "ed6ed52e6625d6b20cbed96de874cebd56dc119625501dafd588d2620eb71e39",
            "97fc35b01fca007bc990b9c7e5dbbf0287e86a0a9e63801dba68ba9b721a38c6",
            "f6bb40d6c7dc2573644941314aebf202d0cb4222afaef8ab7df09a9694e74112",
            "c7d6e4bbdc4ab2e1d4c05057a4ecb30115514b2fb9d5697f09872c8784ddf8c4",
            "085a4f7f699ed7ca36b87f57039c750fecc587e4ebbc8fd573d16448fed6fc12",
            "ede856537732739328fe97a6d877296d45a1e893402aac170c8a247105af3757",
            "540777029568e8162b5e18e76867aae1e18fa0584f392c2b81aa85ec6d140756",
            "c9aee1b7b939ca390c0e01f0e7973568ff6f51c75559e9994b299b6325189768",
            "705775af9574111282f5a021e8765777a87dae13878cd0d30d2a71752b31e46c",
            "b8de3794379608cfe70961484bbbf1e08c3772caeb4410e2e422a6bfef38a295",
            "fd05dd1cfd5efeefb3bd22da1cc766c02794f3cddbe5a20f3f048c370754ed11",
            "737ee326ac087086d43c9e522d9f55856eba2349d3dbd69ff1a5f577a3333eb9",
            "c26cf6a259c806e1a333d2c92899703e078f19c84c294e3220324e375459a0c8",
            "9d63edc53f5012d1a87a0b667bec1a4ec565d8f343c6c2ba6223d02bec75558d",
            "f085f1a4682e916c269be405ee61285be9e7bace5ade9108e296254ef525a3d6",
            "3c22e814c8b9df23730a618be44e35e5f8df4a7daac3dbfaf4b6812b3cfef018",
            "a302c10adb94ebf001fc364a37e4446d576c8278aa2835a1258456aabe4eb83a",
            "c2e16514c3231e6b3738ba0e2cfd19aae4aee036774595a9bbc27259ea1f2876",
            "c7356fc2a799df28936d8ff5fc29ee9beb94226d93ce9a6b84b792e802a6cf6d",
            "7d1c5e14cb1097d719a0104a74823e85b8713dc8e8f59718cd1eaa1d7ddd76a3",
            "ba01d2d02b62990d5e72cf09c43c8b953be70cf7948a6098ce42a43a1fef73de",
            "ce5069482513854fe132284c5b505724c1333f48cfc957fd6425df90f0a134ce",
            "54c24530d0bb9ca3e79c842a44d1b9aa3a73c9f2fadc65b2a91c225b900b84d9",
            "b29c04979a9229c908cde6a093a387d4de60b1c3d882de64057f21337463faf6",
            "b6683d0623fd9baeb0bc9977fccd7d10fba2b4d0db3ad0fa29a5d55a1b1219d8",
            "ded290cede00913b4c54b6fd6e61e5695a9d4df7909ea820326b85f914edfe69",
            "a6ff1829fa3c393775d30ceef4989242f3356e0435f89394b5b31ab0097747ff",
            "b999eb7aff4541a284fa4de9b840dd26f7f6a3c4bf48d6e6c3cd172cfd4dae05",
            "bed862c9f02278e44b1d037d88e987ca3eb1ba62f0567b3e352edc61185f9a6a",
            "433f1752580933f2d94561191141380744831990f0cb66d8d82e5c6ab303a936",
            "b5f0d123d5953f2eb4692bb61400984006d049e76179073993a7688fdd01de1d",
            "b2a1efe986620c8f6c499e30dacae876d334a36a4b4e34ebc85101be2ae02ec7",
            "7712d099ddc3cf162e11dbe95bf3254e1ac10eccc274256da8fd43617b99b6c4",
            "3dcc62df14f049016780d05d056804d13da13ce80a7856edd5f73f6b64d8b31a",
            "36d5b170a8f1f3f14789a82a6a2b481269b52d9361511b5ba4b068ef61a82970",
            "fc509dccf91eb3267ee7361d262ab4b2959ecc0666a99922bcb6d600d9801526",
            "bb12b484deba2fe03a306c576547c99c5a779a5dd0fa0956f2a75a76be863d03",
            "44ccc4dad11d7a14d9972fe8c30c6e0807ffff83cc2457161cf637e0000541b1",
            "6dca05532f071a151240d26b66de149b5bbd711d1e6c5c7e409a9bc8aa6dab32",
            "361fbcb8a105a1f16056a7d10d37def30b0e9ee09fedd3a664d5f9969838fc06",
            "e3ed796a657a623757208e26f56d4eb9cd7f972ee6053336fc7d08e208a82004",
            "5ff49f51e6a850f64375b24fcc0b7431579734e41a7925437c49ecc2655962bf",
            "ed4a91354611accaa9bec1123f1aeb33006112ff195ac672ac2c1355f2623160",
            "219cc2997c05c9901389983de16a1fc2924d9980273eb44798a23888747e0876",
            "4333e53e365299ea94610c45e0a127aa57ebcb572c72044234a687f049d5e9ef",
            "c40feb480190239c11baeadc189004356037845d5ecd839031c4af73cff29b1c",
            "17e6fc9245a522344fa62925e3d892c11005ba5a816c277473340ad3d473535f",
            "15c953543e6296fbc934e0f51db7c0b92832e10b849c52b4bd396d0a2ce71f0f",
            "69c5a2ed40c8b52a0e800e5fc21c194c2913e846eac415b8a7f6e178f9f9efab",
            "c818e1efc314cdd18f32bb61ff7356bdc65d5ba7460f9118d8dea08daa55e0a1",
            "391acd936c7cfb2e76da4b9e52294b86fb33af4639f16955802a2e500a4edd63",
            "e928468fc969c1b52a1f785d4f5a500ed308cb3fd4e2681edd8c4733a2dbb70b",
            "d766e53f79211febc1a39bc6eaaefce7d863f29ef241534da4bbf0fadd10236b",
            "2a0e564e0409719eebfc27fc54bf7706b70bc911227f6a433d42b9f639b7f7c6",
            "544bad4facc7b53d76285d9602abcd455e1f9c93c4662a0adb76ba71b126a385",
            "9231e71fc97873c7a5993fdbdac413a5c23e2d7d8d24b1d4e14bd340aab87938",
            "9ad0d40cbe617b09fc80c93725e8a91c7671d8e56c5ac0fd660c01f6187346be",
            "9251a04a3b59960ecc6ad1ab90be5a0faf5310d007c801ea330ab7e4183d0f32",
            "f0b56dc19a5efe11c57a3ff4e41d59c9469e49f8c70f4baeba0b82f2d53e0a3c",
            "b3f2151da1cb45efb14523692c474873a4da294ee5690e80f6d3bbd97b99773b",
            "dbd52462e608b72c6a266bdef56583c2d38ec7facdf6685f3149f592f7f527e4",
            "510a2c21b9097b87b363deb7a0b709fe7ba2af69a4f29eafcad14ff2a3a169b3",
            "cceb187a9a4235c1e8b718ec772391d5976c979ce27d338af5b0015b87b58de2",
            "7d8e76c7b1aa5fd161afd5f1402dad21c028bcf6ffb6d5b62055a144739724e5",
            "61b920879de14bfc725624608a4df594a33217fc12a5d2e8c691e0ab0639fb7b",
            "c36aae5b54daac912065cb6aa56c46996e2d7e38fde30f8855430768dfeebac6",
            "01f380ffc5e6c29a60456007f4b72bef84563c7d40a3d2d0ce37f13496464f60",
            "d49192e50b5422cf55788d97a68b1f55310ceb8757938ec5182226b66cabccf4",
            "57b4fe0287a289ed888c83b5544f1fda48d60a18f3e983720e716034ca744ab4",
            "ecd0d6ba8b09d1c18c934ef60cc62746e348384710c91a08ec5b56cf302b6353",
            "29479beee76278d993168e44cd6af6295b7fb53df9a7d66e29c01a6f2b99f5f7",
            "969c1bb29963d06913e91f739b9db1dcf6457893b81e96973a64090c41cf5d61",
            "bab776f7a1e107fbfc46aa4fabb9434038a95a7e69569c4484bfc76926f555a8",
            "205a71d8c11034b9f5522fbb5139ad0912e59b1dc8d7274bd2d5f44e8821cffc",
            "1810467134692954fbea7bc982d44a487607de469f83550e46cdfa246b5b86d0",
            "25eb1e486263e12c9ab44ad73a361a48371db9f280fcaee7d1d72790cf856f3a",
            "3d7f313286a7cfe2e906db16a183b7ab08462545e52d788fc9ddc9aaa7f03408",
            "a36a24d34f075cd8cc2447bdfbcb6094b8dfd178391864b5f9ac9880b01d2788",
            "45132cd136c1a90c93af514da48c076bf51eacc3eb22a5456d867d6347d3b37f",
            "e14ee0bbfa785ed48c072a80934aa2b17acb84e3153199f6b65c10e745adb27a",
            "73736d0287dc114bc1930426d9cb76b83a6586af94bf3114da31d83b4745aedb",
            "4838b32c77faa259b743d5a9e5afd249fd1c2b0e2a3a87e307394472ebf4365d",
            "d48949eb956c25562e42193d72cf2093ec623c985d468458e89369cb952864b9",
            "0c67d59a032d656bcd106ab0e2221e4f989b5e49b4c6e5b127079348f4720c94",
            "acea310937b071e9ce374c486369f149b245b84fedab706effc89502d94f1e82",
            "b34052abcb87a00f716e3e08d54f433291e5e0cf008b6923f2213eaabd862484",
            "311ba8e0c1a63f25eea85503da4c77eb4b1a3e77b71e4ffc34c630ee77bbde94",
            "81ccbae8ebb9de93be94672a608d99d9ced8514f14f0cd6efb0cf16f1e62bf5d",
            "c5de3b19998f2fb544cb95a66c9c465d94f9f06235e0e37416b40c332167d2f5",
            "bc17854b43972742a7c854bf62313a268340e8bd7bf8acdbf492f52f7dd5fe08",
            "5872b06bb84e9660bc0a604323534997cddad15057ec67be5595ce3f0a00d7c3",
            "2c884a799bf405c970e8bedab8b18347793eb3de58bb1c2d601411fbda86153d",
            "d278806a777aef99535ae86f4df9b645d4d0c07b5d08f60aa2ae3116cf3a7eb2",
            "991c4937d2fc4396431c328278c26b5c64c9246576ae90075a82c5c9329f3a77",
            "dfcbb12934032ea57f42383be767f41d8200e36e076b6c411fa073aa77bd3934",
            "14316385087573c35ef809a16bdfba0b70163f26f2abee7b0855810644500d35",
            "062b653890b6ff052bed8fc7d69b9cacfbca9eebbe8f4f6631bc95a30661975c",
            "11e3c792a4b444b43650827de954a0e6615a65edda1492e2e306530bb13fac96",
            "c0d87f03676ad9f91fd4c1a4162fd8c2cfa3c060314fa6bd18f1528257bd6f67",
            "028c38ee2692b8df0d2a19ac38fa1535135d7ca13c90705bafb8d8706f2f9669",
            "1df3fa45a3a12ba5950bb859352c121be3227eefaaacaf4cd8d228f4691a2f4c",
            "14107fc9da4a865c4113d4cda8cfd27ca96b2c897ecd5b97b55ea3288066e5bf",
            "9958ada11f49ae7ea3820353779740b620807e84447be69cad195ccf48f8635a",
            "9d1bb3be3305c60b33825f790d699f1d39ce88b498dd587b9dee87ace44a6dc7",
            "22e29942a351fc6ae89311c3584d2b878edbb7d0f8f9a6345a6a12a372a08054",
            "7e6240a67adf8f69cfbc0fbc30148b954fcf9f9423c3feadb5f39cca595b371d",
            "be73f8e3f881ef9db5f2ad0cd46f464ab1ef55e4d6a9d1f2827a403bfa2eca6e",
            "9761098684a1d626009070765e129df7537ec247e9933460cbb3015df9db98c3",
            "9e0ad51a425206feab3734d696cb626116a42dee07b06fe6c435825766a5c062",
            "3b1a75b8c149f7b7e20458f92af578a195ace27a76d77132c343d93055539a8d",
            "3f42f83f3972ea2b988ac1f6efce83986f69dca5c347f6911dee7eb53fbf45f6",
            "6599ad9c1703602b62e389211e4e5e78e8d283d475c2330a9a4a9acaa979aa4f",
            "92e785bda0d4aed1233af715ebe91e48719904fb445de6c31f448eda8ce43d27",
            "a8e0a4fbb8fe2a43a4889246c3ac4104b04ca68ac0892a59b65ecfd16878b0ac",
            "190b9b8f0b2c83d298d68f821a772706681b605861401d1a59067ca3e491e2da",
            "c28ceda90f8ddcf833e083c58db7df9480491fbc56946d0dea0a1a7f5d7539e8",
            "afe3732f0f1c19560ae02021b64b62ee49ed6bb2bb2f13bd55f9b257dd50edcf",
            "26d2fee58ffa4b024312ea03d0cae32535d63cc3f43d0936dab185de57ef7fbe",
            "15ef195749852eff5131a3e642b5e98ddc2413a77758bcbd3d14f395d22b1245",
            "05ba53b5e73b96962068fd920ff2a84f376c61db26f3c82af5c44a9aa0f56168",
            "f86902dfc1842e292e0d993383b92ad0308e5fd3634898d3c1b4e8baa1cac889",
            "3fe2b80fa0c5e17a0a1e6f6b4d2382dcef7355a8664205a26dfc233968d90e39",
            "15f207e3a89dfba0ca4cc87e4d09f26caa82ddc64e6b8757a1997361b2318de5",
            "f3183ea29d0caa63ba08e08b7016e525c180333edb85c6f02da2a0a851e5f08f",
            "4b489198d344b2cb285d8b1750a37c72a1508c2d3644fa2969dbe62f79d64d3e",
            "d8f84b9e1a6d559b1cd56a2aeea4a7cfaee893771dff2c76fd14dfbb38f724fd",
            "93dab6359deaa1f33ef8b11fb3e97f6ac90c568b7ef940c125b8d5293579aa7b",
            "37292c96c5089bd9abcdca2213983b95897df0711da04dfaa5686c8e2fb362d7",
            "4f22e347b5b269ad81ede2f5c1ad5646475fe554fc282d8464a27b0c78450000",
            "0839b8434a4e31c4cd4f220ddc1f9e4d9ef26c28c36a077ccb19315982e6fcd2",
            "c855add569084ec5490fe7bccfd84119012f682e1b7d174d1d39f047554efdba",
            "b0811a3c9de67b10481e98e531088eae56cedf7a6a83cc6f36c8d80b3d1b19b7",
            "95b99dfa8b247dd69fdfb5c28983bcbcddbef4d66615edbc1c59a66fd4fc83c6",
            "ad9cdaa18624a135ebadc800de90381467b2047c28fc7878c0735d69d8f98f69",
            "c8cc5d8a0e6bf1fb711419cb87d024ebdee068e1ae66e0a70a06d5c31b531456",
            "73748b538ad1796298be10499733d794ff22071a69ecc71f773a414b9115f84c",
            "31c8ac8bb3962fbbb82afc845bfb5475325595cdc1bf6992c7fa8bf64f808430",
            "81d6bd909eafc5c6cd01636ff13ca3e0a2ca162581f3cbbad0b9e231898862fc",
            "1da45e0f68837a9b34bc46f1c555d8e1b9e61c62cf7d39bd3678aa8c4cc3c650",
            "a9488f3a5194c213a089a8af06365de0bf303d115673e7d04b4bdffcaa6efacb",
            "b1aafbe54b29d47cfbae45c990e6bb6817fb72ee934f44d36973042348198c1a",
            "a17446c704bed5fd2cf3078e33010102e6cd239926393c78a9d69dfbed64165b",
            "6d556b0589b30b2fa4611cedcc9e179f8f97fae465791204fde117d78c37c84d",
            "6a676f9aa900a5ca7708cb81efd091b45334d6397440ea3f515a32ee1303bc9b",
            "a17f24ab754a18c4d48a70ca15271741ecdabc4517289391546ec0e4e279c7f3",
            "77ec8aa3853696d57ff65c3e9c9fe6ced2ae81448a7188353774d56e16df2022",
            "ce3da43f70fb829b15ceb5dfdb52c345de7f2a292ee565feeb6976b61a8c3bc7",
            "eb7b2ca72c09f310f3d0e15cbdb5a8d3aa26761ac397a6e86b63d57728c71b27",
            "583db3f766c152e9fec756ec79988a7ea3eb436de19e07d253eaf71cd6f9383f",
            "378ba1f097a0fc79c158cf65c8bcccd61f6946b1cb177747a291b5fcb46b9f1b",
            "16275227c06ebd8bcdcc591f34aae8d5e85ed7c79c3253b1872bef725f39ac7b",
            "c1b9cf27446331c7ee11c4f507b2835e356684db2ca05cf475b6389927c3d147",
            "51bfe8ce608f5b5dd212c4af2ce54f69f84b1aba22d5851b7074ce43915f0045",
            "7e20be75689fe553debf4569cd2775b442ac4de0ba5777bd9a975736c83b9ae2",
            "48c49138cfdf4f1743e56808326aade4e2d1ab37a9ad50b12daa7575c3d6ab14",
            "4e87d29f9adabd29fc70f0dd02113194c8de73e79a75f1251fc1fa662e52ad5e",
            "aa0f3719593ada267993d7bd0ff7f0cc5c748c4a5cc8f947b2040be2d9830918",
            "2d6abbcafa41e24440b079f8b008af2914c0818aed40073b98a917f7604a0412",
            "e3e23e2563b7a7ed894c36ae5afd773fe547e5411b6a4ec73431ff474d423653",
            "6dd235438030ce2ca4b88587905b98ccd3b53fbcd38f2a2b11146546e38eaab0",
            "77f75ba7a3fbf054d1931b951a76ffd42096620e8dfd59ef028b88b381fafb9d",
            "2b6feb45ec3346cbb6006ebe806a2c3b6bb271f4eab6f5e710b19ba95af977ea",
            "1632e92fc89c8d859439061bb391c773edd16624dd5257e17eb8db074887363c",
            "4681b543f7370515e584c1a6271d42e35ef34460902e812a4bbf5bf41bddb2ea",
            "e9eb1e179e4b8f1aab311cfc615f1c5acb81cb0e5593af4ad4a5a4bd013bb340",
            "f4ce9a458336f33ae20d614344966fca8bec60030de5cd615e2f3cfed10549fb",
            "0c41a2b1525a7a42e6f6272e1ed493cd3ab6ad1f45dc2ffd08f910b77709ee3a",
            "dfe0fa04d2c2d792be88df2173655d68ac1f2f86924a3af109e316d48893cb4e",
            "5be19391f66f0b4df210bc646cb033ef7be20b57d6b002b0dc748b261b9ddf29",
            "36dcedfdd9f86c5c33e5d3b6dc21f3bc9d14bd9bb32be307fe692e359dcfdbc4",
            "001ba805a9d904ba1b67cf681d5b02d6c7db904c1c1c343445f4cf3349a876b3",
            "34672f02a817ec3394f6a6c631e1aad9812149f4207b5636619a00cd4a51f2ef",
            "c5aee18197ae2bbaa4b91745bc0fef418a54f51168c038426da94d039aac9169",
            "bc3689d6449e2af5f3521abbeb69bac2b68e389005ac554ac6100e54b998c867",
            "f345f3237c82085c13027b666960027046214456bc33d2c97ecadab9c84d94dc",
            "4b62ec2f0b311df784dc855f1fd328cc6c8500be4191e641e8741edebd64e09e",
            "13067bf82e5d740eac45813598032ba72077afed14a42009c9e15515416d3f64",
            "b8a53b5629afdeff40d0e9deb56f3204ae0ee7071f8f9642088cabf43ac2dd88",
            "5a83c8577981458f25dbaff10ace327fb7f23e60b7075214e4e03af974f59e05",
            "9bff703159cd5d7b18170868bd752dfa5ca887b282b9ad6bd68fa76c4579672e",
            "8c870364c00d6cbe37846e9c67db9f31fd8d1f35e984b99bd4f4ced6ae027185",
            "4559f865a1eb5bdec2d6ba8e9942f4f956bb4f6a63e20a167dcabd5181a389f0",
            "597b289bbfbaa6dc6846ffdbfa30b027b15837e4308a729ba801aa2c176bc3c7",
            "3b149434eaca4570293f61f7d7df1a7afaddad2277f5bbebfdd8473763aa7889",
            "40fb0315299e6a9d7a67b366eecd868923bbd0873ce9d0e7987a730e9a464f1e",
            "c3ad267b61d59f83dd9d5fe09a6666597ef782fce6ee52a11b260c2ba4885eaf",
            "a71b2008c64357e01a0fbe5f7aafd5fbc2748ae76c6c7121fcccdda2f080013e",
            "1a21c4fbb9f6ffebd29d75e0214fe2c58bb4f2fcf12d68f2f8f5cdce84713729",
            "ebb35fb8a5a3b76eb6140eebe6814ebb532b1aff5bec36be12f5fcac2a4c1d84",
            "d12d8c08061af70bb161ead41f5ab3b0fcf943432e01fda1292851f19ed4ebe0",
            "4f55e759683c59ecfdd26aa2950665094548228fd6f31454d68d754a0e7b87be",
            "078029b10b0a39670744f17a68c2fd541cd69217191175d94802b90e67cddb9f",
            "9dabba32ac30a08ec3071ac00cb9d05369bc0b6fc97b29e3abe5bb19a016ce82",
            "2c6ef09975f2b805c128b617a934269574d1368ecfc859796b2ce31dd229122a",
            "d120cbc71923d586bb824f8d916eff013fde966de3ae0e1d18a6734843f9abd0",
            "1edab278020647d9d661aacfc5887548f57e47fa69df08605186acfd1be06cde",
            "fe95296c5bbd9acf6bea4b178791ed825b94220dfb432044cbe6cd7f60ca328e",
            "9a58882f6878fd0248306327fea23cd186177948e5029f584d43eb1425c50569",
            "7d0e88f151780b27a793d475dda814eba830d3ff627dc95fcf381aa217ab7d7f",
            "15efee8a12f534eb021bb1aad0224443d178e8941268f839fef99f0af10122e9",
            "0ceb418c7632bc4eeb0421b2ba9de7f04b83a3db888df8aa8e92b89a25f0da7c",
            "a6856b47a1a8d726c81bcc33b0eed4409a67d68a4277084ed3176bb256a273f4",
            "98c35bd1b101e07562cb38dd07a5211ad250dd9f7e9b5fef505511ddb9aa3569",
            "34ed2a995413a48e75de47f427dbd803255c31a1decf99e0d721060dc9999b42",
            "787269d38bccd6890c28164c5634cf1da4f90c105a1411ec51cfd20c05302361",
            "ef567b49ffb9a893cc0931712b7584115d1425fb40ffa29bc663579e04d159f6",
            "08c7cd27c78616b88af95bf86a7ac8f502e7d95ddb89d8b43ec34359a4321db0",
            "47fe6ac76b0cc5325d02ede000d2caf28c94519ba799ffe441e6df72772bf024",
            "a3a30f160b6bb1481affc3852a15110975cb5da07f165c2c97b9966877f62004",
            "19296c8ed5b425fe4448a3fdac6487040e657ee3191a3099b49765cfb87e7284",
            "4eca9a45b23aefeac7796a30ab4552d017e65cfbff0b06341c2f061219371a59",
            "7305438ba1986e019d77a70ed314d35efa6fc6def742327793c3b41a204b17fb",
            "9c508b2f224785014b44f5c846182e6f9d8b592b900d5b6dfc56e4cfbb33a615",
            "7d77a6307688ea5ad9024420d4473981b28564d54c805dff784cf3ab8a2882b2",
            "927ed44a86f58b405a94629ee907ef43212907144602d35f6e0f303881c36ce0",
            "006004d04fe07c9f69c5e0a81860505acbe96d8a67273fea6ecac53b8b876f64",
            "b5a5256eb6549e80f0c32c0afbc20ca3efe5e0398e98e47dd0c53fc761f862b1",
            "d5e5b51e3d384a0bb6e1140960e0bd2ee05ba71ef395f27e49c6f35bb0e45955",
            "c3bdde396682f04d4ae2be821d698b5d0b5370dbd09c396170e5ab0ed3ca26cd",
            "3af6c02367dd66920542b0bfd181a7141768dae23810f72f1b85b3a698b2f3e6",
            "121c3ece42b8fcd8d70ceef876842e2d7aa78ec8fcfa70e77c2e9a56e799ed89",
            "bb2b67f36c548358957a4183647bbc22f34fd98b0be5f817978e3f832301a20b",
            "cd5f586f214fa5a40da0e7ff3e6539cd93eea183b36a5b7568db8b796b8d6ba1",
            "a6db5401bb81fd533182be052a191862288f2be70a88e79a269a38900b90aaf9",
            "784c689e427f96efe9b249e83e92c72b8e1a9743acc266ec34d5320139ddd888",
            "9401f67c0176e64210fd02454cf4763b81b92c12dcaf493b129111d4451b5825",
            "1993a4ff30528b36e917508c0a685c0ca272ece2d17c03487a6c63a49b1f6c07",
            "986e72939bca755f2cd45fbdc3dc222830b743841d94e4df26376cc0d188b823",
            "2a0317e07f5c0e1ed87f2d20429dda974595670fa63be56f797875de78e63c3d",
            "5a55f23d76ed2367a115b5944f8457fe54ec654987ba06596d42b013a0d499a6",
            "3ee743b56ac8a80e31bf5730ff32d4e3669261075ad3f10c1600275cd971adb8",
            "18ee627ae30463255dfa5d9e99176fd946e26aa6470b433067978e9ed2e694e8",
            "3580c965850f88dacb0105556e488bfd4556c2d9862c74d04f15e2d9be615e8f",
            "3c72064f3a32f595c671fba499d74629909ed938aceb431d1ffc13afe144281f",
            "fcb43c062d5071eec43310557e0d82a621814810b8294477b254ad23cc696de1",
            "4f490d4ee1f5a708894593988e725da38416dfa9688119be00ba7af3ea1dc3c5",
            "555ebdd05ed88e8fed4cd8e85ecad13ab7f2a6d9c8a78929e9f602be31f7e950",
            "663c13068582053387a7e3730e915919c348c7495e14ee82a572bc82e70c1703",
            "da26cffb01471cdc38de7bced9f596fa692a9e9e728dd640f222eb1c9bade909",
            "91c96243beadfb360826b72f27942599830beafa1e919add5bbae68be80cc8ae",
            "b9a1c55100507c231dd06699ea5daf7ab92bfc98b2f1a71bddd194c2b8f94208",
            "125df7d90373691ad869a261dbe24d2b3de2af04843fd461ace8d2fc10e9df8a",
            "4cb07afee33c9a31370900e9490def25541466e54c5128d9124af46c9c271140",
            "bdb349ef8fb67839dda70ee396aee6dd987f4764e2aecbeec4f9c0bcde021ae7",
            "86d2b074681fa5deaaa084d84f09b35e0ec6ff009d5283c3620ec333fad508df",
            "efb3c94ff47ad652c9068db56be9a72863fac1eed7c54fc18431baf7fab3ff5a",
            "b395e13a974b1cac8e7572ba12010d0972acdcdefb80e6a19d7a0ef38d0ed43a",
            "569688d2dcc728413e3cb38cbda1d708d7dda191df1f8e05413cd5018b8a3543",
            "6422a14f3afc89549c4a743f64a0420a259cf6cb9780cb85bd1dd5954ebe5313",
            "bc86b075a511368806bd67a65f68e471e3d4acd815df83034d59bf8afe5545b6",
            "acaef266e87744f882da013aff827e32defc66d9fd2af723b51f143c563d9e34",
            "ea39aa86e8db053faf0ec13abe3097d7ee125f69aacbb472f8143d865300337a",
            "5ae7e8e21d4e2f9c1701f25be1bc518c8308c461d0eab80aea795871cd3879b7",
            "52bad360b65753060c9b6179653cf7b33065b6d0ae608df0f66839e06fd7db97",
            "81b5e47443dbca2896794ddcb13030c71a7a66704c09d7fa61c64bb2ae4bec82",
            "49ee759fda83d00664d4a16e279dd9ace303dce89bf0765e7ab86947978f2a9a",
            "02fa1e24933d8d1a0e562f1d2ba969e01b0634c6e1c5d447d84cf19fe05d1606",
            "2afcaa14d7110ce1cab5414056f6ede3c08b52dfecd13b9e2083e930cdd23b3f",
            "3bdcd71172d1e70f734e5b8317e0c00c83ed1b160db9ad8eb77ba9b8644874f8",
            "0055b380be1c24bfa3184db5fa06834e5c5d4681dc0bc25f859307326b73f12c",
            "96724ac1dad0fcf9565f8a61358911b72528cba2a9f6ec31be082034c6fa7a83",
            "fb3178649fc459f3e9c6d01360632fe30247c923e60cf5fcc929f2698c0522d0",
            "b8151eead3601f53f33c1efda72e27c334539125484fdbaacc040a25831da91b",
            "4f0b9cfa71905e0f15286c10554e0f702258a4c4c11e49fab9742065d24547fc",
            "38ea62780997ca6f957a9b80fc2e743616bf99e22491f7e967a174ccaed14110",
            "d9a03833d897cd9ea03e942cbb8ec81e80a181a2f75112cc811fce5054c88098",
            "bca2f447f0a98e2a5745b68916050824c7a6269993e4fd4a3c56f5509bbe0f01",
            "8ad12f3352e93954d68d1cd41fc5e44d20a5468280129bcb1cb0b14e21f570e1",
            "073a8b07aae90c1e843849fe02a2800059a8c890ac04b5cb5a2cfd8d05fd465f",
            "5e36c8bfc7fe69b31552c8cb9ac03ef5fe51cf356ffb7840062f3f22d0a44643",
            "1013b66971310ce3546737a7e326a10067ef478a5ac0a32d3035a832bf384662",
            "65936aa21d7a57eb2dc6cff0669d88ddff2aa66a3e7be84b208d574dd3b6986d",
            "6858b368c22f7a2c46d574495218705a8ec161243f62455936404fa4cd8b1942",
            "6d441cad176cb18554cb43b978da85b6509042c302660cad775b6065f2d14419",
            "9d16eb40ffb62a2dc8a34fa7e0c7e1653ce6fcc42a37ebb490328fe512365533",
            "38ba136b08d335882837eaaaf72db3e9d2d19bb969cf1bfef9bed91a90ec127c",
            "a954ac7bd9d86b081dc73f4c5e5d7476c7ba5a562a658cc263fd8c4b1615a8e1",
            "ba4f15bba5126d441f8d37f39108ef5e364f85147f67e4809ae5cdb09265445f",
            "8c20b945a12e63468756b98b7b46a18e024dac43f6aeb463d6136d78c525d4ea",
            "d6fe39ef54011d7df3cd2773ef3f5aafc416618fe5983eb2d6b2cf71b21822ab",
            "875d194d73bb763413dbf7cd238f3ca71f34a0e90c4a1b3957ec252fc03f8d88",
            "3abfcd978793e6d70a39c505c093b6cac135b68d5670476b178415cca408c296",
            "8e81741f0a96f8ccd09a4a766eb0bab6a6c1b16a7b70cd23b604d891b2edac62",
            "df96e9319f3db406092285da6a93ad7b516270c9c8c286dffc21e0e83167a18a",
            "0bbeffbf1ecbe4d227a00caf4588d9c6aec8e60c9cae81f4bf0b22c1bb3b57be",
            "e18ffcdaff0df6983600773e5344e9ee289a0993f1a88ba79407cb008c63d9d4",
            "665b64943c1dcf36f135577b8581952d1ccf47a4e173327e36c83d4d7c293ca5",
            "a337ab144b91e2fe82dc73190a4221f601a947657fc7159118d0c3bff66bb8f6",
            "2edc234977953ae7855e6f8dcaa960a25e26c9db4c67f8f13dfa689a1d957954",
            "cf2f0916edaf16f2cb682e16fc9c026dea14cdc1819009f5d20173d9a27f4340",
            "abd306f77f0b85528abaa6c3aa5eb146a8a1d6d1eaea583c8aeb4810a425986a",
            "4137a40e040eb1f930729de8f2fc6251457a03dfebd6afeaf1803c6cbe16b93e",
            "6cdf62b5a4d6852e7e760ad601d188926fd1e86af971c466299133c6db965517",
            "4dbf389fad487a0ed0e5f1391909d7bdb171c715b192aba3854e4975cad99225",
            "bae8d99d38cb18aa1581defb3d1ac28d094a5f445a0ddc0e4dc5c95b54c2faf2",
            "5ed048722769477b8196bf6a1f08404488348d86096b95df6281bb5aaf3949b7",
            "a15ac551a580a33e1584039953dbde5cced5fb871cceb49d6aca095628cbdfd5",
            "d001da08330cb41afba14f7517b0c5a5ded3f0ac98dab42f9721e07499dbb2e7",
            "449bc232ab73fc5214d3f329f58233aef0428c1d2cadebd379f9417c4d9bb869",
            "538a9950408d048170b857a0eb8a5712830325279ec9886f144b6ac9fd75ff30",
            "a89944b366dba78709fb763a3f2e032ed5c59836c5add8d7a81b566a638d89ce",
            "99ea95f3f52bdf0daca360a7d2c83a49728239c43326103803e7bf2c412a61f6",
            "3fa70de7a450bf74a9653c64780a4d118503256ce7d0d6dede928e53e54007f4",
            "b1b1e777582d9ba68154ceb4f6a6a6acd1de3a1680bf4dd65ecd7bd223676cba",
            "0cef5c60826ea53e51e728950329416ded0fb179223df360cb8f08d38bacf7a5",
            "4fd6a88ff71921883a62f8690ca4c0582f974cb9ec1a0cdf16ac0e7edd98ef27",
            "61224b9ec156bf72225adff80512da777fed88621b48236dd3aeaf7480a126b9",
            "3fb2bd5eba04cca94dfca94c4808fa28c460c968f0f429239471e9ec61788bf6",
            "4e408ab5d41a1e037b219f1f90d2dc7e8bb622d1c8814e0e90d9764b70a65f06",
            "238020693a8345d228661d75141cea79365244113b0b8d9aae7feef0b7bcf54d",
            "0e1d117374db64f8a4f2d7434423252d5b0540460671ad1038c3398540f85e18",
            "bfaa081865c614d7a2b6d6b13138b709f008c2acf03e039964971865656daf2b",
            "aa7d846944dcd99f4601e6b030f33c01d35dc164f1351c06b7666d49747f5155",
            "0dfe318f136158a3292cd6f34e2fd14dd1a6fe6150d9d15746c6ea4536986515",
            "36fe01ac97428a6bc855af0b6c08430a2b56f51e47d250484cfe1bcb61612130",
            "a4bc20bed71360801de893d8b426f337afd765b1275dc53a9b3fd3f342818842",
            "8f79171efe2690530f17dee2be9a94f436f846a77415cf43820e7a1b1e5b4df4",
            "cfdf96b58662357595590097ca8b02be4099eec2f71b4f6a7430fa15ffdbee79",
            "39c8f8af7753355607d71f4adc32560f992774b70ea98e647ee0ed84f4cacf97",
            "1dd23df66fa83a98556e2bcc0942ee1fe757d3a872b11fd6f0a984ae810f153f",
            "7d361d75343cb421492e939c683c9dad9921914cbf77a08eaaf263449d3dfbda",
            "d8e582053edf2a98af37081f24d90f27e5acfa4a4eceb897b8d69e0a6d8900ea"
        )

        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fakeWallet = PrivateKeyBuilder()
        val client = runBlocking {
            Client.create(
                account = fakeWallet,
                options = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.DEV, true),
                    appContext = context,
                    dbEncryptionKey = key
                )
            )
        }
        // Create the group
        val group = client.conversations.newGroup(memberInboxIds)

        // Sync the group
        group.sync()

        println("LOPI: Group created with ID: ${group.id}")
    }
}
