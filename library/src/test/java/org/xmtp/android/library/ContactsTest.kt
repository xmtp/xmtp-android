package org.xmtp.android.library

import org.junit.*
import org.junit.Assert.*

class ContactsTests {

    @Test
    fun testCanFindContact() {
        val fixtures = fixtures()
        fixtures.bobClient.ensureUserContactPublished()
        val contactBundle = fixtures.aliceClient.contacts.find(fixtures.bob.walletAddress)
        if (contactBundle == null) {
            XCTFail("did not find contact bundle")
            return
        }
        assertEquals(contactBundle.walletAddress, fixtures.bob.walletAddress)
    }

    @Test
    fun testCachesContacts() {
        val fixtures = fixtures()
        fixtures.bobClient.ensureUserContactPublished()
        // Look up the first time
        fixtures.aliceClient.contacts.find(fixtures.bob.walletAddress)
        fixtures.fakeApiClient.assertNoQuery {
            val contactBundle = fixtures.aliceClient.contacts.find(fixtures.bob.walletAddress)
            if (contactBundle == null) {
                XCTFail("did not find contact bundle")
                return@assertNoQuery
            }
            assertEquals(contactBundle.walletAddress, fixtures.bob.walletAddress)
        }
        assert(fixtures.aliceClient.contacts.has(fixtures.bob.walletAddress))
    }
}
