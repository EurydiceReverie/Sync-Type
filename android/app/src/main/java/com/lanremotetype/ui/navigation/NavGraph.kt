package com.lanremotetype.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lanremotetype.viewmodel.MainViewModel
import com.lanremotetype.ui.screens.ConnectScreen
import com.lanremotetype.ui.screens.HomeScreen
import com.lanremotetype.ui.screens.QueueScreen
import com.lanremotetype.ui.screens.SettingsScreen
import com.lanremotetype.ui.screens.TypeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Connect : Screen("connect")
    object Type : Screen("type")
    object Queue : Screen("queue")
    object Settings : Screen("settings")
}

@Composable
fun AppNavGraph(
    viewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToConnect = { navController.navigate(Screen.Connect.route) },
                onNavigateToType = { navController.navigate(Screen.Type.route) },
                onNavigateToQueue = { navController.navigate(Screen.Queue.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Connect.route) {
            ConnectScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Type.route) {
            TypeScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Queue.route) {
            QueueScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToConnect = { navController.navigate(Screen.Connect.route) }
            )
        }
    }
}
