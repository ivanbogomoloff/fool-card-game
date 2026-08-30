package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.presentation.game.OpponentRoleBanner
import com.example.foolcardgame.presentation.game.OpponentUi
import com.example.foolcardgame.ui.components.card.CardFace
import com.example.foolcardgame.ui.screens.profile.AvatarPresets
import com.example.foolcardgame.ui.theme.ReadyGreen
import com.example.foolcardgame.ui.theme.SoftCharcoal
import com.example.foolcardgame.ui.theme.SoftCoral

@Composable
fun OpponentsRow(
    opponents: List<OpponentUi>,
    phase: GamePhase,
    onOpponentAvatarBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        opponents.forEach { opponent ->
            OpponentItem(
                opponent = opponent,
                showNotReadyOutline = phase == GamePhase.LOBBY_WAITING && !opponent.isReady,
                onAvatarBoundsChanged = { bounds ->
                    onOpponentAvatarBoundsChanged(opponent.id, bounds)
                },
            )
        }
    }
}

@Composable
private fun OpponentItem(
    opponent: OpponentUi,
    showNotReadyOutline: Boolean,
    onAvatarBoundsChanged: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val avatar = AvatarPresets.get(opponent.avatarId)
    val visibleBacks = opponent.cardCount.coerceIn(0, 6)
    val (label, labelColor) = opponentRoleLabel(opponent)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (showNotReadyOutline) {
                        Modifier
                            .border(width = 2.dp, color = SoftCoral, shape = CircleShape)
                            .background(SoftCoral.copy(alpha = 0.12f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        onAvatarBoundsChanged(coordinates.boundsInRoot())
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = avatar.emoji, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
        if (!opponent.isConnected) {
            Text(
                text = "Нет сети",
                style = MaterialTheme.typography.labelSmall,
                color = SoftCoral,
            )
        }
        Box(
            modifier = Modifier
                .width((28 + (visibleBacks - 1).coerceAtLeast(0) * 10).dp)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            repeat(visibleBacks) { index ->
                CardFace(
                    card = null,
                    faceUp = false,
                    width = 28.dp,
                    height = 40.dp,
                    modifier = Modifier
                        .offset(x = (index * 10).dp)
                        .zIndex(index.toFloat()),
                )
            }
        }
        if (opponent.cardCount > 0) {
            Text(
                text = "${opponent.cardCount}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun opponentRoleLabel(opponent: OpponentUi): Pair<String, Color> = when (opponent.roleBanner) {
    OpponentRoleBanner.ATTACKING -> "Ходит ${opponent.displayName}" to ReadyGreen
    OpponentRoleBanner.DEFENDING -> "Отбивается ${opponent.displayName}" to SoftCoral
    OpponentRoleBanner.NONE -> opponent.displayName to SoftCharcoal
}
