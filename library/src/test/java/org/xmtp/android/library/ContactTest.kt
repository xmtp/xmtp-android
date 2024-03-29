package org.xmtp.android.library

import com.google.protobuf.kotlin.toByteString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmtp.android.library.messages.ContactBundleBuilder
import org.xmtp.android.library.messages.Envelope
import org.xmtp.android.library.messages.identityAddress
import org.xmtp.android.library.messages.walletAddress

class ContactTest {

    @Test
    fun testParsingV2Bundle() {
        val ints = arrayOf(
            18, 181, 2, 10, 178, 2, 10, 150, 1, 10, 76, 8, 140, 241, 170, 138, 182, 48, 26, 67, 10,
            65, 4, 33, 132, 132, 43, 80, 179, 54, 132, 47, 151, 245, 23, 108, 148, 94, 190, 2, 33,
            232, 232, 185, 73, 64, 44, 47, 65, 168, 25, 56, 252, 1, 58, 243, 20, 103, 8, 253, 118,
            10, 1, 108, 158, 125, 149, 128, 37, 28, 250, 204, 1, 66, 194, 61, 119, 197, 121, 158,
            210, 234, 92, 79, 181, 1, 150, 18, 70, 18, 68, 10, 64, 43, 154, 228, 249, 69, 206, 218,
            165, 35, 55, 141, 145, 183, 129, 104, 75, 106, 62, 28, 73, 69, 7, 170, 65, 66, 93, 11,
            184, 229, 204, 140, 101, 71, 74, 0, 227, 140, 89, 53, 35, 203, 180, 87, 102, 89, 176,
            57, 128, 165, 42, 214, 173, 199, 17, 159, 200, 254, 25, 80, 227, 20, 16, 189, 92, 16, 1,
            18, 150, 1, 10, 76, 8, 244, 246, 171, 138, 182, 48, 26, 67, 10, 65, 4, 104, 191, 167,
            212, 49, 159, 46, 123, 133, 52, 69, 73, 137, 157, 76, 63, 233, 223, 129, 64, 138, 86,
            91, 26, 191, 241, 109, 249, 216, 96, 226, 255, 103, 29, 192, 3, 181, 228, 63, 52, 101,
            88, 96, 141, 236, 194, 111, 16, 105, 88, 127, 215, 255, 63, 92, 135, 251, 14, 176, 85,
            65, 211, 88, 80, 18, 70, 10, 68, 10, 64, 252, 165, 96, 161, 187, 19, 203, 60, 89, 195,
            73, 176, 189, 203, 109, 113, 106, 39, 71, 116, 44, 101, 180, 16, 243, 70, 128, 58, 46,
            10, 55, 243, 43, 115, 21, 23, 153, 241, 208, 212, 162, 205, 197, 139, 2, 117, 1, 40,
            200, 252, 136, 148, 18, 125, 39, 175, 130, 113, 103, 83, 120, 60, 232, 109, 16, 1
        )
        val data =
            ints.foldIndexed(ByteArray(ints.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
        val envelope = Envelope.newBuilder().apply {
            message = data.toByteString()
        }.build()
        val contactBundle = ContactBundleBuilder.buildFromEnvelope(envelope)
        assert(!contactBundle.v1.hasKeyBundle())
        assert(contactBundle.v2.hasKeyBundle())
        assertEquals(contactBundle.walletAddress, "0x66942eC8b0A6d0cff51AEA9C7fd00494556E705F")
    }

    @Test
    fun testParsingV1Bundle() {
        val ints = arrayOf( // This is a serialized PublicKeyBundle (instead of a ContactBundle)
            10, 146, 1, 8, 236, 130, 192, 166, 148, 48, 18, 68, 10, 66, 10, 64, 70, 34, 101, 46, 39,
            87, 114, 210, 103, 135, 87, 49, 162, 200, 82, 177, 11, 4, 137, 31, 235, 91, 185, 46, 177,
            208, 228, 102, 44, 61, 40, 131, 109, 210, 93, 42, 44, 235, 177, 73, 72, 234, 18, 32, 230,
            61, 146, 58, 65, 78, 178, 163, 164, 241, 118, 167, 77, 240, 13, 100, 151, 70, 190, 15,
            26, 67, 10, 65, 4, 8, 71, 173, 223, 174, 185, 150, 4, 179, 111, 144, 35, 5, 210, 6, 60,
            21, 131, 135, 52, 37, 221, 72, 126, 21, 103, 208, 31, 182, 76, 187, 72, 66, 92, 193, 74,
            161, 45, 135, 204, 55, 10, 20, 119, 145, 136, 45, 194, 140, 164, 124, 47, 238, 17, 198,
            243, 102, 171, 67, 128, 164, 117, 7, 83
        )
        val data =
            ints.foldIndexed(ByteArray(ints.size)) { i, a, v -> a.apply { set(i, v.toByte()) } }
        val envelope = Envelope.newBuilder().apply {
            this.message = data.toByteString()
        }.build()
        val contactBundle = ContactBundleBuilder.buildFromEnvelope(envelope = envelope)
        assertEquals(contactBundle.walletAddress, "0x66942eC8b0A6d0cff51AEA9C7fd00494556E705F")
        assertEquals(contactBundle.identityAddress, "0xD320f1454e33ab9393c0cc596E6321d80e4b481e")
        assert(!contactBundle.v1.keyBundle.hasPreKey())
    }
}
