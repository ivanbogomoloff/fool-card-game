package com.example.foolcardgame.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.presentation.game.GameHistoryEntryUi
import com.example.foolcardgame.ui.theme.SoftCharcoal

@Composable
fun GameHistoryPanel(
    entries: List<GameHistoryEntryUi>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 140.dp)
            .background(
                color = Color.White.copy(alpha = 0.88f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            Text(
                text = entry.text,
                style = MaterialTheme.typography.labelSmall,
                color = SoftCharcoal,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
