package com.example.foolcardgame.presentation.profile

import com.example.foolcardgame.domain.model.ThemeMode

data class ProfileUiState(
    val displayName: String = "",
    val avatarId: Int = 0,
    val soundsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val error: String? = null,
)
