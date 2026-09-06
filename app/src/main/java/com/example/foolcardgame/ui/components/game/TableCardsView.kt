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
import kotlin.math.ceil

private val MaxTableCardWidth = 64.dp
private val MaxTableCardHeight = 90.dp
private const val TableColumnCount = 3
private val TableRowSpacingComfortable = 10.dp
private val TableRowSpacingCrowded = 5.dp
private val BeatenCardDim = Color.Black.copy(alpha = 0.42f)
private const val CrowdedPairThreshold = 4

/** Горизонтальное перекрытие отбивки над атакой (комфортное). */
private const val DefenseOverlapXComfortable = 0.70f

/** Горизонтальное перекрытие при многих парах — стопки ближе. */
private const val DefenseOverlapXCrowded = 0.55f

/** Вертикальное перекрытие отбивки над атакой. */
private const val DefenseOverlapYFraction = 0.20f

private val CardAspect = MaxTableCardHeight / MaxTableCardWidth

@Composable
fun TableCardsView(
    tablePairs: List<TablePairUi>,
    onTableBoundsChanged: (Rect) -> Unit,
    onAttackCardBoundsChanged: (pairId: Int, bounds: Rect) -> Unit,
    modifier: Modifier = Modifier,
    showEmptyHint: Boolean = true,
) {
    val crowded = tablePairs.size >= CrowdedPairThreshold
    val contentPadding = if (crowded) 4.dp else 8.dp
    val rowSpacing = if (crowded) TableRowSpacingCrowded else TableRowSpacingComfortable
    val overlapX = if (crowded) DefenseOverlapXCrowded else DefenseOverlapXComfortable

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .onGloballyPositioned { coordinates ->
                onTableBoundsChanged(coordinates.boundsInRoot())
            },
        contentAlignment = Alignment.Center,
    ) {
        if (tablePairs.isNotEmpty()) {
            val rowCount = ceil(tablePairs.size / TableColumnCount.toFloat()).toInt().coerceAtLeast(1)
            val cellWidth = maxWidth / TableColumnCount
            val spacingTotal = rowSpacing * (rowCount - 1).coerceAtLeast(0)
            val stackHeightFactor = 1f + DefenseOverlapYFraction
            val maxCardHeightFromRows = (maxHeight - spacingTotal) / (rowCount * stackHeightFactor)
            val widthCap = min(MaxTableCardWidth, cellWidth)
            val heightCap = min(MaxTableCardHeight, maxCardHeightFromRows)
            val cardWidth = min(widthCap, heightCap / CardAspect)
            val cardHeight = cardWidth * CardAspect

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
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
                                    overlapXFraction = overlapX,
                                    overlapYFraction = DefenseOverlapYFraction,
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
    overlapXFraction: Float,
    overlapYFraction: Float,
    onAttackCardBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDefense = pair.defense != null
    val defenseOffsetX = cardWidth * overlapXFraction
    val defenseOffsetY = cardHeight * overlapYFraction
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
