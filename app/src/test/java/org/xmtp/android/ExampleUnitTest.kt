package org.xmtp.android

import org.junit.Test
import org.junit.Assert.*
import org.xmtp.proto.message.api.v1.MessageApiOuterClass.SortDirection

class ExampleUnitTest {
    @Test fun testTypesAreAvailable() {
        assertEquals(1, SortDirection.SORT_DIRECTION_ASCENDING_VALUE)
    }
}