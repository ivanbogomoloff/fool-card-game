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
	"golang.org/x/crypto/bcrypt"
)

func testDSN() string {
	host := envOr("DB_HOST", "127.0.0.1")
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

// ensureMigrated применяет Up; при dirty/ошибке — полный reset схемы и повтор.
func ensureMigrated(t *testing.T, db *sql.DB) {
	t.Helper()
	if err := migrate.Up(db); err != nil {
		resetSchema(t, db)
		if err := migrate.Up(db); err != nil {
			t.Fatalf("migrate.Up after reset: %v", err)
		}
	}
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

	var colCount int
	err := db.QueryRow(`
		SELECT COUNT(*) FROM information_schema.columns
		WHERE table_schema = DATABASE() AND table_name = 'accounts' AND column_name IN ('username','password_hash')`).Scan(&colCount)
	if err != nil || colCount != 2 {
		t.Fatalf("accounts.username/password_hash: err=%v count=%d", err, colCount)
	}
	var idx int
	err = db.QueryRow(`
		SELECT COUNT(*) FROM information_schema.statistics
		WHERE table_schema = DATABASE() AND table_name = 'accounts' AND index_name = 'uq_accounts_username'`).Scan(&idx)
	if err != nil || idx < 1 {
		t.Fatalf("uq_accounts_username: err=%v idx=%d", err, idx)
	}
}

func TestAuth_LoginRegisterAndPassword(t *testing.T) {
	db := openTestDB(t)
	resetSchema(t, db)
	ensureMigrated(t, db)

	acc := &store.Accounts{DB: db}
	ctx := context.Background()

	res1, err := acc.Login(ctx, "Алиса", "")
	if err != nil {
		t.Fatalf("register: %v", err)
	}
	if !res1.IsRegistration || res1.PlainPassword == "" || len(res1.PlainPassword) < store.MinPasswordLen {
		t.Fatalf("want registration password len>=%d, got %+v", store.MinPasswordLen, res1)
	}
	if res1.Account.Username != "Алиса" || res1.Token == "" || res1.Account.ID == "" {
		t.Fatalf("unexpected: %+v", res1)
	}

	var hash string
	if err := db.QueryRow(`SELECT password_hash FROM accounts WHERE id = ?`, res1.Account.ID).Scan(&hash); err != nil {
		t.Fatal(err)
	}
	if err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(res1.PlainPassword)); err != nil {
		t.Fatalf("bcrypt mismatch: %v", err)
	}

	if _, err := acc.Login(ctx, "Алиса", ""); err != store.ErrNameTakenNeedPassword {
		t.Fatalf("want ErrNameTakenNeedPassword, got %v", err)
	}

	if _, err := acc.Login(ctx, "Алиса", "wrong-password-!!!!!!"); err != store.ErrInvalidPassword {
		t.Fatalf("want ErrInvalidPassword, got %v", err)
	}

	res2, err := acc.Login(ctx, "Алиса", res1.PlainPassword)
	if err != nil {
		t.Fatalf("relogin: %v", err)
	}
	if res2.IsRegistration || res2.PlainPassword != "" {
		t.Fatalf("relogin must not return plaintext password: %+v", res2)
	}
	if res2.Account.ID != res1.Account.ID {
		t.Fatalf("account id changed")
	}
	if res2.Token == res1.Token {
		t.Fatal("ожидался новый token")
	}

	id, err := acc.AccountIDByToken(ctx, res2.Token)
	if err != nil || id != res1.Account.ID {
		t.Fatalf("lookup: id=%q err=%v", id, err)
	}

	if _, err := acc.Login(ctx, "", ""); err != store.ErrEmptyUsername {
		t.Fatalf("empty username: %v", err)
	}
}
