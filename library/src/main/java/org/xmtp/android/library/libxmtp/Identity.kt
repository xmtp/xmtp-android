package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiIdentifier
import uniffi.xmtpv3.FfiIdentifierKind

enum class IdentityKind {
    ETHEREUM, PASSKEY
}

class Identity(val ffiIdentifier: FfiIdentifier) {
    constructor(
        kind: IdentityKind,
        identifier: String,
        relyingPartner: String? = null,
    ) :
            this(
                ffiIdentifier = FfiIdentifier(
                    identifier,
                    kind.toFfiPublicIdentifierKind(),
                    relyingPartner
                ),
            )

    val kind: IdentityKind
        get() = ffiIdentifier.identifierKind.toIdentityKind()

    val identifier: String
        get() = ffiIdentifier.identifier

    val relyingPartner: String?
        get() = ffiIdentifier.relyingPartner
}

fun IdentityKind.toFfiPublicIdentifierKind(): FfiIdentifierKind {
    return when (this) {
        IdentityKind.ETHEREUM -> FfiIdentifierKind.ETHEREUM
        IdentityKind.PASSKEY -> FfiIdentifierKind.PASSKEY
    }
}

fun FfiIdentifierKind.toIdentityKind(): IdentityKind {
    return when (this) {
        FfiIdentifierKind.ETHEREUM -> IdentityKind.ETHEREUM
        FfiIdentifierKind.PASSKEY -> IdentityKind.PASSKEY
    }
}
