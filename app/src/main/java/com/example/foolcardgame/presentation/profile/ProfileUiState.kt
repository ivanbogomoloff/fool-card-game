package com.example.foolcardgame.presentation.profile

data class ProfileUiState(
    val displayName: String = "",
    val avatarId: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
    val error: String? = null,
)
