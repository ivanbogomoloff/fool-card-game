package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.GameActionsUi
import com.example.foolcardgame.presentation.game.HandPrimaryAction
import com.example.foolcardgame.ui.components.common.PrimaryButton

@Composable
fun GameActionBar(
    actions: GameActionsUi,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onTakeClick: () -> Unit,
    onReadyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.primary == HandPrimaryAction.NONE) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (actions.primary) {
            HandPrimaryAction.READY -> PrimaryButton(
                text = "Готов",
                onClick = onReadyClick,
                fillWidth = false,
            )
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
