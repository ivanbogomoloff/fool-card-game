package logging

import (
	"context"
	"fmt"
	"log"
	"time"

	"foolcardgame/server/internal/pb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// Unary — access-лог unary RPC: stdout всегда; IN/OUT в requests.log при Full.
func Unary(l *Logger) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (resp any, err error) {
		start := time.Now()
		method := shortMethod(info.FullMethod)

		if l != nil && l.Full() {
			l.Request("IN %s %s", method, protoSummary(req))
		}

		defer func() {
			if r := recover(); r != nil {
				err = status.Errorf(codes.Internal, "panic: %v", r)
				if l != nil {
					l.Error("ERR %s panic=%v", method, r)
				}
			}
		}()

		resp, err = handler(ctx, req)
		dur := time.Since(start)
		code := status.Code(err)

		log.Printf("gRPC %s code=%s dur=%s", method, code, dur.Round(time.Microsecond))

		if l != nil && l.Full() {
			if err != nil {
				l.Request("OUT %s code=%s dur=%s err=%v", method, code, dur.Round(time.Microsecond), err)
			} else {
				l.Request("OUT %s code=%s dur=%s %s", method, code, dur.Round(time.Microsecond), protoSummary(resp))
			}
		}
		if err != nil && l != nil {
			l.Error("ERR %s code=%s dur=%s err=%v", method, code, dur.Round(time.Microsecond), err)
		}
		return resp, err
	}
}

// Stream — лог открытия/закрытия stream; при Full — Recv/Send с типом oneof.
func Stream(l *Logger) grpc.StreamServerInterceptor {
	return func(srv any, ss grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) (err error) {
		start := time.Now()
		method := shortMethod(info.FullMethod)

		if l != nil && l.Full() {
			l.Request("IN %s stream=open", method)
		}

		wrapped := ss
		if l != nil && l.Full() {
			wrapped = &loggingStream{ServerStream: ss, logger: l, method: method}
		}

		defer func() {
			if r := recover(); r != nil {
				err = status.Errorf(codes.Internal, "panic: %v", r)
				if l != nil {
					l.Error("ERR %s panic=%v", method, r)
				}
			}
		}()

		err = handler(srv, wrapped)
		dur := time.Since(start)
		code := status.Code(err)

		log.Printf("gRPC %s stream code=%s dur=%s", method, code, dur.Round(time.Microsecond))

		if l != nil && l.Full() {
			if err != nil {
				l.Request("OUT %s stream=close code=%s dur=%s err=%v", method, code, dur.Round(time.Microsecond), err)
			} else {
				l.Request("OUT %s stream=close code=%s dur=%s", method, code, dur.Round(time.Microsecond))
			}
		}
		if err != nil && l != nil {
			l.Error("ERR %s stream code=%s dur=%s err=%v", method, code, dur.Round(time.Microsecond), err)
		}
		return err
	}
}

type loggingStream struct {
	grpc.ServerStream
	logger *Logger
	method string
}

func (s *loggingStream) RecvMsg(m any) error {
	err := s.ServerStream.RecvMsg(m)
	if err != nil {
		return err
	}
	s.logger.Request("IN %s recv=%s", s.method, messageKind(m))
	return nil
}

func (s *loggingStream) SendMsg(m any) error {
	s.logger.Request("OUT %s send=%s", s.method, messageKind(m))
	return s.ServerStream.SendMsg(m)
}

func shortMethod(full string) string {
	// /foolcard.v1.Auth/Login → Auth/Login
	if i := lastSlashBefore(full); i >= 0 {
		svc := full[1:i] // без ведущего /
		if j := lastDot(svc); j >= 0 {
			svc = svc[j+1:]
		}
		return svc + full[i:]
	}
	return full
}

func lastSlashBefore(s string) int {
	for i := len(s) - 1; i >= 0; i-- {
		if s[i] == '/' {
			return i
		}
	}
	return -1
}

func lastDot(s string) int {
	for i := len(s) - 1; i >= 0; i-- {
		if s[i] == '.' {
			return i
		}
	}
	return -1
}

func protoSummary(v any) string {
	if v == nil {
		return "<nil>"
	}
	if m, ok := v.(proto.Message); ok {
		return compactProto(m)
	}
	return fmt.Sprintf("%T", v)
}

func compactProto(m proto.Message) string {
	switch x := m.(type) {
	case *pb.LoginRequest:
		name := ""
		if x.Username != nil {
			name = *x.Username
		}
		hasPwd := x.Password != nil && *x.Password != ""
		return fmt.Sprintf("username=%q password_set=%v", name, hasPwd)
	case *pb.LoginResponse:
		reg := x.Password != nil && *x.Password != ""
		return fmt.Sprintf("token=%s username=%q account_id=%s registered=%v", x.Token, x.Username, x.AccountId, reg)
	case *pb.PlayerProfile:
		return fmt.Sprintf("username=%q avatar_id=%d", x.Username, x.AvatarId)
	case *pb.JoinGameRequest:
		return fmt.Sprintf("code=%q username=%q", x.Code, x.Username)
	default:
		return fmt.Sprintf("%T{%v}", m, m)
	}
}

func messageKind(m any) string {
	switch x := m.(type) {
	case *pb.ClientMessage:
		return clientPayloadKind(x)
	case *pb.ServerMessage:
		return serverPayloadKind(x)
	case proto.Message:
		return string(x.ProtoReflect().Descriptor().Name())
	default:
		return fmt.Sprintf("%T", m)
	}
}

func clientPayloadKind(m *pb.ClientMessage) string {
	if m == nil {
		return "ClientMessage<nil>"
	}
	switch m.Payload.(type) {
	case *pb.ClientMessage_QuickMatch:
		return "QuickMatch"
	case *pb.ClientMessage_Subscribe:
		return "Subscribe"
	case *pb.ClientMessage_Kick:
		return "Kick"
	case *pb.ClientMessage_StartGame:
		return "StartGame"
	case *pb.ClientMessage_PlayCard:
		return "PlayCard"
	case *pb.ClientMessage_AddCard:
		return "AddCard"
	case *pb.ClientMessage_Pass:
		return "Pass"
	case *pb.ClientMessage_Bito:
		return "Bito"
	case *pb.ClientMessage_Ready:
		return "Ready"
	case *pb.ClientMessage_Leave:
		return "Leave"
	case *pb.ClientMessage_Ping:
		return "Ping"
	case nil:
		return "empty"
	default:
		return fmt.Sprintf("%T", m.Payload)
	}
}

func serverPayloadKind(m *pb.ServerMessage) string {
	if m == nil {
		return "ServerMessage<nil>"
	}
	switch m.Payload.(type) {
	case *pb.ServerMessage_QueueState:
		return "QueueState"
	case *pb.ServerMessage_RoomState:
		return "RoomState"
	case *pb.ServerMessage_GameState:
		return "GameState"
	case *pb.ServerMessage_Error:
		return "Error"
	case *pb.ServerMessage_Kicked:
		return "Kicked"
	case *pb.ServerMessage_MatchStarted:
		return "MatchStarted"
	case *pb.ServerMessage_LeftAck:
		return "LeftAck"
	case *pb.ServerMessage_Pong:
		return "Pong"
	case nil:
		return "empty"
	default:
		return fmt.Sprintf("%T", m.Payload)
	}
}
