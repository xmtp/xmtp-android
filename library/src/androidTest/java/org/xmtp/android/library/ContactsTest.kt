package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress

@RunWith(AndroidJUnit4::class)
class ContactsTest {

    @Test
    fun testNormalizesAddresses() {
        val fixtures = fixtures()
        runBlocking { fixtures.bobClient.ensureUserContactPublished() }
        val bobAddressLowerCased = fixtures.bobClient.address.lowercase()
        val bobContact = fixtures.aliceClient.getUserContact(peerAddress = bobAddressLowerCased)
        assert(bobContact != null)
    }

    @Test
    fun testCanFindContact() {
        val fixtures = fixtures()
        runBlocking { fixtures.bobClient.ensureUserContactPublished() }
        val contactBundle = fixtures.aliceClient.contacts.find(fixtures.bob.walletAddress)
        assertEquals(contactBundle?.walletAddress, fixtures.bob.walletAddress)
    }

    @Test
    fun testCachesContacts() {
        val fixtures = fixtures()
        runBlocking { fixtures.bobClient.ensureUserContactPublished() }
        // Look up the first time
        fixtures.aliceClient.contacts.find(fixtures.bob.walletAddress)
        fixtures.fakeApiClient.assertNoQuery {
            val contactBundle = fixtures.aliceClient.contacts.find(fixtures.bob.walletAddress)
            assertEquals(contactBundle?.walletAddress, fixtures.bob.walletAddress)
        }
        assert(fixtures.aliceClient.contacts.has(fixtures.bob.walletAddress))
    }

    @Test
    fun testAllowAddress() {
        val fixtures = fixtures()

        val contacts = fixtures.bobClient.contacts
        var result = contacts.isAllowed(fixtures.alice.walletAddress)

        assert(!result)

        runBlocking { contacts.allow(listOf(fixtures.alice.walletAddress)) }

        result = contacts.isAllowed(fixtures.alice.walletAddress)
        assert(result)
    }

    @Test
    fun testDenyAddress() {
        val fixtures = fixtures()

        val contacts = fixtures.bobClient.contacts
        var result = contacts.isAllowed(fixtures.alice.walletAddress)

        assert(!result)

        runBlocking { contacts.deny(listOf(fixtures.alice.walletAddress)) }

        result = contacts.isDenied(fixtures.alice.walletAddress)
        assert(result)
    }

    @Test
    fun testHehehe() {
        val privateKeyData = listOf(0x08, 0x36, 0x20, 0x0f, 0xfa, 0xfa, 0x17, 0xa3, 0xcb, 0x8b, 0x54, 0xf2, 0x2d, 0x6a, 0xfa, 0x60, 0xb1, 0x3d, 0xa4, 0x87, 0x26, 0x54, 0x32, 0x41, 0xad, 0xc5, 0xc2, 0x50, 0xdb, 0xb0, 0xe0, 0xcd)
            .map { it.toByte() }
            .toByteArray()
        // Use hardcoded privateKey
        val privateKey = PrivateKeyBuilder.buildFromPrivateKeyData(privateKeyData)
        val privateKeyBuilder = PrivateKeyBuilder(privateKey)
        val options = ClientOptions()
        val client = Client().create(account = privateKeyBuilder, options = options)
        val startTime = System.currentTimeMillis()

        runBlocking { client.contacts.refreshConsentList()}
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        println("Time to execute block: $duration ms")

    }
}
