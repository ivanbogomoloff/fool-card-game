package com.example.foolcardgame.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.foolcardgame.di.AppGraph
import com.example.foolcardgame.presentation.game.GameViewModel
import com.example.foolcardgame.presentation.game.GameViewModelFactory
import com.example.foolcardgame.presentation.offline.OfflineSetupViewModel
import com.example.foolcardgame.presentation.offline.OfflineSetupViewModelFactory
import com.example.foolcardgame.presentation.profile.ProfileViewModel
import com.example.foolcardgame.presentation.profile.ProfileViewModelFactory
import com.example.foolcardgame.ui.screens.game.GameDebugScreen
import com.example.foolcardgame.ui.screens.game.GameSessionScreen
import com.example.foolcardgame.ui.screens.login.LoginScreen
import com.example.foolcardgame.ui.screens.main.MainMenuScreen
import com.example.foolcardgame.ui.screens.offline.OfflineSetupScreen
import com.example.foolcardgame.ui.screens.profile.ProfileScreen
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
            val context = LocalContext.current
            val viewModel: OfflineSetupViewModel = viewModel(
                factory = OfflineSetupViewModelFactory(
                    gameClient = AppGraph.localGameClient(),
                    profileRepository = AppGraph.profileRepository(context),
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.navigateToGame.collect { sessionId ->
                    navController.navigate(Routes.game(sessionId)) {
                        popUpTo(Routes.OFFLINE_SETUP) { inclusive = true }
                    }
                }
            }
            OfflineSetupScreen(
                uiState = uiState,
                onBotCountSelected = viewModel::onBotCountSelected,
                onBotReactionRangeChanged = viewModel::onBotReactionRangeChanged,
                onStartClick = viewModel::onStartClick,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ONLINE_LOBBY) {
            PlaceholderScreen(
                title = "Игра по сети",
                message = "Лобби — скоро",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.PROFILE) {
            val context = LocalContext.current
            val viewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(
                    repository = AppGraph.profileRepository(context),
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ProfileScreen(
                uiState = uiState,
                onDisplayNameChange = viewModel::onDisplayNameChange,
                onAvatarSelected = viewModel::onAvatarSelected,
                onSaveClick = viewModel::saveProfile,
                onBack = { navController.popBackStack() },
                onSnackbarShown = viewModel::consumeSnackbarMessage,
            )
        }
        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val viewModel: GameViewModel = viewModel(
                factory = GameViewModelFactory(
                    gameClient = AppGraph.localGameClient(),
                    sessionId = sessionId,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            GameSessionScreen(
                uiState = uiState,
                title = "Игра",
                onBackClick = viewModel::onBackClick,
                onLeaveConfirm = {
                    viewModel.onLeaveConfirm()
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onLeaveDismiss = viewModel::onLeaveDismiss,
                onCardClick = viewModel::onCardSelected,
                onAttackDrop = viewModel::onAttackDrop,
                onDefendDrop = viewModel::onDefendDrop,
                onBitoClick = viewModel::onBitoClick,
                onPassClick = viewModel::onPassClick,
                onTakeClick = viewModel::onTakeClick,
                onReadyClick = viewModel::onReadyClick,
                onFlyAnimationFinished = viewModel::onFlyAnimationFinished,
                onLobbyTimeoutDismiss = {
                    viewModel.onLobbyTimeoutDismiss()
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                },
            )
        }
        composable(Routes.GAME_DEBUG) {
            GameDebugScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
