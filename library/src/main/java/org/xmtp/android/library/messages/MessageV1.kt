package org.xmtp.android.library.messages

import org.xmtp.android.library.CipherText
import org.xmtp.android.library.Crypto
import java.util.Date

typealias MessageV1 = org.xmtp.proto.message.contents.MessageOuterClass.MessageV1
enum class MessageV1Error (val rawValue: Error) {
    cannotDecodeFromBytes(0);

    companion object {
        operator fun invoke(rawValue: Error) = MessageV1Error.values().firstOrNull { it.rawValue == rawValue }
    }
}

fun MessageV1.Companion.encode(sender: PrivateKeyBundleV1, recipient: PublicKeyBundle, message: ByteArray, timestamp: Date) : MessageV1 {
    val secret = sender.sharedSecret(peer = recipient, myPreKey = sender.preKeys[0].publicKey, isRecipient = false)
    val header = MessageHeaderV1(sender = sender.toPublicKeyBundle(), recipient = recipient, timestamp = UInt64(timestamp.millisecondsSinceEpoch))
    val headerBytes = header.serializedData()
    val ciphertext = Crypto.encrypt(secret, message, additionalData = headerBytes)
    return MessageV1(headerBytes = headerBytes, ciphertext = ciphertext)
}

fun MessageV1.Companion.fromBytes(bytes: ByteArray) : MessageV1 {
    val message = Message(serializedData = bytes)
    var headerBytes: ByteArray
    var ciphertext: CipherText
    when (message.version) {
        v1 -> {
            headerBytes = message.v1.headerBytes
            ciphertext = message.v1.ciphertext
        }
        v2 -> {
            headerBytes = message.v2.headerBytes
            ciphertext = message.v2.ciphertext
        }
        else -> throw MessageV1Error.cannotDecodeFromBytes
    }
    return MessageV1(headerBytes = headerBytes, ciphertext = ciphertext)
}

constructor(MessageV1.headerBytes: Data, ciphertext: CipherText) : this() {    this.headerBytes = headerBytes
    this.ciphertext = ciphertext
}
val MessageV1.header: MessageHeaderV1
    get() = do {
        return MessageHeaderV1(serializedData = headerBytes)
    } catch {
        print("Error deserializing MessageHeaderV1 ${error}")
        throw error
    }
val MessageV1.senderAddress: String?
    get() = do {
        val senderKey = header.sender.identityKey.recoverWalletSignerPublicKey()
        return senderKey.walletAddress
    } catch {
        print("Error getting sender address: ${error}")
        return null
    }
val MessageV1.sentAt: Date
        Date(timeIntervalSince1970 = Double(header.timestamp / 1000))
val MessageV1.recipientAddress: String?
    get() = do {
        val recipientKey = header.recipient.identityKey.recoverWalletSignerPublicKey()
        return recipientKey.walletAddress
    } catch {
        print("Error getting recipient address: ${error}")
        return null
    }

fun MessageV1.decrypt(viewer: PrivateKeyBundleV1) : ByteArray {
    val header = MessageHeaderV1(serializedData = headerBytes)
    val recipient = header.recipient
    val sender = header.sender
    var secret: ByteArray
    if (viewer.walletAddress == sender.walletAddress) {
        secret = viewer.sharedSecret(peer = recipient, myPreKey = sender.preKey, isRecipient = false)
    } else {
        secret = viewer.sharedSecret(peer = sender, myPreKey = recipient.preKey, isRecipient = true)
    }
    return Crypto.decrypt(secret, ciphertext, additionalData = headerBytes)
}
