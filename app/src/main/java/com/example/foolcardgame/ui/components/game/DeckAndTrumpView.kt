package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.components.card.CardFace
import com.example.foolcardgame.ui.theme.SoftCharcoal

@Composable
fun DeckAndTrumpView(
    deckCount: Int,
    trump: CardUi?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardFace(card = null, faceUp = false)
            Text(
                text = deckCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = SoftCharcoal,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Козырь",
                style = MaterialTheme.typography.labelSmall,
                color = SoftCharcoal,
            )
            CardFace(card = trump, faceUp = trump != null)
        }
    }
}
