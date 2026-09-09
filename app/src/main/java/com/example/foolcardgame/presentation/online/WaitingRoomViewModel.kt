package com.example.foolcardgame.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.api.dto.RoomPlayerDto
import com.example.foolcardgame.data.client.GameClient
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

data class WaitingRoomUiState(
    val sessionId: String,
    val playerId: String,
    val accessCode: String = "",
    val hostId: String = "",
    val players: List<RoomPlayerDto> = emptyList(),
    val isHost: Boolean = false,
    val isLoading: Boolean = true,
    val isStarting: Boolean = false,
    val errorMessage: String? = null,
)

class WaitingRoomViewModel(
    private val gameClient: GameClient,
    sessionId: String,
    playerId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        WaitingRoomUiState(sessionId = sessionId, playerId = playerId),
    )
    val uiState: StateFlow<WaitingRoomUiState> = _uiState.asStateFlow()

    private val _navigateToGame = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToGame: SharedFlow<String> = _navigateToGame.asSharedFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    fun onKickClick(targetPlayerId: String) {
        val state = _uiState.value
        if (!state.isHost || state.isStarting) return
        viewModelScope.launch {
            gameClient.kickPlayer(state.sessionId, targetPlayerId)
                .onSuccess { refreshOnce() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Не удалось удалить игрока")
                    }
                }
        }
    }

    fun onStartClick() {
        val state = _uiState.value
        if (!state.isHost || state.isStarting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStarting = true, errorMessage = null) }
            gameClient.startGame(state.sessionId)
                .onSuccess {
                    pollJob?.cancel()
                    _navigateToGame.emit(state.sessionId)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isStarting = false,
                            errorMessage = error.message ?: "Не удалось начать игру",
                        )
                    }
                }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val started = refreshOnce()
                if (started) return@launch
                delay(GameClient.ROOM_POLL_INTERVAL_MS)
            }
        }
    }

    /** @return true, если матч уже стартовал. */
    private suspend fun refreshOnce(): Boolean {
        val sessionId = _uiState.value.sessionId
        val playerId = _uiState.value.playerId
        var started = false
        gameClient.getRoom(sessionId)
            .onSuccess { room ->
                _uiState.update {
                    it.copy(
                        accessCode = room.accessCode,
                        hostId = room.hostId,
                        players = room.players,
                        isHost = room.hostId == playerId,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                if (room.started) {
                    pollJob?.cancel()
                    _navigateToGame.emit(room.sessionId)
                    started = true
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Не удалось загрузить комнату",
                    )
                }
            }
        return started
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}

class WaitingRoomViewModelFactory(
    private val gameClient: GameClient,
    private val sessionId: String,
    private val playerId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WaitingRoomViewModel::class.java))
        return WaitingRoomViewModel(gameClient, sessionId, playerId) as T
    }
}
