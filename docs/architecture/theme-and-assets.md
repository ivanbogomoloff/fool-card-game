# Тема и ассеты

## Цветовая палитра

Мягкие тона. Реализация в `ui/theme/Color.kt`.

| Роль | Название | Hex |
|------|----------|-----|
| Фон стола | Sage green | `#E8EDE3` |
| Поверхности / карты | Warm cream | `#F5F0E8` |
| Акцент (кнопки) | Dusty teal | `#6B9E9B` |
| Текст primary | Soft charcoal | `#3D3D3D` |
| Trump highlight | Muted gold | `#C9A962` |
| Ошибка / выход | Soft coral | `#D4847C` |

### Тёмная тема

| Роль | Hex |
|------|-----|
| Фон стола | `#1E2824` |
| Поверхности / карты | `#2F3834` |
| Текст на тёмном | `#E8EDE3` |

## Настройка темы

`ThemeMode` в `domain/model/ThemeMode.kt`, сохранение в DataStore (`theme_mode`):

- `SYSTEM` — по умолчанию, следует системной теме
- `LIGHT` — светлая
- `DARK` — тёмная

Применение через `AppTheme` в `MainActivity`; выбор в экране настроек (ProfileScreen).

## Карты

### MVP (Phase 3) — программный рендер

`Composable CardFace` на Canvas:

- Масть: ♠ ♥ ♦ ♣
- Ранг: 6–10, В, Д, К, Т
- Фон cream, рамка soft charcoal
- Красные масти: hearts, diamonds
- Рубашка: паттерн на warm cream

### Эволюция (Phase 4+)

Опционально заменить на drawable/webp:

- AI-генерация: 36 лиц + рубашка
- Naming: `card_spades_6.webp`, `card_back.webp`
- Экспорт 2x/3x в `res/drawable-*`

## Аватары

- 8–12 preset-иконок в `res/drawable/avatar_*.xml`
- Выбор в настройках профиля
- Phase 6: upload через API (опционально)

## Шрифты

- Material 3 default (system)
- Размер ранга на карте: крупный, жирный

## Compose Theme

Расширить `FoolCardGameTheme`:

```kotlin
// Дополнительные токены
val TableGreen = Color(0xFFE8EDE3)
val CardCream = Color(0xFFF5F0E8)
val AccentTeal = Color(0xFF6B9E9B)
val TrumpGold = Color(0xFFC9A962)
```
