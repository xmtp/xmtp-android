package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.codecs.ContentTypeReaction
import org.xmtp.android.library.codecs.Reaction
import org.xmtp.android.library.codecs.ReactionAction
import org.xmtp.android.library.codecs.ReactionCodec
import org.xmtp.android.library.codecs.ReactionSchema
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class GroupTest {
    lateinit var fakeApiClient: FakeApiClient
    lateinit var alixWallet: PrivateKeyBuilder
    lateinit var boWallet: PrivateKeyBuilder
    lateinit var alix: PrivateKey
    lateinit var alixClient: Client
    lateinit var bo: PrivateKey
    lateinit var boClient: Client
    lateinit var caroWallet: PrivateKeyBuilder
    lateinit var caro: PrivateKey
    lateinit var caroClient: Client
    lateinit var fixtures: Fixtures

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fixtures =
            fixtures(
                clientOptions = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    enableAlphaMls = true,
                    appContext = context
                )
            )
        alixWallet = fixtures.aliceAccount
        alix = fixtures.alice
        boWallet = fixtures.bobAccount
        bo = fixtures.bob
        caroWallet = fixtures.caroAccount
        caro = fixtures.caro

        fakeApiClient = fixtures.fakeApiClient
        alixClient = fixtures.aliceClient
        boClient = fixtures.bobClient
        caroClient = fixtures.caroClient
    }

    @Test
    fun testCanCreateAGroup() {
        val group = boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        assert(group.id.isNotEmpty())
    }

    @Test
    fun testCanListGroupMembers() {
        val group = boClient.conversations.newGroup(
            listOf(
                alix.walletAddress.lowercase(),
                caro.walletAddress.lowercase()
            )
        )
        assertEquals(
            group.memberAddresses().sorted(),
            listOf(
                caro.walletAddress.lowercase(),
                alix.walletAddress.lowercase(),
                bo.walletAddress.lowercase()
            ).sorted()
        )
    }

    @Test
    fun testCanAddGroupMembers() {
        val group = boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        group.addMembers(listOf(caro.walletAddress.lowercase()))
        assertEquals(
            group.memberAddresses().sorted(),
            listOf(
                caro.walletAddress.lowercase(),
                alix.walletAddress.lowercase(),
                bo.walletAddress.lowercase()
            ).sorted()
        )
    }

    @Test
    fun testCanRemoveGroupMembers() {
        val group = boClient.conversations.newGroup(
            listOf(
                alix.walletAddress.lowercase(),
                caro.walletAddress.lowercase()
            )
        )
        group.removeMembers(listOf(caro.walletAddress.lowercase()))
        assertEquals(
            group.memberAddresses().sorted(),
            listOf(
                alix.walletAddress.lowercase(),
                bo.walletAddress.lowercase()
            ).sorted()
        )
    }

    @Test
    fun testCanListGroups() {
        boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        boClient.conversations.newGroup(listOf(caro.walletAddress.lowercase()))
        val groups = boClient.conversations.listGroups()
        assertEquals(groups.size, 2)
    }

    @Test
    fun testCanListGroupsAndConversations() {
        boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        boClient.conversations.newGroup(listOf(caro.walletAddress.lowercase()))
        boClient.conversations.newConversation(alix.walletAddress.lowercase())
        val convos = boClient.conversations.list(includeGroups = true)
        assertEquals(convos.size, 3)
    }

    @Test
    fun testCanSendMessageToGroup() {
        val group = boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        group.send("howdy")
        group.send("gm")
        assertEquals(group.messages().first().body, "gm")
        assertEquals(group.messages().size, 3)

        val sameGroup = alixClient.conversations.listGroups().last()
        assertEquals(sameGroup.messages().size, 2)
        assertEquals(sameGroup.messages().first().body, "gm")
    }

    @Test
    fun testCanSendContentTypesToGroup() {
        Client.register(codec = ReactionCodec())

        val group = boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        group.send("gm")
        val messageToReact = group.messages()[0]

        val reaction = Reaction(
            reference = messageToReact.id,
            action = ReactionAction.Added,
            content = "U+1F603",
            schema = ReactionSchema.Unicode
        )

        group.send(content = reaction, options = SendOptions(contentType = ContentTypeReaction))

        val messages = group.messages()
        assertEquals(messages.size, 3)
        val content: Reaction? = messages.first().content()
        assertEquals("U+1F603", content?.content)
        assertEquals(messageToReact.id, content?.reference)
        assertEquals(ReactionAction.Added, content?.action)
        assertEquals(ReactionSchema.Unicode, content?.schema)
    }

    @Test
    fun testCanStreamGroupMessages() = kotlinx.coroutines.test.runTest {
        val group = boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))

        group.streamMessages().test {
            group.send("hi")
            assertEquals("hi", awaitItem().body)
            awaitComplete()
        }
    }

    @Test
    fun testCanStreamGroups() = kotlinx.coroutines.test.runTest {
        boClient.conversations.streamGroups().test {
            val conversation =
                boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
            conversation.send(content = "hi")
            assertEquals("hi", awaitItem().messages().first().body)
            awaitComplete()
        }
    }

    @Test
    fun testCanStreamGroupsAndConversations() = kotlinx.coroutines.test.runTest {
        boClient.conversations.stream(includeGroups = true).test {
            val group =
                boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
            val conversation =
                boClient.conversations.newConversation(alix.walletAddress.lowercase())
            assertEquals("hi", awaitItem().messages().first().body)
            awaitComplete()
        }
    }
}