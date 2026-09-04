package com.example.foolcardgame.ui.components.card

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.FoolCardGameTheme
import com.example.foolcardgame.ui.theme.TrumpGold

@Composable
fun IllustratedCardFace(
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
    val drawableRes = when {
        faceUp && card != null -> cardFaceDrawableRes(card.id)
        else -> cardBackDrawableRes()
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
            ),
    ) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview
@Composable
private fun IllustratedCardFacePreview() {
    FoolCardGameTheme {
        IllustratedCardFace(
            card = CardUi("SPADES_KING", "K", "♠", false),
            modifier = Modifier.padding(8.dp),
            selected = true,
        )
    }
}
