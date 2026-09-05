package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.presentation.game.TablePairUi
import com.example.foolcardgame.ui.components.card.CardFace

private val MaxTableCardWidth = 64.dp
private val MaxTableCardHeight = 90.dp
private const val TableColumnCount = 3
private val TableRowSpacing = 10.dp
private val BeatenCardDim = Color.Black.copy(alpha = 0.42f)

/** Horizontal overlap of defense over attack: 30% → shift by 70% of width. */
private const val DefenseOverlapXFraction = 0.70f

/** Vertical overlap of defense over attack: 80% → shift by 20% of height. */
private const val DefenseOverlapYFraction = 0.20f

@Composable
fun TableCardsView(
    tablePairs: List<TablePairUi>,
    onTableBoundsChanged: (Rect) -> Unit,
    onAttackCardBoundsChanged: (pairId: Int, bounds: Rect) -> Unit,
    modifier: Modifier = Modifier,
    showEmptyHint: Boolean = true,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .onGloballyPositioned { coordinates ->
                onTableBoundsChanged(coordinates.boundsInRoot())
            },
        contentAlignment = Alignment.Center,
    ) {
        if (tablePairs.isNotEmpty()) {
            val cellWidth = maxWidth / TableColumnCount
            val cardWidth = min(MaxTableCardWidth, cellWidth)
            val cardHeight = cardWidth * (MaxTableCardHeight.value / MaxTableCardWidth.value)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TableRowSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                tablePairs.chunked(TableColumnCount).forEach { rowPairs ->
                    val centerShortRow = rowPairs.size < TableColumnCount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (centerShortRow) {
                            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        } else {
                            Arrangement.Start
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowPairs.forEachIndexed { indexInRow, pair ->
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (centerShortRow) {
                                            Modifier
                                        } else {
                                            Modifier.weight(1f)
                                        },
                                    )
                                    .zIndex(indexInRow.toFloat()),
                                contentAlignment = Alignment.Center,
                            ) {
                                TablePairView(
                                    pair = pair,
                                    cardWidth = cardWidth,
                                    cardHeight = cardHeight,
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
    }
}

@Composable
private fun TablePairView(
    pair: TablePairUi,
    cardWidth: Dp,
    cardHeight: Dp,
    onAttackCardBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDefense = pair.defense != null
    val defenseOffsetX = cardWidth * DefenseOverlapXFraction
    val defenseOffsetY = cardHeight * DefenseOverlapYFraction
    val stackWidth = if (hasDefense) cardWidth + defenseOffsetX else cardWidth
    val stackHeight = if (hasDefense) cardHeight + defenseOffsetY else cardHeight

    Box(
        modifier = modifier
            .width(stackWidth)
            .height(stackHeight),
    ) {
        DimmedTableCard(
            card = pair.attack,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            dimmed = hasDefense,
            modifier = Modifier
                .zIndex(0f)
                .onGloballyPositioned { coordinates ->
                    onAttackCardBoundsChanged(coordinates.boundsInRoot())
                },
        )
        if (hasDefense) {
            DimmedTableCard(
                card = pair.defense,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                dimmed = true,
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = defenseOffsetX, y = defenseOffsetY),
            )
        }
    }
}

@Composable
private fun DimmedTableCard(
    card: CardUi?,
    cardWidth: Dp,
    cardHeight: Dp,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        CardFace(
            card = card,
            width = cardWidth,
            height = cardHeight,
        )
        if (dimmed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(BeatenCardDim),
            )
        }
    }
}
