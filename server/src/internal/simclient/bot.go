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
	Quick    bool
	Join     string // код комнаты; пусто если Quick
	StartMin int    // минимум игроков в комнате для авто-StartGame (дефолт 2)
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

	startMin := opt.StartMin
	if startMin < 2 {
		startMin = 2
	}
	if c.Cfg.Think > 0 {
		fmt.Fprintf(out, "bot think fixed=%s\n", c.Cfg.Think)
	} else {
		fmt.Fprintf(out, "bot think random=[%s,%s]\n", c.Cfg.ThinkMin, c.Cfg.ThinkMax)
	}

	for {
		delay := nextThinkDelay(c.Cfg)
		timer := time.NewTimer(delay)
		select {
		case <-sessionCtx.Done():
			timer.Stop()
			return sessionCtx.Err()
		case <-ctx.Done():
			timer.Stop()
			_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Leave{Leave: &pb.Leave{}}})
			return ctx.Err()
		case <-timer.C:
			if st := hub.GameState(); st != nil && st.Phase == pb.GamePhase_FINISHED {
				fmt.Fprintln(out, "bot match finished, exit")
				return nil
			}
			botTick(hub, c, out, startMin)
		}
	}
}

func nextThinkDelay(cfg Config) time.Duration {
	if cfg.Think > 0 {
		return cfg.Think
	}
	min := cfg.ThinkMin
	max := cfg.ThinkMax
	if min <= 0 {
		min = time.Second
	}
	if max < min {
		max = min
	}
	if max == min {
		return min
	}
	span := max - min
	return min + time.Duration(rand.Int63n(int64(span)+1))
}

func botTick(hub *SessionHub, c *Client, out io.Writer, startMin int) {
	st := hub.GameState()
	if st == nil {
		hub.mu.RLock()
		room := hub.room
		hub.mu.RUnlock()
		if room != nil && !room.Started && c.playerID != "" && room.HostId == c.playerID && len(room.Players) >= startMin {
			_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_StartGame{StartGame: &pb.StartGame{}}})
			fmt.Fprintf(out, "bot StartGame (players=%d min=%d)\n", len(room.Players), startMin)
		}
		return
	}
	if st.Phase == pb.GamePhase_FINISHED {
		fmt.Fprintln(out, "bot FINISHED")
		return
	}
	if st.CanReady {
		_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Ready{Ready: &pb.Ready{}}})
		fmt.Fprintln(out, "bot Ready")
		return
	}
	msg := chooseLegalAction(st)
	if msg == nil {
		return
	}
	_ = hub.Send(msg)
	switch msg.Payload.(type) {
	case *pb.ClientMessage_Bito:
		fmt.Fprintln(out, "bot Bito")
	case *pb.ClientMessage_Pass:
		fmt.Fprintln(out, "bot Pass")
	case *pb.ClientMessage_AddCard:
		fmt.Fprintln(out, "bot AddCard")
	case *pb.ClientMessage_PlayCard:
		fmt.Fprintln(out, "bot PlayCard")
	}
}
