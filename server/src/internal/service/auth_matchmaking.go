package service

import (
	"context"
	"errors"

	"foolcardgame/server/internal/auth"
	"foolcardgame/server/internal/hub"
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

// MatchmakingServer — private Create/Join.
type MatchmakingServer struct {
	pb.UnimplementedMatchmakingServer
	Hub *hub.Hub
}

func NewMatchmakingServer(h *hub.Hub) *MatchmakingServer {
	return &MatchmakingServer{Hub: h}
}

func (s *MatchmakingServer) CreateGame(ctx context.Context, req *pb.PlayerProfile) (*pb.CreateGameResponse, error) {
	if s.Hub == nil {
		return nil, status.Error(codes.Internal, "hub не настроен")
	}
	accountID, ok := auth.AccountIDFromContext(ctx)
	if !ok {
		return nil, status.Error(codes.Unauthenticated, "нет account_id")
	}
	username := ""
	var avatar int32
	if req != nil {
		username = req.Username
		avatar = req.AvatarId
	}
	resp, err := s.Hub.CreatePrivate(accountID, username, avatar)
	if err != nil {
		return nil, status.Errorf(codes.FailedPrecondition, "%v", err)
	}
	return resp, nil
}

func (s *MatchmakingServer) JoinGame(ctx context.Context, req *pb.JoinGameRequest) (*pb.JoinGameResponse, error) {
	if s.Hub == nil {
		return nil, status.Error(codes.Internal, "hub не настроен")
	}
	accountID, ok := auth.AccountIDFromContext(ctx)
	if !ok {
		return nil, status.Error(codes.Unauthenticated, "нет account_id")
	}
	code, username := "", ""
	var avatar int32
	if req != nil {
		code = req.Code
		username = req.Username
		avatar = req.AvatarId
	}
	resp, err := s.Hub.JoinPrivate(accountID, code, username, avatar)
	if err != nil {
		msg := err.Error()
		if msg == "комната не найдена" {
			return nil, status.Error(codes.NotFound, msg)
		}
		return nil, status.Errorf(codes.FailedPrecondition, "%v", err)
	}
	return resp, nil
}
