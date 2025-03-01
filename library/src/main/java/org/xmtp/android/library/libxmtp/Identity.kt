package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiPublicIdentifier
import uniffi.xmtpv3.FfiPublicIdentifierKind
import uniffi.xmtpv3.FfiRootIdentifier
import uniffi.xmtpv3.FfiRootIdentifierKind

enum class IdentityKind {
    ETHEREUM, PASSKEY
}

class Identity constructor(
    private val ffiPublicIdentifier: FfiPublicIdentifier?,
    private val ffiRootIdentifier: FfiRootIdentifier?,
) {
    constructor(
        kind: IdentityKind,
        identifier: String,
        relyingPartner: String? = null,
    ) :
            this(
                ffiPublicIdentifier = FfiPublicIdentifier(
                    identifier,
                    kind.toFfiPublicIdentifierKind(),
                    relyingPartner
                ),
                ffiRootIdentifier = FfiRootIdentifier(
                    identifier,
                    kind.toFfiRootIdentifierKind(),
                    relyingPartner
                )
            )

    // Identity Kind (Derived from whichever FFI object is present)
    val kind: IdentityKind
        get() = ffiPublicIdentifier?.identifierKind?.toIdentityKind()
            ?: ffiRootIdentifier?.identifierKind?.toIdentityKind()
            ?: throw IllegalStateException("Identity must have a valid kind")

    // Identifier (Either Public or Root)
    val identifier: String
        get() = ffiPublicIdentifier?.identifier ?: ffiRootIdentifier?.identifier
        ?: throw IllegalStateException("Identity must have an identifier")

    // Relying Partner (If Applicable)
    val relyingPartner: String?
        get() = ffiPublicIdentifier?.relyingPartner ?: ffiRootIdentifier?.relyingPartner

    // Convert to FfiPublicIdentifier (if it's a public identity)
    fun toFfiPublicIdentifier(): FfiPublicIdentifier? {
        return ffiPublicIdentifier
    }

    // Convert to FfiRootIdentifier (if it's a root identity)
    fun toFfiRootIdentifier(): FfiRootIdentifier? {
        return ffiRootIdentifier
    }
}


// Convert IdentityKind to FfiPublicIdentifierKind
fun IdentityKind.toFfiPublicIdentifierKind(): FfiPublicIdentifierKind {
    return when (this) {
        IdentityKind.ETHEREUM -> FfiPublicIdentifierKind.ETHEREUM
        IdentityKind.PASSKEY -> FfiPublicIdentifierKind.PASSKEY
    }
}

// Convert IdentityKind to FfiRootIdentifierKind
fun IdentityKind.toFfiRootIdentifierKind(): FfiRootIdentifierKind {
    return when (this) {
        IdentityKind.ETHEREUM -> FfiRootIdentifierKind.ETHEREUM
        IdentityKind.PASSKEY -> FfiRootIdentifierKind.PASSKEY
    }
}

// Convert FfiPublicIdentifierKind to IdentityKind
fun FfiPublicIdentifierKind.toIdentityKind(): IdentityKind {
    return when (this) {
        FfiPublicIdentifierKind.ETHEREUM -> IdentityKind.ETHEREUM
        FfiPublicIdentifierKind.PASSKEY -> IdentityKind.PASSKEY
    }
}

// Convert FfiRootIdentifierKind to IdentityKind
fun FfiRootIdentifierKind.toIdentityKind(): IdentityKind {
    return when (this) {
        FfiRootIdentifierKind.ETHEREUM -> IdentityKind.ETHEREUM
        FfiRootIdentifierKind.PASSKEY -> IdentityKind.PASSKEY
    }
}
