package com.example.foolcardgame.ui.screens.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.online.JoinPrivateUiState
import com.example.foolcardgame.ui.components.common.PrimaryButton
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.FoolCardGameTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinPrivateScreen(
    uiState: JoinPrivateUiState,
    onCodeChange: (String) -> Unit,
    onJoinClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Войти по коду") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = AccentTeal,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.code,
                onValueChange = onCodeChange,
                label = { Text("Код комнаты") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PrimaryButton(
                text = if (uiState.isJoining) "Вход…" else "Войти",
                onClick = onJoinClick,
                enabled = !uiState.isJoining,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinPrivateScreenPreview() {
    FoolCardGameTheme {
        JoinPrivateScreen(
            uiState = JoinPrivateUiState(code = "ABCD12"),
            onCodeChange = {},
            onJoinClick = {},
            onBack = {},
        )
    }
}
