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
	name := flag.String("name", "", "username (логин)")
	username := flag.String("username", "", "alias для --name")
	password := flag.String("password", "", "пароль (если имя занято); иначе из credentials-файла")
	creds := flag.String("creds", "", "путь к credentials.json (пусто = только память процесса; либо FOOLCARD_CREDS)")
	avatar := flag.Int("avatar", 0, "avatar id (matchmaking)")
	quick := flag.Bool("quick", false, "bot: QuickMatch")
	join := flag.String("join", "", "bot: код комнаты")
	think := flag.Duration("think", 0, "bot: фиксированная пауза (0 = случайная think-min..think-max)")
	thinkMin := flag.Duration("think-min", time.Second, "bot: мин. «думанья» (как Android)")
	thinkMax := flag.Duration("think-max", 5*time.Second, "bot: макс. «думанья» (как Android)")
	startMin := flag.Int("start-min", 2, "bot host: минимум игроков перед StartGame")
	flag.Parse()

	nameExplicit := false
	flag.Visit(func(f *flag.Flag) {
		if f.Name == "name" || f.Name == "username" {
			nameExplicit = true
		}
	})

	uname := *name
	if *username != "" {
		uname = *username
		nameExplicit = true
	}
	if uname == "" {
		uname = "player"
	}

	cfg := simclient.Config{
		Addr:      *addr,
		TLS:       *tlsOn,
		Name:      uname,
		Avatar:    int32(*avatar),
		Password:  *password,
		CredsPath: *creds,
		Think:     *think,
		ThinkMin:  *thinkMin,
		ThinkMax:  *thinkMax,
	}
	simclient.ApplyStoredCredentials(&cfg, nameExplicit)

	fmt.Fprintf(os.Stderr, "simclient username=%s creds=%s password_loaded=%v\n",
		cfg.Name, simclient.CredsPathLabel(cfg), cfg.Password != "")

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
		opt := simclient.BotOptions{Quick: *quick, Join: *join, StartMin: *startMin}
		if err := simclient.RunBot(ctx, client, opt, os.Stdout); err != nil && err != context.Canceled {
			fmt.Fprintf(os.Stderr, "bot: %v\n", err)
			os.Exit(1)
		}
	default:
		fmt.Fprintf(os.Stderr, "unknown mode %q\n", *mode)
		os.Exit(2)
	}
}
