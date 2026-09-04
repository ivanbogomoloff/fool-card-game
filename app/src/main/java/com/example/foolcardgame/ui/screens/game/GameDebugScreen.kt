package com.example.foolcardgame.ui.screens.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foolcardgame.data.client.DebugScenario
import com.example.foolcardgame.presentation.game.GameDebugViewModel
import com.example.foolcardgame.presentation.game.GameDebugViewModelFactory

@Composable
fun GameDebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameDebugViewModel = viewModel(factory = GameDebugViewModelFactory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GameSessionScreen(
        uiState = uiState,
        onBackClick = viewModel::onBackClick,
        onLeaveConfirm = {
            viewModel.onLeaveConfirm()
            onBack()
        },
        onLeaveDismiss = viewModel::onLeaveDismiss,
        onCardClick = viewModel::onCardSelected,
        onAttackDrop = viewModel::onAttackDrop,
        onDefendDrop = viewModel::onDefendDrop,
        onBitoClick = viewModel::onBitoClick,
        onPassClick = viewModel::onPassClick,
        onTakeClick = viewModel::onTakeClick,
        onReadyClick = viewModel::onReadyClick,
        onToggleLoserCardsClick = viewModel::onToggleLoserCardsClick,
        onExitClick = {
            viewModel.onExitClick()
            onBack()
        },
        onFlyAnimationFinished = viewModel::onFlyAnimationFinished,
        onLobbyTimeoutDismiss = {
            viewModel.onLobbyTimeoutDismiss()
            onBack()
        },
        modifier = modifier,
        debugPanel = {
            DebugScenarioPanel(
                onScenarioSelected = viewModel::setScenario,
                onClearTable = viewModel::clearTable,
                onTakePending = viewModel::setTakePending,
                onOpponentTurn = viewModel::setOpponentTurn,
            )
        },
    )
}

@Composable
private fun DebugScenarioPanel(
    onScenarioSelected: (DebugScenario) -> Unit,
    onClearTable: () -> Unit,
    onTakePending: () -> Unit,
    onOpponentTurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DebugScenario.entries.forEach { scenario ->
            FilterChip(
                selected = false,
                onClick = { onScenarioSelected(scenario) },
                label = { Text(text = scenario.label) },
            )
        }
        FilterChip(
            selected = false,
            onClick = onTakePending,
            label = { Text(text = "Беру") },
        )
        FilterChip(
            selected = false,
            onClick = onOpponentTurn,
            label = { Text(text = "Ход бота") },
        )
        FilterChip(
            selected = false,
            onClick = onClearTable,
            label = { Text(text = "Стол пуст") },
        )
    }
}

private val DebugScenario.label: String
    get() = when (this) {
        DebugScenario.LOBBY_WAITING -> "Лобби"
        DebugScenario.LOBBY_DISCONNECTED -> "Disconnect"
        DebugScenario.IN_PROGRESS -> "Игра"
        DebugScenario.FINISHED -> "Конец"
    }
