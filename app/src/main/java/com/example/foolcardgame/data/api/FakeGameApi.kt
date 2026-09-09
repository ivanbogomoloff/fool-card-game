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
import com.example.foolcardgame.data.api.dto.RoomPlayerDto
import com.example.foolcardgame.data.api.dto.RoomStateDto
import com.example.foolcardgame.data.client.MockGameStates
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-process stub REST для Phase 5. */
class FakeGameApi(
    /** После скольких попыток fast/join вернуть сессию. */
    private val fastJoinSuccessAfterAttempts: Int = 2,
) : GameApi {

    private val mutex = Mutex()
    private var lastDisplayName: String = "Игрок"
    private val fastJoinAttempts = mutableMapOf<String, Int>()
    private val rooms = mutableMapOf<String, MutableRoom>()
    private val codeToSession = mutableMapOf<String, String>()

    override suspend fun login(request: LoginRequestDto): LoginResponseDto = mutex.withLock {
        val name = request.displayName?.takeIf { it.isNotBlank() } ?: lastDisplayName
        lastDisplayName = name
        LoginResponseDto(
            token = "fake-${UUID.randomUUID()}",
            displayName = name,
            avatarId = 0,
        )
    }

    override suspend fun fastJoin(
        token: String,
        profile: PlayerProfileRequestDto,
    ): FastJoinResponseDto? = mutex.withLock {
        requireToken(token)
        val attempts = (fastJoinAttempts[token] ?: 0) + 1
        fastJoinAttempts[token] = attempts
        if (attempts < fastJoinSuccessAfterAttempts) return@withLock null
        fastJoinAttempts.remove(token)
        val sessionId = nextOnlineSessionId()
        val hostId = "p-${UUID.randomUUID().toString().take(8)}"
        rooms[sessionId] = MutableRoom(
            sessionId = sessionId,
            accessCode = randomCode(),
            hostId = hostId,
            started = true,
            players = mutableListOf(
                RoomPlayerDto(
                    id = hostId,
                    displayName = profile.displayName,
                    avatarId = profile.avatarId,
                    isHost = true,
                ),
            ),
        )
        FastJoinResponseDto(sessionId = sessionId)
    }

    override suspend fun createGame(
        token: String,
        profile: PlayerProfileRequestDto,
    ): CreateGameResponseDto = mutex.withLock {
        requireToken(token)
        val sessionId = nextOnlineSessionId()
        val hostId = "p-${UUID.randomUUID().toString().take(8)}"
        val code = randomCode()
        rooms[sessionId] = MutableRoom(
            sessionId = sessionId,
            accessCode = code,
            hostId = hostId,
            started = false,
            players = mutableListOf(
                RoomPlayerDto(
                    id = hostId,
                    displayName = profile.displayName,
                    avatarId = profile.avatarId,
                    isHost = true,
                ),
            ),
        )
        codeToSession[code] = sessionId
        CreateGameResponseDto(sessionId = sessionId, accessCode = code, hostId = hostId)
    }

    override suspend fun joinGame(
        token: String,
        request: JoinByCodeRequestDto,
    ): JoinGameResponseDto = mutex.withLock {
        requireToken(token)
        val sessionId = codeToSession[request.code.trim().uppercase()]
            ?: error("Комната с таким кодом не найдена")
        val room = rooms[sessionId] ?: error("Комната не найдена")
        require(!room.started) { "Игра уже началась" }
        val playerId = "p-${UUID.randomUUID().toString().take(8)}"
        room.players.add(
            RoomPlayerDto(
                id = playerId,
                displayName = request.displayName,
                avatarId = request.avatarId,
                isHost = false,
            ),
        )
        JoinGameResponseDto(sessionId = sessionId, playerId = playerId)
    }

    override suspend fun getRoom(token: String, sessionId: String): RoomStateDto = mutex.withLock {
        requireToken(token)
        rooms[sessionId]?.toDto() ?: error("Комната не найдена")
    }

    override suspend fun kickPlayer(
        token: String,
        sessionId: String,
        request: KickPlayerRequestDto,
    ) {
        mutex.withLock {
            requireToken(token)
            val room = rooms[sessionId] ?: error("Комната не найдена")
            require(request.playerId != room.hostId) { "Нельзя удалить хоста" }
            room.players.removeAll { it.id == request.playerId }
        }
    }

    override suspend fun startGame(token: String, sessionId: String) {
        mutex.withLock {
            requireToken(token)
            val room = rooms[sessionId] ?: error("Комната не найдена")
            require(room.players.size >= 2) { "Нужно минимум 2 игрока" }
            room.started = true
        }
    }

    override suspend fun getState(token: String, sessionId: String): GameStateDto = mutex.withLock {
        requireToken(token)
        MockGameStates.lobbyWaiting().copy(sessionId = sessionId)
    }

    private fun requireToken(token: String) {
        require(token.isNotBlank()) { "Unauthorized" }
    }

    private fun nextOnlineSessionId(): String = "online-${UUID.randomUUID()}"

    private fun randomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }

    private data class MutableRoom(
        val sessionId: String,
        val accessCode: String,
        val hostId: String,
        var started: Boolean,
        val players: MutableList<RoomPlayerDto>,
    ) {
        fun toDto() = RoomStateDto(
            sessionId = sessionId,
            accessCode = accessCode,
            hostId = hostId,
            started = started,
            players = players.toList(),
        )
    }
}
