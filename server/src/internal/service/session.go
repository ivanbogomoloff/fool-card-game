package service

import (
	"io"

	"foolcardgame/server/internal/pb"

	"google.golang.org/grpc/codes"
)

// SessionServer — bidi Session; на этапе 2 поддерживается Ping→Pong.
type SessionServer struct {
	pb.UnimplementedGameSessionServer
}

func NewSessionServer() *SessionServer { return &SessionServer{} }

func (s *SessionServer) Session(stream pb.GameSession_SessionServer) error {
	for {
		msg, err := stream.Recv()
		if err == io.EOF {
			return nil
		}
		if err != nil {
			return err
		}

		switch payload := msg.Payload.(type) {
		case *pb.ClientMessage_Ping:
			if err := stream.Send(&pb.ServerMessage{
				Payload: &pb.ServerMessage_Pong{Pong: &pb.Pong{}},
			}); err != nil {
				return err
			}
		case *pb.ClientMessage_Leave:
			if err := stream.Send(&pb.ServerMessage{
				Payload: &pb.ServerMessage_LeftAck{LeftAck: &pb.LeftAck{}},
			}); err != nil {
				return err
			}
			return nil
		case nil:
			if err := stream.Send(&pb.ServerMessage{
				Payload: &pb.ServerMessage_Error{Error: &pb.Error{
					Code:    "EMPTY",
					Message: "пустое ClientMessage",
				}},
			}); err != nil {
				return err
			}
		default:
			_ = payload
			if err := stream.Send(&pb.ServerMessage{
				Payload: &pb.ServerMessage_Error{Error: &pb.Error{
					Code:    codes.Unimplemented.String(),
					Message: "обработчик будет на этапах 4–5",
				}},
			}); err != nil {
				return err
			}
		}
	}
}

// Ensure interface compliance for tests that dial Session.
var _ pb.GameSessionServer = (*SessionServer)(nil)
