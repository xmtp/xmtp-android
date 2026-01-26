package org.xmtp.android.example.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.xmtp.android.example.ClientManager
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.Group
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PermissionLevel
import org.xmtp.android.library.libxmtp.PublicIdentity

class GroupManagementViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val conversationTopicFlow =
        savedStateHandle.getStateFlow<String?>(
            GroupManagementActivity.EXTRA_CONVERSATION_TOPIC,
            null,
        )

    fun setConversationTopic(topic: String?) {
        savedStateHandle[GroupManagementActivity.EXTRA_CONVERSATION_TOPIC] = topic
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private var group: Group? = null

    fun loadGroupInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val topic = conversationTopicFlow.value ?: throw Exception("No topic provided")
                val conversation =
                    ClientManager.client.conversations.findConversationByTopic(topic)
                        ?: throw Exception("Conversation not found")

                if (conversation !is Conversation.Group) {
                    throw Exception("Not a group conversation")
                }

                group = conversation.group
                group?.sync()

                val members = group?.members() ?: emptyList()
                val currentUserInboxId = ClientManager.client.inboxId

                // Get addresses for members
                val memberItems =
                    members
                        .map { member ->
                            val addresses = member.identities.mapNotNull { it.identifier }
                            MemberItem(
                                member = member,
                                isCurrentUser = member.inboxId == currentUserInboxId,
                                displayAddress = addresses.firstOrNull(),
                            )
                        }.sortedWith(
                            compareBy<MemberItem> { !it.isCurrentUser }
                                .thenBy {
                                    when (it.member.permissionLevel) {
                                        PermissionLevel.SUPER_ADMIN -> 0
                                        PermissionLevel.ADMIN -> 1
                                        PermissionLevel.MEMBER -> 2
                                    }
                                },
                        )

                val currentUserMember = members.find { it.inboxId == currentUserInboxId }
                val currentUserRole = currentUserMember?.permissionLevel ?: PermissionLevel.MEMBER
                val canManageMembers =
                    currentUserRole == PermissionLevel.SUPER_ADMIN ||
                        currentUserRole == PermissionLevel.ADMIN

                _uiState.value =
                    UiState.Success(
                        groupId = group?.id ?: "",
                        createdAt = group?.createdAt,
                        members = memberItems,
                        currentUserRole = currentUserRole,
                        canManageMembers = canManageMembers,
                    )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun addMember(address: String): StateFlow<ActionState> {
        val flow = MutableStateFlow<ActionState>(ActionState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val publicIdentity = PublicIdentity(IdentityKind.ETHEREUM, address)
                group?.addMembersByIdentity(listOf(publicIdentity))
                flow.value = ActionState.Success("Member added successfully")
                loadGroupInfo() // Refresh
            } catch (e: Exception) {
                flow.value = ActionState.Error(e.localizedMessage ?: "Failed to add member")
            }
        }
        return flow
    }

    fun removeMember(inboxId: String): StateFlow<ActionState> {
        val flow = MutableStateFlow<ActionState>(ActionState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                group?.removeMembers(listOf(inboxId))
                flow.value = ActionState.Success("Member removed successfully")
                loadGroupInfo() // Refresh
            } catch (e: Exception) {
                flow.value = ActionState.Error(e.localizedMessage ?: "Failed to remove member")
            }
        }
        return flow
    }

    fun promoteToAdmin(inboxId: String): StateFlow<ActionState> {
        val flow = MutableStateFlow<ActionState>(ActionState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                group?.addAdmin(inboxId)
                flow.value = ActionState.Success("Member promoted to admin")
                loadGroupInfo() // Refresh
            } catch (e: Exception) {
                flow.value = ActionState.Error(e.localizedMessage ?: "Failed to promote member")
            }
        }
        return flow
    }

    fun demoteFromAdmin(inboxId: String): StateFlow<ActionState> {
        val flow = MutableStateFlow<ActionState>(ActionState.Loading)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                group?.removeAdmin(inboxId)
                flow.value = ActionState.Success("Admin demoted to member")
                loadGroupInfo() // Refresh
            } catch (e: Exception) {
                flow.value = ActionState.Error(e.localizedMessage ?: "Failed to demote admin")
            }
        }
        return flow
    }

    sealed class UiState {
        object Loading : UiState()

        data class Success(
            val groupId: String,
            val createdAt: java.util.Date?,
            val members: List<MemberItem>,
            val currentUserRole: PermissionLevel,
            val canManageMembers: Boolean,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }

    sealed class ActionState {
        object Loading : ActionState()

        data class Success(
            val message: String,
        ) : ActionState()

        data class Error(
            val message: String,
        ) : ActionState()
    }
}

data class MemberItem(
    val member: org.xmtp.android.library.libxmtp.Member,
    val isCurrentUser: Boolean,
    val displayAddress: String?,
)
