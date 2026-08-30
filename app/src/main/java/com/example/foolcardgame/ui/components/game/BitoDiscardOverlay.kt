package com.example.foolcardgame.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.components.card.CardFace
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class TableFlyawayDirection {
    data object Right : TableFlyawayDirection()
    data object Down : TableFlyawayDirection()
    data class ToTarget(val end: Offset) : TableFlyawayDirection()
}

data class FlyingDiscardCard(
    val id: String,
    val card: CardUi?,
    val startTopLeftInRoot: Offset,
    val endTopLeftInRoot: Offset,
    val widthPx: Float,
    val heightPx: Float,
    val staggerIndex: Int,
)

private const val FlyDurationMs = 700
private const val FlyStaggerMs = 50L

@Composable
fun TableCardsFlyawayOverlay(
    cards: List<FlyingDiscardCard>,
    layoutTopLeftInRoot: Offset,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return

    val density = LocalDensity.current
    val cameraDistance = with(density) { 12.dp.toPx() * density.density }

    LaunchedEffect(cards.map { it.id }) {
        val maxStagger = cards.maxOfOrNull { it.staggerIndex } ?: 0
        delay(FlyDurationMs + maxStagger * FlyStaggerMs + 50L)
        onFinished()
    }

    Box(modifier = modifier.fillMaxSize().zIndex(20f)) {
        cards.forEach { flying ->
            FlyingDiscardCardItem(
                flying = flying,
                layoutTopLeftInRoot = layoutTopLeftInRoot,
                cameraDistance = cameraDistance,
            )
        }
    }
}

@Composable
private fun FlyingDiscardCardItem(
    flying: FlyingDiscardCard,
    layoutTopLeftInRoot: Offset,
    cameraDistance: Float,
) {
    var progress by remember(flying.id) { mutableFloatStateOf(0f) }
    var rotationY by remember(flying.id) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    LaunchedEffect(flying.id) {
        delay(flying.staggerIndex * FlyStaggerMs)
        coroutineScope {
            launch {
                val anim = Animatable(0f)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FlyDurationMs,
                        easing = LinearOutSlowInEasing,
                    ),
                ) {
                    progress = value
                }
            }
            launch {
                val anim = Animatable(0f)
                anim.animateTo(
                    targetValue = 180f,
                    animationSpec = tween(
                        durationMillis = FlyDurationMs,
                        easing = LinearOutSlowInEasing,
                    ),
                ) {
                    rotationY = value
                }
            }
        }
    }

    val faceUp = rotationY <= 90f
    val displayRotation = if (faceUp) rotationY else rotationY - 180f
    val currentTopLeft = Offset(
        x = flying.startTopLeftInRoot.x +
            (flying.endTopLeftInRoot.x - flying.startTopLeftInRoot.x) * progress,
        y = flying.startTopLeftInRoot.y +
            (flying.endTopLeftInRoot.y - flying.startTopLeftInRoot.y) * progress,
    )

    CardFace(
        card = flying.card,
        faceUp = faceUp,
        width = with(density) { flying.widthPx.toDp() },
        height = with(density) { flying.heightPx.toDp() },
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (currentTopLeft.x - layoutTopLeftInRoot.x).roundToInt(),
                    y = (currentTopLeft.y - layoutTopLeftInRoot.y).roundToInt(),
                )
            }
            .graphicsLayer {
                this.rotationY = displayRotation
                this.cameraDistance = cameraDistance
                // Slight shrink into the hand.
                val scale = 1f - 0.15f * progress
                scaleX = scale
                scaleY = scale
            },
    )
}
