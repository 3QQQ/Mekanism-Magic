"""Generate Source pipe textures from Mekanism's basic mechanical pipe.

The metal, glass, alpha, shading, and tier color are copied verbatim from
Mekanism. Only the green tier-accent mask is rearranged into an arcane ring
on the center and a repeating rune/chevron on the arms.
"""

from __future__ import annotations

import io
import zipfile
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
MEKANISM_JAR = ROOT / "libs" / "Mekanism-1.21.1-10.7.19.85.jar"
OUTPUT = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "mekanism_magic"
    / "textures"
    / "block"
    / "magic_source_pipe"
)

ACCENT = (62, 240, 144, 255)
NEUTRAL = (135, 135, 135, 255)

CENTER = "assets/mekanism/textures/block/models/multipart/basic_mechanical_pipe.png"
VERTICAL = (
    "assets/mekanism/textures/block/models/multipart/"
    "basic_mechanical_pipe_vertical.png"
)
OPAQUE_CENTER = (
    "assets/mekanism/textures/block/models/multipart/opaque/"
    "basic_mechanical_pipe.png"
)
OPAQUE_VERTICAL = (
    "assets/mekanism/textures/block/models/multipart/opaque/"
    "basic_mechanical_pipe_vertical.png"
)


def read_texture(archive: zipfile.ZipFile, name: str) -> Image.Image:
    return Image.open(io.BytesIO(archive.read(name))).convert("RGBA")


def replace_accent(image: Image.Image) -> None:
    for y in range(image.height):
        for x in range(image.width):
            if image.getpixel((x, y)) == ACCENT:
                image.putpixel((x, y), NEUTRAL)


def draw_center_rune(image: Image.Image) -> None:
    """Draw an eight-segment ring without touching the glass center."""
    replace_accent(image)
    segments = (
        (4, 2),
        (10, 2),
        (2, 4),
        (12, 4),
        (2, 10),
        (12, 10),
        (4, 12),
        (10, 12),
    )
    for start_x, start_y in segments:
        for y in range(start_y, start_y + 2):
            for x in range(start_x, start_x + 2):
                if image.getpixel((x, y))[3] != 0:
                    image.putpixel((x, y), ACCENT)


def draw_vertical_rune(image: Image.Image) -> None:
    """Turn Mekanism's straight tier stripes into a two-pixel chevron."""
    replace_accent(image)
    for band in range(8):
        y = band * 2
        left_x, right_x = (
            (2, 12) if band % 2 == 0 else (4, 10)
        )
        for start_x in (left_x, right_x):
            for offset_y in range(2):
                for offset_x in range(2):
                    x = start_x + offset_x
                    pixel_y = y + offset_y
                    if image.getpixel((x, pixel_y))[3] != 0:
                        image.putpixel((x, pixel_y), ACCENT)


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False)


def main() -> None:
    with zipfile.ZipFile(MEKANISM_JAR) as archive:
        center = read_texture(archive, CENTER)
        vertical = read_texture(archive, VERTICAL)
        opaque_center = read_texture(archive, OPAQUE_CENTER)
        opaque_vertical = read_texture(archive, OPAQUE_VERTICAL)

    draw_center_rune(center)
    draw_vertical_rune(vertical)
    draw_center_rune(opaque_center)
    draw_vertical_rune(opaque_vertical)

    save(center, OUTPUT / "magic_source_pipe.png")
    save(vertical, OUTPUT / "magic_source_pipe_vertical.png")
    save(opaque_center, OUTPUT / "opaque" / "magic_source_pipe.png")
    save(
        opaque_vertical,
        OUTPUT / "opaque" / "magic_source_pipe_vertical.png",
    )


if __name__ == "__main__":
    main()
