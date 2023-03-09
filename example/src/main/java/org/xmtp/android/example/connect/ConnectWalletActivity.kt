package org.xmtp.android.example.connect

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.pinkroom.walletconnectkit.WalletConnectButton
import dev.pinkroom.walletconnectkit.WalletConnectKit
import dev.pinkroom.walletconnectkit.WalletConnectKitConfig
import kotlinx.coroutines.launch
import org.xmtp.android.example.MainActivity
import org.xmtp.android.example.R
import org.xmtp.android.example.databinding.ActivityConnectWalletBinding

class ConnectWalletActivity : AppCompatActivity() {

    companion object {
        private const val WC_URI_SCHEME = "wc://wc?uri="
    }

    val config = WalletConnectKitConfig(
        context = this,
        bridgeUrl = "wss://bridge.aktionariat.com:8887",
        appUrl = "xmtp.org",
        appName = "XMTP Example app",
        appDescription = "XMTP Example app",
    )

    private val viewModel: ConnectWalletViewModel by viewModels()
    private lateinit var binding: ActivityConnectWalletBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConnectWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val walletConnectKit = WalletConnectKit.Builder(config).build()

        val walletConnectButton = findViewById<WalletConnectButton>(R.id.walletConnectButton)

        walletConnectButton.start(walletConnectKit) {address ->
            println("address: $address")
            Toast.makeText(this, "Connnected! address: $address", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureUiState)
            }
        }

        binding.generateButton.setOnClickListener {
            viewModel.generateWallet()
        }

        val showConnectButton = isConnectAvailable()
        binding.connectButton.isEnabled = showConnectButton
        binding.connectError.isVisible = !showConnectButton
        binding.connectButton.setOnClickListener {
            viewModel.connectWallet()
        }
    }

    private fun isConnectAvailable(): Boolean {
        val wcIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(WC_URI_SCHEME)
        }
        return wcIntent.resolveActivity(packageManager) != null
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
            is ConnectWalletViewModel.ConnectUiState.Connect -> connect(uiState.uri)
        }
    }

    private fun signIn(address: String, encodedKey: String) {
        val accountManager = AccountManager.get(this)
        Account(address, resources.getString(R.string.account_type)).also { account ->
            accountManager.addAccountExplicitly(account, encodedKey, null)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(message: String) {
        binding.progress.visibility = View.GONE
        binding.generateButton.visibility = View.VISIBLE
        binding.connectButton.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading() {
        binding.progress.visibility = View.VISIBLE
        binding.generateButton.visibility = View.GONE
        binding.connectButton.visibility = View.GONE
    }

    private fun connect(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("$WC_URI_SCHEME$uri")
        startActivity(intent)
    }
}
