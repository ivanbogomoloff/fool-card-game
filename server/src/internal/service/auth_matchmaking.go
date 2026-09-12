package service

import (
	"context"
	"errors"

	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/store"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// AuthServer — Login с уникальным username и паролем.
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
	username := ""
	if req.Username != nil {
		username = *req.Username
	}
	password := ""
	if req.Password != nil {
		password = *req.Password
	}

	res, err := s.Accounts.Login(ctx, username, password)
	if err != nil {
		switch {
		case errors.Is(err, store.ErrEmptyUsername):
			return nil, status.Error(codes.InvalidArgument, "укажите username")
		case errors.Is(err, store.ErrNameTakenNeedPassword):
			return nil, status.Error(codes.FailedPrecondition, "имя занято, укажите пароль")
		case errors.Is(err, store.ErrInvalidPassword):
			return nil, status.Error(codes.Unauthenticated, "неверный пароль")
		default:
			return nil, status.Errorf(codes.Internal, "login: %v", err)
		}
	}

	resp := &pb.LoginResponse{
		Token:     res.Token,
		Username:  res.Account.Username,
		AccountId: res.Account.ID,
	}
	if res.IsRegistration && res.PlainPassword != "" {
		pwd := res.PlainPassword
		resp.Password = &pwd
	}
	return resp, nil
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
