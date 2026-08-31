package com.example.foolcardgame.ui.screens.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.presentation.game.GameUiState
import com.example.foolcardgame.ui.components.game.GameTableLayout
import com.example.foolcardgame.ui.components.game.LeaveGameDialog
import com.example.foolcardgame.ui.components.game.LobbyTimeoutDialog
import androidx.compose.material3.MaterialTheme
import com.example.foolcardgame.ui.theme.AccentTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSessionScreen(
    uiState: GameUiState,
    title: String,
    onBackClick: () -> Unit,
    onLeaveConfirm: () -> Unit,
    onLeaveDismiss: () -> Unit,
    onCardClick: (String) -> Unit,
    onAttackDrop: (String) -> Unit,
    onDefendDrop: (String, Int) -> Unit,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onTakeClick: () -> Unit,
    onReadyClick: () -> Unit,
    onToggleLoserCardsClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
    onFlyAnimationFinished: () -> Unit = {},
    onLobbyTimeoutDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    debugPanel: @Composable (() -> Unit)? = null,
) {
    val handleBack = if (uiState.phase == GamePhase.FINISHED) onExitClick else onBackClick

    BackHandler(onBack = handleBack)

    LeaveGameDialog(
        visible = uiState.showLeaveDialog,
        onConfirm = onLeaveConfirm,
        onDismiss = onLeaveDismiss,
    )

    LobbyTimeoutDialog(
        visible = uiState.showLobbyTimeoutDialog,
        onDismiss = onLobbyTimeoutDismiss,
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = AccentTeal,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            debugPanel?.invoke()
            GameTableLayout(
                uiState = uiState,
                onCardClick = onCardClick,
                onAttackDrop = onAttackDrop,
                onDefendDrop = onDefendDrop,
                onBitoClick = onBitoClick,
                onPassClick = onPassClick,
                onTakeClick = onTakeClick,
                onReadyClick = onReadyClick,
                onToggleLoserCardsClick = onToggleLoserCardsClick,
                onExitClick = onExitClick,
                onFlyAnimationFinished = onFlyAnimationFinished,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
