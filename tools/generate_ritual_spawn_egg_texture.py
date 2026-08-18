from pathlib import Path

from PIL import Image, ImageDraw


root = Path(__file__).resolve().parents[1]
target = root / "src/main/resources/assets/mekanism_magic/textures/item/ritual_spawn_egg.png"
target.parent.mkdir(parents=True, exist_ok=True)

image = Image.new("RGB", (16, 16), (0, 0, 0))
draw = ImageDraw.Draw(image)
egg = (47, 31, 62)
accent = (176, 82, 190)
highlight = (235, 139, 225)
draw.rectangle((5, 1, 10, 1), fill=accent)
draw.rectangle((3, 2, 12, 3), fill=egg)
draw.rectangle((2, 4, 13, 11), fill=egg)
draw.rectangle((3, 12, 12, 13), fill=egg)
draw.rectangle((5, 14, 10, 14), fill=egg)
draw.rectangle((4, 5, 11, 6), fill=accent)
draw.rectangle((5, 8, 10, 9), fill=highlight)
draw.rectangle((6, 10, 9, 11), fill=accent)
draw.point((7, 8), fill=(255, 239, 255))
draw.point((8, 8), fill=(255, 239, 255))
draw.point((2, 7), fill=(17, 12, 25))
draw.point((13, 7), fill=(17, 12, 25))
image.save(target)
