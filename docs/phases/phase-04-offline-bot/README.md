# Phase 4 — Оффлайн с ботом

## Цель

Полная игра против 1–3 ботов. Логика в чистом Kotlin (`GameEngine`), UI через `LocalGameClient` как «локальный API».

## Зависимости

- [Phase 3 — Game UI](../phase-03-game-ui-debug/README.md)

## Wireframe

```mermaid
flowchart TB
    subgraph offlineSetup [OfflineSetupScreen]
        BotCount[1 / 2 / 3 bota]
        BtnStart[Zapustit]
    end
    BotCount --> BtnStart
    BtnStart --> GameSession
```

Полная версия: [navigation-and-screens.md](../../architecture/navigation-and-screens.md#offlinesetup)

## Файлы

```
domain/model/Card.kt, Suit.kt, Rank.kt, Player.kt, GameState.kt
domain/engine/GameEngine.kt, Deck.kt, Rules.kt
domain/engine/GameEngine.onTick()
domain/bot/BotAI.kt
data/client/LocalGameClient.kt
ui/screens/offline/OfflineSetupScreen.kt
presentation/offline/OfflineSetupViewModel.kt
presentation/game/GameViewModel.kt  (подключить LocalGameClient)
```

## Задачи

- [ ] Domain models (36 карт, игроки, стол)
- [ ] `GameEngine`: раздача, playCard, addCard, pass, bito, добор, конец
- [ ] `GameEngine.onTick()`: auto-ready ботов
- [ ] `BotAI`: атака, отбивка, подкидывание
- [ ] `LocalGameClient` implements `GameClient`
- [ ] `OfflineSetupScreen`: выбор 1–3 ботов → createSession → game
- [ ] `GameViewModel`: collect `observeState`, `leaveSession` in onCleared
- [ ] Вернуть `startDestination = login`
- [ ] `RulesTest`, `GameEngineTest`, `LocalGameClientTest`, `BotAITest`

## DoD

- Полная партия 1 человек vs 1–3 бота
- Tick обновляет UI без ручного refresh
- Все actions (playCard, addCard, pass, bito) работают
- **GameEngineTest** покрывает обязательные сценарии из [testing.md](../../architecture/testing.md)

## Юнит-тесты

**Критический этап для тестов.** Фиксированный `seed` колоды.

| Класс | Сценарии |
|-------|----------|
| `RulesTest` | карта бьёт карту (масть, козырь) |
| `DeckTest` | 36 карт, shuffle детерминирован с seed |
| `GameEngineTest` | раздача, атака, отбивка, addCard, pass, bito, добор, конец партии, onTick auto-ready |
| `BotAITest` | бот выбирает легальный ход |
| `LocalGameClientTest` | createSession + playCard + getState; observeState после action |

См. полный список кейсов: [testing.md](../../architecture/testing.md#phase-4--эталонные-сценарии-gameengine).

## Следующий этап

[Phase 5 — Online](../phase-05-online/README.md)
