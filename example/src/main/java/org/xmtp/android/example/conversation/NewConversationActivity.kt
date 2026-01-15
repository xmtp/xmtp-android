package org.xmtp.android.example.conversation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ActivityNewConversationBinding
import org.xmtp.android.example.extension.truncatedAddress
import java.util.regex.Pattern
import kotlin.math.abs

class NewConversationActivity :
    AppCompatActivity(),
    RecentContactClickListener {
    private lateinit var binding: ActivityNewConversationBinding
    private val viewModel: NewConversationViewModel by viewModels()

    private val groupAddresses: MutableList<String> = mutableListOf()
    private var isGroupMode = false
    private var recentContactsAdapter: RecentContactsAdapter? = null

    private val avatarColors =
        listOf(
            Color.parseColor("#FC4F37"),
            Color.parseColor("#5856D6"),
            Color.parseColor("#34C759"),
            Color.parseColor("#FF9500"),
            Color.parseColor("#007AFF"),
            Color.parseColor("#AF52DE"),
            Color.parseColor("#00C7BE"),
            Color.parseColor("#FF2D55"),
        )

    companion object {
        private const val MIN_GROUP_MEMBERS = 1 // Just 1 other member needed (current user is automatically included)
        private val ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$")

        fun intent(context: Context): Intent = Intent(context, NewConversationActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupToggleGroup()
        setupAddressInput()
        setupActionButton()
        setupCreateButton()
        setupRecentContacts()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentContacts.collect(::updateRecentContacts)
            }
        }

        updateUiForMode()
        viewModel.loadRecentContacts()
    }

    private fun setupToggleGroup() {
        binding.messageTypeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isGroupMode = checkedId == R.id.groupButton
                updateUiForMode()
                // Clear input when switching modes
                binding.addressInput.text?.clear()
                groupAddresses.clear()
                binding.membersChipGroup.removeAllViews()
                updateMemberCount()
                recentContactsAdapter?.setGroupMode(isGroupMode, groupAddresses)
            }
        }
    }

    private fun setupAddressInput() {
        binding.addressInput.addTextChangedListener { text ->
            val input = text?.toString()?.trim() ?: ""
            val isValidAddress = ADDRESS_PATTERN.matcher(input).matches()

            if (isGroupMode) {
                // In group mode, show add button for valid addresses not already added
                binding.actionButton.isVisible = isValidAddress && !groupAddresses.contains(input.lowercase())
                binding.actionButton.setImageResource(R.drawable.ic_add_24)
                binding.actionButton.setColorFilter(
                    resources.getColor(
                        if (binding.actionButton.isVisible) R.color.xmtp_primary else R.color.text_tertiary,
                        null,
                    ),
                )
            } else {
                // In DM mode, show clear button when there's text
                binding.actionButton.isVisible = input.isNotEmpty()
                binding.actionButton.setImageResource(R.drawable.ic_close_24)
                binding.actionButton.setColorFilter(
                    resources.getColor(R.color.text_tertiary, null),
                )
                // Enable create button for valid DM address
                binding.createButton.isEnabled = isValidAddress
            }

            // Update helper text color based on validation
            if (input.isNotEmpty() && !isValidAddress) {
                binding.helperText.setTextColor(
                    resources.getColor(R.color.error, null),
                )
            } else {
                binding.helperText.setTextColor(
                    resources.getColor(R.color.text_tertiary, null),
                )
            }
        }
    }

    private fun setupActionButton() {
        binding.actionButton.setOnClickListener {
            if (isGroupMode) {
                // Add member to group
                val address =
                    binding.addressInput.text
                        ?.toString()
                        ?.trim() ?: ""
                if (ADDRESS_PATTERN.matcher(address).matches() && !groupAddresses.contains(address.lowercase())) {
                    addMember(address)
                    binding.addressInput.text?.clear()
                }
            } else {
                // Clear DM address input
                binding.addressInput.text?.clear()
            }
        }
    }

    private fun setupCreateButton() {
        binding.createButton.setOnClickListener {
            val address =
                binding.addressInput.text
                    ?.toString()
                    ?.trim() ?: ""
            val groupName =
                binding.groupNameInput.text
                    ?.toString()
                    ?.trim() ?: ""

            if (isGroupMode) {
                if (groupAddresses.size >= MIN_GROUP_MEMBERS) {
                    viewModel.createGroup(groupAddresses, groupName)
                }
            } else {
                if (ADDRESS_PATTERN.matcher(address).matches()) {
                    viewModel.createConversation(address)
                }
            }
        }
    }

    private fun setupRecentContacts() {
        val adapter = RecentContactsAdapter(this, avatarColors)
        recentContactsAdapter = adapter
        binding.recentContactsList.layoutManager = LinearLayoutManager(this)
        binding.recentContactsList.adapter = adapter
        // Initialize adapter with current mode
        adapter.setGroupMode(isGroupMode, groupAddresses)
    }

    private fun updateUiForMode() {
        // Update adapter when mode changes
        recentContactsAdapter?.setGroupMode(isGroupMode, groupAddresses)
        if (isGroupMode) {
            binding.groupNameCard.isVisible = true
            binding.helperText.text = getString(R.string.minimum_members_hint)
            binding.createButton.text = getString(R.string.create_group)
            binding.membersChipGroup.isVisible = groupAddresses.isNotEmpty()
            binding.memberCount.isVisible = groupAddresses.isNotEmpty()
            updateMemberCount()
        } else {
            binding.groupNameCard.isVisible = false
            binding.helperText.text = getString(R.string.address_helper_text)
            binding.createButton.text = getString(R.string.start_conversation)
            binding.membersChipGroup.isVisible = false
            binding.memberCount.isVisible = false
            // Re-evaluate create button state for DM mode
            val input =
                binding.addressInput.text
                    ?.toString()
                    ?.trim() ?: ""
            binding.createButton.isEnabled = ADDRESS_PATTERN.matcher(input).matches()
        }
    }

    private fun addMember(address: String) {
        val normalizedAddress = address.lowercase()
        groupAddresses.add(normalizedAddress)

        val chip =
            Chip(this).apply {
                text = address.truncatedAddress()
                isCloseIconVisible = true
                setChipBackgroundColorResource(R.color.surface_variant)
                setTextColor(resources.getColor(R.color.text_primary, null))
                setCloseIconTintResource(R.color.text_tertiary)
                tag = normalizedAddress
                setOnCloseIconClickListener {
                    removeMember(normalizedAddress)
                }
            }

        binding.membersChipGroup.addView(chip)
        binding.membersChipGroup.isVisible = true
        updateMemberCount()
        recentContactsAdapter?.setGroupMode(isGroupMode, groupAddresses)
    }

    private fun removeMember(address: String) {
        groupAddresses.remove(address)

        for (i in 0 until binding.membersChipGroup.childCount) {
            val chip = binding.membersChipGroup.getChildAt(i) as? Chip
            if (chip?.tag == address) {
                binding.membersChipGroup.removeView(chip)
                break
            }
        }

        binding.membersChipGroup.isVisible = groupAddresses.isNotEmpty()
        updateMemberCount()
        recentContactsAdapter?.setGroupMode(isGroupMode, groupAddresses)
    }

    private fun updateMemberCount() {
        val count = groupAddresses.size
        binding.memberCount.isVisible = count > 0
        binding.memberCount.text = getString(R.string.member_count, count)
        if (isGroupMode) {
            binding.createButton.isEnabled = count >= MIN_GROUP_MEMBERS
        }
    }

    private fun updateRecentContacts(contacts: List<RecentContact>) {
        binding.recentContactsHeader.isVisible = contacts.isNotEmpty()
        binding.recentContactsList.isVisible = contacts.isNotEmpty()
        recentContactsAdapter?.setData(contacts)
    }

    private fun ensureUiState(uiState: NewConversationViewModel.UiState) {
        when (uiState) {
            is NewConversationViewModel.UiState.Error -> {
                binding.addressInput.isEnabled = true
                binding.groupNameInput.isEnabled = true
                binding.actionButton.isEnabled = true
                binding.messageTypeToggle.isEnabled = true
                if (isGroupMode) {
                    binding.createButton.isEnabled = groupAddresses.size >= MIN_GROUP_MEMBERS
                    binding.createButton.text = getString(R.string.create_group)
                } else {
                    val input =
                        binding.addressInput.text
                            ?.toString()
                            ?.trim() ?: ""
                    binding.createButton.isEnabled = ADDRESS_PATTERN.matcher(input).matches()
                    binding.createButton.text = getString(R.string.start_conversation)
                }
                binding.progress.visibility = View.GONE
                showError(uiState.message)
            }

            NewConversationViewModel.UiState.Loading -> {
                binding.addressInput.isEnabled = false
                binding.groupNameInput.isEnabled = false
                binding.actionButton.isEnabled = false
                binding.createButton.isEnabled = false
                binding.createButton.text = ""
                binding.messageTypeToggle.isEnabled = false
                binding.progress.visibility = View.VISIBLE
            }

            is NewConversationViewModel.UiState.Success -> {
                startActivity(
                    ConversationDetailActivity.intent(
                        this,
                        topic = uiState.conversation.topic,
                        peerAddress = uiState.conversation.id,
                    ),
                )
                finish()
            }

            NewConversationViewModel.UiState.Unknown -> Unit
        }
    }

    private fun showError(message: String) {
        val error = message.ifBlank { getString(R.string.error) }
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    override fun onContactClick(contact: RecentContact) {
        if (isGroupMode) {
            val address = contact.address.lowercase()
            if (groupAddresses.contains(address)) {
                removeMember(address)
            } else {
                addMember(contact.address)
            }
        } else {
            // In DM mode, start conversation directly
            viewModel.createConversation(contact.address)
        }
    }

    override fun onAddClick(contact: RecentContact) {
        val address = contact.address.lowercase()
        if (!groupAddresses.contains(address)) {
            addMember(contact.address)
        }
    }
}

data class RecentContact(
    val address: String,
    val inboxId: String?,
    val lastActivityTime: Long,
)

interface RecentContactClickListener {
    fun onContactClick(contact: RecentContact)

    fun onAddClick(contact: RecentContact)
}
