package com.example.foolcardgame.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OnlineLobbyUiState(
    val displayName: String = UserProfile.DEFAULT_DISPLAY_NAME,
    val avatarId: Int = UserProfile.DEFAULT_AVATAR_ID,
    val friendsExpanded: Boolean = false,
    val isQuickMatching: Boolean = false,
    val isCreating: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface OnlineLobbyNavEvent {
    data class ToGame(val sessionId: String) : OnlineLobbyNavEvent
    data class ToWaiting(val sessionId: String, val playerId: String) : OnlineLobbyNavEvent
    data class ToJoinByCode(val displayName: String, val avatarId: Int) : OnlineLobbyNavEvent
}

class OnlineLobbyViewModel(
    private val gameClient: GameClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineLobbyUiState())
    val uiState: StateFlow<OnlineLobbyUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<OnlineLobbyNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<OnlineLobbyNavEvent> = _navEvents.asSharedFlow()

    private var quickMatchJob: Job? = null

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, errorMessage = null) }
    }

    fun onAvatarSelected(avatarId: Int) {
        _uiState.update { it.copy(avatarId = avatarId, errorMessage = null) }
    }

    fun onFriendsExpandToggle() {
        _uiState.update { it.copy(friendsExpanded = !it.friendsExpanded) }
    }

    fun onQuickMatchClick() {
        if (_uiState.value.isQuickMatching) return
        quickMatchJob?.cancel()
        quickMatchJob = viewModelScope.launch {
            _uiState.update { it.copy(isQuickMatching = true, errorMessage = null) }
            val name = trimmedName()
            val avatarId = _uiState.value.avatarId
            while (isActive) {
                val result = gameClient.quickMatch(name, avatarId)
                result
                    .onSuccess { sessionId ->
                        if (sessionId != null) {
                            _uiState.update { it.copy(isQuickMatching = false) }
                            _navEvents.emit(OnlineLobbyNavEvent.ToGame(sessionId))
                            return@launch
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isQuickMatching = false,
                                errorMessage = error.message ?: "Не удалось найти игру",
                            )
                        }
                        return@launch
                    }
                delay(GameClient.ROOM_POLL_INTERVAL_MS)
            }
        }
    }

    fun onCancelQuickMatch() {
        quickMatchJob?.cancel()
        quickMatchJob = null
        _uiState.update { it.copy(isQuickMatching = false) }
    }

    fun onCreatePrivateClick() {
        if (_uiState.value.isCreating || _uiState.value.isQuickMatching) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorMessage = null) }
            val result = gameClient.createPrivateGame(trimmedName(), _uiState.value.avatarId)
            _uiState.update { it.copy(isCreating = false) }
            result
                .onSuccess { created ->
                    _navEvents.emit(
                        OnlineLobbyNavEvent.ToWaiting(
                            sessionId = created.sessionId,
                            playerId = created.hostId,
                        ),
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Не удалось создать игру")
                    }
                }
        }
    }

    fun onJoinByCodeClick() {
        viewModelScope.launch {
            _navEvents.emit(
                OnlineLobbyNavEvent.ToJoinByCode(
                    displayName = trimmedName(),
                    avatarId = _uiState.value.avatarId,
                ),
            )
        }
    }

    private fun trimmedName(): String =
        _uiState.value.displayName.trim().ifBlank { UserProfile.DEFAULT_DISPLAY_NAME }
}

class OnlineLobbyViewModelFactory(
    private val gameClient: GameClient,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(OnlineLobbyViewModel::class.java))
        return OnlineLobbyViewModel(gameClient) as T
    }
}
