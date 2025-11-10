package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Auth {
    @Test
    fun testAuthCallback() =
        runBlocking {
            val callback =
                object : AuthCallback {
                    override suspend fun onAuthRequired(): Credential = Credential.create("cbName", "cbValue", 456L)
                }
            val credential = callback.onAuthRequired()
            Assert.assertEquals("cbName", credential.name)
            Assert.assertEquals("cbValue", credential.value)
            Assert.assertEquals(456L, credential.expiresAtSeconds)
            val api =
                ClientOptions.Api(
                    env = XMTPEnvironment.DEV,
                    gatewayHost = "test.com",
                    authCallback = callback,
                )
            val client = Client.connectToApiBackend(api)
            Assert.assertNotNull(client)
        }

    @Test
    fun testAuthHandle() =
        runBlocking {
            val handle = AuthHandle()
            val credential = Credential.create("handleName", "handleValue", 789L)
            handle.set(credential)
            val api =
                ClientOptions.Api(
                    env = XMTPEnvironment.DEV,
                    gatewayHost = "test.com",
                    authHandle = handle,
                )
            val client = Client.connectToApiBackend(api)
            Assert.assertNotNull(client)
            handle.close()
        }
}
