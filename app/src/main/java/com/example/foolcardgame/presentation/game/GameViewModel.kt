package com.example.foolcardgame.presentation.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.api.dto.GameSessionId
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
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

    fun onAttackDrop(cardId: String) {
        playCardById(cardId, targetPairId = null)
    }

    fun onDefendDrop(cardId: String, pairId: Int) {
        playCardById(cardId, targetPairId = pairId)
    }

    fun onBitoClick() {
        viewModelScope.launch { gameClient.bito(sessionId) }
    }

    fun onPassClick() {
        viewModelScope.launch { gameClient.pass(sessionId) }
    }

    fun onTakeClick() {
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

    private fun playCardById(cardId: String, targetPairId: Int?) {
        val card = cardId.toCardOrNull() ?: return
        viewModelScope.launch {
            gameClient.playCard(sessionId, card, targetPairId)
            _uiState.update { it.copy(selectedCardId = null) }
        }
    }
}

internal fun String.toCardOrNull(): Card? {
    val parts = split('_')
    if (parts.size != 2) return null
    val suit = runCatching { Suit.valueOf(parts[0]) }.getOrNull() ?: return null
    val rank = runCatching { Rank.valueOf(parts[1]) }.getOrNull() ?: return null
    return Card(suit = suit, rank = rank)
}
