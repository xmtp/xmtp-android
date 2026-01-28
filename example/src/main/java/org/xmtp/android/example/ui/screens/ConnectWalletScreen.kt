package org.xmtp.android.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.R
import org.xmtp.android.example.connect.ConnectWalletViewModel
import org.xmtp.android.example.ui.components.DropdownSelector
import org.xmtp.android.example.ui.components.LoadingButton
import org.xmtp.android.example.ui.theme.XMTPTheme
import org.xmtp.android.library.XMTPEnvironment
import uniffi.xmtpv3.FfiLogLevel

data class EnvironmentOption(
    val environment: XMTPEnvironment,
    val displayName: String,
)

data class LogLevelOption(
    val logLevel: FfiLogLevel?,
    val displayName: String,
)

private val environmentOptions = listOf(
    EnvironmentOption(XMTPEnvironment.DEV, "Dev"),
    EnvironmentOption(XMTPEnvironment.PRODUCTION, "Production"),
    EnvironmentOption(XMTPEnvironment.LOCAL, "Local"),
)

private val logLevelOptions = listOf(
    LogLevelOption(null, "Off"),
    LogLevelOption(FfiLogLevel.ERROR, "Error"),
    LogLevelOption(FfiLogLevel.WARN, "Warn"),
    LogLevelOption(FfiLogLevel.INFO, "Info"),
    LogLevelOption(FfiLogLevel.DEBUG, "Debug"),
    LogLevelOption(FfiLogLevel.TRACE, "Trace"),
)

@Composable
fun ConnectWalletScreen(
    uiState: ConnectWalletViewModel.ConnectUiState,
    onGenerateWallet: (XMTPEnvironment, FfiLogLevel?) -> Unit,
    onConnectSuccess: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedEnvironment by remember { mutableStateOf(environmentOptions[0]) }
    var selectedLogLevel by remember { mutableStateOf(logLevelOptions[0]) }

    val isLoading = uiState is ConnectWalletViewModel.ConnectUiState.Loading
    val errorMessage = (uiState as? ConnectWalletViewModel.ConnectUiState.Error)?.message

    // Handle success navigation
    LaunchedEffect(uiState) {
        if (uiState is ConnectWalletViewModel.ConnectUiState.Success) {
            onConnectSuccess(uiState.address)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // XMTP Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_xmtp_white),
                    contentDescription = "XMTP Logo",
                    modifier = Modifier.size(120.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Title
                Text(
                    text = "Welcome to XMTP",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Connect your wallet to start messaging",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Environment Selector
                DropdownSelector(
                    label = "Environment",
                    options = environmentOptions,
                    selectedOption = selectedEnvironment,
                    onOptionSelected = { selectedEnvironment = it },
                    optionLabel = { it.displayName },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Log Level Selector
                DropdownSelector(
                    label = "Log Level",
                    options = logLevelOptions,
                    selectedOption = selectedLogLevel,
                    onOptionSelected = { selectedLogLevel = it },
                    optionLabel = { it.displayName },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Generate Wallet Button
                LoadingButton(
                    text = "Generate Wallet",
                    onClick = {
                        onGenerateWallet(
                            selectedEnvironment.environment,
                            selectedLogLevel.logLevel,
                        )
                    },
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Error Message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectWalletScreenPreview() {
    XMTPTheme {
        ConnectWalletScreen(
            uiState = ConnectWalletViewModel.ConnectUiState.Unknown,
            onGenerateWallet = { _, _ -> },
            onConnectSuccess = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectWalletScreenLoadingPreview() {
    XMTPTheme {
        ConnectWalletScreen(
            uiState = ConnectWalletViewModel.ConnectUiState.Loading,
            onGenerateWallet = { _, _ -> },
            onConnectSuccess = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectWalletScreenErrorPreview() {
    XMTPTheme {
        ConnectWalletScreen(
            uiState = ConnectWalletViewModel.ConnectUiState.Error("Failed to connect to network"),
            onGenerateWallet = { _, _ -> },
            onConnectSuccess = {},
        )
    }
}
