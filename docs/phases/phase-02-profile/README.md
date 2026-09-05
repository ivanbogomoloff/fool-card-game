# Phase 2 — Профиль

## Цель

Экран настроек: аватар, тема, колода, звуки. Имя профиля в настройках не редактируется — понадобится для сетевой игры позже.

## Wireframe

```mermaid
flowchart TB
    subgraph profileScreen [ProfileScreen]
        AvatarPicker[Avatar grid]
        ThemeChips[Theme and card theme]
        Sounds[Sounds switch]
        BtnSave[Sohranit]
    end
```

Полная версия: [navigation-and-screens.md](../../architecture/navigation-and-screens.md#profile)

## Файлы

```
ui/screens/profile/ProfileScreen.kt
presentation/profile/ProfileViewModel.kt
presentation/profile/ProfileUiState.kt
data/local/ProfileDataStore.kt
data/repository/ProfileRepository.kt
data/api/ProfileApi.kt
data/api/FakeProfileApi.kt
```

## Задачи

- [x] DataStore: `displayName` (зарезервировано под online), `avatarId`
- [x] `ProfileViewModel` — load/save
- [x] UI: grid аватаров (8 preset); имя профиля не в настройках
- [x] `ProfileApi` interface + `FakeProfileApi` (stub POST)
- [x] `ProfileRepository` — local first, затем fake API call
- [x] Кнопка «Сохранить» — persist + snackbar
- [x] Gradle: DataStore, kotlinx-serialization
- [x] `ProfileRepositoryTest`, `ProfileViewModelTest`

## DoD

- Имя и аватар сохраняются между перезапусками приложения
- POST уходит в FakeProfileApi (логируется)
- `ProfileRepositoryTest` и `ProfileViewModelTest` проходят

## Юнит-тесты

| Класс | Сценарии |
|-------|----------|
| `ProfileRepositoryTest` | save → read displayName/avatarId; fake API вызывается |
| `ProfileViewModelTest` | load on init; save updates state; ошибка API → error state |

Зависимости: `coroutines-test`, при необходимости Turbine.

См. [testing.md](../../architecture/testing.md).

## Следующий этап

[Phase 3 — UI игры](../phase-03-game-ui-debug/README.md)
