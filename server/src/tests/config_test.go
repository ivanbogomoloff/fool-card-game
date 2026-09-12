package tests

import (
	"os"
	"testing"
	"time"

	"foolcardgame/server/internal/config"
)

func setEnv(t *testing.T, kv map[string]string) {
	t.Helper()
	for k, v := range kv {
		t.Setenv(k, v)
	}
}

func clearDBEnv(t *testing.T) {
	t.Helper()
	for _, k := range []string{"HOST", "DB_HOST", "DB_USER", "DB_PASSWORD", "DB_NAME", "TLS_ENABLED", "FULL_LOGGING", "QUICK_FILL_WINDOW", "QUICK_QUEUE_TIMEOUT", "QUICK_MIN_PLAYERS", "QUICK_MAX_PLAYERS"} {
		_ = os.Unsetenv(k)
	}
}

func TestLoad_DefaultsFullLoggingAndFillWindow(t *testing.T) {
	clearDBEnv(t)
	setEnv(t, map[string]string{
		"TLS_ENABLED":  "false",
		"DB_HOST":      "mariadb",
		"DB_USER":      "fool",
		"DB_PASSWORD":  "secret",
		"DB_NAME":      "foolcard",
	})

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if !cfg.FullLogging {
		t.Fatalf("FULL_LOGGING default: want true")
	}
	if cfg.QuickFillWindow != 5*time.Second {
		t.Fatalf("QUICK_FILL_WINDOW default: want 5s, got %v", cfg.QuickFillWindow)
	}
	if cfg.QuickQueueTimeout != 120*time.Second {
		t.Fatalf("QUICK_QUEUE_TIMEOUT default: want 120s, got %v", cfg.QuickQueueTimeout)
	}
	if cfg.HTTPAddr != ":8080" {
		t.Fatalf("HTTP_ADDR default: want :8080, got %q", cfg.HTTPAddr)
	}
}

func TestLoad_ParseQuickFillWindow(t *testing.T) {
	clearDBEnv(t)
	setEnv(t, map[string]string{
		"TLS_ENABLED":        "false",
		"DB_HOST":            "localhost",
		"DB_USER":            "u",
		"DB_PASSWORD":        "p",
		"DB_NAME":            "foolcard",
		"QUICK_FILL_WINDOW":  "7s",
		"QUICK_QUEUE_TIMEOUT": "90s",
	})

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if cfg.QuickFillWindow != 7*time.Second {
		t.Fatalf("got fill %v", cfg.QuickFillWindow)
	}
	if cfg.QuickQueueTimeout != 90*time.Second {
		t.Fatalf("got timeout %v", cfg.QuickQueueTimeout)
	}
}

func TestLoad_InvalidFillWindow(t *testing.T) {
	clearDBEnv(t)
	setEnv(t, map[string]string{
		"TLS_ENABLED":       "false",
		"DB_HOST":           "localhost",
		"DB_USER":           "u",
		"DB_PASSWORD":       "p",
		"QUICK_FILL_WINDOW": "not-a-duration",
	})

	_, err := config.Load()
	if err == nil {
		t.Fatal("expected error for invalid QUICK_FILL_WINDOW")
	}
}

func TestLoad_MissingRequiredWhenTLS(t *testing.T) {
	clearDBEnv(t)
	setEnv(t, map[string]string{
		"TLS_ENABLED": "true",
		"DB_HOST":     "mariadb",
		"DB_USER":     "fool",
		"DB_PASSWORD": "secret",
		"DB_NAME":     "foolcard",
		// HOST отсутствует
	})

	_, err := config.Load()
	if err == nil {
		t.Fatal("expected error when HOST missing and TLS enabled")
	}
}

func TestLoad_MissingDB(t *testing.T) {
	clearDBEnv(t)
	setEnv(t, map[string]string{
		"TLS_ENABLED": "false",
		"HOST":        "example.com",
	})

	_, err := config.Load()
	if err == nil {
		t.Fatal("expected error when DB_* missing")
	}
}

func TestLoad_GamesActiveSecret(t *testing.T) {
	clearDBEnv(t)
	setEnv(t, map[string]string{
		"TLS_ENABLED":         "false",
		"DB_HOST":             "mariadb",
		"DB_USER":             "fool",
		"DB_PASSWORD":         "secret",
		"DB_NAME":             "foolcard",
		"GAMES_ACTIVE_SECRET": "ops-secret",
	})
	cfg, err := config.Load()
	if err != nil {
		t.Fatal(err)
	}
	if cfg.GamesActiveSecret != "ops-secret" {
		t.Fatalf("secret=%q", cfg.GamesActiveSecret)
	}
}

