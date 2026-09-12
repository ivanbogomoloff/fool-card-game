package tests

import (
	"context"
	"io"
	"net"
	"testing"
	"time"

	"foolcardgame/server/internal/grpcserver"
	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/store"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

const bufSize = 1024 * 1024

func startBufServer(t *testing.T) (pb.AuthClient, pb.MatchmakingClient, pb.GameSessionClient, func()) {
	t.Helper()
	db := openTestDB(t)
	ensureMigrated(t, db)
	accounts := &store.Accounts{DB: db}

	lis := bufconn.Listen(bufSize)
	srv := grpcserver.New(grpcserver.Options{Accounts: accounts})
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
	return pb.NewAuthClient(conn), pb.NewMatchmakingClient(conn), pb.NewGameSessionClient(conn), cleanup
}

func loginRegister(t *testing.T, auth pb.AuthClient, username string) (token, password string) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	resp, err := auth.Login(ctx, &pb.LoginRequest{Username: &username})
	if err != nil {
		t.Fatalf("Login register %q: %v", username, err)
	}
	if resp.Token == "" || resp.AccountId == "" {
		t.Fatal("empty token/account_id")
	}
	if resp.Password == nil || len(*resp.Password) < store.MinPasswordLen {
		t.Fatalf("want generated password, got %v", resp.Password)
	}
	return resp.Token, *resp.Password
}

// uniqName — короткое уникальное имя для регистрации между прогонами.
func uniqName(prefix string) string {
	return prefix + "_" + time.Now().Format("150405.000")
}

func TestSession_PingPong(t *testing.T) {
	auth, _, session, cleanup := startBufServer(t)
	defer cleanup()

	token, _ := loginRegister(t, auth, uniqName("PingUser"))

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	ctx = metadata.NewOutgoingContext(ctx, metadata.Pairs("authorization", "Bearer "+token))

	stream, err := session.Session(ctx)
	if err != nil {
		t.Fatalf("Session: %v", err)
	}
	if err := stream.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Ping{Ping: &pb.Ping{}}}); err != nil {
		t.Fatalf("Send Ping: %v", err)
	}
	msg, err := stream.Recv()
	if err != nil {
		t.Fatalf("Recv: %v", err)
	}
	if msg.GetPong() == nil {
		t.Fatalf("want Pong, got %#v", msg.Payload)
	}
	_ = stream.CloseSend()
}

func TestAuthInterceptor_MissingBearer(t *testing.T) {
	_, mm, _, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err := mm.CreateGame(ctx, &pb.PlayerProfile{Username: "A", AvatarId: 0})
	if err == nil {
		t.Fatal("expected Unauthenticated")
	}
	st, ok := status.FromError(err)
	if !ok || st.Code() != codes.Unauthenticated {
		t.Fatalf("want Unauthenticated, got %v", err)
	}
}

func TestAuthInterceptor_InvalidToken(t *testing.T) {
	_, mm, _, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	ctx = metadata.NewOutgoingContext(ctx, metadata.Pairs("authorization", "Bearer deadbeef"))

	_, err := mm.CreateGame(ctx, &pb.PlayerProfile{Username: "A", AvatarId: 0})
	st, ok := status.FromError(err)
	if !ok || st.Code() != codes.Unauthenticated {
		t.Fatalf("want Unauthenticated for invalid token, got %v", err)
	}
}

func TestAuthInterceptor_WithBearer_ReachesHandler(t *testing.T) {
	auth, mm, _, cleanup := startBufServer(t)
	defer cleanup()

	token, _ := loginRegister(t, auth, uniqName("BearerUser"))

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	ctx = metadata.NewOutgoingContext(ctx, metadata.Pairs("authorization", "Bearer "+token))

	resp, err := mm.CreateGame(ctx, &pb.PlayerProfile{Username: "A", AvatarId: 0})
	if err != nil {
		t.Fatalf("CreateGame after auth: %v", err)
	}
	if resp.GameId == "" || resp.AccessCode == "" || resp.PlayerId == "" {
		t.Fatalf("incomplete CreateGameResponse: %#v", resp)
	}
}

func TestLogin_EmptyUsername(t *testing.T) {
	auth, _, _, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err := auth.Login(ctx, &pb.LoginRequest{})
	st, ok := status.FromError(err)
	if !ok || st.Code() != codes.InvalidArgument {
		t.Fatalf("want InvalidArgument, got %v", err)
	}
}

func TestLogin_NameTakenNeedsPassword(t *testing.T) {
	auth, _, _, cleanup := startBufServer(t)
	defer cleanup()

	name := uniqName("Taken")
	_, _ = loginRegister(t, auth, name)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_, err := auth.Login(ctx, &pb.LoginRequest{Username: &name})
	st, ok := status.FromError(err)
	if !ok || st.Code() != codes.FailedPrecondition {
		t.Fatalf("want FailedPrecondition, got %v", err)
	}
}

func TestLogin_ReloginWithPassword(t *testing.T) {
	auth, _, _, cleanup := startBufServer(t)
	defer cleanup()

	name := uniqName("Relogin")
	_, pwd := loginRegister(t, auth, name)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	resp, err := auth.Login(ctx, &pb.LoginRequest{Username: &name, Password: &pwd})
	if err != nil {
		t.Fatalf("relogin: %v", err)
	}
	if resp.Password != nil {
		t.Fatal("password must not be in relogin response")
	}
	if resp.AccountId == "" || resp.Token == "" {
		t.Fatal("empty account/token")
	}
}

func TestSession_RequiresBearer(t *testing.T) {
	_, _, session, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	stream, err := session.Session(ctx)
	if err != nil {
		st, ok := status.FromError(err)
		if ok && st.Code() == codes.Unauthenticated {
			return
		}
		t.Fatalf("Session open: %v", err)
	}
	err = stream.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Ping{Ping: &pb.Ping{}}})
	if err == nil {
		_, err = stream.Recv()
	}
	if err == nil || err == io.EOF {
		t.Fatal("expected Unauthenticated without bearer")
	}
	st, ok := status.FromError(err)
	if !ok || st.Code() != codes.Unauthenticated {
		t.Fatalf("want Unauthenticated, got %v", err)
	}
}
