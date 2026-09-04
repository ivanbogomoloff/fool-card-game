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

### Темы колоды (`CardTheme`)

`CardTheme` в `domain/model/CardTheme.kt`, сохранение в DataStore (`card_theme`):

- `ILLUSTRATED` — по умолчанию, WebP из atlas (`IllustratedCardFace`)
- `MINIMAL` — программный рендер символов (`MinimalCardFace`)

Выбор в ProfileScreen (секция «Колода»). Тема пробрасывается через `LocalCardTheme` в `AppTheme`; все call-site'ы используют единый `CardFace`.

### Illustrated — drawable/WebP

- 36 лиц + рубашка в `res/drawable-nodpi/`
- Naming: `card_{suit}_{rank}.webp` (совпадает с `Card.id`, напр. `HEARTS_ACE` → `card_hearts_ace.webp`)
- Рубашка: `card_back.webp`
- Маппер: `CardDrawableMapper.kt` (`cardFaceDrawableRes`, `cardBackDrawableRes`)
- Имена ассетов (JVM-тест): `cardAssetName()` в `CardAssetNames.kt`

#### Pipeline нарезки

Исходники (вне репозитория) → скрипт `tools/slice_card_assets.py`:

```bash
python3 tools/slice_card_assets.py \
  --faces ~/Pictures/fool-card-game/card-setup.png \
  --back ~/Pictures/fool-card-game/card-deck.png \
  --output app/src/main/res/drawable-nodpi/
```

Atlas: 4×13 (HEARTS, DIAMONDS, CLUBS, SPADES × A..K). Экспортируются колонки 6–K и Ace (36 карт).

### Minimal — программный рендер

`MinimalCardFace`:

- Масть: ♠ ♥ ♦ ♣
- Ранг: 6–10, В, Д, К, Т
- Фон cream, рамка soft charcoal
- Красные масти: hearts, diamonds
- Рубашка: градиент AccentTeal

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
