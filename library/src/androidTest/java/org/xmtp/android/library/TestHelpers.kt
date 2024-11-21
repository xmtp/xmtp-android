package org.xmtp.android.library

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Uint
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Numeric
import org.xmtp.android.library.artifact.CoinbaseSmartWallet
import org.xmtp.android.library.artifact.CoinbaseSmartWalletFactory
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.Signature
import org.xmtp.android.library.messages.ethHash
import org.xmtp.android.library.messages.walletAddress
import java.math.BigInteger
import java.security.SecureRandom

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

const val ANVIL_TEST_PRIVATE_KEY_1 =
    "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
const val ANVIL_TEST_PRIVATE_KEY_2 =
    "59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d"
const val ANVIL_TEST_PRIVATE_KEY_3 =
    "5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a"
private const val ANVIL_TEST_PORT = "http://10.0.2.2:8545"

class FakeSCWWallet : SigningKey {
    private val web3j: Web3j = Web3j.build(HttpService(ANVIL_TEST_PORT))
    private var contractDeployerCredentials: Credentials? = null
    var walletAddress: String = ""

    override val address: String
        get() = walletAddress

    override val type: WalletType
        get() = WalletType.SCW

    override var chainId: Long? = 31337L

    companion object {
        fun generate(privateKey: String): FakeSCWWallet {
            return FakeSCWWallet().apply {
                contractDeployerCredentials = Credentials.create(privateKey)
                createSmartContractWallet()
            }
        }
    }

    override suspend fun signSCW(message: String): ByteArray {
        val smartWallet = CoinbaseSmartWallet.load(
            walletAddress,
            web3j,
            contractDeployerCredentials,
            DefaultGasProvider()
        )
        val digest = Signature.newBuilder().build().ethHash(message)
        val replaySafeHash = smartWallet.replaySafeHash(digest).send()

        val signature =
            Sign.signMessage(replaySafeHash, contractDeployerCredentials!!.ecKeyPair, false)
        val signatureBytes = signature.r + signature.s + signature.v
        val tokens = listOf(
            Uint(BigInteger.ZERO),
            DynamicBytes(signatureBytes)
        )
        val encoded = FunctionEncoder.encodeConstructor(tokens)
        val encodedBytes = Numeric.hexStringToByteArray(encoded)

        return encodedBytes
    }

    private fun createSmartContractWallet() {
        val smartWalletContract = CoinbaseSmartWallet.deploy(
            web3j,
            contractDeployerCredentials,
            DefaultGasProvider()
        ).send()

        val factory = CoinbaseSmartWalletFactory.deploy(
            web3j,
            contractDeployerCredentials,
            DefaultGasProvider(),
            BigInteger.ZERO,
            smartWalletContract.contractAddress
        ).send()

        val ownerAddress = ByteArray(32) { 0 }.apply {
            System.arraycopy(
                contractDeployerCredentials!!.address.hexToByteArray(),
                0,
                this,
                12,
                20
            )
        }
        val owners = listOf(ownerAddress)
        val nonce = BigInteger.ZERO

        val transactionReceipt = factory.createAccount(owners, nonce, BigInteger.ZERO).send()
        val smartWalletAddress = factory.getAddress(owners, nonce).send()

        if (transactionReceipt.isStatusOK) {
            walletAddress = smartWalletAddress
        } else {
            throw Exception("Transaction failed: ${transactionReceipt.status}")
        }
    }
}

class Fixtures {
    val key = SecureRandom().generateSeed(32)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val clientOptions = ClientOptions(
        ClientOptions.Api(XMTPEnvironment.LOCAL, isSecure = false),
        dbEncryptionKey = key,
        appContext = context,
    )
    val alixAccount = PrivateKeyBuilder()
    val boAccount = PrivateKeyBuilder()
    val caroAccount = PrivateKeyBuilder()

    var alix: PrivateKey = alixAccount.getPrivateKey()
    var alixClient: Client =
        runBlocking { Client().create(account = alixAccount, options = clientOptions) }

    var bo: PrivateKey = boAccount.getPrivateKey()
    var boClient: Client =
        runBlocking { Client().create(account = boAccount, options = clientOptions) }

    var caro: PrivateKey = caroAccount.getPrivateKey()
    var caroClient: Client =
        runBlocking { Client().create(account = caroAccount, options = clientOptions) }
}

fun fixtures(): Fixtures =
    Fixtures()
