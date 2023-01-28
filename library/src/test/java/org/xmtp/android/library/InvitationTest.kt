package org.xmtp.android.library

import org.junit.*
import org.junit.Assert.*
import org.xmtp.android.library.messages.InvitationV1
import org.xmtp.android.library.messages.PrivateKeyBundleV1
import org.xmtp.android.library.messages.SealedInvitation
import java.util.Date

class InvitationTest {

    @Test
    fun testGenerateSealedInvitation() {
        val aliceWallet = FakeWallet.generate()
        val bobWallet = FakeWallet.generate()
        val alice = PrivateKeyBundleV1.generate(wallet = aliceWallet)
        val bob = PrivateKeyBundleV1.generate(wallet = bobWallet)
        val invitation = InvitationV1.createRandom()
        val newInvitation = SealedInvitation.createV1(sender = alice.toV2(), recipient = bob.toV2().getPublicKeyBundle(), created = Date(), invitation = invitation)
        val deserialized = SealedInvitation(serializedData = newInvitation.serializedData())
        assert(!deserialized.v1.headerBytes.isEmpty(), "header bytes empty")
        assertEquals(newInvitation, deserialized)
        val header = newInvitation.v1.header
        // Ensure the headers haven't been mangled
        assertEquals(header.sender, alice.toV2().getPublicKeyBundle())
        assertEquals(header.recipient, bob.toV2().getPublicKeyBundle())
        // Ensure alice can decrypt the invitation
        val aliceInvite = newInvitation.v1.getInvitation(viewer = alice.toV2())
        assertEquals(aliceInvite.topic, invitation.topic)
        assertEquals(aliceInvite.aes256GcmHkdfSha256.keyMaterial, invitation.aes256GcmHkdfSha256.keyMaterial)
        // Ensure bob can decrypt the invitation
        val bobInvite = newInvitation.v1.getInvitation(viewer = bob.toV2())
        assertEquals(bobInvite.topic, invitation.topic)
        assertEquals(bobInvite.aes256GcmHkdfSha256.keyMaterial, invitation.aes256GcmHkdfSha256.keyMaterial)
    }
}