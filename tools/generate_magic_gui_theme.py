"""Generate the complete Mekanism Magic GUI skin from Mekanism assets.

The generated files live in the mekanism_magic namespace. A client-only
resource remapper selects them only while one of this addon's screens is open,
so vanilla Mekanism and other addons retain their own appearance.
"""

from __future__ import annotations

import colorsys
import io
import re
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DARK = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "mekanism_magic"
    / "gui_theme"
)
OUTPUT_LIGHT = OUTPUT_DARK.parent / "gui_theme_light"
PREVIEW_DARK = ROOT / "tmp" / "gui-theme-assets-preview.png"
PREVIEW_LIGHT = ROOT / "tmp" / "gui-theme-light-assets-preview.png"

DARK_NEUTRAL_STOPS = (
    (0.00, (3, 5, 9)),
    (0.22, (7, 11, 18)),
    (0.55, (18, 27, 40)),
    (0.74, (37, 49, 66)),
    (1.00, (100, 120, 145)),
)

LIGHT_NEUTRAL_STOPS = (
    (0.00, (35, 44, 58)),
    (0.18, (68, 81, 99)),
    (0.40, (137, 152, 170)),
    (0.60, (188, 200, 213)),
    (0.78, (223, 231, 239)),
    (1.00, (250, 252, 255)),
)


def mekanism_jar() -> Path:
    candidates = list((ROOT / "libs").glob("Mekanism-*.jar"))
    if not candidates:
        raise FileNotFoundError("No Mekanism jar found in libs/")

    def version(path: Path) -> tuple[int, ...]:
        numbers = re.findall(r"\d+", path.stem)
        return tuple(int(number) for number in numbers)

    return max(candidates, key=version)


def interpolate_neutral(
    value: float, stops: tuple[tuple[float, tuple[int, int, int]], ...]
) -> tuple[int, int, int]:
    for index in range(1, len(stops)):
        upper_value, upper_color = stops[index]
        lower_value, lower_color = stops[index - 1]
        if value <= upper_value:
            amount = (value - lower_value) / (upper_value - lower_value)
            return tuple(
                round(lower + (upper - lower) * amount)
                for lower, upper in zip(lower_color, upper_color)
            )
    return stops[-1][1]


def theme_pixel(
    pixel: tuple[int, int, int, int], light: bool,
    foreground_neutral: bool = False,
) -> tuple[int, int, int, int]:
    red, green, blue, alpha = pixel
    if alpha == 0:
        return red, green, blue, alpha

    red_unit = red / 255
    green_unit = green / 255
    blue_unit = blue / 255
    hue, saturation, value = colorsys.rgb_to_hsv(
        red_unit, green_unit, blue_unit
    )
    luminance = 0.2126 * red_unit + 0.7152 * green_unit + 0.0722 * blue_unit

    if saturation <= 0.14:
        stops = LIGHT_NEUTRAL_STOPS if light else DARK_NEUTRAL_STOPS
        themed = interpolate_neutral(luminance, stops)
        if foreground_neutral and not light:
            # Mekanism draws many controls as near-black glyphs on a
            # transparent canvas. They work on its pale GUI, but disappear
            # when copied verbatim onto our dark work deck. Keep structural
            # textures dark while giving icon-like assets a readable floor.
            icon_floor = (108, 126, 150)
            icon_ceiling = (224, 234, 248)
            themed = tuple(
                round(floor + (ceiling - floor) * luminance)
                for floor, ceiling in zip(icon_floor, icon_ceiling)
            )
        return themed[0], themed[1], themed[2], alpha

    # Mekanism's energy green becomes the Source cyan used throughout this
    # addon's bars, active buttons, meters, and information tabs.
    if 0.24 <= hue <= 0.48 and green > red * 1.35 and green > blue * 1.08:
        hue = 0.49
        saturation = max(0.58, min(0.82, saturation))
        if light:
            value = max(0.48, min(0.78, 0.34 + value * 0.50))
        else:
            value = max(0.48, min(0.92, 0.30 + value * 0.66))
    elif 0.10 <= hue <= 0.20:
        # Recipe progress remains semantically warm, but uses the theme's
        # restrained ritual-gold instead of saturated yellow.
        hue = 0.115
        saturation = max(0.48, min(0.74, saturation))
        value = max(0.50, min(0.82 if light else 0.92, value))
    else:
        # Preserve semantic input/output/error hues while taming highlights.
        saturation = min(0.88, saturation * 1.04)
        if light:
            value = min(0.82, max(0.30, value * 0.88))
        else:
            value = min(0.92, max(0.18, value * 0.94))

    themed_float = colorsys.hsv_to_rgb(hue, saturation, value)
    return tuple(round(channel * 255) for channel in themed_float) + (alpha,)


def is_foreground_asset(image: Image.Image) -> bool:
    """Detect compact transparent sprites whose neutral pixels are glyphs."""
    pixels = list(image.get_flattened_data())
    if max(image.size) > 64:
        return False
    transparent = sum(pixel[3] < 16 for pixel in pixels)
    if transparent / len(pixels) < 0.20:
        return False
    opaque = [pixel for pixel in pixels if pixel[3] >= 128]
    if not opaque:
        return False
    neutral = sum(
        max(pixel[:3]) - min(pixel[:3]) <= 36 for pixel in opaque
    )
    return neutral / len(opaque) >= 0.55


def themed_image(source: bytes, light: bool) -> Image.Image:
    image = Image.open(io.BytesIO(source)).convert("RGBA")
    foreground_neutral = is_foreground_asset(image)
    # Pillow 14 removes Image.getdata(); get_flattened_data() is the direct,
    # deterministic replacement and keeps palette generation warning-free.
    image.putdata([
        theme_pixel(pixel, light, foreground_neutral)
        for pixel in image.get_flattened_data()
    ])
    return image


def write_preview(
    samples: dict[str, Image.Image], output: Path, light: bool
) -> None:
    selected = [
        "slot/normal.png",
        "slot/input.png",
        "slot/output.png",
        "slot/power.png",
        "progress/bar.png",
        "progress/down.png",
        "holder_left.png",
        "tabs/energy_info_fe.png",
        "button.png",
        "inner_screen.png",
    ]
    cards: list[tuple[str, Image.Image]] = []
    for name in selected:
        image = samples.get(name)
        if image is None:
            continue
        maximum = 128 if image.width >= 64 or image.height >= 64 else 80
        scale = max(1, min(maximum // image.width, maximum // image.height))
        cards.append((name, image.resize(
            (image.width * scale, image.height * scale), Image.Resampling.NEAREST
        )))

    width = 640
    row_height = 150
    background = (232, 238, 245, 255) if light else (10, 14, 22, 255)
    label = (28, 39, 56, 255) if light else (221, 231, 245, 255)
    preview = Image.new("RGBA", (width, row_height * 2), background)
    draw = ImageDraw.Draw(preview)
    for index, (name, image) in enumerate(cards[:10]):
        column = index % 5
        row = index // 5
        left = column * 128 + (128 - image.width) // 2
        top = row * row_height + 8
        preview.alpha_composite(image, (left, top))
        draw.text((column * 128 + 5, row * row_height + 130), name,
                  fill=label)
    output.parent.mkdir(parents=True, exist_ok=True)
    preview.save(output)


def generate_scheme(
    archive: zipfile.ZipFile, entries: list[str], output_root: Path,
    light: bool
) -> dict[str, Image.Image]:
    samples: dict[str, Image.Image] = {}
    for entry in entries:
        relative = entry.removeprefix("assets/mekanism/gui/")
        output = output_root / relative
        output.parent.mkdir(parents=True, exist_ok=True)
        image = themed_image(archive.read(entry), light)
        image.save(output)
        samples[relative] = image
    return samples


def main() -> int:
    jar = mekanism_jar()
    with zipfile.ZipFile(jar) as archive:
        entries = sorted(
            name for name in archive.namelist()
            if name.startswith("assets/mekanism/gui/") and name.endswith(".png")
        )
        dark_samples = generate_scheme(
            archive, entries, OUTPUT_DARK, light=False
        )
        light_samples = generate_scheme(
            archive, entries, OUTPUT_LIGHT, light=True
        )
    write_preview(dark_samples, PREVIEW_DARK, light=False)
    write_preview(light_samples, PREVIEW_LIGHT, light=True)
    print(
        f"Generated {len(entries)} dark and {len(entries)} light GUI "
        f"textures from {jar.name}"
    )
    print(f"Dark preview: {PREVIEW_DARK}")
    print(f"Light preview: {PREVIEW_LIGHT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
