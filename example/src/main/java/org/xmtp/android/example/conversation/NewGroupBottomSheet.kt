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
import org.xmtp.android.example.databinding.BottomSheetNewGroupBinding
import org.xmtp.android.example.extension.truncatedAddress
import java.util.regex.Pattern

class NewGroupBottomSheet : BottomSheetDialogFragment() {
    private val viewModel: NewConversationViewModel by viewModels()
    private var _binding: BottomSheetNewGroupBinding? = null
    private val addresses: MutableList<String> = mutableListOf()
    private val binding get() = _binding!!

    companion object {
        const val TAG = "NewGroupBottomSheet"
        private const val MIN_MEMBERS = 2

        private val ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$")

        fun newInstance(): NewGroupBottomSheet = NewGroupBottomSheet()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetNewGroupBinding.inflate(inflater, container, false)
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

        binding.addressInput.addTextChangedListener { text ->
            val input = text?.toString()?.trim() ?: ""
            val isValidAddress = ADDRESS_PATTERN.matcher(input).matches()

            // Enable add button only for valid addresses not already added
            binding.addButton.isEnabled = isValidAddress && !addresses.contains(input)

            // Update add button tint based on enabled state
            binding.addButton.setColorFilter(
                resources.getColor(
                    if (binding.addButton.isEnabled) R.color.xmtp_primary else R.color.text_tertiary,
                    null,
                ),
            )

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

        binding.addButton.setOnClickListener {
            val address =
                binding.addressInput.text
                    ?.toString()
                    ?.trim() ?: ""
            if (ADDRESS_PATTERN.matcher(address).matches() && !addresses.contains(address)) {
                addMember(address)
                binding.addressInput.text?.clear()
            }
        }

        binding.createButton.setOnClickListener {
            if (addresses.size >= MIN_MEMBERS) {
                viewModel.createGroup(addresses)
            }
        }

        updateMemberCount()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun addMember(address: String) {
        addresses.add(address)

        // Create a chip for the member
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
        addresses.remove(address)

        // Find and remove the chip with this address
        for (i in 0 until binding.membersChipGroup.childCount) {
            val chip = binding.membersChipGroup.getChildAt(i) as? Chip
            if (chip?.tag == address) {
                binding.membersChipGroup.removeView(chip)
                break
            }
        }

        binding.membersChipGroup.isVisible = addresses.isNotEmpty()
        updateMemberCount()
    }

    private fun updateMemberCount() {
        val count = addresses.size
        binding.memberCount.isVisible = count > 0
        binding.memberCount.text = resources.getString(R.string.member_count, count)
        binding.createButton.isEnabled = count >= MIN_MEMBERS
    }

    private fun ensureUiState(uiState: NewConversationViewModel.UiState) {
        when (uiState) {
            is NewConversationViewModel.UiState.Error -> {
                binding.addressInput.isEnabled = true
                binding.addButton.isEnabled = true
                binding.createButton.isEnabled = addresses.size >= MIN_MEMBERS
                binding.createButton.text = getString(R.string.create_group)
                binding.progress.visibility = View.GONE
                showError(uiState.message)
            }

            NewConversationViewModel.UiState.Loading -> {
                binding.addressInput.isEnabled = false
                binding.addButton.isEnabled = false
                binding.createButton.isEnabled = false
                binding.createButton.text = ""
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
