package com.example.foolcardgame.domain.engine

import com.example.foolcardgame.domain.bot.BotAI
import com.example.foolcardgame.domain.model.ActionPermissions
import com.example.foolcardgame.domain.model.Card
import com.example.foolcardgame.domain.model.GameActionEvent
import com.example.foolcardgame.domain.model.GameActionKind
import com.example.foolcardgame.domain.model.GameConfig
import com.example.foolcardgame.domain.model.GamePhase
import com.example.foolcardgame.domain.model.GameState
import com.example.foolcardgame.domain.model.Player
import com.example.foolcardgame.domain.model.PlayerStatus
import com.example.foolcardgame.domain.model.RoundEvent
import com.example.foolcardgame.domain.model.RoundEventKind
import com.example.foolcardgame.domain.model.Suit
import com.example.foolcardgame.domain.model.TablePair
import com.example.foolcardgame.domain.model.canAddMoreAttacks
import com.example.foolcardgame.domain.model.permissionsFor
import com.example.foolcardgame.domain.model.nextThrowPhaseActor
import com.example.foolcardgame.domain.model.throwingClosed
import java.util.UUID

/**
 * Pure Kotlin game session engine (local “API”).
 * No Android / HTTP dependencies — actions mirror GameClient verbs.
 */
class GameEngine(
    private val botAI: BotAI = BotAI(),
    /** When true, BotAI drives every seat (functional / simulation tests). */
    private val controlAllPlayers: Boolean = false,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val sessions = mutableMapOf<String, GameState>()

    fun createSession(config: GameConfig): String {
        val sessionId = UUID.randomUUID().toString()
        val human = Player(
            id = config.humanId,
            displayName = config.humanDisplayName,
            avatarId = config.humanAvatarId,
            isBot = false,
            isConnected = true,
            status = PlayerStatus.WAITING,
        )
        val bots = (1..config.botCount).map { index ->
            Player(
                id = "bot-$index",
                displayName = "Бот $index",
                avatarId = index % 8,
                isBot = true,
                isConnected = false,
                status = PlayerStatus.DISCONNECTED,
            )
        }
        sessionSeeds[sessionId] = config.seed
        sessions[sessionId] = dealForLobby(
            GameState(
                sessionId = sessionId,
                phase = GamePhase.LOBBY_WAITING,
                players = listOf(human) + bots,
                deck = emptyList(),
                trumpCard = null,
                trumpSuit = null,
                tablePairs = emptyList(),
                attackerId = null,
                defenderId = null,
                currentPlayerId = null,
            ),
            config.seed,
        )
        return sessionId
    }

    private val sessionSeeds = mutableMapOf<String, Long>()

    fun getState(sessionId: String): GameState =
        sessions[sessionId] ?: error("Unknown session: $sessionId")

    fun permissions(sessionId: String, playerId: String): ActionPermissions =
        getState(sessionId).permissionsFor(playerId)

    fun ready(sessionId: String, playerId: String): Result<GameState> = mutate(sessionId) { state ->
        if (state.phase != GamePhase.LOBBY_WAITING) {
            return@mutate Result.failure(IllegalStateException("Not in lobby"))
        }
        val player = state.player(playerId)
            ?: return@mutate Result.failure(IllegalArgumentException("Unknown player"))
        if (player.isReady) return@mutate Result.success(state)

        var next = state.updatePlayer(playerId) {
            it.copy(isReady = true, isConnected = true, status = PlayerStatus.PLAYING)
        }.bumpTick()

        if (next.players.all { it.isReady }) {
            next = beginGame(next)
        }
        Result.success(next)
    }

    fun setConnected(sessionId: String, playerId: String, connected: Boolean): Result<GameState> =
        mutate(sessionId) { state ->
            if (state.phase != GamePhase.LOBBY_WAITING) {
                return@mutate Result.failure(IllegalStateException("Not in lobby"))
            }
            val player = state.player(playerId)
                ?: return@mutate Result.failure(IllegalArgumentException("Unknown player"))
            if (player.isConnected == connected) return@mutate Result.success(state)
            Result.success(
                state.updatePlayer(playerId) {
                    it.copy(
                        isConnected = connected,
                        status = if (connected) PlayerStatus.WAITING else PlayerStatus.DISCONNECTED,
                    )
                }.bumpTick(),
            )
        }

    fun skipTurn(sessionId: String, playerId: String): Result<GameState> = mutate(sessionId) { state ->
        skipTurnInMemory(state, playerId)
    }

    fun playCard(
        sessionId: String,
        playerId: String,
        card: Card,
        targetPairId: Int?,
    ): Result<GameState> = mutate(sessionId) { state ->
        if (state.phase != GamePhase.IN_PROGRESS) {
            return@mutate Result.failure(IllegalStateException("Game not in progress"))
        }
        if (targetPairId == null) {
            attackOrThrow(state, playerId, card)
        } else {
            defend(state, playerId, card, targetPairId)
        }
    }

    fun addCard(sessionId: String, playerId: String, card: Card): Result<GameState> =
        mutate(sessionId) { state ->
            if (state.phase != GamePhase.IN_PROGRESS) {
                return@mutate Result.failure(IllegalStateException("Game not in progress"))
            }
            throwCard(state, playerId, card)
        }

    fun pass(sessionId: String, playerId: String): Result<GameState> = mutate(sessionId) { state ->
        if (state.phase != GamePhase.IN_PROGRESS) {
            return@mutate Result.failure(IllegalStateException("Game not in progress"))
        }
        val perms = state.permissionsFor(playerId)
        when {
            perms.canTake -> defenderTakes(state, playerId)
            perms.canPass -> markPassed(state, playerId)
            else -> Result.failure(IllegalStateException("Pass not allowed"))
        }
    }

    fun bito(sessionId: String, playerId: String): Result<GameState> = mutate(sessionId) { state ->
        if (state.phase != GamePhase.IN_PROGRESS) {
            return@mutate Result.failure(IllegalStateException("Game not in progress"))
        }
        if (!state.permissionsFor(playerId).canBito) {
            return@mutate Result.failure(IllegalStateException("Bito not allowed"))
        }
        Result.success(
            endRoundBito(state)
                .withTurnDeadline()
                .bumpTick()
                .recordRoundEvent(RoundEventKind.BITO, playerId)
                .recordActionEvent(GameActionKind.BITO, playerId),
        )
    }

    /** Test-only: inject a fully prepared state. */
    fun loadStateForTest(state: GameState, seed: Long = GameConfig.DEFAULT_SEED) {
        sessions[state.sessionId] = state
        sessionSeeds[state.sessionId] = seed
    }

    fun leave(sessionId: String, @Suppress("UNUSED_PARAMETER") playerId: String): GameState {
        val state = sessions.remove(sessionId) ?: return getOrEmpty(sessionId)
        sessionSeeds.remove(sessionId)
        return state.bumpTick()
    }

    /**
     * Checks turn deadline (skip if expired). Bot moves and lobby staging are owned by LocalGameClient.
     */
    fun onTick(sessionId: String): GameState {
        val state = sessions[sessionId] ?: return getOrEmpty(sessionId)
        var next = state
        if (next.phase == GamePhase.IN_PROGRESS) {
            val playerId = next.currentPlayerId
            val deadline = next.turnDeadlineAtMs
            if (playerId != null && deadline != null && clock() >= deadline) {
                next = skipTurnInMemory(next, playerId).getOrDefault(next)
            }
        }
        next = next.bumpTick()
        sessions[sessionId] = next
        return next
    }

    /** Runs bot actions until a human must act or the game ends (tests). */
    fun advanceUntilHumanOrFinished(sessionId: String, maxSteps: Int = 64): GameState {
        var state = getState(sessionId)
        state = advanceBots(state, maxSteps)
        sessions[sessionId] = state
        return state
    }

    /** One BotAI step (LocalGameClient after think delay). */
    fun advanceOneBot(sessionId: String): GameState {
        val advanced = advanceBots(getState(sessionId), maxSteps = 1)
        sessions[sessionId] = advanced
        return advanced
    }

    // --- internals ---

    private fun skipTurnInMemory(state: GameState, playerId: String): Result<GameState> {
        if (state.phase != GamePhase.IN_PROGRESS) {
            return Result.failure(IllegalStateException("Game not in progress"))
        }
        if (state.currentPlayerId != playerId) {
            return Result.failure(IllegalStateException("Not this player's turn"))
        }
        return when {
            state.tablePairs.isEmpty() -> Result.success(skipEmptyTableAttack(state, playerId))
            state.unbeatenPairs.isNotEmpty() && playerId == state.defenderId ->
                Result.success(skipDefenderTimeout(state))
            state.allBeaten -> Result.success(skipThrowPhase(state, playerId))
            else -> Result.failure(IllegalStateException("Cannot skip turn in this state"))
        }
    }

    private fun skipEmptyTableAttack(state: GameState, playerId: String): GameState {
        val players = state.playersWithCards().ifEmpty { state.playersInGame() }
        val fromIndex = players.indexOfFirst { it.id == playerId }.coerceAtLeast(0)
        val newAttacker = nextPlayerWithCards(players, fromIndex) ?: players.first()
        val newAttackerIndex = players.indexOfFirst { it.id == newAttacker.id }
        val newDefender = nextPlayerWithCards(players, newAttackerIndex) ?: players.first()
        return state.copy(
            attackerId = newAttacker.id,
            defenderId = newDefender.id,
            currentPlayerId = newAttacker.id,
            passedPlayerIds = emptySet(),
            defenderHandSizeAtRoundStart = state.player(newDefender.id)?.hand?.size ?: 0,
        ).withTurnDeadline().bumpTick()
    }

    private fun skipDefenderTimeout(state: GameState): GameState {
        // Table discarded (not taken by defender), then roles like after bito — no UI event.
        return endRoundBito(state).withTurnDeadline().bumpTick()
    }

    private fun skipThrowPhase(state: GameState, playerId: String): GameState {
        var next = state.copy(passedPlayerIds = state.passedPlayerIds + playerId)
        if (next.throwingClosed()) {
            val attackerId = next.attackerId ?: playerId
            return endRoundBito(next)
                .withTurnDeadline()
                .bumpTick()
                .recordRoundEvent(RoundEventKind.BITO, attackerId)
                .recordActionEvent(GameActionKind.BITO, attackerId)
        }
        val nextActor = next.nextThrowPhaseActor() ?: next.attackerId
        return next.copy(currentPlayerId = nextActor)
            .withTurnDeadline()
            .bumpTick()
            .recordActionEvent(GameActionKind.PASS, playerId)
    }

    private fun mutate(
        sessionId: String,
        block: (GameState) -> Result<GameState>,
    ): Result<GameState> {
        val current = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Unknown session"))
        val result = block(current.copy(lastRoundEvent = null, lastActionEvent = null))
        result.onSuccess { sessions[sessionId] = it }
        return result
    }

    private fun getOrEmpty(sessionId: String): GameState = GameState(
        sessionId = sessionId,
        phase = GamePhase.FINISHED,
        players = emptyList(),
        deck = emptyList(),
        trumpCard = null,
        trumpSuit = null,
        tablePairs = emptyList(),
        attackerId = null,
        defenderId = null,
        currentPlayerId = null,
    )

    private fun advanceBots(state: GameState, maxSteps: Int): GameState {
        var current = state
        repeat(maxSteps) {
            if (current.phase != GamePhase.IN_PROGRESS) return current
            val action = botAI.chooseAction(current, controlAllPlayers = controlAllPlayers)
                ?: return current
            val result = applyBotAction(current, action)
            current = result.getOrElse { return current }
        }
        return current
    }

    private fun applyBotAction(state: GameState, action: BotAI.Action): Result<GameState> {
        return when (action) {
            is BotAI.Action.Ready -> readyInMemory(state, action.playerId)
            is BotAI.Action.PlayCard -> {
                if (action.targetPairId == null) {
                    attackOrThrow(state, action.playerId, action.card)
                } else {
                    defend(state, action.playerId, action.card, action.targetPairId)
                }
            }
            is BotAI.Action.AddCard -> throwCard(state, action.playerId, action.card)
            is BotAI.Action.Pass -> {
                val perms = state.permissionsFor(action.playerId)
                when {
                    perms.canTake -> defenderTakes(state, action.playerId)
                    perms.canPass -> markPassed(state, action.playerId)
                    else -> Result.failure(IllegalStateException("Bot pass invalid"))
                }
            }
            is BotAI.Action.Bito -> {
                if (!state.permissionsFor(action.playerId).canBito) {
                    Result.failure(IllegalStateException("Bot bito invalid"))
                } else {
                    Result.success(
                        endRoundBito(state)
                            .withTurnDeadline()
                            .bumpTick()
                            .recordRoundEvent(RoundEventKind.BITO, action.playerId)
                            .recordActionEvent(GameActionKind.BITO, action.playerId),
                    )
                }
            }
        }
    }

    private fun readyInMemory(state: GameState, playerId: String): Result<GameState> {
        if (state.phase != GamePhase.LOBBY_WAITING) {
            return Result.failure(IllegalStateException("Not in lobby"))
        }
        var next = state.updatePlayer(playerId) {
            it.copy(isReady = true, isConnected = true, status = PlayerStatus.PLAYING)
        }.bumpTick()
        if (next.players.all { it.isReady }) {
            next = beginGame(next)
        }
        return Result.success(next)
    }

    /** Deal 6 cards and trump while staying in lobby so players can study hands before ready. */
    private fun dealForLobby(state: GameState, seed: Long): GameState {
        var deck = Deck.shuffled(seed)
        val dealtPlayers = state.players.map { player ->
            val hand = deck.take(6)
            deck = deck.drop(6)
            player.copy(hand = hand, isFinished = false)
        }
        val trumpCard = deck.lastOrNull()
        return state.copy(
            phase = GamePhase.LOBBY_WAITING,
            players = dealtPlayers,
            deck = deck,
            trumpCard = trumpCard,
            trumpSuit = trumpCard?.suit,
            tablePairs = emptyList(),
            attackerId = null,
            defenderId = null,
            currentPlayerId = null,
            passedPlayerIds = emptySet(),
            defenderHandSizeAtRoundStart = 0,
        )
    }

    private fun beginGame(state: GameState): GameState {
        val players = state.players
        val attackerId = findFirstAttacker(players, state.trumpSuit)
        val attackerIndex = players.indexOfFirst { it.id == attackerId }
        val defender = nextPlayerWithCards(players, attackerIndex)
            ?: players[(attackerIndex + 1) % players.size]
        return state.copy(
            phase = GamePhase.IN_PROGRESS,
            attackerId = attackerId,
            defenderId = defender.id,
            currentPlayerId = attackerId,
            passedPlayerIds = emptySet(),
            defenderHandSizeAtRoundStart = defender.hand.size,
            winnerIds = emptyList(),
            loserId = null,
        ).withTurnDeadline().bumpTick()
    }

    private fun findFirstAttacker(players: List<Player>, trumpSuit: Suit?): String {
        if (trumpSuit == null) return players.first().id
        var best: Pair<Player, Card>? = null
        for (player in players) {
            for (card in player.hand) {
                if (card.suit != trumpSuit) continue
                if (best == null || card.rank < best.second.rank) {
                    best = player to card
                }
            }
        }
        return best?.first?.id ?: players.first().id
    }

    private fun attackOrThrow(state: GameState, playerId: String, card: Card): Result<GameState> {
        return if (state.tablePairs.isEmpty()) {
            firstAttack(state, playerId, card)
        } else {
            throwCard(state, playerId, card)
        }
    }

    private fun firstAttack(state: GameState, playerId: String, card: Card): Result<GameState> {
        if (playerId != state.attackerId) {
            return Result.failure(IllegalStateException("Not the attacker"))
        }
        if (playerId != state.currentPlayerId) {
            return Result.failure(IllegalStateException("Not your turn"))
        }
        val player = state.player(playerId)
            ?: return Result.failure(IllegalArgumentException("Unknown player"))
        if (card !in player.hand) {
            return Result.failure(IllegalArgumentException("Card not in hand"))
        }
        val pair = TablePair(id = nextPairId(state), attack = card)
        val next = state
            .removeFromHand(playerId, card)
            .copy(
                tablePairs = listOf(pair),
                currentPlayerId = state.defenderId,
                passedPlayerIds = emptySet(),
                defenderHandSizeAtRoundStart = state.player(state.defenderId!!)?.hand?.size
                    ?: state.defenderHandSizeAtRoundStart,
            )
            .markFinishedPlayers()
            .withTurnDeadline()
            .bumpTick()
            .recordActionEvent(GameActionKind.ATTACK, playerId)
        return Result.success(next.checkGameEnd())
    }

    private fun throwCard(state: GameState, playerId: String, card: Card): Result<GameState> {
        if (state.tablePairs.isEmpty()) {
            return Result.failure(IllegalStateException("Nothing to throw onto"))
        }
        if (playerId == state.defenderId) {
            return Result.failure(IllegalStateException("Defender cannot throw"))
        }
        if (!state.canAddMoreAttacks()) {
            return Result.failure(IllegalStateException("Table limit reached"))
        }
        val player = state.player(playerId)
            ?: return Result.failure(IllegalArgumentException("Unknown player"))
        if (card !in player.hand) {
            return Result.failure(IllegalArgumentException("Card not in hand"))
        }
        if (!Rules.canThrow(card, state.tableRanks)) {
            return Result.failure(IllegalArgumentException("Rank not on table"))
        }
        if (playerId in state.passedPlayerIds) {
            return Result.failure(IllegalStateException("Already passed"))
        }
        if (state.allBeaten && playerId != state.currentPlayerId) {
            return Result.failure(IllegalStateException("Not your turn"))
        }

        val pair = TablePair(id = nextPairId(state), attack = card)
        val next = state
            .removeFromHand(playerId, card)
            .copy(
                tablePairs = state.tablePairs + pair,
                currentPlayerId = state.defenderId,
                passedPlayerIds = emptySet(),
            )
            .markFinishedPlayers()
            .withTurnDeadline()
            .bumpTick()
            .recordActionEvent(GameActionKind.THROW_IN, playerId)
        return Result.success(next.checkGameEnd())
    }

    private fun defend(
        state: GameState,
        playerId: String,
        card: Card,
        targetPairId: Int,
    ): Result<GameState> {
        if (playerId != state.defenderId) {
            return Result.failure(IllegalStateException("Not the defender"))
        }
        val trump = state.trumpSuit
            ?: return Result.failure(IllegalStateException("No trump"))
        val player = state.player(playerId)
            ?: return Result.failure(IllegalArgumentException("Unknown player"))
        if (card !in player.hand) {
            return Result.failure(IllegalArgumentException("Card not in hand"))
        }
        val pairIndex = state.tablePairs.indexOfFirst { it.id == targetPairId }
        if (pairIndex < 0) {
            return Result.failure(IllegalArgumentException("Unknown pair"))
        }
        val pair = state.tablePairs[pairIndex]
        if (pair.defense != null) {
            return Result.failure(IllegalStateException("Already beaten"))
        }
        if (!Rules.beats(card, pair.attack, trump)) {
            return Result.failure(IllegalArgumentException("Card does not beat"))
        }

        val newPairs = state.tablePairs.toMutableList()
        newPairs[pairIndex] = pair.copy(defense = card)
        var next = state
            .removeFromHand(playerId, card)
            .copy(tablePairs = newPairs)
            .markFinishedPlayers()

        next = if (next.allBeaten) {
            if (next.throwingClosed()) {
                // Limit reached or no throwers — round ends as bito immediately.
                val attackerId = next.attackerId ?: playerId
                return Result.success(
                    endRoundBito(next)
                        .withTurnDeadline()
                        .bumpTick()
                        .recordRoundEvent(RoundEventKind.BITO, attackerId)
                        .recordActionEvent(GameActionKind.BITO, attackerId),
                )
            }
            next.copy(currentPlayerId = next.nextThrowPhaseActor() ?: next.attackerId)
        } else {
            next.copy(currentPlayerId = next.defenderId)
        }
        return Result.success(
            next.withTurnDeadline().bumpTick().recordActionEvent(GameActionKind.DEFEND, playerId).checkGameEnd(),
        )
    }

    private fun defenderTakes(state: GameState, playerId: String): Result<GameState> {
        if (playerId != state.defenderId) {
            return Result.failure(IllegalStateException("Only defender can take"))
        }
        val cards = state.tablePairs.flatMap { listOfNotNull(it.attack, it.defense) }
        var next = state
            .addToHand(playerId, cards)
            .copy(
                tablePairs = emptyList(),
                passedPlayerIds = emptySet(),
            )
        next = drawUpToSix(next, skipDefenderDraw = false)
        // Taker skips; next clockwise after taker becomes attacker.
        val players = next.playersInGame()
        val takerIndex = players.indexOfFirst { it.id == playerId }
        val newAttacker = nextPlayerWithCards(players, takerIndex)
            ?: return Result.success(next.copy(phase = GamePhase.FINISHED).bumpTick())
        val newAttackerIndex = players.indexOfFirst { it.id == newAttacker.id }
        val newDefender = nextPlayerWithCards(players, newAttackerIndex)
            ?: return Result.success(next.copy(phase = GamePhase.FINISHED).bumpTick())

        next = next.copy(
            attackerId = newAttacker.id,
            defenderId = newDefender.id,
            currentPlayerId = newAttacker.id,
            defenderHandSizeAtRoundStart = next.player(newDefender.id)?.hand?.size ?: 0,
        ).markFinishedPlayers().checkGameEnd().withTurnDeadline().bumpTick()
            .recordRoundEvent(RoundEventKind.TOOK, playerId)
            .recordActionEvent(GameActionKind.TOOK, playerId)
        return Result.success(next)
    }

    private fun markPassed(state: GameState, playerId: String): Result<GameState> {
        val next = state.copy(
            passedPlayerIds = state.passedPlayerIds + playerId,
        )
        return if (next.throwingClosed() && next.allBeaten) {
            val attackerId = next.attackerId ?: playerId
            Result.success(
                endRoundBito(next)
                    .withTurnDeadline()
                    .bumpTick()
                    .recordRoundEvent(RoundEventKind.BITO, attackerId)
                    .recordActionEvent(GameActionKind.BITO, attackerId),
            )
        } else {
            val nextActor = next.nextThrowPhaseActor() ?: next.attackerId
            Result.success(
                next.copy(currentPlayerId = nextActor)
                    .withTurnDeadline()
                    .bumpTick()
                    .recordActionEvent(GameActionKind.PASS, playerId),
            )
        }
    }

    private fun endRoundBito(state: GameState): GameState {
        val lastRoundTable = state.tablePairs
        val discarded = lastRoundTable.flatMap { listOfNotNull(it.attack, it.defense) }
        var next = state.copy(
            tablePairs = emptyList(),
            passedPlayerIds = emptySet(),
        )
        next = drawUpToSix(next, skipDefenderDraw = false)
        next = next.markFinishedPlayers()

        val finishedCheck = next.checkGameEnd()
        if (finishedCheck.phase == GamePhase.FINISHED) {
            return finishedCheck.copy(
                tablePairs = lastRoundTable,
                discardPile = state.discardPile,
                turnDeadlineAtMs = null,
                turnStartedAtMs = null,
            )
        }

        next = next.copy(discardPile = state.discardPile + discarded)

        val oldDefenderId = state.defenderId ?: return next
        val players = next.playersInGame().ifEmpty { next.playersWithCards() }
        val defenderIndex = players.indexOfFirst { it.id == oldDefenderId }
        // After bito, previous defender becomes the attacker.
        val newAttacker = players.find { it.id == oldDefenderId && it.hand.isNotEmpty() }
            ?: (if (defenderIndex >= 0) {
                nextPlayerWithCards(players, defenderIndex)
            } else {
                null
            })
            ?: players.firstOrNull()
            ?: return next
        val newAttackerIndex = players.indexOfFirst { it.id == newAttacker.id }
        val newDefender = nextPlayerWithCards(players, newAttackerIndex) ?: players.first()

        return next.copy(
            attackerId = newAttacker.id,
            defenderId = newDefender.id,
            currentPlayerId = newAttacker.id,
            defenderHandSizeAtRoundStart = next.player(newDefender.id)?.hand?.size ?: 0,
        ).withTurnDeadline()
    }

    /**
     * Draw to 6: attacker first, then clockwise, defender last.
     */
    private fun drawUpToSix(state: GameState, skipDefenderDraw: Boolean): GameState {
        if (state.deck.isEmpty()) return state
        val attackerId = state.attackerId ?: return state
        val defenderId = state.defenderId
        val ordered = buildDrawOrder(state, attackerId, defenderId, skipDefenderDraw)
        var deck = state.deck
        var players = state.players
        for (playerId in ordered) {
            val index = players.indexOfFirst { it.id == playerId }
            if (index < 0) continue
            var hand = players[index].hand
            while (hand.size < 6 && deck.isNotEmpty()) {
                // Draw from front; keep last card (trump) until only it remains
                val drawn = deck.first()
                deck = deck.drop(1)
                hand = hand + drawn
            }
            players = players.toMutableList().also {
                it[index] = players[index].copy(hand = hand)
            }
        }
        val trumpCard = if (deck.isEmpty()) state.trumpCard else deck.last()
        return state.copy(players = players, deck = deck, trumpCard = trumpCard)
    }

    private fun buildDrawOrder(
        state: GameState,
        attackerId: String,
        defenderId: String?,
        skipDefender: Boolean,
    ): List<String> {
        val inGame = state.players.filter { !it.isFinished }
        val start = inGame.indexOfFirst { it.id == attackerId }.coerceAtLeast(0)
        val order = mutableListOf<String>()
        for (i in inGame.indices) {
            val p = inGame[(start + i) % inGame.size]
            if (skipDefender && p.id == defenderId) continue
            order.add(p.id)
        }
        // Ensure defender is last if present and not skipped
        if (!skipDefender && defenderId != null) {
            order.remove(defenderId)
            order.add(defenderId)
        }
        return order
    }

    private fun nextPlayerWithCards(players: List<Player>, fromIndex: Int): Player? {
        if (players.isEmpty()) return null
        for (i in 1..players.size) {
            val candidate = players[(fromIndex + i) % players.size]
            if (!candidate.isFinished && candidate.hand.isNotEmpty()) return candidate
        }
        return players.firstOrNull { !it.isFinished }
    }

    private fun nextPairId(state: GameState): Int =
        (state.tablePairs.maxOfOrNull { it.id } ?: 0) + 1

    private fun GameState.removeFromHand(playerId: String, card: Card): GameState =
        updatePlayer(playerId) { player ->
            val index = player.hand.indexOfFirst { it == card }
            if (index < 0) player
            else player.copy(hand = player.hand.toMutableList().also { it.removeAt(index) })
        }

    private fun GameState.addToHand(playerId: String, cards: List<Card>): GameState =
        updatePlayer(playerId) { it.copy(hand = it.hand + cards) }

    private fun GameState.updatePlayer(playerId: String, transform: (Player) -> Player): GameState =
        copy(players = players.map { if (it.id == playerId) transform(it) else it })

    private fun GameState.bumpTick(): GameState = copy(tick = tick + 1)

    private fun GameState.withTurnDeadline(): GameState {
        if (phase != GamePhase.IN_PROGRESS || currentPlayerId == null) {
            return copy(turnStartedAtMs = null, turnDeadlineAtMs = null)
        }
        val now = clock()
        val timeoutMs = if (allBeaten && tablePairs.isNotEmpty()) {
            GameConfig.THROW_TIMEOUT_MS
        } else {
            GameConfig.TURN_TIMEOUT_MS
        }
        return copy(
            turnStartedAtMs = now,
            turnDeadlineAtMs = now + timeoutMs,
        )
    }

    private fun GameState.markFinishedPlayers(): GameState {
        if (deck.isNotEmpty()) return this
        return copy(
            players = players.map { player ->
                if (!player.isFinished && player.hand.isEmpty()) {
                    player.copy(isFinished = true, status = PlayerStatus.PLAYING)
                } else {
                    player
                }
            },
        )
    }

    private fun GameState.checkGameEnd(): GameState {
        if (phase != GamePhase.IN_PROGRESS) return this
        if (deck.isNotEmpty()) return this
        val withCards = players.filter { !it.isFinished && it.hand.isNotEmpty() }
        val winners = players.filter { it.isFinished || it.hand.isEmpty() }.map { it.id }
        return when {
            withCards.size <= 1 -> {
                val loser = withCards.singleOrNull()
                copy(
                    phase = GamePhase.FINISHED,
                    currentPlayerId = null,
                    loserId = loser?.id,
                    winnerIds = players.map { it.id }.filter { it != loser?.id },
                )
            }
            else -> copy(winnerIds = winners.filter { id ->
                players.find { it.id == id }?.hand?.isEmpty() == true
            })
        }
    }

    private fun GameState.recordRoundEvent(kind: RoundEventKind, playerId: String): GameState =
        copy(lastRoundEvent = RoundEvent(kind = kind, playerId = playerId, atTick = tick))

    private fun GameState.recordActionEvent(kind: GameActionKind, playerId: String): GameState =
        copy(lastActionEvent = GameActionEvent(kind = kind, playerId = playerId, atTick = tick))
}
