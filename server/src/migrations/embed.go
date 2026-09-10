package migrations

import "embed"

// FS — SQL-файлы миграций (golang-migrate source iofs).
//
//go:embed *.sql
var FS embed.FS
