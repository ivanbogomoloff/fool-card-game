# Навигация и экраны

Полное описание маршрутов, wireframes и layout экрана игры.

## App NavGraph

Стартовый экран: **`main`**. `LoginScreen` открывается при входе в онлайн без сессии (не при запуске приложения).

```mermaid
flowchart LR
    MainMenu --> OfflineSetup
    MainMenu -->|no auth| Login
    MainMenu -->|auth ok| OnlineLobby
    Login --> OnlineLobby
    MainMenu --> Settings
    OfflineSetup --> GameSession
    OnlineLobby -->|quick match| GameSession
    OnlineLobby -->|friends create or join| WaitingRoom
    OnlineLobby --> JoinPrivate
    JoinPrivate --> WaitingRoom
    WaitingRoom -->|host start| GameSession
    GameSession -->|Back confirm| MainMenu
```

## Online Flow

```mermaid
flowchart TB
    subgraph onlineFlow [Online Flow]
        LoginGate[Login if needed]
        OnlineLobby[OnlineLobby local profile]
        QuickMatch[Bystraya igra poll]
        JoinPrivate[JoinPrivate enter code]
        WaitingRoom[WaitingRoom room poll kick start]
        LoginGate --> OnlineLobby
        OnlineLobby --> QuickMatch
        OnlineLobby --> JoinPrivate
        OnlineLobby -->|create| WaitingRoom
        JoinPrivate --> WaitingRoom
        QuickMatch --> GameSession
        WaitingRoom -->|host start| GameSession
    end
```

## Таблица маршрутов

| Экран | Маршрут | Аргументы | Back behaviour |
|-------|---------|-----------|----------------|
| Вход | `login` | optional returnTo | pop → main (или returnTo) |
| Главный | `main` | — | exit app |
| Оффлайн-настройка | `offline/setup` | — | pop → main |
| Лобби | `online/lobby` | — | pop → main |
| Создание игры | `online/create` | — | pop → lobby |
| Вход по коду | `online/join` | — | pop → lobby |
| Ожидание | `online/waiting/{sessionId}` | sessionId | pop → lobby + leave |
| Настройки | `profile` | — | pop → main |
| Игра | `game/{sessionId}` | sessionId | LeaveGameDialog → main |
| Debug игра | `game/debug` | — | pop (Phase 3 only) |

> **Phase 3 override:** `startDestination = game/debug` на время разработки UI.

## Back navigation (игра)

```mermaid
flowchart LR
    GameSession -->|Back press| LeaveDialog[LeaveGameDialog]
    LeaveDialog -->|Confirm| MainMenu
    LeaveDialog -->|Cancel| GameSession
    LeaveDialog -->|Confirm| leaveSession[GameClient.leaveSession]
```

---

## Wireframes

### Login

```mermaid
flowchart TB
    subgraph loginScreen [LoginScreen]
        Logo[App Logo / Title]
        BtnLogin[Button Vojti]
    end
    Logo --> BtnLogin
```

### MainMenu

```mermaid
flowchart TB
    subgraph mainMenu [MainMenuScreen]
        Title[DURAK]
        BtnOffline[Offline igra]
        BtnOnline[Online igra]
        BtnSettings[Nastrojki]
    end
    Title --> BtnOffline
    Title --> BtnOnline
    Title --> BtnSettings
```

### Profile

```mermaid
flowchart TB
    subgraph profileScreen [ProfileScreen Nastrojki]
        ThemeChips[Theme and card theme]
        Sounds[Sounds switch]
    end
```

### OfflineSetup

```mermaid
flowchart TB
    subgraph offlineSetup [OfflineSetupScreen]
        BotCount[Radio 1 / 2 / 3 bota]
        BtnStart[Button Zapustit]
    end
    BotCount --> BtnStart
```

### OnlineLobby

```mermaid
flowchart TB
    subgraph onlineLobby [OnlineLobbyScreen]
        ProfileLocal[Avatar plus Imya local]
        BtnQuick[Bystraya igra]
        BtnFriends[Igra s druzyami expand]
        BtnCreate[Sozdat igru]
        BtnJoin[Voyti po kodu]
    end
    ProfileLocal --> BtnQuick
    ProfileLocal --> BtnFriends
    BtnFriends --> BtnCreate
    BtnFriends --> BtnJoin
```

### JoinPrivate

```mermaid
flowchart TB
    subgraph joinPrivate [JoinPrivateScreen]
        CodeField[TextField kod igry]
        BtnJoin[Button Vojti]
    end
    CodeField --> BtnJoin
```

### WaitingRoom

```mermaid
flowchart TB
    subgraph waitingRoom [WaitingRoomScreen]
        AccessCode[Kod dostupa]
        WaitingDots[WaitingDots anim]
        PlayerList[List igrokov Kick u hosta]
        BtnStart[Nachat igru host]
    end
    AccessCode --> PlayerList --> BtnStart
```

---

## Game Session Layout

```mermaid
flowchart TB
    subgraph gameScreen [GameSessionScreen]
        Chrome[Home and Settings overlay]
        subgraph table [Felt table]
            Opponents[Opponents left top right]
            Deck[DeckAndTrumpView top center]
            Pairs[TableCardsView center]
            Actions[GameActionBar indicator plus button]
        end
        Hand[PlayerHandView fan]
    end
    Chrome --> table --> Hand
```

### ASCII layout

```
┌─────────────────────────────────────┐
│ [дом]                         [⚙]   │
│  Бот 1        Бот 2         Бот 3   │
│  веер         веер          веер    │
│              [колода 12]            │
│              [козырь]               │
│         пары атаки/защиты           │
│            · · ·  щит               │
│                          [ Беру ]   │
│     рука игрока веером              │
└─────────────────────────────────────┘
```

## Component Tree (игра)

```mermaid
flowchart TB
    GameSessionScreen --> GameTableLayout
    GameTableLayout --> OpponentsRow
    GameTableLayout --> DeckAndTrumpView
    GameTableLayout --> TableCardsView
    GameTableLayout --> GameActionBar
    GameTableLayout --> PlayerHandView
    GameSessionScreen --> LeaveGameDialog
    OpponentsRow --> OpponentSeat
    PlayerHandView --> CardFace
    TableCardsView --> CardPair
    CardPair --> CardFace
```

## UI по GamePhase

| Phase | Видимые элементы |
|-------|------------------|
| `LOBBY_WAITING` | PlayerList, кнопка «Готов», индикаторы ready/connected |
| `IN_PROGRESS` | Стол, колода, рука, action bar |
| `FINISHED` | Результат партии, кнопка «В меню» |

## Связанные phase-документы

- [Phase 0 — Login](../phases/phase-00-login/README.md)
- [Phase 1 — Main Menu](../phases/phase-01-main-menu/README.md)
- [Phase 3 — Game UI](../phases/phase-03-game-ui-debug/README.md)
- [Phase 5 — Online](../phases/phase-05-online/README.md)
