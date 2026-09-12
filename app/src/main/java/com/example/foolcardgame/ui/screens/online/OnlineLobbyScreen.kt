package com.example.foolcardgame.ui.screens.online

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.foolcardgame.presentation.online.OnlineLobbyUiState
import com.example.foolcardgame.ui.components.common.PrimaryButton
import com.example.foolcardgame.ui.components.common.WaitingDots
import com.example.foolcardgame.ui.screens.profile.AvatarPresets
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.FoolCardGameTheme
import com.example.foolcardgame.ui.theme.TrumpGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLobbyScreen(
    uiState: OnlineLobbyUiState,
    onAvatarSelected: (Int) -> Unit,
    onQuickMatchClick: () -> Unit,
    onCancelQuickMatch: () -> Unit,
    onFriendsExpandToggle: () -> Unit,
    onCreatePrivateClick: () -> Unit,
    onJoinByCodeClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Игра по сети") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(text = "Профиль", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = {},
                    label = { Text("Имя") },
                    singleLine = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarPresets.options.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { avatar ->
                                LobbyAvatarItem(
                                    avatar = avatar,
                                    selected = avatar.id == uiState.avatarId,
                                    onClick = { onAvatarSelected(avatar.id) },
                                    enabled = !uiState.isQuickMatching,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - row.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
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
            item {
                PrimaryButton(
                    text = "Быстрая игра",
                    onClick = onQuickMatchClick,
                    enabled = !uiState.isQuickMatching && !uiState.isCreating,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !uiState.isQuickMatching,
                            onClick = onFriendsExpandToggle,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Игра с друзьями",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        imageVector = if (uiState.friendsExpanded) {
                            Icons.Filled.ExpandLess
                        } else {
                            Icons.Filled.ExpandMore
                        },
                        contentDescription = null,
                        tint = AccentTeal,
                    )
                }
            }
            if (uiState.friendsExpanded) {
                item {
                    PrimaryButton(
                        text = if (uiState.isCreating) "Создание…" else "Создать игру",
                        onClick = onCreatePrivateClick,
                        enabled = !uiState.isQuickMatching && !uiState.isCreating,
                    )
                }
                item {
                    PrimaryButton(
                        text = "Войти по коду",
                        onClick = onJoinByCodeClick,
                        enabled = !uiState.isQuickMatching && !uiState.isCreating,
                    )
                }
            }
        }
    }

    if (uiState.isQuickMatching) {
        Dialog(
            onDismissRequest = onCancelQuickMatch,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(text = "Ожидайте", style = MaterialTheme.typography.titleLarge)
                WaitingDots(color = AccentTeal)
                if (uiState.queueWaitingCount > 0) {
                    Text(
                        text = "В очереди: ${uiState.queueWaitingCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PrimaryButton(text = "Отмена", onClick = onCancelQuickMatch)
            }
        }
    }
}

@Composable
private fun LobbyAvatarItem(
    avatar: AvatarPresets.AvatarOption,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(avatar.backgroundColor)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) TrumpGold else AccentTeal.copy(alpha = 0f),
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = avatar.emoji, style = MaterialTheme.typography.headlineSmall)
    }
}

@Preview(showBackground = true)
@Composable
private fun OnlineLobbyScreenPreview() {
    FoolCardGameTheme {
        OnlineLobbyScreen(
            uiState = OnlineLobbyUiState(
                displayName = "Иван",
                avatarId = 1,
                friendsExpanded = true,
            ),
            onAvatarSelected = {},
            onQuickMatchClick = {},
            onCancelQuickMatch = {},
            onFriendsExpandToggle = {},
            onCreatePrivateClick = {},
            onJoinByCodeClick = {},
            onBack = {},
        )
    }
}
