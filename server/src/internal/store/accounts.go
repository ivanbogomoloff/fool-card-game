package store

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
)

// Ошибки lookup токена.
var (
	ErrInvalidToken = errors.New("недействительный токен")
)

// Accounts — аккаунты и opaque Bearer-токены.
type Accounts struct {
	DB *sql.DB
	// TokenTTL — срок жизни токена; 0 = без expires_at.
	TokenTTL time.Duration
}

// Account — строка accounts.
type Account struct {
	ID          string
	DisplayName string
	AvatarID    int32
}

// Login создаёт новый аккаунт и opaque token; в БД хранится только sha256(token).
func (a *Accounts) Login(ctx context.Context, displayName string) (token string, acc Account, err error) {
	if displayName == "" {
		displayName = "Игрок"
	}
	acc = Account{
		ID:          uuid.NewString(),
		DisplayName: displayName,
		AvatarID:    0,
	}
	token, err = newOpaqueToken()
	if err != nil {
		return "", Account{}, err
	}
	hash := HashToken(token)

	var expires any
	if a.TokenTTL > 0 {
		expires = time.Now().UTC().Add(a.TokenTTL)
	}

	tx, err := a.DB.BeginTx(ctx, nil)
	if err != nil {
		return "", Account{}, fmt.Errorf("begin: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if _, err := tx.ExecContext(ctx,
		`INSERT INTO accounts (id, display_name, avatar_id) VALUES (?, ?, ?)`,
		acc.ID, acc.DisplayName, acc.AvatarID,
	); err != nil {
		return "", Account{}, fmt.Errorf("insert account: %w", err)
	}
	if _, err := tx.ExecContext(ctx,
		`INSERT INTO auth_tokens (token_hash, account_id, expires_at) VALUES (?, ?, ?)`,
		hash, acc.ID, expires,
	); err != nil {
		return "", Account{}, fmt.Errorf("insert token: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return "", Account{}, fmt.Errorf("commit: %w", err)
	}
	return token, acc, nil
}

// AccountIDByToken проверяет opaque token и возвращает account_id.
func (a *Accounts) AccountIDByToken(ctx context.Context, token string) (string, error) {
	if token == "" {
		return "", ErrInvalidToken
	}
	hash := HashToken(token)
	var (
		accountID string
		expiresAt sql.NullTime
	)
	err := a.DB.QueryRowContext(ctx,
		`SELECT account_id, expires_at FROM auth_tokens WHERE token_hash = ?`,
		hash,
	).Scan(&accountID, &expiresAt)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrInvalidToken
	}
	if err != nil {
		return "", fmt.Errorf("lookup token: %w", err)
	}
	if expiresAt.Valid && time.Now().UTC().After(expiresAt.Time) {
		return "", ErrInvalidToken
	}
	return accountID, nil
}

// HashToken — sha256 hex (PK auth_tokens.token_hash).
func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

func newOpaqueToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", fmt.Errorf("token: %w", err)
	}
	return hex.EncodeToString(b), nil
}

// FinishedGame — данные для INSERT после FINISHED (вызывается на этапе 5).
type FinishedGame struct {
	ID             string
	StartedAt      time.Time
	FinishedAt     time.Time
	AccessCode     sql.NullString
	PlayersAtStart int
	Result         string // DRAW | HAS_FOOL
	FoolAccountID  sql.NullString
	Players        []GamePlayerRow
}

// GamePlayerRow — строка game_players.
type GamePlayerRow struct {
	AccountID    string
	PlayerResult string // WIN | FOOL | DRAW | LEFT
}

// Games — репозиторий статистики; flush только после FINISHED.
type Games struct {
	DB *sql.DB
}

// InsertFinished пишет games + game_players в одной транзакции.
func (g *Games) InsertFinished(ctx context.Context, m FinishedGame) error {
	tx, err := g.DB.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if _, err := tx.ExecContext(ctx, `
		INSERT INTO games (id, started_at, finished_at, access_code, players_at_start, result, fool_account_id)
		VALUES (?, ?, ?, ?, ?, ?, ?)`,
		m.ID, m.StartedAt.UTC(), m.FinishedAt.UTC(), m.AccessCode, m.PlayersAtStart, m.Result, m.FoolAccountID,
	); err != nil {
		return fmt.Errorf("insert game: %w", err)
	}
	for _, p := range m.Players {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO game_players (game_id, account_id, player_result) VALUES (?, ?, ?)`,
			m.ID, p.AccountID, p.PlayerResult,
		); err != nil {
			return fmt.Errorf("insert game_player: %w", err)
		}
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit: %w", err)
	}
	return nil
}
