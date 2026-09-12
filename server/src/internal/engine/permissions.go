package engine

// ActionPermissions — права игрока на действия.
type ActionPermissions struct {
	CanReady bool
	CanBito  bool
	CanPass  bool
	CanTake  bool
}

// PermissionsFor вычисляет права игрока из состояния.
func (s State) PermissionsFor(playerID string) ActionPermissions {
	p := s.Player(playerID)
	if p == nil {
		return ActionPermissions{}
	}
	switch s.Phase {
	case PhaseLobbyWaiting:
		return ActionPermissions{CanReady: !p.IsReady}
	case PhaseFinished:
		return ActionPermissions{}
	default:
		return s.inProgressPermissions(playerID)
	}
}

func (s State) inProgressPermissions(playerID string) ActionPermissions {
	isDefender := playerID == s.DefenderID
	isAttacker := playerID == s.AttackerID
	hasUnbeaten := len(s.UnbeatenPairs()) > 0

	canTake := isDefender && hasUnbeaten
	canBito := isAttacker && s.AllBeaten() && !s.AttackerBitoDeclared
	canPass := !isDefender && !isAttacker &&
		s.AttackerBitoDeclared &&
		s.AllBeaten() &&
		s.isThrower(playerID) &&
		!s.hasPassed(playerID)

	return ActionPermissions{
		CanBito:  canBito,
		CanPass:  canPass,
		CanTake:  canTake,
	}
}

func (s State) isThrower(playerID string) bool {
	_, ok := s.throwerIDs()[playerID]
	return ok
}

func (s State) hasPassed(playerID string) bool {
	_, ok := s.PassedPlayerIDs[playerID]
	return ok
}

func (s State) throwerIDs() map[string]struct{} {
	out := make(map[string]struct{})
	for _, p := range s.Players {
		if !p.IsFinished && p.PlayerID != s.DefenderID && len(p.Hand) > 0 {
			out[p.PlayerID] = struct{}{}
		}
	}
	return out
}

func (s State) helperThrowerIDs() map[string]struct{} {
	all := s.throwerIDs()
	delete(all, s.AttackerID)
	return all
}

// ThrowingClosed — фаза подкида закрыта (ждём подтверждения «Бито»).
func (s State) ThrowingClosed() bool {
	if !s.AllBeaten() {
		return false
	}
	if !s.CanAddMoreAttacks() {
		return true
	}
	if !s.AttackerBitoDeclared {
		return false
	}
	helpers := s.helperThrowerIDs()
	if len(helpers) == 0 {
		return true
	}
	for id := range helpers {
		if !s.hasPassed(id) {
			return false
		}
	}
	return true
}

// CanAddMoreAttacks — лимит карт на столе.
func (s State) CanAddMoreAttacks() bool {
	return (Rules{}).CanAddAttackCard(len(s.TablePairs), s.DefenderHandSizeAtRoundStart)
}

func (s State) throwPhaseTurnOrder() []string {
	if !s.AllBeaten() || !s.AttackerBitoDeclared {
		return nil
	}
	attacker := s.AttackerID
	defender := s.DefenderID
	helpers := s.helperThrowerIDs()
	if len(helpers) == 0 || len(s.Players) == 0 {
		return nil
	}

	attIdx := -1
	for i, p := range s.Players {
		if p.PlayerID == attacker {
			attIdx = i
			break
		}
	}
	if attIdx < 0 {
		ordered := make([]string, 0, len(helpers))
		for id := range helpers {
			ordered = append(ordered, id)
		}
		return ordered
	}

	ordered := make([]string, 0, len(helpers))
	for i := 1; i < len(s.Players); i++ {
		p := s.Players[(attIdx+i)%len(s.Players)]
		if p.PlayerID == defender {
			continue
		}
		if _, ok := helpers[p.PlayerID]; ok {
			ordered = append(ordered, p.PlayerID)
		}
	}
	return ordered
}

func (s State) nextThrowPhaseActor() string {
	for _, id := range s.throwPhaseTurnOrder() {
		if !s.hasPassed(id) {
			return id
		}
	}
	return ""
}
