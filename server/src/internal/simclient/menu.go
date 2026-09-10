package simclient

import (
	"bufio"
	"fmt"
	"io"
	"strconv"
	"strings"
)

// LineReader читает строки с одного Scanner (нельзя создавать Scanner на каждый вызов — теряется буфер).
type LineReader struct {
	sc *bufio.Scanner
}

// NewLineReader оборачивает in одним Scanner.
func NewLineReader(in io.Reader) *LineReader {
	return &LineReader{sc: bufio.NewScanner(in)}
}

// Line возвращает следующую trimmed-строку или io.EOF.
func (r *LineReader) Line() (string, error) {
	if !r.sc.Scan() {
		if err := r.sc.Err(); err != nil {
			return "", err
		}
		return "", io.EOF
	}
	return strings.TrimSpace(r.sc.Text()), nil
}

// MenuItem пункт нумерованного меню.
type MenuItem struct {
	ID    int
	Label string
	Key   string
}

// PromptMenu печатает меню и возвращает выбранный Key.
func PromptMenu(out io.Writer, in *LineReader, title string, items []MenuItem) (string, error) {
	fmt.Fprintln(out, title)
	for _, it := range items {
		fmt.Fprintf(out, "%d) %s\n", it.ID, it.Label)
	}
	fmt.Fprint(out, "> ")
	line, err := in.Line()
	if err != nil {
		return "", err
	}
	n, err := strconv.Atoi(line)
	if err != nil {
		return "", fmt.Errorf("введите номер пункта")
	}
	for _, it := range items {
		if it.ID == n {
			return it.Key, nil
		}
	}
	return "", fmt.Errorf("нет пункта %d", n)
}

// PromptString запрашивает произвольную строку (код комнаты и т.п.).
func PromptString(out io.Writer, in *LineReader, prompt string) (string, error) {
	fmt.Fprint(out, prompt)
	return in.Line()
}
