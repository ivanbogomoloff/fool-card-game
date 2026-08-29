package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.OpponentUi
import com.example.foolcardgame.ui.screens.profile.AvatarPresets

@Composable
fun OpponentsRow(
    opponents: List<OpponentUi>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        opponents.forEach { opponent ->
            OpponentItem(opponent = opponent)
        }
    }
}

@Composable
private fun OpponentItem(
    opponent: OpponentUi,
    modifier: Modifier = Modifier,
) {
    val avatar = AvatarPresets.get(opponent.avatarId)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = avatar.emoji, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = opponent.displayName,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = "${opponent.cardCount} карт",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
