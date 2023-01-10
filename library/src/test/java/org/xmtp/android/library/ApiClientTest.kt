package org.xmtp.android.library

import org.junit.Test

class ApiClientTest {
    @Test fun testCanGetApiClient() {
        GRPCApiClient(environment = XMTPEnvironment.LOCAL)
    }
}