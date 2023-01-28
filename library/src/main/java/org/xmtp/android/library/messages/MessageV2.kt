package org.xmtp.android.library.messages

import org.xmtp.android.library.Client
import org.xmtp.android.library.DecodedMessage
import org.xmtp.android.library.codecs.EncodedContent

typealias MessageV2 = org.xmtp.proto.message.contents.MessageOuterClass.MessageV2
enum class MessageV2Error (val rawValue: Error) {
    invalidSignature(0);

    companion object {
        operator fun invoke(rawValue: Error) = MessageV2Error.values().firstOrNull { it.rawValue == rawValue }
    }
}

constructor(MessageV2.headerBytes: Data, ciphertext: CipherText) : this() {    this.headerBytes = headerBytes
    this.ciphertext = ciphertext
}

fun MessageV2.Companion.decode(message: MessageV2, keyMaterial: ByteArray) : DecodedMessage =
    do {
        val decrypted = Crypto.decrypt(keyMaterial, message.ciphertext, additionalData = message.headerBytes)
        val signed = SignedContent(serializedData = decrypted)
        // Verify content signature
        val digest = SHA256.hash(data = message.headerBytes + signed.payload)
        val key = PublicKey.with { key  ->
            key.secp256K1Uncompressed.bytes = KeyUtil.recoverPublicKey(message = Data(digest.bytes), signature = signed.signature.rawData)
        }
        if (key.walletAddress != (PublicKey(signed.sender.preKey).walletAddress)) {
            throw MessageV2Error.invalidSignature
        }
        val encodedMessage = EncodedContent(serializedData = signed.payload)
        val header = MessageHeaderV2(serializedData = message.headerBytes)
        return DecodedMessage(encodedContent = encodedMessage, senderAddress = signed.sender.walletAddress, sent = Date(timeIntervalSince1970 = Double(header.createdNs / 1_000_000) / 1000))
    } catch {
        print("ERROR DECODING: ${error}")
        throw error
    }

fun MessageV2.Companion.encode(client: Client, encodedContent: EncodedContent, topic: String, keyMaterial: ByteArray) : MessageV2 {
    val payload = encodedContent.serializedData()
    val date = Date()
    val header = MessageHeaderV2(topic = topic, created = date)
    val headerBytes = header.serializedData()
    val digest = SHA256.hash(data = headerBytes + payload)
    val preKey = client.keys.preKeys[0]
    val signature = await
    preKey.sign(Data(digest))
    val bundle = client.privateKeyBundleV1.toV2().getPublicKeyBundle()
    val signedContent = SignedContent(payload = payload, sender = bundle, signature = signature)
    val signedBytes = signedContent.serializedData()
    val ciphertext = Crypto.encrypt(keyMaterial, signedBytes, additionalData = headerBytes)
    return MessageV2(headerBytes = headerBytes, ciphertext = ciphertext)
}
