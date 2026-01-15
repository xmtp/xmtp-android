package org.xmtp.android.example.conversation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.BottomSheetNewMessageBinding
import org.xmtp.android.example.extension.truncatedAddress
import java.util.regex.Pattern

class NewMessageBottomSheet : BottomSheetDialogFragment() {
    private val viewModel: NewConversationViewModel by viewModels()
    private var _binding: BottomSheetNewMessageBinding? = null
    private val binding get() = _binding!!

    private val groupAddresses: MutableList<String> = mutableListOf()
    private var isGroupMode = false

    companion object {
        const val TAG = "NewMessageBottomSheet"
        private const val MIN_GROUP_MEMBERS = 2

        private val ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$")

        fun newInstance(): NewMessageBottomSheet = NewMessageBottomSheet()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetNewMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }

        setupToggleGroup()
        setupAddressInput()
        setupActionButton()
        setupCreateButton()
        updateUiForMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            }
        }
    }

    private fun setupAddressInput() {
        binding.addressInput.addTextChangedListener { text ->
            val input = text?.toString()?.trim() ?: ""
            val isValidAddress = ADDRESS_PATTERN.matcher(input).matches()

            if (isGroupMode) {
                // In group mode, show add button for valid addresses not already added
                binding.actionButton.isVisible = isValidAddress && !groupAddresses.contains(input)
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
                if (ADDRESS_PATTERN.matcher(address).matches() && !groupAddresses.contains(address)) {
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

            if (isGroupMode) {
                if (groupAddresses.size >= MIN_GROUP_MEMBERS) {
                    viewModel.createGroup(groupAddresses)
                }
            } else {
                if (ADDRESS_PATTERN.matcher(address).matches()) {
                    viewModel.createConversation(address)
                }
            }
        }
    }

    private fun updateUiForMode() {
        if (isGroupMode) {
            binding.headerSubtitle.text = getString(R.string.new_group_subtitle)
            binding.helperText.text = getString(R.string.minimum_members_hint)
            binding.createButton.text = getString(R.string.create_group)
            binding.membersChipGroup.isVisible = groupAddresses.isNotEmpty()
            binding.memberCount.isVisible = groupAddresses.isNotEmpty()
            updateMemberCount()
        } else {
            binding.headerSubtitle.text = getString(R.string.new_conversation_subtitle)
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
        groupAddresses.add(address)

        val chip =
            Chip(requireContext()).apply {
                text = address.truncatedAddress()
                isCloseIconVisible = true
                setChipBackgroundColorResource(R.color.surface_variant)
                setTextColor(resources.getColor(R.color.text_primary, null))
                setCloseIconTintResource(R.color.text_tertiary)
                tag = address
                setOnCloseIconClickListener {
                    removeMember(address)
                }
            }

        binding.membersChipGroup.addView(chip)
        binding.membersChipGroup.isVisible = true
        updateMemberCount()
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
    }

    private fun updateMemberCount() {
        val count = groupAddresses.size
        binding.memberCount.isVisible = count > 0
        binding.memberCount.text = resources.getString(R.string.member_count, count)
        if (isGroupMode) {
            binding.createButton.isEnabled = count >= MIN_GROUP_MEMBERS
        }
    }

    private fun ensureUiState(uiState: NewConversationViewModel.UiState) {
        when (uiState) {
            is NewConversationViewModel.UiState.Error -> {
                binding.addressInput.isEnabled = true
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
                binding.actionButton.isEnabled = false
                binding.createButton.isEnabled = false
                binding.createButton.text = ""
                binding.messageTypeToggle.isEnabled = false
                binding.progress.visibility = View.VISIBLE
            }

            is NewConversationViewModel.UiState.Success -> {
                startActivity(
                    ConversationDetailActivity.intent(
                        requireContext(),
                        topic = uiState.conversation.topic,
                        peerAddress = uiState.conversation.id,
                    ),
                )
                dismiss()
            }

            NewConversationViewModel.UiState.Unknown -> Unit
        }
    }

    private fun showError(message: String) {
        val error = message.ifBlank { resources.getString(R.string.error) }
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }
}
