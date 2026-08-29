package com.example.foolcardgame.data.api.dto

import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.PlayerStatus
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import kotlinx.serialization.Serializable

typealias GameSessionId = String

@Serializable
enum class SuitDto {
    SPADES,
    HEARTS,
    DIAMONDS,
    CLUBS,
}

@Serializable
enum class RankDto {
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

@Serializable
enum class GamePhaseDto {
    LOBBY_WAITING,
    IN_PROGRESS,
    FINISHED,
}

@Serializable
enum class PlayerStatusDto {
    WAITING,
    PLAYING,
    DISCONNECTED,
    LEFT,
}

@Serializable
data class CardDto(
    val suit: SuitDto,
    val rank: RankDto,
)

@Serializable
data class TablePairDto(
    val id: Int,
    val attack: CardDto,
    val defense: CardDto? = null,
)

@Serializable
data class PlayerStateDto(
    val id: String,
    val displayName: String,
    val avatarId: Int,
    val handCount: Int,
    val isReady: Boolean,
    val isConnected: Boolean,
    val status: PlayerStatusDto,
)

@Serializable
data class GameStateDto(
    val sessionId: String,
    val phase: GamePhaseDto,
    val players: List<PlayerStateDto>,
    val localPlayerId: String,
    val serverTick: Long? = null,
    val deckCount: Int = 0,
    val trump: CardDto? = null,
    val tablePairs: List<TablePairDto> = emptyList(),
    val localHand: List<CardDto> = emptyList(),
    val currentPlayerId: String? = null,
    val attackerId: String? = null,
    val defenderId: String? = null,
    val canBito: Boolean = false,
    val canPass: Boolean = false,
    val canTake: Boolean = false,
    val canReady: Boolean = false,
    val winnerName: String? = null,
    val loserName: String? = null,
    val turnDeadlineAtMs: Long? = null,
)

fun CardDto.toDomain(): Card = Card(suit = suit.toDomain(), rank = rank.toDomain())
fun Card.toDto(): CardDto = CardDto(suit = suit.toDto(), rank = rank.toDto())

fun SuitDto.toDomain(): Suit = Suit.valueOf(name)
fun Suit.toDto(): SuitDto = SuitDto.valueOf(name)

fun RankDto.toDomain(): Rank = Rank.valueOf(name)
fun Rank.toDto(): RankDto = RankDto.valueOf(name)

fun GamePhaseDto.toDomain(): GamePhase = GamePhase.valueOf(name)
fun GamePhase.toDto(): GamePhaseDto = GamePhaseDto.valueOf(name)

fun PlayerStatusDto.toDomain(): PlayerStatus = PlayerStatus.valueOf(name)
