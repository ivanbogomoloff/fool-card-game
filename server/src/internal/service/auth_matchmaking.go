package service

import (
	"context"

	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/store"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// AuthServer — Login с записью accounts + auth_tokens.
type AuthServer struct {
	pb.UnimplementedAuthServer
	Accounts *store.Accounts
}

func NewAuthServer(accounts *store.Accounts) *AuthServer {
	return &AuthServer{Accounts: accounts}
}

func (s *AuthServer) Login(ctx context.Context, req *pb.LoginRequest) (*pb.LoginResponse, error) {
	if s.Accounts == nil {
		return nil, status.Error(codes.Internal, "Auth: store не настроен")
	}
	name := ""
	if req.DisplayName != nil {
		name = *req.DisplayName
	}
	token, acc, err := s.Accounts.Login(ctx, name)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "login: %v", err)
	}
	return &pb.LoginResponse{
		Token:       token,
		DisplayName: acc.DisplayName,
		AvatarId:    acc.AvatarID,
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
