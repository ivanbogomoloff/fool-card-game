package com.example.foolcardgame.domain.model

enum class Suit {
    SPADES,
    HEARTS,
    DIAMONDS,
    CLUBS,
}

enum class Rank {
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN,
    JACK,
    QUEEN,
    KING,
    ACE,
}

data class Card(
    val suit: Suit,
    val rank: Rank,
) {
    val id: String = "${suit.name}_${rank.name}"
}
