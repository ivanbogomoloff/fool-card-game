package tests

import (
	"context"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"foolcardgame/server/internal/grpcserver"
	"foolcardgame/server/internal/logging"
	"foolcardgame/server/internal/migrate"
	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/store"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"
)

func startBufServerWithLogger(t *testing.T, logger *logging.Logger) (pb.AuthClient, pb.MatchmakingClient, func()) {
	t.Helper()
	db := openTestDB(t)
	if err := migrate.Up(db); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	accounts := &store.Accounts{DB: db}

	lis := bufconn.Listen(bufSize)
	srv := grpcserver.New(grpcserver.Options{Logger: logger, Accounts: accounts})
	go func() {
		_ = srv.Serve(lis)
	}()

	dialer := func(context.Context, string) (net.Conn, error) {
		return lis.Dial()
	}
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(dialer),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}

	cleanup := func() {
		_ = conn.Close()
		srv.Stop()
		_ = lis.Close()
	}
	return pb.NewAuthClient(conn), pb.NewMatchmakingClient(conn), cleanup
}

func TestLogging_Full_LoginWritesRequestsLog(t *testing.T) {
	dir := t.TempDir()
	logger, err := logging.New(dir, true)
	if err != nil {
		t.Fatalf("logging.New: %v", err)
	}
	defer logger.Close()

	auth, _, cleanup := startBufServerWithLogger(t, logger)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	name := "Тест"
	resp, err := auth.Login(ctx, &pb.LoginRequest{DisplayName: &name})
	if err != nil {
		t.Fatalf("Login: %v", err)
	}
	if resp.Token == "" {
		t.Fatal("empty token")
	}

	time.Sleep(20 * time.Millisecond)
	_ = logger.Close()

	body, err := os.ReadFile(filepath.Join(dir, "requests.log"))
	if err != nil {
		t.Fatalf("read requests.log: %v", err)
	}
	text := string(body)
	if !strings.Contains(text, "IN Auth/Login") {
		t.Fatalf("want IN Auth/Login in requests.log, got:\n%s", text)
	}
	if !strings.Contains(text, "OUT Auth/Login") {
		t.Fatalf("want OUT Auth/Login in requests.log, got:\n%s", text)
	}
	if !strings.Contains(text, "token="+resp.Token) {
		t.Fatalf("want raw token in OUT, got:\n%s", text)
	}
	if !strings.Contains(text, `display_name="Тест"`) {
		t.Fatalf("want display_name in log, got:\n%s", text)
	}
}

func TestLogging_FullFalse_NoRequestsFile_ErrorStillLogged(t *testing.T) {
	dir := t.TempDir()
	logger, err := logging.New(dir, false)
	if err != nil {
		t.Fatalf("logging.New: %v", err)
	}
	defer logger.Close()

	if _, err := os.Stat(filepath.Join(dir, "requests.log")); !os.IsNotExist(err) {
		t.Fatalf("requests.log must not exist when full=false, err=%v", err)
	}

	_, mm, cleanup := startBufServerWithLogger(t, logger)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err = mm.CreateGame(ctx, &pb.PlayerProfile{DisplayName: "A", AvatarId: 0})
	if err == nil {
		t.Fatal("expected Unauthenticated")
	}

	time.Sleep(20 * time.Millisecond)
	_ = logger.Close()

	if _, err := os.Stat(filepath.Join(dir, "requests.log")); !os.IsNotExist(err) {
		t.Fatalf("requests.log must still be absent, err=%v", err)
	}

	body, err := os.ReadFile(filepath.Join(dir, "errors.log"))
	if err != nil {
		t.Fatalf("read errors.log: %v", err)
	}
	text := string(body)
	if !strings.Contains(text, "ERR Matchmaking/CreateGame") {
		t.Fatalf("want ERR Matchmaking/CreateGame in errors.log, got:\n%s", text)
	}
	if !strings.Contains(text, "Unauthenticated") {
		t.Fatalf("want Unauthenticated in errors.log, got:\n%s", text)
	}
}
