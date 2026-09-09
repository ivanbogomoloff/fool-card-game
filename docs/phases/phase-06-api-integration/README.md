# Phase 6 — Подключение реального API (gRPC)

## Цель

Заменить Fake*-реализации на живой **gRPC**-сервер ([`server/docs`](../../../server/docs/README.md)): unary Login/Create/Join + bidi `Session` stream.

## Зависимости

- [Phase 5 — Online](../phase-05-online/README.md) — UI и fake
- [ТЗ сервера](../../../server/docs/README.md) — этапы 1–5 реализованы
- [Требования клиента](../../../server/docs/06-android-phase6.md)
- [API-контракты](../../architecture/api-contracts.md)
- [GameClient](../../architecture/game-client.md)

## Файлы (ориентир)

```
server/proto/                     # общий контракт
data/network/GrpcModule.kt        # channel, keepalive, credentials
data/network/AuthMetadata.kt      # Bearer token
data/client/RemoteGameClient.kt   # gRPC вместо Fake/REST poll
BuildConfig / local.properties    # host:port или https host
```

Стек: **grpc-kotlin**, не Retrofit poll для матча.

## Задачи

- [ ] Codegen stubs из `server/proto`
- [ ] gRPC channel: TLS (prod) / plaintext (debug); keepalive ~30s
- [ ] Auth: `Auth.Login` + metadata `authorization: Bearer`
- [ ] QuickMatch: stream + `QuickMatch`; UI на `QueueState` → `MatchStarted` (без poll 5 с)
- [ ] Private: Create/Join unary → `Subscribe`; kick/start через stream
- [ ] `observeState` / actions / **`Leave`** через bidi Session
- [ ] Reconnect при смене сети + resnapshot
- [ ] Ошибки gRPC → UiError; loading states
- [ ] LoginScreen против живого Login

## DoD

- [ ] Login, лобби, waiting, матч работают с живым сервером
- [ ] Состояние матча и комнаты — push по stream (не GET poll)
- [ ] Leave добровольный; disconnect ≠ LEFT
- [ ] Keepalive / смена Wi‑Fi↔LTE с восстановлением stream
- [ ] Unit-тесты: metadata, error mapping, ViewModel на QueueState/MatchStarted

## Юнит-тесты

| Класс | Сценарии |
|-------|----------|
| `AuthMetadataTest` | token в metadata |
| `ApiErrorMapperTest` / gRPC status → UiError | Unauthenticated, Unavailable, … |
| `OnlineLobbyViewModelTest` | обновлён под stream-очередь (не 204-poll) |
| `WaitingRoomViewModelTest` | RoomState со stream |

См. [testing.md](../../architecture/testing.md), [06-android-phase6.md](../../../server/docs/06-android-phase6.md).

## Завершение

Сервер + клиент на gRPC. MVP онлайн готов к релизу.
