package com.example.foolcardgame.presentation.game

import com.example.foolcardgame.data.api.dto.GameActionEventDto
import com.example.foolcardgame.data.api.dto.GameActionKindDto
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.PlayerStateDto

internal fun opponentBadgeMessage(kind: GameActionKindDto): String? = when (kind) {
    GameActionKindDto.PASS -> "Пас"
    GameActionKindDto.TOOK -> "Беру"
    GameActionKindDto.BITO -> "Бито"
    GameActionKindDto.ATTACK,
    GameActionKindDto.DEFEND,
    GameActionKindDto.THROW_IN,
    -> null
}

internal fun formatGameHistoryLine(
    playerName: String,
    kind: GameActionKindDto,
    timestampMs: Long,
): String {
    val action = when (kind) {
        GameActionKindDto.ATTACK -> "атакует"
        GameActionKindDto.DEFEND -> "отбивает"
        GameActionKindDto.THROW_IN -> "подкидывает"
        GameActionKindDto.PASS -> "пас"
        GameActionKindDto.TOOK -> "взял"
        GameActionKindDto.BITO -> "бито"
    }
    val time = GameHistoryFormatter.formatTime(timestampMs)
    return "$time: $playerName $action"
}

internal fun GameActionEventDto.historyKey(): String = "${atTick}_${kind}_$playerId"

internal fun GameStateDto.playerName(playerId: String): String =
    players.find { it.id == playerId }?.displayName ?: playerId

object GameHistoryFormatter {
    fun formatTime(timestampMs: Long): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        return java.time.Instant.ofEpochMilli(timestampMs)
            .atZone(java.time.ZoneId.systemDefault())
            .format(formatter)
    }
}

internal fun GameActionEventDto.toHistoryEntry(
    players: List<PlayerStateDto>,
    timestampMs: Long,
): GameHistoryEntryUi {
    val playerName = players.find { it.id == playerId }?.displayName ?: playerId
    return GameHistoryEntryUi(
        id = historyKey(),
        text = formatGameHistoryLine(playerName, kind, timestampMs),
        timestampMs = timestampMs,
    )
}
