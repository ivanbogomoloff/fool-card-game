package com.example.foolcardgame.ui.screens.profile

import androidx.compose.ui.graphics.Color
import com.example.foolcardgame.domain.model.UserProfile

object AvatarPresets {
    data class AvatarOption(
        val id: Int,
        val emoji: String,
        val backgroundColor: Color,
    )

    val options: List<AvatarOption> = listOf(
        AvatarOption(0, "🎴", Color(0xFF6B9E9B)),
        AvatarOption(1, "🃏", Color(0xFFC9A962)),
        AvatarOption(2, "♠", Color(0xFF5C6B73)),
        AvatarOption(3, "♥", Color(0xFFD4847C)),
        AvatarOption(4, "♦", Color(0xFF8B6B9E)),
        AvatarOption(5, "♣", Color(0xFF4A7C59)),
        AvatarOption(6, "🙂", Color(0xFF7BA3C9)),
        AvatarOption(7, "🦊", Color(0xFFC9856B)),
    )

    fun get(id: Int): AvatarOption {
        return options.getOrElse(id) { options.first() }
    }

    init {
        require(options.size == UserProfile.AVATAR_COUNT)
    }
}
