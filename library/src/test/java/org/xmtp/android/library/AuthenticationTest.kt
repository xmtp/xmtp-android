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
//        val privateKey = PrivateKeyFactory()
//        val key1 = ubyteArrayOf(182u,
//            91u, 104u, 100u, 219u, 48u, 80u, 45u, 44u, 208u, 72u, 135u,
//            250u, 186u, 45u, 27u, 179u, 253u, 176u, 84u, 232u, 84u, 177u, 175u,
//            201u, 194u, 225u, 64u, 253u, 86u, 114u, 174u)
//        val key1Bytes = key1.toByteArray()
//        val key = PrivateKeyFactory.create(key1Bytes)
//        val key1Pub = key.publicKey.secp256K1Uncompressed.bytes.toByteArray()
//
//        privateKey.setPrivateKey(key)
//
//        val key2 = arrayOf(215, 112, 80, 239, 192, 233, 31, 135, 196, 237, 175, 68, 230, 68, 153, 137, 255, 252, 122, 108, 52, 58, 180, 57, 225, 170, 202, 250, 50, 16, 142, 224)
//        val key2Bytes = key2.foldIndexed(ByteArray(key2.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
//        val identity = PrivateKeyFactory.create(key2Bytes)
//        val key2Pub = identity.publicKey.secp256K1Uncompressed.bytes.toByteArray()

        val privateKey = PrivateKeyFactory()
        val identity = PrivateKeyFactory.privateKey.generate()
        // Prompt them to sign "XMTP : Create Identity ..."
        val authorized = privateKey.createIdentity(identity)
        // Create the `Authorization: Bearer $authToken` for API calls.
        val authToken = authorized.createAuthToken()
        val tokenData = authToken.toByteStringUtf8().toByteArray()
        val base64TokenData = com.google.crypto.tink.subtle.Base64.decode(tokenData,0)
        if (tokenData.isEmpty() || base64TokenData.isEmpty()) {
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