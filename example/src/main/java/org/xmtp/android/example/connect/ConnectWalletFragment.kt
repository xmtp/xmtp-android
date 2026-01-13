package org.xmtp.android.example.connect

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.walletconnect.wcmodal.client.WalletConnectModal
import com.walletconnect.wcmodal.ui.openWalletConnectModal
import kotlinx.coroutines.launch
import org.xmtp.android.example.MainActivity
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.FragmentConnectWalletBinding
import org.xmtp.android.library.XMTPEnvironment
import timber.log.Timber
import uniffi.xmtpv3.FfiLogLevel

class ConnectWalletFragment : Fragment() {
    private val viewModel: ConnectWalletViewModel by viewModels()

    private var _binding: FragmentConnectWalletBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConnectWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showWalletState.collect(::showWalletState)
            }
        }

        setupEnvironmentSpinner()
        setupLogLevelSpinner()

        binding.generateButton.setOnClickListener {
            val selectedEnvironment = getSelectedEnvironment()
            val selectedLogLevel = getSelectedLogLevel()
            viewModel.generateWallet(selectedEnvironment, selectedLogLevel)
        }
    }

    private fun setupEnvironmentSpinner() {
        val environments = resources.getStringArray(R.array.environment_options)
        val adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                environments,
            )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.environmentSpinner.adapter = adapter
        // Default to Dev (index 0)
        binding.environmentSpinner.setSelection(0)
    }

    private fun setupLogLevelSpinner() {
        val logLevels = resources.getStringArray(R.array.log_level_options)
        val adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                logLevels,
            )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.logLevelSpinner.adapter = adapter
        // Default to Off (index 0)
        binding.logLevelSpinner.setSelection(0)
    }

    private fun getSelectedEnvironment(): XMTPEnvironment =
        when (binding.environmentSpinner.selectedItemPosition) {
            0 -> XMTPEnvironment.DEV
            1 -> XMTPEnvironment.PRODUCTION
            else -> XMTPEnvironment.DEV
        }

    private fun getSelectedLogLevel(): FfiLogLevel? =
        when (binding.logLevelSpinner.selectedItemPosition) {
            0 -> null // Off
            1 -> FfiLogLevel.ERROR
            2 -> FfiLogLevel.WARN
            3 -> FfiLogLevel.INFO
            4 -> FfiLogLevel.DEBUG
            5 -> FfiLogLevel.TRACE
            else -> null
        }

    private fun ensureUiState(uiState: ConnectWalletViewModel.ConnectUiState) {
        when (uiState) {
            is ConnectWalletViewModel.ConnectUiState.Error -> showError(uiState.message)
            ConnectWalletViewModel.ConnectUiState.Loading -> showLoading()
            is ConnectWalletViewModel.ConnectUiState.Success -> signIn(uiState.address)

            ConnectWalletViewModel.ConnectUiState.Unknown -> Unit
        }
    }

    private fun showWalletState(walletState: ConnectWalletViewModel.ShowWalletForSigningState) {
        if (walletState.showWallet) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, walletState.uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                requireActivity().startActivity(intent)
                viewModel.clearShowWalletState()
            } catch (e: Exception) {
                Timber.tag(FRAGMENT_LOG_TAG).e("Activity not found: $e")
            }
        }
    }

    private fun signIn(address: String) {
        val accountManager = AccountManager.get(requireContext())
        Account(address, resources.getString(R.string.account_type)).also { account ->
            accountManager.addAccountExplicitly(account, address, null)
        }
        requireActivity().startActivity(Intent(requireActivity(), MainActivity::class.java))
        requireActivity().finish()
    }

    private fun showError(message: String) {
        binding.progress.visibility = View.GONE
        binding.generateButton.visibility = View.VISIBLE
        binding.environmentSpinner.visibility = View.VISIBLE
        binding.environmentLabel.visibility = View.VISIBLE
        binding.logLevelSpinner.visibility = View.VISIBLE
        binding.logLevelLabel.visibility = View.VISIBLE
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading() {
        binding.progress.visibility = View.VISIBLE
        binding.generateButton.visibility = View.GONE
        binding.connectButton.visibility = View.GONE
        binding.connectError.visibility = View.GONE
        binding.environmentSpinner.visibility = View.GONE
        binding.environmentLabel.visibility = View.GONE
        binding.logLevelSpinner.visibility = View.GONE
        binding.logLevelLabel.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val WC_URI_SCHEME = "wc://wc?uri="
        private const val FRAGMENT_LOG_TAG = "ConnectWalletFragment"
    }
}
