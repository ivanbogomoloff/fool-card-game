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
                        displayName = profile.displayName,
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

    fun onDisplayNameChange(name: String) {
        _uiState.update { it.copy(displayName = name, error = null) }
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
        val state = _uiState.value
        val trimmedName = state.displayName.trim()
        if (trimmedName.isEmpty()) {
            _uiState.update { it.copy(error = "Введите имя профиля") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, snackbarMessage = null) }
            val result = repository.saveProfile(
                _uiState.value.toUserProfile(
                    displayName = trimmedName,
                ),
            )
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
        displayName: String? = null,
        avatarId: Int? = null,
        soundsEnabled: Boolean? = null,
        themeMode: ThemeMode? = null,
        cardTheme: CardTheme? = null,
    ): UserProfile = UserProfile(
        displayName = displayName ?: this.displayName.trim().ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME },
        avatarId = avatarId ?: this.avatarId,
        soundsEnabled = soundsEnabled ?: this.soundsEnabled,
        themeMode = themeMode ?: this.themeMode,
        cardTheme = cardTheme ?: this.cardTheme,
    )
}
