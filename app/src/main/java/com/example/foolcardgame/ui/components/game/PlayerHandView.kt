package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.components.card.CardFace

private val HandCardWidth = 80.dp
private val HandCardHeight = 116.dp
private val PreferredStep = 44.dp
private val MinStep = 18.dp

@Composable
fun PlayerHandView(
    hand: List<CardUi>,
    selectedCardId: String?,
    draggingCardId: String?,
    onCardClick: (String) -> Unit,
    onDragStart: (cardId: String, positionInRoot: Offset) -> Unit,
    onDrag: (positionInRoot: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    if (hand.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val gaps = (hand.size - 1).coerceAtLeast(0)
        val step = if (gaps == 0) {
            0.dp
        } else {
            val availableForGaps = maxWidth - HandCardWidth
            val fitted = availableForGaps / gaps
            min(PreferredStep, max(MinStep, fitted))
        }
        val fanWidth = HandCardWidth + step * gaps

        Box(
            modifier = Modifier
                .width(fanWidth)
                .height(HandCardHeight),
        ) {
            hand.forEachIndexed { index, card ->
                DraggableHandCard(
                    card = card,
                    selected = card.id == selectedCardId,
                    isDragging = card.id == draggingCardId,
                    interactive = interactive,
                    onCardClick = onCardClick,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    modifier = Modifier
                        .offset(x = step * index)
                        .zIndex(index.toFloat()),
                )
            }
        }
    }
}

@Composable
private fun DraggableHandCard(
    card: CardUi,
    selected: Boolean,
    isDragging: Boolean,
    interactive: Boolean,
    onCardClick: (String) -> Unit,
    onDragStart: (cardId: String, positionInRoot: Offset) -> Unit,
    onDrag: (positionInRoot: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val layoutCoordinatesState = rememberUpdatedState(layoutCoordinates)
    val onCardClickState = rememberUpdatedState(onCardClick)
    val onDragStartState = rememberUpdatedState(onDragStart)
    val onDragState = rememberUpdatedState(onDrag)
    val onDragEndState = rememberUpdatedState(onDragEnd)
    val onDragCancelState = rememberUpdatedState(onDragCancel)

    CardFace(
        card = card,
        selected = selected,
        width = HandCardWidth,
        height = HandCardHeight,
        onClick = {
            if (interactive && !isDragging) onCardClickState.value(card.id)
        },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                layoutCoordinates = coordinates
            }
            .then(
                if (interactive) {
                    Modifier.pointerInput(card.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                val start = layoutCoordinatesState.value.toRootOrNull(localOffset)
                                    ?: localOffset
                                onDragStartState.value(card.id, start)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val next = layoutCoordinatesState.value.toRootOrNull(change.position)
                                    ?: change.position
                                onDragState.value(next)
                            },
                            onDragEnd = { onDragEndState.value() },
                            onDragCancel = { onDragCancelState.value() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    )
}

private fun LayoutCoordinates?.toRootOrNull(local: Offset): Offset? {
    val coords = this ?: return null
    if (!coords.isAttached) return null
    return coords.localToRoot(local)
}
