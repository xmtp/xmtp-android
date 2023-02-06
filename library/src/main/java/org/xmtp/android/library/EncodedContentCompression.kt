package org.xmtp.android.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater


enum class EncodedContentCompression {
    DEFLATE,
    GZIP;

    fun compress(content: ByteArray): ByteArray? {
        when (this) {
            DEFLATE -> {
                val deflater = Deflater(1, true)
                deflater.setInput(content)
                deflater.finish()

                val outputStream = ByteArrayOutputStream(content.size)

                try {
                    val bytesCompressed = ByteArray(Short.MAX_VALUE.toInt())
                    val numberOfBytesAfterCompression = deflater.deflate(bytesCompressed)
                    val returnValues = ByteArray(numberOfBytesAfterCompression)
                    System.arraycopy(
                        bytesCompressed,
                        0,
                        returnValues,
                        0,
                        numberOfBytesAfterCompression
                    )
                    return returnValues
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    deflater.end()
                    outputStream.close()
                }
            }
            GZIP -> {
                val blockcopy: ByteArray = ByteBuffer
                    .allocate(4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(content.size)
                    .array()
                val os = ByteArrayOutputStream(content.size)
                val gos = GZIPOutputStream(os)
                gos.write(content)
                gos.close()
                os.close()
                val compressed = ByteArray(4 + os.toByteArray().size)
                System.arraycopy(blockcopy, 0, compressed, 0, 4)
                System.arraycopy(
                    os.toByteArray(), 0, compressed, 4,
                    os.toByteArray().size
                )
                return compressed
            }
        }
        return null
    }

    fun decompress(content: ByteArray): ByteArray? {
        when (this) {
            DEFLATE -> {
                try {
                    val inflater = Inflater(true)
                    val outputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    inflater.setInput(content)

                    while (!inflater.finished()) {
                        val count = inflater.inflate(buffer)
                        outputStream.write(buffer, 0, count)
                    }
                    inflater.end()
                    outputStream.close()
                    return outputStream.toByteArray()

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            GZIP -> {
                val gzipInputStream = GZIPInputStream(
                    ByteArrayInputStream(
                        content, 4,
                        content.size - 4
                    )
                )
                val baos = ByteArrayOutputStream()
                var value = 0
                while (value != -1) {
                    value = gzipInputStream.read()
                    if (value != -1) {
                        baos.write(value)
                    }
                }
                gzipInputStream.close()
                baos.close()
                return baos.toByteArray()
            }
        }
        return null
    }
}
