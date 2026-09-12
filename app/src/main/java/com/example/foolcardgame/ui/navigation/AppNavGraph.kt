package com.example.foolcardgame.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.foolcardgame.presentation.login.LoginViewModel
import com.example.foolcardgame.presentation.login.LoginViewModelFactory
import com.example.foolcardgame.presentation.offline.OfflineSetupViewModel
import com.example.foolcardgame.presentation.offline.OfflineSetupViewModelFactory
import com.example.foolcardgame.presentation.online.JoinPrivateViewModel
import com.example.foolcardgame.presentation.online.JoinPrivateViewModelFactory
import com.example.foolcardgame.presentation.online.OnlineLobbyNavEvent
import com.example.foolcardgame.presentation.online.OnlineLobbyViewModel
import com.example.foolcardgame.presentation.online.OnlineLobbyViewModelFactory
import com.example.foolcardgame.presentation.online.WaitingRoomViewModel
import com.example.foolcardgame.presentation.online.WaitingRoomViewModelFactory
import com.example.foolcardgame.presentation.profile.ProfileViewModel
import com.example.foolcardgame.presentation.profile.ProfileViewModelFactory
import com.example.foolcardgame.ui.screens.game.GameDebugScreen
import com.example.foolcardgame.ui.screens.game.GameSessionScreen
import com.example.foolcardgame.ui.screens.login.LoginScreen
import com.example.foolcardgame.ui.screens.main.MainMenuScreen
import com.example.foolcardgame.ui.screens.offline.OfflineSetupScreen
import com.example.foolcardgame.ui.screens.online.JoinPrivateScreen
import com.example.foolcardgame.ui.screens.online.OnlineLobbyScreen
import com.example.foolcardgame.ui.screens.online.WaitingRoomScreen
import com.example.foolcardgame.ui.screens.profile.ProfileScreen

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.MAIN,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) {
            val context = LocalContext.current
            val viewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(
                    gameClient = AppGraph.remoteGameClient(context),
                    credentialsStore = AppGraph.accountCredentials(context),
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.navigateToLobby.collect {
                    navController.navigate(Routes.ONLINE_LOBBY) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            }
            LoginScreen(
                uiState = uiState,
                onUsernameChange = viewModel::onUsernameChange,
                onLoginClick = viewModel::onLoginClick,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MAIN) {
            val context = LocalContext.current
            MainMenuScreen(
                onOfflineClick = { navController.navigate(Routes.OFFLINE_SETUP) },
                onOnlineClick = {
                    if (AppGraph.remoteGameClient(context).isAuthorized()) {
                        navController.navigate(Routes.ONLINE_LOBBY)
                    } else {
                        navController.navigate(Routes.LOGIN)
                    }
                },
                onSettingsClick = { navController.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.OFFLINE_SETUP) {
            val viewModel: OfflineSetupViewModel = viewModel(
                factory = OfflineSetupViewModelFactory(
                    gameClient = AppGraph.localGameClient(),
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
            val context = LocalContext.current
            val viewModel: OnlineLobbyViewModel = viewModel(
                factory = OnlineLobbyViewModelFactory(
                    gameClient = AppGraph.remoteGameClient(context),
                    credentialsStore = AppGraph.accountCredentials(context),
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.navEvents.collect { event ->
                    when (event) {
                        is OnlineLobbyNavEvent.ToGame -> {
                            navController.navigate(Routes.game(event.sessionId))
                        }
                        is OnlineLobbyNavEvent.ToWaiting -> {
                            navController.navigate(
                                Routes.onlineWaiting(event.sessionId, event.playerId),
                            )
                        }
                        is OnlineLobbyNavEvent.ToJoinByCode -> {
                            navController.navigate(
                                Routes.onlineJoin(
                                    displayName = event.displayName,
                                    avatarId = event.avatarId,
                                ),
                            )
                        }
                    }
                }
            }
            OnlineLobbyScreen(
                uiState = uiState,
                onAvatarSelected = viewModel::onAvatarSelected,
                onQuickMatchClick = viewModel::onQuickMatchClick,
                onCancelQuickMatch = viewModel::onCancelQuickMatch,
                onFriendsExpandToggle = viewModel::onFriendsExpandToggle,
                onCreatePrivateClick = viewModel::onCreatePrivateClick,
                onJoinByCodeClick = viewModel::onJoinByCodeClick,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ONLINE_JOIN,
            arguments = listOf(
                navArgument("displayName") { type = NavType.StringType },
                navArgument("avatarId") { type = NavType.IntType },
            ),
        ) { entry ->
            val context = LocalContext.current
            val displayName = entry.arguments?.getString("displayName").orEmpty()
            val avatarId = entry.arguments?.getInt("avatarId") ?: 0
            val viewModel: JoinPrivateViewModel = viewModel(
                factory = JoinPrivateViewModelFactory(
                    gameClient = AppGraph.remoteGameClient(context),
                    displayName = displayName,
                    avatarId = avatarId,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.navigateToWaiting.collect { target ->
                    navController.navigate(
                        Routes.onlineWaiting(target.sessionId, target.playerId),
                    ) {
                        popUpTo(Routes.ONLINE_LOBBY) { inclusive = false }
                    }
                }
            }
            JoinPrivateScreen(
                uiState = uiState,
                onCodeChange = viewModel::onCodeChange,
                onJoinClick = viewModel::onJoinClick,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.ONLINE_WAITING,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("playerId") { type = NavType.StringType },
            ),
        ) { entry ->
            val context = LocalContext.current
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val playerId = entry.arguments?.getString("playerId").orEmpty()
            val viewModel: WaitingRoomViewModel = viewModel(
                factory = WaitingRoomViewModelFactory(
                    gameClient = AppGraph.remoteGameClient(context),
                    sessionId = sessionId,
                    playerId = playerId,
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.navigateToGame.collect { gameSessionId ->
                    navController.navigate(Routes.game(gameSessionId)) {
                        popUpTo(Routes.ONLINE_LOBBY) { inclusive = false }
                    }
                }
            }
            WaitingRoomScreen(
                uiState = uiState,
                onKickClick = viewModel::onKickClick,
                onStartClick = viewModel::onStartClick,
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
                onSoundsEnabledChange = viewModel::onSoundsEnabledChange,
                onThemeModeChange = viewModel::onThemeModeChange,
                onCardThemeChange = viewModel::onCardThemeChange,
                onBack = { navController.popBackStack() },
                onSnackbarShown = viewModel::consumeSnackbarMessage,
            )
        }
        composable(
            route = Routes.GAME,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { entry ->
            val context = LocalContext.current
            val sessionId = entry.arguments?.getString("sessionId").orEmpty()
            val gameClient = if (Routes.isOnlineSession(sessionId)) {
                AppGraph.remoteGameClient(context)
            } else {
                AppGraph.localGameClient()
            }
            val viewModel: GameViewModel = viewModel(
                factory = GameViewModelFactory(
                    gameClient = gameClient,
                    sessionId = sessionId,
                    soundEffects = AppGraph.gameSoundEffects(context),
                ),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> viewModel.onAppPaused()
                        Lifecycle.Event.ON_START -> viewModel.onAppResumed()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    viewModel.onAppPaused()
                }
            }
            GameSessionScreen(
                uiState = uiState,
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
                onSettingsClick = { navController.navigate(Routes.PROFILE) },
                onToggleLoserCardsClick = viewModel::onToggleLoserCardsClick,
                onExitClick = {
                    viewModel.onExitClick()
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = false }
                        launchSingleTop = true
                    }
                },
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
