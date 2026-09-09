package com.example.foolcardgame.presentation.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinPrivateUiState(
    val code: String = "",
    val isJoining: Boolean = false,
    val errorMessage: String? = null,
)

data class JoinPrivateNavTarget(
    val sessionId: String,
    val playerId: String,
)

class JoinPrivateViewModel(
    private val gameClient: GameClient,
    private val displayName: String,
    private val avatarId: Int,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinPrivateUiState())
    val uiState: StateFlow<JoinPrivateUiState> = _uiState.asStateFlow()

    private val _navigateToWaiting = MutableSharedFlow<JoinPrivateNavTarget>(extraBufferCapacity = 1)
    val navigateToWaiting: SharedFlow<JoinPrivateNavTarget> = _navigateToWaiting.asSharedFlow()

    fun onCodeChange(value: String) {
        _uiState.update {
            it.copy(code = value.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8), errorMessage = null)
        }
    }

    fun onJoinClick() {
        if (_uiState.value.isJoining) return
        val code = _uiState.value.code.trim()
        if (code.length < 4) {
            _uiState.update { it.copy(errorMessage = "Введите код комнаты") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isJoining = true, errorMessage = null) }
            val name = displayName.trim().ifBlank { UserProfile.DEFAULT_DISPLAY_NAME }
            gameClient.joinByCode(code, name, avatarId)
                .onSuccess { (sessionId, playerId) ->
                    _uiState.update { it.copy(isJoining = false) }
                    _navigateToWaiting.emit(JoinPrivateNavTarget(sessionId, playerId))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isJoining = false,
                            errorMessage = error.message ?: "Не удалось войти",
                        )
                    }
                }
        }
    }
}

class JoinPrivateViewModelFactory(
    private val gameClient: GameClient,
    private val displayName: String,
    private val avatarId: Int,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(JoinPrivateViewModel::class.java))
        return JoinPrivateViewModel(gameClient, displayName, avatarId) as T
    }
}
