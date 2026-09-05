package com.example.foolcardgame.presentation.profile

import com.example.foolcardgame.domain.model.CardTheme
import com.example.foolcardgame.domain.model.ThemeMode

data class ProfileUiState(
    val avatarId: Int = 0,
    val soundsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cardTheme: CardTheme = CardTheme.ILLUSTRATED,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val error: String? = null,
)
