package com.example.foolcardgame.domain.model

fun cardAssetName(cardId: String): String {
    val parts = cardId.split("_")
    require(parts.size == 2) { "Unknown card id: $cardId" }
    return "card_${parts[0].lowercase()}_${parts[1].lowercase()}"
}

const val CARD_BACK_ASSET_NAME = "card_back"
