package com.example.foolcardgame.presentation.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foolcardgame.data.client.LocalGameClient

class OfflineSetupViewModelFactory(
    private val gameClient: LocalGameClient,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OfflineSetupViewModel::class.java)) {
            return OfflineSetupViewModel(gameClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
