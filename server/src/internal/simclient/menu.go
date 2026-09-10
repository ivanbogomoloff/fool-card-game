package simclient

import (
	"bufio"
	"fmt"
	"io"
	"strconv"
	"strings"
)

// MenuItem пункт нумерованного меню.
type MenuItem struct {
	ID    int
	Label string
	Key   string
}

// ReadLine читает строку с stdin.
func ReadLine(in io.Reader) (string, error) {
	sc := bufio.NewScanner(in)
	if !sc.Scan() {
		if err := sc.Err(); err != nil {
			return "", err
		}
		return "", io.EOF
	}
	return strings.TrimSpace(sc.Text()), nil
}

// PromptMenu печатает меню и возвращает выбранный Key.
func PromptMenu(out io.Writer, in io.Reader, title string, items []MenuItem) (string, error) {
	fmt.Fprintln(out, title)
	for _, it := range items {
		fmt.Fprintf(out, "%d) %s\n", it.ID, it.Label)
	}
	fmt.Fprint(out, "> ")
	line, err := ReadLine(in)
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
func PromptString(out io.Writer, in io.Reader, prompt string) (string, error) {
	fmt.Fprint(out, prompt)
	return ReadLine(in)
}
