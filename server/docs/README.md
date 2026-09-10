# ТЗ: Go gRPC-сервер FoolCardGame

Техническое задание на бэкенд онлайн-игры «Подкидной дурак».  
**Этот документ и файлы `01`…`06` — источник правды для реализации сервера.** Код пишется отдельными итерациями строго по этапным чеклистам.

## Цель

Живой сервер в каталоге [`../`](../) (рядом с этим `docs/`):

- **gRPC over HTTP/2 + TLS** (Let's Encrypt via `autocert` на `:80` / `:443`)
- **Bidirectional stream** `Session` — команды клиента и push состояния (без REST-poll)
- **QuickMatch** — очередь с fill-window и автостартом; **private** — код + старт хостом
- **MariaDB** — только аккаунты и статистика **после** `FINISHED`
- **Файловые логи** `./server/logs` (`FULL_LOGGING` по умолчанию включён)

Клиент Android: Phase 5 на `FakeGameApi`; подключение к этому серверу — **Phase 6** ([`06-android-phase6.md`](06-android-phase6.md)).

## Границы (scope)

| Входит | Не входит |
|--------|-----------|
| ТЗ и последующая реализация Go-сервера по этапам `01`…`05` | Изменения UI Android в рамках написания ТЗ |
| Контракты gRPC / proto (описание) | WebSocket / REST poll как транспорт матча |
| Схема БД и правила flush статистики | Хранение realtime-стола/колоды в БД |
| Требования к Phase 6 (grpc-kotlin) | Полная реализация Phase 6 в этом ТЗ |

## Зависимости

| Документ / этап | Роль |
|-----------------|------|
| [Phase 5 — Online](../../docs/phases/phase-05-online/README.md) | UI лобби / waiting / fake API — уже есть |
| [Правила](../../docs/game-rules/podkidnoy-durak.md) | Домен engine |
| [GameEngine (Kotlin)](../../app/src/main/java/com/example/foolcardgame/domain/engine/GameEngine.kt) | Эталон логики для порта на Go |
| [Phase 6](../../docs/phases/phase-06-api-integration/README.md) | Подключение Android к серверу |
| [API-контракты](../../docs/architecture/api-contracts.md) | Синхронизированы с этим ТЗ (gRPC) |

## Карта этапов реализации

| # | Файл | Кратко |
|---|------|--------|
| 0 | [README.md](README.md) | Scope, глоссарий, DoD всего сервера |
| 1 | [01-scaffold-docker.md](01-scaffold-docker.md) | Структура, Docker, compose, MariaDB, env, logs/ |
| 2 | [02-grpc-tls-proto.md](02-grpc-tls-proto.md) | Proto, bidi Session, autocert, keepalive |
| 3 | [03-accounts-db.md](03-accounts-db.md) | accounts, tokens, Login; схема games; уникальные id |
| 4 | [04-matchmaking-session.md](04-matchmaking-session.md) | QuickMatch, private, Leave, hub + playerChannel |
| 5 | [05-engine-stats-logging.md](05-engine-stats-logging.md) | Engine, MatchLog→flush, FULL_LOGGING, тесты |
| 6 | [06-android-phase6.md](06-android-phase6.md) | grpc-kotlin, GameClient, reconnect |
| — | [07-simclient.md](07-simclient.md) | Терминальный gRPC-симулятор (human/bot) |

Каждый этапный файл содержит: цель, зависимости, MUST, контракты/алгоритмы, критерии приёмки, тесты этапа.

## Глоссарий

| Термин | Описание |
|--------|----------|
| Session (stream) | gRPC bidi RPC: входящие `ClientMessage`, исходящие `ServerMessage` |
| playerChannel | Буферизованный `chan` на игрока; goroutine стрима читает и `Send` |
| QuickMatch | Быстрая игра без кода: очередь + fill 5 с + автостарт сервером |
| MatchLog | In-memory лог матча для flush в БД только после `FINISHED` |
| Leave | Явный выход клиента; отличается от `DISCONNECTED` |
| FULL_LOGGING | Файловые логи запросов/уведомлений/домена (дефолт `true`) |

## Целевая структура `./server` (после реализации)

```text
server/
  docs/                 # это ТЗ
  Dockerfile
  docker-compose.yaml
  docker-compose.override.yaml
  .env.example
  bin/
  logs/
  proto/
  src/
```

Подробности — в [01-scaffold-docker.md](01-scaffold-docker.md).

## DoD всего сервера (MVP)

- [ ] `docker compose up` поднимает `api` + `mariadb`; прод: TLS на 443, ACME на 80
- [ ] Login + Create/Join + Session stream работают end-to-end
- [ ] QuickMatch: ≥2 → FILLING 5 с → автостарт без клиентского `StartGame`
- [ ] Private: kick/start только хост
- [ ] Leave ≠ disconnect; статистика в БД только после `FINISHED`
- [ ] Уникальный UUID игры = память = `logs/game-{id}.log` = PK `games.id`
- [ ] `FULL_LOGGING`: IN/OUT в game-лог; ошибки в `errors.log`
- [ ] Unit-тесты в `server/src/tests` зелёные
- [ ] Контракты согласованы с Phase 6 / `api-contracts.md`

## Порядок работы

1. Утвердить это ТЗ.
2. Реализовывать сервер по `01` → `05` (чеклисты в файлах).
3. Phase 6 Android — по [`06-android-phase6.md`](06-android-phase6.md).
