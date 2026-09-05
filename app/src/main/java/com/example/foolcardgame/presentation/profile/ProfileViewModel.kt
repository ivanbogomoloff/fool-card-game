package com.example.foolcardgame.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.repository.ProfileRepository
import com.example.foolcardgame.data.repository.SaveProfileResult
import com.example.foolcardgame.domain.model.CardTheme
import com.example.foolcardgame.domain.model.ThemeMode
import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProfile().collect { profile ->
                _uiState.update {
                    it.copy(
                        avatarId = profile.avatarId,
                        soundsEnabled = profile.soundsEnabled,
                        themeMode = profile.themeMode,
                        cardTheme = profile.cardTheme,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onAvatarSelected(avatarId: Int) {
        _uiState.update { it.copy(avatarId = avatarId, error = null) }
    }

    fun onSoundsEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(soundsEnabled = enabled, error = null) }
        viewModelScope.launch {
            repository.saveProfile(_uiState.value.toUserProfile(soundsEnabled = enabled))
        }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode, error = null) }
        viewModelScope.launch {
            repository.saveProfile(_uiState.value.toUserProfile(themeMode = mode))
        }
    }

    fun onCardThemeChange(theme: CardTheme) {
        _uiState.update { it.copy(cardTheme = theme, error = null) }
        viewModelScope.launch {
            repository.saveProfile(_uiState.value.toUserProfile(cardTheme = theme))
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, snackbarMessage = null) }
            val result = repository.saveProfile(_uiState.value.toUserProfile())
            _uiState.update {
                when (result) {
                    SaveProfileResult.Success -> it.copy(
                        isSaving = false,
                        snackbarMessage = "Профиль сохранён",
                    )
                    is SaveProfileResult.SavedLocallyApiFailed -> it.copy(
                        isSaving = false,
                        snackbarMessage = "Сохранено локально",
                        error = result.message ?: "Не удалось отправить на сервер",
                    )
                }
            }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun ProfileUiState.toUserProfile(
        avatarId: Int? = null,
        soundsEnabled: Boolean? = null,
        themeMode: ThemeMode? = null,
        cardTheme: CardTheme? = null,
    ): UserProfile = UserProfile(
        // Display name is for online accounts later; settings do not edit it.
        displayName = UserProfile.DEFAULT_DISPLAY_NAME,
        avatarId = avatarId ?: this.avatarId,
        soundsEnabled = soundsEnabled ?: this.soundsEnabled,
        themeMode = themeMode ?: this.themeMode,
        cardTheme = cardTheme ?: this.cardTheme,
    )
}
