"""Generate smooth, tintable machine-animation sprites.

The block-entity renderer uses these complete translucent motifs instead of
assembling circles and energy fields from dozens of tiny cuboids. They use a
64-unit logical drawing grid, are rasterised at 512px, and are downsampled to
256px so close first-person views retain smooth curves without changing the
renderer geometry or animation layering.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = (
    ROOT / "src" / "main" / "resources" / "assets"
    / "mekanism_magic" / "textures" / "block" / "animation"
)
LOGICAL_SIZE = 64
SCALE = 16
OUTPUT_SIZE = 512
CANVAS = LOGICAL_SIZE * SCALE


def point(center: float, radius: float, angle: float) -> tuple[float, float]:
    radians = math.radians(angle)
    return (
        (center + math.cos(radians) * radius) * SCALE,
        (center + math.sin(radians) * radius) * SCALE,
    )


def glow_composite(
    lines: Image.Image, radius: float = 1.15, strength: float = 0.24
) -> Image.Image:
    """Add a narrow halo without softening the opaque line artwork."""
    alpha = lines.getchannel("A")
    glow = Image.new("RGBA", lines.size, (255, 255, 255, 0))
    glow.putalpha(alpha.filter(ImageFilter.GaussianBlur(radius * SCALE)))
    glow = glow.point(lambda value: value * strength if value else 0)
    return Image.alpha_composite(glow, lines)


def finish(
    image: Image.Image,
    name: str,
    alpha_cutoff: int = 12,
    output_size: int = OUTPUT_SIZE,
) -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    finished = image.resize(
        (output_size, output_size), Image.Resampling.LANCZOS
    )
    # Gaussian glow and Lanczos resampling can leave alpha values of 1-3 over
    # the entire sprite. Stacking several translucent fields then makes
    # the nominally transparent sprite rectangle visible as a coloured block.
    # Drop only that invisible tail; the antialiased motif edge remains smooth.
    alpha = finished.getchannel("A").point(
        lambda value: 0 if value < alpha_cutoff else value
    )
    finished.putalpha(alpha)
    finished.save(OUTPUT / name)


def arcane_disc() -> None:
    image = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    draw = ImageDraw.Draw(image)
    c = CANVAS / 2
    white = (255, 255, 255, 255)
    soft = (255, 255, 255, 205)

    for radius, width, color in ((27, 1.35, white), (22, .70, soft),
                                 (12.5, .95, white)):
        box = tuple((value * SCALE for value in
                     (32 - radius, 32 - radius, 32 + radius, 32 + radius)))
        draw.ellipse(box, outline=color, width=max(1, round(width * SCALE)))

    draw.arc((8*SCALE, 8*SCALE, 56*SCALE, 56*SCALE), 18, 108,
             fill=white, width=round(1.4*SCALE))
    draw.arc((8*SCALE, 8*SCALE, 56*SCALE, 56*SCALE), 142, 232,
             fill=white, width=round(1.4*SCALE))
    draw.arc((8*SCALE, 8*SCALE, 56*SCALE, 56*SCALE), 266, 346,
             fill=white, width=round(1.4*SCALE))

    hexagon = [point(32, 15.5, angle) for angle in range(-90, 270, 60)]
    draw.line(hexagon + [hexagon[0]], fill=soft,
              width=round(.9*SCALE), joint="curve")
    triangle = [point(32, 9, angle) for angle in (-90, 30, 150)]
    draw.line(triangle + [triangle[0]], fill=white,
              width=round(1.1*SCALE), joint="curve")

    for angle in range(0, 360, 30):
        inner = point(32, 24.5 if angle % 60 else 23, angle)
        outer = point(32, 29 if angle % 60 else 30.5, angle)
        draw.line((inner, outer), fill=white,
                  width=round((.75 if angle % 60 else 1.2) * SCALE))
    for angle in range(0, 360, 60):
        x, y = point(32, 18.5, angle + 30)
        r = 1.0 * SCALE
        draw.ellipse((x-r, y-r, x+r, y+r), fill=white)

    finish(image, "arcane_disc.png", alpha_cutoff=1)


def arcane_disc_fine() -> None:
    """Restrained tracing glyph for large, model-authored front circles.

    It deliberately carries fewer secondary strokes and almost no bloom. The
    factory already has a detailed gold/cyan static circle underneath, so this
    sprite should animate that artwork instead of painting another opaque
    heavy seal over it.
    """
    image = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    draw = ImageDraw.Draw(image)
    white = (255, 255, 255, 255)
    soft = (255, 255, 255, 225)

    for radius, width, color in (
        (27, 1.05, white),
        (23.2, 0.55, soft),
        (13.2, 0.75, white),
    ):
        draw.ellipse(
            tuple(value * SCALE for value in (
                32-radius, 32-radius, 32+radius, 32+radius
            )),
            outline=color,
            width=max(1, round(width * SCALE)),
        )

    # Three separated sweeps make rotation readable without forming a solid
    # band when the cyan and violet layers cross.
    sweep_box = (7*SCALE, 7*SCALE, 57*SCALE, 57*SCALE)
    for start in (12, 132, 252):
        draw.arc(sweep_box, start, start + 66, fill=white,
                 width=round(1.1*SCALE))

    triangle = [point(32, 10.5, angle) for angle in (-90, 30, 150)]
    draw.line(triangle + [triangle[0]], fill=white,
              width=round(0.8*SCALE), joint="curve")
    for angle in range(0, 360, 60):
        draw.line(
            (point(32, 24.5, angle), point(32, 29.5, angle)),
            fill=white,
            width=round(0.8*SCALE),
        )
        x, y = point(32, 19.2, angle + 30)
        radius = 0.8 * SCALE
        draw.ellipse((x-radius, y-radius, x+radius, y+radius), fill=soft)

    # Keep this at the same 512px resolution as the authored model circle and
    # do not apply Gaussian bloom. Its cutout render path provides the crisp
    # edge; rotation supplies the motion without translucent smearing.
    finish(image, "arcane_disc_fine.png", alpha_cutoff=1,
           output_size=512)


def occult_seal() -> None:
    image = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    draw = ImageDraw.Draw(image)
    white = (255, 255, 255, 255)
    soft = (255, 255, 255, 200)

    for radius, width, color in ((28, 1.35, white), (24.2, .70, soft),
                                 (15.5, .95, white), (7, .70, soft)):
        draw.ellipse(tuple(value * SCALE for value in
                           (32-radius, 32-radius, 32+radius, 32+radius)),
                     outline=color, width=max(1, round(width*SCALE)))

    for offset in (0, 45):
        for angle in range(offset, 360 + offset, 90):
            start = point(32, 17.5, angle - 14)
            crest = point(32, 22.5, angle)
            end = point(32, 17.5, angle + 14)
            draw.line((start, crest, end), fill=white,
                      width=round(.9*SCALE), joint="curve")

    for angle in range(0, 360, 45):
        inner = point(32, 25, angle)
        outer = point(32, 30, angle)
        draw.line((inner, outer), fill=white, width=round(.9*SCALE))
        x, y = point(32, 20.4, angle + 22.5)
        r = .8 * SCALE
        draw.ellipse((x-r, y-r, x+r, y+r), fill=soft)

    # Three curved containment petals avoid the large crossed-X silhouette.
    for angle in (0, 120, 240):
        a = point(32, 4.5, angle)
        b = point(32, 10.5, angle + 34)
        c = point(32, 12.5, angle + 68)
        draw.line((a, b, c), fill=white,
                  width=round(.9*SCALE), joint="curve")
    draw.ellipse((29*SCALE, 29*SCALE, 35*SCALE, 35*SCALE),
                 outline=white, width=round(.9*SCALE))

    finish(image, "occult_seal.png", alpha_cutoff=1)


def phase_vortex() -> None:
    image = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    draw = ImageDraw.Draw(image)
    white = (255, 255, 255, 250)
    soft = (255, 255, 255, 190)
    for arm in range(3):
        points = []
        for step in range(72):
            progress = step / 71
            radius = 3 + progress * 25
            angle = arm * 120 + progress * 430
            points.append(point(32, radius, angle))
        draw.line(points, fill=white, width=round(1.5*SCALE), joint="curve")
    draw.ellipse((8*SCALE, 8*SCALE, 56*SCALE, 56*SCALE),
                 outline=soft, width=round(.8*SCALE))
    draw.ellipse((27*SCALE, 27*SCALE, 37*SCALE, 37*SCALE),
                 outline=white, width=round(1.0*SCALE))
    finish(image, "phase_vortex.png", alpha_cutoff=1)


def core_orb() -> None:
    image = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    pixels = image.load()
    center = (CANVAS - 1) / 2
    outer = 29 * SCALE
    for y in range(CANVAS):
        for x in range(CANVAS):
            distance = math.hypot(x - center, y - center) / outer
            if distance > 1:
                continue
            core = max(0.0, 1.0 - distance)
            bright_center = max(0.0, 1.0 - distance / .34)
            halo = max(0.0, 1.0 - abs(distance - .62) / .075)
            # Zero at the outer radius is important: a non-zero base alpha
            # turns every crossed orb billboard into a visible square/diamond.
            alpha = int(min(
                255,
                core ** 1.35 * 185 + bright_center * 95 + halo * 55,
            ))
            pixels[x, y] = (255, 255, 255, alpha)
    draw = ImageDraw.Draw(image)
    draw.ellipse((17*SCALE, 17*SCALE, 47*SCALE, 47*SCALE),
                 outline=(255, 255, 255, 220), width=round(1.5*SCALE))
    draw.ellipse((25*SCALE, 25*SCALE, 39*SCALE, 39*SCALE),
                 outline=(255, 255, 255, 255), width=round(2*SCALE))
    finish(image, "core_orb.png", alpha_cutoff=8)


def energy_ribbon() -> None:
    image = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    draw = ImageDraw.Draw(image)
    points = []
    for step in range(CANVAS):
        progress = step / (CANVAS - 1)
        x = (32 + math.sin(progress * math.tau * 2) * 4) * SCALE
        points.append((x, step))
    draw.line(points, fill=(255, 255, 255, 255),
              width=round(1.5*SCALE), joint="curve")
    for offset, alpha in ((-5, 175), (5, 175)):
        shifted = [(x + offset*SCALE, y) for x, y in points]
        draw.line(shifted, fill=(255, 255, 255, alpha),
                  width=round(.8*SCALE))
    finish(image, "energy_ribbon.png", alpha_cutoff=1)


arcane_disc()
arcane_disc_fine()
occult_seal()
phase_vortex()
core_orb()
energy_ribbon()
print(f"Generated machine animation textures in {OUTPUT}")
