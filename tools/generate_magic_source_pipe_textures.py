"""Generate clean Source pipe textures from Mekanism's original pipe UVs.

The original casing, tier colour, animation, shading and alpha silhouette are
preserved pixel-for-pixel. Mekanism's small translucent inner-channel accents
are cleared so they cannot become repeated blocks over the separately rendered
Source stream; no extra rails, glyphs or ornaments are painted onto pipe runs.
"""

from __future__ import annotations

import io
import math
import zipfile
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
MEKANISM_JAR = ROOT / "libs" / "Mekanism-1.21.1-10.7.19.85.jar"
MEKANISM_EXTRAS_JAR = ROOT / "libs" / "mekanism_extras-1.21.1-1.4.1.jar"
TEXTURE_ROOT = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "mekanism_magic"
    / "textures"
    / "block"
)
PREVIEW = ROOT / "output" / "imagegen" / "magic_source_pipe_tiers.png"
FLOW_TEXTURE = TEXTURE_ROOT / "magic_source_flow.png"
FLOW_TEXTURE_V = TEXTURE_ROOT / "magic_source_flow_v.png"
FLOW_TEXTURE_REVERSE = TEXTURE_ROOT / "magic_source_flow_reverse.png"
FLOW_TEXTURE_V_REVERSE = (
    TEXTURE_ROOT / "magic_source_flow_v_reverse.png"
)

# Source every tier directly from the matching Mekanism/Mekanism Extras pipe.
# This keeps the original tier language, including animated high-tier colours.
TIER_SOURCES = {
    "magic_source_pipe": ("mekanism", "basic"),
    "advanced_magic_source_pipe": ("mekanism", "advanced"),
    "elite_magic_source_pipe": ("mekanism", "elite"),
    "ultimate_magic_source_pipe": ("mekanism", "ultimate"),
    "absolute_magic_source_pipe": ("mekanism_extras", "absolute"),
    "supreme_magic_source_pipe": ("mekanism_extras", "supreme"),
    "cosmic_magic_source_pipe": ("mekanism_extras", "cosmic"),
    "infinite_magic_source_pipe": ("mekanism_extras", "infinite"),
}

# The moving Source is rendered separately. These old glass pixels become
# fully clear so their 2x2 UV details cannot turn into repeated coloured blocks
# or bright divider lines when stretched along a connected pipe.
SOURCE_VIOLET = (126, 82, 153)
CHANNEL_ALPHA = 0
FLOW_FRAME_COUNT = 16


def read_texture(archive: zipfile.ZipFile, name: str) -> Image.Image:
    return Image.open(io.BytesIO(archive.read(name))).convert("RGBA")


def source_path(namespace: str, stem: str,
                vertical: bool, opaque: bool) -> str:
    suffix = "_vertical" if vertical else ""
    opaque_dir = "opaque/" if opaque else ""
    return (
        f"assets/{namespace}/textures/block/models/multipart/"
        f"{opaque_dir}{stem}_mechanical_pipe{suffix}.png"
    )


def tint_existing_channel(image: Image.Image, mask: Image.Image) -> None:
    """Tint only pixels translucent in the original normal texture.

    ``mask`` is also supplied for the opaque fallback, ensuring its geometry
    matches the normal version without guessing colours or positions.
    """
    for y in range(16):
        for x in range(16):
            if 0 < mask.getpixel((x, y))[3] < 255:
                alpha = image.getpixel((x, y))[3]
                # The opaque fallback keeps Mekanism's original neutral glass.
                # Only the translucent model receives this subtle empty-pipe
                # identity; a filled pipe gets its colour from the renderer.
                if alpha < 255:
                    image.putpixel(
                        (x, y), (*SOURCE_VIOLET, min(alpha, CHANNEL_ALPHA)))


def generate_flow_texture() -> Image.Image:
    """Build an edge-matched magical stream with wisps and travelling motes.

    The language follows the generated Source-flow concept: dark violet plasma,
    one soft lavender filament and sparse carried lights. All motion lives in
    this single texture layer, so it cannot reintroduce translucent geometry
    overlaps. The field is periodic across both axes for connected runs.
    """
    texture = Image.new(
        "RGBA", (16, 16 * FLOW_FRAME_COUNT), (0, 0, 0, 0))
    for frame_index in range(FLOW_FRAME_COUNT):
        phase = frame_index / FLOW_FRAME_COUNT
        frame = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            vertical = y / 15
            for x in range(16):
                horizontal = x / 15

                # Quiet plasma variation without a repeated stripe pattern.
                # Every time coefficient below is an integer. This is
                # deliberate: phase 1 must equal phase 0 exactly, otherwise
                # Minecraft's final-frame -> first-frame interpolation exposes
                # a visible reset even when every spatial edge is seamless.
                plasma_a = 0.5 + 0.5 * math.sin(math.tau * (
                    horizontal + 0.28 * math.sin(math.tau * vertical)
                    - phase))
                plasma_b = 0.5 + 0.5 * math.sin(math.tau * (
                    vertical - 0.22 * math.sin(math.tau * horizontal)
                    + phase))
                plasma = plasma_a * 0.58 + plasma_b * 0.42

                # One broad wisp and a dim echo curl through the pipe. Their
                # X-periodic paths meet cleanly between neighbouring segments.
                wisp_center = 7.5 + 2.15 * math.sin(math.tau * (
                    horizontal - phase))
                wisp_center += 0.65 * math.sin(math.tau * (
                    horizontal * 2 + phase))
                wisp_distance = abs(y - wisp_center)
                wisp = math.exp(-(wisp_distance * wisp_distance) / 1.35)

                echo_center = 7.5 + 3.6 * math.sin(math.tau * (
                    horizontal + phase + 0.43))
                echo_distance = abs(y - echo_center)
                echo = math.exp(-(echo_distance * echo_distance) / 0.72)

                # Three tiny motes travel along the main wisp. Wrapped X
                # distance keeps them continuous as they cross a tile edge.
                mote = 0.0
                for offset in (0.08, 0.43, 0.76):
                    mote_x = (offset + phase) % 1
                    delta_x = abs(horizontal - mote_x)
                    delta_x = min(delta_x, 1 - delta_x)
                    mote_y = 7.5 + 2.15 * math.sin(math.tau * (
                        mote_x - phase))
                    mote_y += 0.65 * math.sin(math.tau * (
                        mote_x * 2 + phase))
                    distance = (delta_x * 16) ** 2 + (y - mote_y) ** 2
                    mote = max(mote, math.exp(-distance / 0.62))

                red = round(min(255,
                                76 + plasma * 25
                                + wisp * 70 + echo * 24 + mote * 80))
                green = round(min(255,
                                  24 + plasma * 17
                                  + wisp * 58 + echo * 18 + mote * 112))
                blue = round(min(255,
                                 145 + plasma * 34
                                 + wisp * 61 + echo * 31 + mote * 72))
                alpha = round(min(244,
                                  202 + plasma * 14
                                  + wisp * 17 + mote * 11))
                frame.putpixel((x, y), (red, green, blue, alpha))
        # Trigonometric endpoints are mathematically equal, but floating-point
        # rounding can still differ by one colour step. Copy the first edge so
        # the packed PNG is byte-identical where Minecraft tiles it.
        for y in range(16):
            frame.putpixel((15, y), frame.getpixel((0, y)))
        for x in range(16):
            frame.putpixel((x, 15), frame.getpixel((x, 0)))
        texture.paste(frame, (0, frame_index * 16))
    return texture


def transform_flow(
        texture: Image.Image,
        transform: Image.Transpose) -> Image.Image:
    """Apply one orientation transform independently to every frame."""
    transformed = Image.new("RGBA", texture.size, (0, 0, 0, 0))
    for top in range(0, texture.height, 16):
        frame = texture.crop((0, top, 16, top + 16))
        frame = frame.transpose(transform)
        transformed.paste(frame, (0, top))
    return transformed


def render_frames(source: Image.Image, mask_source: Image.Image) -> Image.Image:
    """Tint every 16px frame while preserving source animation height."""
    if source.height != mask_source.height:
        raise ValueError(
            f"Pipe texture/mask height mismatch: "
            f"{source.height} != {mask_source.height}"
        )
    rendered = source.copy()
    for top in range(0, source.height, 16):
        frame = source.crop((0, top, 16, top + 16))
        mask = mask_source.crop((0, top, 16, top + 16))
        tint_existing_channel(frame, mask)
        # Paste the RGBA frame verbatim. Alpha compositing would normalise the
        # hidden RGB values of fully transparent pixels and break the promise
        # that every unrelated source pixel remains untouched.
        rendered.paste(frame, (0, top))
    return rendered


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False)


def save_preview(tiers: list[tuple[Image.Image, Image.Image]]) -> None:
    """Save a nearest-neighbour QA sheet without changing game textures."""
    scale = 16
    panel = 16 * scale
    row_gap = 16
    preview = Image.new(
        "RGBA",
        (panel * 2 + 48, panel * len(tiers) + row_gap * (len(tiers) + 1)),
        (12, 15, 18, 255),
    )
    for index, (center, vertical) in enumerate(tiers):
        top = row_gap + index * (panel + row_gap)
        for column, texture in enumerate((center, vertical)):
            frame = texture.crop((0, 0, 16, 16))
            enlarged = frame.resize((panel, panel), Image.Resampling.NEAREST)
            preview.alpha_composite(enlarged, (16 + column * (panel + 16), top))
    save(preview, PREVIEW)


def copy_animation_metadata(
        archive: zipfile.ZipFile,
        original: str,
        destination: Path) -> None:
    meta_name = original + ".mcmeta"
    meta_destination = Path(str(destination) + ".mcmeta")
    if meta_name in archive.namelist():
        meta_destination.write_bytes(archive.read(meta_name))
    elif meta_destination.exists():
        meta_destination.unlink()


def main() -> None:
    previews: list[tuple[Image.Image, Image.Image]] = []
    with zipfile.ZipFile(MEKANISM_JAR) as mekanism_archive, \
            zipfile.ZipFile(MEKANISM_EXTRAS_JAR) as extras_archive:
        archives = {
            "mekanism": mekanism_archive,
            "mekanism_extras": extras_archive,
        }
        for tier_name, (namespace, stem) in TIER_SOURCES.items():
            archive = archives[namespace]
            center_name = source_path(namespace, stem, False, False)
            vertical_name = source_path(namespace, stem, True, False)
            opaque_center_name = source_path(namespace, stem, False, True)
            opaque_vertical_name = source_path(namespace, stem, True, True)

            center_source = read_texture(archive, center_name)
            vertical_source = read_texture(archive, vertical_name)
            center = render_frames(center_source, center_source)
            vertical = render_frames(vertical_source, vertical_source)
            opaque_center = render_frames(
                read_texture(archive, opaque_center_name), center_source)
            opaque_vertical = render_frames(
                read_texture(archive, opaque_vertical_name), vertical_source)

            output = TEXTURE_ROOT / tier_name
            outputs = (
                (center, output / "magic_source_pipe.png", center_name),
                (vertical, output / "magic_source_pipe_vertical.png", vertical_name),
                (
                    opaque_center,
                    output / "opaque" / "magic_source_pipe.png",
                    opaque_center_name,
                ),
                (
                    opaque_vertical,
                    output / "opaque" / "magic_source_pipe_vertical.png",
                    opaque_vertical_name,
                ),
            )
            for image, destination, original in outputs:
                save(image, destination)
                copy_animation_metadata(archive, original, destination)
            previews.append((center, vertical))

    save_preview(previews)
    flow_u = generate_flow_texture()
    save(flow_u, FLOW_TEXTURE)
    save(transform_flow(
        flow_u, Image.Transpose.ROTATE_270), FLOW_TEXTURE_V)
    save(transform_flow(
        flow_u, Image.Transpose.FLIP_LEFT_RIGHT), FLOW_TEXTURE_REVERSE)
    save(transform_flow(
        flow_u, Image.Transpose.ROTATE_90), FLOW_TEXTURE_V_REVERSE)


if __name__ == "__main__":
    main()
