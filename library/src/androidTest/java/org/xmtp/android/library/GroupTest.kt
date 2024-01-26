package org.xmtp.android.library

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress

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
            fixtures(clientOptions = ClientOptions(enableLibXmtpV3 = true, appContext = context))
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
        boClient.conversations.newConversation(alix.walletAddress)
        val convos = boClient.conversations.list(includeGroups = true)
        assertEquals(convos.size, 3)
    }

    @Test
    fun testCanSendMessageToGroup() {
        val group = boClient.conversations.newGroup(listOf(alix.walletAddress.lowercase()))
        group.send("howdy")
        group.send("gm")
        runBlocking { group.sync() }
        assertEquals(group.messages().last().body, "howdy")
        assertEquals(group.messages().size, 2)

        val sameGroup = alixClient.conversations.listGroups().last()
        assertEquals(sameGroup.messages().size, 2)
        assertEquals(sameGroup.messages().last().body, "howdy")
    }
}