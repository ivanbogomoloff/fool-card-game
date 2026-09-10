# Этап 3 — Аккаунты, токены, схема статистики, уникальные game id

## Цель

MariaDB: аккаунты и токены входа; схема таблиц статистики завершённых игр; правила уникальности id игры.  
**Запись в `games` / `game_players` на этом этапе только схема + репозиторий; flush вызывается на этапе 5.**

## Зависимости

- [01-scaffold-docker.md](01-scaffold-docker.md)
- [02-grpc-tls-proto.md](02-grpc-tls-proto.md)

## MUST

1. Миграции при старте api (`golang-migrate` или встроенный runner из `src/migrations`).
2. Таблицы: `accounts`, `auth_tokens`, `games`, `game_players`.
3. `Auth.Login` создаёт/находит аккаунт, выдаёт opaque token (или JWT), сохраняет `token_hash` в `auth_tokens`.
4. Interceptor: валидный Bearer → `account_id` в context; иначе `Unauthenticated`.
5. Id игры — **UUID** (или ULID), глобально уникальный; тот же id: in-memory Session, `logs/game-{id}.log`, PK `games.id`.
6. БД **не** хранит очередь matchmaking, колоду, текущий стол, комнаты в realtime.

## Схема

### `accounts`

| Колонка | Тип | Описание |
|---------|-----|----------|
| `id` | PK (UUID/CHAR) | |
| `display_name` | VARCHAR | |
| `avatar_id` | INT | |
| `created_at` | DATETIME | |

### `auth_tokens`

| Колонка | Тип | Описание |
|---------|-----|----------|
| `token_hash` | PK/UNIQUE | hash выданного token |
| `account_id` | FK → accounts | |
| `expires_at` | DATETIME | nullable или TTL |
| `created_at` | DATETIME | |

### `games`

Пишется **только после FINISHED** (этап 5).

| Колонка | Тип | Описание |
|---------|-----|----------|
| `id` | PK | = game UUID |
| `started_at` | DATETIME | |
| `finished_at` | DATETIME | |
| `access_code` | VARCHAR NULL | пусто для QuickMatch |
| `players_at_start` | INT | |
| `result` | ENUM/VARCHAR | `DRAW` \| `HAS_FOOL` |
| `fool_account_id` | FK NULL | если HAS_FOOL |

### `game_players`

| Колонка | Тип | Описание |
|---------|-----|----------|
| `game_id` | FK → games | |
| `account_id` | FK → accounts | |
| `player_result` | VARCHAR | `WIN` \| `FOOL` \| `DRAW` \| `LEFT` |
| UNIQUE | `(game_id, account_id)` | |

Агрегаты профиля (число побед и т.д.) в v1 — SQL по `game_players`, без обязательной денормализации.

## Login

1. Клиент вызывает `Auth.Login` (optional display_name).
2. Сервер создаёт аккаунт (или обновляет имя) + token.
3. Response: `token`, `display_name`, `avatar_id` (дефолт avatar `0` если не задан).
4. Дальнейшие RPC — Bearer.

Профиль для matchmaking (`display_name`, `avatar_id`) также передаётся в `QuickMatch` / Create / Join — как на Android Phase 5.

## Уникальность game id

- Генерация при **старте матча** (после QuickMatch fill или private `StartGame`), не при постановке в очередь.
- Коллизии недопустимы; access_code комнат — отдельное поле (6 символов из `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`), не заменяет game id.

## Нефункциональные требования

- Пароли / OAuth вне MVP Login (упрощённый вход по аналогии с Phase 5 fake gate допустим; token всё равно обязателен).
- Секреты БД только из env.

## Критерии приёмки

- [x] Миграции применяются на чистой БД
- [x] Login → token → защищённый unary успешен; без token — отказ
- [x] Схема `games` / `game_players` существует; до FINISHED строк в `games` нет (проверка на этапе 5)
- [x] Генератор id: unit-тест уникальности при параллельных вызовах

## Миграции (как устроено)

Файлы лежат в **`server/src/migrations/`** рядом с Go-модулем:

```text
server/src/migrations/
  embed.go                 # //go:embed *.sql → package migrations.FS
  000001_init.up.sql       # accounts, auth_tokens, games, game_players
  000001_init.down.sql     # DROP в обратном порядке
```

**Куда писать новые изменения схемы**

1. Добавить пару файлов с следующим номером: `000002_<slug>.up.sql` / `000002_<slug>.down.sql` (формат [golang-migrate](https://github.com/golang-migrate/migrate)).
2. Не править уже применённые `up` на проде — только новая версия.
3. Embed подхватывает все `*.sql` автоматически (`embed.go`); отдельный COPY в Dockerfile не нужен — SQL попадает в бинарник через `go:embed`.

**Когда применяются**

При старте `api` в [`cmd/server/main.go`](../src/cmd/server/main.go): после успешного `Ping` MariaDB вызывается `migrate.Up(db)` ([`internal/migrate`](../src/internal/migrate/migrate.go)). Таблица `schema_migrations` ведёт golang-migrate. Повторный старт с той же версией — no-op (`ErrNoChange`).

DSN должен допускать несколько statements в одном файле (`multiStatements=true` уже в `config.DSN`).

**Локально / тесты**

- Compose: `docker compose up -d --build api` — миграции сами при старте контейнера.
- Unit: `TestMigrate_UpOnEmptyDB` сбрасывает таблицы и гоняет `Up` на `127.0.0.1:3306` (порт из override).

## Тесты этапа

| Тест | Сценарий |
|------|----------|
| `auth_*` | login; повторный login; invalid token |
| `game_id_*` | N параллельных UUID — все различны |
| `migrate_*` | up на пустой БД |

## Следующий этап

[04-matchmaking-session.md](04-matchmaking-session.md)
