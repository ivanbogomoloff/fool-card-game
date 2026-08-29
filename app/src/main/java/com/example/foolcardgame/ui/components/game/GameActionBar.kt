package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.GameActionsUi
import com.example.foolcardgame.ui.components.common.PrimaryButton

@Composable
fun GameActionBar(
    actions: GameActionsUi,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onReadyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (actions.readyVisible) {
            PrimaryButton(
                text = "Готов",
                onClick = onReadyClick,
                modifier = Modifier.weight(1f),
            )
        } else {
            OutlinedButton(
                onClick = onPassClick,
                enabled = actions.passEnabled,
                modifier = Modifier.weight(1f),
            ) {
                androidx.compose.material3.Text(text = "Пас")
            }
            PrimaryButton(
                text = "Бито",
                onClick = onBitoClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
