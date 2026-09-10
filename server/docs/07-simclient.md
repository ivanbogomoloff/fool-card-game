# Симулятор клиента (simclient)

Терминальный клиент для отладки Go gRPC-сервера. Говорит **только** по protobuf/gRPC (HTTP/2), без знания серверной логики.

## Сборка

### Docker (тот же Dockerfile, что у api)

```bash
cd server
docker build -t fool-simclient --target simclient .

# сеть compose (имя может быть server_default)
docker run -it --rm --network server_default fool-simclient \
  --mode human --addr api:8080 --name Алиса
```

Сервер:

```bash
docker compose up -d --build
```

Оба бинарника в stage `build`; targets: `runtime` (api), `simclient`, `bin` (оба файла).

### Локально

```bash
cd server/src
go run ./cmd/simclient --mode human --name Алиса --addr 127.0.0.1:8080
go run ./cmd/simclient --mode bot --name Бот1 --quick --addr 127.0.0.1:8080
```

## Human

После старта — **нумерованное меню** (ввод только номера):

1. login  
2. create  
3. join (запросит код)  
4. quick match  
5. ping  
…  
8. сыграть карту — экран стола/рук  

Ход картами:

```text
Стол:
1. Дама ♠ > Туз ♠
2. 10 ♠ > ?

Ваши карты:
1. Валет ♠
2. Король ♠

> 2>1
```

| Ввод | Смысл |
|------|--------|
| `2>1` | рука 2 бьёт пару 1 |
| `3` | атака/подкид картой 3 |
| `p` / `b` | pass / bito |

## Bot

```bash
docker run --rm --network server_default fool-simclient \
  --mode bot --name Бот1 --quick --addr api:8080
```

Авто: login → quick|join → реакция по `can_*` / случайная карта из `local_hand`.

## Что уже работает на сервере (после этапа 2)

| RPC | Статус |
|-----|--------|
| `Auth.Login` | ok (stub token) |
| Session `Ping` / `Leave` | ok |
| Create/Join/ходы | `Unimplemented` до этапов 3–5 |

Симулятор всё равно шлёт реальные запросы и печатает ошибки — удобно наращивать проверку.
