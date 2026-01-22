package org.xmtp.android.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.xmtp.android.example.ui.screens.ChatScreen
import org.xmtp.android.example.ui.screens.ConversationItem
import org.xmtp.android.example.ui.screens.HomeScreen
import org.xmtp.android.example.ui.screens.MessageItem
import org.xmtp.android.example.ui.screens.ProfileScreen
import org.xmtp.android.example.ui.screens.SettingsScreen

sealed class Screen(
    val route: String,
) {
    object Home : Screen("home")

    object Profile : Screen("profile")

    object Settings : Screen("settings")

    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems =
    listOf(
        BottomNavItem(
            screen = Screen.Home,
            title = "Chats",
            selectedIcon = Icons.Filled.Chat,
            unselectedIcon = Icons.Outlined.Chat,
        ),
        BottomNavItem(
            screen = Screen.Profile,
            title = "Profile",
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
        ),
        BottomNavItem(
            screen = Screen.Settings,
            title = "Settings",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    conversations: List<ConversationItem> = emptyList(),
    messages: Map<String, List<MessageItem>> = emptyMap(),
    walletAddress: String = "",
    inboxId: String = "",
    installationId: String = "",
    hideDeletedMessages: Boolean = false,
    onSendMessage: (String, String) -> Unit = { _, _ -> },
    onNewConversation: () -> Unit = {},
    onLogout: () -> Unit = {},
    onHideDeletedMessagesChange: (Boolean) -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Check if we should show bottom nav
    val showBottomNav =
        currentDestination?.route in
            listOf(
                Screen.Home.route,
                Screen.Profile.route,
                Screen.Settings.route,
            )

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected =
                            currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                )
                            },
                            label = { Text(item.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    conversations = conversations,
                    onConversationClick = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    },
                    onNewConversationClick = onNewConversation,
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    walletAddress = walletAddress,
                    inboxId = inboxId,
                    installationId = installationId,
                    onLogout = onLogout,
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    hideDeletedMessages = hideDeletedMessages,
                    onHideDeletedMessagesChange = onHideDeletedMessagesChange,
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
}
