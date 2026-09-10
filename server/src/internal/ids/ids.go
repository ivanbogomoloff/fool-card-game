package ids

import (
	"crypto/rand"
	"fmt"

	"github.com/google/uuid"
)

// NewGameID возвращает глобально уникальный UUID строки для матча / PK games.id / лог-файла.
func NewGameID() string {
	return uuid.NewString()
}

const accessCodeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

// NewAccessCode — 6 символов для private-комнаты (не заменяет game id).
func NewAccessCode() (string, error) {
	const n = 6
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", fmt.Errorf("access code: %w", err)
	}
	out := make([]byte, n)
	for i := range b {
		out[i] = accessCodeAlphabet[int(b[i])%len(accessCodeAlphabet)]
	}
	return string(out), nil
}
