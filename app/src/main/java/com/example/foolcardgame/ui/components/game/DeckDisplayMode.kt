package com.example.foolcardgame.ui.components.game

internal enum class DeckDisplayMode {
    Hidden,
    TrumpOnly,
    StackWithTrump,
}

internal fun deckDisplayMode(deckCount: Int): DeckDisplayMode = when {
    deckCount <= 0 -> DeckDisplayMode.Hidden
    deckCount == 1 -> DeckDisplayMode.TrumpOnly
    else -> DeckDisplayMode.StackWithTrump
}
