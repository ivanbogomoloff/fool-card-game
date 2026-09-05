# Навигация и экраны

Полное описание маршрутов, wireframes и layout экрана игры.

## App NavGraph

Стартовый экран: **`main`**. Маршрут `login` / `LoginScreen` сохранён для будущей сетевой фазы и при запуске не открывается.

```mermaid
flowchart LR
    MainMenu --> OfflineSetup
    MainMenu --> OnlineLobby
    MainMenu --> Profile
    OfflineSetup --> GameSession
    OnlineLobby --> CreateGame
    OnlineLobby --> JoinPrivate
    CreateGame --> WaitingRoom
    JoinPrivate --> WaitingRoom
    WaitingRoom -->|all ready| GameSession
    GameSession -->|Back confirm| MainMenu
```

## Online Flow

```mermaid
flowchart TB
    subgraph onlineFlow [Online Flow]
        OnlineLobby[OnlineLobby list plus buttons]
        CreateGame[CreateGame players private flag]
        JoinPrivate[JoinPrivate enter code]
        WaitingRoom[WaitingRoom ready indicators tick]
        OnlineLobby --> CreateGame
        OnlineLobby --> JoinPrivate
        CreateGame --> WaitingRoom
        JoinPrivate --> WaitingRoom
    end
```

## Таблица маршрутов

| Экран | Маршрут | Аргументы | Back behaviour |
|-------|---------|-----------|----------------|
| Вход | `login` | — | exit app |
| Главный | `main` | — | exit app |
| Оффлайн-настройка | `offline/setup` | — | pop → main |
| Лобби | `online/lobby` | — | pop → main |
| Создание игры | `online/create` | — | pop → lobby |
| Вход по коду | `online/join` | — | pop → lobby |
| Ожидание | `online/waiting/{sessionId}` | sessionId | pop → lobby + leave |
| Профиль | `profile` | — | pop → main |
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
    subgraph profileScreen [ProfileScreen]
        AvatarPicker[Avatar grid picker]
        NameField[TextField Imya]
        BtnSave[Button Sohranit]
    end
    AvatarPicker --> NameField --> BtnSave
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
        GameList[LazyColumn spisok igr stub]
        BtnCreate[Button Sozdat igru]
        BtnJoin[Button Vojti po kodu]
    end
    GameList --> BtnCreate
    GameList --> BtnJoin
```

### CreateGame

```mermaid
flowchart TB
    subgraph createGame [CreateGameScreen]
        PlayerCount[Slider igrokov 2-4]
        PrivateToggle[Switch privatnaya igra]
        BtnCreate[Button Sozdat]
    end
    PlayerCount --> PrivateToggle --> BtnCreate
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
    subgraph waitingRoom [WaitingRoom LOBBY_WAITING]
        PlayerList[List igrokov isReady isConnected]
        BtnReady[Button Gotov]
        TickIndicator[Tick obnovlenie sostoyaniya]
    end
    PlayerList --> BtnReady
    TickIndicator --> PlayerList
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
