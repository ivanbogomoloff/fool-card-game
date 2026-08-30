package com.example.foolcardgame.presentation.offline

data class OfflineSetupUiState(
    val botCount: Int = 1,
    val botReactionMinSec: Int = 1,
    val botReactionMaxSec: Int = 5,
    val isStarting: Boolean = false,
    val errorMessage: String? = null,
)
