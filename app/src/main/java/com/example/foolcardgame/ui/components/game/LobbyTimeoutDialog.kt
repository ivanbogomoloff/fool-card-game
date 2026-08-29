package com.example.foolcardgame.ui.components.game

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LobbyTimeoutDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = "Вас исключили из игры") },
        text = {
            Text(text = "Нужно было нажать кнопку «Готов».")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "ОК")
            }
        },
    )
}
