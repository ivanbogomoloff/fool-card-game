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
	"foolcardgame/server/internal/grpcserver"
	"foolcardgame/server/internal/logging"
	"foolcardgame/server/internal/migrate"
	"foolcardgame/server/internal/store"
	"foolcardgame/server/internal/tlssetup"

	_ "github.com/go-sql-driver/mysql"
	"github.com/soheilhy/cmux"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	if err := os.MkdirAll(cfg.LogDir, 0o755); err != nil {
		log.Fatalf("log dir: %v", err)
	}
	appLog, err := logging.New(cfg.LogDir, cfg.FullLogging)
	if err != nil {
		log.Fatalf("logging: %v", err)
	}
	defer appLog.Close()

	db, err := openDB(cfg)
	if err != nil {
		log.Fatalf("db: %v", err)
	}
	defer db.Close()

	if err := migrate.Up(db); err != nil {
		log.Fatalf("migrate: %v", err)
	}
	log.Printf("migrations ok")

	accounts := &store.Accounts{DB: db}

	healthMux := http.NewServeMux()
	healthMux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		if err := db.PingContext(ctx); err != nil {
			http.Error(w, "db unavailable", http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	var (
		grpcSrv *grpc.Server
		acmeLn  net.Listener
	)

	if cfg.TLSEnabled {
		mgr, err := tlssetup.Manager(cfg)
		if err != nil {
			log.Fatalf("autocert: %v", err)
		}
		if err := os.MkdirAll(cfg.ACMECacheDir, 0o700); err != nil {
			log.Fatalf("acme cache: %v", err)
		}
		acmeLn, err = tlssetup.ListenACME(mgr)
		if err != nil {
			log.Fatalf("acme :80: %v", err)
		}
		creds := credentials.NewTLS(tlssetup.TLSConfig(mgr))
		grpcSrv = grpcserver.New(grpcserver.Options{TLS: creds, Logger: appLog, Accounts: accounts})
		ln, err := net.Listen("tcp", ":443")
		if err != nil {
			log.Fatalf("listen :443: %v", err)
		}
		go serveGRPC(grpcSrv, ln, "gRPC+TLS :443")
		log.Printf("TLS: ACME :80, gRPC :443 host=%s cache=%s", cfg.Host, cfg.ACMECacheDir)
	} else {
		grpcSrv = grpcserver.New(grpcserver.Options{Logger: appLog, Accounts: accounts})
		ln, err := net.Listen("tcp", cfg.HTTPAddr)
		if err != nil {
			log.Fatalf("listen %s: %v", cfg.HTTPAddr, err)
		}
		m := cmux.New(ln)
		grpcL := m.MatchWithWriters(cmux.HTTP2MatchHeaderFieldSendSettings("content-type", "application/grpc"))
		httpL := m.Match(cmux.Any())
		go serveGRPC(grpcSrv, grpcL, "gRPC "+cfg.HTTPAddr)
		go func() {
			if err := http.Serve(httpL, healthMux); err != nil {
				log.Printf("http health: %v", err)
			}
		}()
		go func() {
			log.Printf("listening cmux on %s (grpc + /healthz), FULL_LOGGING=%v", cfg.HTTPAddr, cfg.FullLogging)
			if err := m.Serve(); err != nil {
				log.Fatalf("cmux: %v", err)
			}
		}()
	}

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)
	<-stop

	grpcSrv.GracefulStop()
	if acmeLn != nil {
		_ = acmeLn.Close()
	}
}

func serveGRPC(s *grpc.Server, ln net.Listener, label string) {
	log.Printf("serving %s", label)
	if err := s.Serve(ln); err != nil {
		log.Printf("%s stopped: %v", label, err)
	}
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
