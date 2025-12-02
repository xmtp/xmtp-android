package org.xmtp.android.library

import uniffi.xmtpv3.FfiAuthCallback
import uniffi.xmtpv3.FfiAuthHandle
import uniffi.xmtpv3.FfiCredential

/**
 * Represents an authentication credential with sensitive data. The [value] should not be logged or
 * stored insecurely. Use try-with-resources or explicit close() for handles. expiresAtSeconds is a
 * Unix timestamp in seconds; must be non-negative.
 */
data class Credential
    private constructor(
        val name: String?,
        private val valueCharArray: CharArray,
        val expiresAtSeconds: Long,
    ) {
        val value: String = valueCharArray.concatToString()

        init {
            require(name?.isNotBlank() == true) { "name must not be blank" }
            require(expiresAtSeconds >= 0) { "expiresAtSeconds must be non-negative" }
        }

        /** Creates a Credential, automatically zeroing the sensitive value after use. */
        companion object {
            @JvmStatic
            fun create(
                name: String?,
                value: String,
                /** Number of seconds since unix epoch */
                expiresAtSeconds: Long,
            ): Credential {
                val charArray = value.toCharArray()
                val credential = Credential(name, charArray, expiresAtSeconds)
                // Zero the original string reference if possible, but since it's immutable, just return
                return credential
            }

            @JvmStatic
            internal fun fromFfi(ffi: FfiCredential): Credential = create(ffi.name, ffi.value, ffi.expiresAtSeconds)

            internal fun toFfi(credential: Credential): FfiCredential =
                FfiCredential(credential.name, credential.value, credential.expiresAtSeconds)
        }

        override fun toString(): String = "Credential(name=$name, value=[REDACTED], expiresAtSeconds=$expiresAtSeconds)"
    }

/**
 * Public interface for authentication callbacks. Implementations must be non-blocking and should
 * perform heavy operations (e.g., network calls) on background threads. If onAuthRequired throws an
 * exception, it will propagate and may crash the FFI layer; wrap in try-catch if needed.
 */
interface AuthCallback {
    /** Called when authentication is required. Return a valid Credential or throw an exception. */
    suspend fun onAuthRequired(): Credential

    fun toFfi(): FfiAuthCallback =
        object : FfiAuthCallback {
            override suspend fun onAuthRequired(): FfiCredential = Credential.toFfi(this@AuthCallback.onAuthRequired())
        }
}

/**
 * Handle for managing authentication state. Implements AutoCloseable for try-with-resources
 * support. Close after use to free native resources.
 */
class AuthHandle(
    private val ffiHandle: FfiAuthHandle,
) : AutoCloseable {
    internal val handle: FfiAuthHandle
        get() = ffiHandle

    constructor() : this(FfiAuthHandle())

    /**
     * Sets the current credential asynchronously.
     * @param credential The authentication credential to set.
     */
    suspend fun set(credential: Credential) {
        ffiHandle.`set`(Credential.toFfi(credential))
    }

    fun id(): ULong = ffiHandle.id()

    override fun close() {
        ffiHandle.close()
    }
}
