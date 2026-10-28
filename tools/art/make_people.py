"""Draws the CrazyTower people sprites.

Each look is a 36x24 sheet with three 12x24 frames: standing, step A, step B (facing right).
Run: python3 tools/art/make_people.py game/src/main/resources/people
"""
import sys
from pathlib import Path
from PIL import Image

OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)

INK = (15, 18, 32, 255)
W, H = 12, 24

SKIN = [(242, 201, 160), (217, 162, 122), (140, 90, 60), (255, 224, 196)]


def rgba(c):
    return c if len(c) == 4 else (*c, 255)


def darker(c, f=0.75):
    return tuple(int(v * f) for v in c[:3]) + (255,)


class Frame:
    def __init__(self):
        self.px = [[None] * W for _ in range(H)]

    def r(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if 0 <= xx < W and 0 <= yy < H:
                    self.px[yy][xx] = rgba(c)

    def p(self, x, y, c):
        self.r(x, y, 1, 1, c)

    def outlined(self):
        img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for y in range(H):
            for x in range(W):
                if self.px[y][x] is None and any(
                    0 <= x + dx < W and 0 <= y + dy < H and self.px[y + dy][x + dx] is not None
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                ):
                    img.putpixel((x, y), INK)
                elif self.px[y][x] is not None:
                    img.putpixel((x, y), self.px[y][x])
        return img


def person(frame_no, skin, hair, shirt, pants, shoes=(40, 40, 52), extra=None, legs=True):
    f = Frame()
    # legs
    if legs:
        if frame_no == 0:
            f.r(4, 17, 2, 6, pants); f.r(6, 17, 2, 6, darker(pants))
            f.r(4, 23, 2, 1, shoes); f.r(6, 23, 3, 1, shoes)
        elif frame_no == 1:
            f.r(3, 17, 2, 6, pants); f.r(7, 17, 2, 6, darker(pants))
            f.r(2, 23, 3, 1, shoes); f.r(7, 23, 3, 1, shoes)
        else:
            f.r(5, 17, 2, 6, pants); f.r(5, 19, 2, 4, darker(pants))
            f.r(5, 23, 3, 1, shoes)
    # torso and arms
    f.r(3, 9, 6, 8, shirt)
    swing = {0: 0, 1: 1, 2: -1}[frame_no]
    f.r(2, 9 + max(0, -swing), 1, 6, darker(shirt))
    f.r(9, 9 + max(0, swing), 1, 6, darker(shirt))
    f.p(2, 15 + max(0, -swing), skin)
    f.p(9, 15 + max(0, swing), skin)
    # head
    f.r(3, 3, 6, 6, skin)
    f.r(3, 2, 6, 2, hair)
    f.p(3, 4, hair)
    f.p(7, 5, INK)
    f.p(8, 7, darker(skin, 0.85))
    if extra:
        extra(f, frame_no)
    return f.outlined()


def sheet(name, **kw):
    img = Image.new("RGBA", (W * 3, H), (0, 0, 0, 0))
    for i in range(3):
        img.paste(person(i, **kw), (i * W, 0))
    img.save(OUT / f"{name}.png")


def tie(f, n):
    f.r(5, 9, 2, 1, (238, 240, 250)); f.r(6, 10, 1, 4, (200, 30, 60))


def headset(f, n):
    f.r(2, 3, 1, 4, (30, 30, 40)); f.r(3, 1, 6, 1, (30, 30, 40)); f.p(8, 7, (79, 227, 193))


def clown(f, n):
    f.r(1, 1, 3, 3, (255, 95, 162)); f.r(8, 1, 3, 3, (79, 227, 193)); f.r(4, 1, 4, 2, (255, 201, 77))
    f.p(9, 6, (230, 30, 60)); f.r(5, 10, 2, 2, (255, 255, 255)); f.r(5, 13, 2, 2, (255, 255, 255))


def vampire(f, n):
    f.r(1, 8, 1, 12, (160, 20, 50)); f.r(10, 8, 1, 12, (160, 20, 50)); f.r(3, 8, 6, 1, (160, 20, 50))
    f.p(6, 8, (255, 255, 255))


def alien(f, n):
    f.p(4, 0, (120, 255, 140)); f.p(7, 0, (120, 255, 140)); f.r(4, 1, 1, 1, INK); f.r(7, 1, 1, 1, INK)
    f.r(6, 5, 2, 1, INK)


def nurse(f, n):
    f.r(4, 1, 4, 2, (255, 255, 255)); f.p(6, 1, (230, 30, 60))


def retiree(f, n):
    f.r(10, 12, 1, 12, (120, 80, 50))  # walking stick


def influencer(f, n):
    f.r(9, 8, 2, 3, (40, 40, 52)); f.p(10, 9, (79, 227, 193))  # phone
    f.r(3, 4, 6, 1, (20, 20, 30))  # sunglasses


def ghost_extra(f, n):
    f.r(3, 17, 6, 5, (230, 236, 255)); f.p(3 + n, 22, (230, 236, 255)); f.p(7 - n, 22, (230, 236, 255))
    f.r(4, 5, 1, 1, INK)


def villain(f, n):
    f.r(3, 2, 6, 1, (20, 20, 30)); f.r(1, 9, 1, 10, (60, 0, 90)); f.r(10, 9, 1, 10, (60, 0, 90))
    f.r(3, 4, 6, 1, (60, 0, 90))  # mask


def timetraveler(f, n):
    f.r(3, 4, 6, 2, (255, 201, 77)); f.p(4, 4, INK); f.p(7, 4, INK)  # goggles
    f.r(9, 13, 2, 2, (79, 227, 193))  # gadget


def hoodie(f, n):
    f.r(2, 2, 8, 2, (90, 90, 120)); f.p(2, 4, (90, 90, 120)); f.r(4, 12, 4, 1, (70, 70, 100))


def courier(f, n):
    f.r(3, 1, 7, 2, (69, 201, 136)); f.r(9, 2, 2, 1, (69, 201, 136))
    f.r(9, 10, 3, 5, (176, 120, 72))  # parcel


def tourist(f, n):
    f.r(2, 1, 8, 1, (255, 201, 77)); f.r(3, 0, 6, 1, (255, 201, 77))  # sun hat
    f.r(4, 11, 3, 2, (40, 40, 52))  # camera


def penguin(f, n):
    f.r(3, 2, 6, 20, (20, 22, 34))
    f.r(4, 9, 4, 12, (255, 255, 255)); f.p(4, 5, (255, 255, 255)); f.p(6, 5, INK)
    f.r(9, 6, 2, 1, (255, 158, 77))
    off = {0: 0, 1: -1, 2: 1}[n]
    f.r(3 + off, 22, 2, 1, (255, 158, 77)); f.r(7 - off, 22, 2, 1, (255, 158, 77))
    f.r(2, 10, 1, 6, (20, 22, 34)); f.r(9, 10, 1, 6, (20, 22, 34))


def official(f, n):
    f.r(3, 1, 6, 2, (42, 47, 74)); f.r(2, 2, 8, 1, (42, 47, 74)); f.r(8, 10, 3, 4, (238, 240, 250))  # cap + files


LOOKS = {
    "suit": dict(skin=SKIN[0], hair=(60, 40, 30), shirt=(42, 47, 74), pants=(30, 32, 50), extra=tie),
    "suit2": dict(skin=SKIN[1], hair=(20, 20, 24), shirt=(127, 178, 255), pants=(42, 47, 74), extra=tie),
    "casual": dict(skin=SKIN[3], hair=(230, 190, 90), shirt=(255, 95, 162), pants=(46, 134, 222)),
    "casual2": dict(skin=SKIN[2], hair=(30, 30, 40), shirt=(79, 227, 193), pants=(60, 64, 92)),
    "tourist": dict(skin=SKIN[3], hair=(140, 70, 30), shirt=(255, 158, 77), pants=(238, 220, 180), extra=tourist),
    "courier": dict(skin=SKIN[1], hair=(30, 30, 40), shirt=(69, 201, 136), pants=(42, 47, 74), extra=courier),
    "callcenter": dict(skin=SKIN[0], hair=(140, 70, 30), shirt=(183, 156, 255), pants=(42, 47, 74), extra=headset),
    "startup": dict(skin=SKIN[3], hair=(230, 190, 90), shirt=(90, 90, 120), pants=(46, 134, 222), extra=hoodie),
    "clown": dict(skin=(255, 255, 255), hair=(255, 95, 162), shirt=(255, 201, 77), pants=(46, 134, 222), shoes=(230, 30, 60), extra=clown),
    "vampire": dict(skin=(220, 225, 240), hair=(20, 20, 30), shirt=(20, 20, 30), pants=(20, 20, 30), extra=vampire),
    "alien": dict(skin=(120, 255, 140), hair=(120, 255, 140), shirt=(183, 156, 255), pants=(60, 64, 92), extra=alien),
    "nurse": dict(skin=SKIN[1], hair=(60, 40, 30), shirt=(238, 240, 250), pants=(238, 240, 250), extra=nurse),
    "retiree": dict(skin=SKIN[0], hair=(220, 220, 225), shirt=(150, 120, 90), pants=(110, 100, 90), extra=retiree),
    "influencer": dict(skin=SKIN[3], hair=(255, 95, 162), shirt=(255, 255, 255), pants=(255, 158, 196), extra=influencer),
    "ghost": dict(skin=(230, 236, 255), hair=(230, 236, 255), shirt=(230, 236, 255), pants=(230, 236, 255), extra=ghost_extra, legs=False),
    "villain": dict(skin=SKIN[0], hair=(20, 20, 30), shirt=(60, 0, 90), pants=(20, 20, 30), extra=villain),
    "timetraveler": dict(skin=SKIN[2], hair=(220, 220, 225), shirt=(140, 110, 70), pants=(60, 64, 92), extra=timetraveler),
    "official": dict(skin=SKIN[0], hair=(60, 40, 30), shirt=(80, 90, 70), pants=(50, 55, 45), extra=official),
    "penguin": dict(skin=(20, 22, 34), hair=(20, 22, 34), shirt=(20, 22, 34), pants=(20, 22, 34), extra=penguin, legs=False),
}

for name, kw in LOOKS.items():
    sheet(name, **kw)

# overview for the docs
names = list(LOOKS)
over = Image.new("RGBA", (W * 3 * 4 + 40, (H + 6) * ((len(names) + 3) // 4) + 6), (43, 58, 103, 255))
for i, name in enumerate(names):
    im = Image.open(OUT / f"{name}.png")
    over.paste(im, (6 + (i % 4) * (W * 3 + 8), 6 + (i // 4) * (H + 6)), im)
over = over.resize((over.width * 4, over.height * 4), Image.NEAREST)
over.save(OUT.parent / "people_overview.png") if len(sys.argv) < 3 else over.save(sys.argv[2])
print(len(names), "looks")
