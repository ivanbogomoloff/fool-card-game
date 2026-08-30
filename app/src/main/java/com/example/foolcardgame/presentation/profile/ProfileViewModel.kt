package com.example.foolcardgame.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.repository.ProfileRepository
import com.example.foolcardgame.data.repository.SaveProfileResult
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
            val state = _uiState.value
            repository.saveProfile(
                UserProfile(
                    displayName = state.displayName.trim().ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME },
                    avatarId = state.avatarId,
                    soundsEnabled = enabled,
                ),
            )
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
                UserProfile(
                    displayName = trimmedName,
                    avatarId = state.avatarId,
                    soundsEnabled = state.soundsEnabled,
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
}
