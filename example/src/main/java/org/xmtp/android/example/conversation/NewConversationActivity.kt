package org.xmtp.android.example.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.xmtp.android.example.ui.components.RecentContactData
import org.xmtp.android.example.ui.screens.ConversationType
import org.xmtp.android.example.ui.screens.NewConversationScreen
import org.xmtp.android.example.ui.theme.XMTPTheme
import java.util.regex.Pattern

class NewConversationActivity : ComponentActivity() {
    private val viewModel: NewConversationViewModel by viewModels()

    companion object {
        private val ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$")

        fun intent(context: Context): Intent = Intent(context, NewConversationActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.loadRecentContacts()

        setContent {
            XMTPTheme {
                var addressInput by remember { mutableStateOf("") }
                var groupName by remember { mutableStateOf("") }
                var conversationType by remember { mutableStateOf(ConversationType.DM) }
                val selectedMembers = remember { mutableStateListOf<String>() }

                val uiState by viewModel.uiState.collectAsState()
                val recentContacts by viewModel.recentContacts.collectAsState()

                val isLoading = uiState is NewConversationViewModel.UiState.Loading

                val isValidAddress = ADDRESS_PATTERN.matcher(addressInput.trim()).matches()
                val canCreate = when (conversationType) {
                    ConversationType.DM -> isValidAddress
                    ConversationType.GROUP -> selectedMembers.isNotEmpty()
                }

                // Convert RecentContact to RecentContactData
                val recentContactsData = recentContacts.map { contact ->
                    RecentContactData(
                        address = contact.address,
                        lastActivityTimeNs = contact.lastActivityTime,
                    )
                }

                // Handle UI state changes
                LaunchedEffect(uiState) {
                    when (uiState) {
                        is NewConversationViewModel.UiState.Success -> {
                            val conversation = (uiState as NewConversationViewModel.UiState.Success).conversation
                            startActivity(
                                ConversationDetailActivity.intent(
                                    this@NewConversationActivity,
                                    topic = conversation.topic,
                                    peerAddress = conversation.id,
                                ),
                            )
                            finish()
                        }
                        is NewConversationViewModel.UiState.Error -> {
                            val error = (uiState as NewConversationViewModel.UiState.Error).message
                            Toast.makeText(
                                this@NewConversationActivity,
                                error.ifBlank { "Error" },
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        else -> Unit
                    }
                }

                NewConversationScreen(
                    addressInput = addressInput,
                    groupName = groupName,
                    selectedMembers = selectedMembers.toList(),
                    recentContacts = recentContactsData,
                    conversationType = conversationType,
                    isLoading = isLoading,
                    canCreate = canCreate,
                    onAddressChange = { addressInput = it },
                    onGroupNameChange = { groupName = it },
                    onConversationTypeChange = { newType ->
                        conversationType = newType
                        // Clear input and members when switching modes
                        addressInput = ""
                        selectedMembers.clear()
                    },
                    onAddMember = {
                        val address = addressInput.trim()
                        if (ADDRESS_PATTERN.matcher(address).matches() &&
                            !selectedMembers.contains(address.lowercase())
                        ) {
                            selectedMembers.add(address.lowercase())
                            addressInput = ""
                        }
                    },
                    onRemoveMember = { address ->
                        selectedMembers.remove(address)
                    },
                    onRecentContactClick = { address ->
                        if (conversationType == ConversationType.GROUP) {
                            val normalizedAddress = address.lowercase()
                            if (selectedMembers.contains(normalizedAddress)) {
                                selectedMembers.remove(normalizedAddress)
                            } else {
                                selectedMembers.add(normalizedAddress)
                            }
                        } else {
                            // In DM mode, start conversation directly
                            viewModel.createConversation(address)
                        }
                    },
                    onClose = { finish() },
                    onCreate = {
                        if (conversationType == ConversationType.GROUP) {
                            if (selectedMembers.isNotEmpty()) {
                                viewModel.createGroup(selectedMembers.toList(), groupName)
                            }
                        } else {
                            val address = addressInput.trim()
                            if (ADDRESS_PATTERN.matcher(address).matches()) {
                                viewModel.createConversation(address)
                            }
                        }
                    },
                )
            }
        }
    }
}

data class RecentContact(
    val address: String,
    val inboxId: String?,
    val lastActivityTime: Long,
)
