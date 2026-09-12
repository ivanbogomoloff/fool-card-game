# Этап 1 — Scaffold, Docker, MariaDB, env, logs

## Цель

Каркас каталога `./server`, сборка бинарника через `docker build`, запуск через `docker compose` с MariaDB и томами для логов. Без игровой логики.

## Зависимости

- Утверждённое [README.md](README.md) ТЗ

## MUST

1. Структура каталогов как ниже.
2. Multi-stage `Dockerfile`: сборка Go-бинарника → runtime-образ; артефакт доступен как `bin/server` (или копирование из build-stage).
3. `docker-compose.yaml`: сервисы `api` + `mariadb`; `api` зависит от healthy MariaDB; volume логов; env из `.env`.
4. `docker-compose.override.yaml`: локальный режим без Let's Encrypt (plaintext gRPC, порт например `8080`, MariaDB `3306`).
5. `.env.example` со всеми ключами (см. ниже); реальный `.env` в `.gitignore`.
6. Каталог `logs/` с `.gitkeep`; `logs/*.log` в `.gitignore`.
7. При старте `api` читает конфиг из env; падает с понятной ошибкой, если обязательные переменные пусты (в prod: `HOST`, DB_*).

## Структура каталогов

```text
server/
  Dockerfile
  docker-compose.yaml
  docker-compose.override.yaml
  .env.example
  .gitignore
  bin/                             # скомпилированный бинарник
  logs/                            # runtime: game-*.log, errors.log, matchmaking.log
  docs/                            # ТЗ (этот каталог)
  proto/                           # *.proto (заполняется на этапе 2)
  src/
    go.mod
    go.sum
    cmd/server/main.go             # минимальный health / listen stub на этапе 1
    internal/
      config/
    migrations/                    # пусто или placeholder; схема на этапе 3
    tests/
```

## Docker / runtime

**Сборка:** из `./server`:

```bash
docker build -t fool-server .
```

**Запуск:**

```bash
docker compose --env-file .env up
```

**Сервисы:**

| Сервис | Назначение |
|--------|------------|
| `api` | Go-бинарник; порты 80+443 (prod) или 8080 (override) |
| `mariadb` | Официальный образ; volume `mariadb_data`; healthcheck |

**Volume логов:** `./logs:/app/logs` (или путь из `LOG_DIR`).

## Переменные окружения (`.env.example`)

| Переменная | Дефолт / пример | Смысл |
|------------|-----------------|--------|
| `HOST` | `example.com` | Домен для autocert HostWhitelist (prod) |
| `ACME_EMAIL` | `admin@example.com` | Контакт Let's Encrypt |
| `TLS_ENABLED` | `true` (prod) / `false` (local) | Включить autocert + :443 |
| `HTTP_ADDR` | `:8080` | Listen без TLS (local) |
| `DB_HOST` | `mariadb` | Хост БД в сети compose |
| `DB_PORT` | `3306` | |
| `DB_USER` | | |
| `DB_PASSWORD` | | |
| `DB_NAME` | `foolcard` | |
| `FULL_LOGGING` | `true` | Файловые game/matchmaking логи |
| `LOG_DIR` | `./logs` | Каталог логов |
| `QUICK_MIN_PLAYERS` | `2` | См. этап 4 |
| `QUICK_MAX_PLAYERS` | `4` | |
| `QUICK_FILL_WINDOW` | `5s` | |
| `QUICK_QUEUE_TIMEOUT` | `120s` | |
| `GAMES_ACTIVE_SECRET` | (пусто → `/games/active` всегда 401) | Секрет заголовка `X-Secret-Key` для `GET /games/active` |

## Слушатели (заготовка; полная TLS-логика — этап 2)

- Prod: `:80` (ACME), `:443` (gRPC TLS) через `net.Listen("tcp", …)`
- Local override: один `HTTP_ADDR` без TLS

На этапе 1 достаточно поднять процесс, подключиться к MariaDB (ping) и слушать порт (заглушка handler / health).

## Нефункциональные требования

- Образ `api` не содержит исходников `.env` с секретами.
- MariaDB данные переживают `compose down` (named volume).
- Логи с хоста читаются из `./server/logs` без `docker exec`.

## Критерии приёмки

- [x] `docker build` успешен, бинарник в image / `bin/`
- [x] `docker compose up` (с override): MariaDB healthy, api стартует, порт доступен
- [x] `.env.example` полный; `.env` и `logs/*.log` не коммитятся
- [x] `config` пакет загружает env; unit-тест парсинга конфига

## Тесты этапа

| Тест | Сценарий |
|------|----------|
| `config_*` | Дефолт `FULL_LOGGING=true`; обязательные поля; парсинг duration `QUICK_FILL_WINDOW` |

## Следующий этап

[02-grpc-tls-proto.md](02-grpc-tls-proto.md)
