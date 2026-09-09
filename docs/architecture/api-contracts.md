# API-контракты

REST API будет реализован отдельно. В **Phase 5** клиент ходит на заглушки (`FakeGameApi`); живой бэкенд — **Phase 6**.

Префикс игровых маршрутов: **`/game/`** (не `/games/`).

## Endpoints

| Метод | Endpoint | Назначение |
|-------|----------|------------|
| POST | `/auth/login` | Вход (UI-gate в Phase 5 на fake) |
| POST | `/game/fast/join` | Быстрая игра (poll); 200 + sessionId или 204 ещё ждём; тело: displayName, avatarId |
| POST | `/game/create` | Создать комнату → `{ sessionId, accessCode }` + профиль в теле |
| POST | `/game/join` | Войти по коду (+ профиль в теле) |
| GET | `/game/{id}/room` | Состав комнаты (poll 5 с) |
| POST | `/game/{id}/kick` | Хост удаляет игрока `{ playerId }` |
| POST | `/game/{id}/start` | Хост начинает матч |
| GET | `/game/{id}/state` | Tick-опрос состояния матча |
| POST | `/game/{id}/actions` | Игровые действия |
| POST | `/game/{id}/leave` | Выход из сессии |

Имя и аватар **не** сохраняются отдельным `/profile` в matchmaking: передаются при `fast/join` / `create` / `join`.

## POST /game/fast/join

```json
{ "displayName": "Иван", "avatarId": 3 }
```

Ответ 200: `{ "sessionId": "abc123" }`. Ответ 204: ещё в очереди.

## POST /game/create

```json
{ "displayName": "Иван", "avatarId": 3 }
```

```json
{ "sessionId": "abc123", "accessCode": "ABCD12", "hostId": "p1" }
```

## POST /game/join

```json
{ "code": "ABCD12", "displayName": "Мария", "avatarId": 1 }
```

```json
{ "sessionId": "abc123" }
```

## GET /game/{id}/room

```json
{
  "sessionId": "abc123",
  "accessCode": "ABCD12",
  "hostId": "p1",
  "started": false,
  "players": [
    { "id": "p1", "displayName": "Иван", "avatarId": 0, "isHost": true },
    { "id": "p2", "displayName": "Мария", "avatarId": 1, "isHost": false }
  ]
}
```

## POST /game/{id}/kick

```json
{ "playerId": "p2" }
```

## POST /game/{id}/start

Пустое тело. После успеха клиент переходит на `GET /game/{id}/state`.

## GET /game/{id}/state

Tick-опрос. Возвращает полное состояние партии (как раньше).

## POST /game/{id}/actions

Типы: `playCard`, `addCard`, `pass`, `bito`, `ready`.

## DTO

- Все DTO — `@Serializable` data classes в `data/api/dto/`.
- Ошибки: HTTP 4xx/5xx → sealed class `ApiError` в repository (Phase 6).

## Заглушки по этапам

| Этап | Реализация |
|------|------------|
| Phase 5 | `FakeGameApi` |
| Phase 6 | Реальный base URL + auth interceptor |
