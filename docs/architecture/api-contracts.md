# API-контракты

Транспорт онлайн-API: **gRPC over HTTP/2** (protobuf).  
В **Phase 5** клиент использует заглушки (`FakeGameApi`, имитация REST). Живой бэкенд и клиент на gRPC — **после ТЗ сервера** ([`server/docs`](../../server/docs/README.md)) и **Phase 6**.

Источник правды по серверу: [`server/docs`](../../server/docs/README.md). Ниже — сжатое описание для Android.

## Сервисы

### Unary

| RPC | Назначение |
|-----|------------|
| `Auth.Login` | Вход по уникальному `username` (+ `password` если имя занято) → `token`, `username`, `account_id`; при регистрации ещё plaintext `password` (≥18). Без `avatar_id`. |
| `Matchmaking.CreateGame` | Комната с друзьями → `game_id`/`session_id`, `access_code`, `host_id`, `player_id` |
| `Matchmaking.JoinGame` | Вход по коду → `game_id`, `player_id` (+ профиль в запросе) |

Auth на всех защищённых RPC: metadata `authorization: Bearer <token>`.  
Клиент хранит `account_id` + password в Keystore / EncryptedSharedPreferences (Phase 6). Аватар — только в matchmaking / QuickMatch (`username` + `avatar_id`).

### Bidi stream

```text
Session(stream ClientMessage) returns (stream ServerMessage)
```

Один stream на очередь быстрой игры / waiting / матч. Poll `GET /room` и `GET /state` **не используются**.

**ClientMessage:** `QuickMatch`, `Subscribe`, `Kick`, `StartGame`, `PlayCard`, `AddCard`, `Pass`, `Bito`, `Ready`, `Leave`, `Ping`.

**ServerMessage:** `QueueState`, `RoomState`, `GameState` (per-player), `Error`, `Kicked`, `MatchStarted`, `LeftAck`, `Pong`.

## Быстрая игра

Не unary poll и не HTTP 204. Клиент открывает `Session` и шлёт `QuickMatch`. Сервер пушит `QueueState` (`SEARCHING` / `FILLING`); при наборе ≥2 + fill ~5 с — **автостарт** (`MatchStarted` + `GameState`) без клиентского `StartGame`.

## Игра с друзьями

1. `CreateGame` или `JoinGame` (unary)
2. `Subscribe` на stream → `RoomState`
3. Хост: `Kick` / `StartGame` (≥2)
4. Далее `GameState` по событиям

## Leave

`ClientMessage.Leave` — добровольный выход (`LEFT`). Обрыв stream — `DISCONNECTED` (окно reconnect).  
`leaveSession` на клиенте в Phase 6 обязан слать `Leave`, не только чистить локальный id.

## Профиль в matchmaking

Имя и аватар передаются в `QuickMatch` / Create / Join. Отдельный `/profile` для лобби не требуется.

## DTO на клиенте

- Kotlin `@Serializable` DTO в `data/api/dto/` остаются для UI/domain mapping.
- Phase 6: маппинг protobuf ↔ DTO в `RemoteGameClient`.
- Ошибки gRPC → sealed `ApiError` / `Result`.

## Заглушки по этапам

| Этап | Реализация |
|------|------------|
| Phase 5 | `FakeGameApi` (локальные stubs) |
| Server | Go gRPC — [`server/docs`](../../server/docs/README.md) |
| Phase 6 | grpc-kotlin + живой сервер |
