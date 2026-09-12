package simclient

import (
	"context"
	"fmt"
	"io"
	"sync"

	"foolcardgame/server/internal/pb"
)

// SessionHub держит bidi stream и последний GameState.
type SessionHub struct {
	mu     sync.RWMutex
	stream pb.GameSession_SessionClient
	state  *pb.GameState
	room   *pb.RoomState
	queue  *pb.QueueState
	out    io.Writer
}

func (h *SessionHub) SetStream(s pb.GameSession_SessionClient) {
	h.mu.Lock()
	h.stream = s
	h.mu.Unlock()
}

func (h *SessionHub) Send(msg *pb.ClientMessage) error {
	h.mu.RLock()
	s := h.stream
	h.mu.RUnlock()
	if s == nil {
		return fmt.Errorf("session не открыт")
	}
	return s.Send(msg)
}

func (h *SessionHub) GameState() *pb.GameState {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return h.state
}

func (h *SessionHub) apply(msg *pb.ServerMessage) {
	h.mu.Lock()
	defer h.mu.Unlock()
	switch p := msg.Payload.(type) {
	case *pb.ServerMessage_GameState:
		h.state = p.GameState
	case *pb.ServerMessage_RoomState:
		h.room = p.RoomState
	case *pb.ServerMessage_QueueState:
		h.queue = p.QueueState
	}
}

func (h *SessionHub) readLoop(cancel context.CancelFunc) {
	for {
		h.mu.RLock()
		s := h.stream
		h.mu.RUnlock()
		if s == nil {
			return
		}
		msg, err := s.Recv()
		if err != nil {
			fmt.Fprintf(h.out, "<< stream closed: %v\n", err)
			cancel()
			return
		}
		h.apply(msg)
		PrintServerMessage(h.out, msg)
	}
}

// RunHuman нумерованное меню.
func RunHuman(ctx context.Context, c *Client, in io.Reader, out io.Writer) error {
	hub := &SessionHub{out: out}
	sessionCtx, sessionCancel := context.WithCancel(ctx)
	defer sessionCancel()
	lines := NewLineReader(in)

	ensureSession := func() error {
		hub.mu.RLock()
		ok := hub.stream != nil
		hub.mu.RUnlock()
		if ok {
			return nil
		}
		if c.Cfg.Token == "" {
			return fmt.Errorf("сначала login")
		}
		stream, err := c.Session.Session(c.AuthedContext(sessionCtx))
		if err != nil {
			return err
		}
		hub.SetStream(stream)
		go hub.readLoop(sessionCancel)
		return nil
	}

	for {
		title := fmt.Sprintf("\n%s @ %s (token=%v)", c.Cfg.Name, c.Cfg.Addr, c.Cfg.Token != "")
		items := []MenuItem{
			{ID: 1, Label: "login", Key: "login"},
			{ID: 2, Label: "create (друзья)", Key: "create"},
			{ID: 3, Label: "join по коду", Key: "join"},
			{ID: 4, Label: "quick match", Key: "quick"},
			{ID: 5, Label: "ping", Key: "ping"},
			{ID: 6, Label: "subscribe (после create/join)", Key: "subscribe"},
			{ID: 7, Label: "start game (host)", Key: "start"},
			{ID: 8, Label: "сыграть карту", Key: "play"},
			{ID: 9, Label: "pass", Key: "pass"},
			{ID: 10, Label: "bito", Key: "bito"},
			{ID: 11, Label: "ready", Key: "ready"},
			{ID: 12, Label: "leave", Key: "leave"},
			{ID: 13, Label: "показать стол", Key: "table"},
			{ID: 0, Label: "выход", Key: "quit"},
		}
		key, err := PromptMenu(out, lines, title, items)
		if err != nil {
			if err == io.EOF {
				return nil
			}
			fmt.Fprintf(out, "! %v\n", err)
			continue
		}
		switch key {
		case "quit":
			_ = hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Leave{Leave: &pb.Leave{}}})
			return nil
		case "login":
			pwd := ResolvePassword(c.Cfg)
			req := &pb.LoginRequest{Username: &c.Cfg.Name}
			if pwd != "" {
				p := pwd
				req.Password = &p
				c.Cfg.Password = pwd
			}
			resp, err := c.Auth.Login(ctx, req)
			if err != nil {
				fmt.Fprintf(out, "! Login: %v\n", err)
				continue
			}
			c.Cfg.Token = resp.Token
			if resp.Username != "" {
				c.Cfg.Name = resp.Username
			}
			plain := resp.GetPassword()
			if plain == "" {
				plain = pwd
			}
			at, err := PersistLoginResult(&c.Cfg, resp.AccountId, resp.Username, plain)
			if err != nil {
				fmt.Fprintf(out, "! save credentials: %v\n", err)
			} else {
				fmt.Fprintf(out, "credentials → %s\n", at)
			}
			showPwd := c.Cfg.Password
			if showPwd == "" {
				showPwd = plain
			}
			if showPwd != "" {
				fmt.Fprintf(out, "ok login account_id=%s username=%s password=%s\n",
					resp.AccountId, resp.Username, showPwd)
			} else {
				fmt.Fprintf(out, "ok login account_id=%s username=%s (пароль неизвестен)\n",
					resp.AccountId, resp.Username)
			}
		case "create":
			if c.Cfg.Token == "" {
				fmt.Fprintln(out, "! сначала login")
				continue
			}
			resp, err := c.Match.CreateGame(c.AuthedContext(ctx), &pb.PlayerProfile{
				Username: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
			})
			if err != nil {
				fmt.Fprintf(out, "! CreateGame: %v\n", err)
				continue
			}
			fmt.Fprintf(out, "ok game=%s code=%s host=%s player=%s\n",
				resp.GameId, resp.AccessCode, resp.HostId, resp.PlayerId)
			c.storeIDs(resp.GameId, resp.PlayerId)
		case "join":
			if c.Cfg.Token == "" {
				fmt.Fprintln(out, "! сначала login")
				continue
			}
			code, err := PromptString(out, lines, "код комнаты: ")
			if err != nil {
				return err
			}
			resp, err := c.Match.JoinGame(c.AuthedContext(ctx), &pb.JoinGameRequest{
				Code: code, Username: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
			})
			if err != nil {
				fmt.Fprintf(out, "! JoinGame: %v\n", err)
				continue
			}
			fmt.Fprintf(out, "ok game=%s player=%s\n", resp.GameId, resp.PlayerId)
			c.storeIDs(resp.GameId, resp.PlayerId)
		case "quick":
			if err := ensureSession(); err != nil {
				fmt.Fprintf(out, "! %v\n", err)
				continue
			}
			err := hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_QuickMatch{QuickMatch: &pb.QuickMatch{
				Username: c.Cfg.Name, AvatarId: c.Cfg.Avatar,
			}}})
			if err != nil {
				fmt.Fprintf(out, "! QuickMatch: %v\n", err)
			}
		case "ping":
			if err := ensureSession(); err != nil {
				fmt.Fprintf(out, "! %v\n", err)
				continue
			}
			if err := hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Ping{Ping: &pb.Ping{}}}); err != nil {
				fmt.Fprintf(out, "! Ping: %v\n", err)
			}
		case "subscribe":
			if err := ensureSession(); err != nil {
				fmt.Fprintf(out, "! %v\n", err)
				continue
			}
			gid, pid := c.gameID, c.playerID
			if gid == "" {
				gid, _ = PromptString(out, lines, "game_id: ")
				pid, _ = PromptString(out, lines, "player_id: ")
				c.storeIDs(gid, pid)
			}
			err := hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_Subscribe{Subscribe: &pb.Subscribe{
				GameId: gid, PlayerId: pid,
			}}})
			if err != nil {
				fmt.Fprintf(out, "! Subscribe: %v\n", err)
			}
		case "start":
			if err := ensureSession(); err != nil {
				fmt.Fprintf(out, "! %v\n", err)
				continue
			}
			if err := hub.Send(&pb.ClientMessage{Payload: &pb.ClientMessage_StartGame{StartGame: &pb.StartGame{}}}); err != nil {
				fmt.Fprintf(out, "! Start: %v\n", err)
			}
		case "play":
			if err := ensureSession(); err != nil {
				fmt.Fprintf(out, "! %v\n", err)
				continue
			}
			if err := promptPlay(out, lines, hub); err != nil {
				fmt.Fprintf(out, "! %v\n", err)
			}
		case "pass", "bito", "ready", "leave":
			if err := ensureSession(); err != nil {
				fmt.Fprintf(out, "! %v\n", err)
				continue
			}
			var msg *pb.ClientMessage
			switch key {
			case "pass":
				msg = &pb.ClientMessage{Payload: &pb.ClientMessage_Pass{Pass: &pb.Pass{}}}
			case "bito":
				msg = &pb.ClientMessage{Payload: &pb.ClientMessage_Bito{Bito: &pb.Bito{}}}
			case "ready":
				msg = &pb.ClientMessage{Payload: &pb.ClientMessage_Ready{Ready: &pb.Ready{}}}
			case "leave":
				msg = &pb.ClientMessage{Payload: &pb.ClientMessage_Leave{Leave: &pb.Leave{}}}
			}
			if err := hub.Send(msg); err != nil {
				fmt.Fprintf(out, "! %s: %v\n", key, err)
			}
		case "table":
			RenderTable(out, hub.GameState())
		}
	}
}

func promptPlay(out io.Writer, in *LineReader, hub *SessionHub) error {
	st := hub.GameState()
	RenderTable(out, st)
	fmt.Fprintln(out, "\nХод (рука>пара для отбивки, номер руки для атаки/подкида; p=pass, b=bito):")
	fmt.Fprint(out, "> ")
	line, err := in.Line()
	if err != nil {
		return err
	}
	var hand []*pb.Card
	var pairs []*pb.TablePair
	if st != nil {
		hand = st.LocalHand
		pairs = st.TablePairs
	}
	intent, err := ParsePlayInput(line, hand, pairs)
	if err != nil {
		return err
	}
	return hub.Send(intent.ToClientMessage())
}

func (c *Client) storeIDs(gameID, playerID string) {
	c.gameID = gameID
	c.playerID = playerID
}
