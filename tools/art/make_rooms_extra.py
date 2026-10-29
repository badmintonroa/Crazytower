"""Draws the rooms that have no generated art yet (paths, homes, food) in a flat cartoon style.

Writes game/src/main/resources/rooms/extra.png and extra.json, a TexturePacker hash atlas
at 4x like the main room atlas. Run: python3 tools/art/make_rooms_extra.py
"""
import json
import os
from PIL import Image, ImageDraw, ImageFont

S = 4                       # atlas scale
SEG_W, H = 24 * S, 48 * S   # one segment, full room height
INK = (27, 29, 43)
OUT = os.path.join(os.path.dirname(__file__), "../../game/src/main/resources/rooms")

FONT_PATHS = ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]


def font(size):
    for p in FONT_PATHS:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


class Room:
    def __init__(self, segments, wall, floor=(46, 82, 92), ceiling=(30, 35, 54)):
        self.w = segments * SEG_W
        self.im = Image.new("RGBA", (self.w, H), wall)
        self.d = ImageDraw.Draw(self.im)
        self.wall = wall
        d = self.d
        # wall texture: soft vertical stripes
        for x in range(0, self.w, 48):
            d.rectangle([x, 16, x + 23, H - 22], fill=shade(wall, 0.97))
        d.rectangle([0, 0, self.w, 15], fill=ceiling)
        d.line([0, 15, self.w, 15], fill=INK, width=3)
        for x in range(40, self.w, 144):
            d.rectangle([x, 15, x + 40, 20], fill=(240, 240, 220), outline=INK, width=2)
        d.rectangle([0, H - 22, self.w, H], fill=floor)
        d.rectangle([0, H - 26, self.w, H - 22], fill=shade(wall, 0.75))
        d.line([0, H - 22, self.w, H - 22], fill=INK, width=3)
        for x in range(0, self.w, 32):
            d.line([x, H - 21, x - 10, H], fill=shade(floor, 0.85), width=2)
        d.rectangle([0, 0, 3, H], fill=INK)
        d.rectangle([self.w - 4, 0, self.w, H], fill=INK)

    def box(self, x, y, w, h, fill, width=3):
        self.d.rectangle([x, y, x + w, y + h], fill=fill, outline=INK, width=width)

    def window(self, x, y, w, h, sky=(150, 196, 230), night=False):
        self.box(x, y, w, h, (255, 255, 255))
        self.d.rectangle([x + 5, y + 5, x + w - 5, y + h - 5], fill=sky)
        for i in range(3):
            bx = x + 8 + i * (w - 16) // 3
            self.d.rectangle([bx, y + h - 22 - i * 8, bx + (w - 16) // 3 - 4, y + h - 6], fill=shade(sky, 0.75))
        self.d.line([x + w // 2, y, x + w // 2, y + h], fill=INK, width=3)

    def plant(self, x, base=None):
        base = base or H - 24
        self.box(x, base - 22, 24, 22, (196, 112, 70))
        for dx, dy, r in [(12, -40, 16), (2, -32, 11), (22, -30, 11), (12, -56, 10)]:
            self.d.ellipse([x + dx - r, base + dy - r, x + dx + r, base + dy + r], fill=(76, 160, 92), outline=INK, width=3)

    def sign(self, x, y, text, bg=(255, 244, 214), fg=INK, size=15, pad=6):
        f = font(size)
        lines = text.split("\n")
        tw = max(self.d.textlength(t, font=f) for t in lines)
        th = len(lines) * (size + 3)
        self.box(x, y, tw + 2 * pad, th + 2 * pad - 2, bg)
        for i, t in enumerate(lines):
            self.d.text((x + pad, y + pad + i * (size + 3) - 2), t, fill=fg, font=f)

    def sofa(self, x, w, color):
        base = H - 24
        self.box(x, base - 44, w, 26, shade(color, 0.85))
        self.box(x - 6, base - 30, w + 12, 24, color)
        self.box(x - 10, base - 38, 14, 32, shade(color, 0.8))
        self.box(x + w - 4, base - 38, 14, 32, shade(color, 0.8))
        self.box(x + 4, base - 8, 8, 8, INK, 1)
        self.box(x + w - 12, base - 8, 8, 8, INK, 1)

    def table(self, x, w, color=(150, 104, 70), h=40):
        base = H - 24
        self.box(x, base - h, w, 8, color)
        self.box(x + 6, base - h + 8, 6, h - 8, shade(color, 0.7), 2)
        self.box(x + w - 12, base - h + 8, 6, h - 8, shade(color, 0.7), 2)

    def crate(self, x, w=34, h=28):
        base = H - 24
        self.box(x, base - h, w, h, (198, 158, 104))
        self.d.line([x, base - h + 9, x + w, base - h + 9], fill=INK, width=2)

    def save(self):
        return self.im


def corridor():
    r = Room(6, (206, 212, 222))
    for x in (60, 330):
        r.box(x, 52, 60, 116, (122, 132, 160))
        r.d.ellipse([x + 46, 108, x + 54, 116], fill=(240, 210, 90), outline=INK, width=2)
    r.sign(200, 60, "NOTAUSGANG\n(vermutlich)", bg=(90, 190, 120), fg=(255, 255, 255), size=13)
    r.plant(490)
    r.box(220, 140, 70, 28, (220, 90, 80))  # fire extinguisher shelf
    return r.save()


def stairs():
    r = Room(2, (196, 204, 216))
    steps = 9
    for i in range(steps):
        x = 12 + i * 18
        y = H - 24 - (i + 1) * 17
        r.box(x, y, 192 - 12 - x, 17, (150, 160, 182) if i % 2 else (166, 176, 196), 2)
    r.d.line([14, H - 60, 180, 30], fill=INK, width=4)
    r.d.line([14, H - 62, 180, 28], fill=(230, 190, 80), width=2)
    r.sign(16, 26, "↑ 1", size=14)
    return r.save()


def elevator():
    r = Room(2, (176, 186, 204))
    r.box(36, 34, 120, 134, (98, 106, 126))
    r.box(42, 40, 54, 128, (190, 198, 214))
    r.box(96, 40, 54, 128, (190, 198, 214))
    r.d.line([96, 40, 96, 168], fill=INK, width=3)
    r.box(70, 20, 52, 12, (24, 24, 36), 2)
    r.d.text((82, 18), "▲▼", fill=(255, 120, 90), font=font(11))
    r.box(162, 96, 14, 26, (220, 220, 230), 2)
    r.d.ellipse([165, 100, 173, 108], fill=(255, 200, 80), outline=INK, width=1)
    r.d.ellipse([165, 110, 173, 118], fill=(255, 200, 80), outline=INK, width=1)
    return r.save()


def home_empty():
    r = Room(6, (232, 226, 210), floor=(140, 100, 70))
    r.window(60, 40, 120, 80)
    r.window(380, 40, 120, 80)
    r.crate(210); r.crate(250, 30, 22); r.crate(228, 26, 18)
    r.sign(230, 50, "ZU\nVERMIETEN", bg=(255, 255, 255), size=14)
    return r.save()


def home_family():
    r = Room(6, (244, 214, 170), floor=(150, 104, 72))
    r.window(40, 38, 110, 80)
    r.sofa(60, 110, (90, 140, 210))
    r.box(200, 74, 76, 50, (36, 40, 56))  # tv
    r.d.rectangle([206, 80, 270, 118], fill=(120, 200, 220))
    r.table(196, 84, h=30)
    r.box(330, 60, 110, 108, (240, 240, 236))  # kitchen
    r.box(330, 120, 110, 48, (210, 120, 90))
    r.d.ellipse([350, 100, 380, 116], fill=(80, 80, 90), outline=INK, width=2)
    r.d.ellipse([480, 146, 500, 166], fill=(255, 90, 90), outline=INK, width=2)  # ball
    r.box(510, 132, 26, 36, (250, 210, 70))  # toy
    r.sign(460, 40, "GRILLEN\nVERBOTEN", bg=(255, 255, 255), size=12)
    return r.save()


def home_students():
    r = Room(6, (190, 206, 170), floor=(110, 96, 86))
    r.window(400, 38, 110, 80, sky=(110, 120, 170))
    r.box(40, 148, 140, 20, (230, 230, 250))  # mattress
    r.box(40, 140, 40, 12, (250, 250, 250), 2)
    for i in range(5):
        r.box(210, 160 - i * 10, 60, 10, (230, 180, 110), 2)  # pizza boxes
    r.sign(66, 40, "WG-PLAN:\nKEINER", bg=(255, 255, 255), size=12)
    r.box(300, 60, 60, 80, (240, 90, 140))  # poster
    r.d.text((310, 86), "ROCK", fill=(255, 255, 255), font=font(16))
    for i in range(6):
        r.d.ellipse([320 + i * 8, 140 - i * 6, 344 + i * 8, 150 - i * 6], fill=(240, 240, 240), outline=INK, width=2)
    r.box(530, 120, 30, 48, (60, 60, 70))  # speaker
    return r.save()


def home_retirees():
    r = Room(6, (226, 206, 220), floor=(126, 90, 80))
    r.window(250, 36, 130, 86)
    r.sofa(60, 70, (170, 110, 120))
    r.sofa(430, 70, (170, 110, 120))
    r.d.ellipse([160, 40, 200, 80], fill=(250, 246, 230), outline=INK, width=3)  # clock
    r.d.line([180, 60, 180, 46], fill=INK, width=2); r.d.line([180, 60, 190, 62], fill=INK, width=2)
    r.box(300, 110, 30, 14, (40, 40, 50))  # binoculars on sill
    r.plant(540)
    r.table(200, 50, h=30)
    r.d.ellipse([212, 120, 238, 132], fill=(240, 240, 240), outline=INK, width=2)
    return r.save()


def home_influencer():
    r = Room(6, (250, 220, 236), floor=(220, 180, 200))
    r.window(420, 38, 120, 82)
    r.sofa(80, 140, (240, 120, 180))
    r.d.ellipse([290, 40, 370, 120], outline=INK, width=10)
    r.d.ellipse([294, 44, 366, 116], outline=(255, 250, 220), width=6)  # ring light
    r.d.line([330, 120, 330, 168], fill=INK, width=4)
    r.box(380, 110, 30, 22, (40, 40, 50))  # camera
    r.d.line([395, 132, 395, 168], fill=INK, width=3)
    r.sign(70, 40, "#LIKE", bg=(255, 255, 255), fg=(230, 60, 140), size=16)
    return r.save()


def home_penguins():
    r = Room(6, (196, 230, 246), floor=(230, 246, 255))
    for x, w, h in [(40, 70, 60), (120, 50, 40), (420, 90, 70), (520, 40, 30)]:
        r.box(x, H - 24 - h, w, h, (230, 248, 255))
    r.sign(220, 44, "HEIZUNG: -10 °C", bg=(255, 255, 255), size=13)
    for x in (300, 350):
        r.d.ellipse([x, 110, x + 30, 168], fill=INK)
        r.d.ellipse([x + 6, 122, x + 24, 166], fill=(255, 255, 255))
        r.d.polygon([(x + 12, 124), (x + 22, 128), (x + 12, 132)], fill=(250, 170, 60))
    r.box(200, 150, 50, 18, (120, 160, 220))
    return r.save()


def home_ghost():
    r = Room(6, (110, 96, 140), floor=(70, 60, 90), ceiling=(30, 26, 44))
    r.window(400, 40, 110, 80, sky=(40, 40, 80))
    r.sofa(70, 110, (90, 70, 110))
    r.d.ellipse([260, 60, 330, 130], fill=(240, 240, 255), outline=INK, width=3)
    r.d.rectangle([263, 96, 327, 150], fill=(240, 240, 255))
    r.d.ellipse([280, 84, 290, 96], fill=INK); r.d.ellipse([302, 84, 312, 96], fill=INK)
    for i in range(4):
        r.d.line([0, 20 + i * 8, 40 - i * 8, 16], fill=(200, 200, 220), width=1)
    r.sign(70, 40, "BUH!", bg=(240, 240, 255), size=14)
    return r.save()


def food_counter(r, color):
    r.box(320, 104, 220, 64, color)
    r.box(316, 96, 228, 10, shade(color, 0.7))


def food_empty():
    r = Room(6, (226, 222, 214), floor=(120, 120, 130))
    food_counter(r, (190, 190, 196))
    r.box(340, 40, 180, 40, (255, 255, 255))
    r.d.text((352, 50), "KÜCHE SUCHT KOCH", fill=INK, font=font(13))
    r.table(60, 60); r.table(170, 60)
    return r.save()


def food_imbiss():
    r = Room(6, (250, 210, 120), floor=(150, 70, 60))
    food_counter(r, (220, 70, 60))
    r.box(330, 30, 200, 56, (40, 40, 50))
    r.d.text((342, 36), "CURRYWURST 4,50", fill=(255, 220, 90), font=font(14))
    r.d.text((342, 58), "POMMES  NACH GEFÜHL", fill=(255, 255, 255), font=font(11))
    for x in (60, 170):
        r.table(x, 64, color=(240, 240, 240))
        r.box(x + 20, 120, 24, 12, (250, 210, 60), 2)
    return r.save()


def food_sushi():
    r = Room(6, (236, 228, 214), floor=(90, 70, 60))
    r.box(20, 120, 536, 20, (120, 120, 130))  # conveyor belt
    for x in range(40, 540, 60):
        r.d.ellipse([x, 108, x + 30, 124], fill=(250, 250, 250), outline=INK, width=2)
        r.d.rectangle([x + 8, 110, x + 22, 118], fill=(240, 120, 90))
    r.sign(220, 40, "BAND KAPUTT\nBITTE LAUFEN", bg=(255, 255, 255), size=12)
    for x in (60, 480):
        r.d.ellipse([x, 30, x + 36, 70], fill=(230, 60, 60), outline=INK, width=3)
    r.d.line([0, 140, 576, 140], fill=INK, width=2)
    return r.save()


def food_cafe():
    r = Room(6, (214, 182, 150), floor=(100, 70, 50))
    food_counter(r, (120, 80, 56))
    r.box(370, 56, 70, 48, (180, 180, 190))  # espresso machine
    r.d.rectangle([386, 70, 424, 86], fill=(60, 60, 70))
    r.sign(460, 40, "ESPRESSO\nSTNR 4711", bg=(255, 250, 240), size=11)
    for x in (50, 170):
        r.table(x, 70, color=(150, 110, 80))
        r.d.ellipse([x + 26, 120, x + 42, 132], fill=(255, 255, 255), outline=INK, width=2)
    r.window(40, 36, 200, 70)
    return r.save()


def special_security():
    r = Room(6, (70, 78, 98), floor=(40, 44, 60), ceiling=(20, 22, 34))
    for i in range(3):
        for j in range(2):
            x, y = 40 + i * 70, 36 + j * 50
            r.box(x, y, 60, 42, (30, 34, 46))
            r.d.rectangle([x + 5, y + 5, x + 55, y + 37], fill=(90, 200, 150) if (i + j) % 2 else (120, 170, 230))
    r.table(260, 120, color=(90, 96, 120))
    r.sign(410, 44, "SICHERHEIT\nBITTE LÄCHELN", bg=(240, 220, 80), size=12)
    r.d.ellipse([440, 104, 476, 140], fill=(240, 200, 160), outline=INK, width=3)
    r.d.rectangle([444, 114, 472, 122], fill=INK)  # sunglasses
    r.box(436, 140, 44, 28, (40, 44, 60))
    return r.save()


def special_teleporter():
    r = Room(2, (60, 52, 100), floor=(36, 30, 64), ceiling=(20, 16, 40))
    for i, c in enumerate([(120, 255, 230), (90, 210, 255), (180, 140, 255)]):
        r.d.ellipse([30 + i * 8, 60 + i * 10, 162 - i * 8, 170 - i * 4], outline=c, width=4)
    r.box(26, 158, 140, 12, (150, 150, 180))
    r.d.ellipse([70, 80, 122, 132], fill=(200, 255, 245))
    r.sign(20, 22, "MEISTENS", bg=(120, 255, 230), size=11)
    return r.save()


def special_ufo():
    r = Room(6, (40, 54, 96), floor=(80, 86, 104), ceiling=(40, 54, 96))
    for x, y in [(60, 30), (200, 50), (330, 24), (480, 60), (540, 30), (120, 80)]:
        r.d.ellipse([x, y, x + 5, y + 5], fill=(255, 255, 220))
    r.d.ellipse([170, 130, 410, 176], fill=(230, 230, 90), outline=INK, width=4)
    r.d.text((262, 136), "UFO", fill=INK, font=font(26))
    r.d.ellipse([240, 48, 340, 88], fill=(170, 190, 210), outline=INK, width=3)
    r.d.ellipse([266, 30, 314, 66], fill=(150, 255, 200), outline=INK, width=3)
    r.sign(440, 100, "PARKEN NUR\nMIT GOLD", bg=(255, 255, 255), size=11)
    return r.save()


def special_parking():
    r = Room(6, (110, 112, 120), floor=(64, 66, 74), ceiling=(50, 52, 62))
    d = r.d
    # concrete pillars and painted bays
    for x in (8, 280, 552):
        r.box(x, 20, 24, 150, (150, 150, 156))
        d.rectangle([x + 2, 120, x + 22, 132], fill=(240, 200, 60))
    for x in range(40, 560, 120):
        d.line([x, H - 22, x + 20, H], fill=(240, 240, 230), width=4)
    cars = [(48, (220, 70, 70)), (168, (70, 140, 220)), (330, (240, 200, 60)), ]
    for x, color in cars:
        r.box(x, 132, 96, 30, color)
        r.box(x + 16, 112, 60, 24, shade(color, 0.85))
        d.rectangle([x + 22, 116, x + 70, 132], fill=(170, 210, 235))
        for wx in (x + 18, x + 76):
            d.ellipse([wx - 10, 154, wx + 10, 174], fill=INK)
            d.ellipse([wx - 4, 160, wx + 4, 168], fill=(180, 180, 190))
    # the one parked crosswise since 1987
    # seen from behind: it blocks two bays
    r.box(450, 116, 70, 48, (200, 120, 200))
    d.rectangle([460, 122, 510, 138], fill=(170, 210, 235))
    for lx in (456, 504):
        d.rectangle([lx, 146, lx + 10, 154], fill=(240, 70, 70))
    d.rectangle([462, 164, 474, 174], fill=INK)
    d.rectangle([496, 164, 508, 174], fill=INK)
    r.sign(100, 34, "P", bg=(60, 110, 220), size=26)
    r.sign(330, 34, "NICHT QUER\nPARKEN (SEIT 1987)", bg=(255, 255, 255), size=11)
    return r.save()


ROOMS = {
    "corridor": corridor, "stairs": stairs, "elevator_shaft": elevator,
    "home_empty": home_empty, "home_family": home_family, "home_students": home_students,
    "home_retirees": home_retirees, "home_influencer": home_influencer,
    "home_penguins": home_penguins, "home_ghost": home_ghost,
    "food_empty": food_empty, "food_imbiss": food_imbiss, "food_sushi": food_sushi, "food_cafe": food_cafe,
    "special_security": special_security, "special_teleporter": special_teleporter, "special_ufo": special_ufo,
    "special_parking": special_parking,
}


def main():
    images = {name: fn() for name, fn in ROOMS.items()}
    pad = 8
    cols = 3
    cell_w = 6 * SEG_W + pad
    rows = (len(images) + cols - 1) // cols
    atlas = Image.new("RGBA", (cols * cell_w, rows * (H + pad)), (0, 0, 0, 0))
    frames = {}
    for i, (name, im) in enumerate(images.items()):
        x, y = (i % cols) * cell_w, (i // cols) * (H + pad)
        atlas.paste(im, (x, y))
        w, h = im.size
        frames[name + ".png"] = {
            "frame": {"x": x, "y": y, "w": w, "h": h}, "rotated": False, "trimmed": False,
            "spriteSourceSize": {"x": 0, "y": 0, "w": w, "h": h}, "sourceSize": {"w": w, "h": h},
        }
    atlas.save(os.path.join(OUT, "extra.png"))
    with open(os.path.join(OUT, "extra.json"), "w") as f:
        json.dump({"frames": frames, "meta": {"image": "extra.png", "scale": str(S),
                   "size": {"w": atlas.width, "h": atlas.height}}}, f, indent=1)


if __name__ == "__main__":
    main()
