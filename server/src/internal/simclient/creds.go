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

// DiskCredsPath — путь к файлу, если включена запись на диск.
// Диск: явный --creds или env FOOLCARD_CREDS; иначе только память процесса.
func DiskCredsPath(cfg Config) (path string, onDisk bool) {
	if cfg.CredsPath != "" {
		return cfg.CredsPath, true
	}
	if p := os.Getenv("FOOLCARD_CREDS"); p != "" {
		return p, true
	}
	return "", false
}

// CredsPathLabel для логов UI.
func CredsPathLabel(cfg Config) string {
	if p, ok := DiskCredsPath(cfg); ok {
		return p
	}
	return "memory"
}

// LoadCredentials читает файл по path; пустой path → пустые credentials.
func LoadCredentials(path string) (Credentials, error) {
	if path == "" {
		return Credentials{}, nil
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

// SaveCredentials атомарно пишет credentials на path.
func SaveCredentials(path string, c Credentials) error {
	if path == "" {
		return fmt.Errorf("пустой path")
	}
	credsMu.Lock()
	defer credsMu.Unlock()
	dir := filepath.Dir(path)
	if dir != "." && dir != "" {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return err
		}
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

// ApplyStoredCredentials подставляет password из файла (если диск включён).
// nameExplicit — передан ли --name/--username в CLI.
func ApplyStoredCredentials(cfg *Config, nameExplicit bool) {
	path, onDisk := DiskCredsPath(*cfg)
	if !onDisk {
		return
	}
	c, err := LoadCredentials(path)
	if err != nil || c.Password == "" || c.Username == "" {
		return
	}
	if !nameExplicit {
		cfg.Name = c.Username
		cfg.Password = c.Password
		cfg.AccountID = c.AccountID
		return
	}
	if cfg.Name == c.Username && cfg.Password == "" {
		cfg.Password = c.Password
		cfg.AccountID = c.AccountID
	}
}

// ResolvePassword: сначала память cfg.Password, иначе файл (если есть).
func ResolvePassword(cfg Config) string {
	if cfg.Password != "" {
		return cfg.Password
	}
	path, onDisk := DiskCredsPath(cfg)
	if !onDisk {
		return ""
	}
	c, err := LoadCredentials(path)
	if err != nil || c.Password == "" {
		return ""
	}
	if c.Username == cfg.Name {
		return c.Password
	}
	return ""
}

// PersistLoginResult всегда кладёт пароль в память; на диск — только если задан --creds / FOOLCARD_CREDS.
// Возвращает куда сохранили: "memory" или путь файла.
func PersistLoginResult(cfg *Config, accountID, username, plainPassword string) (storedAt string, err error) {
	if plainPassword == "" {
		if cfg.Password != "" {
			plainPassword = cfg.Password
		} else if path, ok := DiskCredsPath(*cfg); ok {
			existing, _ := LoadCredentials(path)
			if existing.Password != "" && existing.Username == username {
				plainPassword = existing.Password
			}
		}
	}
	if plainPassword == "" {
		return "", fmt.Errorf("нет пароля для сохранения")
	}

	cfg.Password = plainPassword
	cfg.AccountID = accountID
	if username != "" {
		cfg.Name = username
	}

	path, onDisk := DiskCredsPath(*cfg)
	if !onDisk {
		return "memory", nil
	}
	c := Credentials{
		Username:  cfg.Name,
		AccountID: accountID,
		Password:  plainPassword,
	}
	if err := SaveCredentials(path, c); err != nil {
		// Пароль уже в памяти — повторный login в этой сессии сработает.
		return "memory", fmt.Errorf("диск недоступен (%s): %w; пароль оставлен в памяти", path, err)
	}
	return path, nil
}
