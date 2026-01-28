package org.xmtp.android.example.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.xmtp.android.example.ui.components.MemberPermissionLevel
import org.xmtp.android.example.ui.components.MemberRowData
import org.xmtp.android.example.ui.screens.GroupDetails
import org.xmtp.android.example.ui.screens.GroupManagementScreen
import org.xmtp.android.example.ui.theme.XMTPTheme
import org.xmtp.android.library.libxmtp.PermissionLevel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class GroupManagementActivity : ComponentActivity() {
    private val viewModel: GroupManagementViewModel by viewModels()

    companion object {
        const val EXTRA_CONVERSATION_TOPIC = "EXTRA_CONVERSATION_TOPIC"
        private val ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$")

        fun intent(
            context: Context,
            topic: String,
        ): Intent =
            Intent(context, GroupManagementActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_TOPIC, topic)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.setConversationTopic(intent.extras?.getString(EXTRA_CONVERSATION_TOPIC))

        setContent {
            XMTPTheme {
                val uiState by viewModel.uiState.collectAsState()
                val scope = rememberCoroutineScope()

                var showAddMemberDialog by remember { mutableStateOf(false) }
                var addMemberAddress by remember { mutableStateOf("") }
                var showLeaveGroupDialog by remember { mutableStateOf(false) }
                var showConfirmationDialog by remember { mutableStateOf<ConfirmationDialogData?>(null) }

                // Load group info on first composition
                LaunchedEffect(Unit) {
                    viewModel.loadGroupInfo()
                }

                // Handle action state results
                fun handleActionState(state: GroupManagementViewModel.ActionState) {
                    when (state) {
                        is GroupManagementViewModel.ActionState.Success -> {
                            Toast.makeText(
                                this@GroupManagementActivity,
                                state.message,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        is GroupManagementViewModel.ActionState.Error -> {
                            Toast.makeText(
                                this@GroupManagementActivity,
                                state.message.ifBlank { "Error" },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        is GroupManagementViewModel.ActionState.Loading -> Unit
                    }
                }

                val isLoading = uiState is GroupManagementViewModel.UiState.Loading

                val groupDetails = when (val state = uiState) {
                    is GroupManagementViewModel.UiState.Success -> {
                        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        val createdAtString = state.createdAt?.let { dateFormat.format(it) } ?: ""
                        GroupDetails(
                            groupId = state.groupId.truncatedAddress(),
                            groupName = state.groupId,
                            createdAt = createdAtString,
                            memberCount = state.members.size,
                            currentUserRole = state.currentUserRole.toMemberPermissionLevel(),
                        )
                    }
                    else -> GroupDetails(
                        groupId = "",
                        groupName = "",
                        createdAt = "",
                        memberCount = 0,
                        currentUserRole = MemberPermissionLevel.MEMBER,
                    )
                }

                val members = when (val state = uiState) {
                    is GroupManagementViewModel.UiState.Success -> {
                        state.members.map { member ->
                            MemberRowData(
                                inboxId = member.member.inboxId,
                                displayAddress = member.displayAddress,
                                permissionLevel = member.member.permissionLevel.toMemberPermissionLevel(),
                                isCurrentUser = member.isCurrentUser,
                            )
                        }
                    }
                    else -> emptyList()
                }

                // Handle error state
                LaunchedEffect(uiState) {
                    if (uiState is GroupManagementViewModel.UiState.Error) {
                        Toast.makeText(
                            this@GroupManagementActivity,
                            (uiState as GroupManagementViewModel.UiState.Error).message,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                GroupManagementScreen(
                    groupDetails = groupDetails,
                    members = members,
                    isLoading = isLoading,
                    onBackClick = { finish() },
                    onAddMemberClick = { showAddMemberDialog = true },
                    onMemberClick = { inboxId ->
                        // Find member's display address
                        val member = members.find { it.inboxId == inboxId }
                        member?.displayAddress?.let { address ->
                            startActivity(
                                UserProfileActivity.intent(
                                    this@GroupManagementActivity,
                                    address,
                                    inboxId,
                                ),
                            )
                        }
                    },
                    onPromoteToAdmin = { inboxId ->
                        val member = members.find { m -> m.inboxId == inboxId }
                        val displayName = member?.displayAddress?.truncatedAddress()
                            ?: inboxId.truncatedAddress()
                        showConfirmationDialog = ConfirmationDialogData(
                            title = "Promote to Admin",
                            message = "Are you sure you want to promote $displayName to admin?",
                            confirmText = "Promote",
                            onConfirm = {
                                scope.launch {
                                    viewModel.promoteToAdmin(inboxId).collect { state ->
                                        handleActionState(state)
                                    }
                                }
                            },
                        )
                    },
                    onDemoteFromAdmin = { inboxId ->
                        val member = members.find { m -> m.inboxId == inboxId }
                        val displayName = member?.displayAddress?.truncatedAddress()
                            ?: inboxId.truncatedAddress()
                        showConfirmationDialog = ConfirmationDialogData(
                            title = "Remove Admin",
                            message = "Are you sure you want to remove admin privileges from $displayName?",
                            confirmText = "Remove",
                            onConfirm = {
                                scope.launch {
                                    viewModel.demoteFromAdmin(inboxId).collect { state ->
                                        handleActionState(state)
                                    }
                                }
                            },
                        )
                    },
                    onRemoveMember = { inboxId ->
                        val member = members.find { m -> m.inboxId == inboxId }
                        val displayName = member?.displayAddress?.truncatedAddress()
                            ?: inboxId.truncatedAddress()
                        showConfirmationDialog = ConfirmationDialogData(
                            title = "Remove Member",
                            message = "Are you sure you want to remove $displayName from this group?",
                            confirmText = "Remove",
                            onConfirm = {
                                scope.launch {
                                    viewModel.removeMember(inboxId).collect { state ->
                                        handleActionState(state)
                                    }
                                }
                            },
                        )
                    },
                    onLeaveGroup = { showLeaveGroupDialog = true },
                )

                // Add member dialog
                if (showAddMemberDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showAddMemberDialog = false
                            addMemberAddress = ""
                        },
                        title = { Text("Add Member") },
                        text = {
                            OutlinedTextField(
                                value = addMemberAddress,
                                onValueChange = { addMemberAddress = it },
                                label = { Text("Wallet Address") },
                                placeholder = { Text("0x...") },
                                singleLine = true,
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val address = addMemberAddress.trim()
                                    if (ADDRESS_PATTERN.matcher(address).matches()) {
                                        scope.launch {
                                            viewModel.addMember(address).collect { state ->
                                                handleActionState(state)
                                            }
                                        }
                                        showAddMemberDialog = false
                                        addMemberAddress = ""
                                    } else {
                                        Toast.makeText(
                                            this@GroupManagementActivity,
                                            "Invalid address",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            ) {
                                Text("Add")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showAddMemberDialog = false
                                    addMemberAddress = ""
                                },
                            ) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                // Leave group confirmation dialog
                if (showLeaveGroupDialog) {
                    AlertDialog(
                        onDismissRequest = { showLeaveGroupDialog = false },
                        title = { Text("Leave Group") },
                        text = { Text("Are you sure you want to leave this group?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showLeaveGroupDialog = false
                                    // TODO: Implement leave group when API supports it
                                    Toast.makeText(
                                        this@GroupManagementActivity,
                                        "Leave group not yet implemented",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            ) {
                                Text("Leave")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLeaveGroupDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }

                // Generic confirmation dialog
                showConfirmationDialog?.let { dialogData ->
                    AlertDialog(
                        onDismissRequest = { showConfirmationDialog = null },
                        title = { Text(dialogData.title) },
                        text = { Text(dialogData.message) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dialogData.onConfirm()
                                    showConfirmationDialog = null
                                },
                            ) {
                                Text(dialogData.confirmText)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmationDialog = null }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class ConfirmationDialogData(
    val title: String,
    val message: String,
    val confirmText: String,
    val onConfirm: () -> Unit,
)

private fun PermissionLevel.toMemberPermissionLevel(): MemberPermissionLevel {
    return when (this) {
        PermissionLevel.SUPER_ADMIN -> MemberPermissionLevel.SUPER_ADMIN
        PermissionLevel.ADMIN -> MemberPermissionLevel.ADMIN
        PermissionLevel.MEMBER -> MemberPermissionLevel.MEMBER
    }
}

private fun String.truncatedAddress(): String {
    if (length >= 10) {
        val start = 6
        val end = length - 4
        return replaceRange(start, end, "...")
    }
    return this
}
