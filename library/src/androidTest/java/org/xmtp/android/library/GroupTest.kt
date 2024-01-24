package org.xmtp.android.library

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    lateinit var fixtures: Fixtures

    @Before
    fun setUp() {
        fixtures = fixtures(clientOptions = ClientOptions(enableLibXmtpV3 = true))
        alixWallet = fixtures.aliceAccount
        alix = fixtures.alice
        boWallet = fixtures.bobAccount
        bo = fixtures.bob
        fakeApiClient = fixtures.fakeApiClient
        alixClient = fixtures.aliceClient
        boClient = fixtures.bobClient
    }
    @Test
    fun testCanCreateAGroup() {
        val group = boClient.conversations.newGroup(alix.walletAddress)
        assert(group.id.isNotEmpty())

        val groups = boClient.conversations.listGroups()
        assertEquals(groups.size, alixClient.conversations.listGroups().size)
    }
}