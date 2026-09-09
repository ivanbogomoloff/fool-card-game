package com.example.foolcardgame.ui.screens.online

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.data.api.dto.RoomPlayerDto
import com.example.foolcardgame.presentation.online.WaitingRoomUiState
import com.example.foolcardgame.ui.components.common.PrimaryButton
import com.example.foolcardgame.ui.components.common.WaitingDots
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.FoolCardGameTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingRoomScreen(
    uiState: WaitingRoomUiState,
    onKickClick: (String) -> Unit,
    onStartClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun copyAccessCode() {
        val code = uiState.accessCode
        if (code.isBlank()) return
        clipboardManager.setText(AnnotatedString(code))
        scope.launch {
            snackbarHostState.showSnackbar("Код скопирован")
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Комната ожидания") },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        if (uiState.isLoading && uiState.players.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = AccentTeal)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "Ожидание игроков", style = MaterialTheme.typography.titleMedium)
                    WaitingDots(color = AccentTeal)
                }
            }
            if (uiState.accessCode.isNotBlank()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Код: ${uiState.accessCode}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = AccentTeal,
                            modifier = Modifier.clickable(onClick = ::copyAccessCode),
                        )
                        IconButton(onClick = ::copyAccessCode) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Скопировать код",
                                tint = AccentTeal,
                            )
                        }
                    }
                }
            }
            uiState.errorMessage?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(uiState.players, key = { it.id }) { player ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = buildString {
                            append(player.displayName)
                            if (player.isHost) append(" (хост)")
                            if (player.id == uiState.playerId) append(" — вы")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (uiState.isHost && !player.isHost) {
                        TextButton(onClick = { onKickClick(player.id) }) {
                            Text("Удалить")
                        }
                    }
                }
            }
            if (uiState.isHost) {
                item {
                    PrimaryButton(
                        text = if (uiState.isStarting) "Запуск…" else "Начать игру",
                        onClick = onStartClick,
                        enabled = !uiState.isStarting && uiState.players.size >= 2,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaitingRoomScreenPreview() {
    FoolCardGameTheme {
        WaitingRoomScreen(
            uiState = WaitingRoomUiState(
                sessionId = "s1",
                playerId = "p1",
                accessCode = "ABCD12",
                hostId = "p1",
                isHost = true,
                isLoading = false,
                players = listOf(
                    RoomPlayerDto("p1", "Иван", 0, true),
                    RoomPlayerDto("p2", "Мария", 1, false),
                ),
            ),
            onKickClick = {},
            onStartClick = {},
            onBack = {},
        )
    }
}
