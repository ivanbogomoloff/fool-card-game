package com.example.foolcardgame.ui.components.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.foolcardgame.presentation.game.TablePairUi

sealed class TableDropAction {
    data class Defend(val pairId: Int) : TableDropAction()
    data object Attack : TableDropAction()
}

internal const val AttackHitSlopPx = 48f

data class DraggedCardSizePx(
    val width: Float,
    val height: Float,
)

private fun draggedCardRect(position: Offset, cardSize: DraggedCardSizePx?): Rect? {
    if (cardSize == null || cardSize.width <= 0f || cardSize.height <= 0f) return null
    return Rect(
        left = position.x - cardSize.width / 2f,
        top = position.y - cardSize.height / 2f,
        right = position.x + cardSize.width / 2f,
        bottom = position.y + cardSize.height / 2f,
    )
}

private fun hitsDropZone(
    position: Offset,
    zone: Rect,
    cardSize: DraggedCardSizePx?,
): Boolean {
    if (zone.contains(position)) return true
    val cardRect = draggedCardRect(position, cardSize) ?: return false
    return zone.overlaps(cardRect)
}

private fun hitsRect(
    position: Offset,
    target: Rect,
    cardSize: DraggedCardSizePx?,
): Boolean {
    val hitArea = target.inflate(AttackHitSlopPx)
    if (hitArea.contains(position)) return true
    val cardRect = draggedCardRect(position, cardSize) ?: return false
    return hitArea.overlaps(cardRect)
}

fun tableDropZone(
    playAreaBounds: Rect,
    actionBarBounds: Rect,
    handBounds: Rect,
    layoutBounds: Rect,
): Rect {
    val left = if (layoutBounds.width > 1f) layoutBounds.left else playAreaBounds.left
    val right = if (layoutBounds.width > 1f) layoutBounds.right else playAreaBounds.right
    val top = if (playAreaBounds.height > 1f) {
        playAreaBounds.top
    } else if (layoutBounds.height > 1f) {
        layoutBounds.top
    } else {
        0f
    }
    val bottom = when {
        actionBarBounds.height > 1f -> actionBarBounds.top
        handBounds.height > 1f -> handBounds.top
        playAreaBounds.height > 1f -> playAreaBounds.bottom
        layoutBounds.height > 1f -> layoutBounds.bottom
        else -> 0f
    }
    if (right <= left || bottom <= top) {
        return playAreaBounds
    }
    return Rect(left, top, right, bottom)
}

fun resolveTableDrop(
    position: Offset,
    tablePairs: List<TablePairUi>,
    tableBounds: Rect,
    attackCardBounds: Map<Int, Rect>,
    cardSize: DraggedCardSizePx? = null,
    handTop: Float = Float.NaN,
    playAreaBounds: Rect = Rect.Zero,
    isLocalDefender: Boolean = true,
): TableDropAction? {
    val unbeaten = tablePairs.filter { it.defense == null }
    val onTable = isDropOnTable(
        position = position,
        zone = tableBounds,
        playAreaBounds = playAreaBounds,
        cardSize = cardSize,
        handTop = handTop,
    )
    if (unbeaten.isNotEmpty()) {
        if (!isLocalDefender) {
            return if (onTable || tablePairs.any { pair ->
                attackCardBounds[pair.id]?.let { bounds ->
                    hitsRect(position, bounds, cardSize)
                } == true
            }) {
                TableDropAction.Attack
            } else {
                null
            }
        }
        return resolveDefendDrop(
            position = position,
            unbeaten = unbeaten,
            onTable = onTable,
            attackCardBounds = attackCardBounds,
            cardSize = cardSize,
        )
    }
    val onPairSlop = tablePairs.any { pair ->
        attackCardBounds[pair.id]?.let { bounds ->
            hitsRect(position, bounds, cardSize)
        } == true
    }
    return if (onTable || onPairSlop) TableDropAction.Attack else null
}

private fun resolveDefendDrop(
    position: Offset,
    unbeaten: List<TablePairUi>,
    onTable: Boolean,
    attackCardBounds: Map<Int, Rect>,
    cardSize: DraggedCardSizePx?,
): TableDropAction? {
    val scored = unbeaten.mapNotNull { pair ->
        val bounds = attackCardBounds[pair.id] ?: return@mapNotNull null
        val distance = (position - bounds.center).getDistance()
        val inSlop = hitsRect(position, bounds, cardSize)
        Triple(pair.id, distance, inSlop)
    }
    val nearest = scored.minByOrNull { it.second }
    return when {
        nearest != null && (onTable || nearest.third) -> TableDropAction.Defend(nearest.first)
        onTable -> TableDropAction.Defend(unbeaten.first().id)
        else -> null
    }
}

private fun isDropOnTable(
    position: Offset,
    zone: Rect,
    playAreaBounds: Rect,
    cardSize: DraggedCardSizePx?,
    handTop: Float,
): Boolean {
    if (hitsDropZone(position, zone, cardSize)) return true
    if (!handTop.isNaN() && isCardLiftedAboveHand(position, cardSize, handTop)) {
        val cardRect = draggedCardRect(position, cardSize) ?: return false
        return playAreaBounds.height > 1f && playAreaBounds.overlaps(cardRect)
    }
    return false
}

private fun isCardLiftedAboveHand(
    position: Offset,
    cardSize: DraggedCardSizePx?,
    handTop: Float,
): Boolean {
    if (cardSize == null) return false
    return position.y - cardSize.height / 2f < handTop
}
