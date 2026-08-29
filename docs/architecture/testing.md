# Юнит-тесты

Стратегия самопроверки на каждом этапе. Тесты пишутся **вместе с кодом этапа**, до перехода к следующему.

## Инструменты

| Библиотека | Назначение |
|------------|------------|
| JUnit 4 | Базовый runner |
| `kotlinx-coroutines-test` | `runTest`, `TestDispatcher` для suspend/Flow |
| Turbine | Тестирование `Flow` (`observeState`) |
| MockWebServer (OkHttp) | Phase 5–6: fake HTTP без живого API |

Зависимости добавляются в `gradle/libs.versions.toml` по мере необходимости (Phase 2+).

## Структура

```
app/src/test/java/com/example/foolcardgame/
├── domain/
│   ├── engine/          # GameEngine, Rules, Deck — приоритет
│   └── bot/
├── data/
│   ├── client/
│   ├── repository/
│   └── api/
└── presentation/
    └── */
```

- **Domain** — `src/test/` (JVM, без Android).
- **ViewModel / Repository** — JVM-тесты с `InstantTaskExecutorRule` не нужен (Compose ViewModel + coroutines-test).
- **Compose UI** — только при необходимости (`compose-ui-test`); основной упор на domain + data + ViewModel.

## Запуск

```bash
./gradlew testDebugUnitTest
```

Перед завершением этапа все тесты текущей phase должны быть **зелёными**.

## Принципы

1. **Domain first** — правила дурака покрываются unit-тестами без UI (Phase 4).
2. **GameClient** — local и remote тестируются через одинаковые сценарии (интерфейс + fake).
3. **Детерминизм** — фиксированный seed колоды в тестах engine.
4. **Не дублировать** — один сценарий «отбить + бито + добор», не 10 копий.
5. **Самопроверка** — каждый phase README содержит чеклист тестов; DoD этапа включает `./gradlew test`.

## Покрытие по этапам

| Phase | Что тестировать | Пример класса |
|-------|-----------------|---------------|
| 0 | Routes, NavGraph args (опционально) | `RoutesTest` |
| 1 | — | минимум или skip |
| 2 | ProfileRepository, serialization профиля | `ProfileRepositoryTest` |
| 3 | Маппинг `GameStateDto` → `UiState`, fake tick Flow | `GameUiStateMapperTest`, `DebugGameClientTest` |
| 4 | **GameEngine**, Rules, BotAI, LocalGameClient | `GameEngineTest`, `RulesTest`, `LocalGameClientTest` |
| 5 | DTO, RemoteGameClient + MockWebServer | `GameApiDtoTest`, `RemoteGameClientTest` |
| 6 | Auth interceptor, error mapping | `ApiErrorMapperTest` |

## Phase 4 — эталонные сценарии GameEngine

Обязательные тест-кейсы для `GameEngineTest`:

```kotlin
// Раздача
fun deal_gives6CardsEach_andTrumpFromDeckBottom()

// Атака / отбивка
fun playCard_attack_placesCardOnTable()
fun playCard_defense_beatsWithSameSuitHigher()
fun playCard_defense_beatsWithTrump()

// Подкидывание
fun addCard_throwsSameRank_whenAllowed()
fun addCard_rejects_whenRankNotOnTable()
fun addCard_respectsLimit_min6_andDefenderHandCount()

// Раунд
fun pass_defenderTakesAll_whenCannotBeat()
fun bito_clearsTable_andDrawsCards()

// Tick / ready
fun onTick_autoReadyBots_inLobby()
fun ready_marksPlayerReady()

// Конец
fun gameEnds_whenDeckEmpty_andOnePlayerHasCards()
```

## Phase 3 — Flow / tick

```kotlin
@Test
fun observeState_emitsImmediately_andOnInterval() = runTest {
    val client = DebugGameClient(testDispatcher)
    client.observeState("debug", pollIntervalMs = 100).test {
        awaitItem() // initial
        advanceTimeBy(100)
        awaitItem() // tick
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun playCard_triggersImmediateEmit_beforeNextTick() = runTest { ... }
```

## Phase 5 — MockWebServer

```kotlin
@Test
fun getState_parsesPlayersReadyAndConnected() {
    server.enqueue(MockResponse().setBody(jsonState))
    val state = client.getState(sessionId)
    assertTrue(state.players.all { it.isReady })
}
```

## Связанные документы

- [Обзор архитектуры](overview.md)
- [GameClient](game-client.md)
- Phase README — секция «Юнит-тесты» в каждом этапе
