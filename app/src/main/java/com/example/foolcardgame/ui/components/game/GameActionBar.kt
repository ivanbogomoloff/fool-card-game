package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.GameActionsUi
import com.example.foolcardgame.presentation.game.HandPrimaryAction
import com.example.foolcardgame.ui.theme.GameActionBlue
import com.example.foolcardgame.ui.theme.SoftCoral
import com.example.foolcardgame.ui.theme.TrumpGold

@Composable
fun GameActionBar(
    actions: GameActionsUi,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onTakeClick: () -> Unit,
    onReadyClick: () -> Unit,
    modifier: Modifier = Modifier,
    readySecondsLeft: Int? = null,
    turnSecondsLeft: Int? = null,
    isLocalPlayerTurn: Boolean = false,
    isLocalDefending: Boolean = false,
    isLocalAttacking: Boolean = false,
    finishedSummary: String? = null,
    canRevealLoserCards: Boolean = false,
    showLoserCards: Boolean = false,
    onToggleLoserCardsClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
) {
    val showStatusOnly = actions.primary == HandPrimaryAction.NONE &&
        (isLocalDefending || (isLocalAttacking && isLocalPlayerTurn))
    if (actions.primary == HandPrimaryAction.NONE && !showStatusOnly) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        when (actions.primary) {
            HandPrimaryAction.READY -> {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TableActionButton(text = "Готов", onClick = onReadyClick)
                    if (readySecondsLeft != null) {
                        Text(
                            text = formatReadyTimer(readySecondsLeft),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                }
            }
            HandPrimaryAction.WAITING_READY -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(Color.White, CircleShape),
                            )
                        }
                    }
                    Text(
                        text = "Ожидание готовности игроков",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            HandPrimaryAction.BITO -> {
                TurnStatus(
                    isLocalDefending = false,
                    isLocalAttacking = true,
                    turnSecondsLeft = turnSecondsLeft,
                    modifier = Modifier.align(Alignment.Center),
                )
                TableActionButton(
                    text = "Бито",
                    onClick = onBitoClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            HandPrimaryAction.PASS -> {
                TurnStatus(
                    isLocalDefending = false,
                    isLocalAttacking = false,
                    turnSecondsLeft = turnSecondsLeft,
                    modifier = Modifier.align(Alignment.Center),
                )
                TableActionButton(
                    text = "Бито",
                    onClick = onPassClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            HandPrimaryAction.TAKE -> {
                TurnStatus(
                    isLocalDefending = isLocalDefending,
                    isLocalAttacking = false,
                    turnSecondsLeft = turnSecondsLeft,
                    modifier = Modifier.align(Alignment.Center),
                )
                TableActionButton(
                    text = "Беру",
                    onClick = onTakeClick,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            HandPrimaryAction.FINISHED -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (finishedSummary != null) {
                        Text(
                            text = finishedSummary,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (canRevealLoserCards) {
                            TableActionButton(
                                text = if (showLoserCards) {
                                    "Скрыть карты Дурака"
                                } else {
                                    "Показать карты Дурака"
                                },
                                onClick = onToggleLoserCardsClick,
                            )
                        }
                        TableActionButton(text = "Выйти", onClick = onExitClick)
                    }
                }
            }
            HandPrimaryAction.NONE -> {
                TurnStatus(
                    isLocalDefending = isLocalDefending,
                    isLocalAttacking = isLocalAttacking && isLocalPlayerTurn,
                    turnSecondsLeft = if (isLocalPlayerTurn) turnSecondsLeft else null,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun TurnStatus(
    isLocalDefending: Boolean,
    isLocalAttacking: Boolean,
    turnSecondsLeft: Int?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLocalDefending || isLocalAttacking) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
        if (isLocalDefending) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "Вы отбиваетесь",
                tint = TrumpGold,
                modifier = Modifier.size(28.dp),
            )
        } else if (isLocalAttacking) {
            Icon(
                imageVector = Icons.Filled.SportsMma,
                contentDescription = "Ваш ход",
                tint = SoftCoral,
                modifier = Modifier.size(28.dp),
            )
        }
        if (turnSecondsLeft != null) {
            Text(
                text = formatReadyTimer(turnSecondsLeft),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TableActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GameActionBlue,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

internal fun formatReadyTimer(secondsLeft: Int): String {
    val clamped = secondsLeft.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
