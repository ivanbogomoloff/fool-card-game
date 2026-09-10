package tests

import (
	"context"
	"io"
	"net"
	"testing"
	"time"

	"foolcardgame/server/internal/grpcserver"
	"foolcardgame/server/internal/pb"

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
	lis := bufconn.Listen(bufSize)
	srv := grpcserver.New(grpcserver.Options{})
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

func TestSession_PingPong(t *testing.T) {
	_, _, session, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	ctx = metadata.NewOutgoingContext(ctx, metadata.Pairs("authorization", "Bearer test-token"))

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

	_, err := mm.CreateGame(ctx, &pb.PlayerProfile{DisplayName: "A", AvatarId: 0})
	if err == nil {
		t.Fatal("expected Unauthenticated")
	}
	st, ok := status.FromError(err)
	if !ok || st.Code() != codes.Unauthenticated {
		t.Fatalf("want Unauthenticated, got %v", err)
	}
}

func TestAuthInterceptor_WithBearer_ReachesHandler(t *testing.T) {
	_, mm, _, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	ctx = metadata.NewOutgoingContext(ctx, metadata.Pairs("authorization", "Bearer any"))

	_, err := mm.CreateGame(ctx, &pb.PlayerProfile{DisplayName: "A", AvatarId: 0})
	st, ok := status.FromError(err)
	if !ok || st.Code() != codes.Unimplemented {
		t.Fatalf("want Unimplemented stub after auth, got %v", err)
	}
}

func TestLogin_PublicWithoutBearer(t *testing.T) {
	auth, _, _, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	resp, err := auth.Login(ctx, &pb.LoginRequest{})
	if err != nil {
		t.Fatalf("Login: %v", err)
	}
	if resp.Token == "" {
		t.Fatal("empty token")
	}
}

func TestSession_RequiresBearer(t *testing.T) {
	_, _, session, cleanup := startBufServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	stream, err := session.Session(ctx)
	if err != nil {
		// Некоторые версии gRPC отдают ошибку уже на открытии stream.
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
