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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        hand.forEach { card ->
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
) {
    var boundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var fingerInRoot by remember { mutableStateOf(Offset.Zero) }

    CardFace(
        card = card,
        selected = selected,
        onClick = {
            if (interactive && !isDragging) onCardClick(card.id)
        },
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                boundsInRoot = coordinates.boundsInRoot()
            }
            .then(
                if (interactive) {
                    Modifier.pointerInput(card.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                val start = Offset(
                                    x = boundsInRoot.left + localOffset.x,
                                    y = boundsInRoot.top + localOffset.y,
                                )
                                fingerInRoot = start
                                onDragStart(card.id, start)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val next = fingerInRoot + dragAmount
                                fingerInRoot = next
                                onDrag(next)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                } else {
                    Modifier
                },
            ),
    )
}
