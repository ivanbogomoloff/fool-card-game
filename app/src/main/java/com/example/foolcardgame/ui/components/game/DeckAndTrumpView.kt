package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.components.card.CardFace

@Composable
fun DeckAndTrumpView(
    deckCount: Int,
    trump: CardUi?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = 100.dp, height = 100.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when (deckDisplayMode(deckCount)) {
            DeckDisplayMode.Hidden -> Unit
            DeckDisplayMode.TrumpOnly -> {
                if (trump != null) {
                    CardFace(
                        card = trump,
                        faceUp = true,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            DeckDisplayMode.StackWithTrump -> {
                if (trump != null) {
                    CardFace(
                        card = trump,
                        faceUp = true,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 28.dp)
                            .rotate(90f),
                    )
                }
                CardFace(
                    card = null,
                    faceUp = false,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}
