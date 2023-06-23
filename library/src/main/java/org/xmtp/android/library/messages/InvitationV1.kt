package org.xmtp.android.library.messages

import com.google.crypto.tink.subtle.Base64.encodeToString
import com.google.crypto.tink.subtle.Hkdf
import com.google.protobuf.kotlin.toByteString
import org.xmtp.android.library.Client
import org.xmtp.android.library.messages.InvitationV1Builder.Companion.INVITE_SALT
import org.xmtp.proto.message.contents.Invitation
import org.xmtp.proto.message.contents.Invitation.InvitationV1.Context
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

typealias InvitationV1 = org.xmtp.proto.message.contents.Invitation.InvitationV1

class InvitationV1Builder {
    companion object {
        val INVITE_SALT = "__XMTP__INVITATION__SALT__XMTP__".toByteArray(StandardCharsets.UTF_8)
        fun buildFromTopic(
            topic: Topic,
            context: Invitation.InvitationV1.Context? = null,
            aes256GcmHkdfSha256: Invitation.InvitationV1.Aes256gcmHkdfsha256,
        ): InvitationV1 {
            return InvitationV1.newBuilder().apply {
                this.topic = topic.description
                if (context != null) {
                    this.context = context
                }
                this.aes256GcmHkdfSha256 = aes256GcmHkdfSha256
            }.build()
        }

        fun buildContextFromId(
            conversationId: String = "",
            metadata: Map<String, String> = mapOf(),
        ): Invitation.InvitationV1.Context {
            return Invitation.InvitationV1.Context.newBuilder().apply {
                this.conversationId = conversationId
                this.putAllMetadata(metadata)
            }.build()
        }
    }
}

fun InvitationV1.createRandom(context: Invitation.InvitationV1.Context? = null): InvitationV1 {
    val inviteContext = context ?: Invitation.InvitationV1.Context.newBuilder().build()
    val randomBytes = SecureRandom().generateSeed(32)
    val randomString = encodeToString(randomBytes, 0).replace(Regex("=*$"), "")
        .replace(Regex("[^A-Za-z0-9]"), "")
    val topic = Topic.directMessageV2(randomString)
    val keyMaterial = SecureRandom().generateSeed(32)
    val aes256GcmHkdfSha256 = Invitation.InvitationV1.Aes256gcmHkdfsha256.newBuilder().apply {
        this.keyMaterial = keyMaterial.toByteString()
    }.build()

    return InvitationV1Builder.buildFromTopic(
        topic = topic,
        context = inviteContext,
        aes256GcmHkdfSha256 = aes256GcmHkdfSha256
    )
}
fun InvitationV1.createDeterministic(
    client: Client,
    recipient: SignedPublicKeyBundle,
    context: Invitation.InvitationV1.Context? = null,
): InvitationV1 {
    val inviteContext = context ?: Invitation.InvitationV1.Context.newBuilder().build()
    val v2Keys = client.privateKeyBundle.v2

    val secret = v2Keys.sharedSecret(
        recipient,
        v2Keys.getPreKeysOrBuilder(0).publicKey,
        false
    )
    val sortedAddresses = listOf(
        client.address,
        recipient.walletAddress,
    ).sorted()
    val msgString = (inviteContext.conversationId ?: "") + sortedAddresses.joinToString()
    val msgBytes = msgString.encodeToByteArray()

    val sha256HMAC: Mac = Mac.getInstance("HmacSHA256")
    val secretKey = SecretKeySpec(secret, "HmacSHA256")
    sha256HMAC.init(secretKey)
    val topic = sha256HMAC.doFinal(msgBytes).toString()

    val infoString = "0|" + sortedAddresses.joinToString(separator = "|")
    val info = infoString.encodeToByteArray()
    val nonceData = SecureRandom().generateSeed(12)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val key = Hkdf.computeHkdf("HMACSHA256", secret, INVITE_SALT, info, 32)
    val keySpec = SecretKeySpec(key, "AES")
    val gcmSpec = GCMParameterSpec(128, nonceData)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
    val keyMaterial = cipher.doFinal(msgBytes)
    val aes256GcmHkdfSha256 = Invitation.InvitationV1.Aes256gcmHkdfsha256.newBuilder().apply {
        this.keyMaterial = keyMaterial.toByteString()
    }.build()

    return InvitationV1Builder.buildFromTopic(
        topic = Topic.directMessageV2(topic),
        context = inviteContext,
        aes256GcmHkdfSha256 = aes256GcmHkdfSha256
    )
}

class InvitationV1ContextBuilder {
    companion object {
        fun buildFromConversation(
            conversationId: String = "",
            metadata: Map<String, String> = mapOf(),
        ): Context {
            return Context.newBuilder().also {
                it.conversationId = conversationId
                it.putAllMetadata(metadata)
            }.build()
        }
    }
}
