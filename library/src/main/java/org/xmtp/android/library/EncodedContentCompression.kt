package org.xmtp.android.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
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
                val out = ByteArrayOutputStream()
                val bis = ByteArrayInputStream(content)
                try {
                    val gzipInputStream = GZIPInputStream(bis)
                    val buffer = ByteArray(256)
                    var n: Int
                    while (gzipInputStream.read(buffer).also { n = it } >= 0) {
                        out.write(buffer, 0, n)
                    }
                } catch (e: IOException) {
                    println("gzip uncompress error.")
                } finally {
                    bis.close()
                    out.close()
                }
                return out.toByteArray()
            }
        }
    }
}
