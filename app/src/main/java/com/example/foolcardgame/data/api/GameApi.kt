package com.example.foolcardgame.data.api

import com.example.foolcardgame.data.api.dto.CreateGameResponseDto
import com.example.foolcardgame.data.api.dto.FastJoinResponseDto
import com.example.foolcardgame.data.api.dto.GameStateDto
import com.example.foolcardgame.data.api.dto.JoinByCodeRequestDto
import com.example.foolcardgame.data.api.dto.JoinGameResponseDto
import com.example.foolcardgame.data.api.dto.KickPlayerRequestDto
import com.example.foolcardgame.data.api.dto.LoginRequestDto
import com.example.foolcardgame.data.api.dto.LoginResponseDto
import com.example.foolcardgame.data.api.dto.PlayerProfileRequestDto
import com.example.foolcardgame.data.api.dto.RoomStateDto

/**
 * REST-контракт игрового API (Phase 5 — stub, Phase 6 — Retrofit).
 * Префикс маршрутов: `/game/`.
 */
interface GameApi {
    suspend fun login(request: LoginRequestDto): LoginResponseDto

    /** POST /game/fast/join — null означает «ещё ждём» (204). */
    suspend fun fastJoin(token: String, profile: PlayerProfileRequestDto): FastJoinResponseDto?

    /** POST /game/create */
    suspend fun createGame(token: String, profile: PlayerProfileRequestDto): CreateGameResponseDto

    /** POST /game/join */
    suspend fun joinGame(token: String, request: JoinByCodeRequestDto): JoinGameResponseDto

    /** GET /game/{id}/room */
    suspend fun getRoom(token: String, sessionId: String): RoomStateDto

    /** POST /game/{id}/kick */
    suspend fun kickPlayer(token: String, sessionId: String, request: KickPlayerRequestDto)

    /** POST /game/{id}/start */
    suspend fun startGame(token: String, sessionId: String)

    /** GET /game/{id}/state */
    suspend fun getState(token: String, sessionId: String): GameStateDto
}
