package tlssetup

import (
	"crypto/tls"
	"fmt"
	"net"
	"net/http"

	"foolcardgame/server/internal/config"

	"golang.org/x/crypto/acme/autocert"
)

// Manager собирает autocert для prod (HOST + ACME).
func Manager(cfg config.Config) (*autocert.Manager, error) {
	if cfg.Host == "" {
		return nil, fmt.Errorf("HOST обязателен при TLS_ENABLED=true")
	}
	cacheDir := cfg.ACMECacheDir
	if cacheDir == "" {
		cacheDir = "./autocert-cache"
	}
	m := &autocert.Manager{
		Prompt:     autocert.AcceptTOS,
		HostPolicy: autocert.HostWhitelist(cfg.Host),
		Cache:      autocert.DirCache(cacheDir),
		Email:      cfg.ACMEEmail,
	}
	return m, nil
}

// TLSConfig для gRPC credentials (ALPN h2).
func TLSConfig(m *autocert.Manager) *tls.Config {
	cfg := m.TLSConfig()
	if cfg.NextProtos == nil {
		cfg.NextProtos = []string{"h2", "http/1.1"}
	}
	return cfg
}

// ListenACME поднимает :80 для HTTP-01 challenge и опционального fallback (health, /games/active).
func ListenACME(m *autocert.Manager, fallback http.Handler) (net.Listener, error) {
	ln, err := net.Listen("tcp", ":80")
	if err != nil {
		return nil, fmt.Errorf("listen :80: %w", err)
	}
	go func() {
		_ = http.Serve(ln, m.HTTPHandler(fallback))
	}()
	return ln, nil
}
