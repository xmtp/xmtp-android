package org.xmtp.android.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmtp.android.library.libxmtp.GroupPermissionPreconfiguration
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PermissionLevel
import org.xmtp.android.library.libxmtp.PermissionOption
import org.xmtp.android.library.libxmtp.PermissionPolicySet
import org.xmtp.android.library.libxmtp.PublicIdentity
import org.xmtp.android.library.messages.PrivateKey
import org.xmtp.android.library.messages.PrivateKeyBuilder
import org.xmtp.android.library.messages.walletAddress
import uniffi.xmtpv3.GenericException
import java.security.SecureRandom

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
        val key = SecureRandom().generateSeed(32)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fixtures = fixtures()
        alixWallet = fixtures.alixAccount
        alix = fixtures.alix
        boWallet = fixtures.boAccount
        bo = fixtures.bo
        caroWallet = fixtures.caroAccount
        caro = fixtures.caro

        alixClient = fixtures.alixClient
        boClient = fixtures.boClient
        caroClient = fixtures.caroClient
    }

    @Test
    fun testGroupCreatedWithCorrectAdminList() {
        val boGroup = runBlocking { boClient.conversations.newGroup(listOf(alixClient.inboxId)) }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        assert(!boGroup.isAdmin(boClient.inboxId))
        assert(boGroup.isSuperAdmin(boClient.inboxId))
        assert(!runBlocking { alixGroup.isCreator() })
        assert(!alixGroup.isAdmin(alixClient.inboxId))
        assert(!alixGroup.isSuperAdmin(alixClient.inboxId))

        val adminList = runBlocking {
            boGroup.listAdmins()
        }
        val superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }
        assert(adminList.isEmpty())
        assert(!adminList.contains(boClient.inboxId))
        assert(superAdminList.size == 1)
        assert(superAdminList.contains(boClient.inboxId))
    }

    @Test
    fun testGroupCanUpdateAdminList() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                ),
                GroupPermissionPreconfiguration.ADMIN_ONLY
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        assert(!boGroup.isAdmin(boClient.inboxId))
        assert(boGroup.isSuperAdmin(boClient.inboxId))
        assert(!runBlocking { alixGroup.isCreator() })
        assert(!alixGroup.isAdmin(alixClient.inboxId))
        assert(!alixGroup.isSuperAdmin(alixClient.inboxId))

        var adminList = runBlocking {
            boGroup.listAdmins()
        }
        var superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }
        assert(adminList.size == 0)
        assert(!adminList.contains(boClient.inboxId))
        assert(superAdminList.size == 1)
        assert(superAdminList.contains(boClient.inboxId))

        // Verify that alix can NOT  update group name
        assert(boGroup.name == "")
        val exception = assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.updateName("Alix group name")
            }
        }
        assertEquals(exception.message, "Permission denied: Unable to update group name")
        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }
        assert(boGroup.name == "")
        assert(alixGroup.name == "")

        runBlocking {
            boGroup.addAdmin(alixClient.inboxId)
            boGroup.sync()
            alixGroup.sync()
        }

        adminList = runBlocking {
            boGroup.listAdmins()
        }
        superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }

        assert(alixGroup.isAdmin(alixClient.inboxId))
        assert(adminList.size == 1)
        assert(adminList.contains(alixClient.inboxId))
        assert(superAdminList.size == 1)

        // Verify that alix can now update group name
        runBlocking {
            boGroup.sync()
            alixGroup.sync()
            alixGroup.updateName("Alix group name")
            alixGroup.sync()
            boGroup.sync()
        }
        assert(boGroup.name == "Alix group name")
        assert(alixGroup.name == "Alix group name")

        runBlocking {
            boGroup.removeAdmin(alixClient.inboxId)
            boGroup.sync()
            alixGroup.sync()
        }

        adminList = runBlocking {
            boGroup.listAdmins()
        }
        superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }

        assert(!alixGroup.isAdmin(alixClient.inboxId))
        assert(adminList.size == 0)
        assert(!adminList.contains(alixClient.inboxId))
        assert(superAdminList.size == 1)

        // Verify that alix can NOT  update group name
        val exception2 = assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.updateName("Alix group name 2")
            }
        }
        assertEquals(exception.message, "Permission denied: Unable to update group name")
    }

    @Test
    fun testGroupCanUpdateSuperAdminList() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                ),
                GroupPermissionPreconfiguration.ADMIN_ONLY
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        assert(boGroup.isSuperAdmin(boClient.inboxId))
        assert(!alixGroup.isSuperAdmin(alixClient.inboxId))

        // Attempt to remove bo as a super admin by alix should fail since she is not a super admin
        val exception = assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.removeSuperAdmin(boClient.inboxId)
            }
        }
        assertEquals(exception.message, "Permission denied: Unable to remove super admin")

        // Make alix a super admin
        runBlocking {
            boGroup.addSuperAdmin(alixClient.inboxId)
            boGroup.sync()
            alixGroup.sync()
        }

        assert(alixGroup.isSuperAdmin(alixClient.inboxId))

        // Now alix should be able to remove bo as a super admin
        runBlocking {
            alixGroup.removeSuperAdmin(boClient.inboxId)
            alixGroup.sync()
            boGroup.sync()
        }

        val superAdminList = runBlocking {
            boGroup.listSuperAdmins()
        }

        assert(!superAdminList.contains(boClient.inboxId))
        assert(superAdminList.contains(alixClient.inboxId))
    }

    @Test
    fun testGroupMembersAndPermissionLevel() {
        val group = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                ),
                GroupPermissionPreconfiguration.ADMIN_ONLY
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        // Initial checks for group members and their permissions
        var members = runBlocking { group.members() }
        var admins = members.filter { it.permissionLevel == PermissionLevel.ADMIN }
        var superAdmins = members.filter { it.permissionLevel == PermissionLevel.SUPER_ADMIN }
        var regularMembers = members.filter { it.permissionLevel == PermissionLevel.MEMBER }

        assert(admins.size == 0)
        assert(superAdmins.size == 1)
        assert(regularMembers.size == 2)

        // Add alix as an admin
        runBlocking {
            group.addAdmin(alixClient.inboxId)
            group.sync()
            alixGroup.sync()
        }

        members = runBlocking { group.members() }
        admins = members.filter { it.permissionLevel == PermissionLevel.ADMIN }
        superAdmins = members.filter { it.permissionLevel == PermissionLevel.SUPER_ADMIN }
        regularMembers = members.filter { it.permissionLevel == PermissionLevel.MEMBER }

        assert(admins.size == 1)
        assert(superAdmins.size == 1)
        assert(regularMembers.size == 1)

        // Add caro as a super admin
        runBlocking {
            group.addSuperAdmin(caroClient.inboxId)
            group.sync()
            alixGroup.sync()
        }

        members = runBlocking { group.members() }
        admins = members.filter { it.permissionLevel == PermissionLevel.ADMIN }
        superAdmins = members.filter { it.permissionLevel == PermissionLevel.SUPER_ADMIN }
        regularMembers = members.filter { it.permissionLevel == PermissionLevel.MEMBER }

        assert(admins.size == 1)
        assert(superAdmins.size == 2)
        assert(regularMembers.isEmpty())
    }

    @Test
    fun testCanCommitAfterInvalidPermissionsCommit() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                ),
                GroupPermissionPreconfiguration.ALL_MEMBERS
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        // Verify that alix can NOT  add an admin
        assert(boGroup.name == "")
        val exception = assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.addAdmin(alixClient.inboxId)
            }
        }
        assertEquals(exception.message, "Permission denied: Unable to add admin")
        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }

        // Verify that alix can update group name
        runBlocking {
            boGroup.sync()
            alixGroup.sync()
            alixGroup.updateName("Alix group name")
            alixGroup.sync()
            boGroup.sync()
        }
        assert(boGroup.name == "Alix group name")
        assert(alixGroup.name == "Alix group name")
    }

    @Test
    fun testCanUpdatePermissions() {
        val boGroup = runBlocking {
            boClient.conversations.newGroup(
                listOf(
                    alixClient.inboxId,
                    caroClient.inboxId
                ),
                GroupPermissionPreconfiguration.ADMIN_ONLY
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        // Verify that alix can NOT update group name
        assert(boGroup.name == "")
        val exception = assertThrows(XMTPException::class.java) {
            runBlocking {
                alixGroup.updateDescription("new group description")
            }
        }
        assertEquals(exception.message, "Permission denied: Unable to update group description")
        runBlocking {
            alixGroup.sync()
            boGroup.sync()
        }
        assertEquals(
            boGroup.permissionPolicySet().updateGroupDescriptionPolicy,
            PermissionOption.Admin
        )

        // Update group name permissions so Alix can update
        runBlocking {
            boGroup.updateDescriptionPermission(PermissionOption.Allow)
            boGroup.sync()
            alixGroup.sync()
        }
        assertEquals(
            boGroup.permissionPolicySet().updateGroupDescriptionPolicy,
            PermissionOption.Allow
        )

        // Verify that alix can now update group name
        runBlocking {
            alixGroup.updateDescription("Alix group description")
            alixGroup.sync()
            boGroup.sync()
        }
        assert(boGroup.description == "Alix group description")
        assert(alixGroup.description == "Alix group description")
    }

    @Test
    fun canCreateGroupWithCustomPermissions() {
        val permissionPolicySet = PermissionPolicySet(
            addMemberPolicy = PermissionOption.Admin,
            removeMemberPolicy = PermissionOption.Deny,
            addAdminPolicy = PermissionOption.Admin,
            removeAdminPolicy = PermissionOption.SuperAdmin,
            updateGroupNamePolicy = PermissionOption.Admin,
            updateGroupDescriptionPolicy = PermissionOption.Allow,
            updateGroupImagePolicy = PermissionOption.Admin,
            updateMessageDisappearingPolicy = PermissionOption.Admin,
        )
        val boGroup = runBlocking {
            boClient.conversations.newGroupCustomPermissions(
                inboxIds = listOf(alixClient.inboxId, caroClient.inboxId),
                permissionPolicySet = permissionPolicySet,
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        // Verify permission look correct
        val alixPermissionSet = alixGroup.permissionPolicySet()
        assert(alixPermissionSet.addMemberPolicy == PermissionOption.Admin)
        assert(alixPermissionSet.removeMemberPolicy == PermissionOption.Deny)
        assert(alixPermissionSet.addAdminPolicy == PermissionOption.Admin)
        assert(alixPermissionSet.removeAdminPolicy == PermissionOption.SuperAdmin)
        assert(alixPermissionSet.updateGroupNamePolicy == PermissionOption.Admin)
        assert(alixPermissionSet.updateGroupDescriptionPolicy == PermissionOption.Allow)
        assert(alixPermissionSet.updateGroupImagePolicy == PermissionOption.Admin)
    }

    @Test
    fun createGroupWithInvalidCustomPermissionsFails() {
        // Add/Remove Admin can not be allow
        val permissionPolicySetInvalid = PermissionPolicySet(
            addMemberPolicy = PermissionOption.Admin,
            removeMemberPolicy = PermissionOption.Deny,
            addAdminPolicy = PermissionOption.Admin,
            removeAdminPolicy = PermissionOption.Allow,
            updateGroupNamePolicy = PermissionOption.Admin,
            updateGroupDescriptionPolicy = PermissionOption.Allow,
            updateGroupImagePolicy = PermissionOption.Admin,
            updateMessageDisappearingPolicy = PermissionOption.Admin,
        )

        assertThrows(GenericException.GroupMutablePermissions::class.java) {
            val boGroup = runBlocking {
                boClient.conversations.newGroupCustomPermissions(
                    inboxIds = listOf(alixClient.inboxId, caroClient.inboxId),
                    permissionPolicySet = permissionPolicySetInvalid,
                )
            }
        }

        val permissionPolicySetValid = PermissionPolicySet(
            addMemberPolicy = PermissionOption.Admin,
            removeMemberPolicy = PermissionOption.Deny,
            addAdminPolicy = PermissionOption.Admin,
            removeAdminPolicy = PermissionOption.SuperAdmin,
            updateGroupNamePolicy = PermissionOption.Admin,
            updateGroupDescriptionPolicy = PermissionOption.Allow,
            updateGroupImagePolicy = PermissionOption.Admin,
            updateMessageDisappearingPolicy = PermissionOption.Allow,
        )

        // Valid custom policy works as expected
        runBlocking { alixClient.conversations.sync() }
        assert(runBlocking { alixClient.conversations.listGroups() }.isEmpty())

        val boGroup = runBlocking {
            boClient.conversations.newGroupCustomPermissions(
                inboxIds = listOf(alixClient.inboxId, caroClient.inboxId),
                permissionPolicySet = permissionPolicySetValid,
            )
        }
        runBlocking { alixClient.conversations.sync() }
        assert(runBlocking { alixClient.conversations.listGroups() }.size == 1)
    }

    @Test
    fun canCreateGroupWithInboxIdCustomPermissions() {
        val permissionPolicySet = PermissionPolicySet(
            addMemberPolicy = PermissionOption.Admin,
            removeMemberPolicy = PermissionOption.Deny,
            addAdminPolicy = PermissionOption.Admin,
            removeAdminPolicy = PermissionOption.SuperAdmin,
            updateGroupNamePolicy = PermissionOption.Admin,
            updateGroupDescriptionPolicy = PermissionOption.Allow,
            updateGroupImagePolicy = PermissionOption.Admin,
            updateMessageDisappearingPolicy = PermissionOption.Admin,
        )
        val boGroup = runBlocking {
            boClient.conversations.newGroupCustomPermissionsWithIdentities(
                identities = listOf(
                    PublicIdentity(IdentityKind.ETHEREUM, alix.walletAddress),
                    PublicIdentity(IdentityKind.ETHEREUM, caro.walletAddress)
                ),
                permissionPolicySet = permissionPolicySet,
            )
        }
        runBlocking { alixClient.conversations.sync() }
        val alixGroup = runBlocking { alixClient.conversations.listGroups().first() }

        // Verify permission look correct
        val alixPermissionSet = alixGroup.permissionPolicySet()
        assert(alixPermissionSet.addMemberPolicy == PermissionOption.Admin)
        assert(alixPermissionSet.removeMemberPolicy == PermissionOption.Deny)
        assert(alixPermissionSet.addAdminPolicy == PermissionOption.Admin)
        assert(alixPermissionSet.removeAdminPolicy == PermissionOption.SuperAdmin)
        assert(alixPermissionSet.updateGroupNamePolicy == PermissionOption.Admin)
        assert(alixPermissionSet.updateGroupDescriptionPolicy == PermissionOption.Allow)
        assert(alixPermissionSet.updateGroupImagePolicy == PermissionOption.Admin)
    }
}
