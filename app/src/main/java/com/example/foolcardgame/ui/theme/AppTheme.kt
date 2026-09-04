package com.example.foolcardgame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foolcardgame.di.AppGraph
import com.example.foolcardgame.domain.model.CardTheme
import com.example.foolcardgame.domain.model.ThemeMode
import kotlinx.coroutines.flow.map

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val repository = AppGraph.profileRepository(context)
    val themeMode by repository.observeProfile()
        .map { it.themeMode }
        .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val cardTheme by repository.observeProfile()
        .map { it.cardTheme }
        .collectAsStateWithLifecycle(initialValue = CardTheme.ILLUSTRATED)

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    CompositionLocalProvider(LocalCardTheme provides cardTheme) {
        FoolCardGameTheme(darkTheme = darkTheme, content = content)
    }
}
