package org.xmtp.android.library

import com.google.protobuf.kotlin.toByteStringUtf8
import junit.framework.TestCase.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import org.xmtp.android.library.messages.*
import org.xmtp.proto.message.api.v1.MessageApiOuterClass
import java.lang.Thread.sleep
import java.util.*

class AuthenticationTest {

    @Test
    fun testCreateToken() {
        val key = PrivateKeyFactory()
        val identity = PrivateKeyFactory.privateKey.generate()
        // Prompt them to sign "XMTP : Create Identity ..."
        val authorized = key.createIdentity(identity)
        // Create the `Authorization: Bearer $authToken` for API calls.
        val authToken = authorized.createAuthToken()
        val tokenData = authToken.toByteStringUtf8()
        val base64TokenData =
            Base64.getEncoder().encodeToString(authToken.toByteArray()).toByteStringUtf8()
        if (tokenData.isEmpty || base64TokenData.isEmpty()) {
            fail("could not get token data")
            return
        }
        val token = Token.parseFrom(base64TokenData)
        val authData = AuthData.parseFrom(token.authDataBytes)
        assertEquals(authData.walletAddr, authorized.address)
    }

    @Test
    fun testEnablingSavingAndLoadingOfStoredKeys() {
        val alice = PrivateKeyFactory()
        val identity = PrivateKey.newBuilder().build().generate()
        val authorized = alice.createIdentity(identity)
        val bundle = authorized.toBundle
        val encryptedBundle = bundle.encrypted(alice)
        val decrypted = encryptedBundle.decrypted(alice)
        assertEquals(decrypted.v1.identityKey.secp256K1.bytes, identity.secp256K1.bytes)
        assertEquals(decrypted.v1.identityKey.publicKey, authorized.authorized)
    }

    @Test
    fun testSaveKey() = runBlocking {
        val alice = PrivateKeyFactory()
        val identity = PrivateKey.newBuilder().build().generate()
        val authorized = alice.createIdentity(identity)
        val authToken = authorized.createAuthToken()
        val api = ApiClient(environment = XMTPEnvironment.LOCAL, secure = false)
        api.setAuthToken(authToken)
        val encryptedBundle = authorized.toBundle.encrypted(alice)
        val envelope = MessageApiOuterClass.Envelope.newBuilder()
        envelope.contentTopic = Topic.userPrivateStoreKeyBundle(authorized.address).description
        envelope.timestampNs = Date().millisecondsSinceEpoch.toLong() * 1_000_000
        envelope.message = encryptedBundle.toByteString()
        api.publish(envelopes = listOf(envelope.build()))
        withContext(Dispatchers.IO) {
            sleep(2_000_000_000)
        }
        val result = api.query(topics = listOf(Topic.userPrivateStoreKeyBundle(authorized.address)))
        assert(result.envelopesList.size == 1)
    }
}