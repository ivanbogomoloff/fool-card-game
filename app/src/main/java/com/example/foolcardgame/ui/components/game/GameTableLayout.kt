package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.presentation.game.GameUiState
import com.example.foolcardgame.presentation.game.TablePairUi
import com.example.foolcardgame.ui.components.card.CardFace
import com.example.foolcardgame.ui.theme.AccentTeal
import kotlin.math.roundToInt

private data class HandDragState(
    val cardId: String,
    val card: CardUi,
    val positionInRoot: Offset,
)

private const val DefenseOverlapXFraction = 0.70f
private const val DefenseOverlapYFraction = 0.20f

@Composable
fun GameTableLayout(
    uiState: GameUiState,
    onCardClick: (String) -> Unit,
    onAttackDrop: (cardId: String) -> Unit,
    onDefendDrop: (cardId: String, pairId: Int) -> Unit,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onTakeClick: () -> Unit,
    onReadyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = AccentTeal)
        }
        return
    }

    when (uiState.phase) {
        GamePhase.FINISHED -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.resultMessage.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        GamePhase.LOBBY_WAITING,
        GamePhase.IN_PROGRESS,
        -> {
            InProgressGameLayout(
                uiState = uiState,
                onCardClick = onCardClick,
                onAttackDrop = onAttackDrop,
                onDefendDrop = onDefendDrop,
                onBitoClick = onBitoClick,
                onPassClick = onPassClick,
                onTakeClick = onTakeClick,
                onReadyClick = onReadyClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun InProgressGameLayout(
    uiState: GameUiState,
    onCardClick: (String) -> Unit,
    onAttackDrop: (cardId: String) -> Unit,
    onDefendDrop: (cardId: String, pairId: Int) -> Unit,
    onBitoClick: () -> Unit,
    onPassClick: () -> Unit,
    onTakeClick: () -> Unit,
    onReadyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragState by remember { mutableStateOf<HandDragState?>(null) }
    var tableBounds by remember { mutableStateOf(Rect.Zero) }
    var layoutBounds by remember { mutableStateOf(Rect.Zero) }
    val attackCardBounds = remember { mutableStateMapOf<Int, Rect>() }
    var discardFlyaway by remember { mutableStateOf<List<FlyingDiscardCard>>(emptyList()) }
    val density = LocalDensity.current
    val isDiscardAnimating = discardFlyaway.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                layoutBounds = coordinates.boundsInRoot()
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OpponentsRow(
                opponents = uiState.opponents,
                phase = uiState.phase,
                modifier = Modifier.fillMaxWidth(),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val deckShiftRight = maxWidth * 0.05f
                val tableStartPadding = 100.dp + deckShiftRight + 20.dp
                TableCardsView(
                    tablePairs = uiState.tablePairs,
                    onTableBoundsChanged = { tableBounds = it },
                    onAttackCardBoundsChanged = { pairId, bounds ->
                        attackCardBounds[pairId] = bounds
                    },
                    showEmptyHint = !isDiscardAnimating,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = tableStartPadding),
                )
                DeckAndTrumpView(
                    trump = uiState.trump,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-28).dp + deckShiftRight),
                )
            }
            GameActionBar(
                actions = uiState.actions,
                readySecondsLeft = uiState.readySecondsLeft,
                onBitoClick = {
                    if (isDiscardAnimating) return@GameActionBar
                    if (uiState.tablePairs.isNotEmpty()) {
                        discardFlyaway = snapshotDiscardCards(
                            pairs = uiState.tablePairs,
                            attackBounds = attackCardBounds,
                        )
                        attackCardBounds.clear()
                    }
                    onBitoClick()
                },
                onPassClick = onPassClick,
                onTakeClick = onTakeClick,
                onReadyClick = onReadyClick,
            )
            PlayerHandView(
                hand = uiState.hand,
                selectedCardId = uiState.selectedCardId,
                draggingCardId = dragState?.cardId,
                onCardClick = onCardClick,
                onDragStart = { cardId, position ->
                    val card = uiState.hand.firstOrNull { it.id == cardId } ?: return@PlayerHandView
                    dragState = HandDragState(cardId, card, position)
                },
                onDrag = { position ->
                    dragState = dragState?.copy(positionInRoot = position)
                },
                onDragEnd = {
                    val current = dragState
                    dragState = null
                    if (current == null) return@PlayerHandView
                    resolveDrop(
                        position = current.positionInRoot,
                        cardId = current.cardId,
                        tablePairs = uiState.tablePairs,
                        tableBounds = tableBounds,
                        attackCardBounds = attackCardBounds,
                        onAttackDrop = onAttackDrop,
                        onDefendDrop = onDefendDrop,
                    )
                },
                onDragCancel = { dragState = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (isDiscardAnimating) {
            val flyExtraPx = with(density) { 120.dp.toPx() }
            BitoDiscardOverlay(
                cards = discardFlyaway,
                layoutTopLeftInRoot = Offset(layoutBounds.left, layoutBounds.top),
                flyDistancePx = layoutBounds.width + flyExtraPx,
                onFinished = { discardFlyaway = emptyList() },
            )
        }

        val activeDrag = dragState
        if (activeDrag != null) {
            val cardWidthPx = with(density) { 56.dp.toPx() }
            val cardHeightPx = with(density) { 80.dp.toPx() }
            CardFace(
                card = activeDrag.card,
                selected = true,
                modifier = Modifier
                    .zIndex(10f)
                    .offset {
                        IntOffset(
                            x = (activeDrag.positionInRoot.x - layoutBounds.left - cardWidthPx / 2)
                                .roundToInt(),
                            y = (activeDrag.positionInRoot.y - layoutBounds.top - cardHeightPx / 2)
                                .roundToInt(),
                        )
                    },
            )
        }
    }
}

private fun snapshotDiscardCards(
    pairs: List<TablePairUi>,
    attackBounds: Map<Int, Rect>,
): List<FlyingDiscardCard> {
    val flying = mutableListOf<FlyingDiscardCard>()
    var stagger = 0
    pairs.forEach { pair ->
        val attackRect = attackBounds[pair.id]
        if (attackRect != null) {
            flying += FlyingDiscardCard(
                id = "attack-${pair.id}",
                card = pair.attack,
                startTopLeftInRoot = Offset(attackRect.left, attackRect.top),
                widthPx = attackRect.width,
                heightPx = attackRect.height,
                staggerIndex = stagger++,
            )
            val defense = pair.defense
            if (defense != null) {
                flying += FlyingDiscardCard(
                    id = "defense-${pair.id}",
                    card = defense,
                    startTopLeftInRoot = Offset(
                        x = attackRect.left + attackRect.width * DefenseOverlapXFraction,
                        y = attackRect.top + attackRect.height * DefenseOverlapYFraction,
                    ),
                    widthPx = attackRect.width,
                    heightPx = attackRect.height,
                    staggerIndex = stagger++,
                )
            }
        } else {
            // Fallback if bounds missing: still animate with zero-size placeholder skip
        }
    }
    return flying
}

private const val AttackHitSlopPx = 48f

private fun resolveDrop(
    position: Offset,
    cardId: String,
    tablePairs: List<TablePairUi>,
    tableBounds: Rect,
    attackCardBounds: Map<Int, Rect>,
    onAttackDrop: (String) -> Unit,
    onDefendDrop: (String, Int) -> Unit,
) {
    val nearestUndefended = tablePairs
        .asSequence()
        .filter { it.defense == null }
        .mapNotNull { pair ->
            val bounds = attackCardBounds[pair.id] ?: return@mapNotNull null
            val hitArea = bounds.inflate(AttackHitSlopPx)
            if (!hitArea.contains(position)) return@mapNotNull null
            pair to (position - bounds.center).getDistance()
        }
        .minByOrNull { it.second }
        ?.first

    if (nearestUndefended != null) {
        onDefendDrop(cardId, nearestUndefended.id)
        return
    }
    if (tableBounds.contains(position)) {
        onAttackDrop(cardId)
    }
}
