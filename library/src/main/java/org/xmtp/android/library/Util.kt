package org.xmtp.android.library

import org.web3j.crypto.Hash

class Util {
    companion object {
        fun keccak256(data: ByteArray): ByteArray =
            Hash.sha256(data)
    }
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

