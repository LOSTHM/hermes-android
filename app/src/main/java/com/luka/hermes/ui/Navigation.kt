package com.luka.hermes.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// ── Destinations ────────────────────────────────────────────────────────────

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem("sessions", "Sessions", Icons.Filled.Chat, Icons.Outlined.Chat),
    BottomNavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

// ── Nav Host ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesNavHost(
    initialRoute: String = "sessions",
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val settingsViewModel: SettingsViewModel = viewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // Hide bottom bar on detail screens
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = NavigationBarDefaults.Elevation,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            // Sessions / Chat list
            composable("sessions") {
                val sessionsViewModel: SessionsViewModel = viewModel()
                SessionsScreen(
                    viewModel = sessionsViewModel,
                    onSessionSelected = { id ->
                        navController.navigate("chat/$id")
                    },
                    onDirectChat = {
                        navController.navigate("direct_chat")
                    },
                )
            }

            // Hermes session chat
            composable(
                route = "chat/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                val chatViewModel: ChatViewModel = viewModel()
                chatViewModel.setChatMode(ChatMode.HERMES)
                ChatScreen(
                    sessionId = sessionId,
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            // Direct API chat
            composable("direct_chat") {
                val chatViewModel: ChatViewModel = viewModel()
                chatViewModel.setChatMode(ChatMode.DIRECT)
                chatViewModel.setDirectConfig(
                    baseUrl = settingsState.apiBaseUrl,
                    apiKey = settingsState.apiKey,
                    model = settingsState.apiModel,
                    temperature = settingsState.temperature,
                    maxTokens = settingsState.maxTokens,
                    systemPrompt = settingsState.systemPrompt,
                )
                ChatScreen(
                    sessionId = null,
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            // Settings
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onConfigured = {
                        navController.navigate("sessions") {
                            popUpTo("sessions") { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
