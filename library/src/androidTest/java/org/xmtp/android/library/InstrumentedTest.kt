package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.generate
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.message.contents.PrivateKeyOuterClass

@RunWith(AndroidJUnit4::class)
class InstrumentedTest {
    @Test
    fun testPublishingAndFetchingContactBundlesWithWhileGeneratingKeys() {
        val aliceWallet = PrivateKeyBuilder()
        val clientOptions = ClientOptions(api = ClientOptions.Api(env = XMTPEnvironment.LOCAL, isSecure = false))
        val client = Client().create(aliceWallet, clientOptions)
        assertEquals(XMTPEnvironment.LOCAL, client.apiClient.environment)
        val noContactYet = client.getUserContact(peerAddress = aliceWallet.getPrivateKey().walletAddress)
        assertNull(noContactYet)
        runBlocking {
            client.publishUserContact()
        }
        val contact = client.getUserContact(peerAddress = aliceWallet.getPrivateKey().walletAddress)
        assertEquals(contact?.v1?.keyBundle?.identityKey?.secp256K1Uncompressed, client.privateKeyBundleV1?.identityKey?.publicKey?.secp256K1Uncompressed)
        assert(contact?.v1?.keyBundle?.identityKey?.hasSignature() ?: false)
        assert(contact?.v1?.keyBundle?.preKey?.hasSignature() ?: false)
    }
}