package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import com.example.foolcardgame.domain.model.RoundEventKind
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
private const val DragCardScale = 1.6f
private val HandCardWidth = 72.dp
private val HandCardHeight = 104.dp

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
    onToggleLoserCardsClick: () -> Unit = {},
    onExitClick: () -> Unit = {},
    onFlyAnimationFinished: () -> Unit = {},
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

    InProgressGameLayout(
        uiState = uiState,
        onCardClick = onCardClick,
        onAttackDrop = onAttackDrop,
        onDefendDrop = onDefendDrop,
        onBitoClick = onBitoClick,
        onPassClick = onPassClick,
        onTakeClick = onTakeClick,
        onReadyClick = onReadyClick,
        onToggleLoserCardsClick = onToggleLoserCardsClick,
        onExitClick = onExitClick,
        onFlyAnimationFinished = onFlyAnimationFinished,
        modifier = modifier,
    )
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
    onToggleLoserCardsClick: () -> Unit,
    onExitClick: () -> Unit,
    onFlyAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragState by remember { mutableStateOf<HandDragState?>(null) }
    var tableBounds by remember { mutableStateOf(Rect.Zero) }
    var playAreaBounds by remember { mutableStateOf(Rect.Zero) }
    var layoutBounds by remember { mutableStateOf(Rect.Zero) }
    var handBounds by remember { mutableStateOf(Rect.Zero) }
    var actionBarBounds by remember { mutableStateOf(Rect.Zero) }
    val opponentAvatarBounds = remember { mutableStateMapOf<String, Rect>() }
    val attackCardBounds = remember { mutableStateMapOf<Int, Rect>() }
    var discardFlyaway by remember { mutableStateOf<List<FlyingDiscardCard>>(emptyList()) }
    var suppressTableCards by remember { mutableStateOf(false) }
    var pendingAfterFlyaway by remember { mutableStateOf<(() -> Unit)?>(null) }
    var lastHandledFlyTick by remember { mutableStateOf(-1L) }
    val density = LocalDensity.current
    val isGameFinished = uiState.phase == GamePhase.FINISHED
    val isHandInteractive = uiState.phase == GamePhase.IN_PROGRESS
    val isDiscardAnimating = discardFlyaway.isNotEmpty() && !isGameFinished
    val visibleTablePairs = if (suppressTableCards) emptyList() else uiState.tablePairs
    val livePairIds = visibleTablePairs.map { it.id }.toSet()
    SideEffect {
        attackCardBounds.keys.filter { it !in livePairIds }.forEach { attackCardBounds.remove(it) }
    }

    fun startFlyaway(
        pairs: List<TablePairUi>,
        direction: TableFlyawayDirection,
    ): Boolean {
        if (isGameFinished) return false
        if (isDiscardAnimating || pairs.isEmpty()) return false
        val flyExtraPx = with(density) { 120.dp.toPx() }
        val cardWidthPx = with(density) { HandCardWidth.toPx() }
        val cardHeightPx = with(density) { HandCardHeight.toPx() }
        val fallbackBounds = if (tableBounds.width > 1f) {
            tableBounds
        } else {
            layoutBounds
        }
        discardFlyaway = snapshotDiscardCards(
            pairs = pairs,
            attackBounds = attackCardBounds,
            direction = direction,
            layoutBounds = layoutBounds,
            handBounds = handBounds,
            flyExtraPx = flyExtraPx,
            fallbackTableBounds = fallbackBounds,
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
        )
        if (discardFlyaway.isNotEmpty()) {
            attackCardBounds.clear()
        }
        return discardFlyaway.isNotEmpty()
    }

    fun startTableFlyaway(direction: TableFlyawayDirection): Boolean =
        startFlyaway(uiState.tablePairs, direction)

    LaunchedEffect(uiState.tableFlyAnimation?.atTick) {
        val animation = uiState.tableFlyAnimation ?: return@LaunchedEffect
        if (animation.atTick == lastHandledFlyTick) return@LaunchedEffect
        lastHandledFlyTick = animation.atTick
        val direction = when (animation.kind) {
            RoundEventKind.TOOK -> {
                val target = opponentAvatarBounds[animation.targetOpponentId]?.center
                    ?: layoutBounds.center
                TableFlyawayDirection.ToTarget(
                    end = Offset(
                        x = target.x,
                        y = target.y,
                    ),
                )
            }
            RoundEventKind.BITO -> TableFlyawayDirection.Right
        }
        if (startFlyaway(animation.pairs, direction)) {
            suppressTableCards = true
            pendingAfterFlyaway = {
                suppressTableCards = false
                onFlyAnimationFinished()
            }
        } else {
            onFlyAnimationFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                layoutBounds = coordinates.boundsInRoot()
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        playAreaBounds = coordinates.boundsInRoot()
                    },
            ) {
                TableCardsView(
                    tablePairs = visibleTablePairs,
                    onTableBoundsChanged = { tableBounds = it },
                    onAttackCardBoundsChanged = { pairId, bounds ->
                        attackCardBounds[pairId] = bounds
                    },
                    showEmptyHint = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 72.dp, end = 72.dp, top = 168.dp, bottom = 8.dp),
                )
                val hasTopOpponent = uiState.opponents.size == 1 || uiState.opponents.size >= 3
                DeckAndTrumpView(
                    deckCount = uiState.deckCount,
                    trump = uiState.trump,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (hasTopOpponent) 132.dp else 64.dp),
                )
                OpponentsRow(
                    opponents = uiState.opponents,
                    phase = uiState.phase,
                    opponentAction = uiState.opponentAction,
                    loserId = uiState.loserId,
                    localPlayerId = uiState.localPlayerId,
                    showLoserCards = uiState.showLoserCards,
                    revealLoserCards = uiState.revealLoserCards,
                    onOpponentAvatarBoundsChanged = { id, bounds ->
                        opponentAvatarBounds[id] = bounds
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                GameActionBar(
                    actions = uiState.actions,
                    readySecondsLeft = uiState.readySecondsLeft,
                    turnSecondsLeft = uiState.turnSecondsLeft,
                    isLocalPlayerTurn = uiState.isLocalPlayerTurn,
                    isLocalDefending = uiState.isLocalDefending,
                    isLocalAttacking = uiState.isLocalAttacking,
                    finishedSummary = uiState.finishedSummary,
                    canRevealLoserCards = uiState.canRevealLoserCards,
                    showLoserCards = uiState.showLoserCards,
                    onBitoClick = {
                        if (isDiscardAnimating) return@GameActionBar
                        if (uiState.deckCount == 0) {
                            onBitoClick()
                        } else {
                            startTableFlyaway(TableFlyawayDirection.Right)
                            onBitoClick()
                        }
                    },
                    onPassClick = onPassClick,
                    onTakeClick = {
                        if (isDiscardAnimating) return@GameActionBar
                        if (startTableFlyaway(TableFlyawayDirection.Down)) {
                            suppressTableCards = true
                            pendingAfterFlyaway = {
                                onTakeClick()
                                suppressTableCards = false
                            }
                        } else {
                            onTakeClick()
                        }
                    },
                    onReadyClick = onReadyClick,
                    onToggleLoserCardsClick = onToggleLoserCardsClick,
                    onExitClick = onExitClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { coordinates ->
                            actionBarBounds = coordinates.boundsInRoot()
                        },
                )
            }
            PlayerHandView(
                hand = uiState.hand,
                selectedCardId = if (isHandInteractive) {
                    uiState.selectedCardId
                } else {
                    null
                },
                draggingCardId = dragState?.cardId,
                interactive = isHandInteractive,
                onCardClick = onCardClick,
                onDragStart = { cardId, position ->
                    if (!isHandInteractive) return@PlayerHandView
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
                    val cardWidthPx = with(density) { HandCardWidth.toPx() } * DragCardScale
                    val cardHeightPx = with(density) { HandCardHeight.toPx() } * DragCardScale
                    when (
                        val action = resolveTableDrop(
                            position = current.positionInRoot,
                            tablePairs = uiState.tablePairs,
                            tableBounds = tableDropZone(
                                playAreaBounds = playAreaBounds,
                                actionBarBounds = actionBarBounds,
                                handBounds = handBounds,
                                layoutBounds = layoutBounds,
                            ),
                            attackCardBounds = attackCardBounds,
                            cardSize = DraggedCardSizePx(
                                width = cardWidthPx,
                                height = cardHeightPx,
                            ),
                            handTop = if (handBounds.height > 1f) handBounds.top else Float.NaN,
                            playAreaBounds = playAreaBounds,
                        )
                    ) {
                        is TableDropAction.Defend -> onDefendDrop(current.cardId, action.pairId)
                        TableDropAction.Attack -> onAttackDrop(current.cardId)
                        null -> Unit
                    }
                },
                onDragCancel = { dragState = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .onGloballyPositioned { coordinates ->
                        handBounds = coordinates.boundsInRoot()
                    },
            )
        }

        if (isDiscardAnimating) {
            TableCardsFlyawayOverlay(
                cards = discardFlyaway,
                layoutTopLeftInRoot = Offset(layoutBounds.left, layoutBounds.top),
                onFinished = {
                    discardFlyaway = emptyList()
                    pendingAfterFlyaway?.invoke()
                    pendingAfterFlyaway = null
                },
            )
        }

        val activeDrag = dragState
        if (activeDrag != null) {
            val cardWidthPx = with(density) { HandCardWidth.toPx() }
            val cardHeightPx = with(density) { HandCardHeight.toPx() }
            CardFace(
                card = activeDrag.card,
                selected = true,
                width = HandCardWidth,
                height = HandCardHeight,
                scaleOverride = DragCardScale,
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
    direction: TableFlyawayDirection,
    layoutBounds: Rect,
    handBounds: Rect,
    flyExtraPx: Float,
    fallbackTableBounds: Rect,
    cardWidthPx: Float,
    cardHeightPx: Float,
): List<FlyingDiscardCard> {
    val starts = mutableListOf<Triple<String, CardUi?, Rect>>()
    pairs.forEachIndexed { index, pair ->
        val attackRect = attackBounds[pair.id] ?: syntheticCardRect(
            tableBounds = fallbackTableBounds,
            index = index,
            totalPairs = pairs.size,
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
        )
        starts += Triple("attack-${pair.id}", pair.attack, attackRect)
        val defense = pair.defense
        if (defense != null) {
            val defenseRect = Rect(
                left = attackRect.left + attackRect.width * DefenseOverlapXFraction,
                top = attackRect.top + attackRect.height * DefenseOverlapYFraction,
                right = attackRect.left + attackRect.width * DefenseOverlapXFraction + attackRect.width,
                bottom = attackRect.top + attackRect.height * DefenseOverlapYFraction + attackRect.height,
            )
            starts += Triple("defense-${pair.id}", defense, defenseRect)
        }
    }
    if (starts.isEmpty()) return emptyList()

    val handReady = handBounds.width > 1f && handBounds.height > 1f
    return starts.mapIndexed { index, (id, card, rect) ->
        val end = when (direction) {
            TableFlyawayDirection.Right -> Offset(
                x = layoutBounds.right + flyExtraPx,
                y = rect.top,
            )
            TableFlyawayDirection.Down -> {
                if (handReady) {
                    val fan = (index - (starts.size - 1) / 2f) * (rect.width * 0.22f)
                    Offset(
                        x = handBounds.center.x - rect.width / 2f + fan,
                        y = handBounds.center.y - rect.height / 2f,
                    )
                } else {
                    Offset(
                        x = rect.left,
                        y = layoutBounds.bottom - rect.height - flyExtraPx,
                    )
                }
            }
            is TableFlyawayDirection.ToTarget -> Offset(
                x = direction.end.x - rect.width / 2f,
                y = direction.end.y - rect.height / 2f,
            )
        }
        FlyingDiscardCard(
            id = id,
            card = card,
            startTopLeftInRoot = Offset(rect.left, rect.top),
            endTopLeftInRoot = end,
            widthPx = rect.width,
            heightPx = rect.height,
            staggerIndex = index,
        )
    }
}

private fun syntheticCardRect(
    tableBounds: Rect,
    index: Int,
    totalPairs: Int,
    cardWidthPx: Float,
    cardHeightPx: Float,
): Rect {
    val center = if (tableBounds.width > 1f && tableBounds.height > 1f) {
        tableBounds.center
    } else {
        Offset.Zero
    }
    val fan = (index - (totalPairs - 1) / 2f) * (cardWidthPx * 0.55f)
    val left = center.x - cardWidthPx / 2f + fan
    val top = center.y - cardHeightPx / 2f
    return Rect(left, top, left + cardWidthPx, top + cardHeightPx)
}

