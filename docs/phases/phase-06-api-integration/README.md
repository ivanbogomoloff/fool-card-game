# Phase 6 — Подключение реального API

## Цель

Заменить все Fake*-реализации на живой REST API.

## Зависимости

- [Phase 5 — Online](../phase-05-online/README.md)
- [API-контракты](../../architecture/api-contracts.md)

## Файлы

```
data/api/AuthApi.kt
data/api/ProfileApi.kt          (реальная impl)
data/api/GameApi.kt             (реальная impl)
data/network/AuthInterceptor.kt
data/network/ApiModule.kt       (Retrofit builder, base URL)
BuildConfig.API_BASE_URL
```

## Задачи

- [ ] Настроить base URL (BuildConfig / local.properties)
- [ ] Auth: POST `/auth/login`, token в OkHttp interceptor
- [ ] Profile: GET/POST `/profile`
- [ ] Games: все endpoints из api-contracts.md
- [ ] Обработка ошибок сети (sealed ApiResult)
- [ ] Loading / error states в UI
- [ ] LoginScreen: реальный вход вместо заглушки
- [ ] `ApiErrorMapperTest`, smoke-тесты реальных endpoint-контрактов

## DoD

- Login, profile, lobby, game работают с живым API
- Tick-опрос через GET /games/{id}/state
- Graceful handling offline / 4xx / 5xx
- Error mapping покрыт unit-тестами

## Юнит-тесты

| Класс | Сценарии |
|-------|----------|
| `ApiErrorMapperTest` | 401, 404, 500 → UiError / Result |
| `AuthInterceptorTest` | token добавляется в header |
| `*IntegrationTest` (optional) | контракт JSON совпадает с `api-contracts.md` |

См. [testing.md](../../architecture/testing.md).

## Завершение

Все этапы пройдены. Игра готова к релизу MVP.
