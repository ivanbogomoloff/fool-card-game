package simclient

import (
	"context"
	"fmt"
	"io"
	"math/rand"
	"time"

	"foolcardgame/server/internal/pb"
)

// BotOptions режим бота.
type BotOptions struct {
	Quick bool
	Join  string // код комнаты; пусто если Quick
}

// RunBot login → quick|join → авто-реакции по GameState.
func RunBot(ctx context.Context, c *Client, opt BotOptions, out io.Writer) error {
	resp, err := c.Auth.Login(ctx, &pb.LoginRequest{DisplayName: &c.Cfg.Name})
	if err != nil {
		return fmt.Errorf("login: %w", err)
	}
	c.Cfg.Token = resp.Token
	fmt.Fprintf(out, "bot login ok name=%s\n", resp.DisplayName)

	if !opt.Quick && opt.Join != "" {
		jr, err := c.Match.JoinGame(c.AuthedContext(ctx), &pb.JoinGameRequest{
			Code: opt.Join, DisplayName: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
		})
		if err != nil {
			return fmt.Errorf("join: %w", err)
		}
		c.storeIDs(jr.GameId, jr.PlayerId)
		fmt.Fprintf(out, "bot join game=%s player=%s\n", jr.GameId, jr.PlayerId)
	} else if !opt.Quick && opt.Join == "" {
		cr, err := c.Match.CreateGame(c.AuthedContext(ctx), &pb.PlayerProfile{
			DisplayName: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
		})
		if err != nil {
			fmt.Fprintf(out, "bot create (may be unimplemented): %v\n", err)
		} else {
			c.storeIDs(cr.GameId, cr.PlayerId)
			fmt.Fprintf(out, "bot create game=%s code=%s\n", cr.GameId, cr.AccessCode)
		}
	}

	stream, err := c.Session.Session(c.AuthedContext(ctx))
	if err != nil {
		return fmt.Errorf("session: %w", err)
	}
	hub := &SessionHub{out: out}
	hub.SetStream(stream)
	sessionCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	go hub.readLoop(cancel)

	if opt.Quick {
		if err := hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{
			DisplayName: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
		}}}); err != nil {
			return err
		}
		fmt.Fprintln(out, "bot QuickMatch sent")
	} else if c.gameID != "" && c.playerID != "" {
		if err := hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Subscribe{Subscribe: &pb.Subscribe{
			GameId: c.gameID, PlayerId: c.playerID,
		}}}); err != nil {
			fmt.Fprintf(out, "bot subscribe: %v\n", err)
		}
	}

	ticker := time.NewTicker(c.Cfg.Think)
	defer ticker.Stop()
	for {
		select {
		case <-sessionCtx.Done():
			return sessionCtx.Err()
		case <-ctx.Done():
			_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Leave{Leave: &pb.Leave{}}})
			return ctx.Err()
		case <-ticker.C:
			botTick(hub, c, out)
		}
	}
}

func botTick(hub *SessionHub, c *Client, out io.Writer) {
	st := hub.GameState()
	if st == nil {
		// если мы host в room — пробуем start
		hub.mu.RLock()
		room := hub.room
		hub.mu.RUnlock()
		if room != nil && !room.Started && c.playerID != "" && room.HostId == c.playerID && len(room.Players) >= 2 {
			_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_StartGame{StartGame: &pb.StartGame{}}})
			fmt.Fprintln(out, "bot StartGame")
		}
		return
	}
	if st.CanReady {
		_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Ready{Ready: &pb.Ready{}}})
		fmt.Fprintln(out, "bot Ready")
		return
	}
	if st.CanBito {
		_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Bito{Bito: &pb.Bito{}}})
		fmt.Fprintln(out, "bot Bito")
		return
	}
	if st.CanPass || st.CanTake {
		_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Pass{Pass: &pb.Pass{}}})
		fmt.Fprintln(out, "bot Pass")
		return
	}
	if len(st.LocalHand) == 0 {
		return
	}
	// простая политика: случайная карта; если есть незакрытая пара — бьём первой
	hi := rand.Intn(len(st.LocalHand))
	var undefended *pb.TablePair
	for _, p := range st.TablePairs {
		if p.Defense == nil {
			undefended = p
			break
		}
	}
	intent := PlayIntent{Kind: "play", HandIndex: hi, HandCard: st.LocalHand[hi]}
	if undefended != nil {
		pid := undefended.Id
		intent.TargetPairID = &pid
	} else if len(st.TablePairs) > 0 {
		intent.Kind = "add"
	}
	_ = hub.Send(intent.ToClientMessage())
	fmt.Fprintf(out, "bot play hand[%d]\n", hi+1)
}
