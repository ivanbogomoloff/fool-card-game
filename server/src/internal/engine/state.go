package engine

const (
	// TurnTimeoutMs — таймаут хода атаки/отбивки.
	TurnTimeoutMs = 60_000
	// ThrowTimeoutMs — таймаут фазы подкида / «Бито».
	ThrowTimeoutMs = 30_000
)

// Phase — фаза партии.
type Phase int

const (
	PhaseLobbyWaiting Phase = iota + 1
	PhaseInProgress
	PhaseFinished
)

// PlayerStatus — статус места за столом.
type PlayerStatus int

const (
	PlayerStatusWaiting PlayerStatus = iota + 1
	PlayerStatusPlaying
	PlayerStatusDisconnected
	PlayerStatusLeft
)

// RoundEventKind — событие раунда для UI.
type RoundEventKind int

const (
	RoundEventTook RoundEventKind = iota + 1
	RoundEventBito
)

// GameActionKind — тип последнего действия.
type GameActionKind int

const (
	ActionAttack GameActionKind = iota + 1
	ActionDefend
	ActionThrowIn
	ActionPass
	ActionTook
	ActionBito
)

// RoundEvent — «беру» / «бито» для клиента.
type RoundEvent struct {
	Kind     RoundEventKind
	PlayerID string
	AtTick   int64
}

// GameActionEvent — последнее игровое действие.
type GameActionEvent struct {
	Kind     GameActionKind
	PlayerID string
	AtTick   int64
}

// Player — игрок за столом.
type Player struct {
	PlayerID   string
	AccountID  string
	Username   string
	AvatarID   int32
	Hand       []Card
	IsReady    bool
	IsConnected bool
	Status     PlayerStatus
	IsFinished bool
}

// TablePair — пара атака/защита на столе.
type TablePair struct {
	ID      int32
	Attack  Card
	Defense *Card
}

// IsBeaten — отбита ли пара.
func (p TablePair) IsBeaten() bool {
	return p.Defense != nil
}

// State — полное внутреннее состояние партии.
type State struct {
	GameID                     string
	Phase                      Phase
	Players                    []Player
	Deck                       []Card
	TrumpCard                  *Card
	TrumpSuit                  *Suit
	TablePairs                 []TablePair
	AttackerID                 string
	DefenderID                 string
	CurrentPlayerID            string
	PassedPlayerIDs            map[string]struct{}
	AttackerBitoDeclared       bool
	DefenderHandSizeAtRoundStart int
	Tick                       int64
	WinnerIDs                  []string
	LoserID                    *string
	DiscardPile                []Card
	TurnStartedAtMs            *int64
	TurnDeadlineAtMs           *int64
	LastRoundEvent             *RoundEvent
	LastActionEvent            *GameActionEvent
}

// Player возвращает игрока по id.
func (s State) Player(id string) *Player {
	for i := range s.Players {
		if s.Players[i].PlayerID == id {
			return &s.Players[i]
		}
	}
	return nil
}

// PlayersInGame — не выбывшие игроки.
func (s State) PlayersInGame() []Player {
	out := make([]Player, 0, len(s.Players))
	for _, p := range s.Players {
		if !p.IsFinished {
			out = append(out, p)
		}
	}
	return out
}

// PlayersWithCards — игроки с картами в руке.
func (s State) PlayersWithCards() []Player {
	out := make([]Player, 0, len(s.Players))
	for _, p := range s.Players {
		if !p.IsFinished && len(p.Hand) > 0 {
			out = append(out, p)
		}
	}
	return out
}

// TableRanks — ранги карт на столе (для подкида).
func (s State) TableRanks() map[Rank]struct{} {
	ranks := make(map[Rank]struct{})
	for _, pair := range s.TablePairs {
		ranks[pair.Attack.Rank] = struct{}{}
		if pair.Defense != nil {
			ranks[pair.Defense.Rank] = struct{}{}
		}
	}
	return ranks
}

// UnbeatenPairs — неотбитые пары.
func (s State) UnbeatenPairs() []TablePair {
	out := make([]TablePair, 0)
	for _, p := range s.TablePairs {
		if !p.IsBeaten() {
			out = append(out, p)
		}
	}
	return out
}

// AllBeaten — все карты на столе отбиты.
func (s State) AllBeaten() bool {
	if len(s.TablePairs) == 0 {
		return false
	}
	for _, p := range s.TablePairs {
		if !p.IsBeaten() {
			return false
		}
	}
	return true
}

func (s *State) bumpTick() {
	s.Tick++
}

func (s *State) clearEvents() {
	s.LastRoundEvent = nil
	s.LastActionEvent = nil
}

func (s *State) updatePlayer(playerID string, fn func(*Player)) {
	for i := range s.Players {
		if s.Players[i].PlayerID == playerID {
			fn(&s.Players[i])
			return
		}
	}
}

func (s *State) removeFromHand(playerID string, card Card) {
	s.updatePlayer(playerID, func(p *Player) {
		for i, c := range p.Hand {
			if c == card {
				p.Hand = append(p.Hand[:i], p.Hand[i+1:]...)
				return
			}
		}
	})
}

func (s *State) addToHand(playerID string, cards []Card) {
	s.updatePlayer(playerID, func(p *Player) {
		p.Hand = append(p.Hand, cards...)
	})
}

func (s *State) hasCardInHand(playerID string, card Card) bool {
	p := s.Player(playerID)
	if p == nil {
		return false
	}
	for _, c := range p.Hand {
		if c == card {
			return true
		}
	}
	return false
}

func (s *State) withTurnDeadline(now int64) {
	if s.Phase != PhaseInProgress || s.CurrentPlayerID == "" {
		s.TurnStartedAtMs = nil
		s.TurnDeadlineAtMs = nil
		return
	}
	timeout := int64(TurnTimeoutMs)
	if s.AllBeaten() && len(s.TablePairs) > 0 {
		timeout = ThrowTimeoutMs
	}
	started := now
	deadline := now + timeout
	s.TurnStartedAtMs = &started
	s.TurnDeadlineAtMs = &deadline
}

func (s *State) markFinishedPlayers() {
	if len(s.Deck) > 0 {
		return
	}
	for i := range s.Players {
		p := &s.Players[i]
		if !p.IsFinished && len(p.Hand) == 0 {
			p.IsFinished = true
			if p.Status != PlayerStatusLeft {
				p.Status = PlayerStatusPlaying
			}
		}
	}
}

func (s *State) checkGameEnd() {
	if s.Phase != PhaseInProgress || len(s.Deck) > 0 {
		return
	}
	var withCards []Player
	var emptiedIDs []string
	for _, p := range s.Players {
		if !p.IsFinished && len(p.Hand) > 0 {
			withCards = append(withCards, p)
		}
		if p.IsFinished || len(p.Hand) == 0 {
			emptiedIDs = append(emptiedIDs, p.PlayerID)
		}
	}
	switch {
	case len(withCards) == 0:
		s.Phase = PhaseFinished
		s.CurrentPlayerID = ""
		s.LoserID = nil
		s.WinnerIDs = nil
	case len(withCards) == 1:
		loser := withCards[0].PlayerID
		s.Phase = PhaseFinished
		s.CurrentPlayerID = ""
		s.LoserID = &loser
		winners := make([]string, 0, len(s.Players)-1)
		for _, p := range s.Players {
			if p.PlayerID != loser {
				winners = append(winners, p.PlayerID)
			}
		}
		s.WinnerIDs = winners
	default:
		winners := make([]string, 0)
		for _, id := range emptiedIDs {
			p := s.Player(id)
			if p != nil && len(p.Hand) == 0 {
				winners = append(winners, id)
			}
		}
		s.WinnerIDs = winners
	}
}

func (s *State) recordRoundEvent(kind RoundEventKind, playerID string) {
	s.LastRoundEvent = &RoundEvent{Kind: kind, PlayerID: playerID, AtTick: s.Tick}
}

func (s *State) recordActionEvent(kind GameActionKind, playerID string) {
	s.LastActionEvent = &GameActionEvent{Kind: kind, PlayerID: playerID, AtTick: s.Tick}
}

func nextPairID(pairs []TablePair) int32 {
	var max int32
	for _, p := range pairs {
		if p.ID > max {
			max = p.ID
		}
	}
	return max + 1
}

func nextPlayerWithCards(players []Player, fromIndex int) *Player {
	if len(players) == 0 {
		return nil
	}
	for i := 1; i <= len(players); i++ {
		candidate := players[(fromIndex+i)%len(players)]
		if !candidate.IsFinished && len(candidate.Hand) > 0 {
			return &candidate
		}
	}
	for i := range players {
		if !players[i].IsFinished {
			return &players[i]
		}
	}
	return nil
}
