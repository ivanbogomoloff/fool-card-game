package com.example.foolcardgame.ui.screens.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.login.LoginUiState
import com.example.foolcardgame.ui.components.common.PrimaryButton
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.FoolCardGameTheme

@Composable
fun LoginScreen(
    uiState: LoginUiState = LoginUiState(),
    onUsernameChange: (String) -> Unit = {},
    onLoginClick: () -> Unit,
    onBack: (() -> Unit)? = null,
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
            modifier = Modifier.padding(bottom = 32.dp),
        )
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Имя пользователя") },
            singleLine = true,
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )
        if (uiState.isLoading) {
            CircularProgressIndicator(color = AccentTeal)
        } else {
            PrimaryButton(
                text = "Войти",
                onClick = onLoginClick,
                fillWidth = false,
            )
        }
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        onBack?.let { back ->
            Text(
                text = "Назад",
                color = AccentTeal,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clickable(onClick = back),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    FoolCardGameTheme {
        LoginScreen(onLoginClick = {})
    }
}
