package org.xmtp.android.example.connect

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment.Companion.findNavController
import androidx.navigation.fragment.findNavController
import com.walletconnect.wcmodal.client.Modal
import com.walletconnect.wcmodal.client.WalletConnectModal
import com.walletconnect.wcmodal.ui.openWalletConnectModal
import kotlinx.coroutines.launch
import org.xmtp.android.example.MainActivity
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.FragmentConnectWalletBinding


class ConnectWalletFragment : Fragment() {


    private val viewModel: ConnectWalletViewModel by viewModels()

    private var _binding: FragmentConnectWalletBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentConnectWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }

        binding.generateButton.setOnClickListener {
            viewModel.generateWallet()
        }

        val isConnectWalletAvailable = isConnectAvailable()
        binding.connectButton.isEnabled = isConnectWalletAvailable
        binding.connectError.isVisible = !isConnectWalletAvailable
        binding.connectButton.setOnClickListener {
            //binding.connectButton.start(viewModel.walletConnectKit, ::onConnected, ::onDisconnected)
            findNavController().openWalletConnectModal(id = R.id.action_to_bottomSheet)
        }

    }

    private fun onConnected(address: String) {
        viewModel.connectWallet()
    }

    private fun onDisconnected() {
        // No-op currently.
    }

    private fun isConnectAvailable(): Boolean {
        val wcIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(WC_URI_SCHEME)
        }
        return wcIntent.resolveActivity(requireActivity().packageManager) != null
    }

    private fun ensureUiState(uiState: ConnectWalletViewModel.ConnectUiState) {
        when (uiState) {
            is ConnectWalletViewModel.ConnectUiState.Error -> showError(uiState.message)
            ConnectWalletViewModel.ConnectUiState.Loading -> showLoading()
            is ConnectWalletViewModel.ConnectUiState.Success -> signIn(
                uiState.address,
                uiState.encodedKeyData
            )
            ConnectWalletViewModel.ConnectUiState.Unknown -> Unit
        }
    }

    private fun signIn(address: String, encodedKey: String) {
        val accountManager = AccountManager.get(requireContext())
        Account(address, resources.getString(R.string.account_type)).also { account ->
            accountManager.addAccountExplicitly(account, encodedKey, null)
        }
        requireActivity().startActivity(Intent(requireActivity(), MainActivity::class.java))
        requireActivity().finish()
    }

    private fun showError(message: String) {
        binding.progress.visibility = View.GONE
        binding.generateButton.visibility = View.VISIBLE
        binding.connectButton.visibility = View.VISIBLE
        binding.connectError.isVisible = !isConnectAvailable()
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading() {
        binding.progress.visibility = View.VISIBLE
        binding.generateButton.visibility = View.GONE
        binding.connectButton.visibility = View.GONE
        binding.connectError.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val WC_URI_SCHEME = "wc://wc?uri="
    }

}