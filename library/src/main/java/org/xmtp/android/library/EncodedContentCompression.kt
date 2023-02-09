package org.xmtp.android.library

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.InflaterOutputStream

enum class EncodedContentCompression {
    DEFLATE,
    GZIP;

    fun compress(content: ByteArray): ByteArray? {
        return when (this) {
            DEFLATE -> {
                val bos = ByteArrayOutputStream()
                DeflaterOutputStream(bos).write(content)
                bos.toByteArray()
            }
            GZIP -> {
                val bos = ByteArrayOutputStream()
                GZIPOutputStream(bos).write(content)
                bos.toByteArray()
            }
        }
    }

    fun decompress(content: ByteArray): ByteArray? {
        return when (this) {
            DEFLATE -> {
                val bos = ByteArrayOutputStream()
                InflaterOutputStream(bos).write(content)
                bos.toByteArray()
            }
            GZIP -> {
                GZIPInputStream(content.inputStream()).readBytes()
            }
        }
    }
}
