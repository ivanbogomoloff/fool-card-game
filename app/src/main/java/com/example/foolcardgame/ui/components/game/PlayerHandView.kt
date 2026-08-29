package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.components.card.CardFace

@Composable
fun PlayerHandView(
    hand: List<CardUi>,
    selectedCardId: String?,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        hand.forEach { card ->
            CardFace(
                card = card,
                selected = card.id == selectedCardId,
                onClick = { onCardClick(card.id) },
            )
        }
    }
}
