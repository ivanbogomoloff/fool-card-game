package com.example.foolcardgame.domain.model

enum class GamePhase {
    LOBBY_WAITING,
    IN_PROGRESS,
    FINISHED,
}

enum class PlayerStatus {
    WAITING,
    PLAYING,
    DISCONNECTED,
    LEFT,
}
