package hub

import "foolcardgame/server/internal/pb"

// RoomPlayer — участник private-лобби / краткого waiting QuickMatch.
type RoomPlayer struct {
	PlayerID  string
	AccountID string
	Username  string
	AvatarID  int32
	IsHost    bool
	Status    pb.PlayerStatus
}

// RoomMeta — метаданные комнаты до/после старта.
type RoomMeta struct {
	GameID     string
	AccessCode string
	HostID     string
	Started    bool
	Players    []RoomPlayer
}

func (r *RoomMeta) findPlayer(playerID string) *RoomPlayer {
	for i := range r.Players {
		if r.Players[i].PlayerID == playerID {
			return &r.Players[i]
		}
	}
	return nil
}

func (r *RoomMeta) findByAccount(accountID string) *RoomPlayer {
	for i := range r.Players {
		if r.Players[i].AccountID == accountID {
			return &r.Players[i]
		}
	}
	return nil
}

func (r *RoomMeta) removePlayer(playerID string) bool {
	for i := range r.Players {
		if r.Players[i].PlayerID == playerID {
			r.Players = append(r.Players[:i], r.Players[i+1:]...)
			return true
		}
	}
	return false
}

func (r *RoomMeta) toProto() *pb.RoomState {
	players := make([]*pb.RoomPlayer, 0, len(r.Players))
	for _, p := range r.Players {
		if p.Status == pb.PlayerStatus_LEFT {
			continue
		}
		players = append(players, &pb.RoomPlayer{
			Id:       p.PlayerID,
			Username: p.Username,
			AvatarId: p.AvatarID,
			IsHost:   p.IsHost,
		})
	}
	return &pb.RoomState{
		GameId:     r.GameID,
		AccessCode: r.AccessCode,
		HostId:     r.HostID,
		Started:    r.Started,
		Players:    players,
	}
}
