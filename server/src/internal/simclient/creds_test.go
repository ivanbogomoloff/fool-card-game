package simclient

import (
	"os"
	"path/filepath"
	"testing"
)

func TestPersist_MemoryOnlyWithoutCredsPath(t *testing.T) {
	cfg := Config{Name: "Alice"}
	at, err := PersistLoginResult(&cfg, "acc-1", "Alice", "generated-password-123456")
	if err != nil {
		t.Fatal(err)
	}
	if at != "memory" {
		t.Fatalf("storedAt=%q want memory", at)
	}
	if ResolvePassword(cfg) != "generated-password-123456" {
		t.Fatalf("memory password not resolved")
	}
	// Без пути на диск файл не создаём в cwd.
	if _, err := os.Stat("foolcard-credentials.json"); err == nil {
		t.Fatal("не должны писать foolcard-credentials.json без --creds")
	}
}

func TestPersist_DiskWhenCredsPathSet(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "credentials.json")
	cfg := Config{Name: "Alice", CredsPath: path}

	at, err := PersistLoginResult(&cfg, "acc-1", "Alice", "generated-password-123456")
	if err != nil {
		t.Fatal(err)
	}
	if at != path {
		t.Fatalf("storedAt=%q want %q", at, path)
	}

	cfg2 := Config{Name: "Alice", CredsPath: path}
	ApplyStoredCredentials(&cfg2, true)
	if ResolvePassword(cfg2) != "generated-password-123456" {
		t.Fatalf("disk password not loaded")
	}
}

func TestApplyStoredCredentials_ImplicitNameFromDisk(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "credentials.json")
	cfg := Config{Name: "Bob", CredsPath: path}
	_, _ = PersistLoginResult(&cfg, "acc-2", "Bob", "bob-password-abcdefgh")

	cfg2 := Config{Name: "player", CredsPath: path}
	ApplyStoredCredentials(&cfg2, false)
	if cfg2.Name != "Bob" || cfg2.Password != "bob-password-abcdefgh" {
		t.Fatalf("got name=%q password=%q", cfg2.Name, cfg2.Password)
	}
}

func TestApplyStoredCredentials_NoDiskSkipped(t *testing.T) {
	cfg := Config{Name: "player", Password: ""}
	ApplyStoredCredentials(&cfg, false)
	if cfg.Password != "" {
		t.Fatal("memory-only: нечего подставлять с диска")
	}
}
