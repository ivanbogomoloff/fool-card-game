package store

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"
	"unicode"

	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"
)

const (
	// MinPasswordLen — минимальная длина пароля аккаунта.
	MinPasswordLen = 18
	// GeneratedPasswordLen — длина пароля, выдаваемого при регистрации.
	GeneratedPasswordLen = 24
)

var (
	ErrInvalidToken           = errors.New("недействительный токен")
	ErrEmptyUsername          = errors.New("пустой username")
	ErrNameTakenNeedPassword  = errors.New("имя занято, укажите пароль")
	ErrInvalidPassword        = errors.New("неверный пароль")
)

// Accounts — аккаунты и opaque Bearer-токены.
type Accounts struct {
	DB *sql.DB
	// TokenTTL — срок жизни токена; 0 = без expires_at.
	TokenTTL time.Duration
}

// Account — строка accounts.
type Account struct {
	ID       string
	Username string
	AvatarID int32
}

// LoginResult — результат Login (password заполнен только при регистрации).
type LoginResult struct {
	Token          string
	Account        Account
	PlainPassword  string // только при первой регистрации
	IsRegistration bool
}

// Login: новый username → регистрация + plaintext password; занятый → проверка password.
func (a *Accounts) Login(ctx context.Context, username, password string) (LoginResult, error) {
	username = strings.TrimSpace(username)
	if username == "" {
		return LoginResult{}, ErrEmptyUsername
	}
	if containsSpace(username) {
		return LoginResult{}, fmt.Errorf("%w: username не должен содержать пробелы", ErrEmptyUsername)
	}

	var (
		id           string
		avatarID     int32
		passwordHash string
	)
	err := a.DB.QueryRowContext(ctx,
		`SELECT id, avatar_id, password_hash FROM accounts WHERE username = ?`,
		username,
	).Scan(&id, &avatarID, &passwordHash)

	if errors.Is(err, sql.ErrNoRows) {
		return a.register(ctx, username)
	}
	if err != nil {
		return LoginResult{}, fmt.Errorf("lookup username: %w", err)
	}

	if strings.TrimSpace(password) == "" {
		return LoginResult{}, ErrNameTakenNeedPassword
	}
	if err := bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(password)); err != nil {
		return LoginResult{}, ErrInvalidPassword
	}

	token, err := a.issueToken(ctx, id)
	if err != nil {
		return LoginResult{}, err
	}
	return LoginResult{
		Token: token,
		Account: Account{
			ID:       id,
			Username: username,
			AvatarID: avatarID,
		},
	}, nil
}

func (a *Accounts) register(ctx context.Context, username string) (LoginResult, error) {
	plain, err := generatePassword(GeneratedPasswordLen)
	if err != nil {
		return LoginResult{}, err
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(plain), bcrypt.DefaultCost)
	if err != nil {
		return LoginResult{}, fmt.Errorf("bcrypt: %w", err)
	}

	acc := Account{
		ID:       uuid.NewString(),
		Username: username,
		AvatarID: 0,
	}
	token, err := newOpaqueToken()
	if err != nil {
		return LoginResult{}, err
	}
	tokenHash := HashToken(token)

	var expires any
	if a.TokenTTL > 0 {
		expires = time.Now().UTC().Add(a.TokenTTL)
	}

	tx, err := a.DB.BeginTx(ctx, nil)
	if err != nil {
		return LoginResult{}, fmt.Errorf("begin: %w", err)
	}
	defer func() { _ = tx.Rollback() }()

	if _, err := tx.ExecContext(ctx,
		`INSERT INTO accounts (id, username, avatar_id, password_hash) VALUES (?, ?, ?, ?)`,
		acc.ID, acc.Username, acc.AvatarID, string(hash),
	); err != nil {
		// Гонка: имя заняли параллельно.
		if isDuplicateKey(err) {
			return LoginResult{}, ErrNameTakenNeedPassword
		}
		return LoginResult{}, fmt.Errorf("insert account: %w", err)
	}
	if _, err := tx.ExecContext(ctx,
		`INSERT INTO auth_tokens (token_hash, account_id, expires_at) VALUES (?, ?, ?)`,
		tokenHash, acc.ID, expires,
	); err != nil {
		return LoginResult{}, fmt.Errorf("insert token: %w", err)
	}
	if err := tx.Commit(); err != nil {
		return LoginResult{}, fmt.Errorf("commit: %w", err)
	}
	return LoginResult{
		Token:          token,
		Account:        acc,
		PlainPassword:  plain,
		IsRegistration: true,
	}, nil
}

func (a *Accounts) issueToken(ctx context.Context, accountID string) (string, error) {
	token, err := newOpaqueToken()
	if err != nil {
		return "", err
	}
	var expires any
	if a.TokenTTL > 0 {
		expires = time.Now().UTC().Add(a.TokenTTL)
	}
	if _, err := a.DB.ExecContext(ctx,
		`INSERT INTO auth_tokens (token_hash, account_id, expires_at) VALUES (?, ?, ?)`,
		HashToken(token), accountID, expires,
	); err != nil {
		return "", fmt.Errorf("insert token: %w", err)
	}
	return token, nil
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

const passwordAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

func generatePassword(n int) (string, error) {
	if n < MinPasswordLen {
		n = MinPasswordLen
	}
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", fmt.Errorf("password: %w", err)
	}
	out := make([]byte, n)
	for i := range b {
		out[i] = passwordAlphabet[int(b[i])%len(passwordAlphabet)]
	}
	return string(out), nil
}

func containsSpace(s string) bool {
	for _, r := range s {
		if unicode.IsSpace(r) {
			return true
		}
	}
	return false
}

func isDuplicateKey(err error) bool {
	if err == nil {
		return false
	}
	msg := err.Error()
	return strings.Contains(msg, "Duplicate") || strings.Contains(msg, "1062")
}

// FinishedGame — данные для INSERT после FINISHED (вызывается на этапе 5).
type FinishedGame struct {
	ID             string
	StartedAt      time.Time
	FinishedAt     time.Time
	AccessCode     sql.NullString
	PlayersAtStart int
	Result         string
	FoolAccountID  sql.NullString
	Players        []GamePlayerRow
}

// GamePlayerRow — строка game_players.
type GamePlayerRow struct {
	AccountID    string
	PlayerResult string
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
