package com.example.foolcardgame.ui.screens.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.presentation.game.GameUiState
import com.example.foolcardgame.ui.components.game.GameTableLayout
import com.example.foolcardgame.ui.components.game.LeaveGameDialog
import com.example.foolcardgame.ui.components.game.LobbyTimeoutDialog
import com.example.foolcardgame.ui.theme.FeltGreenCenter
import com.example.foolcardgame.ui.theme.FeltGreenEdge
import com.example.foolcardgame.ui.theme.SoftCharcoal

@Composable
fun GameSessionScreen(
    uiState: GameUiState,
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
    onSettingsClick: () -> Unit = {},
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(FeltGreenCenter, FeltGreenEdge),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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

        GameChromeButton(
            icon = Icons.Filled.Home,
            contentDescription = "В меню",
            onClick = handleBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        )
        GameChromeButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Настройки",
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 8.dp),
        )
    }
}

@Composable
private fun GameChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = SoftCharcoal,
            )
        }
    }
}
