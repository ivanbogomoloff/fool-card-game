# API-контракты

REST API будет реализован отдельно. Клиент закладывается на эти endpoints с заглушками до Phase 6.

## Endpoints

| Метод | Endpoint | Назначение |
|-------|----------|------------|
| POST | `/auth/login` | Вход (Phase 6) |
| GET | `/profile` | Получить профиль |
| POST | `/profile` | Сохранить профиль |
| GET | `/games` | Список игр |
| POST | `/games` | Создать игру |
| POST | `/games/join` | Вход по коду |
| GET | `/games/{id}/state` | Tick-опрос состояния |
| POST | `/games/{id}/actions` | Игровые действия |
| POST | `/games/{id}/leave` | Выход из сессии |

## GET /games/{id}/state

Tick-опрос. Возвращает полное состояние партии.

```json
{
  "sessionId": "abc123",
  "phase": "IN_PROGRESS",
  "serverTick": 1700000000123,
  "players": [
    {
      "id": "p1",
      "displayName": "Иван",
      "avatarId": 0,
      "handCount": 5,
      "isReady": true,
      "isConnected": true,
      "status": "PLAYING"
    }
  ],
  "deckCount": 12,
  "trump": { "suit": "hearts", "rank": "7" },
  "table": [],
  "currentPlayerId": "p1"
}
```

## POST /games/{id}/actions

Типы действий: `playCard`, `addCard`, `pass`, `bito`, `ready`.

```json
{ "type": "addCard", "card": { "suit": "hearts", "rank": "7" } }
```

```json
{ "type": "playCard", "card": { "suit": "spades", "rank": "K" }, "targetPairId": 2 }
```

```json
{ "type": "ready" }
```

## POST /profile

```json
{ "displayName": "Иван", "avatarId": 3 }
```

## POST /games

```json
{ "maxPlayers": 4, "isPrivate": true }
```

## POST /games/join

```json
{ "code": "ABCD12" }
```

## DTO

- Все DTO — `@Serializable` data classes в `data/api/dto/`.
- Общие для local (маппинг из domain) и remote.
- Ошибки: HTTP 4xx/5xx → sealed class `ApiError` в repository.

## Заглушки по этапам

| Этап | Реализация |
|------|------------|
| Phase 2 | `FakeProfileApi` |
| Phase 5 | `FakeGameApi` / OkHttp Interceptor |
| Phase 6 | Реальный base URL + auth interceptor |
