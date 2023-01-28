package org.xmtp.android.library

public enum class EncodedContentCompression {
    deflate,
    gzip


    fun compress(content: ByteArray) : ByteArray {
        when (this) {
            deflate -> {
                // 78 9C - Default Compression according to https://www.ietf.org/rfc/rfc1950.txt
                val header = Data(listOf(0x78, 0x9C))
                // Perform rfc1951 compression
                val compressed = (content as NSData).compressed(using = .zlib) as Data
                // Needed for rfc1950 compliance
                val checksum = adler32(content)
                return header + compressed + checksum
            }
            gzip -> return content.gzipped()
        }
    }

    fun decompress(content: ByteArray) : ByteArray {
        when (this) {
            deflate -> {
                // Swift uses https://www.ietf.org/rfc/rfc1951.txt while JS uses https://www.ietf.org/rfc/rfc1950.txt
                // They're basically the same except the JS version has a two byte header that we can just get rid of
                // and a four byte checksum at the end that seems to be ignored here.
                val data = NSData(data = content[2...])
                val inflated = data.decompressed(using = .zlib)
                return inflated as ByteArray
            }
            gzip -> return content.gunzipped()
        }
    }

    private fun adler32(data: ByteArray) : ByteArray {
        val prime = UInt32(65521)
        var s1 = UInt32(1 & 0xFFFF)
        var s2 = UInt32((1 >> 16) & 0xFFFF)
        data.forEach {
            s1 += UInt32(it)
            if (s1 >= prime) {
                s1 = s1 % prime
            }
            s2 += s1
            if (s2 >= prime) {
                s2 = s2 % prime
            }
        }
        var result = ((s2 << 16) | s1).bigEndian
        return Data(bytes = &result, count = MemoryLayout<UInt32>.size)
    }
}
