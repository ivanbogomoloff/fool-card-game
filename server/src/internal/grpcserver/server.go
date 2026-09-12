package grpcserver

import (
	"time"

	"foolcardgame/server/internal/auth"
	"foolcardgame/server/internal/hub"
	"foolcardgame/server/internal/logging"
	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/service"
	"foolcardgame/server/internal/store"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/health"
	healthpb "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/keepalive"
)

// Options параметры создания gRPC Server.
type Options struct {
	// TLS — credentials для prod; nil = insecure (local).
	TLS credentials.TransportCredentials
	// Logger — access/error логи; nil = только stdout без файлов.
	Logger *logging.Logger
	// Accounts — Login и проверка Bearer; обязателен для защищённых RPC.
	Accounts *store.Accounts
	// Hub — matchmaking + session; если nil, создаётся с дефолтами.
	Hub *hub.Hub
}

// New создаёт сервер с keepalive, interceptor и зарегистрированными сервисами.
func New(opts Options) *grpc.Server {
	ka := keepalive.ServerParameters{
		Time:                  45 * time.Second,
		Timeout:               10 * time.Second,
		MaxConnectionAge:      30 * time.Minute,
		MaxConnectionAgeGrace: 30 * time.Second,
	}
	enf := keepalive.EnforcementPolicy{
		MinTime:             30 * time.Second,
		PermitWithoutStream: true,
	}

	serverOpts := []grpc.ServerOption{
		grpc.KeepaliveParams(ka),
		grpc.KeepaliveEnforcementPolicy(enf),
		grpc.ChainUnaryInterceptor(logging.Unary(opts.Logger), auth.UnaryInterceptor(opts.Accounts)),
		grpc.ChainStreamInterceptor(logging.Stream(opts.Logger), auth.StreamInterceptor(opts.Accounts)),
	}
	if opts.TLS != nil {
		serverOpts = append(serverOpts, grpc.Creds(opts.TLS))
	}

	s := grpc.NewServer(serverOpts...)

	h := opts.Hub
	if h == nil {
		h = hub.New(hub.Config{})
	}

	pb.RegisterAuthServer(s, service.NewAuthServer(opts.Accounts))
	pb.RegisterMatchmakingServer(s, service.NewMatchmakingServer(h))
	pb.RegisterGameSessionServer(s, service.NewSessionServer(h))

	hs := health.NewServer()
	hs.SetServingStatus("", healthpb.HealthCheckResponse_SERVING)
	healthpb.RegisterHealthServer(s, hs)

	return s
}
