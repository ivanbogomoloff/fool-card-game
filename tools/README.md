# Tools

## slice_card_assets.py

Нарезает atlas лиц карт (4×13) и отдельный PNG рубашки в WebP для `res/drawable-nodpi/`.

### Требования

- Python 3.10+
- Pillow: `pip install Pillow`

### Запуск

```bash
python3 tools/slice_card_assets.py \
  --faces ~/Pictures/fool-card-game/card-setup.png \
  --back ~/Pictures/fool-card-game/card-deck.png \
  --output app/src/main/res/drawable-nodpi/
```

Экспортируются 36 карт (6–Туз) и `card_back.webp`. Исходные PNG в репозиторий не добавлять.
