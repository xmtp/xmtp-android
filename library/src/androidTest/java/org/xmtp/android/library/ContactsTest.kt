package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.walletAddress
import java.util.Date

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
    fun testRefreshConsentListPagination() {
        val fixtures = fixtures()
        val contacts = fixtures.bobClient.contacts
        val aliceAddress = fixtures.alice.walletAddress
        runBlocking {
            contacts.deny(listOf(aliceAddress))
        }
        val date = Date()
        val result: ConsentList
        runBlocking {
            result = contacts.consentList.load(date)
        }
        assert(result.entries[ConsentListEntry.address(aliceAddress).key]?.consentType == null)
        val allResult: ConsentList
        runBlocking {
            allResult = contacts.consentList.load()
        }
        assert(allResult.entries[ConsentListEntry.address(aliceAddress).key]?.consentType != null)
    }
}
