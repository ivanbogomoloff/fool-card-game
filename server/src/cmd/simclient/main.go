package main

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"foolcardgame/server/internal/simclient"
)

func main() {
	mode := flag.String("mode", "human", "human | bot")
	addr := flag.String("addr", "127.0.0.1:8080", "gRPC host:port")
	tlsOn := flag.Bool("tls", false, "использовать TLS")
	name := flag.String("name", "Игрок", "display name")
	avatar := flag.Int("avatar", 0, "avatar id")
	quick := flag.Bool("quick", false, "bot: QuickMatch")
	join := flag.String("join", "", "bot: код комнаты")
	think := flag.Duration("think", 300*time.Millisecond, "bot: пауза между ходами")
	flag.Parse()

	cfg := simclient.Config{
		Addr:   *addr,
		TLS:    *tlsOn,
		Name:   *name,
		Avatar: int32(*avatar),
		Think:  *think,
	}
	client, err := simclient.Dial(cfg)
	if err != nil {
		fmt.Fprintf(os.Stderr, "dial: %v\n", err)
		os.Exit(1)
	}
	defer client.Close()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	switch *mode {
	case "human":
		if err := simclient.RunHuman(ctx, client, os.Stdin, os.Stdout); err != nil && err != context.Canceled {
			fmt.Fprintf(os.Stderr, "human: %v\n", err)
			os.Exit(1)
		}
	case "bot":
		opt := simclient.BotOptions{Quick: *quick, Join: *join}
		if err := simclient.RunBot(ctx, client, opt, os.Stdout); err != nil && err != context.Canceled {
			fmt.Fprintf(os.Stderr, "bot: %v\n", err)
			os.Exit(1)
		}
	default:
		fmt.Fprintf(os.Stderr, "unknown mode %q\n", *mode)
		os.Exit(2)
	}
}
