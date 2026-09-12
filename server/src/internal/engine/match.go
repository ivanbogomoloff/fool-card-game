package engine

import (
	"errors"
	"fmt"

	"github.com/google/uuid"
)

// SeatIn — место за столом при старте матча.
type SeatIn struct {
	PlayerID  string
	AccountID string
	Username  string
	AvatarID  int32
}

// Match — авторитетная партия в памяти.
type Match struct {
	state State
	seed  int64
	clock func() int64
}

// NewMatch раздаёт колоду 36 (по 6 карт), открывает козырь и сразу переводит партию в IN_PROGRESS.
// gameID должен совпадать с session / logs / PK games.id; пустой — сгенерировать UUID.
func NewMatch(gameID string, seats []SeatIn, seed int64, clock func() int64) *Match {
	if clock == nil {
		clock = defaultClock
	}
	if len(seats) < 2 || len(seats) > 4 {
		panic(fmt.Sprintf("engine: seats must be 2..4, got %d", len(seats)))
	}
	if gameID == "" {
		gameID = uuid.NewString()
	}
	deck := Shuffled(seed)

	players := make([]Player, len(seats))
	for i, seat := range seats {
		hand := append([]Card(nil), deck[:6]...)
		deck = deck[6:]
		players[i] = Player{
			PlayerID:    seat.PlayerID,
			AccountID:   seat.AccountID,
			Username:    seat.Username,
			AvatarID:    seat.AvatarID,
			Hand:        hand,
			IsReady:     true,
			IsConnected: true,
			Status:      PlayerStatusPlaying,
		}
	}

	var trumpCard *Card
	var trumpSuit *Suit
	if len(deck) > 0 {
		tc := deck[len(deck)-1]
		trumpCard = &tc
		ts := tc.Suit
		trumpSuit = &ts
	}

	attackerID := findFirstAttacker(players, trumpSuit)
	attIdx := 0
	for i, p := range players {
		if p.PlayerID == attackerID {
			attIdx = i
			break
		}
	}
	defender := nextPlayerWithCards(players, attIdx)
	defenderID := players[(attIdx+1)%len(players)].PlayerID
	if defender != nil {
		defenderID = defender.PlayerID
	}
	defHandSize := 0
	for _, p := range players {
		if p.PlayerID == defenderID {
			defHandSize = len(p.Hand)
			break
		}
	}

	state := State{
		GameID:                       gameID,
		Phase:                        PhaseInProgress,
		Players:                      players,
		Deck:                         deck,
		TrumpCard:                    trumpCard,
		TrumpSuit:                    trumpSuit,
		PassedPlayerIDs:              make(map[string]struct{}),
		AttackerID:                   attackerID,
		DefenderID:                   defenderID,
		CurrentPlayerID:              attackerID,
		DefenderHandSizeAtRoundStart: defHandSize,
	}

	now := clock()
	state.withTurnDeadline(now)

	return &Match{state: state, seed: seed, clock: clock}
}

func defaultClock() int64 {
	return unixMillisNow()
}

// State возвращает снимок состояния.
func (m *Match) State() State {
	return m.state.clone()
}

func (m *Match) mutate(fn func(*State) error) error {
	m.state.clearEvents()
	if err := fn(&m.state); err != nil {
		return err
	}
	return nil
}

// PlayCard — атака (targetPairID=nil) или отбивка.
func (m *Match) PlayCard(playerID string, card Card, targetPairID *int32) error {
	return m.mutate(func(s *State) error {
		if s.Phase != PhaseInProgress {
			return errGameNotInProgress
		}
		if targetPairID == nil {
			return m.attackOrThrow(s, playerID, card)
		}
		return m.defend(s, playerID, card, *targetPairID)
	})
}

// AddCard — подкид карты на отбитый стол.
func (m *Match) AddCard(playerID string, card Card) error {
	return m.mutate(func(s *State) error {
		if s.Phase != PhaseInProgress {
			return errGameNotInProgress
		}
		return m.throwCard(s, playerID, card)
	})
}

// Pass — «беру» (защитник) или подтверждение «Бито» (помощник).
func (m *Match) Pass(playerID string) error {
	return m.mutate(func(s *State) error {
		if s.Phase != PhaseInProgress {
			return errGameNotInProgress
		}
		perms := s.PermissionsFor(playerID)
		switch {
		case perms.CanTake:
			return m.defenderTakes(s, playerID)
		case perms.CanPass:
			return m.markPassed(s, playerID)
		default:
			return errPassNotAllowed
		}
	})
}

// Bito — атакующий объявляет «Бито».
func (m *Match) Bito(playerID string) error {
	return m.mutate(func(s *State) error {
		if s.Phase != PhaseInProgress {
			return errGameNotInProgress
		}
		if !s.PermissionsFor(playerID).CanBito {
			return errBitoNotAllowed
		}
		*s = m.declareAttackerBito(*s, playerID)
		return nil
	})
}

// OnTick пропускает ход при истечении дедлайна.
func (m *Match) OnTick() (changed bool) {
	s := &m.state
	if s.Phase != PhaseInProgress {
		return false
	}
	playerID := s.CurrentPlayerID
	deadline := s.TurnDeadlineAtMs
	if playerID == "" || deadline == nil || m.clock() < *deadline {
		return false
	}
	s.clearEvents()
	before := s.Tick
	_ = m.skipTurnInMemory(s, playerID)
	return s.Tick != before || s.CurrentPlayerID != playerID
}

// Leave помечает игрока LEFT, сбрасывает руку в отбой и перераспределяет роли.
func (m *Match) Leave(playerID string) error {
	return m.mutate(func(s *State) error {
		p := s.Player(playerID)
		if p == nil {
			return errUnknownPlayer
		}
		if p.Status == PlayerStatusLeft {
			return nil
		}

		s.DiscardPile = append(s.DiscardPile, p.Hand...)
		s.updatePlayer(playerID, func(pl *Player) {
			pl.Hand = nil
			pl.IsFinished = true
			pl.Status = PlayerStatusLeft
		})
		delete(s.PassedPlayerIDs, playerID)

		if s.Phase != PhaseInProgress {
			s.bumpTick()
			return nil
		}

		wasDefender := playerID == s.DefenderID
		if wasDefender && len(s.UnbeatenPairs()) > 0 {
			*s = m.endRoundBito(*s)
			s.bumpTick()
			s.checkGameEnd()
			if s.Phase == PhaseInProgress {
				s.withTurnDeadline(m.clock())
			}
			return nil
		}

		m.reassignRolesAfterLeave(s, playerID)
		s.markFinishedPlayers()
		s.checkGameEnd()
		if s.Phase == PhaseInProgress {
			s.withTurnDeadline(m.clock())
		}
		s.bumpTick()
		return nil
	})
}

func (m *Match) attackOrThrow(s *State, playerID string, card Card) error {
	if len(s.TablePairs) == 0 {
		return m.firstAttack(s, playerID, card)
	}
	return m.throwCard(s, playerID, card)
}

func (m *Match) firstAttack(s *State, playerID string, card Card) error {
	if playerID != s.AttackerID {
		return errNotAttacker
	}
	if playerID != s.CurrentPlayerID {
		return errNotYourTurn
	}
	if !s.hasCardInHand(playerID, card) {
		return errCardNotInHand
	}

	pair := TablePair{ID: nextPairID(s.TablePairs), Attack: card}
	s.removeFromHand(playerID, card)
	defSize := s.DefenderHandSizeAtRoundStart
	if dp := s.Player(s.DefenderID); dp != nil {
		defSize = len(dp.Hand)
	}
	s.TablePairs = []TablePair{pair}
	s.CurrentPlayerID = s.DefenderID
	s.PassedPlayerIDs = make(map[string]struct{})
	s.AttackerBitoDeclared = false
	s.DefenderHandSizeAtRoundStart = defSize
	s.markFinishedPlayers()
	s.withTurnDeadline(m.clock())
	s.bumpTick()
	s.recordActionEvent(ActionAttack, playerID)
	s.checkGameEnd()
	return nil
}

func (m *Match) throwCard(s *State, playerID string, card Card) error {
	if len(s.TablePairs) == 0 {
		return errNothingToThrow
	}
	if playerID == s.DefenderID {
		return errDefenderCannotThrow
	}
	if s.AttackerBitoDeclared && playerID == s.AttackerID {
		return errAttackerAlreadyBito
	}
	if !s.CanAddMoreAttacks() {
		return errTableLimitReached
	}
	if !s.hasCardInHand(playerID, card) {
		return errCardNotInHand
	}
	if !(Rules{}).CanThrow(card, s.TableRanks()) {
		return errRankNotOnTable
	}
	if s.hasPassed(playerID) {
		return errAlreadyPassed
	}

	pair := TablePair{ID: nextPairID(s.TablePairs), Attack: card}
	s.removeFromHand(playerID, card)
	s.TablePairs = append(s.TablePairs, pair)
	s.CurrentPlayerID = s.DefenderID
	s.PassedPlayerIDs = make(map[string]struct{})
	s.AttackerBitoDeclared = false
	s.markFinishedPlayers()
	s.withTurnDeadline(m.clock())
	s.bumpTick()
	s.recordActionEvent(ActionThrowIn, playerID)
	s.checkGameEnd()
	return nil
}

func (m *Match) defend(s *State, playerID string, card Card, targetPairID int32) error {
	if playerID != s.DefenderID {
		return errNotDefender
	}
	if s.TrumpSuit == nil {
		return errNoTrump
	}
	if !s.hasCardInHand(playerID, card) {
		return errCardNotInHand
	}

	pairIndex := -1
	for i, p := range s.TablePairs {
		if p.ID == targetPairID {
			pairIndex = i
			break
		}
	}
	if pairIndex < 0 {
		return errUnknownPair
	}
	pair := s.TablePairs[pairIndex]
	if pair.Defense != nil {
		return errAlreadyBeaten
	}
	if !(Rules{}).Beats(card, pair.Attack, *s.TrumpSuit) {
		return errCardDoesNotBeat
	}

	def := card
	newPairs := append([]TablePair(nil), s.TablePairs...)
	newPairs[pairIndex] = TablePair{ID: pair.ID, Attack: pair.Attack, Defense: &def}
	s.removeFromHand(playerID, card)
	s.TablePairs = newPairs
	s.markFinishedPlayers()

	if s.AllBeaten() {
		s.CurrentPlayerID = s.AttackerID
	} else {
		s.CurrentPlayerID = s.DefenderID
	}
	s.withTurnDeadline(m.clock())
	s.bumpTick()
	s.recordActionEvent(ActionDefend, playerID)
	s.checkGameEnd()
	return nil
}

func (m *Match) defenderTakes(s *State, playerID string) error {
	if playerID != s.DefenderID {
		return errOnlyDefenderCanTake
	}
	var cards []Card
	for _, p := range s.TablePairs {
		cards = append(cards, p.Attack)
		if p.Defense != nil {
			cards = append(cards, *p.Defense)
		}
	}
	s.addToHand(playerID, cards)
	s.TablePairs = nil
	s.PassedPlayerIDs = make(map[string]struct{})
	s.AttackerBitoDeclared = false

	*s = m.drawUpToSix(*s, false)

	players := s.PlayersInGame()
	takerIdx := -1
	for i, p := range players {
		if p.PlayerID == playerID {
			takerIdx = i
			break
		}
	}
	newAttacker := nextPlayerWithCards(players, takerIdx)
	if newAttacker == nil {
		s.Phase = PhaseFinished
		s.bumpTick()
		return nil
	}
	newAttIdx := 0
	for i, p := range players {
		if p.PlayerID == newAttacker.PlayerID {
			newAttIdx = i
			break
		}
	}
	newDefender := nextPlayerWithCards(players, newAttIdx)
	if newDefender == nil {
		s.Phase = PhaseFinished
		s.bumpTick()
		return nil
	}

	s.AttackerID = newAttacker.PlayerID
	s.DefenderID = newDefender.PlayerID
	s.CurrentPlayerID = newAttacker.PlayerID
	if dp := s.Player(newDefender.PlayerID); dp != nil {
		s.DefenderHandSizeAtRoundStart = len(dp.Hand)
	}
	s.markFinishedPlayers()
	s.checkGameEnd()
	s.withTurnDeadline(m.clock())
	s.bumpTick()
	s.recordRoundEvent(RoundEventTook, playerID)
	s.recordActionEvent(ActionTook, playerID)
	return nil
}

func (m *Match) declareAttackerBito(s State, playerID string) State {
	declared := s
	declared.AttackerBitoDeclared = true
	nextActor := declared.nextThrowPhaseActor()
	if declared.ThrowingClosed() || nextActor == "" {
		out := m.endRoundBito(declared)
		out.withTurnDeadline(m.clock())
		out.bumpTick()
		out.recordRoundEvent(RoundEventBito, playerID)
		out.recordActionEvent(ActionBito, playerID)
		return out
	}
	declared.CurrentPlayerID = nextActor
	declared.withTurnDeadline(m.clock())
	declared.bumpTick()
	declared.recordActionEvent(ActionBito, playerID)
	return declared
}

func (m *Match) markPassed(s *State, playerID string) error {
	s.PassedPlayerIDs[playerID] = struct{}{}
	nextActor := s.nextThrowPhaseActor()
	if s.AllBeaten() && (s.ThrowingClosed() || nextActor == "") {
		attackerID := s.AttackerID
		if attackerID == "" {
			attackerID = playerID
		}
		*s = m.endRoundBito(*s)
		s.withTurnDeadline(m.clock())
		s.bumpTick()
		s.recordRoundEvent(RoundEventBito, attackerID)
		s.recordActionEvent(ActionBito, attackerID)
		return nil
	}
	s.CurrentPlayerID = nextActor
	s.withTurnDeadline(m.clock())
	s.bumpTick()
	s.recordActionEvent(ActionPass, playerID)
	return nil
}

func (m *Match) endRoundBito(s State) State {
	lastRoundTable := append([]TablePair(nil), s.TablePairs...)
	var discarded []Card
	for _, p := range lastRoundTable {
		discarded = append(discarded, p.Attack)
		if p.Defense != nil {
			discarded = append(discarded, *p.Defense)
		}
	}

	next := s
	next.TablePairs = nil
	next.PassedPlayerIDs = make(map[string]struct{})
	next.AttackerBitoDeclared = false
	next = m.drawUpToSix(next, false)
	next.markFinishedPlayers()

	finishedCheck := next
	finishedCheck.checkGameEnd()
	if finishedCheck.Phase == PhaseFinished {
		finishedCheck.TablePairs = lastRoundTable
		finishedCheck.DiscardPile = s.DiscardPile
		finishedCheck.TurnStartedAtMs = nil
		finishedCheck.TurnDeadlineAtMs = nil
		return finishedCheck
	}

	next.DiscardPile = append(append([]Card(nil), s.DiscardPile...), discarded...)

	oldDefenderID := s.DefenderID
	players := next.PlayersInGame()
	if len(players) == 0 {
		players = next.PlayersWithCards()
	}
	defenderIndex := -1
	for i, p := range players {
		if p.PlayerID == oldDefenderID {
			defenderIndex = i
			break
		}
	}

	var newAttacker *Player
	for i := range players {
		if players[i].PlayerID == oldDefenderID && len(players[i].Hand) > 0 {
			newAttacker = &players[i]
			break
		}
	}
	if newAttacker == nil && defenderIndex >= 0 {
		newAttacker = nextPlayerWithCards(players, defenderIndex)
	}
	if newAttacker == nil && len(players) > 0 {
		newAttacker = &players[0]
	}
	if newAttacker == nil {
		return next
	}

	newAttIdx := 0
	for i, p := range players {
		if p.PlayerID == newAttacker.PlayerID {
			newAttIdx = i
			break
		}
	}
	newDefender := nextPlayerWithCards(players, newAttIdx)
	if newDefender == nil {
		newDefender = &players[0]
	}

	next.AttackerID = newAttacker.PlayerID
	next.DefenderID = newDefender.PlayerID
	next.CurrentPlayerID = newAttacker.PlayerID
	if dp := next.Player(newDefender.PlayerID); dp != nil {
		next.DefenderHandSizeAtRoundStart = len(dp.Hand)
	}
	next.withTurnDeadline(m.clock())
	return next
}

func (m *Match) drawUpToSix(s State, skipDefenderDraw bool) State {
	if len(s.Deck) == 0 {
		return s
	}
	attackerID := s.AttackerID
	if attackerID == "" {
		return s
	}
	ordered := buildDrawOrder(s, attackerID, s.DefenderID, skipDefenderDraw)
	deck := append([]Card(nil), s.Deck...)
	for _, playerID := range ordered {
		for {
			p := s.Player(playerID)
			if p == nil {
				break
			}
			if len(p.Hand) >= 6 || len(deck) == 0 {
				break
			}
			drawn := deck[0]
			deck = deck[1:]
			s.updatePlayer(playerID, func(pl *Player) {
				pl.Hand = append(pl.Hand, drawn)
			})
		}
	}
	var trumpCard *Card
	if len(deck) == 0 {
		trumpCard = s.TrumpCard
	} else {
		tc := deck[len(deck)-1]
		trumpCard = &tc
	}
	s.Deck = deck
	s.TrumpCard = trumpCard
	return s
}

func buildDrawOrder(s State, attackerID, defenderID string, skipDefender bool) []string {
	inGame := s.PlayersInGame()
	start := 0
	for i, p := range inGame {
		if p.PlayerID == attackerID {
			start = i
			break
		}
	}
	order := make([]string, 0, len(inGame))
	for i := range inGame {
		p := inGame[(start+i)%len(inGame)]
		if skipDefender && p.PlayerID == defenderID {
			continue
		}
		order = append(order, p.PlayerID)
	}
	if !skipDefender && defenderID != "" {
		filtered := order[:0]
		for _, id := range order {
			if id != defenderID {
				filtered = append(filtered, id)
			}
		}
		order = append(filtered, defenderID)
	}
	return order
}

func findFirstAttacker(players []Player, trumpSuit *Suit) string {
	if trumpSuit == nil || len(players) == 0 {
		return players[0].PlayerID
	}
	var bestPlayer *Player
	var bestCard *Card
	for i := range players {
		for _, card := range players[i].Hand {
			if card.Suit != *trumpSuit {
				continue
			}
			if bestCard == nil || card.Rank < bestCard.Rank {
				cp := card
				bestCard = &cp
				bestPlayer = &players[i]
			}
		}
	}
	if bestPlayer != nil {
		return bestPlayer.PlayerID
	}
	return players[0].PlayerID
}

func (m *Match) skipTurnInMemory(s *State, playerID string) error {
	if s.Phase != PhaseInProgress {
		return errGameNotInProgress
	}
	if s.CurrentPlayerID != playerID {
		return errNotYourTurn
	}
	switch {
	case len(s.TablePairs) == 0:
		*s = m.skipEmptyTableAttack(*s, playerID)
		return nil
	case len(s.UnbeatenPairs()) > 0 && playerID == s.DefenderID:
		*s = m.endRoundBito(*s)
		s.withTurnDeadline(m.clock())
		s.bumpTick()
		return nil
	case s.AllBeaten():
		if playerID == s.AttackerID && !s.AttackerBitoDeclared {
			*s = m.declareAttackerBito(*s, playerID)
			return nil
		}
		return m.markPassed(s, playerID)
	default:
		return errCannotSkipTurn
	}
}

func (m *Match) skipEmptyTableAttack(s State, playerID string) State {
	players := s.PlayersWithCards()
	if len(players) == 0 {
		players = s.PlayersInGame()
	}
	fromIndex := 0
	for i, p := range players {
		if p.PlayerID == playerID {
			fromIndex = i
			break
		}
	}
	newAttacker := nextPlayerWithCards(players, fromIndex)
	if newAttacker == nil {
		newAttacker = &players[0]
	}
	newAttIdx := 0
	for i, p := range players {
		if p.PlayerID == newAttacker.PlayerID {
			newAttIdx = i
			break
		}
	}
	newDefender := nextPlayerWithCards(players, newAttIdx)
	if newDefender == nil {
		newDefender = &players[0]
	}
	s.AttackerID = newAttacker.PlayerID
	s.DefenderID = newDefender.PlayerID
	s.CurrentPlayerID = newAttacker.PlayerID
	s.PassedPlayerIDs = make(map[string]struct{})
	s.AttackerBitoDeclared = false
	if dp := s.Player(newDefender.PlayerID); dp != nil {
		s.DefenderHandSizeAtRoundStart = len(dp.Hand)
	}
	s.withTurnDeadline(m.clock())
	s.bumpTick()
	return s
}

func (m *Match) reassignRolesAfterLeave(s *State, leftPlayerID string) {
	players := s.PlayersWithCards()
	if len(players) == 0 {
		players = s.PlayersInGame()
	}
	if len(players) == 0 {
		s.CurrentPlayerID = ""
		return
	}

	needAttacker := s.AttackerID == leftPlayerID || s.Player(s.AttackerID) == nil || s.Player(s.AttackerID).IsFinished
	needDefender := s.DefenderID == leftPlayerID || s.Player(s.DefenderID) == nil || s.Player(s.DefenderID).IsFinished

	if needAttacker {
		fromIdx := 0
		for i, p := range players {
			if p.PlayerID == s.AttackerID || p.PlayerID == leftPlayerID {
				fromIdx = i
				break
			}
		}
		if na := nextPlayerWithCards(players, fromIdx); na != nil {
			s.AttackerID = na.PlayerID
		}
	}
	if needDefender {
		attIdx := 0
		for i, p := range players {
			if p.PlayerID == s.AttackerID {
				attIdx = i
				break
			}
		}
		if nd := nextPlayerWithCards(players, attIdx); nd != nil && nd.PlayerID != s.AttackerID {
			s.DefenderID = nd.PlayerID
		}
	}

	if s.CurrentPlayerID == leftPlayerID || s.Player(s.CurrentPlayerID) == nil || s.Player(s.CurrentPlayerID).IsFinished {
		if len(s.TablePairs) == 0 {
			s.CurrentPlayerID = s.AttackerID
		} else if s.AllBeaten() {
			if actor := s.nextThrowPhaseActor(); actor != "" {
				s.CurrentPlayerID = actor
			} else {
				s.CurrentPlayerID = s.AttackerID
			}
		} else {
			s.CurrentPlayerID = s.DefenderID
		}
	}
}

func (s State) clone() State {
	out := s
	if s.TrumpCard != nil {
		tc := *s.TrumpCard
		out.TrumpCard = &tc
	}
	if s.TrumpSuit != nil {
		ts := *s.TrumpSuit
		out.TrumpSuit = &ts
	}
	if s.LoserID != nil {
		id := *s.LoserID
		out.LoserID = &id
	}
	out.Players = append([]Player(nil), s.Players...)
	for i := range out.Players {
		out.Players[i].Hand = append([]Card(nil), s.Players[i].Hand...)
	}
	out.Deck = append([]Card(nil), s.Deck...)
	out.TablePairs = append([]TablePair(nil), s.TablePairs...)
	for i, p := range out.TablePairs {
		if p.Defense != nil {
			d := *p.Defense
			out.TablePairs[i].Defense = &d
		}
	}
	out.PassedPlayerIDs = make(map[string]struct{}, len(s.PassedPlayerIDs))
	for id := range s.PassedPlayerIDs {
		out.PassedPlayerIDs[id] = struct{}{}
	}
	out.WinnerIDs = append([]string(nil), s.WinnerIDs...)
	out.DiscardPile = append([]Card(nil), s.DiscardPile...)
	if s.TurnStartedAtMs != nil {
		v := *s.TurnStartedAtMs
		out.TurnStartedAtMs = &v
	}
	if s.TurnDeadlineAtMs != nil {
		v := *s.TurnDeadlineAtMs
		out.TurnDeadlineAtMs = &v
	}
	if s.LastRoundEvent != nil {
		e := *s.LastRoundEvent
		out.LastRoundEvent = &e
	}
	if s.LastActionEvent != nil {
		e := *s.LastActionEvent
		out.LastActionEvent = &e
	}
	return out
}

var (
	errGameNotInProgress   = errors.New("game not in progress")
	errNotAttacker         = errors.New("not the attacker")
	errNotYourTurn         = errors.New("not this player's turn")
	errCardNotInHand       = errors.New("card not in hand")
	errNothingToThrow      = errors.New("nothing to throw onto")
	errDefenderCannotThrow = errors.New("defender cannot throw")
	errAttackerAlreadyBito = errors.New("attacker already declared bito")
	errTableLimitReached   = errors.New("table limit reached")
	errRankNotOnTable      = errors.New("rank not on table")
	errAlreadyPassed       = errors.New("already passed")
	errNotDefender         = errors.New("not the defender")
	errNoTrump             = errors.New("no trump")
	errUnknownPair         = errors.New("unknown pair")
	errAlreadyBeaten       = errors.New("already beaten")
	errCardDoesNotBeat     = errors.New("card does not beat")
	errOnlyDefenderCanTake = errors.New("only defender can take")
	errPassNotAllowed      = errors.New("pass not allowed")
	errBitoNotAllowed      = errors.New("bito not allowed")
	errUnknownPlayer       = errors.New("unknown player")
	errCannotSkipTurn      = errors.New("cannot skip turn in this state")
)
