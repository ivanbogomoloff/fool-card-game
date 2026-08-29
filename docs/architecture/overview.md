# Архитектура приложения

## Слои MVVM

```mermaid
flowchart TB
    subgraph ui [UI Layer - Compose]
        Screens[Screens]
        Components[Game UI Components]
    end

    subgraph presentation [Presentation - MVVM]
        ViewModels[ViewModels]
        UiState[UiState / UiEvent]
    end

    subgraph domain [Domain - Pure Kotlin]
        GameEngine[GameEngine]
        BotAI[BotAI]
        Models[Card / Player / GameState]
    end

    subgraph data [Data Layer]
        GameClient[GameClient Interface]
        LocalGameClient[LocalGameClient]
        RemoteGameClient[RemoteGameClient]
        ProfileRepo[ProfileRepository]
        ApiService[Retrofit ApiService]
    end

    Screens --> ViewModels
    ViewModels --> GameClient
    ViewModels --> ProfileRepo
    LocalGameClient --> GameEngine
    RemoteGameClient --> ApiService
    GameEngine --> BotAI
```

## Принципы

- **UI** — только Compose, без бизнес-логики.
- **ViewModel** — держит `UiState`, вызывает `GameClient` / repositories.
- **Domain** — чистый Kotlin, без Android-зависимостей.
- **Data** — реализации клиентов, API, локальное хранение.

## Пакетная структура

```
com.example.foolcardgame/
├── ui/
│   ├── navigation/          # NavHost, routes
│   ├── theme/               # Мягкая палитра
│   ├── components/          # CardView, PlayerAvatar, ActionBar...
│   └── screens/
│       ├── login/
│       ├── main/
│       ├── profile/
│       ├── offline/
│       ├── online/
│       └── game/
├── presentation/            # ViewModels + UiState
├── domain/
│   ├── model/
│   ├── engine/              # Логика подкидного дурака
│   └── bot/
├── data/
│   ├── client/              # GameClient, Local*, Remote*
│   ├── api/                 # Retrofit interfaces, DTO
│   ├── repository/
│   └── local/               # DataStore для профиля
└── di/                      # Фабрика клиентов (или Hilt позже)
```

## Зависимости между слоями

```mermaid
flowchart LR
    ui --> presentation
    presentation --> data
    presentation --> domain
    data --> domain
    data --> api[External API]
```

- Domain **не зависит** от UI, Android, Retrofit.
- ViewModel **не зависит** от Compose напрямую (только через state).

## Связанные документы

- [Навигация и экраны](navigation-and-screens.md)
- [GameClient](game-client.md)
- [API-контракты](api-contracts.md)
- [Юнит-тесты](testing.md)
