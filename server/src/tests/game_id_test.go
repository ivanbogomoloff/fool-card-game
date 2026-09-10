package tests

import (
	"sync"
	"testing"

	"foolcardgame/server/internal/ids"
)

func TestGameID_ParallelUnique(t *testing.T) {
	const n = 500
	idsCh := make(chan string, n)
	var wg sync.WaitGroup
	wg.Add(n)
	for i := 0; i < n; i++ {
		go func() {
			defer wg.Done()
			idsCh <- ids.NewGameID()
		}()
	}
	wg.Wait()
	close(idsCh)

	seen := make(map[string]struct{}, n)
	for id := range idsCh {
		if id == "" {
			t.Fatal("пустой game id")
		}
		if _, ok := seen[id]; ok {
			t.Fatalf("дубликат game id: %s", id)
		}
		seen[id] = struct{}{}
	}
	if len(seen) != n {
		t.Fatalf("want %d unique, got %d", n, len(seen))
	}
}

func TestAccessCode_AlphabetAndLength(t *testing.T) {
	code, err := ids.NewAccessCode()
	if err != nil {
		t.Fatal(err)
	}
	if len(code) != 6 {
		t.Fatalf("len=%d want 6", len(code))
	}
	for _, c := range code {
		if !containsRune("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", c) {
			t.Fatalf("недопустимый символ %q в %q", c, code)
		}
	}
}

func containsRune(s string, r rune) bool {
	for _, c := range s {
		if c == r {
			return true
		}
	}
	return false
}
