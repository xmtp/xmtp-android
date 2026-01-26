package org.xmtp.android.example.connect

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.xmtp.android.example.MainActivity
import org.xmtp.android.example.R
import org.xmtp.android.example.ui.screens.ConnectWalletScreen
import org.xmtp.android.example.ui.theme.XMTPTheme

class ConnectWalletActivity : ComponentActivity() {
    private val viewModel: ConnectWalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            XMTPTheme {
                val uiState by viewModel.uiState.collectAsState()

                ConnectWalletScreen(
                    uiState = uiState,
                    onGenerateWallet = { environment, logLevel ->
                        viewModel.generateWallet(environment, logLevel)
                    },
                    onConnectSuccess = { address ->
                        signIn(address)
                    },
                )
            }
        }
    }

    private fun signIn(address: String) {
        val accountManager = AccountManager.get(this)
        Account(address, resources.getString(R.string.account_type)).also { account ->
            accountManager.addAccountExplicitly(account, address, null)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
