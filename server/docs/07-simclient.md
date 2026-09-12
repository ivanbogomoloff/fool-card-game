# Симулятор клиента (simclient)

Терминальный клиент для отладки Go gRPC-сервера. Говорит **только** по protobuf/gRPC (HTTP/2), без знания серверной логики.

## Сборка бинарника

Через Docker (stage `bin` в `Dockerfile`) — артефакт появляется в `./bin/simclient` (рядом с `./bin/server`):

```bash
cd server
mkdir -p bin
DOCKER_BUILDKIT=1 docker build --target bin --output type=local,dest=./bin .
ls -la bin/simclient bin/server
```

Бинарник **linux** (`CGO_ENABLED=0`). Запуск на macOS/Windows из `./bin/` — только в Linux/контейнере/CI; на хосте macOS удобнее `go run` (см. ниже) или образ `--target simclient`.

Альтернатива без BuildKit output:

```bash
cd server
mkdir -p bin
docker build --target bin -t fool-bin .
cid=$(docker create fool-bin)
docker cp "$cid":/simclient ./bin/simclient
docker cp "$cid":/server ./bin/server
docker rm "$cid"
```

Запуск linux-бинарника к api в compose (Linux-хост):

```bash
./bin/simclient --mode human --addr 127.0.0.1:8080 --name Алиса
```

## Сборка образа и запуск

### Docker-образ (тот же Dockerfile, target `simclient`)

```bash
cd server
docker build -t fool-simclient --target simclient .

docker run -it --rm --network server_default \
  fool-simclient --mode human --addr api:8080 --name Алиса
```

Сервер:

```bash
docker compose up -d --build
```

### Локально (без бинарника в `bin/`)

```bash
cd server/src
go run ./cmd/simclient --mode human --name Алиса --addr 127.0.0.1:8080
go run ./cmd/simclient --mode bot --name Бот1 --quick --addr 127.0.0.1:8080
```

Флаги: `--name` / `--username`, `--password`, `--creds` (путь к JSON).

## Credentials

- Без `--creds`: пароль только **в памяти** процесса (повторный login в том же simclient работает; после выхода — нет).
- `--creds /path/credentials.json` или env `FOOLCARD_CREDS`: читать/писать файл (для docker смонтируйте volume).
- После Login в stdout: `credentials → memory` или путь файла; всегда печатается `password=…`.

## Human

Меню: `1) login` → регистрация или вход по паролю; вывод `account_id` и пароль.

## Bot

Авто: login (с credentials) → quick|join → ходы по `can_*`.

## Что работает (после username+password)

| RPC / поведение | Статус |
|-----------------|--------|
| `Auth.Login` | уникальный `username`; регистрация → password; повтор → password; ответ: token, username, account_id |
| Bearer | interceptor → `account_id` |
| Session Ping/Leave | ok |
| Create/Join/QuickMatch | этап 4: private Create/Join + Session QuickMatch/Subscribe |

### Проверка Login дважды

```bash
cd server
docker compose up -d --build api
docker build -t fool-simclient --target simclient .
printf '1\n1\n0\n' | docker run -i --rm --network server_default \
  fool-simclient --mode human --addr api:8080 --name АлисаТест
```

В одной сессии второй `login` берёт пароль из памяти. В `logs/requests.log`: первый `password_set=false`, второй `password_set=true`.
