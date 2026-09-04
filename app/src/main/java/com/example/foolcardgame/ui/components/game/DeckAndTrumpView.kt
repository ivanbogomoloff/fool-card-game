package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.components.card.CardFace
import com.example.foolcardgame.ui.theme.OpponentPlate

@Composable
fun DeckAndTrumpView(
    deckCount: Int,
    trump: CardUi?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = 120.dp, height = 110.dp),
        contentAlignment = Alignment.Center,
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
                            .align(Alignment.BottomCenter)
                            .offset(y = 4.dp),
                    )
                }
                CardFace(
                    card = null,
                    faceUp = false,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .rotate(90f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(OpponentPlate, CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = deckCount.toString(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
