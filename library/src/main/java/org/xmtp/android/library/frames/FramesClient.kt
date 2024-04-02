package org.xmtp.android.library.frames

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmtp.android.library.Client
import org.xmtp.android.library.frames.Constants.PROTOCOL_VERSION
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.PublicKeyBundle
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.SignedPublicKeyBundle
import java.security.MessageDigest
import java.util.*
import org.xmtp.proto.message.contents.SignatureOuterClass
import org.xmtp.proto.message.contents.FrameActionBody

class FramesClient(private val xmtpClient: Client, var proxy: OpenFramesProxy = OpenFramesProxy()) {

    suspend fun signFrameAction(inputs: FrameActionInputs): FramePostPayload = withContext(Dispatchers.IO) {
        val opaqueConversationIdentifier = buildOpaqueIdentifier(inputs)
        val frameUrl = inputs.frameUrl
        val buttonIndex = inputs.buttonIndex
        val inputText = inputs.inputText ?: ""
        val state = inputs.state ?: ""
        val now = System.currentTimeMillis() / 1000
        val timestamp = now

        val toSign = FrameActionBody(frameUrl, buttonIndex, opaqueConversationIdentifier, timestamp.toULong(), inputText, now.toUInt(), state)

        val signedAction = buildSignedFrameAction(toSign)

        val untrustedData = FramePostUntrustedData(frameUrl, now, buttonIndex, inputText, state, xmtpClient.address, opaqueConversationIdentifier, now.toInt())

        val trustedData = FramePostTrustedData(signedAction.encodeToBase64())

        FramePostPayload("xmtp@$PROTOCOL_VERSION", untrustedData, trustedData)
    }

    private suspend fun signDigest(digest: ByteArray): Signature {
        val signedPrivateKey = xmtpClient.keys.identityKey
        val privateKey = PrivateKeyBuilder.buildFromSignedPrivateKey(signedPrivateKey)
        return PrivateKeyBuilder(privateKey).sign(digest)
    }

    private suspend fun getPublicKeyBundle(): ByteArray {
//        val bundleBytes = xmtpClient.privateKeyBundle.toByteArray()
//                    Base64.encodeToString(client.privateKeyBundle.toByteArray(), NO_WRAP)
        return xmtpClient.privateKeyBundle.toByteArray()
    }

    private suspend fun buildSignedFrameAction(actionBodyInputs: FrameActionBody): ByteArray = withContext(Dispatchers.IO) {
        val digest = sha256(actionBodyInputs.toByteArray())
        val signature = signDigest(digest)

        val publicKeyBundle = getPublicKeyBundle()
        val frameAction = FrameAction(actionBodyInputs.toByteArray(), signature, SignedPublicKeyBundle(publicKeyBundle))

        frameAction.toByteArray()
    }

    private fun buildOpaqueIdentifier(inputs: FrameActionInputs): String {
        return when (inputs.conversationInputs) {
            is ConversationActionInputs.Group -> {
                val groupInputs = inputs.conversationInputs.inputs
                val combined = groupInputs.groupId + groupInputs.groupSecret
                val digest = sha256(combined)
                digest.encodeToBase64()
            }
            is ConversationActionInputs.Dm -> {
                val dmInputs = inputs.conversationInputs.inputs
                val conversationTopic = dmInputs.conversationTopic ?: throw InvalidArgumentsError()
                val combined = (conversationTopic.lowercase() + dmInputs.participantAccountAddresses.map { it.lowercase() }.sorted().joinToString("")).toByteArray()
                val digest = sha256(combined)
                digest.encodeToBase64()
            }
        }
    }

    private fun sha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }
}
