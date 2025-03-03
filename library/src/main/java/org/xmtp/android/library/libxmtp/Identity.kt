package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiPublicIdentifier
import uniffi.xmtpv3.FfiPublicIdentifierKind

enum class IdentityKind {
    ETHEREUM, PASSKEY
}

class Identity(val ffiPublicIdentifier: FfiPublicIdentifier) {
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
            )

    val kind: IdentityKind
        get() = ffiPublicIdentifier.identifierKind.toIdentityKind()

    val identifier: String
        get() = ffiPublicIdentifier.identifier

    val relyingPartner: String?
        get() = ffiPublicIdentifier.relyingPartner
}

fun IdentityKind.toFfiPublicIdentifierKind(): FfiPublicIdentifierKind {
    return when (this) {
        IdentityKind.ETHEREUM -> FfiPublicIdentifierKind.ETHEREUM
        IdentityKind.PASSKEY -> FfiPublicIdentifierKind.PASSKEY
    }
}

fun FfiPublicIdentifierKind.toIdentityKind(): IdentityKind {
    return when (this) {
        FfiPublicIdentifierKind.ETHEREUM -> IdentityKind.ETHEREUM
        FfiPublicIdentifierKind.PASSKEY -> IdentityKind.PASSKEY
    }
}
