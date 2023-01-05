package org.xmtp.android.library.extensions

import java.util.*

val Date.millisecondsSinceEpoch: Double
    get() = (System.currentTimeMillis() * 1000).toDouble()
