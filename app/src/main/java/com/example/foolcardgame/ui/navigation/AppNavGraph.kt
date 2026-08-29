package com.example.foolcardgame.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.foolcardgame.ui.screens.login.LoginScreen
import com.example.foolcardgame.ui.screens.main.MainMenuScreen
import com.example.foolcardgame.ui.screens.stub.PlaceholderScreen

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.LOGIN,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginClick = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            MainMenuScreen(
                onOfflineClick = { navController.navigate(Routes.OFFLINE_SETUP) },
                onOnlineClick = { navController.navigate(Routes.ONLINE_LOBBY) },
                onSettingsClick = { navController.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.OFFLINE_SETUP) {
            PlaceholderScreen(title = "Оффлайн — скоро")
        }
        composable(Routes.ONLINE_LOBBY) {
            PlaceholderScreen(title = "Онлайн — скоро")
        }
        composable(Routes.PROFILE) {
            PlaceholderScreen(title = "Настройки — скоро")
        }
    }
}
