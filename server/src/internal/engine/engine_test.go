package engine

import (
	"testing"
)

func fixedClock() func() int64 {
	var now int64 = 1_000
	return func() int64 {
		return now
	}
}

func TestRules_BeatsSameSuitHigher(t *testing.T) {
	trump := SuitHearts
	attack := Card{Suit: SuitSpades, Rank: RankSeven}
	defense := Card{Suit: SuitSpades, Rank: RankTen}
	if !(Rules{}).Beats(defense, attack, trump) {
		t.Fatal("ожидалась отбивка старшей картой той же масти")
	}
	if (Rules{}).Beats(attack, defense, trump) {
		t.Fatal("младшая не должна бить старшую")
	}
}

func TestRules_BeatsTrumpOverNonTrump(t *testing.T) {
	trump := SuitHearts
	attack := Card{Suit: SuitSpades, Rank: RankAce}
	defense := Card{Suit: SuitHearts, Rank: RankSix}
	if !(Rules{}).Beats(defense, attack, trump) {
		t.Fatal("козырь должен бить некозырную карту")
	}
}

func TestRules_BeatsTrumpOverTrumpHigher(t *testing.T) {
	trump := SuitHearts
	attack := Card{Suit: SuitHearts, Rank: RankSeven}
	defense := Card{Suit: SuitHearts, Rank: RankTen}
	if !(Rules{}).Beats(defense, attack, trump) {
		t.Fatal("старший козырь должен бить младший")
	}
}

func TestAttackAndDefend(t *testing.T) {
	m := newTestMatch(testInProgress(
		[]Card{{SuitSpades, RankSeven}, {SuitClubs, RankSix}},
		[]Card{{SuitSpades, RankTen}, {SuitHearts, RankSix}},
	))

	attack := Card{Suit: SuitSpades, Rank: RankSeven}
	if err := m.PlayCard("p1", attack, nil); err != nil {
		t.Fatalf("attack: %v", err)
	}
	st := m.State()
	if len(st.TablePairs) != 1 {
		t.Fatalf("want 1 pair, got %d", len(st.TablePairs))
	}
	if st.CurrentPlayerID != "p2" {
		t.Fatalf("want defender turn p2, got %s", st.CurrentPlayerID)
	}

	defense := Card{Suit: SuitSpades, Rank: RankTen}
	pairID := st.TablePairs[0].ID
	if err := m.PlayCard("p2", defense, &pairID); err != nil {
		t.Fatalf("defend: %v", err)
	}
	st = m.State()
	if st.TablePairs[0].Defense == nil {
		t.Fatal("pair should be beaten")
	}
	if st.CurrentPlayerID != "p1" {
		t.Fatalf("after full defense attacker should act, got %s", st.CurrentPlayerID)
	}
	if !st.PermissionsFor("p1").CanBito {
		t.Fatal("attacker should be able to declare bito")
	}
}

func TestThrowInSameRank(t *testing.T) {
	onTable := Card{Suit: SuitSpades, Rank: RankSeven}
	defended := Card{Suit: SuitSpades, Rank: RankTen}
	throwCard := Card{Suit: SuitClubs, Rank: RankSeven}
	m := newTestMatch(testInProgress(
		[]Card{throwCard, {SuitDiamonds, RankSix}},
		[]Card{{SuitSpades, RankAce}, {SuitClubs, RankAce}, {SuitDiamonds, RankAce}},
		testTablePairs(TablePair{ID: 1, Attack: onTable, Defense: &defended}),
		testDefenderHandSize(3),
		testTwoPlayers(),
	))

	if err := m.AddCard("p1", throwCard); err != nil {
		t.Fatalf("throw: %v", err)
	}
	if len(m.State().TablePairs) != 2 {
		t.Fatalf("want 2 pairs, got %d", len(m.State().TablePairs))
	}
}

func TestTake(t *testing.T) {
	attack := Card{Suit: SuitSpades, Rank: RankAce}
	m := newTestMatch(testInProgress(
		[]Card{{SuitClubs, RankSix}},
		[]Card{{SuitClubs, RankSeven}},
		testTablePairs(TablePair{ID: 1, Attack: attack}),
		testCurrentPlayer("p2"),
		testTwoPlayers(),
	))

	if err := m.Pass("p2"); err != nil {
		t.Fatalf("take: %v", err)
	}
	st := m.State()
	if len(st.TablePairs) != 0 {
		t.Fatal("table should be empty after take")
	}
	if !containsCard(st.Player("p2").Hand, attack) {
		t.Fatal("defender should have taken attack card")
	}
	if st.AttackerID != "p1" || st.DefenderID != "p2" {
		t.Fatalf("after take in 2p: attacker=%s defender=%s", st.AttackerID, st.DefenderID)
	}
}

func TestBitoEndRound(t *testing.T) {
	defended := Card{Suit: SuitSpades, Rank: RankTen}
	m := newTestMatch(testInProgress(
		[]Card{{SuitClubs, RankSix}},
		[]Card{{SuitDiamonds, RankSeven}},
		testTablePairs(TablePair{ID: 1, Attack: Card{SuitSpades, RankSeven}, Defense: &defended}),
		testDefenderHandSize(3),
		testTwoPlayers(),
	))

	if err := m.Bito("p1"); err != nil {
		t.Fatalf("bito: %v", err)
	}
	st := m.State()
	if len(st.TablePairs) != 0 {
		t.Fatal("table cleared after bito in 2p")
	}
	if st.AttackerID != "p2" {
		t.Fatalf("previous defender attacks: got %s", st.AttackerID)
	}
	if st.DefenderID != "p1" {
		t.Fatalf("previous attacker defends: got %s", st.DefenderID)
	}
}

func TestGameEndWithFool(t *testing.T) {
	defended := Card{Suit: SuitSpades, Rank: RankTen}
	m := newTestMatch(State{
		GameID: "g-fool",
		Phase:  PhaseInProgress,
		Players: []Player{
			{PlayerID: "p1", Username: "A", Hand: nil, IsFinished: true, Status: PlayerStatusPlaying},
			{PlayerID: "p2", Username: "B", Hand: []Card{{SuitClubs, RankSix}}, Status: PlayerStatusPlaying},
		},
		Deck: nil,
		TrumpCard: &Card{Suit: SuitHearts, Rank: RankAce},
		TrumpSuit: ptrSuit(SuitHearts),
		TablePairs: []TablePair{{
			ID: 1, Attack: Card{SuitSpades, RankSeven}, Defense: &defended,
		}},
		AttackerID:                   "p1",
		DefenderID:                   "p2",
		CurrentPlayerID:              "p1",
		PassedPlayerIDs:              map[string]struct{}{"p1": {}},
		DefenderHandSizeAtRoundStart: 1,
	})

	if err := m.Bito("p1"); err != nil {
		t.Fatalf("bito: %v", err)
	}
	st := m.State()
	if st.Phase != PhaseFinished {
		t.Fatalf("want FINISHED, got %v", st.Phase)
	}
	if st.LoserID == nil || *st.LoserID != "p2" {
		t.Fatalf("loser should be p2, got %v", st.LoserID)
	}
	if len(st.TablePairs) == 0 {
		t.Fatal("finished bito should keep last table visible")
	}
}

func TestGameEndDrawAllEmpty(t *testing.T) {
	attack := Card{Suit: SuitHearts, Rank: RankKing}
	defense := Card{Suit: SuitHearts, Rank: RankAce}
	m := newTestMatch(State{
		GameID: "g-draw",
		Phase:  PhaseInProgress,
		Players: []Player{
			{PlayerID: "p1", Username: "A", Status: PlayerStatusPlaying},
			{PlayerID: "p2", Username: "B", Hand: []Card{defense}, Status: PlayerStatusPlaying},
		},
		Deck: nil,
		TrumpCard: &Card{Suit: SuitClubs, Rank: RankSix},
		TrumpSuit: ptrSuit(SuitClubs),
		TablePairs: []TablePair{{
			ID: 1, Attack: attack,
		}},
		AttackerID:                   "p1",
		DefenderID:                   "p2",
		CurrentPlayerID:              "p2",
		PassedPlayerIDs:              make(map[string]struct{}),
		DefenderHandSizeAtRoundStart: 1,
	})

	pairID := int32(1)
	if err := m.PlayCard("p2", defense, &pairID); err != nil {
		t.Fatalf("defend: %v", err)
	}
	st := m.State()
	if st.Phase != PhaseFinished {
		t.Fatalf("want FINISHED draw, got %v", st.Phase)
	}
	if st.LoserID != nil {
		t.Fatal("draw should have no loser")
	}
	if len(st.WinnerIDs) != 0 {
		t.Fatal("draw should have empty winners")
	}
}

func TestDrawUpToSixAfterBito(t *testing.T) {
	defended := Card{Suit: SuitSpades, Rank: RankTen}
	deck := []Card{
		{SuitClubs, RankSix},
		{SuitClubs, RankSeven},
		{SuitClubs, RankEight},
		{SuitHearts, RankNine},
	}
	trump := deck[len(deck)-1]
	m := newTestMatch(testInProgress(
		[]Card{{SuitDiamonds, RankSix}},
		[]Card{{SuitDiamonds, RankSeven}},
		testTablePairs(TablePair{ID: 1, Attack: Card{SuitSpades, RankSeven}, Defense: &defended}),
		testDeck(deck...),
		testTrumpCard(&trump),
		testDefenderHandSize(1),
		testTwoPlayers(),
	))

	if err := m.Bito("p1"); err != nil {
		t.Fatalf("bito: %v", err)
	}
	st := m.State()
	if len(st.Player("p1").Hand) <= 1 {
		t.Fatalf("attacker should draw cards, hand=%d", len(st.Player("p1").Hand))
	}
	if len(st.Deck) >= len(deck) {
		t.Fatal("deck should shrink after draw")
	}
}

func TestNewMatch_DealAndStart(t *testing.T) {
	seats := []SeatIn{
		{PlayerID: "p1", Username: "A"},
		{PlayerID: "p2", Username: "B"},
	}
	m := NewMatch("test-game", seats, 42, fixedClock())
	st := m.State()
	if st.Phase != PhaseInProgress {
		t.Fatalf("want IN_PROGRESS, got %v", st.Phase)
	}
	for _, p := range st.Players {
		if len(p.Hand) != 6 {
			t.Fatalf("player %s should have 6 cards, got %d", p.PlayerID, len(p.Hand))
		}
	}
	if len(st.Deck) != 36-2*6 {
		t.Fatalf("unexpected deck size %d", len(st.Deck))
	}
	if st.TrumpCard == nil || st.TrumpSuit == nil {
		t.Fatal("trump required")
	}
	if st.AttackerID == "" || st.DefenderID == "" {
		t.Fatal("roles required")
	}
}

func TestProject_HidesOtherHands(t *testing.T) {
	m := NewMatch("proj", []SeatIn{
		{PlayerID: "p1", Username: "A"},
		{PlayerID: "p2", Username: "B"},
	}, 7, fixedClock())
	p1 := m.Project("p1")
	if len(p1.LocalHand) != 6 {
		t.Fatalf("local hand should be full, got %d", len(p1.LocalHand))
	}
	if p1.Players[1].HandCount != 6 {
		t.Fatalf("other hand count expected 6, got %d", p1.Players[1].HandCount)
	}
}

type testCfg struct {
	tablePairs                   []TablePair
	currentPlayerID              string
	deck                         []Card
	trump                        Suit
	trumpCard                    *Card
	defenderHandSizeAtRoundStart int
	twoPlayers                   bool
}

func testInProgress(attackerHand, defenderHand []Card, mods ...func(*testCfg)) State {
	cfg := testCfg{
		currentPlayerID:              "p1",
		trump:                        SuitHearts,
		defenderHandSizeAtRoundStart: 6,
	}
	for _, m := range mods {
		m(&cfg)
	}
	if cfg.trumpCard == nil && len(cfg.deck) > 0 {
		tc := cfg.deck[len(cfg.deck)-1]
		cfg.trumpCard = &tc
	} else if cfg.trumpCard == nil {
		tc := Card{Suit: cfg.trump, Rank: RankAce}
		cfg.trumpCard = &tc
	}

	players := []Player{
		{PlayerID: "p1", Username: "A", Hand: append([]Card(nil), attackerHand...), Status: PlayerStatusPlaying},
		{PlayerID: "p2", Username: "B", Hand: append([]Card(nil), defenderHand...), Status: PlayerStatusPlaying},
	}
	if !cfg.twoPlayers {
		players = append(players, Player{
			PlayerID: "p3", Username: "C", Hand: []Card{{SuitDiamonds, RankNine}}, Status: PlayerStatusPlaying,
		})
	}

	return State{
		GameID:                       "test",
		Phase:                        PhaseInProgress,
		Players:                      players,
		Deck:                         append([]Card(nil), cfg.deck...),
		TrumpCard:                    cfg.trumpCard,
		TrumpSuit:                    ptrSuit(cfg.trump),
		TablePairs:                   append([]TablePair(nil), cfg.tablePairs...),
		AttackerID:                   "p1",
		DefenderID:                   "p2",
		CurrentPlayerID:              cfg.currentPlayerID,
		PassedPlayerIDs:              make(map[string]struct{}),
		DefenderHandSizeAtRoundStart: cfg.defenderHandSizeAtRoundStart,
	}
}

func testTablePairs(pairs ...TablePair) func(*testCfg) {
	return func(c *testCfg) { c.tablePairs = pairs }
}

func testCurrentPlayer(id string) func(*testCfg) {
	return func(c *testCfg) { c.currentPlayerID = id }
}

func testDeck(deck ...Card) func(*testCfg) {
	return func(c *testCfg) { c.deck = deck }
}

func testTrumpCard(card *Card) func(*testCfg) {
	return func(c *testCfg) { c.trumpCard = card }
}

func testDefenderHandSize(n int) func(*testCfg) {
	return func(c *testCfg) { c.defenderHandSizeAtRoundStart = n }
}

func testTwoPlayers() func(*testCfg) {
	return func(c *testCfg) { c.twoPlayers = true }
}

func newTestMatch(state State) *Match {
	if state.PassedPlayerIDs == nil {
		state.PassedPlayerIDs = make(map[string]struct{})
	}
	return &Match{state: state, clock: fixedClock()}
}

func ptrSuit(s Suit) *Suit { return &s }

func containsCard(hand []Card, c Card) bool {
	for _, x := range hand {
		if x == c {
			return true
		}
	}
	return false
}
