package main

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"foolcardgame/server/internal/config"

	_ "github.com/go-sql-driver/mysql"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	if err := os.MkdirAll(cfg.LogDir, 0o755); err != nil {
		log.Fatalf("log dir: %v", err)
	}

	db, err := openDB(cfg)
	if err != nil {
		log.Fatalf("db: %v", err)
	}
	defer db.Close()

	addr := cfg.HTTPAddr
	if cfg.TLSEnabled {
		// Полный TLS/autocert — этап 2; на этапе 1 слушаем HTTP_ADDR и проверяем DB.
		log.Printf("TLS_ENABLED=true: ACME/gRPC на :80/:443 будут на этапе 2; сейчас health на %s", addr)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		if err := db.PingContext(ctx); err != nil {
			http.Error(w, "db unavailable", http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	ln, err := net.Listen("tcp", addr)
	if err != nil {
		log.Fatalf("listen %s: %v", addr, err)
	}
	srv := &http.Server{Handler: mux}

	go func() {
		log.Printf("listening on %s (FULL_LOGGING=%v, LOG_DIR=%s)", addr, cfg.FullLogging, cfg.LogDir)
		if err := srv.Serve(ln); err != nil && err != http.ErrServerClosed {
			log.Fatalf("serve: %v", err)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = srv.Shutdown(ctx)
}

func openDB(cfg config.Config) (*sql.DB, error) {
	db, err := sql.Open("mysql", cfg.DSN())
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(5)
	db.SetConnMaxLifetime(5 * time.Minute)

	deadline := time.Now().Add(60 * time.Second)
	for {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		err := db.PingContext(ctx)
		cancel()
		if err == nil {
			log.Printf("mariadb ok (%s:%s/%s)", cfg.DBHost, cfg.DBPort, cfg.DBName)
			return db, nil
		}
		if time.Now().After(deadline) {
			_ = db.Close()
			return nil, fmt.Errorf("ping: %w", err)
		}
		log.Printf("ожидание mariadb: %v", err)
		time.Sleep(2 * time.Second)
	}
}
