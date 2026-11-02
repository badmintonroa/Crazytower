"""Puts generated room pictures (ComfyUI, Gemini, ...) into the extra room atlases.

Each picture is named after its room art key, e.g. "food_sushi_00001_.png" or "food_sushi.png".
The crop table below cuts away what lies outside the room (outer walls, sky, the floor above or
below), the rest is scaled to the size the atlas already has for that key and pasted over the
drawn version. Night and run-down looks are made from the new day picture with filters, so all
three looks match until generated night and run-down pictures exist.

Run after make_rooms_extra.py (which redraws the whole atlas):
    python3 tools/art/import_rooms.py <folder with the pictures>
"""
import json
import os
import random
import re
import sys

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "../../game/src/main/resources/rooms")

# Inside of the room in percent of the picture: left, top, right, bottom.
CROPS = {
    "corridor": (7, 3, 92, 96),
    "dorm_empty": (7, 3, 97, 97),
    "dorm_full": (1, 3, 99, 97),
    "elevator_shaft": (0, 0, 100, 100),
    "food_cafe": (6, 3, 93, 97),
    "food_empty": (8, 3, 92, 97),
    "food_imbiss": (14, 2, 77, 97),
    "food_sushi": (1, 3, 99, 97),
    "home_astronauts": (1, 3, 99, 97),
    "home_empty": (8, 3, 92, 97),
    "home_exchange": (1, 2, 99, 97),
    "home_family": (2, 3, 98, 97),
    "home_ghost": (1, 2, 99, 97),
    "home_influencer": (6, 3, 94, 97),
    "home_penguins": (7, 3, 93, 97),
    "home_retirees": (1, 2, 99, 97),
    "home_students": (1, 2, 99, 97),
    "hotel_band": (6, 2, 94, 97),
    "hotel_business": (2, 2, 95, 90),
    "hotel_count": (2, 2, 96, 97),
    "hotel_empty": (11, 10, 90, 97),
    "hotel_officials": (2, 2, 98, 97),
    "hotel_selfie": (1, 2, 97, 97),
    "hotel_single_detective": (2, 3, 95, 97),
    "hotel_single_empty": (15, 3, 83, 97),
    "hotel_single_mime": (22, 3, 97, 97),
    "hotel_single_penguin": (17, 3, 92, 97),
    "hotel_single_salesman": (1, 2, 97, 97),
    "hotel_spacetourists": (10, 3, 93, 97),
    "hotel_suite_billionaire": (1, 3, 99, 97),
    "hotel_suite_diva": (0, 3, 100, 97),
    "hotel_suite_empty": (0, 3, 96, 97),
    "hotel_suite_queen": (0, 3, 99, 97),
    "hotel_wedding": (1, 7, 98, 84),
    "housekeeping_empty": (1, 3, 99, 97),
    "housekeeping_housegeist": (4, 3, 95, 98),
    "housekeeping_robots": (1, 2, 99, 97),
    "housekeeping_staubfrei": (1, 3, 90, 97),
    "office_atlantis": (0, 3, 100, 97),
    "office_dragonbank": (0, 3, 100, 97),
    "office_hedgefund": (0, 3, 97, 97),
    "office_luxury_empty": (0, 3, 100, 97),
    "office_rocket": (8, 3, 92, 97),
    "parking_space": (0, 2, 100, 98),
    "parking_space_blue": (0, 2, 100, 98),
    "parking_space_empty": (0, 5, 100, 82),
    "parking_space_yellow": (0, 2, 100, 98),
    "penthouse_empty": (0, 3, 100, 97),
    "penthouse_full": (0, 3, 100, 97),
    "shop_bio": (5, 6, 96, 88),
    "shop_empty": (1, 3, 99, 97),
    "shop_robo": (1, 3, 99, 97),
    "shop_spaeti": (3, 4, 93, 90),
    "special_conference": (0, 3, 100, 97),
    "special_deck": (0, 3, 100, 97),
    "special_kita": (3, 3, 96, 97),
    "special_parking": (5, 3, 95, 97),
    "special_security": (6, 3, 93, 97),
    "special_souvenirs": (1, 3, 99, 97),
    "special_subway": (0, 3, 100, 97),
    "special_teleporter": (0, 8, 100, 90),
    "special_ufo": (1, 3, 99, 97),
    "stairs": (6, 9, 90, 88),
    "station_capsule": (0, 3, 100, 97),
    "station_lift": (0, 3, 100, 97),
    "station_ramp": (0, 3, 100, 97),
    "station_tank": (0, 3, 100, 97),
}

# A crop may be stretched this much to fill the room; beyond that it is trimmed instead.
MAX_STRETCH = 1.3


def key_of(filename):
    return re.sub(r"(_\d+_?)?\.(png|jpe?g|webp)$", "", filename, flags=re.I)


def fit(im, box, size):
    """Crops the room out of the picture and scales it to the atlas size."""
    w, h = im.size
    l, t, r, b = (box[0] * w / 100, box[1] * h / 100, box[2] * w / 100, box[3] * h / 100)
    want = size[0] / size[1]
    have = (r - l) / (b - t)
    if have < want / MAX_STRETCH:      # too tall: trim top and bottom alike
        nh = (r - l) / (want / MAX_STRETCH)
        t, b = t + ((b - t) - nh) / 2, t + ((b - t) + nh) / 2
    elif have > want * MAX_STRETCH:    # too wide: trim both sides alike
        nw = (b - t) * want * MAX_STRETCH
        l, r = l + ((r - l) - nw) / 2, l + ((r - l) + nw) / 2
    return im.crop((round(l), round(t), round(r), round(b))).resize(size, Image.LANCZOS)


def night(day):
    """Lights off: dark and blue, only lamps, screens and other very bright spots keep glowing."""
    rgb = day.convert("RGB")
    dark = ImageChops.multiply(rgb, Image.new("RGB", rgb.size, (66, 74, 120)))
    lum = rgb.convert("L")
    # sky through the windows is bright too, but blue: it must not glow
    r, g, b = rgb.split()
    not_sky = ImageChops.subtract(ImageChops.add(r, Image.new("L", rgb.size, 30)), b).point(lambda v: 255 if v > 0 else 0)
    glow = ImageChops.multiply(lum.point(lambda v: max(0, v - 215) * 6), not_sky)
    lit = Image.composite(rgb, dark, glow.filter(ImageFilter.GaussianBlur(1)))
    bloom = ImageChops.multiply(rgb, Image.merge("RGB", [glow.filter(ImageFilter.GaussianBlur(10))] * 3))
    out = ImageChops.add(lit, ImageEnhance.Brightness(bloom).enhance(0.6))
    return out.convert("RGBA")


def rundown(day, seed):
    """Neglected: faded, yellowed and grimy, with cracks, stains and cobwebs."""
    rnd = random.Random(seed)
    w, h = day.size
    im = ImageEnhance.Color(day.convert("RGB")).enhance(0.55)
    im = ImageChops.multiply(im, Image.new("RGB", im.size, (235, 215, 175)))
    # blotchy grime: coarse noise blown up and blurred
    noise = Image.new("L", (max(1, w // 24), max(1, h // 24)))
    noise.putdata([rnd.randint(150, 255) for _ in range(noise.width * noise.height)])
    noise = noise.resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(6))
    im = ImageChops.multiply(im, Image.merge("RGB", [noise] * 3))
    d = ImageDraw.Draw(im)
    s = h / 192
    # water stains on the ceiling and walls
    for _ in range(max(2, w // 200)):
        x, y = rnd.uniform(0, w), rnd.uniform(0.1 * h, 0.45 * h)
        rx, ry = rnd.uniform(14, 30) * s, rnd.uniform(8, 18) * s
        stain = Image.new("L", im.size, 0)
        ImageDraw.Draw(stain).ellipse((x - rx, y - ry, x + rx, y + ry), fill=70)
        im = Image.composite(Image.new("RGB", im.size, (110, 90, 50)), im, stain.filter(ImageFilter.GaussianBlur(5 * s)))
    d = ImageDraw.Draw(im)
    # cracks running down from the ceiling
    for _ in range(max(2, w // 160)):
        x, y = rnd.uniform(0.05 * w, 0.95 * w), rnd.uniform(0.08 * h, 0.2 * h)
        pts = [(x, y)]
        for _ in range(rnd.randint(3, 6)):
            x += rnd.uniform(-10, 10) * s
            y += rnd.uniform(6, 14) * s
            pts.append((x, y))
        d.line(pts, fill=(40, 34, 30), width=max(1, round(1.5 * s)))
    # cobwebs in the upper corners
    for cx, sx in ((0, 1), (w, -1)):
        cy, n = 0.06 * h, 28 * s
        for i in range(5):
            a = i / 4
            d.line([(cx, cy), (cx + sx * n * (1 - a) * 1.6, cy + n * a * 1.6)], fill=(225, 225, 225), width=1)
        for ring in (0.35, 0.65, 0.95):
            pts = [(cx + sx * n * (1 - i / 4) * 1.6 * ring, cy + n * (i / 4) * 1.6 * ring) for i in range(5)]
            d.line(pts, fill=(225, 225, 225), width=1)
    # rubbish on the floor
    for _ in range(max(3, w // 120)):
        x, y = rnd.uniform(0.04 * w, 0.96 * w), rnd.uniform(0.84 * h, 0.9 * h)
        rw, rh = rnd.uniform(5, 11) * s, rnd.uniform(3, 6) * s
        c = rnd.choice([(230, 225, 205), (170, 150, 110), (120, 140, 150)])
        d.polygon([(x - rw, y), (x - rw / 3, y - rh), (x + rw, y - rh / 2), (x + rw / 2, y + rh / 3)], fill=c, outline=(40, 34, 30))
    return ImageEnhance.Brightness(im).enhance(0.9).convert("RGBA")


def frames(name):
    with open(os.path.join(OUT, name + ".json")) as f:
        return json.load(f)


def paste(atlas, frame, picture):
    f = frame["frame"]
    atlas.paste(Image.new("RGBA", (f["w"], f["h"]), (0, 0, 0, 0)), (f["x"], f["y"]))
    atlas.paste(picture, (f["x"], f["y"]))


def main(src):
    sheets = {n: (Image.open(os.path.join(OUT, n + ".png")).convert("RGBA"), frames(n)["frames"])
              for n in ("extra", "extra_night", "extra_rundown")}
    done, unknown = [], []
    for filename in sorted(os.listdir(src)):
        key = key_of(filename)
        if key not in CROPS or key + ".png" not in sheets["extra"][1]:
            unknown.append(filename)
            continue
        f = sheets["extra"][1][key + ".png"]["frame"]
        day = fit(Image.open(os.path.join(src, filename)).convert("RGB"), CROPS[key], (f["w"], f["h"])).convert("RGBA")
        paste(sheets["extra"][0], sheets["extra"][1][key + ".png"], day)
        # parking spaces have no night or run-down look
        if key + "_night.png" in sheets["extra_night"][1]:
            paste(sheets["extra_night"][0], sheets["extra_night"][1][key + "_night.png"], night(day))
        if key + "_rundown.png" in sheets["extra_rundown"][1]:
            paste(sheets["extra_rundown"][0], sheets["extra_rundown"][1][key + "_rundown.png"], rundown(day, key))
        done.append(key)
    for n, (atlas, _) in sheets.items():
        atlas.save(os.path.join(OUT, n + ".png"), optimize=True)
    print(f"{len(done)} rooms imported")
    if unknown:
        print("skipped (no room with that name):", ", ".join(unknown))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
