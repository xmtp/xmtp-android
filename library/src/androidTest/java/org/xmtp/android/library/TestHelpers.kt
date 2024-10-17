package org.xmtp.android.library

import android.util.Log
import com.google.protobuf.kotlin.toByteString
import kotlinx.coroutines.runBlocking
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Uint
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Numeric
import org.xmtp.android.library.artifact.CoinbaseSmartWallet
import org.xmtp.android.library.artifact.CoinbaseSmartWalletFactory
import org.xmtp.android.library.messages.ContactBundle
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.Topic
import org.xmtp.android.library.messages.consentProofText
import org.xmtp.android.library.messages.ethHash
import org.xmtp.android.library.messages.toPublicKeyBundle
import org.xmtp.android.library.messages.walletAddress
import org.xmtp.proto.message.contents.SignatureOuterClass
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Date

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

class FakeSCWWallet(
    private val web3j: Web3j,
    private val credentials: Credentials,
) : SigningKey {
    var walletAddress: String = ""

    init {
        runBlocking {
            createSmartContractWallet()
        }
    }

    // Override address to return the created smart contract wallet address
    override val address: String
        get() = walletAddress

    override val isSmartContractWallet: Boolean
        get() = true

    override var chainId: Long = 31337L

    companion object {
        fun generate(
            web3j: Web3j,
            credentials: Credentials,
        ): FakeSCWWallet {
            return FakeSCWWallet(web3j, credentials).apply {
                runBlocking { createSmartContractWallet() }
            }
        }
    }

    override suspend fun sign(data: ByteArray): Signature {
        val smartWallet = CoinbaseSmartWallet.load(
            walletAddress,
            web3j,
            credentials,
            DefaultGasProvider()
        )

        val replaySafeHash = smartWallet.replaySafeHash(data).send()
        Log.d("LOPI1", replaySafeHash.toHex())

        val signature = Sign.signMessage(replaySafeHash, credentials.ecKeyPair, false)
        val signatureBytes = signature.r + signature.s + signature.v
        Log.d("LOPI2", signatureBytes.toHex())

        val tokens = listOf(
            Uint(BigInteger.ZERO),
            DynamicBytes(signatureBytes)
        )
        val encoded = FunctionEncoder.encodeConstructor(tokens)
        val encodedBytes = Numeric.hexStringToByteArray(encoded)
        Log.d("LOPI3", encoded)

        return SignatureOuterClass.Signature.newBuilder().also {
            it.ecdsaCompact = it.ecdsaCompact.toBuilder().also { builder ->
                builder.bytes = signatureBytes.toByteString()
            }.build()
        }.build()
    }

    override suspend fun sign(message: String): Signature {
        val digest = Signature.newBuilder().build().ethHash(message)
        return sign(digest)
    }

    private fun ByteArray.sha256(): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(this)
    }

    private fun createSmartContractWallet() {
        val factory = CoinbaseSmartWalletFactory.deploy(
            web3j,
            credentials,
            DefaultGasProvider(),
            BigInteger.ZERO,
            "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        ).send()

        CoinbaseSmartWallet.deploy(
            web3j,
            credentials,
            DefaultGasProvider()
        ).send()

        val owners = listOf(Hash.sha3("eip155:31337:0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266".toByteArray()))
        val nonce = BigInteger.ZERO

        val transactionReceipt = factory.createAccount(owners, nonce, BigInteger.ZERO).send()
        val smartWalletAddress = factory.getAddress(owners, nonce).send()
        Log.d("LOPI5", smartWalletAddress)
        if (transactionReceipt.isStatusOK) {
            walletAddress = smartWalletAddress
        } else {
            throw Exception("Transaction failed: ${transactionReceipt.status}")
        }
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
