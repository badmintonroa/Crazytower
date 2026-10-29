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


# ---------------------------------------------------------------- M11: the figures from the prompt page
# Placeholders in the same 12x24 grid. The real sheets replace them file by file (same names).

TEAL, AMBER, PINK, BLUE, VIOLET, MINT, ORANGE = (79, 227, 193), (255, 201, 77), (255, 95, 162), (127, 178, 255), (183, 156, 255), (94, 224, 160), (255, 158, 94)
NAVY, GREY, WHITE, BLACK, RED, BROWN, BEIGE = (42, 47, 74), (120, 124, 140), (238, 240, 250), (20, 20, 30), (200, 30, 60), (120, 80, 50), (214, 196, 160)


def combine(*parts):
    def extra(f, n):
        for part in parts:
            part(f, n)
    return extra


def cup(c=WHITE):
    return lambda f, n: f.r(9, 12, 2, 3, c)


def held(w, h, c, y=11):
    return lambda f, n: f.r(9, y, w, h, c)


def hair_long(c):
    return lambda f, n: f.r(2, 3, 1, 6, c)


def bun(c):
    return lambda f, n: f.r(1, 2, 2, 2, c)


def glasses(f, n):
    f.r(6, 4, 3, 1, BLACK)


def tie_c(c):
    return lambda f, n: (f.r(5, 9, 2, 1, WHITE), f.r(6, 10, 1, 4, c))


def cap(c):
    return lambda f, n: (f.r(3, 1, 6, 2, c), f.r(9, 2, 2, 1, c))


def hat(c, top=2):
    return lambda f, n: (f.r(2, 2, 8, 1, c), f.r(3, 2 - top, 6, top, c))


def cape(c, lining=RED):
    return lambda f, n: (f.r(1, 9, 1, 12, lining), f.r(2, 9, 1, 11, c))


def fangs(f, n):
    f.p(7, 8, WHITE)


def moustache(c):
    return lambda f, n: f.r(6, 7, 3, 1, c)


def stripes(c):
    return lambda f, n: [f.r(3, y, 6, 1, c) for y in (10, 12, 14, 16)]


def dots(c1, c2):
    return lambda f, n: (f.p(4, 10, c1), f.p(7, 12, c2), f.p(5, 14, c1), f.p(8, 15, c2), f.p(4, 19, c2), f.p(6, 21, c1))


def nose(f, n):
    f.p(9, 6, RED)


def big_shoes(c):
    return lambda f, n: f.r(2 + (n == 1) * -1, 23, 9, 1, c)


def balls(f, n):
    for i, c in enumerate((PINK, AMBER, TEAL)):
        f.p(4 + i * 2, (0, 1, 0)[(i + n) % 3], c)


def antennae(f, n):
    f.p(4, 0, (120, 255, 140)); f.p(7, 0, (120, 255, 140)); f.p(4, 1, INK); f.p(7, 1, INK)


def third_eye(f, n):
    f.p(5, 4, INK)


def mop(f, n):
    f.r(10, 8, 1, 14, BROWN); f.r(10, 22, 2, 2, GREY)


def keys(f, n):
    f.p(3, 16, AMBER)


def headphones(c):
    return lambda f, n: (f.r(3, 1, 6, 1, c), f.r(2, 3, 1, 3, c), f.r(9, 3, 1, 3, c))


def backpack(c):
    return lambda f, n: f.r(1, 9, 2, 6, c)


def braid(c):
    return lambda f, n: f.r(2, 3, 1, 9, c)


def beret(c):
    return lambda f, n: (f.r(3, 1, 6, 2, c), f.p(2, 2, c))


def splatter(f, n):
    f.p(4, 11, PINK); f.p(7, 13, TEAL); f.p(5, 19, AMBER)


def canvas(f, n):
    f.r(9, 8, 3, 8, BEIGE)


def ponytail(c):
    return lambda f, n: f.r(1, 3, 2, 3, c)


def bags(f, n):
    f.r(9, 12, 3, 4, BEIGE); f.p(10, 10, AMBER); f.p(10, 11, AMBER)


def kid(f, n):
    # children are shorter: clear the top rows, move the head down
    for y in range(0, 6):
        for x in range(12):
            f.px[y][x] = None
    f.r(3, 6, 6, 4, SKIN[3]); f.r(3, 5, 6, 1, AMBER); f.p(7, 7, INK)
    f.r(9, 11, 2, 2, WHITE)


def scarf(c):
    return lambda f, n: f.r(3, 8, 6, 1, c)


def boxes(f, n):
    f.r(8, 7, 4, 6, (230, 210, 170)); f.r(8, 9, 4, 1, RED)


def pearls(f, n):
    f.r(4, 9, 4, 1, WHITE)


def cat(f, n):
    f.r(9, 11, 3, 3, WHITE); f.p(9, 10, WHITE); f.p(11, 10, WHITE)


def monocle(f, n):
    f.p(7, 4, AMBER); f.p(7, 5, INK)


def magnifier(f, n):
    f.r(10, 10, 2, 2, BLUE); f.p(9, 12, BROWN)


def sock(f, n):
    f.r(8, 14, 1, 3, (255, 255, 255)); f.p(8, 15, RED)


def watch(f, n):
    f.r(9, 13, 2, 2, TEAL)


def cobweb(f, n):
    f.p(3, 9, (200, 200, 210)); f.p(8, 9, (200, 200, 210))


def cane(c):
    return lambda f, n: f.r(10, 12, 1, 12, c)


def coffin(f, n):
    f.r(9, 11, 3, 5, (60, 40, 30))


def bat_ears(f, n):
    f.p(3, 1, BLACK); f.p(8, 1, BLACK)


def shades(f, n):
    f.r(5, 4, 4, 1, BLACK)


def tear(f, n):
    f.p(7, 6, BLUE)


def balloon(f, n):
    f.r(10, 6, 2, 2, PINK); f.r(10, 8, 1, 6, GREY)


def face_paint(f, n):
    f.r(3, 3, 6, 6, WHITE); f.p(7, 5, INK); f.p(8, 7, RED)


def tufts(f, n):
    f.r(3, 2, 6, 1, (240, 220, 200)); f.p(2, 3, ORANGE); f.p(9, 2, ORANGE)


def bowtie(c):
    return lambda f, n: f.r(5, 9, 3, 1, c)


def wobble_walk(f, n):
    pass


def ghost2(f, n):
    ghost_extra(f, n); f.r(4, 4, 1, 2, INK); f.r(7, 4, 1, 2, INK); f.r(3, 9, 6, 8, (225, 232, 255))


def speaker(f, n):
    f.r(9, 12, 3, 4, BLACK); f.p(10, 13, TEAL)


def book(f, n):
    f.r(8, 9, 3, 3, BLUE)


LOOKS.update({
    # sheet 1: ordinary office people
    "npc_office_blazer": dict(skin=SKIN[3], hair=(60, 40, 30), shirt=NAVY, pants=NAVY, shoes=BLACK, extra=combine(bun((60, 40, 30)), cup())),
    "npc_office_sleeves": dict(skin=SKIN[0], hair=(90, 60, 40), shirt=BLUE, pants=GREY, extra=combine(tie_c(AMBER), held(3, 3, WHITE))),
    "npc_office_boss": dict(skin=SKIN[0], hair=(200, 200, 205), shirt=(30, 32, 50), pants=(30, 32, 50), extra=combine(moustache((200, 200, 205)), tie_c(RED), held(3, 4, BROWN, 13))),
    "npc_office_intern": dict(skin=SKIN[1], hair=(30, 30, 40), shirt=MINT, pants=(46, 134, 222), shoes=WHITE, extra=combine(hair_long((30, 30, 40)), held(2, 4, WHITE, 9))),
    "npc_office_it": dict(skin=SKIN[3], hair=(140, 70, 30), shirt=BLACK, pants=(90, 100, 70), extra=combine(headphones(GREY), held(3, 2, GREY, 13))),
    # sheet 2: tenant offices
    "npc_accountant": dict(skin=SKIN[0], hair=(40, 30, 25), shirt=GREY, pants=GREY, extra=combine(glasses, tie_c(NAVY), held(3, 3, BLACK, 13))),
    "npc_startup": dict(skin=SKIN[3], hair=(230, 190, 90), shirt=TEAL, pants=(46, 134, 222), shoes=WHITE, extra=combine(hat(ORANGE, 1), held(3, 2, GREY, 12))),
    "npc_callcenter": dict(skin=SKIN[2], hair=(30, 30, 40), shirt=AMBER, pants=NAVY, extra=headset),
    "npc_excuses": dict(skin=SKIN[1], hair=(90, 60, 40), shirt=BEIGE, pants=BEIGE, extra=combine(tie_c(RED), cup())),
    "npc_ministry": dict(skin=SKIN[0], hair=(90, 60, 40), shirt=BROWN, pants=(80, 70, 60), extra=combine(moustache((90, 60, 40)), bowtie(RED), held(2, 4, (60, 40, 30)))),
    # sheet 3: clowns
    "npc_clown_classic": dict(skin=WHITE, hair=PINK, shirt=AMBER, pants=AMBER, shoes=RED, extra=combine(clown, dots(PINK, TEAL), big_shoes(RED))),
    "npc_clown_boss": dict(skin=SKIN[3], hair=ORANGE, shirt=BLACK, pants=BLACK, shoes=RED, extra=combine(tufts, nose, bowtie(PINK), held(3, 3, BLACK, 13))),
    "npc_clown_mime": dict(skin=WHITE, hair=BLACK, shirt=WHITE, pants=BLACK, extra=combine(face_paint, stripes(BLACK), beret(BLACK))),
    "npc_clown_sad": dict(skin=WHITE, hair=BROWN, shirt=BROWN, pants=(110, 80, 50), extra=combine(hat(BROWN, 1), nose, tear, balloon)),
    "npc_clown_juggler": dict(skin=WHITE, hair=TEAL, shirt=TEAL, pants=AMBER, shoes=AMBER, extra=combine(dots(AMBER, PINK), nose, balls)),
    # sheet 4: vampires
    "npc_vampire_count": dict(skin=(220, 225, 240), hair=BLACK, shirt=BLACK, pants=BLACK, extra=combine(cape(BLACK), fangs, held(2, 3, (120, 30, 40)))),
    "npc_vampire_secretary": dict(skin=(220, 225, 240), hair=BLACK, shirt=(130, 20, 40), pants=(130, 20, 40), extra=combine(hair_long(BLACK), glasses, fangs, held(2, 3, BEIGE))),
    "npc_vampire_intern": dict(skin=(220, 225, 240), hair=BLACK, shirt=BLACK, pants=(40, 40, 52), extra=combine(bat_ears, shades, fangs, held(1, 3, RED))),
    "npc_vampire_old": dict(skin=(205, 210, 225), hair=WHITE, shirt=(40, 40, 50), pants=(40, 40, 50), extra=combine(hair_long(WHITE), cobweb, cane(BLACK), fangs)),
    "npc_vampire_collector": dict(skin=(220, 225, 240), hair=(220, 225, 240), shirt=(30, 30, 30), pants=(30, 30, 30), extra=combine(shades, fangs, coffin)),
    # sheet 5: special offices
    "npc_detective": dict(skin=SKIN[0], hair=(90, 60, 40), shirt=BEIGE, pants=BEIGE, extra=combine(hat((140, 110, 80), 1), magnifier, sock)),
    "npc_timetraveller": dict(skin=SKIN[2], hair=BLACK, shirt=PINK, pants=TEAL, shoes=BROWN, extra=combine(hat(BLACK, 2), lambda f, n: f.r(3, 4, 6, 1, AMBER), watch)),
    "npc_supervillain": dict(skin=SKIN[0], hair=BLACK, shirt=BLACK, pants=BLACK, extra=combine(cape(VIOLET, VIOLET), monocle, lambda f, n: f.p(8, 8, BLACK), cat)),
    "npc_alien": dict(skin=(120, 255, 140), hair=(120, 255, 140), shirt=VIOLET, pants=VIOLET, extra=combine(antennae, third_eye, tie_c(AMBER), held(2, 2, BLACK, 13))),
    "npc_janitor": dict(skin=SKIN[1], hair=(60, 40, 30), shirt=(40, 60, 120), pants=(40, 60, 120), extra=combine(cap((40, 60, 120)), keys, mop)),
    # sheet 6: students
    "npc_student_hoodie": dict(skin=SKIN[3], hair=(90, 60, 40), shirt=GREY, pants=(46, 134, 222), extra=combine(hat((60, 64, 92), 1), backpack(ORANGE), cup(AMBER))),
    "npc_student_bookworm": dict(skin=SKIN[1], hair=(60, 40, 30), shirt=AMBER, pants=NAVY, extra=combine(braid((60, 40, 30)), glasses, book)),
    "npc_student_party": dict(skin=SKIN[0], hair=(230, 190, 90), shirt=ORANGE, pants=TEAL, shoes=PINK, extra=combine(dots(MINT, PINK), shades, speaker)),
    "npc_student_gamer": dict(skin=(250, 230, 220), hair=PINK, shirt=BLACK, pants=(60, 64, 92), extra=combine(headphones(VIOLET), held(1, 3, MINT))),
    "npc_student_artist": dict(skin=SKIN[2], hair=BLACK, shirt=(80, 110, 160), pants=(80, 110, 160), extra=combine(beret(RED), splatter, canvas)),
    # sheet 7: families, retirees, penguin
    "npc_family_dad": dict(skin=SKIN[0], hair=(140, 70, 30), shirt=BLUE, pants=(46, 80, 160), extra=combine(stripes(WHITE), held(2, 1, GREY, 13))),
    "npc_family_mom": dict(skin=SKIN[2], hair=(30, 30, 40), shirt=MINT, pants=MINT, extra=combine(ponytail((30, 30, 40)), bags)),
    "npc_kid": dict(skin=SKIN[3], hair=AMBER, shirt=AMBER, pants=(46, 134, 222), shoes=RED, extra=kid),
    "npc_retiree": dict(skin=SKIN[0], hair=(220, 220, 225), shirt=MINT, pants=(110, 100, 90), extra=combine(bun((220, 220, 225)), cane(BROWN))),
    "npc_penguin": dict(skin=(20, 22, 34), hair=(20, 22, 34), shirt=(20, 22, 34), pants=(20, 22, 34), extra=combine(penguin, scarf(AMBER)), legs=False),
    # sheet 8: visitors and events
    "npc_tourist": dict(skin=SKIN[3], hair=(140, 70, 30), shirt=ORANGE, pants=BEIGE, extra=combine(dots(TEAL, AMBER), tourist)),
    "npc_pizza": dict(skin=SKIN[1], hair=(30, 30, 40), shirt=RED, pants=NAVY, extra=combine(cap(RED), boxes)),
    "npc_courier": dict(skin=SKIN[2], hair=(30, 30, 40), shirt=ORANGE, pants=(90, 100, 70), extra=combine(cap(ORANGE), held(3, 4, (176, 120, 72), 10))),
    "npc_vip": dict(skin=SKIN[0], hair=(200, 160, 90), shirt=VIOLET, pants=VIOLET, extra=combine(bun((200, 160, 90)), pearls, held(1, 2, AMBER))),
    "npc_ghost": dict(skin=(230, 236, 255), hair=(230, 236, 255), shirt=(230, 236, 255), pants=(230, 236, 255), extra=ghost2, legs=False),
})

for name, kw in LOOKS.items():
    sheet(name, **kw)

# overview for the docs
names = list(LOOKS)
over = Image.new("RGBA", (W * 3 * 6 + 60, (H + 6) * ((len(names) + 5) // 6) + 6), (43, 58, 103, 255))
for i, name in enumerate(names):
    im = Image.open(OUT / f"{name}.png")
    over.paste(im, (6 + (i % 6) * (W * 3 + 8), 6 + (i // 6) * (H + 6)), im)
over = over.resize((over.width * 4, over.height * 4), Image.NEAREST)
over.save(OUT.parent / "people_overview.png") if len(sys.argv) < 3 else over.save(sys.argv[2])
print(len(names), "looks")
