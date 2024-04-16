import uniffi.xmtpv3.RustBuffer

class RustBufferPool(private val bufferSize: Int, private val poolSize: Int) {
    private val buffers = Array(poolSize) { RustBuffer.alloc(bufferSize) }
    private val available = BooleanArray(poolSize) { true }

    @Synchronized
    fun borrowBuffer(): RustBuffer.ByValue {
        val index = available.indexOfFirst { it }
        if (index == -1) {
            throw RuntimeException("No available buffers in the pool")
        }
        available[index] = false
        return buffers[index]
    }

    @Synchronized
    fun returnBuffer(buffer: RustBuffer.ByValue) {
        val index = buffers.indexOf(buffer)
        if (index == -1) {
            throw IllegalArgumentException("Buffer not from this pool")
        }
        available[index] = true
    }
}
