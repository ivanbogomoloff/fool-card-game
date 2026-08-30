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
import com.example.foolcardgame.presentation.game.GameUiState
import com.example.foolcardgame.ui.components.game.GameTableLayout
import com.example.foolcardgame.ui.components.game.LeaveGameDialog
import com.example.foolcardgame.ui.components.game.LobbyTimeoutDialog
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.TableGreen

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
    onFlyAnimationFinished: () -> Unit = {},
    onLobbyTimeoutDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    debugPanel: @Composable (() -> Unit)? = null,
) {
    BackHandler(onBack = onBackClick)

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
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TableGreen,
                    navigationIconContentColor = AccentTeal,
                ),
            )
        },
        containerColor = TableGreen,
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
                onFlyAnimationFinished = onFlyAnimationFinished,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
