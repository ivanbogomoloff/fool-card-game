package com.example.foolcardgame.ui.components.card

import com.example.foolcardgame.R

fun cardFaceDrawableRes(cardId: String): Int = when (cardId) {
    "HEARTS_SIX" -> R.drawable.card_hearts_six
    "HEARTS_SEVEN" -> R.drawable.card_hearts_seven
    "HEARTS_EIGHT" -> R.drawable.card_hearts_eight
    "HEARTS_NINE" -> R.drawable.card_hearts_nine
    "HEARTS_TEN" -> R.drawable.card_hearts_ten
    "HEARTS_JACK" -> R.drawable.card_hearts_jack
    "HEARTS_QUEEN" -> R.drawable.card_hearts_queen
    "HEARTS_KING" -> R.drawable.card_hearts_king
    "HEARTS_ACE" -> R.drawable.card_hearts_ace
    "DIAMONDS_SIX" -> R.drawable.card_diamonds_six
    "DIAMONDS_SEVEN" -> R.drawable.card_diamonds_seven
    "DIAMONDS_EIGHT" -> R.drawable.card_diamonds_eight
    "DIAMONDS_NINE" -> R.drawable.card_diamonds_nine
    "DIAMONDS_TEN" -> R.drawable.card_diamonds_ten
    "DIAMONDS_JACK" -> R.drawable.card_diamonds_jack
    "DIAMONDS_QUEEN" -> R.drawable.card_diamonds_queen
    "DIAMONDS_KING" -> R.drawable.card_diamonds_king
    "DIAMONDS_ACE" -> R.drawable.card_diamonds_ace
    "CLUBS_SIX" -> R.drawable.card_clubs_six
    "CLUBS_SEVEN" -> R.drawable.card_clubs_seven
    "CLUBS_EIGHT" -> R.drawable.card_clubs_eight
    "CLUBS_NINE" -> R.drawable.card_clubs_nine
    "CLUBS_TEN" -> R.drawable.card_clubs_ten
    "CLUBS_JACK" -> R.drawable.card_clubs_jack
    "CLUBS_QUEEN" -> R.drawable.card_clubs_queen
    "CLUBS_KING" -> R.drawable.card_clubs_king
    "CLUBS_ACE" -> R.drawable.card_clubs_ace
    "SPADES_SIX" -> R.drawable.card_spades_six
    "SPADES_SEVEN" -> R.drawable.card_spades_seven
    "SPADES_EIGHT" -> R.drawable.card_spades_eight
    "SPADES_NINE" -> R.drawable.card_spades_nine
    "SPADES_TEN" -> R.drawable.card_spades_ten
    "SPADES_JACK" -> R.drawable.card_spades_jack
    "SPADES_QUEEN" -> R.drawable.card_spades_queen
    "SPADES_KING" -> R.drawable.card_spades_king
    "SPADES_ACE" -> R.drawable.card_spades_ace
    else -> error("Unknown card id: $cardId")
}

fun cardBackDrawableRes(): Int = R.drawable.card_back
