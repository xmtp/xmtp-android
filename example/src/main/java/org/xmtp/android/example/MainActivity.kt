package org.xmtp.android.example

import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.xmtp.android.example.connect.ConnectWalletActivity
import org.xmtp.android.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var accountManager: AccountManager
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountManager = AccountManager.get(this)

        val keys = loadKeys()
        if (keys == null) {
            showSignIn()
            return
        }

        viewModel.createClient(keys)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.disconnect.setOnClickListener {
            disconnectWallet()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::ensureClientUi)
            }
        }
    }

    private fun ensureClientUi(uiState: MainViewModel.ClientUiState) {
        when (uiState) {
            is MainViewModel.ClientUiState.Ready -> binding.address.text = uiState.address
            else -> Unit
        }
    }

    private fun loadKeys(): ByteArray? {
        val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
        val account = accounts.firstOrNull() ?: return null
        val encodedKey = accountManager.getPassword(account)
        return Base64.decode(encodedKey.toByteArray(), Base64.NO_WRAP)
    }

    private fun showSignIn() {
        startActivity(Intent(this, ConnectWalletActivity::class.java))
        finish()
    }

    private fun disconnectWallet() {
        viewModel.clearClient()
        val accounts = accountManager.getAccountsByType(resources.getString(R.string.account_type))
        accounts.forEach { account ->
            accountManager.removeAccount(account, null, null, null)
        }
        showSignIn()
    }
}
