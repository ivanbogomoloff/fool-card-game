package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config — параметры процесса из переменных окружения.
type Config struct {
	Host       string
	ACMEEmail  string
	TLSEnabled bool
	HTTPAddr   string

	DBHost     string
	DBPort     string
	DBUser     string
	DBPassword string
	DBName     string

	FullLogging bool
	LogDir      string

	ACMECacheDir string

	QuickMinPlayers   int
	QuickMaxPlayers   int
	QuickFillWindow   time.Duration
	QuickQueueTimeout time.Duration
}

// Load читает окружение и проверяет обязательные поля.
func Load() (Config, error) {
	cfg := Config{
		Host:              strings.TrimSpace(os.Getenv("HOST")),
		ACMEEmail:         envOr("ACME_EMAIL", ""),
		TLSEnabled:        envBool("TLS_ENABLED", true),
		HTTPAddr:          envOr("HTTP_ADDR", ":8080"),
		DBHost:            strings.TrimSpace(os.Getenv("DB_HOST")),
		DBPort:            envOr("DB_PORT", "3306"),
		DBUser:            strings.TrimSpace(os.Getenv("DB_USER")),
		DBPassword:        os.Getenv("DB_PASSWORD"),
		DBName:            envOr("DB_NAME", "foolcard"),
		FullLogging:       envBool("FULL_LOGGING", true),
		LogDir:            envOr("LOG_DIR", "./logs"),
		ACMECacheDir:      envOr("ACME_CACHE_DIR", "./autocert-cache"),
		QuickMinPlayers:   envInt("QUICK_MIN_PLAYERS", 2),
		QuickMaxPlayers:   envInt("QUICK_MAX_PLAYERS", 4),
		QuickFillWindow:   0,
		QuickQueueTimeout: 0,
	}

	fillWindow, err := envDuration("QUICK_FILL_WINDOW", 5*time.Second)
	if err != nil {
		return Config{}, fmt.Errorf("QUICK_FILL_WINDOW: %w", err)
	}
	cfg.QuickFillWindow = fillWindow

	queueTimeout, err := envDuration("QUICK_QUEUE_TIMEOUT", 120*time.Second)
	if err != nil {
		return Config{}, fmt.Errorf("QUICK_QUEUE_TIMEOUT: %w", err)
	}
	cfg.QuickQueueTimeout = queueTimeout

	if err := cfg.validate(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func (c Config) validate() error {
	var missing []string
	if c.TLSEnabled && c.Host == "" {
		missing = append(missing, "HOST")
	}
	if c.DBHost == "" {
		missing = append(missing, "DB_HOST")
	}
	if c.DBUser == "" {
		missing = append(missing, "DB_USER")
	}
	if c.DBPassword == "" {
		missing = append(missing, "DB_PASSWORD")
	}
	if c.DBName == "" {
		missing = append(missing, "DB_NAME")
	}
	if len(missing) > 0 {
		return fmt.Errorf("обязательные переменные окружения не заданы: %s", strings.Join(missing, ", "))
	}
	if c.QuickMinPlayers < 2 {
		return fmt.Errorf("QUICK_MIN_PLAYERS должен быть >= 2")
	}
	if c.QuickMaxPlayers < c.QuickMinPlayers {
		return fmt.Errorf("QUICK_MAX_PLAYERS должен быть >= QUICK_MIN_PLAYERS")
	}
	return nil
}

// DSN возвращает строку подключения MySQL/MariaDB.
func (c Config) DSN() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true&charset=utf8mb4&multiStatements=true",
		c.DBUser, c.DBPassword, c.DBHost, c.DBPort, c.DBName)
}

func envOr(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && strings.TrimSpace(v) != "" {
		return v
	}
	return def
}

func envBool(key string, def bool) bool {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def
	}
	b, err := strconv.ParseBool(strings.TrimSpace(v))
	if err != nil {
		return def
	}
	return b
}

func envInt(key string, def int) int {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def
	}
	n, err := strconv.Atoi(strings.TrimSpace(v))
	if err != nil {
		return def
	}
	return n
}

func envDuration(key string, def time.Duration) (time.Duration, error) {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def, nil
	}
	d, err := time.ParseDuration(strings.TrimSpace(v))
	if err != nil {
		return 0, err
	}
	return d, nil
}
