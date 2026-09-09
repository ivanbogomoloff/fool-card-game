package com.example.foolcardgame.presentation.profile

import com.example.foolcardgame.data.api.FakeProfileApi
import com.example.foolcardgame.data.local.InMemoryProfileLocalStore
import com.example.foolcardgame.data.repository.ProfileRepository
import com.example.foolcardgame.domain.model.CardTheme
import com.example.foolcardgame.domain.model.ThemeMode
import com.example.foolcardgame.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsSettingsFromRepository() = runTest {
        val repository = ProfileRepository(
            localStore = InMemoryProfileLocalStore(
                initial = UserProfile(
                    displayName = "Тест",
                    avatarId = 3,
                    soundsEnabled = false,
                    themeMode = ThemeMode.DARK,
                ),
            ),
            api = FakeProfileApi(),
        )

        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.soundsEnabled)
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun onSoundsEnabledChange_savesImmediately() = runTest {
        val localStore = InMemoryProfileLocalStore()
        val repository = ProfileRepository(localStore, FakeProfileApi())
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onSoundsEnabledChange(false)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.soundsEnabled)
        assertEquals(false, localStore.observeProfile().first().soundsEnabled)
    }

    @Test
    fun onThemeModeChange_savesImmediately() = runTest {
        val localStore = InMemoryProfileLocalStore()
        val repository = ProfileRepository(localStore, FakeProfileApi())
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onThemeModeChange(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertEquals(ThemeMode.DARK, localStore.observeProfile().first().themeMode)
    }

    @Test
    fun onCardThemeChange_savesImmediately() = runTest {
        val localStore = InMemoryProfileLocalStore()
        val repository = ProfileRepository(localStore, FakeProfileApi())
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onCardThemeChange(CardTheme.MINIMAL)
        advanceUntilIdle()

        assertEquals(CardTheme.MINIMAL, viewModel.uiState.value.cardTheme)
        assertEquals(CardTheme.MINIMAL, localStore.observeProfile().first().cardTheme)
    }
}
