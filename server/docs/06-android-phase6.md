# Этап 6 — Android / Phase 6 (grpc-kotlin)

## Цель

Требования к подключению Android-клиента к Go gRPC-серверу. Реализация — в [Phase 6](../../docs/phases/phase-06-api-integration/README.md) после готовности сервера (этапы 1–5).

## Зависимости

- Сервер по [01](01-scaffold-docker.md)…[05](05-engine-stats-logging.md)
- Phase 5 UI (лобби, waiting, fake) уже есть
- Контракты: [api-contracts.md](../../docs/architecture/api-contracts.md), [game-client.md](../../docs/architecture/game-client.md)

## MUST (Phase 6)

1. Зависимость **grpc-kotlin**; stubs из `server/proto` (копия, submodule или codegen из того же дерева).
2. `RemoteGameClient` вместо Fake/REST-poll:
   - `login` → unary `Auth.Login`; token в gRPC metadata
   - `quickMatch` → открыть bidi `Session` + `QuickMatch`; Flow из `QueueState` / `MatchStarted` — **без** poll каждые 5 с
   - private: unary Create/Join → `Subscribe` на stream
   - `kickPlayer` / `startGame` → сообщения в stream (только UI хоста)
   - `playCard` / `addCard` / `pass` / `bito` / `ready` → исходящий stream
   - **`leaveSession` → `ClientMessage.Leave`** (не только локальная очистка id)
   - `observeState` → Flow из входящих `GameState` / связанных ServerMessage
3. Убрать online-модель poll room 5 с / state 2 с.
4. Keepalive + reconnect при смене сети (Wi‑Fi ↔ LTE):
   - клиентский keepalive ≈ 30s, согласован с сервером
   - `ConnectivityManager` / NetworkCallback → форсировать reconnect stream
   - backoff при `UNAVAILABLE` / GOAWAY
   - после reconnect — полный resnapshot (`QueueState` / `RoomState` / `GameState`)
5. Base URL / host в BuildConfig или local.properties (host:port или домен :443).
6. Ошибки gRPC → UI (sealed Result / UiError); тесты маппера.

## Не входит в Phase 6 (или опционально позже)

- Отдельный REST `/profile` для matchmaking (профиль уже в QuickMatch/Create/Join)
- Восстановление незавершённого матча после рестарта сервера

## Изменения UI (минимальные)

- Быстрая игра: экран «Ожидайте» подписан на `QueueState` (число/фаза), переход в матч по `MatchStarted` — без кнопки «Начать»
- WaitingRoom друзей: без изменений по смыслу; данные со stream, не poll
- LeaveGameDialog → реальный `Leave` на сервер

## DoD Phase 6

- [ ] Login против живого сервера
- [ ] QuickMatch: набор на сервере, автостарт, матч по stream
- [ ] Private: код, kick, start хостом
- [ ] Ходы и состояние по bidi stream
- [ ] Leave отличается от обрыва (reconnect не помечает LEFT)
- [ ] Keepalive/reconnect при смене сети
- [ ] Unit-тесты клиента (metadata, маппинг ошибок, поведение ViewModel на QueueState)

## Ссылки

- ТЗ сервера: [README.md](README.md)
- Phase 6 checklist: [phase-06-api-integration](../../docs/phases/phase-06-api-integration/README.md)
- Краткий указатель: [phase-05b-server](../../docs/phases/phase-05b-server/README.md)
