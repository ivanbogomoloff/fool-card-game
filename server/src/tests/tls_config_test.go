package tests

import (
	"os"
	"path/filepath"
	"testing"

	"foolcardgame/server/internal/config"
	"foolcardgame/server/internal/tlssetup"
)

func TestManager_RequiresHost(t *testing.T) {
	_, err := tlssetup.Manager(config.Config{Host: "", ACMEEmail: "a@b.c"})
	if err == nil {
		t.Fatal("expected error without HOST")
	}
}

func TestManager_OK(t *testing.T) {
	dir := t.TempDir()
	m, err := tlssetup.Manager(config.Config{
		Host:         "example.com",
		ACMEEmail:    "admin@example.com",
		ACMECacheDir: dir,
	})
	if err != nil {
		t.Fatalf("Manager: %v", err)
	}
	cfg := tlssetup.TLSConfig(m)
	if cfg == nil || cfg.GetCertificate == nil {
		t.Fatal("TLSConfig incomplete")
	}
	_ = filepath.Join(dir)
}

func TestConfig_TLSDisabled_HostOptional(t *testing.T) {
	t.Setenv("TLS_ENABLED", "false")
	t.Setenv("DB_HOST", "localhost")
	t.Setenv("DB_USER", "u")
	t.Setenv("DB_PASSWORD", "p")
	t.Setenv("DB_NAME", "foolcard")
	_ = os.Unsetenv("HOST")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load without HOST: %v", err)
	}
	if cfg.TLSEnabled {
		t.Fatal("want TLS disabled")
	}
}
