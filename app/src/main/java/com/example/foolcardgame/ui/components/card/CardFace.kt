package com.example.foolcardgame.ui.components.card

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.domain.model.CardTheme
import com.example.foolcardgame.presentation.game.CardUi
import com.example.foolcardgame.ui.theme.FoolCardGameTheme
import com.example.foolcardgame.ui.theme.LocalCardTheme

@Composable
fun CardFace(
    card: CardUi?,
    modifier: Modifier = Modifier,
    faceUp: Boolean = true,
    selected: Boolean = false,
    width: Dp = 56.dp,
    height: Dp = 80.dp,
    scaleOverride: Float? = null,
    onClick: (() -> Unit)? = null,
) {
    when (LocalCardTheme.current) {
        CardTheme.ILLUSTRATED -> IllustratedCardFace(
            card = card,
            modifier = modifier,
            faceUp = faceUp,
            selected = selected,
            width = width,
            height = height,
            scaleOverride = scaleOverride,
            onClick = onClick,
        )
        CardTheme.MINIMAL -> MinimalCardFace(
            card = card,
            modifier = modifier,
            faceUp = faceUp,
            selected = selected,
            width = width,
            height = height,
            scaleOverride = scaleOverride,
            onClick = onClick,
        )
    }
}

@Preview
@Composable
private fun CardFacePreview() {
    FoolCardGameTheme {
        CardFace(
            card = CardUi("SPADES_KING", "K", "♠", false),
            modifier = Modifier.padding(8.dp),
            selected = true,
        )
    }
}
