package com.example.foolcardgame.ui.components.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.foolcardgame.presentation.game.TablePairUi
import com.example.foolcardgame.ui.components.card.CardFace
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.CardCream

private val TableCardWidth = 56.dp
private val TableCardHeight = 80.dp

/** 30% horizontal overlap → shift by 70% of width. */
private val DefenseOffsetX = TableCardWidth * 0.70f

/** 80% vertical overlap → shift by 20% of height. */
private val DefenseOffsetY = TableCardHeight * 0.20f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableCardsView(
    tablePairs: List<TablePairUi>,
    onTableBoundsChanged: (Rect) -> Unit,
    onAttackCardBoundsChanged: (pairId: Int, bounds: Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .onGloballyPositioned { coordinates ->
                onTableBoundsChanged(coordinates.boundsInRoot())
            }
            .then(
                if (tablePairs.isEmpty()) {
                    Modifier
                        .border(
                            width = 1.dp,
                            color = AccentTeal.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .background(
                            CardCream.copy(alpha = 0.35f),
                            RoundedCornerShape(12.dp),
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (tablePairs.isEmpty()) {
            Text(
                text = "Перетащите карту на стол",
                style = MaterialTheme.typography.bodyMedium,
                color = AccentTeal,
            )
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tablePairs.forEach { pair ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        TablePairView(
                            pair = pair,
                            onAttackCardBoundsChanged = { bounds ->
                                onAttackCardBoundsChanged(pair.id, bounds)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TablePairView(
    pair: TablePairUi,
    onAttackCardBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDefense = pair.defense != null
    val stackWidth = if (hasDefense) TableCardWidth + DefenseOffsetX else TableCardWidth
    val stackHeight = if (hasDefense) TableCardHeight + DefenseOffsetY else TableCardHeight

    Box(
        modifier = modifier
            .width(stackWidth)
            .height(stackHeight),
    ) {
        CardFace(
            card = pair.attack,
            width = TableCardWidth,
            height = TableCardHeight,
            modifier = Modifier
                .zIndex(0f)
                .onGloballyPositioned { coordinates ->
                    onAttackCardBoundsChanged(coordinates.boundsInRoot())
                },
        )
        if (hasDefense) {
            CardFace(
                card = pair.defense,
                width = TableCardWidth,
                height = TableCardHeight,
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = DefenseOffsetX, y = DefenseOffsetY),
            )
        }
    }
}
