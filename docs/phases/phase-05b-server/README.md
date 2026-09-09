# Phase 5b — ТЗ Go gRPC-сервера

**Статус: ТЗ готово; реализация сервера — по этапным чеклистам.**

## Цель

Промежуточный этап между Phase 5 (fake online) и Phase 6 (клиент на живом API): зафиксировать и затем реализовать бэкенд.

## Документация

Полное декомпозированное ТЗ:

**→ [`server/docs/README.md`](../../../server/docs/README.md)**

| Этап | Файл |
|------|------|
| Оглавление | [README](../../../server/docs/README.md) |
| Scaffold / Docker / MariaDB | [01](../../../server/docs/01-scaffold-docker.md) |
| gRPC / TLS / proto | [02](../../../server/docs/02-grpc-tls-proto.md) |
| Аккаунты / БД | [03](../../../server/docs/03-accounts-db.md) |
| Matchmaking / Session | [04](../../../server/docs/04-matchmaking-session.md) |
| Engine / stats / logs | [05](../../../server/docs/05-engine-stats-logging.md) |
| Android Phase 6 | [06](../../../server/docs/06-android-phase6.md) |

## Зависимости

- [Phase 5 — Online](../phase-05-online/README.md) — завершён по UI/fake
- Далее: реализация `server/` по ТЗ → [Phase 6](../phase-06-api-integration/README.md)

## Кратко по решению

- gRPC bidi `Session` (не REST poll)
- QuickMatch: очередь ≥2 + fill 5 с + автостарт
- MariaDB: аккаунты + статистика только после FINISHED
- Leave явный; FULL_LOGGING в `server/logs`
- TLS: autocert :80 / :443
