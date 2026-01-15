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
import kotlinx.coroutines.launch
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.BottomSheetNewConversationBinding
import java.util.regex.Pattern

class NewConversationBottomSheet : BottomSheetDialogFragment() {
    private val viewModel: NewConversationViewModel by viewModels()
    private var _binding: BottomSheetNewConversationBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "NewConversationBottomSheet"

        private val ADDRESS_PATTERN = Pattern.compile("^0x[a-fA-F0-9]{40}$")

        fun newInstance(): NewConversationBottomSheet = NewConversationBottomSheet()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetNewConversationBinding.inflate(inflater, container, false)
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

            // Show/hide clear button
            binding.clearButton.isVisible = input.isNotEmpty()

            // Enable/disable create button
            binding.createButton.isEnabled = isValidAddress

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

        binding.clearButton.setOnClickListener {
            binding.addressInput.text?.clear()
        }

        binding.createButton.setOnClickListener {
            val address =
                binding.addressInput.text
                    ?.toString()
                    ?.trim() ?: ""
            if (ADDRESS_PATTERN.matcher(address).matches()) {
                viewModel.createConversation(address)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun ensureUiState(uiState: NewConversationViewModel.UiState) {
        when (uiState) {
            is NewConversationViewModel.UiState.Error -> {
                binding.addressInput.isEnabled = true
                binding.createButton.isEnabled = true
                binding.createButton.text = getString(R.string.start_conversation)
                binding.progress.visibility = View.GONE
                showError(uiState.message)
            }
            NewConversationViewModel.UiState.Loading -> {
                binding.addressInput.isEnabled = false
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
