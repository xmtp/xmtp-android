package org.xmtp.android.library.frames

class FramesApiError(message: String, val status: Int) : Exception(message)

class InvalidArgumentsError : Exception()
