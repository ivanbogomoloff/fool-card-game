package com.example.foolcardgame.ui.components.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.TablePairUi
import com.example.foolcardgame.ui.components.card.CardFace

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableCardsView(
    tablePairs: List<TablePairUi>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tablePairs.forEach { pair ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TablePairView(pair = pair)
            }
        }
    }
}

@Composable
private fun TablePairView(
    pair: TablePairUi,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CardFace(card = pair.attack)
        CardFace(card = pair.defense, faceUp = pair.defense != null)
    }
}
