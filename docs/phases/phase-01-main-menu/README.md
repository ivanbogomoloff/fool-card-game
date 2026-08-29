# Phase 1 — Главный экран

## Цель

Главное меню с тремя пунктами: оффлайн-игра, онлайн-игра, настройки.

## Зависимости

- [Phase 0 — Login](../phase-00-login/README.md)

## Wireframe

```mermaid
flowchart TB
    subgraph mainMenu [MainMenuScreen]
        Title[DURAK]
        BtnOffline[Offline igra]
        BtnOnline[Online igra]
        BtnSettings[Nastrojki]
    end
```

Полная версия: [navigation-and-screens.md](../../architecture/navigation-and-screens.md#mainmenu)

## Файлы

```
ui/screens/main/MainMenuScreen.kt
ui/navigation/AppNavGraph.kt   (добавить destinations)
```

## Задачи

- [ ] `MainMenuScreen` с тремя кнопками
- [ ] Навигация: `offline/setup`, `online/lobby`, `profile`
- [ ] Заглушки экранов для offline/online/profile (пустые composable до Phase 2/4/5)
- [ ] Единый стиль кнопок (theme tokens)

## DoD

- Все три пункта меню открывают соответствующие экраны-заглушки
- Back с заглушек возвращает на main
- Тесты этапа не обязательны (логика минимальна)

## Юнит-тесты

Минимум или skip. При желании — smoke-тест навигационных констант из Phase 0.

См. [testing.md](../../architecture/testing.md).

## Следующий этап

[Phase 2 — Профиль](../phase-02-profile/README.md)
