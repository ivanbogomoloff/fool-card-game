package com.example.foolcardgame.ui.components.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.FoolCardGameTheme
import com.example.foolcardgame.ui.theme.TrumpGold

@Composable
fun MinimalCardFace(
    card: CardUi?,
    modifier: Modifier = Modifier,
    faceUp: Boolean = true,
    selected: Boolean = false,
    width: Dp = 56.dp,
    height: Dp = 80.dp,
    scaleOverride: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    val scale by animateFloatAsState(
        targetValue = scaleOverride ?: if (selected) 1.08f else 1f,
        label = "cardScale",
    )
    val shape = RoundedCornerShape(8.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val backgroundModifier = if (faceUp) {
        Modifier.background(surfaceColor)
    } else {
        Modifier.background(minimalCardBackBrush())
    }
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .scale(scale)
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) TrumpGold else AccentTeal.copy(alpha = 0.4f),
                shape = shape,
            )
            .then(backgroundModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (faceUp && card != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = card.rankLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (card.isRed) Color(0xFFC75050) else onSurfaceColor,
                    fontSize = (height.value * 0.22f).sp,
                )
                Text(
                    text = card.suitSymbol,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (card.isRed) Color(0xFFC75050) else onSurfaceColor,
                    fontSize = (height.value * 0.28f).sp,
                )
            }
        } else if (!faceUp) {
            Text(
                text = "🂠",
                fontSize = (height.value * 0.3f).sp,
            )
        }
    }
}

@Composable
fun minimalCardBackBrush(): Brush = Brush.verticalGradient(
    colors = listOf(AccentTeal.copy(alpha = 0.85f), AccentTeal),
)

@Preview
@Composable
private fun MinimalCardFacePreview() {
    FoolCardGameTheme {
        MinimalCardFace(
            card = CardUi("SPADES_KING", "K", "♠", false),
            modifier = Modifier.padding(8.dp),
            selected = true,
        )
    }
}
