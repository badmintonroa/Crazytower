"""Draws the rooms that have no generated art yet (paths, homes, food, hotel, luxury offices)
in a flat cartoon style, each by day, at night and run down.

Writes game/src/main/resources/rooms/extra.png, extra_night.png and extra_rundown.png, each with
a TexturePacker hash atlas JSON at 4x like the main room atlas. Run: python3 tools/art/make_rooms_extra.py
"""
import json
import math
import os
import random
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

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
        self.windows = []   # (x, y, w, h): turn dark at night, get boarded up when run down
        self.lamps = []     # x of the ceiling lamps: they light the room at night
        self.glows = []     # (x, y, w, h): screens and candles that keep glowing at night
        d = self.d
        # wall texture: soft vertical stripes
        for x in range(0, self.w, 48):
            d.rectangle([x, 16, x + 23, H - 22], fill=shade(wall, 0.97))
        d.rectangle([0, 0, self.w, 15], fill=ceiling)
        d.line([0, 15, self.w, 15], fill=INK, width=3)
        for x in range(40, self.w, 144):
            d.rectangle([x, 15, x + 40, 20], fill=(240, 240, 220), outline=INK, width=2)
            self.lamps.append(x + 20)
        d.rectangle([0, H - 22, self.w, H], fill=floor)
        d.rectangle([0, H - 26, self.w, H - 22], fill=shade(wall, 0.75))
        d.line([0, H - 22, self.w, H - 22], fill=INK, width=3)
        for x in range(0, self.w, 32):
            d.line([x, H - 21, x - 10, H], fill=shade(floor, 0.85), width=2)
        d.rectangle([0, 0, 3, H], fill=INK)
        d.rectangle([self.w - 4, 0, self.w, H], fill=INK)

    def box(self, x, y, w, h, fill, width=3):
        self.d.rectangle([x, y, x + w, y + h], fill=fill, outline=INK, width=width)

    def glow(self, x, y, w, h):
        self.glows.append((x, y, w, h))

    def window(self, x, y, w, h, sky=(150, 196, 230)):
        self.windows.append((x, y, w, h))
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
        return self


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
    r.glow(206, 80, 64, 38)
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
    r.glow(290, 40, 80, 80)
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
            r.glow(x + 5, y + 5, 50, 32)
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
    r.glow(30, 60, 132, 110)
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


def parking_bay(car=None):
    """One parking space, two segments wide, with a car in it or empty."""
    r = Room(2, (110, 112, 120), floor=(64, 66, 74), ceiling=(50, 52, 62))
    d = r.d
    r.box(4, 20, 18, 150, (150, 150, 156))
    d.rectangle([6, 120, 20, 132], fill=(240, 200, 60))
    for x in (28, 180):
        d.line([x, H - 22, x + 10, H], fill=(240, 240, 230), width=4)
    r.sign(120, 30, "P", bg=(60, 110, 220), size=22)
    if car is None:
        d.text((70, 140), "FREI", fill=(240, 240, 230), font=font(18))
        return r.save()
    x = 46
    r.box(x, 132, 110, 30, car)
    r.box(x + 18, 110, 70, 26, shade(car, 0.85))
    d.rectangle([x + 24, 114, x + 82, 132], fill=(170, 210, 235))
    for wx in (x + 22, x + 88):
        d.ellipse([wx - 11, 153, wx + 11, 175], fill=INK)
        d.ellipse([wx - 4, 160, wx + 4, 168], fill=(180, 180, 190))
    return r.save()


# ---------------------------------------------------------------- luxury offices (12 segments)

GOLD = (232, 186, 74)


def bed(r, x, w=150, sheet=(250, 250, 250), blanket=(150, 60, 80), frame=(120, 80, 60), lift=0):
    base = H - 24 - lift
    r.box(x - 8, base - 66, 14, 66, frame)              # headboard
    r.box(x, base - 34, w, 22, sheet)
    r.box(x + 30, base - 40, w - 30, 28, blanket)
    r.box(x + 4, base - 44, 30, 14, (255, 255, 255), 2)  # pillow
    r.box(x + w - 6, base - 30, 10, 30, frame, 2)
    r.box(x, base - 12, 6, 12, frame, 2)


def nightstand(r, x, lamp=(250, 230, 160)):
    base = H - 24
    r.box(x, base - 34, 34, 34, (150, 104, 70))
    r.d.line([x + 4, base - 20, x + 30, base - 20], fill=INK, width=2)
    r.d.line([x + 17, base - 34, x + 17, base - 52], fill=INK, width=3)
    r.d.polygon([(x + 4, base - 52), (x + 30, base - 52), (x + 24, base - 70), (x + 10, base - 70)], fill=lamp, outline=INK)
    r.glow(x + 4, base - 70, 26, 18)


def door(r, x, color=(150, 90, 70), hanger=None):
    base = H - 24
    r.box(x, 46, 52, base - 46, color)
    r.box(x + 6, 54, 40, 40, shade(color, 1.12), 2)
    r.d.ellipse([x + 40, 112, x + 48, 120], fill=GOLD, outline=INK, width=2)
    if hanger:
        r.sign(x + 26, 98, hanger, bg=(255, 255, 255), size=9, pad=3)


def palm(r, x):
    base = H - 24
    r.box(x, base - 26, 30, 26, (220, 220, 210))
    r.d.line([x + 15, base - 26, x + 12, base - 80], fill=(140, 100, 60), width=6)
    for dx, dy in [(-34, 6), (34, 6), (-26, -14), (26, -14), (0, -22)]:
        r.d.polygon([(x + 12, base - 80), (x + 12 + dx, base - 80 + dy), (x + 12 + dx // 2, base - 84 + dy)], fill=(70, 160, 90), outline=INK)


def chandelier(r, x):
    d = r.d
    d.line([x, 15, x, 30], fill=INK, width=3)
    d.polygon([(x - 34, 30), (x + 34, 30), (x + 22, 48), (x - 22, 48)], fill=GOLD, outline=INK)
    for i in range(5):
        cx = x - 28 + i * 14
        d.ellipse([cx - 4, 48, cx + 4, 60], fill=(255, 250, 220), outline=INK, width=1)
    r.lamps.append(x)
    r.glow(x - 34, 30, 68, 30)


def luxury_shell(wall=(238, 232, 214), floor=(70, 60, 64), ceiling=(44, 38, 52)):
    r = Room(12, wall, floor=floor, ceiling=ceiling)
    # gold trim and marble veins
    r.d.rectangle([0, 20, r.w, 24], fill=GOLD)
    rnd = random.Random(r.w)
    for _ in range(14):
        x = rnd.randrange(0, r.w)
        r.d.line([x, 30, x + rnd.randrange(-40, 40), 150], fill=shade(wall, 0.9), width=2)
    for x in range(0, r.w, 64):
        r.d.rectangle([x, H - 21, x + 30, H], fill=shade(floor, 1.25))
    return r


def office_luxury_empty():
    r = luxury_shell()
    for x in (70, 470, 870):
        r.window(x, 34, 210, 96, sky=(150, 196, 230))
    chandelier(r, 380); chandelier(r, 780)
    r.box(560, 120, 200, 48, (60, 50, 60))      # empty reception desk
    r.box(552, 112, 216, 10, GOLD)
    r.sign(586, 132, "EMPFANG (FEHLT)", bg=(60, 50, 60), fg=GOLD, size=12, pad=2)
    r.d.ellipse([310, 150, 350, 168], fill=(190, 40, 50), outline=INK, width=3)   # rolled red carpet
    r.box(330, 150, 160, 18, (190, 40, 50))
    palm(r, 30); palm(r, 1090)
    r.sign(870, 140, "ZU VERMIETEN\nNUR MIT PARKPLATZ", bg=(255, 255, 255), size=11)
    return r.save()


def office_hedgefund():
    r = luxury_shell(wall=(96, 66, 52), floor=(40, 30, 32), ceiling=(30, 22, 24))
    d = r.d
    for x in range(0, r.w, 96):
        d.rectangle([x + 6, 30, x + 90, 150], outline=shade(r.wall, 0.8), width=3)  # wood panels
    # stock ticker under the ceiling
    r.box(0, 26, r.w, 20, (20, 20, 28), 2)
    d.text((10, 28), "DAX ▲ +∞   GELD ▲ +12%   MEHR GELD ▲ +40%   MORAL ▼ -99%   KAFFEE ▲ +3%   GOLF ▲", fill=(90, 240, 120), font=font(13))
    r.glow(0, 26, r.w, 20)
    # monitor wall with charts going up
    for i in range(4):
        for j in range(2):
            x, y = 40 + i * 92, 56 + j * 50
            r.box(x, y, 84, 44, (24, 26, 36))
            pts = [(x + 6 + k * 12, y + 38 - k * k * 0.9 - (k % 2) * 4) for k in range(7)]
            d.line(pts, fill=(90, 240, 120), width=3)
            r.glow(x, y, 84, 44)
    # big desk, boss chair, money
    r.box(470, 118, 240, 14, (60, 36, 26))
    r.box(480, 132, 18, 36, (50, 30, 22)); r.box(682, 132, 18, 36, (50, 30, 22))
    r.box(560, 74, 70, 46, (30, 24, 26))       # chair back
    for k in range(6):
        r.box(486 + k * 14, 100 - k * 2, 30, 18, (110, 180, 100), 2)  # money stacks
    r.window(760, 34, 190, 96)
    # golden bull
    d.polygon([(990, 168), (990, 128), (1010, 118), (1070, 118), (1090, 106), (1100, 124), (1090, 168)], fill=GOLD, outline=INK)
    d.line([(1090, 108), (1104, 92)], fill=INK, width=4)
    r.d.ellipse([990, 110, 1006, 126], fill=GOLD, outline=INK, width=2)
    # putting green
    r.box(760, 160, 200, 8, (90, 170, 80), 2)
    d.ellipse([930, 160, 942, 166], fill=INK)
    r.sign(800, 136, "GELD & MEHR GELD", bg=GOLD, size=12)
    return r.save()


def office_dragonbank():
    r = luxury_shell(wall=(110, 100, 120), floor=(60, 52, 62), ceiling=(34, 30, 40))
    d = r.d
    rnd = random.Random(7)
    for _ in range(60):  # stone blocks
        x, y = rnd.randrange(0, r.w - 40), rnd.randrange(28, 140)
        d.rectangle([x, y, x + 40, y + 20], outline=shade(r.wall, 0.82), width=2)
    # gold hoard
    d.polygon([(320, 168), (420, 92), (470, 80), (560, 100), (680, 168)], fill=GOLD, outline=INK)
    for _ in range(70):
        x, y = rnd.randrange(360, 640), rnd.randrange(100, 166)
        d.ellipse([x, y, x + 12, y + 8], fill=(255, 220, 110), outline=shade(GOLD, 0.7), width=1)
    r.box(380, 136, 50, 32, (140, 80, 40)); r.box(380, 128, 50, 12, (160, 96, 50))   # chests
    r.box(590, 140, 46, 28, (140, 80, 40))
    d.ellipse([470, 64, 500, 84], fill=(230, 60, 90), outline=INK, width=2)          # ruby on top
    # round vault door
    d.ellipse([60, 40, 200, 168], fill=(150, 150, 160), outline=INK, width=4)
    d.ellipse([84, 64, 176, 146], outline=INK, width=3)
    for a in range(0, 360, 60):
        x2, y2 = 130 + 34 * math.cos(math.radians(a)), 104 + 34 * math.sin(math.radians(a))
        d.line([130, 104, x2, y2], fill=INK, width=4)
    # torches
    for x in (250, 740, 1080):
        r.box(x, 70, 12, 30, (100, 70, 40), 2)
        d.polygon([(x - 4, 70), (x + 6, 40), (x + 16, 70)], fill=(255, 150, 50), outline=INK)
        r.glow(x - 6, 36, 28, 36)
    r.sign(770, 40, "DRACHENBANK\nZINSEN IN FEUER", bg=(60, 40, 50), fg=GOLD, size=13)
    r.sign(860, 120, "KEINE KREDITE\nFÜR RITTER", bg=(255, 255, 255), size=11)
    # a big scorch mark
    d.ellipse([960, 150, 1040, 168], fill=(50, 40, 44))
    return r.save()


def office_atlantis():
    r = luxury_shell(wall=(60, 140, 170), floor=(214, 200, 150), ceiling=(30, 70, 100))
    d = r.d
    # portholes with fish
    for x in (60, 330, 600, 870):
        d.ellipse([x, 40, x + 90, 130], fill=(170, 150, 110), outline=INK, width=4)
        d.ellipse([x + 10, 50, x + 80, 120], fill=(30, 90, 160), outline=INK, width=3)
        d.polygon([(x + 30, 84), (x + 50, 74), (x + 60, 84), (x + 50, 94)], fill=(250, 160, 60), outline=INK)
        d.polygon([(x + 60, 84), (x + 70, 76), (x + 70, 92)], fill=(250, 160, 60), outline=INK)
    # greek columns
    for x in (220, 490, 760, 1030):
        r.box(x, 30, 26, 138, (240, 236, 220))
        r.box(x - 6, 26, 38, 10, (240, 236, 220), 2)
        for k in range(3):
            d.line([x + 7 + k * 6, 40, x + 7 + k * 6, 160], fill=shade((240, 236, 220), 0.85), width=2)
    # coral and seaweed
    for x in (140, 420, 700, 980):
        for k in range(3):
            d.line([x + k * 10, 168, x + k * 10 + (6 if k % 2 else -6), 120 + k * 8], fill=(60, 170, 90), width=6)
    d.polygon([(560, 168), (566, 130), (576, 150), (586, 120), (594, 168)], fill=(250, 110, 120), outline=INK)
    # bubbles
    rnd = random.Random(3)
    for _ in range(30):
        x, y, s = rnd.randrange(10, r.w - 10), rnd.randrange(30, 150), rnd.randrange(4, 10)
        d.ellipse([x, y, x + s, y + s], outline=(220, 250, 255), width=2)
    # trident
    d.line([1110, 168, 1110, 60], fill=GOLD, width=5)
    for dx in (-12, 0, 12):
        d.line([1110 + dx, 60, 1110 + dx, 44], fill=GOLD, width=4)
    d.line([1098, 60, 1122, 60], fill=GOLD, width=4)
    r.sign(250, 138, "VISA NUR\nBEI EBBE", bg=(255, 255, 255), size=11)
    r.sign(620, 140, "KONSULAT VON ATLANTIS", bg=GOLD, size=12)
    d.ellipse([300, 160, 420, 170], fill=(120, 190, 230))   # puddle
    return r.save()


# ---------------------------------------------------------------- hotel rooms

def hotel_shell(wall=(150, 50, 70)):
    r = Room(6, wall, floor=(90, 40, 50), ceiling=(50, 24, 34))
    for x in range(0, r.w, 24):
        r.d.rectangle([x, 26, x + 6, H - 28], fill=shade(wall, 1.12))   # wallpaper stripes
    r.d.rectangle([0, 132, r.w, 136], fill=GOLD)
    return r


def hotel_empty():
    r = hotel_shell()
    r.window(250, 36, 120, 80)
    bed(r, 70)
    r.d.polygon([(150, 132), (170, 116), (176, 124), (190, 120), (184, 134)], fill=(255, 255, 255), outline=INK)  # towel swan
    nightstand(r, 236)
    door(r, 480, hanger="BITTE\nWECKEN")
    r.sign(390, 46, "FREI", bg=(90, 190, 120), fg=(255, 255, 255), size=16)
    return r.save()


def hotel_business():
    r = hotel_shell(wall=(90, 96, 120))
    r.window(250, 36, 120, 80)
    bed(r, 70, blanket=(60, 70, 100))
    r.box(130, 118, 40, 20, (40, 40, 50), 2); r.d.rectangle([134, 120, 166, 130], fill=(120, 200, 230)); r.glow(134, 120, 32, 10)
    nightstand(r, 236)
    for i, c in enumerate([(220, 70, 60), (70, 140, 220), (240, 200, 60)]):
        r.d.line([250 + i * 6, 80 + i * 3, 254 + i * 6, 110], fill=c, width=4)     # ties on the lamp
    for k in range(3):
        r.box(400 + k * 34, 140, 22, 12, (40, 40, 50), 2)   # three phones ringing
        r.d.text((404 + k * 34, 120), "!", fill=(255, 220, 90), font=font(16))
    r.box(470, 120, 70, 48, (60, 50, 50))   # suitcase
    r.d.rectangle([496, 110, 514, 120], outline=INK, width=3)
    r.sign(400, 40, "MEETING 1+2+3\nGLEICHZEITIG", bg=(255, 255, 255), size=11)
    return r.save()


def hotel_selfie():
    r = hotel_shell(wall=(240, 170, 90))
    r.window(250, 36, 120, 80)
    bed(r, 60, blanket=(240, 120, 160))
    for x in (230, 290, 350):   # selfie sticks
        r.d.line([x, 168, x + 20, 70], fill=INK, width=4)
        r.box(x + 14, 58, 16, 24, (40, 40, 50), 2)
    r.box(470, 120, 30, 48, (220, 60, 50))    # fire extinguisher, photographed from every side
    r.d.rectangle([478, 110, 492, 120], fill=INK)
    for x, y in [(420, 50), (520, 50), (450, 86), (520, 96)]:
        r.box(x, y, 34, 26, (255, 255, 255), 2)
        r.d.rectangle([x + 4, y + 4, x + 30, y + 22], fill=(220, 60, 50))
    r.box(150, 140, 40, 28, (120, 180, 220)); r.d.text((154, 146), "NYC", fill=INK, font=font(11))
    return r.save()


def hotel_wedding():
    r = hotel_shell(wall=(250, 220, 230))
    r.window(250, 36, 120, 80)
    bed(r, 70, blanket=(255, 255, 255), frame=(200, 160, 120))
    d = r.d
    for k in range(9):   # heart garland
        x = 30 + k * 60
        d.polygon([(x, 34), (x + 8, 28), (x + 14, 34), (x + 7, 44)], fill=(230, 70, 100), outline=INK)
    d.line([20, 32, 560, 32], fill=INK, width=1)
    for x, c in [(420, (230, 70, 100)), (450, (255, 200, 220)), (480, (255, 255, 255))]:   # balloons
        d.line([x + 10, 100, x + 14, 168], fill=INK, width=1)
        d.ellipse([x, 60, x + 22, 98], fill=c, outline=INK, width=2)
    r.box(250, 130, 60, 38, (255, 250, 240))   # cake (the rest is in the elevator)
    r.box(260, 112, 40, 18, (255, 250, 240))
    d.ellipse([276, 102, 284, 112], fill=(230, 70, 100))
    rnd = random.Random(5)
    for _ in range(40):
        x = rnd.randrange(10, 560)
        y = 172 + rnd.randrange(0, 14)
        d.rectangle([x, y, x + 4, y + 4], fill=rnd.choice([(230, 70, 100), (250, 220, 90), (120, 200, 240)]))
    r.sign(380, 120, "JUST\nMARRIED", bg=(255, 255, 255), fg=(230, 70, 100), size=12)
    return r.save()


def hotel_count():
    r = hotel_shell(wall=(70, 30, 50))
    d = r.d
    # the window is covered with black velvet
    r.box(250, 36, 120, 80, (20, 16, 24))
    for x in range(256, 366, 14):
        d.line([x, 38, x, 114], fill=(50, 40, 60), width=3)
    # coffin instead of a bed
    d.polygon([(70, 168), (60, 140), (90, 124), (250, 124), (260, 140), (240, 168)], fill=(90, 50, 40), outline=INK)
    d.line([150, 130, 150, 160], fill=GOLD, width=4); d.line([138, 140, 162, 140], fill=GOLD, width=4)
    for x in (290, 330, 520):   # candles
        r.box(x, 136, 10, 32, (250, 246, 230), 2)
        d.polygon([(x + 1, 136), (x + 5, 122), (x + 9, 136)], fill=(255, 200, 80))
        r.glow(x - 4, 118, 18, 22)
    d.polygon([(420, 60), (440, 70), (450, 60), (460, 70), (480, 60), (466, 84), (450, 78), (434, 84)], fill=INK)  # bat
    r.sign(380, 110, "KEIN\nKNOBLAUCH!", bg=(255, 255, 255), fg=(170, 30, 40), size=12)
    return r.save()


def hotel_band():
    r = hotel_shell(wall=(60, 60, 80))
    d = r.d
    r.box(250, 36, 120, 80, (255, 255, 255))   # broken window
    d.rectangle([255, 41, 365, 111], fill=(150, 196, 230))
    r.windows.append((250, 36, 120, 80))
    d.line([(260, 44), (300, 80), (290, 110)], fill=INK, width=2); d.line([(300, 80), (360, 60)], fill=INK, width=2)
    bed(r, 60, blanket=(40, 40, 50))
    r.box(220, 104, 90, 64, (30, 30, 36))   # amp
    for k in range(3):
        d.ellipse([234 + k * 22, 120, 252 + k * 22, 138], fill=(80, 80, 90), outline=INK, width=2)
    d.ellipse([400, 120, 470, 168], fill=(230, 70, 80), outline=INK, width=3)   # drum
    d.ellipse([410, 120, 460, 134], fill=(250, 250, 250), outline=INK, width=2)
    # picture of a TV, lying on the floor
    r.box(480, 150, 70, 18, (120, 90, 60))
    d.text((486, 152), "TV (Bild)", fill=(255, 255, 255), font=font(10))
    for k in range(4):
        r.box(150 + k * 4, 160 - k * 9, 50, 9, (230, 180, 110), 2)   # pizza boxes
    r.box(400, 40, 150, 50, (240, 90, 140))
    d.text((410, 46), "DIE LÄRMSCHUTZ-", fill=(255, 255, 255), font=font(12))
    d.text((410, 64), "WÄNDE  LIVE!", fill=(255, 255, 255), font=font(12))
    return r.save()


def hotel_spacetourists():
    r = hotel_shell(wall=(40, 50, 90))
    d = r.d
    r.window(250, 36, 120, 80, sky=(20, 24, 60))
    for x, y in [(262, 50), (300, 70), (340, 46), (280, 96)]:
        d.ellipse([x, y, x + 4, y + 4], fill=(255, 255, 220))
    # floating bed (zero gravity)
    bed(r, 70, blanket=(110, 230, 180), lift=26)
    for x in (90, 150, 210):
        d.line([x, 146, x + 4, 164], fill=(200, 220, 255), width=2)
    d.ellipse([420, 110, 470, 160], fill=(220, 230, 240), outline=INK, width=3)   # space helmet
    d.ellipse([430, 120, 460, 146], fill=(60, 90, 140))
    r.box(500, 110, 20, 58, (120, 255, 160)); r.glow(500, 110, 20, 58)   # lava lamp
    r.sign(380, 40, "BEWERTUNG\n★★★★★ / 12", bg=(255, 255, 255), size=12)
    return r.save()


# ---------------------------------------------------------------- housekeeping

def housekeeping_shell(wall=(200, 214, 220)):
    r = Room(6, wall, floor=(110, 120, 130), ceiling=(40, 48, 60))
    for y in range(40, 150, 24):
        r.d.line([0, y, r.w, y], fill=shade(wall, 0.92), width=2)   # tiles
    return r


def shelf(r, x, w=120):
    rnd = random.Random(x)
    for y in (60, 100):
        r.box(x, y, w, 6, (150, 110, 80), 2)
        for k in range(w // 20):
            c = rnd.choice([(90, 190, 230), (240, 200, 60), (230, 90, 120), (120, 220, 140)])
            r.box(x + 4 + k * 20, y - 24, 14, 24, c, 2)


def bucket(r, x):
    base = H - 24
    r.d.polygon([(x, base - 30), (x + 36, base - 30), (x + 30, base), (x + 6, base)], fill=(240, 200, 60), outline=INK)
    r.d.line([x + 18, base - 30, x + 30, base - 110], fill=(150, 110, 80), width=5)
    r.d.ellipse([x + 22, base - 120, x + 42, base - 100], fill=(240, 240, 240), outline=INK, width=2)


def washer(r, x):
    base = H - 24
    r.box(x, base - 70, 64, 70, (240, 240, 244))
    r.d.ellipse([x + 10, base - 56, x + 54, base - 12], fill=(120, 190, 230), outline=INK, width=3)
    r.d.ellipse([x + 20, base - 40, x + 30, base - 30], fill=(255, 255, 255))
    r.glow(x + 46, base - 66, 8, 6)


def housekeeping_empty():
    r = housekeeping_shell()
    shelf(r, 40)
    bucket(r, 220)
    washer(r, 300)
    r.box(420, 60, 120, 108, (170, 180, 190))   # empty lockers
    for k in range(3):
        r.d.line([420 + k * 40, 60, 420 + k * 40, 168], fill=INK, width=2)
    r.sign(190, 40, "SUCHEN\nPUTZTEUFEL", bg=(255, 255, 255), size=12)
    return r.save()


def housekeeping_staubfrei():
    r = housekeeping_shell(wall=(190, 226, 210))
    shelf(r, 30)
    washer(r, 180); washer(r, 250)
    for x in (340, 380, 420):
        bucket(r, x)
    r.box(470, 60, 80, 108, (90, 150, 200))   # lockers with uniforms
    r.d.line([510, 60, 510, 168], fill=INK, width=2)
    r.sign(160, 36, "STAUBFREI & SÖHNE\nWIR WISCHEN AUCH LAUNE", bg=(255, 255, 255), size=11)
    return r.save()


def housekeeping_robots():
    r = housekeeping_shell(wall=(210, 210, 230))
    d = r.d
    r.box(30, 140, 300, 10, (90, 96, 120), 2)   # charging dock
    for k in range(5):
        x = 40 + k * 56
        d.ellipse([x, 126, x + 44, 146], fill=(60, 64, 80), outline=INK, width=3)
        d.ellipse([x + 18, 130, x + 26, 136], fill=(90, 240, 120))
        r.glow(x + 18, 130, 8, 6)
    for k in range(3):   # robot tower
        d.ellipse([380, 150 - k * 18, 430, 168 - k * 18], fill=(60, 64, 80), outline=INK, width=3)
    # the hamster that disagrees
    r.box(460, 110, 80, 58, (255, 255, 255), 2)
    for x in range(466, 540, 8):
        d.line([x, 112, x, 166], fill=(160, 160, 170), width=1)
    d.ellipse([486, 140, 512, 162], fill=(220, 160, 90), outline=INK, width=2)
    d.text((516, 118), ">:(", fill=INK, font=font(12))
    r.sign(60, 40, "LADESTATION\nBITTE NICHT SAUGEN", bg=(255, 255, 255), size=11)
    return r.save()


def housekeeping_housegeist():
    r = housekeeping_shell(wall=(110, 96, 140))
    d = r.d
    shelf(r, 40)
    # a floating mop and a ghost with a feather duster
    d.line([260, 70, 300, 150], fill=(150, 110, 80), width=5)
    d.ellipse([290, 140, 320, 166], fill=(240, 240, 240), outline=INK, width=2)
    d.ellipse([380, 60, 450, 130], fill=(240, 240, 255), outline=INK, width=3)
    d.rectangle([383, 96, 447, 150], fill=(240, 240, 255))
    for x in (383, 404, 425):
        d.polygon([(x, 150), (x + 10, 160), (x + 21, 150)], fill=(240, 240, 255))
    d.ellipse([398, 84, 408, 96], fill=INK); d.ellipse([422, 84, 432, 96], fill=INK)
    d.line([450, 110, 490, 70], fill=(150, 110, 80), width=4)
    for k in range(5):
        d.line([490, 70, 480 + k * 6, 50], fill=(250, 120, 160), width=4)
    r.sign(190, 40, "NICHT\nHINSEHEN", bg=(240, 240, 255), size=12)
    return r.save()


def home_exchange():
    r = Room(6, (170, 230, 190), floor=(90, 120, 110))
    d = r.d
    r.window(400, 38, 110, 80)
    r.box(40, 40, 140, 80, (250, 246, 230))   # map of Earth, with notes
    d.ellipse([60, 54, 110, 100], fill=(80, 150, 220), outline=INK, width=2)
    d.ellipse([110, 60, 160, 108], fill=(80, 150, 220), outline=INK, width=2)
    d.text((70, 104), "ERDE ★★★☆☆", fill=INK, font=font(10))
    bed(r, 220, blanket=(120, 230, 120), frame=(140, 140, 160))
    for k, c in enumerate([(255, 120, 120), (255, 220, 90), (120, 200, 255)]):   # souvenirs
        r.box(40 + k * 40, 140, 26, 28, c, 2)
    d.text((44, 146), "I♥", fill=INK, font=font(11))
    r.box(520, 110, 24, 58, (120, 255, 160)); r.glow(520, 110, 24, 58)   # glowing jar
    r.sign(400, 128, "BITTE NICHT\nENTFÜHREN", bg=(255, 255, 255), size=10)
    return r.save()



# ---------------------------------------------------------------- rooms from 3 and 4 stars

def bunk(r, x, blanket=(90, 140, 210), blanket2=(230, 120, 90)):
    base = H - 24
    r.box(x, base - 110, 10, 110, (120, 90, 70))
    r.box(x + 120, base - 110, 10, 110, (120, 90, 70))
    r.box(x + 10, base - 30, 110, 14, (250, 250, 250)); r.box(x + 40, base - 34, 80, 14, blanket)
    r.box(x + 10, base - 92, 110, 14, (250, 250, 250)); r.box(x + 40, base - 96, 80, 14, blanket2)
    r.d.line([x + 4, base - 50, x + 4, base - 70], fill=INK, width=2)


def dorm_shell():
    r = Room(6, (214, 220, 190), floor=(120, 104, 90))
    r.window(250, 36, 90, 74, sky=(120, 150, 200))
    return r


def dorm_empty():
    r = dorm_shell()
    bunk(r, 30, blanket=(200, 200, 200), blanket2=(200, 200, 200))
    bunk(r, 410, blanket=(200, 200, 200), blanket2=(200, 200, 200))
    r.sign(232, 126, "6 BETTEN\nFREI", bg=(255, 255, 255), size=13)
    return r.save()


def dorm_full():
    r = dorm_shell()
    bunk(r, 30)
    bunk(r, 410, blanket=(120, 190, 110), blanket2=(240, 200, 70))
    for i in range(6):
        r.box(196, 160 - i * 9, 56, 9, (230, 180, 110), 2)   # pizza boxes
    r.box(300, 132, 70, 36, (60, 60, 70))   # speaker
    r.d.ellipse([318, 140, 352, 164], fill=(120, 120, 140), outline=INK, width=2)
    for i, c in enumerate([(255, 90, 140), (90, 220, 255), (255, 230, 90)]):
        r.d.ellipse([380 + i * 10, 40 + i * 6, 392 + i * 10, 52 + i * 6], fill=c, outline=INK, width=1)
    r.glow(250, 36, 90, 74)
    r.sign(160, 40, "PARTY\nHEUTE\n(JEDEN TAG)", bg=(240, 90, 140), fg=(255, 255, 255), size=11)
    return r.save()


def shop_shell(wall=(250, 236, 200), awning=(220, 70, 70)):
    r = Room(6, wall, floor=(110, 110, 120))
    for i in range(12):
        c = awning if i % 2 == 0 else (255, 255, 255)
        r.d.polygon([(i * 48, 18), (i * 48 + 48, 18), (i * 48 + 48, 34), (i * 48, 40)], fill=c, outline=INK)
    return r


def shelves(r, x, w, colors):
    base = H - 24
    r.box(x, 58, w, base - 58, (190, 150, 100))
    for k, y in enumerate((70, 102, 134)):
        r.d.line([x, y + 24, x + w, y + 24], fill=INK, width=3)
        for i in range(w // 16):
            c = colors[(i + k) % len(colors)]
            r.box(x + 4 + i * 16, y + 4, 12, 20, c, 2)


def counter(r, x, w=140, color=(90, 110, 150)):
    base = H - 24
    r.box(x, base - 50, w, 50, color)
    r.box(x + w - 50, base - 74, 40, 24, (40, 44, 60))   # till
    r.d.rectangle([x + w - 46, base - 70, x + w - 14, base - 58], fill=(120, 240, 160))
    r.glow(x + w - 46, base - 70, 32, 12)


def shop_empty():
    r = shop_shell(awning=(160, 160, 170))
    r.box(40, 80, 200, 88, (190, 150, 100))
    r.d.line([40, 124, 240, 124], fill=INK, width=3)
    r.crate(300); r.crate(340, 28, 20)
    r.sign(400, 70, "LADEN\nZU VERMIETEN", bg=(255, 255, 255), size=13)
    return r.save()


def shop_spaeti():
    r = shop_shell()
    shelves(r, 30, 220, [(240, 200, 60), (90, 160, 220), (220, 80, 80), (120, 200, 110)])
    counter(r, 300)
    r.box(470, 70, 80, 98, (230, 240, 250))   # fridge
    for y in (86, 116, 146):
        for i in range(4):
            r.box(478 + i * 18, y, 12, 22, (140, 90, 50), 1)
    r.sign(290, 50, "OFFEN\n24/7 (MEISTENS)", bg=(255, 244, 120), size=11)
    return r.save()


def shop_bio():
    r = shop_shell(wall=(222, 236, 200), awning=(90, 160, 90))
    shelves(r, 30, 200, [(120, 190, 90), (230, 140, 60), (200, 70, 70), (240, 220, 120)])
    for i in range(3):   # baskets of vegetables
        x = 260 + i * 66
        r.box(x, H - 70, 56, 46, (170, 120, 70))
        for k in range(4):
            r.d.ellipse([x + 4 + k * 12, H - 84, x + 18 + k * 12, H - 70], fill=[(230, 90, 60), (250, 160, 40), (110, 180, 80)][(i + k) % 3], outline=INK, width=1)
    r.plant(480); r.plant(530)
    r.sign(270, 50, "GRÜNKERN\nALLES BIO, AUCH DIE PREISE", bg=(255, 255, 255), size=10)
    return r.save()


def shop_robo():
    r = shop_shell(wall=(200, 210, 226), awning=(80, 110, 200))
    for i in range(4):   # vending machines
        x = 30 + i * 96
        r.box(x, 52, 80, 116, (60, 80, 140))
        r.d.rectangle([x + 8, 60, x + 52, 140], fill=(160, 220, 250))
        r.glow(x + 8, 60, 44, 80)
        for k in range(4):
            r.box(x + 12, 64 + k * 18, 36, 12, [(240, 90, 90), (250, 210, 70), (120, 220, 120)][(i + k) % 3], 1)
        r.box(x + 58, 80, 14, 30, (30, 30, 40), 2)
    r.d.ellipse([440, 100, 500, 160], fill=(210, 214, 224), outline=INK, width=3)   # robot head
    r.d.ellipse([452, 116, 468, 132], fill=(120, 255, 230), outline=INK, width=2)
    r.d.ellipse([472, 116, 488, 132], fill=(120, 255, 230), outline=INK, width=2)
    r.box(446, 160, 48, 8, (120, 124, 140), 2)
    r.sign(420, 44, "TANTE ROBO\nHAT NIE PAUSE", bg=(255, 255, 255), size=11)
    return r.save()


def special_kita():
    r = Room(6, (252, 230, 170), floor=(120, 170, 120))
    for i, c in enumerate([(240, 90, 90), (90, 160, 240), (250, 210, 70), (120, 200, 110)]):
        r.box(40 + i * 40, 140, 34, 28, c)   # building blocks
        r.d.text((50 + i * 40, 144), "ABCD"[i], fill=(255, 255, 255), font=font(16))
    r.window(230, 36, 110, 80)
    r.d.polygon([(380, 168), (380, 90), (470, 168)], fill=(240, 90, 140), outline=INK)   # slide
    r.box(370, 80, 14, 88, (90, 160, 240))
    for k in range(4):
        r.d.line([372, 96 + k * 18, 382, 96 + k * 18], fill=INK, width=2)
    for k in range(7):   # crayon drawings
        x = 40 + k * 26
        r.box(x, 50 + (k % 2) * 10, 22, 26, (255, 255, 255), 2)
        r.d.ellipse([x + 5, 56 + (k % 2) * 10, x + 17, 68 + (k % 2) * 10], fill=[(240, 90, 90), (250, 210, 70), (90, 160, 240)][k % 3])
    r.sign(480, 50, "RABAUKEN\nKITA", bg=(255, 255, 255), size=13)
    r.d.ellipse([500, 130, 536, 166], fill=(255, 90, 90), outline=INK, width=3)   # ball
    return r.save()


def special_subway():
    r = Room(12, (210, 214, 200), floor=(80, 84, 92), ceiling=(50, 54, 64))
    for x in range(0, r.w, 32):   # tiles
        r.d.line([x, 24, x, H - 26], fill=shade((210, 214, 200), 0.9), width=1)
    for y in range(24, H - 26, 24):
        r.d.line([0, y, r.w, y], fill=shade((210, 214, 200), 0.9), width=1)
    r.d.rectangle([0, 110, r.w, 118], fill=(240, 200, 60))   # stripe
    # the train with its windows
    r.box(140, 60, 760, 108, (90, 140, 210))
    r.d.rectangle([140, 140, 900, 150], fill=(250, 250, 250))
    for i in range(9):
        x = 170 + i * 80
        r.box(x, 74, 56, 46, (190, 220, 245))
        r.glow(x, 74, 56, 46)
        r.d.ellipse([x + 18, 90, x + 34, 106], fill=(240, 200, 160), outline=INK, width=2)   # a commuter
    r.d.polygon([(900, 60), (960, 100), (960, 168), (900, 168)], fill=(90, 140, 210), outline=INK)
    r.d.ellipse([930, 120, 950, 140], fill=(255, 240, 160), outline=INK, width=2)
    r.glow(930, 120, 20, 20)
    r.box(990, 30, 140, 50, (40, 50, 140))
    r.d.text((1006, 38), "U  CRAZY", fill=(255, 255, 255), font=font(22))
    r.sign(990, 92, "NÄCHSTER ZUG:\nGESTERN", bg=(30, 30, 40), fg=(255, 200, 60), size=12)
    r.d.line([0, H - 30, r.w, H - 30], fill=(240, 200, 60), width=4)
    return r.save()


def special_conference():
    r = luxury_shell(wall=(206, 214, 230), floor=(60, 70, 96), ceiling=(36, 42, 66))
    r.box(100, 40, 300, 100, (250, 250, 250))   # projector screen
    r.d.line([130, 120, 190, 90, 250, 104, 320, 60, 370, 70], fill=(220, 70, 70), width=5)
    r.d.text((130, 46), "SYNERGIEN Q5", fill=INK, font=font(18))
    r.glow(100, 40, 300, 100)
    r.box(450, 120, 520, 20, (120, 80, 60))   # long table
    for i in range(9):
        x = 470 + i * 56
        r.box(x, 102, 26, 18, (40, 44, 60), 2)   # chairs
        r.box(x + 4, 112, 18, 6, (240, 240, 240), 1)   # name card
    for x in (480, 640, 800, 940):
        r.box(x, 106, 14, 14, (250, 250, 250), 2)   # coffee cups
    for i in range(3):
        r.d.ellipse([1000 + i * 26, 112, 1022 + i * 26, 130], fill=(196, 140, 80), outline=INK, width=2)   # biscuits
    chandelier(r, 700)
    r.sign(1000, 40, "KONFERENZ\nKEKSE INKLUSIVE", bg=(255, 255, 255), size=11)
    return r.save()


def penthouse_shell():
    r = luxury_shell(wall=(244, 240, 232), floor=(110, 80, 60), ceiling=(60, 54, 64))
    for x in (40, 360, 680):
        r.window(x, 30, 260, 104, sky=(170, 210, 245))
    return r


def penthouse_empty():
    r = penthouse_shell()
    palm(r, 1000)
    r.box(800, 150, 160, 18, (190, 40, 50))
    r.sign(980, 40, "PENTHOUSE\nZU VERKAUFEN", bg=(255, 255, 255), size=12)
    return r.save()


def penthouse_full():
    r = penthouse_shell()
    r.sofa(90, 220, (240, 240, 236))
    r.box(420, 150, 300, 18, (90, 190, 230))   # pool
    r.d.rectangle([426, 152, 714, 166], fill=(130, 220, 250))
    r.d.ellipse([560, 140, 600, 160], fill=(250, 210, 70), outline=INK, width=2)   # rubber duck
    r.box(780, 110, 120, 58, (60, 50, 60))   # bar
    r.box(772, 104, 136, 8, GOLD)
    for i in range(4):
        r.box(790 + i * 26, 84, 12, 20, [(120, 200, 110), (220, 80, 80), (250, 210, 70), (200, 200, 250)][i], 1)
    chandelier(r, 560)
    palm(r, 1060)
    r.sign(960, 40, "BITTE NICHT\nINS AQUARIUM\nWINKEN", bg=(255, 255, 255), size=10)
    return r.save()


class Picture:
    """A finished room picture from the art folder: no lamp or window data, so its night look is a guess."""

    def __init__(self, path):
        self.im = Image.open(os.path.join(os.path.dirname(__file__), path)).convert("RGBA")
        self.w = self.im.width
        self.windows, self.glows = [], []
        self.lamps = list(range(96, self.w, 192))


# ---------------------------------------------------------------- night and run-down looks

NIGHT_SKY = (24, 30, 66)


def night(room):
    """Lights off, dark windows, warm lamp cones, screens and candles keep glowing."""
    day = room.im
    w = day.width
    dark = ImageChops.multiply(day, Image.new("RGBA", day.size, (78, 84, 140, 255)))
    lit = ImageChops.multiply(day, Image.new("RGBA", day.size, (255, 236, 196, 255)))
    mask = Image.new("L", day.size, 0)
    md = ImageDraw.Draw(mask)
    for x in room.lamps:
        md.polygon([(x - 16, 20), (x + 16, 20), (x + 70, H), (x - 70, H)], fill=150)
    for x, y, gw, gh in room.glows:
        md.rectangle([x, y, x + gw, y + gh], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(10))
    for x, y, gw, gh in room.glows:
        md2 = ImageDraw.Draw(mask)
        md2.rectangle([x, y, x + gw, y + gh], fill=255)
    out = Image.composite(lit, dark, mask)
    d = ImageDraw.Draw(out)
    rnd = random.Random(w * 31 + len(room.windows))
    for x, y, ww, wh in room.windows:
        d.rectangle([x + 5, y + 5, x + ww - 5, y + wh - 5], fill=NIGHT_SKY)
        for _ in range(ww // 14):
            sx, sy = rnd.randrange(x + 6, x + ww - 6), rnd.randrange(y + 6, y + wh // 2)
            d.point([(sx, sy), (sx + 1, sy)], fill=(255, 255, 220))
        for i in range(3):   # the skyline outside, with a few lit windows
            bx = x + 8 + i * (ww - 16) // 3
            top = y + wh - 22 - i * 8
            d.rectangle([bx, top, bx + (ww - 16) // 3 - 4, y + wh - 6], fill=(14, 18, 40))
            for k in range(3):
                if rnd.random() < 0.6:
                    lx = bx + 3 + k * 7
                    d.rectangle([lx, top + 4, lx + 3, top + 7], fill=(255, 220, 120))
        d.line([x + ww // 2, y, x + ww // 2, y + wh], fill=INK, width=3)
    return out


def rundown(room):
    """Faded colours, stains, cracks, cobwebs, a dead lamp, boarded windows and rubbish."""
    day = room.im
    w = day.width
    rnd = random.Random(w * 17 + len(room.windows) * 5 + len(room.glows))
    grey = day.convert("L").convert("RGBA")
    out = Image.blend(day, grey, 0.45)
    out = ImageChops.multiply(out, Image.new("RGBA", day.size, (226, 206, 170, 255)))
    stains = Image.new("RGBA", day.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(stains)
    for _ in range(w // 70):
        x, y, r = rnd.randrange(0, w), rnd.randrange(24, 150), rnd.randrange(14, 40)
        sd.ellipse([x - r, y - r // 2, x + r, y + r], fill=(90, 70, 30, 70))
    for _ in range(w // 150):   # water running down from the ceiling
        x = rnd.randrange(10, w - 10)
        sd.rectangle([x, 16, x + rnd.randrange(8, 20), 16 + rnd.randrange(30, 90)], fill=(80, 70, 40, 60))
    stains = stains.filter(ImageFilter.GaussianBlur(4))
    out = Image.alpha_composite(out, stains)
    d = ImageDraw.Draw(out)
    for _ in range(max(1, w // 200)):   # cracks
        x, y = rnd.randrange(20, w - 20), 18
        pts = [(x, y)]
        for _ in range(5):
            x += rnd.randrange(-14, 15); y += rnd.randrange(10, 22)
            pts.append((x, y))
        d.line(pts, fill=INK, width=2)
    for cx, sx in ((4, 1), (w - 5, -1)):   # cobwebs in the corners
        for k in range(1, 4):
            d.arc([cx - k * 14 if sx < 0 else cx - k * 14, 16 - k * 14, cx + k * 14, 16 + k * 14], 0, 180, fill=(230, 230, 230), width=1)
        for a in (10, 45, 80):
            ex = cx + sx * 46 * math.cos(math.radians(a))
            d.line([cx, 16, ex, 16 + 46 * math.sin(math.radians(a))], fill=(230, 230, 230), width=1)
    if room.lamps:   # one lamp hangs down, dead
        x = rnd.choice(room.lamps)
        d.rectangle([x - 24, 13, x + 24, 22], fill=(60, 60, 64))
        d.line([x - 10, 22, x + 4, 40], fill=INK, width=2)
        d.polygon([(x - 6, 40), (x + 14, 40), (x + 10, 48), (x - 2, 48)], fill=(70, 70, 74), outline=INK)
    for i, (x, y, ww, wh) in enumerate(room.windows):
        if i % 2 == 0:   # boarded up
            for k in range(2):
                yy = y + 14 + k * (wh // 2)
                d.polygon([(x - 6, yy), (x + ww + 6, yy + 10), (x + ww + 6, yy + 24), (x - 6, yy + 14)], fill=(150, 110, 70), outline=INK)
        else:
            d.line([(x + 10, y + 10), (x + ww // 2, y + wh // 2), (x + ww - 12, y + 14)], fill=INK, width=2)
    for _ in range(w // 60):   # rubbish on the floor
        x = rnd.randrange(10, w - 30)
        kind = rnd.randrange(3)
        if kind == 0:
            d.ellipse([x, H - 34, x + 14, H - 22], fill=(236, 236, 226), outline=INK, width=2)   # paper ball
        elif kind == 1:
            d.polygon([(x, H - 24), (x + 10, H - 34), (x + 20, H - 24)], fill=(240, 210, 70), outline=INK)   # banana peel
        else:
            d.rectangle([x, H - 30, x + 18, H - 22], fill=(120, 140, 90), outline=INK, width=2)   # can
    d.ellipse([w // 3, H - 14, w // 3 + 90, H - 4], fill=(110, 120, 110, 255))   # puddle
    return out


ROOMS = {
    "corridor": corridor, "stairs": stairs, "elevator_shaft": elevator,
    "home_empty": home_empty, "home_family": home_family, "home_students": home_students,
    "home_retirees": home_retirees, "home_influencer": home_influencer,
    "home_penguins": home_penguins, "home_ghost": home_ghost,
    "food_empty": food_empty, "food_imbiss": food_imbiss, "food_sushi": food_sushi, "food_cafe": food_cafe,
    "special_security": special_security, "special_teleporter": special_teleporter, "special_ufo": special_ufo,
    "special_parking": special_parking,
    "parking_space": lambda: parking_bay((220, 70, 70)), "parking_space_blue": lambda: parking_bay((70, 140, 220)),
    "parking_space_yellow": lambda: parking_bay((240, 200, 60)), "parking_space_empty": lambda: parking_bay(),
    "office_luxury_empty": office_luxury_empty, "office_hedgefund": office_hedgefund,
    "office_dragonbank": office_dragonbank, "office_atlantis": office_atlantis,
    "hotel_empty": hotel_empty, "hotel_business": hotel_business, "hotel_selfie": hotel_selfie,
    "hotel_wedding": hotel_wedding, "hotel_count": hotel_count, "hotel_band": hotel_band,
    "hotel_spacetourists": hotel_spacetourists,
    "housekeeping_empty": housekeeping_empty, "housekeeping_staubfrei": housekeeping_staubfrei,
    "housekeeping_robots": housekeeping_robots, "housekeeping_housegeist": housekeeping_housegeist,
    "home_exchange": home_exchange,
    "extra_copyroom": lambda: Picture("extra_copyroom@4x.png"),
    "dorm_empty": dorm_empty, "dorm_full": dorm_full,
    "shop_empty": shop_empty, "shop_spaeti": shop_spaeti, "shop_bio": shop_bio, "shop_robo": shop_robo,
    "special_kita": special_kita, "special_subway": special_subway, "special_conference": special_conference,
    "penthouse_empty": penthouse_empty, "penthouse_full": penthouse_full,
}

# The old car park only shows in old saves, and the parking spaces are in the basement anyway.
NO_VARIANTS = {"special_parking"}


def pack(images, name):
    """Shelf-packs the pictures into one atlas PNG plus its JSON, with 8 px between them."""
    pad = 8
    width = 3 * (6 * SEG_W + pad)
    x = y = 0
    places = {}
    for key, im in images.items():
        if x + im.width > width:
            x, y = 0, y + H + pad
        places[key] = (x, y)
        x += im.width + pad
    atlas = Image.new("RGBA", (width, y + H), (0, 0, 0, 0))
    frames = {}
    for key, im in images.items():
        px, py = places[key]
        atlas.paste(im, (px, py))
        w, h = im.size
        frames[key + ".png"] = {
            "frame": {"x": px, "y": py, "w": w, "h": h}, "rotated": False, "trimmed": False,
            "spriteSourceSize": {"x": 0, "y": 0, "w": w, "h": h}, "sourceSize": {"w": w, "h": h},
        }
    atlas.save(os.path.join(OUT, name + ".png"))
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump({"frames": frames, "meta": {"image": name + ".png", "scale": str(S),
                   "size": {"w": atlas.width, "h": atlas.height}}}, f, indent=1)


def main():
    rooms = {name: fn() for name, fn in ROOMS.items()}
    pack({name: r.im for name, r in rooms.items()}, "extra")
    varied = {name: r for name, r in rooms.items() if name not in NO_VARIANTS}
    pack({name + "_night": night(r) for name, r in varied.items()}, "extra_night")
    pack({name + "_rundown": rundown(r) for name, r in varied.items()}, "extra_rundown")


if __name__ == "__main__":
    main()
