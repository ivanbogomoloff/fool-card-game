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
	pwd := ResolvePassword(c.Cfg)
	req := &pb.LoginRequest{Username: &c.Cfg.Name}
	if pwd != "" {
		req.Password = &pwd
	}
	resp, err := c.Auth.Login(ctx, req)
	if err != nil {
		return fmt.Errorf("login: %w", err)
	}
	c.Cfg.Token = resp.Token
	plain := resp.GetPassword()
	if plain == "" {
		plain = pwd
	}
	if at, err := PersistLoginResult(&c.Cfg, resp.AccountId, resp.Username, plain); err != nil {
		fmt.Fprintf(out, "bot save credentials: %v\n", err)
	} else {
		fmt.Fprintf(out, "bot credentials → %s\n", at)
	}
	fmt.Fprintf(out, "bot login ok username=%s account_id=%s password=%s\n",
		resp.Username, resp.AccountId, c.Cfg.Password)

	if !opt.Quick && opt.Join != "" {
		jr, err := c.Match.JoinGame(c.AuthedContext(ctx), &pb.JoinGameRequest{
			Code: opt.Join, Username: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
		})
		if err != nil {
			return fmt.Errorf("join: %w", err)
		}
		c.storeIDs(jr.GameId, jr.PlayerId)
		fmt.Fprintf(out, "bot join game=%s player=%s\n", jr.GameId, jr.PlayerId)
	} else if !opt.Quick && opt.Join == "" {
		cr, err := c.Match.CreateGame(c.AuthedContext(ctx), &pb.PlayerProfile{
			Username: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
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
			Username: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
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
