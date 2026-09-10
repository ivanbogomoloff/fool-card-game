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
	Addr   string
	TLS    bool
	Name   string
	Avatar int32
	Token  string
	Think  time.Duration
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
	if cfg.Think == 0 {
		cfg.Think = 300 * time.Millisecond
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
