package com.luka.hermes.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    object Sessions : Screen("sessions", "Sessions", Icons.Default.Chat)
    object Chat : Screen("chat/{sessionId}", "Chat", null)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    fun withArgs(vararg args: Pair<String, String>): String {
        var r = route
        args.forEach { (k, v) -> r = r.replace("{$k}", v) }
        return r
    }
}

private val bottomNavScreens = listOf(Screen.Sessions, Screen.Settings)

@Composable
fun HermesNavHost(
    initialRoute: String = Screen.Sessions.route,
    tokenConfigured: Boolean,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val settingsViewModel: SettingsViewModel = viewModel()

    // Determine if we should show the bottom bar (not on chat screens)
    val showBottomBar = currentDestination?.route in bottomNavScreens.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Sessions.route) {
                val sessionsViewModel: SessionsViewModel = viewModel()
                SessionsScreen(
                    viewModel = sessionsViewModel,
                    onSessionSelected = { sessionId ->
                        navController.navigate(Screen.Chat.withArgs("sessionId" to sessionId))
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                )
            }

            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                val chatViewModel: ChatViewModel = viewModel()
                ChatScreen(
                    sessionId = sessionId,
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onTokenConfigured = {
                        navController.navigate(Screen.Sessions.route) {
                            popUpTo(Screen.Sessions.route) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
