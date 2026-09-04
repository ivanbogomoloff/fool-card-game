#!/usr/bin/env python3
"""Slice a 4x13 card atlas and a separate back image into WebP drawables."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

SUITS = ("HEARTS", "DIAMONDS", "CLUBS", "SPADES")
RANKS_BY_COLUMN = (
    "ACE",
    "TWO",
    "THREE",
    "FOUR",
    "FIVE",
    "SIX",
    "SEVEN",
    "EIGHT",
    "NINE",
    "TEN",
    "JACK",
    "QUEEN",
    "KING",
)
DURAK_COLUMNS = (0, 5, 6, 7, 8, 9, 10, 11, 12)
GRID_COLS = 13
GRID_ROWS = 4
WEBP_QUALITY = 90


WEBP_QUALITY = 90
EDGE_BACKGROUND_RATIO = 0.85


def is_background_pixel(pixel: tuple[int, ...]) -> bool:
    r, g, b = pixel[:3]
    if g > 60 and g > r + 15 and g > b + 15:
        return True
    if r < 40 and g < 40 and b < 40:
        return True
    return False


def trim_cell(cell: Image.Image) -> Image.Image:
    rgba = cell.convert("RGBA")
    width, height = rgba.size
    pixels = rgba.load()

    left, top, right, bottom = 0, 0, width, height

    while top < bottom:
        background_count = sum(
            1 for x in range(left, right) if is_background_pixel(pixels[x, top])
        )
        if background_count / (right - left) > EDGE_BACKGROUND_RATIO:
            top += 1
        else:
            break

    while bottom > top:
        background_count = sum(
            1 for x in range(left, right) if is_background_pixel(pixels[x, bottom - 1])
        )
        if background_count / (right - left) > EDGE_BACKGROUND_RATIO:
            bottom -= 1
        else:
            break

    while left < right:
        background_count = sum(
            1 for y in range(top, bottom) if is_background_pixel(pixels[left, y])
        )
        if background_count / (bottom - top) > EDGE_BACKGROUND_RATIO:
            left += 1
        else:
            break

    while right > left:
        background_count = sum(
            1 for y in range(top, bottom) if is_background_pixel(pixels[right - 1, y])
        )
        if background_count / (bottom - top) > EDGE_BACKGROUND_RATIO:
            right -= 1
        else:
            break

    if right <= left or bottom <= top:
        return cell

    return rgba.crop((left, top, right, bottom))


def asset_name(suit: str, rank: str) -> str:
    return f"card_{suit.lower()}_{rank.lower()}"


def slice_faces(faces_path: Path, output_dir: Path) -> tuple[int, int]:
    atlas = Image.open(faces_path)
    cell_w = atlas.width // GRID_COLS
    cell_h = atlas.height // GRID_ROWS
    reference_size: tuple[int, int] | None = None
    exported = 0

    for row, suit in enumerate(SUITS):
        for col in DURAK_COLUMNS:
            rank = RANKS_BY_COLUMN[col]
            left = col * cell_w
            top = row * cell_h
            cell = atlas.crop((left, top, left + cell_w, top + cell_h))
            trimmed = trim_cell(cell)

            if reference_size is None and rank == "SIX":
                reference_size = trimmed.size

            out_path = output_dir / f"{asset_name(suit, rank)}.webp"
            trimmed.save(out_path, format="WEBP", quality=WEBP_QUALITY)
            exported += 1
            print(f"Wrote {out_path.name} ({trimmed.width}x{trimmed.height})")

    if reference_size is None:
        raise RuntimeError("No cards were exported from the faces atlas.")

    return reference_size


def slice_back(back_path: Path, output_dir: Path, target_size: tuple[int, int]) -> None:
    back = Image.open(back_path).convert("RGBA")
    resized = back.resize(target_size, Image.Resampling.LANCZOS)
    out_path = output_dir / "card_back.webp"
    resized.save(out_path, format="WEBP", quality=WEBP_QUALITY)
    print(f"Wrote {out_path.name} ({resized.width}x{resized.height})")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Slice card atlas assets into Android WebP drawables.")
    parser.add_argument(
        "--faces",
        type=Path,
        required=True,
        help="Path to the 4x13 card faces atlas PNG.",
    )
    parser.add_argument(
        "--back",
        type=Path,
        required=True,
        help="Path to the card back PNG.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Output directory, e.g. app/src/main/res/drawable-nodpi/",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.faces.is_file():
        raise FileNotFoundError(f"Faces atlas not found: {args.faces}")
    if not args.back.is_file():
        raise FileNotFoundError(f"Card back not found: {args.back}")

    args.output.mkdir(parents=True, exist_ok=True)
    reference_size = slice_faces(args.faces, args.output)
    slice_back(args.back, args.output, reference_size)
    print(f"Done. Exported 36 faces + 1 back into {args.output}")


if __name__ == "__main__":
    main()
