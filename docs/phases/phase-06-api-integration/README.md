# Phase 6 — Подключение реального API (gRPC)

## Цель

Заменить Fake*-реализации на живой **gRPC**-сервер ([`server/docs`](../../../server/docs/README.md)): unary Login/Create/Join + bidi `Session` stream.

## Зависимости

- [Phase 5 — Online](../phase-05-online/README.md) — UI и fake
- [ТЗ сервера](../../../server/docs/README.md) — этапы 1–5 реализованы
- [Требования клиента](../../../server/docs/06-android-phase6.md)
- [API-контракты](../../architecture/api-contracts.md)
- [GameClient](../../architecture/game-client.md)

## Файлы

```
server/proto/                          # общий контракт (srcDir в app)
data/network/GrpcChannelFactory.kt     # channel, keepalive, TLS/plaintext
data/network/AuthMetadata.kt           # Bearer token
data/network/GrpcSessionController.kt  # bidi Session + reconnect
data/network/GrpcErrorMapper.kt
data/client/RemoteGameClient.kt        # gRPC вместо Fake/REST poll
BuildConfig / local.properties         # grpc.host / grpc.port (debug)
```

**Host:**
- debug: `local.properties` → `grpc.host` / `grpc.port` (default `192.168.0.4:8080`, plaintext)
- release: `fool-card.n-game.ru:443`, TLS
- Сменил Wi‑Fi IP → правь `grpc.host` в `local.properties`

Стек: **grpc-kotlin**, не Retrofit poll для матча.

## Задачи

- [x] Codegen stubs из `server/proto`
- [x] gRPC channel: TLS (prod) / plaintext (debug); keepalive ~30s
- [x] Auth: `Auth.Login` (username; password из Keystore при совпадении имени / регистрация без UI пароля) + metadata `authorization: Bearer`
- [x] Хранение `account_id` + password в EncryptedSharedPreferences / Keystore
- [x] LoginScreen: только имя; пароль не показывается; «имя занято» → сменить имя ([Phase 7](../phase-07-account-binding/README.md) — привязка/восстановление)
- [x] OnlineLobby: имя игрока read-only (= username аккаунта); аватар выбирается
- [x] QuickMatch: stream + `QuickMatch`; UI на `QueueState` → `MatchStarted` (без poll 5 с)
- [x] Private: Create/Join unary → `Subscribe`; kick/start через stream
- [x] `observeState` / actions / **`Leave`** через bidi Session
- [x] Reconnect при смене сети + resnapshot
- [x] Ошибки gRPC → UiError; loading states
- [x] LoginScreen против живого Login (без avatar на Login; avatar в лобби/matchmaking)

## DoD

- [x] Login, лобби, waiting, матч — клиент на gRPC (ручная проверка на LAN)
- [x] Состояние матча и комнаты — push по stream (не GET poll)
- [x] Leave добровольный (`ClientMessage.Leave`); disconnect ≠ LEFT
- [x] Keepalive / смена Wi‑Fi↔LTE с восстановлением stream
- [x] Unit-тесты: metadata, error mapping, ViewModel на QueueState/MatchStarted

## Юнит-тесты

| Класс | Сценарии |
|-------|----------|
| `AuthMetadataTest` | token в metadata |
| `GrpcErrorMapperTest` | Unauthenticated, Unavailable, … |
| `OnlineLobbyViewModelTest` | stream-очередь (не 204-poll) |
| `WaitingRoomViewModelTest` | RoomState со stream |
| `RemoteGameClientTest` | FakeOnline + OnlineSessionIds |

См. [testing.md](../../architecture/testing.md), [06-android-phase6.md](../../../server/docs/06-android-phase6.md).

## Завершение

Сервер + клиент на gRPC. MVP онлайн готов к релизу.

**Дальше (не в Phase 6):** [Phase 7 — Привязка аккаунта](../phase-07-account-binding/README.md).
