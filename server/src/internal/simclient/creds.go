package simclient

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

// Credentials — сохранённые username + password (+ account_id) для повторного Login.
type Credentials struct {
	Username  string `json:"username"`
	AccountID string `json:"account_id"`
	Password  string `json:"password"`
}

var credsMu sync.Mutex

func defaultCredsPath() string {
	if dir, err := os.UserConfigDir(); err == nil && dir != "" {
		return filepath.Join(dir, "foolcard-simclient", "credentials.json")
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".foolcard-simclient-credentials.json")
}

// LoadCredentials читает файл; path пустой → default.
func LoadCredentials(path string) (Credentials, error) {
	if path == "" {
		path = defaultCredsPath()
	}
	credsMu.Lock()
	defer credsMu.Unlock()
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return Credentials{}, nil
		}
		return Credentials{}, err
	}
	var c Credentials
	if err := json.Unmarshal(data, &c); err != nil {
		return Credentials{}, err
	}
	return c, nil
}

// SaveCredentials атомарно пишет credentials.
func SaveCredentials(path string, c Credentials) error {
	if path == "" {
		path = defaultCredsPath()
	}
	credsMu.Lock()
	defer credsMu.Unlock()
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

// ResolvePassword: флаг --password, иначе из файла если username совпадает.
func ResolvePassword(cfg Config) string {
	if cfg.Password != "" {
		return cfg.Password
	}
	c, err := LoadCredentials(cfg.CredsPath)
	if err != nil || c.Username == "" {
		return ""
	}
	if c.Username == cfg.Name {
		return c.Password
	}
	return ""
}

// PersistLoginResult сохраняет credentials после успешного Login.
func PersistLoginResult(cfg *Config, accountID, username, plainPassword string) error {
	if plainPassword == "" {
		// Повторный вход: обновить token-side метаданные, пароль оставить из файла/флага.
		existing, _ := LoadCredentials(cfg.CredsPath)
		if existing.Password != "" && existing.Username == username {
			plainPassword = existing.Password
		} else if cfg.Password != "" {
			plainPassword = cfg.Password
		}
	}
	if plainPassword == "" {
		return fmt.Errorf("нет пароля для сохранения")
	}
	c := Credentials{
		Username:  username,
		AccountID: accountID,
		Password:  plainPassword,
	}
	cfg.Password = plainPassword
	cfg.AccountID = accountID
	cfg.Name = username
	return SaveCredentials(cfg.CredsPath, c)
}
