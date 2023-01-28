package org.xmtp.android.library.messages

import com.google.protobuf.kotlin.toByteString
import org.xmtp.android.library.CipherText
import org.xmtp.android.library.Crypto
import org.xmtp.android.library.extensions.millisecondsSinceEpoch
import java.util.Date

typealias SealedInvitation = org.xmtp.proto.message.contents.Invitation.SealedInvitation
enum class SealedInvitationError (val rawValue: Error) {
    noSignature(0);

    companion object {
        operator fun invoke(rawValue: Error) = SealedInvitationError.values().firstOrNull { it.rawValue == rawValue }
    }
}

class SealedInvitationBuilder {
    companion object {
        fun buildFromV1(sender: PrivateKeyBundleV2, recipient: SignedPublicKeyBundle, created: Date, invitation: InvitationV1) : SealedInvitation {
            val header = SealedInvitationHeaderV1Builder.buildFromSignedPublicBundle(sender.getPublicKeyBundle(), recipient, (created.millisecondsSinceEpoch * 1_000_000).toLong())
            val secret = sender.sharedSecret(peer = recipient, myPreKey = sender.preKeysList[0].publicKey, isRecipient = false)
            val headerBytes = header.toByteArray()
            val invitationBytes = invitation.toByteArray()
            val ciphertext = Crypto.encrypt(secret, invitationBytes, additionalData = headerBytes)
            return buildFromCipherText(headerBytes, ciphertext)
        }

        fun buildFromCipherText(headerBytes: ByteArray, ciphertext: CipherText?) : SealedInvitation {
            return SealedInvitation.newBuilder().apply {
                v1Builder.headerBytes = headerBytes.toByteString()
                v1Builder.ciphertext = ciphertext
            }.build()
        }
    }
}

fun SealedInvitation.involves(contact: ContactBundle) : Boolean =
    do {
        val contactSignedPublicKeyBundle = contact.toSignedPublicKeyBundle()
        return v1.header.recipient.equals(contactSignedPublicKeyBundle) || v1.header.sender.equals(contactSignedPublicKeyBundle)
    } catch {
        return false
    }
