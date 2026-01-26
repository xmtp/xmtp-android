package org.xmtp.android.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.xmtp.android.example.connect.ConnectWalletViewModel
import org.xmtp.android.example.ui.components.DrawerState
import org.xmtp.android.example.ui.screens.ChatScreen
import org.xmtp.android.example.ui.screens.ConnectWalletScreen
import org.xmtp.android.example.ui.screens.ConversationItem
import org.xmtp.android.example.ui.screens.MainScreen
import org.xmtp.android.example.ui.screens.MessageItem
import org.xmtp.android.library.XMTPEnvironment
import uniffi.xmtpv3.FfiLogLevel

sealed class Screen(
    val route: String,
) {
    object ConnectWallet : Screen("connect")

    object Home : Screen("home")

    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    modifier: Modifier = Modifier,
    // Connect wallet state
    connectWalletUiState: ConnectWalletViewModel.ConnectUiState = ConnectWalletViewModel.ConnectUiState.Unknown,
    onGenerateWallet: (XMTPEnvironment, FfiLogLevel?) -> Unit = { _, _ -> },
    onConnectSuccess: (String) -> Unit = {},
    // Main screen state
    conversations: List<ConversationItem> = emptyList(),
    isLoading: Boolean = false,
    drawerState: DrawerState = DrawerState(
        walletAddress = "",
        environment = "",
        isLogsEnabled = false,
        hideDeletedMessages = false,
    ),
    // Main screen drawer actions
    onNewConversationClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onWalletInfoClick: () -> Unit = {},
    onNewGroupClick: () -> Unit = {},
    onHideDeletedMessagesToggle: (Boolean) -> Unit = {},
    onViewLogsClick: () -> Unit = {},
    onToggleLogsClick: (Boolean) -> Unit = {},
    onCopyAddressClick: () -> Unit = {},
    onDisconnectClick: () -> Unit = {},
    // Chat screen state
    messages: Map<String, List<MessageItem>> = emptyMap(),
    onSendMessage: (String, String) -> Unit = { _, _ -> },
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.ConnectWallet.route) {
            ConnectWalletScreen(
                uiState = connectWalletUiState,
                onGenerateWallet = onGenerateWallet,
                onConnectSuccess = { address ->
                    onConnectSuccess(address)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ConnectWallet.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Home.route) {
            MainScreen(
                conversations = conversations,
                drawerState = drawerState,
                isLoading = isLoading,
                onConversationClick = { conversationId ->
                    navController.navigate(Screen.Chat.createRoute(conversationId))
                },
                onNewConversationClick = onNewConversationClick,
                onSearchClick = onSearchClick,
                onWalletInfoClick = onWalletInfoClick,
                onNewGroupClick = onNewGroupClick,
                onHideDeletedMessagesToggle = onHideDeletedMessagesToggle,
                onViewLogsClick = onViewLogsClick,
                onToggleLogsClick = onToggleLogsClick,
                onCopyAddressClick = onCopyAddressClick,
                onDisconnectClick = onDisconnectClick,
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments =
                listOf(
                    navArgument("conversationId") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val conversation = conversations.find { it.id == conversationId }
            val conversationMessages = messages[conversationId] ?: emptyList()

            ChatScreen(
                conversationName = conversation?.name ?: "Conversation",
                messages = conversationMessages,
                isGroup = conversation?.isGroup ?: false,
                memberCount = conversation?.memberCount,
                onBackClick = { navController.popBackStack() },
                onSendMessage = { message ->
                    onSendMessage(conversationId, message)
                },
            )
        }
    }
}
