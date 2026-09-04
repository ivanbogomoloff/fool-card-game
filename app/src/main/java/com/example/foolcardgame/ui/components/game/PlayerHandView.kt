package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.components.card.CardFace

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
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy((-32).dp),
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
                modifier = Modifier.zIndex(index.toFloat()),
            )
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
        width = 72.dp,
        height = 104.dp,
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
