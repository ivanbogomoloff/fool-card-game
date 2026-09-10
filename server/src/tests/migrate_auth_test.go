package tests

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"testing"
	"time"

	"foolcardgame/server/internal/migrate"
	"foolcardgame/server/internal/store"

	_ "github.com/go-sql-driver/mysql"
)

func testDSN() string {
	host := envOr("DB_HOST", "127.0.0.1")
	// В compose сервисе хост — mariadb; с хоста для тестов — localhost.
	if host == "mariadb" {
		host = "127.0.0.1"
	}
	port := envOr("DB_PORT", "3306")
	user := envOr("DB_USER", "fool")
	pass := envOr("DB_PASSWORD", "foolsecret")
	name := envOr("DB_NAME", "foolcard")
	return fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true&charset=utf8mb4&multiStatements=true",
		user, pass, host, port, name)
}

func envOr(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func openTestDB(t *testing.T) *sql.DB {
	t.Helper()
	db, err := sql.Open("mysql", testDSN())
	if err != nil {
		t.Fatalf("sql.Open: %v", err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		t.Skipf("MariaDB недоступна (%v); поднимите docker compose", err)
	}
	t.Cleanup(func() { _ = db.Close() })
	return db
}

func resetSchema(t *testing.T, db *sql.DB) {
	t.Helper()
	stmts := []string{
		`SET FOREIGN_KEY_CHECKS=0`,
		`DROP TABLE IF EXISTS game_players`,
		`DROP TABLE IF EXISTS games`,
		`DROP TABLE IF EXISTS auth_tokens`,
		`DROP TABLE IF EXISTS accounts`,
		`DROP TABLE IF EXISTS schema_migrations`,
		`SET FOREIGN_KEY_CHECKS=1`,
	}
	for _, s := range stmts {
		if _, err := db.Exec(s); err != nil {
			t.Fatalf("%s: %v", s, err)
		}
	}
}

func TestMigrate_UpOnEmptyDB(t *testing.T) {
	db := openTestDB(t)
	resetSchema(t, db)

	if err := migrate.Up(db); err != nil {
		t.Fatalf("migrate.Up: %v", err)
	}
	if err := migrate.Up(db); err != nil {
		t.Fatalf("migrate.Up second: %v", err)
	}

	for _, table := range []string{"accounts", "auth_tokens", "games", "game_players"} {
		var n int
		err := db.QueryRow(
			`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?`,
			table,
		).Scan(&n)
		if err != nil || n != 1 {
			t.Fatalf("таблица %s отсутствует: err=%v n=%d", table, err, n)
		}
	}
}

func TestAuth_LoginAndTokenLookup(t *testing.T) {
	db := openTestDB(t)
	resetSchema(t, db)
	if err := migrate.Up(db); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	acc := &store.Accounts{DB: db}
	ctx := context.Background()

	token1, a1, err := acc.Login(ctx, "Алиса")
	if err != nil {
		t.Fatalf("Login1: %v", err)
	}
	if token1 == "" || a1.ID == "" || a1.DisplayName != "Алиса" || a1.AvatarID != 0 {
		t.Fatalf("unexpected account/token: %+v %q", a1, token1)
	}

	id, err := acc.AccountIDByToken(ctx, token1)
	if err != nil || id != a1.ID {
		t.Fatalf("lookup: id=%q err=%v want %q", id, err, a1.ID)
	}

	token2, a2, err := acc.Login(ctx, "Алиса")
	if err != nil {
		t.Fatalf("Login2: %v", err)
	}
	if token2 == token1 {
		t.Fatal("повторный Login должен выдать новый token")
	}
	if a2.ID == a1.ID {
		t.Fatal("повторный Login создаёт новый аккаунт (MVP без пароля)")
	}

	if _, err := acc.AccountIDByToken(ctx, "not-a-real-token"); err == nil {
		t.Fatal("ожидался ErrInvalidToken")
	}
}
