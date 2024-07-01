package uniffi.xmtpv3.org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiPermissionPolicy
import uniffi.xmtpv3.FfiPermissionPolicySet
enum class PermissionOption {
    Allow,
    Deny,
    Admin,
    SuperAdmin,
    Unknown;
    companion object {
        fun toFfiPermissionPolicy(option: PermissionOption): FfiPermissionPolicy {
            return when (option) {
                Allow -> FfiPermissionPolicy.ALLOW
                Deny -> FfiPermissionPolicy.DENY
                Admin -> FfiPermissionPolicy.ADMIN
                SuperAdmin -> FfiPermissionPolicy.SUPER_ADMIN
                Unknown -> FfiPermissionPolicy.OTHER
            }
        }
        fun fromFfiPermissionPolicy(ffiPolicy: FfiPermissionPolicy): PermissionOption {
            return when (ffiPolicy) {
                FfiPermissionPolicy.ALLOW -> Allow
                FfiPermissionPolicy.DENY -> Deny
                FfiPermissionPolicy.ADMIN -> Admin
                FfiPermissionPolicy.SUPER_ADMIN -> SuperAdmin
                FfiPermissionPolicy.DOES_NOT_EXIST -> Unknown
                FfiPermissionPolicy.OTHER -> Unknown
            }
        }
    }
}

class PermissionPolicySet(private val ffiPermissionPolicySet: FfiPermissionPolicySet) {
    val addMemberPolicy: PermissionOption
        get() = PermissionOption.fromFfiPermissionPolicy(ffiPermissionPolicySet.addMemberPolicy)
    val removeMemberPolicy: PermissionOption
        get() = PermissionOption.fromFfiPermissionPolicy(ffiPermissionPolicySet.removeMemberPolicy)
    val addAdminPolicy: PermissionOption
        get() = PermissionOption.fromFfiPermissionPolicy(ffiPermissionPolicySet.addAdminPolicy)
    val removeAdminPolicy: PermissionOption
        get() = PermissionOption.fromFfiPermissionPolicy(ffiPermissionPolicySet.removeAdminPolicy)
    val updateGroupNamePolicy: PermissionOption
        get() = PermissionOption.fromFfiPermissionPolicy(ffiPermissionPolicySet.updateGroupNamePolicy)
    val updateGroupDescriptionPolicy: PermissionOption
        get() = PermissionOption.fromFfiPermissionPolicy(ffiPermissionPolicySet.updateGroupDescriptionPolicy)
    val updateGroupImagePolicy: PermissionOption
        get() = PermissionOption.fromFfiPermissionPolicy(ffiPermissionPolicySet.updateGroupImageUrlSquarePolicy)
}
