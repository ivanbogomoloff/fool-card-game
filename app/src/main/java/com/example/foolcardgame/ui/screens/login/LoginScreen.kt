package com.example.foolcardgame.ui.screens.login

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
fun LoginScreen(
    onLoginClick: () -> Unit,
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
        )
        Text(
            text = "Подкидной",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 48.dp),
        )
        PrimaryButton(
            text = "Войти",
            onClick = onLoginClick,
            fillWidth = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    FoolCardGameTheme {
        LoginScreen(onLoginClick = {})
    }
}
