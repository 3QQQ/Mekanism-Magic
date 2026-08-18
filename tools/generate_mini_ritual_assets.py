from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/mekanism_magic/textures/item/mini_ritual"

PENTACLES = {
    "contact_eldritch_spirit": ("contact", (104, 89, 170)),
    "contact_wild_spirit": ("contact", (72, 135, 174)),
    "craft_afrit": ("craft", (202, 76, 77)),
    "craft_djinni": ("craft", (67, 172, 199)),
    "craft_foliot": ("craft", (75, 171, 91)),
    "craft_marid": ("craft", (152, 74, 197)),
    "possess_afrit": ("possess", (202, 76, 77)),
    "possess_djinni": ("possess", (67, 172, 199)),
    "possess_foliot": ("possess", (75, 171, 91)),
    "possess_marid": ("possess", (152, 74, 197)),
    "possess_unbound_afrit": ("possess", (234, 135, 66)),
    "resurrect_spirit": ("resurrect", (70, 183, 133)),
    "summon_afrit": ("summon", (202, 76, 77)),
    "summon_djinni": ("summon", (67, 172, 199)),
    "summon_foliot": ("summon", (75, 171, 91)),
    "summon_marid": ("summon", (152, 74, 197)),
    "summon_unbound_afrit": ("summon", (234, 135, 66)),
    "summon_unbound_marid": ("summon", (211, 87, 175)),
}


def save(image: Image.Image, name: str) -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(TEXTURES / name)


def unbound() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (26, 30, 41, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 1, 14, 14), outline=(83, 96, 119, 255))
    draw.rectangle((3, 3, 12, 12), outline=(57, 187, 194, 255))
    draw.rectangle((5, 5, 10, 10), fill=(40, 46, 61, 255))
    draw.line((5, 8, 10, 8), fill=(113, 235, 221, 255))
    draw.line((8, 5, 8, 10), fill=(113, 235, 221, 255))
    draw.point((8, 8), fill=(245, 255, 246, 255))
    return image


def pentacle(name: str, category: str, accent: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (16, 16), (20, 23, 32, 255))
    draw = ImageDraw.Draw(image)
    dark = tuple(max(0, value // 3) for value in accent)
    bright = tuple(min(255, value + 55) for value in accent)
    draw.rectangle((0, 0, 15, 15), outline=(8, 10, 17, 255))
    draw.rectangle((2, 2, 13, 13), outline=dark + (255,))
    draw.rectangle((3, 3, 12, 12), outline=accent + (255,))

    # The outer ring is the miniature projection boundary.
    for point in ((7, 3), (8, 3), (12, 7), (12, 8),
                  (7, 12), (8, 12), (3, 7), (3, 8)):
        draw.point(point, fill=bright + (255,))

    if category == "craft":
        draw.rectangle((5, 5, 10, 10), outline=bright + (255,))
        draw.line((5, 5, 10, 10), fill=accent + (255,))
        draw.line((10, 5, 5, 10), fill=accent + (255,))
    elif category == "summon":
        draw.line((8, 4, 10, 8), fill=bright + (255,))
        draw.line((10, 8, 8, 11), fill=bright + (255,))
        draw.line((8, 11, 6, 8), fill=bright + (255,))
        draw.line((6, 8, 8, 4), fill=bright + (255,))
        draw.point((8, 7), fill=(245, 255, 249, 255))
    elif category == "possess":
        draw.line((8, 4, 11, 10), fill=bright + (255,))
        draw.line((11, 10, 5, 10), fill=bright + (255,))
        draw.line((5, 10, 8, 4), fill=bright + (255,))
        draw.rectangle((7, 7, 8, 8), fill=(245, 232, 255, 255))
    elif category == "resurrect":
        draw.line((8, 4, 8, 11), fill=bright + (255,))
        draw.line((5, 7, 11, 7), fill=bright + (255,))
        draw.point((6, 10), fill=accent + (255,))
        draw.point((10, 10), fill=accent + (255,))
    else:
        draw.rectangle((5, 6, 10, 9), outline=bright + (255,))
        draw.point((7, 7), fill=(245, 255, 249, 255))
        draw.point((8, 8), fill=(245, 255, 249, 255))

    # A two-bit corner rune makes each pentacle family visibly distinct.
    checksum = sum((index + 1) * ord(char) for index, char in enumerate(name))
    for index, point in enumerate(((5, 5), (10, 5), (5, 10), (10, 10))):
        if checksum & (1 << index):
            draw.point(point, fill=bright + (255,))
    return image


def main() -> None:
    save(unbound(), "mini_ritual_unbound.png")
    for name, (category, accent) in PENTACLES.items():
        save(pentacle(name, category, accent), f"{name}.png")


if __name__ == "__main__":
    main()
