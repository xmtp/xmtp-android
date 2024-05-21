package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress
import uniffi.xmtpv3.GroupPermissions

@RunWith(AndroidJUnit4::class)
class GroupPermissionsTest {
    private lateinit var alixWallet: PrivateKeyBuilder
    private lateinit var boWallet: PrivateKeyBuilder
    private lateinit var alix: PrivateKey
    private lateinit var alixClient: Client
    private lateinit var bo: PrivateKey
    private lateinit var boClient: Client
    private lateinit var caroWallet: PrivateKeyBuilder
    private lateinit var caro: PrivateKey
    private lateinit var caroClient: Client
    private lateinit var fixtures: Fixtures

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fixtures =
            fixtures(
                clientOptions = ClientOptions(
                    ClientOptions.Api(XMTPEnvironment.LOCAL, false),
                    enableAlphaMls = true,
                    appContext = context
                )
            )
        alixWallet = fixtures.aliceAccount
        alix = fixtures.alice
        boWallet = fixtures.bobAccount
        bo = fixtures.bob
        caroWallet = fixtures.caroAccount
        caro = fixtures.caro

        alixClient = fixtures.aliceClient
        boClient = fixtures.bobClient
        caroClient = fixtures.caroClient
    }

    @Test
    fun testGroupCreatedWithCorrectAdminList() {
        val boGroup = runBlocking { boClient.conversations.newGroup(listOf(alix.walletAddress)) }
        runBlocking { alixClient.conversations.syncGroups() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        assert(boGroup.isCreator())
        assert(boGroup.isAdmin(bo.walletAddress))
        assert(boGroup.isSuperAdmin(bo.walletAddress))
        assert(!alixGroup.isCreator())
        assert(!alixGroup.isAdmin(alixClient.address))
        assert(!alixGroup.isSuperAdmin(alixClient.address))

        val adminList = runBlocking {
            boGroup.listAdmins()
        }
        val superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }
        assert(adminList.size == 1)
        assert(adminList.contains(bo.walletAddress.lowercase()))
        assert(superAdminList.size == 1)
        assert(superAdminList.contains(bo.walletAddress.lowercase()))
    }

    @Test
    fun testGroupCanUpdateAdminList() {
        val boGroup = runBlocking { boClient.conversations.newGroup(listOf(alix.walletAddress, caro.walletAddress), GroupPermissions.ADMINS_ONLY) }
        runBlocking { alixClient.conversations.syncGroups() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        assert(boGroup.isCreator())
        assert(boGroup.isAdmin(bo.walletAddress))
        assert(boGroup.isSuperAdmin(bo.walletAddress))
        assert(!alixGroup.isCreator())
        assert(!alixGroup.isAdmin(alix.walletAddress))
        assert(!alixGroup.isSuperAdmin(alix.walletAddress))

        var adminList = runBlocking {
            boGroup.listAdmins()
        }
        var superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }
        assert(adminList.size == 1)
        assert(adminList.contains(bo.walletAddress.lowercase()))
        assert(superAdminList.size == 1)
        assert(superAdminList.contains(bo.walletAddress.lowercase()))

        // Verify that alix can NOT  update group name
        assert(boGroup.name == "New Group")
        val exception = assertThrows(uniffi.xmtpv3.GenericException.GroupException::class.java) {
            runBlocking {
                alixGroup.updateGroupName("Alix group name")
            }
        }
        assertEquals(exception.message, "Group error: Errors occurred during sync [CommitValidation(InsufficientPermissions)]")
        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }
        assert(boGroup.name == "New Group")
        assert(alixGroup.name == "New Group")

        runBlocking {
            boGroup.addAdmin(alix.walletAddress.lowercase())
            boGroup.sync()
            alixGroup.sync()
        }

        adminList = runBlocking {
            boGroup.listAdmins()
        }
        superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }

        assert(alixGroup.isAdmin(alix.walletAddress.lowercase()))
        assert(adminList.size == 2)
        assert(adminList.contains(alix.walletAddress.lowercase()))
        assert(superAdminList.size == 1)

        // Verify that alix can now update group name
        runBlocking {
            boGroup.sync()
            alixGroup.sync()
            alixGroup.updateGroupName("Alix group name")
            alixGroup.sync()
            boGroup.sync()
        }
        assert(boGroup.name == "Alix group name")
        assert(alixGroup.name == "Alix group name")

        runBlocking {
            boGroup.removeAdmin(alix.walletAddress.lowercase())
            boGroup.sync()
            alixGroup.sync()
        }

        adminList = runBlocking {
            boGroup.listAdmins()
        }
        superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }

        assert(!alixGroup.isAdmin(alix.walletAddress.lowercase()))
        assert(adminList.size == 1)
        assert(!adminList.contains(alix.walletAddress.lowercase()))
        assert(superAdminList.size == 1)

        // Verify that alix can NOT  update group name
        val exception2 = assertThrows(uniffi.xmtpv3.GenericException.GroupException::class.java) {
            runBlocking {
                alixGroup.updateGroupName("Alix group name 2")
            }
        }
        assertEquals(exception.message, "Group error: Errors occurred during sync [CommitValidation(InsufficientPermissions)]")
    }
}
