package org.xmtp.android.library.frames

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmtp.android.library.Client
import org.xmtp.android.library.frames.Constants.PROTOCOL_VERSION
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.getPublicKeyBundle
import org.xmtp.proto.message.contents.PublicKeyOuterClass.SignedPublicKeyBundle
import java.security.MessageDigest
import org.xmtp.proto.message.contents.Frames.FrameActionBody
import org.xmtp.proto.message.contents.Frames.FrameAction

class FramesClient(private val xmtpClient: Client, var proxy: OpenFramesProxy = OpenFramesProxy()) {

    suspend fun signFrameAction(inputs: FrameActionInputs): FramePostPayload = withContext(Dispatchers.IO) {
        val opaqueConversationIdentifier = buildOpaqueIdentifier(inputs)
        val frameUrl = inputs.frameUrl
        val buttonIndex = inputs.buttonIndex
        val inputText = inputs.inputText ?: ""
        val state = inputs.state ?: ""
        val now = System.currentTimeMillis() / 1000
        val test = FrameActionBody
            .newBuilder()
            .setFrameUrl(frameUrl)
            .setButtonIndex(buttonIndex)
            .setInputText(inputText)
            .setState(state)
            .setUnixTimestamp(
            now.toInt()
        )

        val toSign = test.build()
        val signedAction = Base64.encodeToString(buildSignedFrameAction(toSign), Base64.NO_WRAP)

        val untrustedData = FramePostUntrustedData(frameUrl, now, buttonIndex, inputText, state, xmtpClient.address, opaqueConversationIdentifier, now.toInt())
        val trustedData = FramePostTrustedData(signedAction)

        FramePostPayload("xmtp@$PROTOCOL_VERSION", untrustedData, trustedData)
    }

    private suspend fun signDigest(digest: ByteArray): Signature {
        val signedPrivateKey = xmtpClient.keys.identityKey
        val privateKey = PrivateKeyBuilder.buildFromSignedPrivateKey(signedPrivateKey)
        return PrivateKeyBuilder(privateKey).sign(digest)
    }

    private suspend fun getPublicKeyBundle():  SignedPublicKeyBundle  {
        return xmtpClient.keys.getPublicKeyBundle()
    }

    private suspend fun buildSignedFrameAction(actionBodyInputs: FrameActionBody): ByteArray {
        val digest = sha256(actionBodyInputs.toByteArray())
        val signature = signDigest(digest)

        val publicKeyBundle = getPublicKeyBundle()
        val frameAction = FrameAction.newBuilder()
            .setActionBody(actionBodyInputs.toByteString())
            .setSignature(signature)
            .setSignedPublicKeyBundle(publicKeyBundle).build();

        return frameAction.toByteArray();
    }

    private fun buildOpaqueIdentifier(inputs: FrameActionInputs): String {
        return when (inputs.conversationInputs) {
            is ConversationActionInputs.Group -> {
                val groupInputs = inputs.conversationInputs.inputs
                val combined = groupInputs.groupId + groupInputs.groupSecret
                val digest = sha256(combined)
                Base64.encodeToString(digest, Base64.NO_WRAP)
            }
            is ConversationActionInputs.Dm -> {
                val dmInputs = inputs.conversationInputs.inputs
                val conversationTopic = dmInputs.conversationTopic ?: throw InvalidArgumentsError()
                val combined = (conversationTopic.lowercase() + dmInputs.participantAccountAddresses.map { it.lowercase() }.sorted().joinToString("")).toByteArray()
                val digest = sha256(combined)
                Base64.encodeToString(digest, Base64.NO_WRAP)
            }
        }
    }

    private fun sha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }
}
