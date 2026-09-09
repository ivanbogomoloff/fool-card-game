package com.example.foolcardgame.presentation.offline

import app.cash.turbine.test
import com.example.foolcardgame.data.client.LocalGameClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): OfflineSetupViewModel =
        OfflineSetupViewModel(LocalGameClient())

    @Test
    fun default_botReactionRangeIsOneToFiveSeconds() {
        val viewModel = viewModel()
        assertEquals(1, viewModel.uiState.value.botReactionMinSec)
        assertEquals(5, viewModel.uiState.value.botReactionMaxSec)
    }

    @Test
    fun onBotReactionRangeChanged_updatesUiState() {
        val viewModel = viewModel()

        viewModel.onBotReactionRangeChanged(minSec = 3, maxSec = 10)

        assertEquals(3, viewModel.uiState.value.botReactionMinSec)
        assertEquals(10, viewModel.uiState.value.botReactionMaxSec)
    }

    @Test
    fun onBotReactionRangeChanged_ignoresInvalidRange() {
        val viewModel = viewModel()

        viewModel.onBotReactionRangeChanged(minSec = 10, maxSec = 5)

        assertEquals(1, viewModel.uiState.value.botReactionMinSec)
        assertEquals(5, viewModel.uiState.value.botReactionMaxSec)
    }

    @Test
    fun onStartClick_emitsSessionId() = runTest {
        val viewModel = viewModel()

        viewModel.navigateToGame.test {
            viewModel.onStartClick()
            val sessionId = awaitItem()
            assertTrue(sessionId.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isStarting)
    }
}
