package logging

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// Logger — файловые логи запросов (FULL_LOGGING) и ошибок (всегда).
type Logger struct {
	mu       sync.Mutex
	full     bool
	requests *os.File // nil если !full
	errors   *os.File
}

// New открывает errors.log всегда; requests.log — только при full=true.
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
	l := &Logger{full: full, errors: errFile}
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
	}
	return l, nil
}

// Full сообщает, включён ли подробный лог запросов.
func (l *Logger) Full() bool {
	if l == nil {
		return false
	}
	return l.full
}

// Close закрывает файлы логов.
func (l *Logger) Close() error {
	if l == nil {
		return nil
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	var first error
	if l.requests != nil {
		if err := l.requests.Close(); err != nil && first == nil {
			first = err
		}
		l.requests = nil
	}
	if l.errors != nil {
		if err := l.errors.Close(); err != nil && first == nil {
			first = err
		}
		l.errors = nil
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

func (l *Logger) write(f *os.File, format string, args ...any) {
	line := fmt.Sprintf("%s %s\n", time.Now().UTC().Format(time.RFC3339), fmt.Sprintf(format, args...))
	l.mu.Lock()
	defer l.mu.Unlock()
	_, _ = f.WriteString(line)
	_ = f.Sync()
}
