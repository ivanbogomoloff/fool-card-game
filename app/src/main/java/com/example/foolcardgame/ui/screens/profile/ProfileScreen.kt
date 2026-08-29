package com.example.foolcardgame.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.profile.ProfileUiState
import com.example.foolcardgame.ui.components.common.PrimaryButton
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.FoolCardGameTheme
import com.example.foolcardgame.ui.theme.TableGreen
import com.example.foolcardgame.ui.theme.TrumpGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onDisplayNameChange: (String) -> Unit,
    onAvatarSelected: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onBack: () -> Unit,
    onSnackbarShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onSnackbarShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = "Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TableGreen,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = AccentTeal,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = TableGreen,
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AccentTeal)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Аватар",
                style = MaterialTheme.typography.titleMedium,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(AvatarPresets.options, key = { it.id }) { avatar ->
                    AvatarItem(
                        avatar = avatar,
                        selected = avatar.id == uiState.avatarId,
                        onClick = { onAvatarSelected(avatar.id) },
                    )
                }
            }

            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Имя профиля") },
                singleLine = true,
                isError = uiState.error != null,
                supportingText = uiState.error?.let { error ->
                    { Text(text = error) }
                },
            )

            PrimaryButton(
                text = if (uiState.isSaving) "Сохранение..." else "Сохранить",
                onClick = onSaveClick,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AvatarItem(
    avatar: AvatarPresets.AvatarOption,
    selected: Boolean,
    onClick: () -> Unit,
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatar.emoji,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    FoolCardGameTheme {
        ProfileScreen(
            uiState = ProfileUiState(
                displayName = "Иван",
                avatarId = 1,
                isLoading = false,
            ),
            onDisplayNameChange = {},
            onAvatarSelected = {},
            onSaveClick = {},
            onBack = {},
            onSnackbarShown = {},
        )
    }
}
