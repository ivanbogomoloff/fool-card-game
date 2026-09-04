package com.example.foolcardgame.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardAssetNamesTest {

    @Test
    fun cardAssetName_mapsAllDurakCards() {
        val suits = listOf("HEARTS", "DIAMONDS", "CLUBS", "SPADES")
        val ranks = listOf("SIX", "SEVEN", "EIGHT", "NINE", "TEN", "JACK", "QUEEN", "KING", "ACE")

        suits.forEach { suit ->
            ranks.forEach { rank ->
                val cardId = "${suit}_$rank"
                assertEquals(
                    "card_${suit.lowercase()}_${rank.lowercase()}",
                    cardAssetName(cardId),
                )
            }
        }
    }

    @Test
    fun cardAssetName_examples() {
        assertEquals("card_hearts_ace", cardAssetName("HEARTS_ACE"))
        assertEquals("card_spades_six", cardAssetName("SPADES_SIX"))
    }
}
