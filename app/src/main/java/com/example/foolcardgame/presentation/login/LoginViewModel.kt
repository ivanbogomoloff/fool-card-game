package com.example.foolcardgame.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.foolcardgame.data.client.GameClient
import com.example.foolcardgame.data.local.AccountCredentialsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class LoginViewModel(
    private val gameClient: GameClient,
    private val credentialsStore: AccountCredentialsStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(
            username = credentialsStore?.load()?.username.orEmpty(),
        ),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _navigateToLobby = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToLobby: SharedFlow<Unit> = _navigateToLobby.asSharedFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onLoginClick() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val username = _uiState.value.username.trim()
            // Пароль с UI не передаём: RemoteGameClient подставит из Keystore при совпадении имени.
            val result = gameClient.login(username)
            result
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _navigateToLobby.emit(Unit)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Не удалось войти",
                        )
                    }
                }
        }
    }
}

class LoginViewModelFactory(
    private val gameClient: GameClient,
    private val credentialsStore: AccountCredentialsStore? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LoginViewModel::class.java))
        return LoginViewModel(gameClient, credentialsStore) as T
    }
}
