#!/usr/bin/env python3
"""Builds every Steam store and library image from key art rendered by the game itself.

    python3 -I tools/steam/capsules.py <keyart-folder> <out-folder>

<keyart-folder> holds the stills from tools/steam/film.sh keyart (wide.png, tall.png, hero.png).
The logo is drawn here with the game's own font (Space Grotesk Bold): "CRAZY" white, "TOWER"
mint, each letter tilted a little. Capsules carry nothing but art and the logo, as Valve asks.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FONT = ROOT / "game/src/main/resources/fonts/SpaceGrotesk-Bold.ttf"
PEOPLE = ROOT / "game/src/main/resources/people"

INK = (18, 21, 42)
MINT = (79, 227, 193)
WHITE = (238, 240, 250)
GOLD = (255, 201, 77)
SKY = (43, 58, 103)


def logo(height, shadow=True):
    """The CrazyTower word mark on a transparent image [height] pixels high (about 4.4 times as wide)."""
    size = int(height * 0.78)
    font = ImageFont.truetype(str(FONT), size)
    stroke = max(2, size // 11)
    rnd = random.Random(4)
    letters = [(c, WHITE) for c in "CRAZY"] + [(" ", None)] + [(c, MINT) for c in "TOWER"]
    tiles = []
    for ch, color in letters:
        if ch == " ":
            tiles.append((None, int(size * 0.12), 0))
            continue
        box = font.getbbox(ch, stroke_width=stroke)
        w, h = box[2] - box[0] + stroke * 2, int(size * 1.25)
        tile = Image.new("RGBA", (w + 20, h + 20), (0, 0, 0, 0))
        d = ImageDraw.Draw(tile)
        d.text((10 - box[0] + stroke, 10 + int(size * 0.05)), ch, font=font, fill=color, stroke_width=stroke, stroke_fill=INK)
        angle = rnd.uniform(-7, 7)
        lift = rnd.uniform(-0.06, 0.06) * size
        advance = int(font.getlength(ch) + stroke * 0.6)
        tiles.append((tile.rotate(angle, resample=Image.BICUBIC, expand=True), advance, lift))
    width = sum(t[1] for t in tiles) + int(size * 0.5)
    canvas = Image.new("RGBA", (width, int(height * 1.15)), (0, 0, 0, 0))
    x = int(size * 0.2)
    for tile, advance, lift in tiles:
        if tile is not None:
            y = int((canvas.height - tile.height) / 2 + lift)
            canvas.alpha_composite(tile, (max(0, x - (tile.width - advance) // 2), max(0, y)))
        x += advance
    canvas = canvas.crop(canvas.getbbox())
    if not shadow:
        return canvas
    # a soft dark glow plus a hard drop shadow, so the mark reads on any background
    pad = stroke * 4
    out = Image.new("RGBA", (canvas.width + pad * 2, canvas.height + pad * 2), (0, 0, 0, 0))
    alpha = canvas.split()[3]
    glow = Image.new("RGBA", out.size, INK + (0,))
    glow.putalpha(Image.new("L", out.size, 0))
    a = Image.new("L", out.size, 0)
    a.paste(alpha, (pad, pad))
    glow.putalpha(a.filter(ImageFilter.GaussianBlur(stroke * 1.6)).point(lambda v: int(v * 0.85)))
    out.alpha_composite(glow)
    hard = Image.new("RGBA", out.size, INK + (255,))
    b = Image.new("L", out.size, 0)
    b.paste(alpha, (pad + stroke // 2, pad + stroke))
    hard.putalpha(b)
    out.alpha_composite(hard)
    out.alpha_composite(canvas, (pad, pad))
    return out


def person(name, height):
    """First frame of a game sprite (48 x 96 per frame), scaled up without blurring the pixels."""
    sheet = Image.open(PEOPLE / f"{name}.png").convert("RGBA")
    frame = sheet.crop((0, 0, 48, 96))
    frame = frame.crop(frame.getbbox())
    scale = height / frame.height
    return frame.resize((max(1, round(frame.width * scale)), round(height)), Image.NEAREST)


def cover(img, w, h, focus=(0.5, 0.5)):
    """Scales [img] to fill w x h and crops around [focus] (fractions of the image)."""
    scale = max(w / img.width, h / img.height)
    big = img.resize((math.ceil(img.width * scale), math.ceil(img.height * scale)), Image.LANCZOS)
    x = int((big.width - w) * focus[0])
    y = int((big.height - h) * focus[1])
    return big.crop((x, y, x + w, y + h)).convert("RGBA")


def shade(img, strength=0.55, direction="left", reach=0.7):
    """Darkens one side with the game's ink colour so the logo stands out."""
    w, h = img.size
    grad = Image.new("L", (w, h), 0)
    px = grad.load()
    for x in range(w) if direction in ("left", "right") else range(h):
        f = x / ((w if direction in ("left", "right") else h) * reach)
        v = int(255 * strength * max(0.0, 1 - f) ** 1.4)
        if direction in ("left", "top"):
            pos = x
        else:
            pos = (w if direction == "right" else h) - 1 - x
        if direction in ("left", "right"):
            for y in range(h):
                px[pos, y] = v
        else:
            for xx in range(w):
                px[xx, pos] = v
    layer = Image.new("RGBA", (w, h), INK + (255,))
    layer.putalpha(grad)
    out = img.copy()
    out.alpha_composite(layer)
    return out


def vignette(img, strength=0.45):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((-w * 0.25, -h * 0.35, w * 1.25, h * 1.35), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) * 0.12))
    dark = Image.new("RGBA", (w, h), INK + (int(255 * strength),))
    inv = Image.eval(mask, lambda v: 255 - v)
    dark.putalpha(Image.eval(inv, lambda v: int(v * strength)))
    out = img.copy()
    out.alpha_composite(dark)
    return out


def place_logo(img, height, center, shadow=True, max_width=0.92):
    mark = logo(height, shadow)
    if mark.width > img.width * max_width:
        mark = mark.resize((int(img.width * max_width), int(mark.height * img.width * max_width / mark.width)), Image.LANCZOS)
    x = int(center[0] * img.width - mark.width / 2)
    y = int(center[1] * img.height - mark.height / 2)
    img.alpha_composite(mark, (x, y))
    return img


def crowd(img, names, height, bottom, left, gap=0.78, shadow=True):
    """Stands a row of game characters on the line [bottom], starting at [left]."""
    x = left
    for name in names:
        p = person(name, height)
        if shadow:
            sh = Image.new("RGBA", (int(p.width * 1.1), int(height * 0.08)), (0, 0, 0, 0))
            ImageDraw.Draw(sh).ellipse((0, 0, sh.width - 1, sh.height - 1), fill=(0, 0, 0, 110))
            img.alpha_composite(sh, (int(x - p.width * 0.05), int(bottom - sh.height * 0.6)))
        img.alpha_composite(p, (int(x), int(bottom - p.height)))
        x += p.width * gap
    return img


def save(img, path, fmt=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.suffix.lower() == ".jpg":
        img.convert("RGB").save(path, quality=93)
    else:
        img.save(path)
    print(f"{path.name}: {img.size[0]} x {img.size[1]}")


def main():
    src = Path(sys.argv[1])
    out = Path(sys.argv[2])
    wide = Image.open(src / "wide.png").convert("RGBA")   # 16:9, rooms close up
    tall = Image.open(src / "tall.png").convert("RGBA")   # a tall slice of the tower
    hero = Image.open(src / "hero.png").convert("RGBA")   # very wide, the whole tower width

    cast = ["npc_clown_classic", "npc_vampire_count", "npc_alien", "npc_ghost", "npc_penguin", "npc_supervillain"]

    # Store: header capsule 920 x 430
    img = vignette(shade(cover(wide, 920, 430, (0.5, 0.45)), 0.6, "top", 0.85))
    place_logo(img, 130, (0.5, 0.36), max_width=0.86)
    crowd(img, cast[:4], 150, 418, 470, gap=0.78)
    save(img, out / "store/header_capsule_920x430.png")

    # Store: small capsule 462 x 174 (mostly logo, it is tiny on Steam)
    img = shade(cover(wide, 462, 174, (0.4, 0.5)), 0.7, "top", 1.4)
    place_logo(img, 80, (0.5, 0.5), max_width=0.9)
    save(img, out / "store/small_capsule_462x174.png")

    # Store: main capsule 1232 x 706
    img = vignette(shade(cover(wide, 1232, 706, (0.5, 0.5)), 0.6, "top", 0.75))
    place_logo(img, 180, (0.5, 0.25))
    crowd(img, cast, 230, 690, 170, gap=0.95)
    save(img, out / "store/main_capsule_1232x706.png")

    # Store: vertical capsule 748 x 896
    img = vignette(shade(cover(tall, 748, 896, (0.5, 0.35)), 0.65, "top", 0.6))
    place_logo(img, 120, (0.5, 0.14))
    crowd(img, cast[:4], 200, 880, 110, gap=0.95)
    save(img, out / "store/vertical_capsule_748x896.png")

    # Store: page background 1438 x 810 (Steam darkens and fades it itself)
    img = cover(wide, 1438, 810, (0.5, 0.5))
    dim = Image.new("RGBA", img.size, INK + (150,))
    img.alpha_composite(dim)
    save(img.filter(ImageFilter.GaussianBlur(1.5)), out / "store/page_background_1438x810.png")

    # Library: capsule 600 x 900
    img = vignette(shade(cover(tall, 600, 900, (0.5, 0.4)), 0.65, "top", 0.6))
    place_logo(img, 100, (0.5, 0.13))
    crowd(img, cast[:3], 190, 884, 95, gap=1.0)
    save(img, out / "library/library_capsule_600x900.png")

    # Library: header 920 x 430 (same as the store header)
    img = vignette(shade(cover(wide, 920, 430, (0.5, 0.45)), 0.6, "top", 0.85))
    place_logo(img, 130, (0.5, 0.36), max_width=0.86)
    crowd(img, cast[:4], 150, 418, 470, gap=0.78)
    save(img, out / "library/library_header_920x430.png")

    # Library: hero 3840 x 1240, art only (Steam puts the logo on top)
    img = cover(hero, 3840, 1240, (0.5, 0.5))
    save(img, out / "library/library_hero_3840x1240.png")

    # Library: logo 1280 x 720 at most, transparent
    mark = logo(300)
    scale = min(1280 / mark.width, 720 / mark.height)
    mark = mark.resize((int(mark.width * scale), int(mark.height * scale)), Image.LANCZOS)
    save(mark, out / "library/library_logo_transparent.png")

    # Community icon 184 x 184 and the app icons: a clown in front of a lit window
    def icon(size):
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        r = size * 0.18
        d.rounded_rectangle((0, 0, size - 1, size - 1), radius=r, fill=SKY)
        # a little tower: three floors of windows
        tw = size * 0.62
        tx = (size - tw) / 2
        d.rectangle((tx, size * 0.16, tx + tw, size * 0.92), fill=INK)
        for row in range(3):
            for col in range(3):
                wx = tx + tw * (0.1 + col * 0.3)
                wy = size * (0.22 + row * 0.2)
                d.rectangle((wx, wy, wx + tw * 0.22, wy + size * 0.12), fill=GOLD if (row + col) % 2 == 0 else MINT)
        d.rectangle((0, size * 0.9, size, size), fill=(62, 142, 110))
        # the clown nose on top
        n = size * 0.17
        d.ellipse((size / 2 - n / 2, size * 0.04, size / 2 + n / 2, size * 0.04 + n), fill=(235, 50, 60), outline=INK, width=max(1, size // 64))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=r, fill=255)
        img.putalpha(mask)
        return img

    save(icon(184).convert("RGB"), out / "community/community_icon_184x184.jpg")
    big = icon(256)
    big.save(out / "community/client_icon.ico", sizes=[(16, 16), (32, 32)])
    big.save(out / "community/shortcut_icon.ico", sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
    big.save(out / "community/icon_256.png")
    print("client_icon.ico, shortcut_icon.ico, icon_256.png")


if __name__ == "__main__":
    main()
