package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.GameUiState
import com.example.foolcardgame.presentation.game.WaitingPlayerUi
import com.example.foolcardgame.ui.screens.profile.AvatarPresets
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.TableGreen

@Composable
fun GameTableLayout(
    uiState: GameUiState,
    onCardClick: (String) -> Unit,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onReadyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = AccentTeal)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        when (uiState.phase) {
            com.example.foolcardgame.domain.model.GamePhase.LOBBY_WAITING -> {
                WaitingRoomContent(
                    players = uiState.waitingPlayers,
                    modifier = Modifier.weight(1f),
                )
            }
            com.example.foolcardgame.domain.model.GamePhase.IN_PROGRESS -> {
                OpponentsRow(
                    opponents = uiState.opponents,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckAndTrumpView(
                            deckCount = uiState.deckCount,
                            trump = uiState.trump,
                        )
                        TableCardsView(
                            tablePairs = uiState.tablePairs,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            com.example.foolcardgame.domain.model.GamePhase.FINISHED -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.resultMessage.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        if (uiState.phase == com.example.foolcardgame.domain.model.GamePhase.IN_PROGRESS) {
            PlayerHandView(
                hand = uiState.hand,
                selectedCardId = uiState.selectedCardId,
                onCardClick = onCardClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        GameActionBar(
            actions = uiState.actions,
            onBitoClick = onBitoClick,
            onPassClick = onPassClick,
            onReadyClick = onReadyClick,
        )
    }
}

@Composable
private fun WaitingRoomContent(
    players: List<WaitingPlayerUi>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Ожидание игроков",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(players, key = { it.id }) { player ->
            WaitingPlayerRow(player = player)
        }
    }
}

@Composable
private fun WaitingPlayerRow(
    player: WaitingPlayerUi,
    modifier: Modifier = Modifier,
) {
    val avatar = AvatarPresets.get(player.avatarId)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = avatar.emoji, style = MaterialTheme.typography.headlineSmall)
            Text(text = player.displayName, style = MaterialTheme.typography.titleMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (player.isReady) "Готов" else "Не готов",
                color = if (player.isReady) AccentTeal else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = if (player.isConnected) "Онлайн" else "Отключён",
                color = if (player.isConnected) AccentTeal else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
