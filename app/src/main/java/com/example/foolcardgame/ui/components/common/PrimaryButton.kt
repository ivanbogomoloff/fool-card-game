package com.example.foolcardgame.ui.components.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foolcardgame.ui.theme.AccentTeal
import com.example.foolcardgame.ui.theme.CardCream

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = if (fillWidth) {
            modifier.fillMaxWidth()
        } else {
            modifier
        }.padding(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentTeal,
            contentColor = CardCream,
        ),
    ) {
        Text(text = text)
    }
}
