package com.example.foolcardgame.presentation.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foolcardgame.data.client.LocalGameClient
import com.example.foolcardgame.data.repository.ProfileRepository

class OfflineSetupViewModelFactory(
    private val gameClient: LocalGameClient,
    private val profileRepository: ProfileRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OfflineSetupViewModel::class.java)) {
            return OfflineSetupViewModel(gameClient, profileRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
