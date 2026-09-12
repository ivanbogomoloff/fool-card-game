# Симулятор клиента (simclient)

Терминальный клиент для отладки Go gRPC-сервера. Говорит **только** по protobuf/gRPC (HTTP/2), без знания серверной логики.

## Сборка

### Docker (тот же Dockerfile, что у api)

```bash
cd server
docker build -t fool-simclient --target simclient .

docker run -it --rm --network server_default \
  -v "$HOME/.config/foolcard-simclient:/home/nonroot/.config/foolcard-simclient" \
  fool-simclient --mode human --addr api:8080 --name Алиса
```

Сервер:

```bash
docker compose up -d --build
```

### Локально

```bash
cd server/src
go run ./cmd/simclient --mode human --name Алиса --addr 127.0.0.1:8080
go run ./cmd/simclient --mode bot --name Бот1 --quick --addr 127.0.0.1:8080
```

Флаги: `--name` / `--username`, `--password`, `--creds` (путь к JSON).

## Credentials (аналог Keystore)

После регистрации simclient сохраняет:

`~/.config/foolcard-simclient/credentials.json` — `{ "username", "account_id", "password" }`.

Повторный login с тем же `--name` подставляет пароль из файла (если не задан `--password`).

## Human

Меню: `1) login` → регистрация или вход по паролю; вывод `account_id` и (только при регистрации) plaintext password.

## Bot

Авто: login (с credentials) → quick|join → ходы по `can_*`.

## Что работает (после username+password)

| RPC / поведение | Статус |
|-----------------|--------|
| `Auth.Login` | уникальный `username`; регистрация → password; повтор → password; ответ: token, username, account_id |
| Bearer | interceptor → `account_id` |
| Session Ping/Leave | ok |
| Create/Join/QuickMatch | Unimplemented до 4–5 |

### Проверка Login дважды

```bash
cd server
docker compose up -d --build api
docker build -t fool-simclient --target simclient .
CREDS=$(mktemp)
printf '1\n0\n' | docker run -i --rm --network server_default \
  -v "$CREDS:/tmp/creds.json" \
  fool-simclient --mode human --addr api:8080 --name Алиса --creds /tmp/creds.json
# второй раз — тот же файл, пароль подставится
printf '1\n0\n' | docker run -i --rm --network server_default \
  -v "$CREDS:/tmp/creds.json" \
  fool-simclient --mode human --addr api:8080 --name Алиса --creds /tmp/creds.json
```

В `logs/requests.log`: `IN/OUT Auth/Login` с `username`, без plaintext password.
