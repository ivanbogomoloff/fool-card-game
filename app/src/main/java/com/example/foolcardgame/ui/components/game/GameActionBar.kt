package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.GameActionsUi
import com.example.foolcardgame.presentation.game.HandPrimaryAction
import com.example.foolcardgame.ui.components.common.PrimaryButton
import com.example.foolcardgame.ui.theme.ReadyGreen
import com.example.foolcardgame.ui.theme.SoftCharcoal

@Composable
fun GameActionBar(
    actions: GameActionsUi,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onTakeClick: () -> Unit,
    onReadyClick: () -> Unit,
    modifier: Modifier = Modifier,
    readySecondsLeft: Int? = null,
) {
    if (actions.primary == HandPrimaryAction.NONE) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (actions.primary) {
            HandPrimaryAction.READY -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton(
                        text = "Готов",
                        onClick = onReadyClick,
                        fillWidth = false,
                        containerColor = ReadyGreen,
                    )
                    if (readySecondsLeft != null) {
                        Text(
                            text = formatReadyTimer(readySecondsLeft),
                            style = MaterialTheme.typography.titleMedium,
                            color = SoftCharcoal,
                        )
                    }
                }
            }
            HandPrimaryAction.BITO -> PrimaryButton(
                text = "Бито",
                onClick = onBitoClick,
                fillWidth = false,
            )
            HandPrimaryAction.PASS -> PrimaryButton(
                text = "Пас",
                onClick = onPassClick,
                fillWidth = false,
            )
            HandPrimaryAction.TAKE -> PrimaryButton(
                text = "Беру",
                onClick = onTakeClick,
                fillWidth = false,
            )
            HandPrimaryAction.NONE -> Unit
        }
    }
}

internal fun formatReadyTimer(secondsLeft: Int): String {
    val clamped = secondsLeft.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
