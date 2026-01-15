package org.xmtp.android.example.conversation

import androidx.annotation.UiThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.xmtp.android.example.ClientManager
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PublicIdentity

class NewConversationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Unknown)
    val uiState: StateFlow<UiState> = _uiState

    private val _recentContacts = MutableStateFlow<List<RecentContact>>(emptyList())
    val recentContacts: StateFlow<List<RecentContact>> = _recentContacts

    @UiThread
    fun createConversation(address: String) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conversation =
                    ClientManager.client.conversations.newConversationWithIdentity(
                        PublicIdentity(
                            IdentityKind.ETHEREUM,
                            address,
                        ),
                    )
                _uiState.value = UiState.Success(conversation)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    @UiThread
    fun createGroup(
        addresses: List<String>,
        groupName: String = "",
    ) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val group =
                    ClientManager.client.conversations.newGroupWithIdentities(
                        identities =
                            addresses.map {
                                PublicIdentity(
                                    IdentityKind.ETHEREUM,
                                    it,
                                )
                            },
                        groupName = groupName,
                    )
                _uiState.value = UiState.Success(Conversation.Group(group))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage.orEmpty())
            }
        }
    }

    @UiThread
    fun loadRecentContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentInboxId = ClientManager.client.inboxId
                val conversations = ClientManager.client.conversations.list()

                // Get unique peer addresses from DM conversations
                val contactsMap = mutableMapOf<String, RecentContact>()

                for (conversation in conversations) {
                    when (conversation) {
                        is Conversation.Dm -> {
                            val members = conversation.dm.members()
                            val peerMember = members.find { it.inboxId != currentInboxId }
                            peerMember?.let { member ->
                                val address = member.identities.firstOrNull()?.identifier
                                if (address != null && !contactsMap.containsKey(address.lowercase())) {
                                    contactsMap[address.lowercase()] =
                                        RecentContact(
                                            address = address,
                                            inboxId = member.inboxId,
                                            lastActivityTime = conversation.lastActivityNs,
                                        )
                                }
                            }
                        }
                        is Conversation.Group -> {
                            // Get all members from groups except current user
                            val members = conversation.group.members()
                            for (member in members) {
                                if (member.inboxId != currentInboxId) {
                                    val address = member.identities.firstOrNull()?.identifier
                                    if (address != null) {
                                        val existingContact = contactsMap[address.lowercase()]
                                        if (existingContact == null ||
                                            conversation.lastActivityNs > existingContact.lastActivityTime
                                        ) {
                                            contactsMap[address.lowercase()] =
                                                RecentContact(
                                                    address = address,
                                                    inboxId = member.inboxId,
                                                    lastActivityTime = conversation.lastActivityNs,
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sort by most recent activity
                val sortedContacts =
                    contactsMap.values
                        .sortedByDescending { it.lastActivityTime }
                        .take(20) // Limit to 20 recent contacts

                _recentContacts.value = sortedContacts
            } catch (e: Exception) {
                // Silently fail - just show empty recent contacts
                _recentContacts.value = emptyList()
            }
        }
    }

    sealed class UiState {
        object Unknown : UiState()

        object Loading : UiState()

        data class Success(
            val conversation: Conversation,
        ) : UiState()

        data class Error(
            val message: String,
        ) : UiState()
    }
}
