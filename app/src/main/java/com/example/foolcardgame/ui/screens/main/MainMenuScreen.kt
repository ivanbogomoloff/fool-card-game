package com.example.foolcardgame.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.ui.components.common.PrimaryButton
import com.example.foolcardgame.ui.theme.FoolCardGameTheme

@Composable
fun MainMenuScreen(
    onOfflineClick: () -> Unit,
    onOnlineClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Дурак",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 48.dp),
        )
        MenuButton(text = "Игра оффлайн", onClick = onOfflineClick)
        MenuButton(text = "Игра по сети", onClick = onOnlineClick)
        MenuButton(text = "Настройки", onClick = onSettingsClick)
    }
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryButton(
        text = text,
        onClick = onClick,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun MainMenuScreenPreview() {
    FoolCardGameTheme {
        MainMenuScreen(
            onOfflineClick = {},
            onOnlineClick = {},
            onSettingsClick = {},
        )
    }
}
