package org.xmtp.android.library

import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.proto.message.contents.SignatureOuterClass
import uniffi.xmtpv3.FfiRootIdentifier
import uniffi.xmtpv3.FfiRootIdentifierKind

interface SigningKey {
    val identity: SignerIdentity

    // The chainId of the Smart Contract Wallet value should be null if not SCW
    var chainId: Long?
        get() = null
        set(_) {}

    // Default blockNumber value set to null
    var blockNumber: Long?
        get() = null
        set(_) {}

    suspend fun sign(data: ByteArray): SignatureOuterClass.Signature? {
        throw NotImplementedError("sign(ByteArray) is not implemented.")
    }

    suspend fun sign(message: String): SignatureOuterClass.Signature? {
        throw NotImplementedError("sign(String) is not implemented.")
    }

    suspend fun signSCW(message: String): ByteArray {
        throw NotImplementedError("signSCW(String) is not implemented.")
    }
}

enum class SignerType {
    SCW, // Smart Contract Wallet
    EOA, // Externally Owned Account *Default
    PASSKEY
}

class SignerIdentity(private val ffiRootIdentifier: FfiRootIdentifier, val type: SignerType) {

    constructor(type: SignerType, identifier: String, relyingPartner: String? = null) :
            this(
                FfiRootIdentifier(identifier, type.toFfiRootIdentifierKind(), relyingPartner),
                type
            )

    val identifier: String
        get() = ffiRootIdentifier.identifier

    val relyingPartner: String?
        get() = ffiRootIdentifier.relyingPartner
}

private fun SignerType.toFfiRootIdentifierKind(): FfiRootIdentifierKind {
    return when (this) {
        SignerType.SCW -> FfiRootIdentifierKind.ETHEREUM
        SignerType.EOA -> FfiRootIdentifierKind.ETHEREUM
        SignerType.PASSKEY -> FfiRootIdentifierKind.PASSKEY
    }
}

fun SignerType.toIdentityKind(): IdentityKind {
    return when (this) {
        SignerType.SCW -> IdentityKind.ETHEREUM
        SignerType.EOA -> IdentityKind.ETHEREUM
        SignerType.PASSKEY -> IdentityKind.PASSKEY
    }
}

fun SignerIdentity.toFfiRootIdentifier(): FfiRootIdentifier {
    return FfiRootIdentifier(
        identifier = this.identifier,
        identifierKind = this.type.toFfiRootIdentifierKind(),
        relyingPartner = this.relyingPartner
    )
}

