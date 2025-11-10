package org.xmtp.android.library

import org.junit.Assert
import org.junit.Test
import uniffi.xmtpv3.FfiCredential

class AuthCredentialTest {
    @Test
    fun testCredentialCreation() {
        val credential = Credential.create("testName", "testValue", 1699999999L)
        Assert.assertEquals("testName", credential.name)
        Assert.assertEquals("testValue", credential.value)
        Assert.assertEquals(1699999999L, credential.expiresAtSeconds)
        Assert.assertTrue(credential.toString().contains("[REDACTED]"))
    }

    @Test
    fun testCredentialInvalidName() {
        try {
            Credential.create("", "value", 123L)
            Assert.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue(e.message!!.contains("blank"))
        }
    }

    @Test
    fun testCredentialInvalidExpires() {
        try {
            Credential.create("name", "value", -1L)
            Assert.fail("Should throw IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue(e.message!!.contains("non-negative"))
        }
    }

    @Test
    fun testCredentialFfiRoundtrip() {
        val ffi = FfiCredential("name", "value", 123L)
        val credential = Credential.fromFfi(ffi)
        val back = Credential.toFfi(credential)
        Assert.assertEquals("name", back.name)
        Assert.assertEquals("value", back.value)
        Assert.assertEquals(123L, back.expiresAtSeconds)
    }
}
