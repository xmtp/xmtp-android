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
        val group = boClient.conversations.newGroup(alix.walletAddress)
        assert(group.id.isNotEmpty())
    }

    @Test
    fun testCanListGroupMembers() {
        val group = boClient.conversations.newGroup(alix.walletAddress)
        group.addMembers(listOf(caro.walletAddress))
        group.send("Howdy")
        assertSame(
            group.memberAddresses(),
            listOf(
                caro.walletAddress.lowercase(),
                alix.walletAddress.lowercase(),
                bo.walletAddress.lowercase()
            )
        )
    }

    @Test
    fun testCanListGroups() {
        boClient.conversations.newGroup(alix.walletAddress)
        boClient.conversations.newGroup(caro.walletAddress)
        val groups = boClient.conversations.listGroups()
        assertEquals(groups.size, 2)
    }

    @Test
    fun testCanListGroupsAndConversations() {
        boClient.conversations.newGroup(alix.walletAddress)
        boClient.conversations.newGroup(caro.walletAddress)
        boClient.conversations.newConversation(alix.walletAddress)
        val convos = boClient.conversations.list(includeGroups = true)
        assertEquals(convos.size, 3)
    }
}