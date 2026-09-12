package simclient

import (
	"context"
	"crypto/tls"
	"fmt"
	"time"

	"foolcardgame/server/internal/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/keepalive"
	"google.golang.org/grpc/metadata"
)

// Config параметры подключения симулятора.
type Config struct {
	Addr      string
	TLS       bool
	Name      string // username
	Avatar    int32
	Token     string
	Password  string // явный пароль или после login
	AccountID string
	CredsPath string
	// Think — фиксированная пауза; если 0, используется случайная [ThinkMin, ThinkMax].
	Think    time.Duration
	ThinkMin time.Duration
	ThinkMax time.Duration
}

// Client обёртка над gRPC без серверной логики.
type Client struct {
	Cfg     Config
	conn    *grpc.ClientConn
	Auth    pb.AuthClient
	Match   pb.MatchmakingClient
	Session pb.GameSessionClient

	gameID   string
	playerID string
}

// Dial открывает gRPC HTTP/2 соединение.
func Dial(cfg Config) (*Client, error) {
	if cfg.Addr == "" {
		cfg.Addr = "127.0.0.1:8080"
	}
	if cfg.ThinkMin == 0 {
		cfg.ThinkMin = time.Second
	}
	if cfg.ThinkMax == 0 {
		cfg.ThinkMax = 5 * time.Second
	}
	if cfg.ThinkMax < cfg.ThinkMin {
		cfg.ThinkMax = cfg.ThinkMin
	}
	var creds credentials.TransportCredentials
	if cfg.TLS {
		creds = credentials.NewTLS(&tls.Config{MinVersion: tls.VersionTLS12})
	} else {
		creds = insecure.NewCredentials()
	}
	conn, err := grpc.NewClient(cfg.Addr,
		grpc.WithTransportCredentials(creds),
		grpc.WithKeepaliveParams(keepalive.ClientParameters{
			Time:                30 * time.Second,
			Timeout:             10 * time.Second,
			PermitWithoutStream: true,
		}),
	)
	if err != nil {
		return nil, fmt.Errorf("dial %s: %w", cfg.Addr, err)
	}
	return &Client{
		Cfg:     cfg,
		conn:    conn,
		Auth:    pb.NewAuthClient(conn),
		Match:   pb.NewMatchmakingClient(conn),
		Session: pb.NewGameSessionClient(conn),
	}, nil
}

func (c *Client) Close() error {
	if c.conn == nil {
		return nil
	}
	return c.conn.Close()
}

// AuthedContext добавляет Bearer metadata.
func (c *Client) AuthedContext(parent context.Context) context.Context {
	if c.Cfg.Token == "" {
		return parent
	}
	return metadata.NewOutgoingContext(parent, metadata.Pairs("authorization", "Bearer "+c.Cfg.Token))
}
