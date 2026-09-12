package com.example.foolcardgame.data.network

import com.example.foolcardgame.data.api.dto.CardDto
import com.example.foolcardgame.data.api.dto.GameActionEventDto
import com.example.foolcardgame.data.api.dto.GameActionKindDto
import com.example.foolcardgame.data.api.dto.GamePhaseDto
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.PlayerStateDto
import com.example.foolcardgame.data.api.dto.PlayerStatusDto
import com.example.foolcardgame.data.api.dto.RankDto
import com.example.foolcardgame.data.api.dto.RoomPlayerDto
import com.example.foolcardgame.data.api.dto.RoomStateDto
import com.example.foolcardgame.data.api.dto.RoundEventDto
import com.example.foolcardgame.data.api.dto.RoundEventKindDto
import com.example.foolcardgame.data.api.dto.SuitDto
import com.example.foolcardgame.data.api.dto.TablePairDto
import com.example.foolcardgame.data.client.OnlineSessionIds
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.Rank
import com.example.foolcardgame.domain.model.Suit
import foolcard.v1.Game
import foolcard.v1.Session

/** Proto ↔ клиентские DTO. */
object ProtoMappers {

    fun toRoomStateDto(room: Game.RoomState): RoomStateDto =
        RoomStateDto(
            sessionId = OnlineSessionIds.wrap(room.gameId),
            accessCode = room.accessCode,
            hostId = room.hostId,
            started = room.started,
            players = room.playersList.map { p ->
                RoomPlayerDto(
                    id = p.id,
                    displayName = p.username,
                    avatarId = p.avatarId,
                    isHost = p.isHost,
                )
            },
        )

    fun toGameStateDto(state: Game.GameState): GameStateDto =
        GameStateDto(
            sessionId = OnlineSessionIds.wrap(state.gameId),
            phase = state.phase.toDto(),
            players = state.playersList.map { it.toDto() },
            localPlayerId = state.localPlayerId,
            serverTick = if (state.hasServerTick()) state.serverTick else null,
            deckCount = state.deckCount,
            trump = if (state.hasTrump()) state.trump.toDto() else null,
            tablePairs = state.tablePairsList.map { it.toDto() },
            localHand = state.localHandList.map { it.toDto() },
            currentPlayerId = if (state.hasCurrentPlayerId()) state.currentPlayerId else null,
            attackerId = if (state.hasAttackerId()) state.attackerId else null,
            defenderId = if (state.hasDefenderId()) state.defenderId else null,
            canBito = state.canBito,
            canPass = state.canPass,
            canTake = state.canTake,
            canReady = state.canReady,
            winnerName = if (state.hasWinnerName()) state.winnerName else null,
            loserName = if (state.hasLoserName()) state.loserName else null,
            loserId = if (state.hasLoserId()) state.loserId else null,
            isDraw = state.isDraw,
            revealLoserCards = state.revealLoserCardsList.map { it.toDto() },
            turnDeadlineAtMs = if (state.hasTurnDeadlineAtMs()) state.turnDeadlineAtMs else null,
            roundEvent = if (state.hasRoundEvent()) state.roundEvent.toDto() else null,
            actionEvent = if (state.hasActionEvent()) state.actionEvent.toDto() else null,
        )

    fun toProtoCard(card: Card): Game.Card =
        Game.Card.newBuilder()
            .setSuit(card.suit.toProto())
            .setRank(card.rank.toProto())
            .build()

    fun queuePhaseName(phase: Game.QueuePhase): String =
        when (phase) {
            Game.QueuePhase.SEARCHING -> "SEARCHING"
            Game.QueuePhase.FILLING -> "FILLING"
            else -> "UNKNOWN"
        }

    private fun Game.PlayerState.toDto(): PlayerStateDto =
        PlayerStateDto(
            id = id,
            displayName = username,
            avatarId = avatarId,
            handCount = handCount,
            isReady = isReady,
            isConnected = isConnected,
            status = status.toDto(),
        )

    private fun Game.TablePair.toDto(): TablePairDto =
        TablePairDto(
            id = id,
            attack = attack.toDto(),
            defense = if (hasDefense()) defense.toDto() else null,
        )

    private fun Game.Card.toDto(): CardDto =
        CardDto(suit = suit.toDto(), rank = rank.toDto())

    private fun Game.RoundEvent.toDto(): RoundEventDto =
        RoundEventDto(
            kind = when (kind) {
                Game.RoundEventKind.TOOK -> RoundEventKindDto.TOOK
                Game.RoundEventKind.BITO -> RoundEventKindDto.BITO
                else -> RoundEventKindDto.TOOK
            },
            playerId = playerId,
            atTick = atTick,
        )

    private fun Game.GameActionEvent.toDto(): GameActionEventDto =
        GameActionEventDto(
            kind = when (kind) {
                Game.GameActionKind.ATTACK -> GameActionKindDto.ATTACK
                Game.GameActionKind.DEFEND -> GameActionKindDto.DEFEND
                Game.GameActionKind.THROW_IN -> GameActionKindDto.THROW_IN
                Game.GameActionKind.PASS -> GameActionKindDto.PASS
                Game.GameActionKind.TOOK_ACTION -> GameActionKindDto.TOOK
                Game.GameActionKind.BITO_ACTION -> GameActionKindDto.BITO
                else -> GameActionKindDto.PASS
            },
            playerId = playerId,
            atTick = atTick,
        )

    private fun Game.GamePhase.toDto(): GamePhaseDto =
        when (this) {
            Game.GamePhase.LOBBY_WAITING -> GamePhaseDto.LOBBY_WAITING
            Game.GamePhase.IN_PROGRESS -> GamePhaseDto.IN_PROGRESS
            Game.GamePhase.FINISHED -> GamePhaseDto.FINISHED
            else -> GamePhaseDto.LOBBY_WAITING
        }

    private fun Game.PlayerStatus.toDto(): PlayerStatusDto =
        when (this) {
            Game.PlayerStatus.WAITING -> PlayerStatusDto.WAITING
            Game.PlayerStatus.PLAYING -> PlayerStatusDto.PLAYING
            Game.PlayerStatus.DISCONNECTED -> PlayerStatusDto.DISCONNECTED
            Game.PlayerStatus.LEFT -> PlayerStatusDto.LEFT
            else -> PlayerStatusDto.WAITING
        }

    private fun Game.Suit.toDto(): SuitDto =
        when (this) {
            Game.Suit.SPADES -> SuitDto.SPADES
            Game.Suit.HEARTS -> SuitDto.HEARTS
            Game.Suit.DIAMONDS -> SuitDto.DIAMONDS
            Game.Suit.CLUBS -> SuitDto.CLUBS
            else -> SuitDto.SPADES
        }

    private fun Game.Rank.toDto(): RankDto =
        when (this) {
            Game.Rank.SIX -> RankDto.SIX
            Game.Rank.SEVEN -> RankDto.SEVEN
            Game.Rank.EIGHT -> RankDto.EIGHT
            Game.Rank.NINE -> RankDto.NINE
            Game.Rank.TEN -> RankDto.TEN
            Game.Rank.JACK -> RankDto.JACK
            Game.Rank.QUEEN -> RankDto.QUEEN
            Game.Rank.KING -> RankDto.KING
            Game.Rank.ACE -> RankDto.ACE
            else -> RankDto.SIX
        }

    private fun Suit.toProto(): Game.Suit =
        when (this) {
            Suit.SPADES -> Game.Suit.SPADES
            Suit.HEARTS -> Game.Suit.HEARTS
            Suit.DIAMONDS -> Game.Suit.DIAMONDS
            Suit.CLUBS -> Game.Suit.CLUBS
        }

    private fun Rank.toProto(): Game.Rank =
        when (this) {
            Rank.SIX -> Game.Rank.SIX
            Rank.SEVEN -> Game.Rank.SEVEN
            Rank.EIGHT -> Game.Rank.EIGHT
            Rank.NINE -> Game.Rank.NINE
            Rank.TEN -> Game.Rank.TEN
            Rank.JACK -> Game.Rank.JACK
            Rank.QUEEN -> Game.Rank.QUEEN
            Rank.KING -> Game.Rank.KING
            Rank.ACE -> Game.Rank.ACE
        }

    fun serverPayloadCase(message: Session.ServerMessage): Session.ServerMessage.PayloadCase =
        message.payloadCase
}
