package org.xmtp.android.library

import android.util.Base64
import com.google.crypto.tink.subtle.Base64.*
import org.xmtp.android.library.messages.*
import org.xmtp.proto.message.contents.PrivateKeyOuterClass

data class AuthorizedIdentity(
    var address: String,
    var authorized: PublicKey,
    var identity: PrivateKey
) {

    fun createAuthToken(): String {
        val publicKey = authorized.toBuilder()
        val authData = AuthDataFactory.create(walletAddress = address)
        val authDataBytes = authData.toByteArray()
        val privateKeyFactory = PrivateKeyFactory()
        privateKeyFactory.setPrivateKey(identity)
        val signature = privateKeyFactory.sign(Util.keccak256(authDataBytes))
        publicKey.signature = signature
        publicKey.build()
        val tokenBuilder = Token.newBuilder()
        tokenBuilder.identityKey = authorized
        tokenBuilder.authDataBytes = authData.toByteString()
        tokenBuilder.authDataSignature = signature
        val token = tokenBuilder.build().toByteArray()
        return encodeToString(token, Base64.DEFAULT)
    }

    val toBundle: PrivateKeyBundle
        get() {
            val bundleBuilder = PrivateKeyOuterClass.PrivateKeyBundle.newBuilder()
            bundleBuilder.v1Builder.identityKey = identity
            bundleBuilder.v1Builder.identityKeyBuilder.publicKey = authorized
            return bundleBuilder.build()
        }
}
