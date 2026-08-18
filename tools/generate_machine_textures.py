from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/mekanism_magic/textures/block"


def save(image: Image.Image, relative: str) -> None:
    target = TEXTURES / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    # Machine shells are deliberately stored without an alpha channel. This
    # prevents resource packs or render-layer changes from treating dark shell
    # pixels as translucent.
    image.convert("RGB").save(target)


def shell(accent: tuple[int, int, int], active: bool = False) -> Image.Image:
    image = Image.new("RGBA", (16, 16), (28, 32, 40, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), outline=(11, 14, 20, 255))
    draw.line((1, 1, 14, 1), fill=(93, 103, 116, 255))
    draw.line((1, 14, 14, 14), fill=(17, 21, 28, 255))
    draw.rectangle((2, 2, 13, 13), fill=(43, 49, 59, 255))
    draw.rectangle((3, 3, 12, 12), fill=(24, 28, 36, 255))
    draw.point((2, 2), fill=(139, 150, 162, 255))
    draw.point((13, 2), fill=(139, 150, 162, 255))
    draw.point((2, 13), fill=(76, 84, 96, 255))
    draw.point((13, 13), fill=(76, 84, 96, 255))
    if active:
        glow = tuple(min(255, channel + 80) for channel in accent)
        draw.rectangle((3, 3, 12, 12), outline=(*accent, 255))
        draw.point((3, 3), fill=(*glow, 255))
        draw.point((12, 3), fill=(*glow, 255))
    return image


def spirit_front(active: bool) -> Image.Image:
    accent = (82, 172, 189)
    image = shell(accent, active)
    draw = ImageDraw.Draw(image)
    dim = (46, 97, 113, 255)
    mid = (102, 72, 152, 255)
    bright = (117, 239, 238, 255) if active else (68, 157, 170, 255)
    core = (229, 255, 250, 255) if active else (142, 203, 204, 255)
    draw.rectangle((5, 4, 10, 11), fill=(16, 19, 29, 255))
    draw.point((7, 3), fill=mid)
    draw.point((8, 3), fill=mid)
    draw.point((4, 7), fill=dim)
    draw.point((11, 7), fill=dim)
    draw.line((6, 5, 9, 5), fill=bright)
    draw.point((5, 6), fill=bright)
    draw.point((10, 6), fill=bright)
    draw.point((5, 8), fill=bright)
    draw.point((10, 8), fill=bright)
    draw.line((6, 9, 9, 9), fill=bright)
    draw.rectangle((7, 6, 8, 8), fill=core)
    draw.point((6, 7), fill=mid)
    draw.point((9, 7), fill=mid)
    draw.line((7, 10, 8, 10), fill=mid)
    return image


def ritual_front(active: bool) -> Image.Image:
    accent = (176, 117, 54)
    image = shell(accent, active)
    draw = ImageDraw.Draw(image)
    gold = (255, 203, 91, 255) if active else (172, 126, 57, 255)
    violet = (202, 116, 255, 255) if active else (104, 60, 137, 255)
    dark = (18, 14, 25, 255)
    draw.rectangle((3, 3, 12, 12), fill=dark)
    for point in ((6, 4), (7, 3), (8, 3), (9, 4), (4, 6), (3, 7),
                  (3, 8), (4, 9), (6, 11), (7, 12), (8, 12), (9, 11),
                  (11, 9), (12, 8), (12, 7), (11, 6)):
        draw.point(point, fill=gold)
    draw.line((7, 5, 10, 10), fill=violet)
    draw.line((8, 5, 5, 10), fill=violet)
    draw.line((5, 10, 10, 10), fill=violet)
    draw.rectangle((7, 7, 8, 8), fill=(245, 230, 255, 255) if active else violet)
    return image


def side(accent: tuple[int, int, int], active: bool = False) -> Image.Image:
    image = shell(accent, active)
    draw = ImageDraw.Draw(image)
    draw.rectangle((4, 4, 11, 11), fill=(20, 24, 31, 255))
    for y in (5, 7, 9):
        draw.line((5, y, 10, y), fill=(78, 88, 102, 255))
    glow = tuple(min(255, channel + 65) for channel in accent)
    draw.line((2, 5, 2, 10), fill=(*accent, 255))
    draw.line((13, 5, 13, 10), fill=(*(glow if active else accent), 255))
    return image


def back(accent: tuple[int, int, int]) -> Image.Image:
    image = shell(accent)
    draw = ImageDraw.Draw(image)
    draw.rectangle((4, 4, 11, 11), fill=(18, 22, 29, 255))
    draw.rectangle((5, 5, 10, 10), outline=(75, 85, 98, 255))
    draw.rectangle((7, 6, 8, 9), fill=(*accent, 255))
    return image


def top(accent: tuple[int, int, int], ritual: bool = False) -> Image.Image:
    image = shell(accent)
    draw = ImageDraw.Draw(image)
    ring = (181, 127, 58, 255) if ritual else (*accent, 255)
    draw.rectangle((4, 4, 11, 11), outline=ring)
    draw.rectangle((6, 6, 9, 9), outline=(112, 123, 137, 255))
    draw.point((7, 7), fill=ring)
    draw.point((8, 8), fill=ring)
    return image


def bottom() -> Image.Image:
    image = Image.new("RGBA", (16, 16), (22, 26, 33, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 15, 15), outline=(8, 11, 16, 255))
    draw.rectangle((2, 2, 13, 13), outline=(56, 64, 75, 255))
    draw.rectangle((5, 5, 10, 10), fill=(14, 17, 23, 255))
    return image


def factory_face(accent: tuple[int, int, int], active: bool) -> Image.Image:
    image = shell(accent, active)
    draw = ImageDraw.Draw(image)
    bright = (125, 245, 232, 255) if active else (62, 147, 153, 255)
    tier_bright = tuple(min(255, channel + (70 if active else 25)) for channel in accent)
    draw.rectangle((2, 4, 13, 12), fill=(18, 21, 29, 255))
    draw.line((3, 4, 12, 4), fill=(83, 92, 106, 255))
    draw.rectangle((5, 5, 10, 10), outline=(*tier_bright, 255))
    draw.point((7, 6), fill=bright)
    draw.point((8, 6), fill=bright)
    draw.line((6, 7, 9, 7), fill=bright)
    draw.line((7, 8, 8, 9), fill=bright)
    draw.point((3, 7), fill=(*tier_bright, 255))
    draw.point((12, 7), fill=(*tier_bright, 255))
    draw.rectangle((3, 11, 12, 12), fill=(47, 54, 65, 255))
    return image


def factory_side(accent: tuple[int, int, int]) -> Image.Image:
    image = shell(accent)
    draw = ImageDraw.Draw(image)
    draw.rectangle((1, 4, 14, 11), fill=(31, 36, 45, 255))
    draw.rectangle((4, 5, 11, 10), fill=(18, 22, 29, 255))
    for x in (5, 7, 9):
        draw.line((x, 6, x, 9), fill=(79, 89, 103, 255))
    draw.line((2, 3, 13, 3), fill=(*accent, 255))
    draw.line((2, 12, 13, 12), fill=(50, 142, 146, 255))
    draw.rectangle((1, 1, 2, 2), fill=(*accent, 255))
    draw.rectangle((13, 1, 14, 2), fill=(*accent, 255))
    return image


def factory_top(accent: tuple[int, int, int]) -> Image.Image:
    image = shell(accent)
    draw = ImageDraw.Draw(image)
    draw.rectangle((2, 2, 13, 13), fill=(37, 42, 51, 255))
    draw.rectangle((4, 4, 11, 11), outline=(*accent, 255))
    draw.line((5, 7, 10, 7), fill=(70, 160, 162, 255))
    draw.line((5, 9, 10, 9), fill=(70, 160, 162, 255))
    return image


def assembler_front(active: bool) -> Image.Image:
    accent = (55, 166, 181)
    image = shell(accent, active)
    draw = ImageDraw.Draw(image)
    cyan = (118, 244, 232, 255) if active else (61, 167, 177, 255)
    gold = (255, 208, 94, 255) if active else (181, 129, 57, 255)
    draw.rectangle((4, 4, 11, 11), fill=(17, 21, 31, 255))
    draw.rectangle((5, 5, 10, 10), outline=cyan)
    draw.line((6, 6, 9, 6), fill=gold)
    draw.line((6, 9, 9, 9), fill=gold)
    draw.line((6, 6, 6, 9), fill=gold)
    draw.line((9, 6, 9, 9), fill=gold)
    draw.point((8, 8), fill=(245, 255, 249, 255) if active else cyan)
    return image


def assembler_side(active: bool = False) -> Image.Image:
    return side((55, 166, 181), active)


def assembler_top() -> Image.Image:
    return top((55, 166, 181))


def main() -> None:
    spirit = (74, 162, 174)
    ritual = (154, 101, 48)

    save(spirit_front(False), "spirit_processor/front.png")
    save(spirit_front(True), "spirit_processor/front_active.png")
    save(side(spirit), "spirit_processor/side.png")
    save(side(spirit, True), "spirit_processor/side_active.png")
    save(back(spirit), "spirit_processor/back.png")
    save(top(spirit), "spirit_processor/top.png")
    save(bottom(), "spirit_processor/bottom.png")

    save(ritual_front(False), "ritual_engine/front.png")
    save(ritual_front(True), "ritual_engine/front_active.png")
    save(side(ritual), "ritual_engine/side.png")
    save(side(ritual, True), "ritual_engine/side_active.png")
    save(back(ritual), "ritual_engine/back.png")
    save(top(ritual, ritual=True), "ritual_engine/top.png")
    save(bottom(), "ritual_engine/bottom.png")

    factory_tiers = {
        "basic": (73, 151, 74),
        "advanced": (181, 64, 57),
        "elite": (49, 155, 190),
        "ultimate": (139, 72, 190),
    }
    # Neutral fallback textures remain available for the shared parent models.
    fallback = (99, 71, 155)
    save(factory_face(fallback, False), "spirit_factory/front.png")
    save(factory_face(fallback, True), "spirit_factory/front_active.png")
    save(factory_side(fallback), "spirit_factory/side.png")
    save(factory_top(fallback), "spirit_factory/top.png")
    save(back(fallback), "spirit_factory/back.png")
    save(bottom(), "spirit_factory/bottom.png")
    for tier, accent in factory_tiers.items():
        folder = f"spirit_factory/{tier}"
        save(factory_face(accent, False), f"{folder}/front.png")
        save(factory_face(accent, True), f"{folder}/front_active.png")
        save(factory_side(accent), f"{folder}/side.png")
        save(factory_top(accent), f"{folder}/top.png")
        save(back(accent), f"{folder}/back.png")
        save(bottom(), f"{folder}/bottom.png")

    save(assembler_front(False), "mini_ritual_assembler/front.png")
    save(assembler_front(True), "mini_ritual_assembler/front_active.png")
    save(assembler_side(), "mini_ritual_assembler/side.png")
    save(assembler_side(True), "mini_ritual_assembler/side_active.png")
    save(back((55, 166, 181)), "mini_ritual_assembler/back.png")
    save(assembler_top(), "mini_ritual_assembler/top.png")
    save(bottom(), "mini_ritual_assembler/bottom.png")


if __name__ == "__main__":
    main()
