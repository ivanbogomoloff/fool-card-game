# Этап 2 — gRPC, protobuf, TLS/autocert, keepalive

## Цель

Описать и реализовать контракты protobuf, gRPC-сервер с bidi `Session`, TLS через `golang.org/x/crypto/acme/autocert`, server keepalive для мобильных сетей.

## Зависимости

- [01-scaffold-docker.md](01-scaffold-docker.md)

## MUST

1. Файлы в `server/proto/`: `auth.proto`, `matchmaking.proto`, `session.proto`, `game.proto` (+ при необходимости `common.proto`).
2. Codegen в Docker build: `protoc` + `protoc-gen-go` + `protoc-gen-go-grpc`.
3. Unary: `Auth.Login`, `Matchmaking.CreateGame`, `Matchmaking.JoinGame`.
4. Bidi: `Session(stream ClientMessage) returns (stream ServerMessage)`.
5. **Быстрая игра не unary-poll** — только через `ClientMessage.QuickMatch` в stream.
6. Auth: metadata `authorization: Bearer <token>` на unary и stream (interceptor). Реализация проверки токена — этап 3; на этапе 2 — каркас interceptor.
7. Prod: `net.Listen("tcp", ":80")` + `autocert.Manager.HTTPHandler`; `net.Listen("tcp", ":443")` + gRPC с `credentials.NewTLS(m.TLSConfig())` (ALPN `h2`).
8. Local: plaintext gRPC на `HTTP_ADDR`.
9. Keepalive params на сервере согласованы с будущим клиентом (см. ниже).

## Proto — сервисы

### Auth

- `rpc Login(LoginRequest) returns (LoginResponse)`
- Request: optional `display_name`
- Response: `token`, `display_name`, `avatar_id`

### Matchmaking (unary, только private)

- `rpc CreateGame(PlayerProfile) returns (CreateGameResponse)` → `session_id` / `game_id`, `access_code`, `host_id`, `player_id`
- `rpc JoinGame(JoinGameRequest) returns (JoinGameResponse)` → `session_id` / `game_id`, `player_id`  
  Request: `code`, `display_name`, `avatar_id`

Идентификатор сессии матча = **уникальный game id** (UUID); в ответах одно поле (рекомендуется `game_id`, алиас `session_id` на клиенте допустим).

### Session (bidi)

```protobuf
rpc Session(stream ClientMessage) returns (stream ServerMessage);
```

**ClientMessage** (oneof):

| Вариант | Поля / смысл |
|---------|----------------|
| `QuickMatch` | `display_name`, `avatar_id` |
| `Subscribe` | `game_id`, `player_id` (после Create/Join) |
| `Kick` | `player_id` |
| `StartGame` | пусто (только private host) |
| `PlayCard` | `card`, optional `target_pair_id` |
| `AddCard` | `card` |
| `Pass` | |
| `Bito` | |
| `Ready` | |
| `Leave` | явный выход |
| `Ping` | optional |

**ServerMessage** (oneof):

| Вариант | Смысл |
|---------|--------|
| `QueueState` | очередь QuickMatch: count, phase `SEARCHING`/`FILLING`, `player_id` |
| `RoomState` | waiting private / краткий авто-waiting |
| `GameState` | per-player проекция (как `GameStateDto` на Android) |
| `Error` | код + сообщение |
| `Kicked` | |
| `MatchStarted` | переход в матч |
| `LeftAck` | подтверждение Leave |
| `Pong` | |

### Game / Card messages

Согласовать поля с Kotlin DTO (`GameStateDto`, `CardDto`, фазы, статусы игроков).  
Карты: suit/rank enums; в логах — UTF `♠♥♦♣` + ранг (этап 5).

`GameState` для зрителя: полная `local_hand`, у остальных только `hand_count`.

## TLS (prod)

```text
:80  → autocert HTTP-01 (+ опционально redirect)
:443 → grpc.NewServer(grpc.Creds(credentials.NewTLS(manager.TLSConfig())))
```

- `HostPolicy: autocert.HostWhitelist(HOST)`
- `Cache: autocert.DirCache` (volume)
- `Prompt: autocert.AcceptTOS`
- Email: `ACME_EMAIL`

## Keepalive (сервер)

Рекомендуемые стартовые значения (уточнить при интеграции с grpc-kotlin):

| Параметр | Значение | Заметка |
|----------|----------|---------|
| `KeepaliveParams.Time` | 30–60s | ping при простое |
| `KeepaliveParams.Timeout` | ~10s | |
| `EnforcementPolicy.MinTime` | ≤ клиентского interval | иначе EnhancementViolation |
| `PermitWithoutStream` | согласовать с клиентом | |
| `MaxConnectionAge` | optional | мягкий recycle + grace |

Клиент (Phase 6): keepalive ~30s, reconnect при смене Wi‑Fi↔LTE, после reconnect — resnapshot state.

## Нефункциональные требования

- REST poll (`GET /room`, `GET /state`) **не реализуются**.
- Один долгоживущий stream на игрока в очереди/комнате/матче.

## Критерии приёмки

- [x] Proto компилируются в Go; сервисы зарегистрированы
- [x] Prod-путь: listen 80 + 443 с autocert (или интеграционный тест с mock TLS в local)
- [x] Local: insecure gRPC на 8080
- [x] Interceptor читает Bearer (пока может принимать любой non-empty до этапа 3)
- [x] bufconn-тест: открытие Session stream echo/ping-pong

## Тесты этапа

| Тест | Сценарий |
|------|----------|
| `session_grpc_*` | bufconn: Ping → Pong |
| `auth_interceptor_*` | отсутствие metadata → ошибка Unauthenticated (после этапа 3 — строже) |
| `tls_config_*` | при `TLS_ENABLED=false` не требуется HOST |

## Следующий этап

[03-accounts-db.md](03-accounts-db.md)
