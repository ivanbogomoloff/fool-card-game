package auth

import (
	"context"
	"strings"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

type ctxKey int

const tokenCtxKey ctxKey = 1

// TokenFromContext возвращает raw Bearer-токен (этап 3 — валидация в БД).
func TokenFromContext(ctx context.Context) (string, bool) {
	v, ok := ctx.Value(tokenCtxKey).(string)
	return v, ok && v != ""
}

func withToken(ctx context.Context, token string) context.Context {
	return context.WithValue(ctx, tokenCtxKey, token)
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
	// Этап 2: любой non-empty token допустим; проверка в БД — этап 3.
	return token, nil
}

// UnaryInterceptor проверяет Bearer на unary RPC (кроме публичных).
func UnaryInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		if isPublicMethod(info.FullMethod) {
			return handler(ctx, req)
		}
		token, err := bearerToken(ctx)
		if err != nil {
			return nil, err
		}
		return handler(withToken(ctx, token), req)
	}
}

// StreamInterceptor проверяет Bearer на stream RPC.
func StreamInterceptor() grpc.StreamServerInterceptor {
	return func(srv any, ss grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		if isPublicMethod(info.FullMethod) {
			return handler(srv, ss)
		}
		token, err := bearerToken(ss.Context())
		if err != nil {
			return err
		}
		return handler(srv, &wrappedStream{ServerStream: ss, ctx: withToken(ss.Context(), token)})
	}
}

type wrappedStream struct {
	grpc.ServerStream
	ctx context.Context
}

func (w *wrappedStream) Context() context.Context { return w.ctx }
