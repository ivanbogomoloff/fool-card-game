package com.example.foolcardgame.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.client.GameClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class GameViewModel(
    protected val gameClient: GameClient,
    protected val sessionId: GameSessionId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gameClient.observeState(sessionId).collect { dto ->
                _uiState.update { current ->
                    GameUiStateMapper.map(dto).copy(
                        selectedCardId = current.selectedCardId,
                        showLeaveDialog = current.showLeaveDialog,
                    )
                }
            }
        }
    }

    fun onCardSelected(cardId: String) {
        _uiState.update {
            it.copy(selectedCardId = if (it.selectedCardId == cardId) null else cardId)
        }
    }

    fun onBitoClick() {
        viewModelScope.launch { gameClient.bito(sessionId) }
    }

    fun onPassClick() {
        viewModelScope.launch { gameClient.pass(sessionId) }
    }

    fun onReadyClick() {
        viewModelScope.launch { gameClient.ready(sessionId) }
    }

    fun onBackClick() {
        _uiState.update { it.copy(showLeaveDialog = true) }
    }

    fun onLeaveConfirm() {
        viewModelScope.launch {
            gameClient.leaveSession(sessionId)
            _uiState.update { it.copy(showLeaveDialog = false) }
        }
    }

    fun onLeaveDismiss() {
        _uiState.update { it.copy(showLeaveDialog = false) }
    }
}
