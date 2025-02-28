package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiPublicIdentifier
import uniffi.xmtpv3.FfiPublicIdentifierKind

enum class IdentityKind {
    ETHEREUM, PASSKEY
}

class Identity(private val ffiIdentifier: FfiPublicIdentifier) {

    constructor(kind: IdentityKind, identifier: String) :
            this(FfiPublicIdentifier(identifier, kind.toFfiPublicIdentifierKind(), null))

    val kind: IdentityKind
        get() = when (ffiIdentifier.identifierKind) {
            FfiPublicIdentifierKind.ETHEREUM -> IdentityKind.ETHEREUM
            FfiPublicIdentifierKind.PASSKEY -> IdentityKind.PASSKEY
        }

    val identifier: String
        get() = ffiIdentifier.identifier

    val relyingPartner: String?
        get() = ffiIdentifier.relyingPartner
}

private fun IdentityKind.toFfiPublicIdentifierKind(): FfiPublicIdentifierKind {
    return when (this) {
        IdentityKind.ETHEREUM -> FfiPublicIdentifierKind.ETHEREUM
        IdentityKind.PASSKEY -> FfiPublicIdentifierKind.PASSKEY
    }
}


fun Identity.toFfiPublicIdentifier(): FfiPublicIdentifier {
    return FfiPublicIdentifier(
        identifier = this.identifier,
        identifierKind = this.kind.toFfiPublicIdentifierKind(),
        relyingPartner = this.relyingPartner
    )
}
