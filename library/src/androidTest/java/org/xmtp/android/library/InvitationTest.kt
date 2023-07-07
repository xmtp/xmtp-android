package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.protobuf.kotlin.toByteString
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.InvitationV1ContextBuilder
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.PrivateKeyBundleV1
import org.xmtp.android.library.messages.SealedInvitation
import org.xmtp.android.library.messages.SealedInvitationBuilder
import org.xmtp.android.library.messages.createDeterministic
import org.xmtp.android.library.messages.generate
import org.xmtp.android.library.messages.getInvitation
import org.xmtp.android.library.messages.getPublicKeyBundle
import org.xmtp.android.library.messages.header
import org.xmtp.android.library.messages.toV2
import java.util.Date

@RunWith(AndroidJUnit4::class)
class InvitationTest {
    @Test
    fun testExistingWallet() {
        // Generated from JS script
        val ints = arrayOf(
            31, 116, 198, 193, 189, 122, 19, 254, 191, 189, 211, 215, 255, 131,
            171, 239, 243, 33, 4, 62, 143, 86, 18, 195, 251, 61, 128, 90, 34, 126, 219, 236
        )
        val bytes =
            ints.foldIndexed(ByteArray(ints.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
        val key = PrivateKey.newBuilder().also {
            it.secp256K1 =
                it.secp256K1.toBuilder().also { builder -> builder.bytes = bytes.toByteString() }
                    .build()
            it.publicKey = it.publicKey.toBuilder().also { builder ->
                builder.secp256K1Uncompressed =
                    builder.secp256K1Uncompressed.toBuilder().also { keyBuilder ->
                        keyBuilder.bytes =
                            KeyUtil.addUncompressedByte(KeyUtil.getPublicKey(bytes)).toByteString()
                    }.build()
            }.build()
        }.build()

        val client = Client().create(account = PrivateKeyBuilder(key))
        Assert.assertEquals(client.apiClient.environment, XMTPEnvironment.DEV)
        val conversations = client.conversations.list()
        Assert.assertEquals(1, conversations.size)
        val message = conversations[0].messages().firstOrNull()
        Assert.assertEquals(message?.body, "hello")
    }

    @Test
    fun testGenerateSealedInvitation() {
        val aliceWallet = FakeWallet.generate()
        val bobWallet = FakeWallet.generate()
        val alice = PrivateKeyBundleV1.newBuilder().build().generate(wallet = aliceWallet)
        val bob = PrivateKeyBundleV1.newBuilder().build().generate(wallet = bobWallet)
        val invitation = InvitationV1.newBuilder().build().createDeterministic(
            sender = alice.toV2(),
            recipient = bob.toV2().getPublicKeyBundle()
        )
        val newInvitation = SealedInvitationBuilder.buildFromV1(
            sender = alice.toV2(),
            recipient = bob.toV2().getPublicKeyBundle(),
            created = Date(),
            invitation = invitation
        )
        val deserialized = SealedInvitation.parseFrom(newInvitation.toByteArray())
        assert(!deserialized.v1.headerBytes.isEmpty)
        assertEquals(newInvitation, deserialized)
        val header = newInvitation.v1.header
        // Ensure the headers haven't been mangled
        assertEquals(header.sender, alice.toV2().getPublicKeyBundle())
        assertEquals(header.recipient, bob.toV2().getPublicKeyBundle())
        // Ensure alice can decrypt the invitation
        val aliceInvite = newInvitation.v1.getInvitation(viewer = alice.toV2())
        assertEquals(aliceInvite.topic, invitation.topic)
        assertEquals(
            aliceInvite.aes256GcmHkdfSha256.keyMaterial,
            invitation.aes256GcmHkdfSha256.keyMaterial
        )
        // Ensure bob can decrypt the invitation
        val bobInvite = newInvitation.v1.getInvitation(viewer = bob.toV2())
        assertEquals(bobInvite.topic, invitation.topic)
        assertEquals(
            bobInvite.aes256GcmHkdfSha256.keyMaterial,
            invitation.aes256GcmHkdfSha256.keyMaterial
        )
    }

    @Test
    fun testDeterministicInvite() {
        val aliceWallet = FakeWallet.generate()
        val bobWallet = FakeWallet.generate()
        val alice = PrivateKeyBundleV1.newBuilder().build().generate(wallet = aliceWallet)
        val bob = PrivateKeyBundleV1.newBuilder().build().generate(wallet = bobWallet)
        val makeInvite = { conversationId: String ->
            InvitationV1.newBuilder().build().createDeterministic(
                sender = alice.toV2(),
                recipient = bob.toV2().getPublicKeyBundle(),
                context = InvitationV1ContextBuilder.buildFromConversation(conversationId)
            )
        }
        // Repeatedly making the same invite should use the same topic/keys
        val original = makeInvite("example.com/conversation-foo")
        for (i in 1..10) {
            val invite = makeInvite("example.com/conversation-foo")
            assertEquals(original.topic, invite.topic)
        }
        // But when the conversationId changes then it use a new topic/keys
        val invite = makeInvite("example.com/conversation-bar")
        assertNotEquals(original.topic, invite.topic)
    }

    @Test
    fun testTopic() {
        val ints1 = arrayOf(8,54,32,15,250,250,23,163,203,139,84,242,45,106,250,96,177,61,164,135,38,84,50,65,173,197,194,80,219,176,224,205)
        val privateKeyData =
            ints1.foldIndexed(ByteArray(ints1.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
        // Use hardcoded privateKey
        val privateKey = PrivateKeyBuilder.buildFromPrivateKeyData(privateKeyData)
        val privateKeyBuilder = PrivateKeyBuilder(privateKey)
        val options = ClientOptions(api = ClientOptions.Api(XMTPEnvironment.DEV, isSecure = true))
        val bigClient = Client().create(account = privateKeyBuilder, options = options)

        val ints = arrayOf(229, 179, 73, 249, 137, 18, 206, 100, 35, 169, 82, 177, 81, 1, 239, 62, 170, 38, 214, 242, 170, 0, 7, 31, 200, 100, 43, 203, 135, 55, 159, 141)
        val privateKeyData2 =
            ints.foldIndexed(ByteArray(ints.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
        // Use hardcoded privateKey
        val privateKey2 = PrivateKeyBuilder.buildFromPrivateKeyData(privateKeyData2)
        val privateKeyBuilder2 = PrivateKeyBuilder(privateKey2)
        val randomClient = Client().create(account = privateKeyBuilder2, options = options)

       val invite = InvitationV1.newBuilder().build().createDeterministic(
            sender = bigClient.keys,
            recipient = randomClient.keys.getPublicKeyBundle(),
        )

        assertEquals(invite.topic, "/xmtp/0/m-67e55220b023e6efbeb4305ec060d86ad83529a71bd5cf77c964c55d46b9ffad/proto")
    }
}
