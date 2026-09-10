package service

import (
	"context"

	"foolcardgame/server/internal/pb"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// AuthServer — заглушка Login (реальная БД — этап 3).
type AuthServer struct {
	pb.UnimplementedAuthServer
}

func NewAuthServer() *AuthServer { return &AuthServer{} }

func (s *AuthServer) Login(_ context.Context, req *pb.LoginRequest) (*pb.LoginResponse, error) {
	name := "Игрок"
	if req.DisplayName != nil && *req.DisplayName != "" {
		name = *req.DisplayName
	}
	// Этап 2: фиктивный token; этап 3 сохранит в auth_tokens.
	return &pb.LoginResponse{
		Token:       "stage2-dev-token",
		DisplayName: name,
		AvatarId:    0,
	}, nil
}

// MatchmakingServer — заглушки private create/join (логика — этап 4).
type MatchmakingServer struct {
	pb.UnimplementedMatchmakingServer
}

func NewMatchmakingServer() *MatchmakingServer { return &MatchmakingServer{} }

func (s *MatchmakingServer) CreateGame(context.Context, *pb.PlayerProfile) (*pb.CreateGameResponse, error) {
	return nil, status.Error(codes.Unimplemented, "CreateGame: этап 4")
}

func (s *MatchmakingServer) JoinGame(context.Context, *pb.JoinGameRequest) (*pb.JoinGameResponse, error) {
	return nil, status.Error(codes.Unimplemented, "JoinGame: этап 4")
}
