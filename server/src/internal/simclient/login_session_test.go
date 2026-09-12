package simclient_test

import (
	"context"
	"fmt"
	"testing"
	"time"

	"foolcardgame/server/internal/pb"
	"foolcardgame/server/internal/simclient"
)

func TestDoubleLogin_PassesPasswordInSameSession(t *testing.T) {
	name := fmt.Sprintf("Probe%d", time.Now().UnixNano()%1_000_000_000)
	cfg := simclient.Config{Addr: "127.0.0.1:8080", Name: name}
	c, err := simclient.Dial(cfg)
	if err != nil {
		t.Skipf("api недоступен: %v", err)
	}
	defer c.Close()
	ctx := context.Background()

	req1 := &pb.LoginRequest{Username: &c.Cfg.Name}
	resp1, err := c.Auth.Login(ctx, req1)
	if err != nil {
		t.Fatalf("login1: %v", err)
	}
	plain := resp1.GetPassword()
	if plain == "" {
		t.Fatalf("login1: сервер не вернул password (Password==nil=%v)", resp1.Password == nil)
	}
	at, err := simclient.PersistLoginResult(&c.Cfg, resp1.AccountId, resp1.Username, plain)
	if err != nil {
		t.Fatalf("persist: %v", err)
	}
	if at != "memory" {
		t.Fatalf("want memory, got %q", at)
	}
	if c.Cfg.Password != plain {
		t.Fatalf("cfg.Password not set")
	}

	pwd := simclient.ResolvePassword(c.Cfg)
	if pwd == "" {
		t.Fatal("ResolvePassword empty after persist")
	}
	req2 := &pb.LoginRequest{Username: &c.Cfg.Name, Password: &pwd}
	resp2, err := c.Auth.Login(ctx, req2)
	if err != nil {
		t.Fatalf("login2: %v", err)
	}
	if resp2.AccountId != resp1.AccountId {
		t.Fatalf("account changed")
	}
	if resp2.GetPassword() != "" {
		t.Fatal("relogin must not return password")
	}
}
