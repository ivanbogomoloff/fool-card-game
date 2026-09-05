package com.example.foolcardgame.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.presentation.game.OpponentActionUi
import com.example.foolcardgame.presentation.game.OpponentPulseUi
import com.example.foolcardgame.presentation.game.OpponentRoleBanner
import com.example.foolcardgame.presentation.game.OpponentUi
import com.example.foolcardgame.ui.components.card.CardFace
import com.example.foolcardgame.ui.screens.profile.AvatarPresets
import com.example.foolcardgame.ui.theme.OpponentPlate
import com.example.foolcardgame.ui.theme.SoftCoral
import com.example.foolcardgame.ui.theme.TrumpGold
import kotlinx.coroutines.delay

private const val NamePlateAutoCollapseMs = 10_000L
private const val NamePlatePulsePeakScale = 1.12f
private const val NamePlatePulseUpMs = 120
private const val NamePlatePulseDownMs = 220

private enum class OpponentSeat {
    Left,
    Top,
    Right,
}

@Composable
fun OpponentsRow(
    opponents: List<OpponentUi>,
    phase: GamePhase,
    opponentAction: OpponentActionUi? = null,
    opponentPulse: OpponentPulseUi? = null,
    loserId: String? = null,
    localPlayerId: String = "",
    showLoserCards: Boolean = false,
    revealLoserCards: List<CardUi> = emptyList(),
    onOpponentAvatarBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val left = when (opponents.size) {
        2, 3 -> opponents.getOrNull(0)
        else -> null
    }
    val top = when (opponents.size) {
        1 -> opponents.getOrNull(0)
        in 3..Int.MAX_VALUE -> opponents.getOrNull(1)
        else -> null
    }
    val right = when (opponents.size) {
        2 -> opponents.getOrNull(1)
        in 3..Int.MAX_VALUE -> opponents.getOrNull(2)
        else -> null
    }

    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        if (left != null) {
            OpponentSeatView(
                opponent = left,
                seat = OpponentSeat.Left,
                phase = phase,
                loserId = loserId,
                localPlayerId = localPlayerId,
                showLoserCards = showLoserCards,
                revealLoserCards = revealLoserCards,
                opponentAction = opponentAction,
                opponentPulse = opponentPulse,
                onOpponentAvatarBoundsChanged = onOpponentAvatarBoundsChanged,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp, bottom = 48.dp),
            )
        }
        if (top != null) {
            OpponentSeatView(
                opponent = top,
                seat = OpponentSeat.Top,
                phase = phase,
                loserId = loserId,
                localPlayerId = localPlayerId,
                showLoserCards = showLoserCards,
                revealLoserCards = revealLoserCards,
                opponentAction = opponentAction,
                opponentPulse = opponentPulse,
                onOpponentAvatarBoundsChanged = onOpponentAvatarBoundsChanged,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
        }
        if (right != null) {
            OpponentSeatView(
                opponent = right,
                seat = OpponentSeat.Right,
                phase = phase,
                loserId = loserId,
                localPlayerId = localPlayerId,
                showLoserCards = showLoserCards,
                revealLoserCards = revealLoserCards,
                opponentAction = opponentAction,
                opponentPulse = opponentPulse,
                onOpponentAvatarBoundsChanged = onOpponentAvatarBoundsChanged,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp, bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun OpponentSeatView(
    opponent: OpponentUi,
    seat: OpponentSeat,
    phase: GamePhase,
    loserId: String?,
    localPlayerId: String,
    showLoserCards: Boolean,
    revealLoserCards: List<CardUi>,
    opponentAction: OpponentActionUi?,
    opponentPulse: OpponentPulseUi?,
    onOpponentAvatarBoundsChanged: (String, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoserMarked = phase == GamePhase.FINISHED &&
        opponent.id == loserId &&
        loserId != localPlayerId
    val showRevealed = showLoserCards &&
        opponent.id == loserId &&
        revealLoserCards.isNotEmpty()
    val actionMessage = if (opponentAction?.opponentId == opponent.id) {
        opponentAction.message
    } else {
        null
    }
    val pulseAtTick = if (opponentPulse?.opponentId == opponent.id) {
        opponentPulse.atTick
    } else {
        null
    }
    val visibleBacks = opponent.cardCount.coerceIn(0, 6)
    val horizontalFan = seat == OpponentSeat.Top

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OpponentRoleIcon(roleBanner = opponent.roleBanner)
        key(opponent.id) {
            OpponentNamePlate(
                opponent = opponent,
                isLoserMarked = isLoserMarked,
                actionMessage = actionMessage,
                pulseAtTick = pulseAtTick,
                onBoundsChanged = { bounds ->
                    onOpponentAvatarBoundsChanged(opponent.id, bounds)
                },
            )
        }
        if (!opponent.isConnected) {
            Text(
                text = "Нет сети",
                style = MaterialTheme.typography.labelSmall,
                color = SoftCoral,
            )
        }
        OpponentCardsFan(
            showRevealedCards = showRevealed,
            revealLoserCards = revealLoserCards,
            visibleBacks = visibleBacks,
            horizontal = horizontalFan,
        )
    }
}

@Composable
private fun OpponentRoleIcon(roleBanner: OpponentRoleBanner) {
    val (icon, tint) = when (roleBanner) {
        OpponentRoleBanner.ATTACKING -> Icons.Filled.SportsMma to SoftCoral
        OpponentRoleBanner.DEFENDING -> Icons.Filled.Shield to TrumpGold
        OpponentRoleBanner.NONE -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun OpponentNamePlate(
    opponent: OpponentUi,
    isLoserMarked: Boolean,
    actionMessage: String?,
    pulseAtTick: Long?,
    onBoundsChanged: (Rect) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(NamePlateAutoCollapseMs)
            expanded = false
        }
    }
    LaunchedEffect(pulseAtTick) {
        if (pulseAtTick == null) return@LaunchedEffect
        pulseScale.snapTo(1f)
        pulseScale.animateTo(
            targetValue = NamePlatePulsePeakScale,
            animationSpec = tween(durationMillis = NamePlatePulseUpMs),
        )
        pulseScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = NamePlatePulseDownMs),
        )
    }

    val useCompactName = actionMessage == null && !isLoserMarked
    val (line1, line2) = if (useCompactName) {
        formatCompactOpponentName(opponent.displayName)
    } else {
        (actionMessage ?: "Дурак") to null
    }
    val showCardCount = opponent.cardCount > 0 && actionMessage == null && !isLoserMarked
    val avatar = AvatarPresets.get(opponent.avatarId)

    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = pulseScale.value
                scaleY = pulseScale.value
            }
            .background(OpponentPlate, RoundedCornerShape(10.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInRoot())
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (expanded) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(avatar.backgroundColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = avatar.emoji,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = opponent.displayName,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = line1,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (line2 != null) {
                Text(
                    text = line2,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (showCardCount) {
            Text(
                text = "${opponent.cardCount} КАРТ",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OpponentCardsFan(
    showRevealedCards: Boolean,
    revealLoserCards: List<CardUi>,
    visibleBacks: Int,
    horizontal: Boolean,
) {
    val cardCount = if (showRevealedCards) revealLoserCards.size else visibleBacks
    if (cardCount == 0) return

    val cardWidth = 28.dp
    val cardHeight = 40.dp
    val step = 10.dp
    val boxModifier = if (horizontal) {
        Modifier.width(cardWidth + step * (cardCount - 1).coerceAtLeast(0))
    } else {
        Modifier.size(
            width = cardWidth,
            height = cardHeight + step * (cardCount - 1).coerceAtLeast(0),
        )
    }

    Box(modifier = boxModifier) {
        if (showRevealedCards) {
            revealLoserCards.forEachIndexed { index, card ->
                CardFace(
                    card = card,
                    faceUp = true,
                    width = cardWidth,
                    height = cardHeight,
                    modifier = Modifier
                        .offset(
                            x = if (horizontal) step * index else 0.dp,
                            y = if (horizontal) 0.dp else step * index,
                        )
                        .zIndex(index.toFloat()),
                )
            }
        } else {
            repeat(visibleBacks) { index ->
                CardFace(
                    card = null,
                    faceUp = false,
                    width = cardWidth,
                    height = cardHeight,
                    modifier = Modifier
                        .offset(
                            x = if (horizontal) step * index else 0.dp,
                            y = if (horizontal) 0.dp else step * index,
                        )
                        .zIndex(index.toFloat()),
                )
            }
        }
    }
}
