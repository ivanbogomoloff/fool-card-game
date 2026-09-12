package logging

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// Logger — requests/errors + per-game sinks и matchmaking.log.
type Logger struct {
	mu       sync.Mutex
	full     bool
	logDir   string
	requests *os.File // nil если !full
	errors   *os.File
	mm       *os.File            // matchmaking.log
	games     map[string]*os.File // gameID → file
}

// New открывает errors.log всегда; requests/matchmaking — только при full=true.
func New(logDir string, full bool) (*Logger, error) {
	if err := os.MkdirAll(logDir, 0o755); err != nil {
		return nil, fmt.Errorf("log dir: %w", err)
	}
	errFile, err := os.OpenFile(
		filepath.Join(logDir, "errors.log"),
		os.O_CREATE|os.O_WRONLY|os.O_APPEND,
		0o644,
	)
	if err != nil {
		return nil, fmt.Errorf("errors.log: %w", err)
	}
	l := &Logger{full: full, logDir: logDir, errors: errFile, games: make(map[string]*os.File)}
	if full {
		reqFile, err := os.OpenFile(
			filepath.Join(logDir, "requests.log"),
			os.O_CREATE|os.O_WRONLY|os.O_APPEND,
			0o644,
		)
		if err != nil {
			_ = errFile.Close()
			return nil, fmt.Errorf("requests.log: %w", err)
		}
		l.requests = reqFile
		mmFile, err := os.OpenFile(
			filepath.Join(logDir, "matchmaking.log"),
			os.O_CREATE|os.O_WRONLY|os.O_APPEND,
			0o644,
		)
		if err != nil {
			_ = reqFile.Close()
			_ = errFile.Close()
			return nil, fmt.Errorf("matchmaking.log: %w", err)
		}
		l.mm = mmFile
	}
	return l, nil
}

// Full сообщает, включён ли подробный лог.
func (l *Logger) Full() bool {
	if l == nil {
		return false
	}
	return l.full
}

// LogDir возвращает каталог логов.
func (l *Logger) LogDir() string {
	if l == nil {
		return ""
	}
	return l.logDir
}

// Close закрывает все файлы.
func (l *Logger) Close() error {
	if l == nil {
		return nil
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	var first error
	closeOne := func(f **os.File) {
		if *f == nil {
			return
		}
		if err := (*f).Close(); err != nil && first == nil {
			first = err
		}
		*f = nil
	}
	closeOne(&l.requests)
	closeOne(&l.mm)
	closeOne(&l.errors)
	for id, f := range l.games {
		if f != nil {
			_ = f.Close()
		}
		delete(l.games, id)
	}
	return first
}

// Request пишет строку в requests.log (только при FULL_LOGGING).
func (l *Logger) Request(format string, args ...any) {
	if l == nil || !l.full || l.requests == nil {
		return
	}
	l.write(l.requests, format, args...)
}

// Error пишет строку в errors.log (всегда).
func (l *Logger) Error(format string, args ...any) {
	if l == nil || l.errors == nil {
		return
	}
	l.write(l.errors, format, args...)
}

// Matchmaking пишет в matchmaking.log (только FULL_LOGGING).
func (l *Logger) Matchmaking(format string, args ...any) {
	if l == nil || !l.full || l.mm == nil {
		return
	}
	l.write(l.mm, format, args...)
}

// OpenGame готовит game-{id}.log при FULL_LOGGING; иначе путь без записи.
func (l *Logger) OpenGame(gameID string) (string, error) {
	path := filepath.Join(l.logDir, fmt.Sprintf("game-%s.log", gameID))
	if l == nil || !l.full {
		return path, nil
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if _, ok := l.games[gameID]; ok {
		return path, nil
	}
	if err := os.MkdirAll(l.logDir, 0o755); err != nil {
		return path, err
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return path, err
	}
	l.games[gameID] = f
	return path, nil
}

// Game пишет IN/OUT/domain в game-{id}.log.
func (l *Logger) Game(gameID, format string, args ...any) {
	if l == nil || !l.full || gameID == "" {
		return
	}
	l.mu.Lock()
	f := l.games[gameID]
	if f == nil {
		path := filepath.Join(l.logDir, fmt.Sprintf("game-%s.log", gameID))
		opened, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
		if err != nil {
			l.mu.Unlock()
			return
		}
		l.games[gameID] = opened
		f = opened
	}
	line := fmt.Sprintf("%s %s\n", time.Now().UTC().Format(time.RFC3339), fmt.Sprintf(format, args...))
	_, _ = f.WriteString(line)
	_ = f.Sync()
	l.mu.Unlock()
}

// CloseGame закрывает sink одной игры.
func (l *Logger) CloseGame(gameID string) {
	if l == nil {
		return
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if f, ok := l.games[gameID]; ok && f != nil {
		_ = f.Close()
		delete(l.games, gameID)
	}
}

func (l *Logger) write(f *os.File, format string, args ...any) {
	line := fmt.Sprintf("%s %s\n", time.Now().UTC().Format(time.RFC3339), fmt.Sprintf(format, args...))
	l.mu.Lock()
	defer l.mu.Unlock()
	_, _ = f.WriteString(line)
	_ = f.Sync()
}
