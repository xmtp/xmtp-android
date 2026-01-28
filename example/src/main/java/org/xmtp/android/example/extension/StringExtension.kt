package org.xmtp.android.example.extension

fun String.truncatedAddress(): String {
    // Need at least 10 characters to meaningfully truncate (show first 6 + "..." + last 4)
    if (length >= 10) {
        val start = 6
        val end = length - 4
        return replaceRange(start, end, "...")
    }
    return this
}
