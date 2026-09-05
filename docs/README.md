# Подкидной дурак — ТЗ и план реализации

Android-клиент карточной игры «Подкидной дурак» на Kotlin + Jetpack Compose.

## Стек

| Категория | Технологии |
|-----------|------------|
| Язык | Kotlin |
| UI | Jetpack Compose, Compose Animation |
| Архитектура | MVVM |
| Сеть | Retrofit, OkHttp, Kotlin Coroutines |
| Сериализация | Kotlinx Serialization |
| Мин. версия | Android 13 (API 33) |

## Документация

### Архитектура

- [Обзор архитектуры](architecture/overview.md) — слои, пакеты, MVVM
- [Навигация и экраны](architecture/navigation-and-screens.md) — все диаграммы экранов и маршрутов
- [GameClient](architecture/game-client.md) — абстракция игровой сессии, tick, actions
- [API-контракты](architecture/api-contracts.md) — REST endpoints (заглушки)
- [Тема и ассеты](architecture/theme-and-assets.md) — цвета, карты, аватары
- [Юнит-тесты](architecture/testing.md) — стратегия самопроверки по этапам

### Правила игры

- [Подкидной дурак](game-rules/podkidnoy-durak.md)

### Этапы реализации

| # | Этап | Папка |
|---|------|-------|
| 0 | Экран входа (заглушка) | [phase-00-login](phases/phase-00-login/) |
| 1 | Главный экран | [phase-01-main-menu](phases/phase-01-main-menu/) |
| 2 | Профиль | [phase-02-profile](phases/phase-02-profile/) |
| 3 | UI игры + debug | [phase-03-game-ui-debug](phases/phase-03-game-ui-debug/) |
| 4 | Оффлайн с ботом | [phase-04-offline-bot](phases/phase-04-offline-bot/) |
| 5 | Сетевая игра | [phase-05-online](phases/phase-05-online/) |
| 6 | Подключение API | [phase-06-api-integration](phases/phase-06-api-integration/) |

## Порядок работы

1. Двигаться строго по этапам 0 → 6.
2. **Сейчас:** Phase 4 (оффлайн с ботами) реализован и играбелен; старт приложения — `main`.
3. Не начинать Phase 5 до сохранения DoD Phase 4 (рабочий `LocalGameClient` / `GameEngine`).
4. UI на русском языке.
5. На каждом этапе писать **юнит-тесты** ([testing.md](architecture/testing.md)); `./gradlew testDebugUnitTest` — зелёный перед переходом дальше.
6. `LoginScreen` сохранён для будущей сетевой авторизации; имя аккаунта не редактируется в настройках.

## Глоссарий

| Термин | Описание |
|--------|----------|
| GameClient | Абстракция игровой сессии (local / remote) |
| Tick | Периодический опрос состояния игры |
| Подкидывание | Добавление карты того же достoинства на стол (`addCard`) |
| Бито | Завершение успешно отбитого раунда |

## Беклог - идеи

- Случайный легальный ход (автоход) за оплату например 500 рублей = 500 автоходов.
