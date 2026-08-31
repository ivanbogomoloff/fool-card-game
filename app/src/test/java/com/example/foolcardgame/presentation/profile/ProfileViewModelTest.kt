package com.example.foolcardgame.presentation.profile

import com.example.foolcardgame.data.api.FakeProfileApi
import com.example.foolcardgame.data.local.InMemoryProfileLocalStore
import com.example.foolcardgame.data.repository.ProfileRepository
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
import org.junit.Assert.assertNotNull
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
    fun init_loadsProfileFromRepository() = runTest {
        val repository = ProfileRepository(
            localStore = InMemoryProfileLocalStore(
                initial = UserProfile(displayName = "Тест", avatarId = 3),
            ),
            api = FakeProfileApi(),
        )

        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        assertEquals("Тест", viewModel.uiState.value.displayName)
        assertEquals(3, viewModel.uiState.value.avatarId)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun saveProfile_updatesStateAndShowsSuccessMessage() = runTest {
        val api = FakeProfileApi()
        val repository = ProfileRepository(
            localStore = InMemoryProfileLocalStore(),
            api = api,
        )
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onDisplayNameChange("Алекс")
        viewModel.onAvatarSelected(2)
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("Профиль сохранён", viewModel.uiState.value.snackbarMessage)
        assertEquals("Алекс", api.lastSavedProfile?.displayName)
        assertEquals(2, api.lastSavedProfile?.avatarId)
    }

    @Test
    fun saveProfile_showsErrorWhenNameIsBlank() = runTest {
        val viewModel = ProfileViewModel(
            ProfileRepository(InMemoryProfileLocalStore(), FakeProfileApi()),
        )
        advanceUntilIdle()

        viewModel.onDisplayNameChange("   ")
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("Введите имя профиля", viewModel.uiState.value.error)
    }

    @Test
    fun saveProfile_showsApiErrorStateWhenApiFails() = runTest {
        val repository = ProfileRepository(
            localStore = InMemoryProfileLocalStore(),
            api = FakeProfileApi(shouldFail = true),
        )
        val viewModel = ProfileViewModel(repository)
        advanceUntilIdle()

        viewModel.onDisplayNameChange("Мария")
        viewModel.saveProfile()
        advanceUntilIdle()

        assertEquals("Сохранено локально", viewModel.uiState.value.snackbarMessage)
        assertNotNull(viewModel.uiState.value.error)
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
}
