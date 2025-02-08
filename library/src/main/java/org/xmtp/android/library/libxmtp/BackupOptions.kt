package org.xmtp.android.library.libxmtp

import uniffi.xmtpv3.FfiBackupElementSelection
import uniffi.xmtpv3.FfiBackupMetadata
import uniffi.xmtpv3.FfiBackupOptions

data class BackupOptions(
    val startNs: Long? = null,
    val endNs: Long? = null,
    val backupElements: List<BackupElement> = listOf(BackupElement.MESSAGES, BackupElement.CONSENT),
)

fun BackupOptions.toFfi(): FfiBackupOptions {
    return FfiBackupOptions(
        startNs = this.startNs,
        endNs = this.endNs,
        elements = this.backupElements.map { it.toFfi() }
    )
}

enum class BackupElement {
    MESSAGES,
    CONSENT;

    fun toFfi(): FfiBackupElementSelection {
        return when (this) {
            MESSAGES -> FfiBackupElementSelection.MESSAGES
            CONSENT -> FfiBackupElementSelection.CONSENT
        }
    }

    companion object {
        fun fromFfi(ffiElement: FfiBackupElementSelection): BackupElement {
            return when (ffiElement) {
                FfiBackupElementSelection.MESSAGES -> MESSAGES
                FfiBackupElementSelection.CONSENT -> CONSENT
            }
        }
    }
}

data class BackupMetadata(private val ffiBackupMetadata: FfiBackupMetadata) {
    val backupVersion: UShort get() = ffiBackupMetadata.backupVersion
    val elements: List<BackupElement>
        get() = ffiBackupMetadata.elements.map {
            BackupElement.fromFfi(
                it
            )
        }
    val exportedAtNs: Long get() = ffiBackupMetadata.exportedAtNs
    val startNs: Long? get() = ffiBackupMetadata.startNs
    val endNs: Long? get() = ffiBackupMetadata.endNs
}
