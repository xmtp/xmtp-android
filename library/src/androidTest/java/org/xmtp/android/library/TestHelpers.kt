package org.xmtp.android.library

import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.runBlocking
import org.xmtp.android.library.messages.ContactBundle
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.toPublicKeyBundle
import org.xmtp.android.library.messages.walletAddress
import java.util.Date
import java.util.*
import kotlin.text.Charsets.UTF_8

class FakeWallet : SigningKey {
    private var privateKey: PrivateKey
    private var privateKeyBuilder: PrivateKeyBuilder

    constructor(key: PrivateKey, builder: PrivateKeyBuilder) {
        privateKey = key
        privateKeyBuilder = builder
    }

    companion object {
        fun generate(): FakeWallet {
            val key = PrivateKeyBuilder()
            return FakeWallet(key.getPrivateKey(), key)
        }
    }

    override suspend fun sign(data: ByteArray): Signature {
        val signature = privateKeyBuilder.sign(data)
        return signature
    }

    override suspend fun sign(message: String): Signature {
        val signature = privateKeyBuilder.sign(message)
        return signature
    }

    override val address: String
        get() = privateKey.walletAddress
}

class FakeSCWWallet : SigningKey {
    var walletAddress: String
    private var internalSignature: String

    init {
        // Simulate a wallet address (could be derived from a hash of some internal data)
        walletAddress =
            UUID.randomUUID().toString() // Using UUID for uniqueness in this fake example
        internalSignature = ByteArray(64) { 0x01 }.toHex() // Fake internal signature
    }

    override val address: String
        get() = walletAddress

    override val isSmartContractWallet: Boolean
        get() = true

    override var chainId: Long = 1L

    companion object {
        @Throws(Exception::class)
        fun generate(): FakeSCWWallet {
            return FakeSCWWallet()
        }
    }

    @Throws(Exception::class)
    override suspend fun sign(data: ByteArray): Signature {
        val signature = Signature.newBuilder()
        signature.ecdsaCompact.toBuilder().bytes = internalSignature.hexToByteArray().toByteString()
        return signature.build()
    }

    @Throws(Exception::class)
    override suspend fun sign(message: String): Signature {
        val digest = message.toByteArray(UTF_8).sha256()
        return sign(digest)
    }

    private fun ByteArray.sha256(): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(this)
    }
}

data class Fixtures(
    val clientOptions: ClientOptions? = ClientOptions(
        ClientOptions.Api(XMTPEnvironment.LOCAL, isSecure = false)
    ),
) {
    val aliceAccount = PrivateKeyBuilder()
    val bobAccount = PrivateKeyBuilder()
    val caroAccount = PrivateKeyBuilder()

    var alice: PrivateKey = aliceAccount.getPrivateKey()
    var aliceClient: Client =
        runBlocking { Client().create(account = aliceAccount, options = clientOptions) }

    var bob: PrivateKey = bobAccount.getPrivateKey()
    var bobClient: Client =
        runBlocking { Client().create(account = bobAccount, options = clientOptions) }

    var caro: PrivateKey = caroAccount.getPrivateKey()
    var caroClient: Client =
        runBlocking { Client().create(account = caroAccount, options = clientOptions) }

    fun publishLegacyContact(client: Client) {
        val contactBundle = ContactBundle.newBuilder().also { builder ->
            builder.v1 = builder.v1.toBuilder().also {
                it.keyBundle = client.v1keys.toPublicKeyBundle()
            }.build()
        }.build()
        val envelope = Envelope.newBuilder().apply {
            contentTopic = Topic.contact(client.address).description
            timestampNs = (Date().time * 1_000_000)
            message = contactBundle.toByteString()
        }.build()

        runBlocking { client.publish(envelopes = listOf(envelope)) }
    }
}

fun fixtures(clientOptions: ClientOptions? = null): Fixtures =
    Fixtures(clientOptions)
