package simclient_test

import (
	"bytes"
	"context"
	"fmt"
	"strings"
	"testing"
	"time"

	"foolcardgame/server/internal/simclient"
)

// Имитация двух пунктов меню login подряд (как human.go).
func TestHumanLoginTwice_Scripted(t *testing.T) {
	name := fmt.Sprintf("Menu%d", time.Now().UnixNano()%1_000_000_000)
	cfg := simclient.Config{Addr: "127.0.0.1:8080", Name: name}
	c, err := simclient.Dial(cfg)
	if err != nil {
		t.Skipf("api: %v", err)
	}
	defer c.Close()

	in := strings.NewReader("1\n1\n0\n")
	var out bytes.Buffer
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	err = simclient.RunHuman(ctx, c, in, &out)
	text := out.String()
	t.Log(text)
	if err != nil && err != context.Canceled {
		// quit may leave stream errors — смотрим вывод
	}
	if !strings.Contains(text, "credentials → memory") && !strings.Contains(text, "password=") {
		t.Fatalf("нет сохранения/пароля в выводе:\n%s", text)
	}
	if strings.Contains(text, "FailedPrecondition") || strings.Contains(text, "имя занято") {
		t.Fatalf("второй login упал без пароля:\n%s", text)
	}
	// Два успешных ok login
	if strings.Count(text, "ok login") < 2 {
		t.Fatalf("ожидали 2× ok login:\n%s", text)
	}
}
