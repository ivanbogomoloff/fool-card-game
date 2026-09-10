package auth

import (
	"context"
	"errors"
	"strings"

	"foolcardgame/server/internal/store"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

type ctxKey int

const (
	tokenCtxKey     ctxKey = 1
	accountIDCtxKey ctxKey = 2
)

// TokenValidator проверяет opaque Bearer и возвращает account_id.
type TokenValidator interface {
	AccountIDByToken(ctx context.Context, token string) (accountID string, err error)
}

// TokenFromContext возвращает raw Bearer-токен, если interceptor его положил.
func TokenFromContext(ctx context.Context) (string, bool) {
	v, ok := ctx.Value(tokenCtxKey).(string)
	return v, ok && v != ""
}

// AccountIDFromContext возвращает id аккаунта после успешной проверки токена.
func AccountIDFromContext(ctx context.Context) (string, bool) {
	v, ok := ctx.Value(accountIDCtxKey).(string)
	return v, ok && v != ""
}

func withAuth(ctx context.Context, token, accountID string) context.Context {
	ctx = context.WithValue(ctx, tokenCtxKey, token)
	return context.WithValue(ctx, accountIDCtxKey, accountID)
}

func isPublicMethod(fullMethod string) bool {
	switch fullMethod {
	case "/foolcard.v1.Auth/Login",
		"/grpc.health.v1.Health/Check",
		"/grpc.health.v1.Health/Watch":
		return true
	default:
		return false
	}
}

func bearerToken(ctx context.Context) (string, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok {
		return "", status.Error(codes.Unauthenticated, "отсутствует metadata authorization")
	}
	vals := md.Get("authorization")
	if len(vals) == 0 {
		return "", status.Error(codes.Unauthenticated, "отсутствует заголовок authorization")
	}
	raw := strings.TrimSpace(vals[0])
	const prefix = "Bearer "
	if !strings.HasPrefix(raw, prefix) {
		return "", status.Error(codes.Unauthenticated, "ожидается Authorization: Bearer <token>")
	}
	token := strings.TrimSpace(strings.TrimPrefix(raw, prefix))
	if token == "" {
		return "", status.Error(codes.Unauthenticated, "пустой Bearer token")
	}
	return token, nil
}

func authenticate(ctx context.Context, v TokenValidator) (context.Context, error) {
	token, err := bearerToken(ctx)
	if err != nil {
		return nil, err
	}
	if v == nil {
		return nil, status.Error(codes.Internal, "auth: TokenValidator не задан")
	}
	accountID, err := v.AccountIDByToken(ctx, token)
	if err != nil {
		if errors.Is(err, store.ErrInvalidToken) {
			return nil, status.Error(codes.Unauthenticated, "недействительный токен")
		}
		return nil, status.Errorf(codes.Internal, "проверка токена: %v", err)
	}
	return withAuth(ctx, token, accountID), nil
}

// UnaryInterceptor проверяет Bearer через БД (кроме публичных методов).
func UnaryInterceptor(v TokenValidator) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		if isPublicMethod(info.FullMethod) {
			return handler(ctx, req)
		}
		ctx, err := authenticate(ctx, v)
		if err != nil {
			return nil, err
		}
		return handler(ctx, req)
	}
}

// StreamInterceptor проверяет Bearer на stream RPC.
func StreamInterceptor(v TokenValidator) grpc.StreamServerInterceptor {
	return func(srv any, ss grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		if isPublicMethod(info.FullMethod) {
			return handler(srv, ss)
		}
		ctx, err := authenticate(ss.Context(), v)
		if err != nil {
			return err
		}
		return handler(srv, &wrappedStream{ServerStream: ss, ctx: ctx})
	}
}

type wrappedStream struct {
	grpc.ServerStream
	ctx context.Context
}

func (w *wrappedStream) Context() context.Context { return w.ctx }
