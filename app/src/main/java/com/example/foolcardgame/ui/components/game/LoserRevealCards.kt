package com.example.foolcardgame.ui.components.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.presentation.game.TablePairUi
import kotlin.math.ceil

private const val LoserRevealColumnCount = 3
private const val LoserRevealPairIdBase = -1000

/** Maps fool hand cards to single-attack table pairs for [TableCardsView]. */
fun revealLoserCardsToTablePairs(cards: List<CardUi>): List<TablePairUi> =
    cards.mapIndexed { index, card ->
        TablePairUi(
            id = LoserRevealPairIdBase - index,
            attack = card,
            defense = null,
        )
    }

/**
 * Top-left positions for [count] cards in a 3-column grid inside [tableBounds],
 * matching the general layout of [TableCardsView].
 */
fun loserRevealTableSlotTops(
    tableBounds: Rect,
    count: Int,
    cardWidthPx: Float,
    cardHeightPx: Float,
): List<Offset> {
    if (count <= 0) return emptyList()
    val area = if (tableBounds.width > 1f && tableBounds.height > 1f) {
        tableBounds
    } else {
        Rect(0f, 0f, cardWidthPx * LoserRevealColumnCount, cardHeightPx * 2)
    }
    val rowCount = ceil(count / LoserRevealColumnCount.toFloat()).toInt().coerceAtLeast(1)
    val cellWidth = area.width / LoserRevealColumnCount
    val cellHeight = area.height / rowCount
    return List(count) { index ->
        val col = index % LoserRevealColumnCount
        val row = index / LoserRevealColumnCount
        val left = area.left + col * cellWidth + (cellWidth - cardWidthPx) / 2f
        val top = area.top + row * cellHeight + (cellHeight - cardHeightPx) / 2f
        Offset(left, top)
    }
}
