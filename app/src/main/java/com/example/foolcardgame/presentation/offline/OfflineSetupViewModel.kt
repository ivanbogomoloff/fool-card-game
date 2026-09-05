package com.example.foolcardgame.presentation.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.client.LocalGameClient
import com.example.foolcardgame.data.repository.ProfileRepository
import com.example.foolcardgame.domain.model.GameConfig
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OfflineSetupViewModel(
    private val gameClient: LocalGameClient,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineSetupUiState())
    val uiState: StateFlow<OfflineSetupUiState> = _uiState.asStateFlow()

    private val _navigateToGame = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToGame: SharedFlow<String> = _navigateToGame.asSharedFlow()

    fun onBotCountSelected(count: Int) {
        if (count !in 1..3) return
        _uiState.update { it.copy(botCount = count, errorMessage = null) }
    }

    fun onBotReactionRangeChanged(minSec: Int, maxSec: Int) {
        val min = minSec.coerceIn(1, 30)
        val max = maxSec.coerceIn(1, 30)
        if (min > max) return
        _uiState.update {
            it.copy(
                botReactionMinSec = min,
                botReactionMaxSec = max,
                errorMessage = null,
            )
        }
    }

    fun onStartClick() {
        if (_uiState.value.isStarting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isStarting = true, errorMessage = null) }
            runCatching {
                val profile = profileRepository.observeProfile().first()
                val reactionMinSec = _uiState.value.botReactionMinSec
                val reactionMaxSec = _uiState.value.botReactionMaxSec
                val sessionId = gameClient.createSession(
                    GameConfig(
                        botCount = _uiState.value.botCount,
                        humanAvatarId = profile.avatarId,
                        seed = Random.Default.nextLong(),
                        botThinkMinMs = reactionMinSec * 1_000L,
                        botThinkMaxMs = reactionMaxSec * 1_000L,
                    ),
                )
                _navigateToGame.emit(sessionId)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isStarting = false,
                        errorMessage = error.message ?: "Не удалось создать игру",
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(isStarting = false) }
            }
        }
    }
}
