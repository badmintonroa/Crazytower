"""Puts generated figure pictures (ComfyUI, Gemini, ...) into the game as people sprite sheets.

Each picture shows one figure three times side by side on a magenta background (#FF00FF): standing,
walking with the left leg forward, walking with the right leg forward. A picture that is less than twice
as wide as high (e.g. 1152x768) has only two poses, standing and walking; the step is used for both
walking frames. That is what the ComfyUI
img2img template vorlage_figur.png (1536x512) gives. The file is named after the look, e.g.
"npc_cook_00001_.png", "npc_cook_v2_00001_.png" or "npc_cook.png". The ComfyUI counter and a
"_v<n>" variant ending are dropped; if a look comes several times, the newest file wins, unless
you pass the files one by one.

For every picture the script
  - keys out the magenta (or uses the alpha channel if the picture already has one),
  - cuts the picture into thirds and takes the figure in each third,
  - scales all three frames alike so an adult is as tall as the figures already in the game
    (about 83 of 96 pixels; children, animals and robots get the factor from SIZES below),
  - stands each figure on the bottom of a 48x96 frame, centred between head and body,
  - pulls it towards the look of the figures already in the game (docs/art_direction_figuren.md):
    colour strength and brightness like theirs, a closed dark outline, hard edges (--raw: off),
  - writes game/src/main/resources/people/<look>.png (144x96) and adds the look to index.txt,
  - and draws people_preview.png next to the pictures: old stand-in and new figure side by side.

Run:
    python3 tools/art/import_people.py <folder or files> [--size npc_dragon=1.1] [--dry-run]
Needs Pillow and numpy.
"""
import argparse
import os
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../..")
PEOPLE = os.path.join(ROOT, "game/src/main/resources/people")
LOOKS_KT = os.path.join(ROOT, "core/src/main/kotlin/crazytower/core/Looks.kt")

FW, FH = 48, 96  # one frame of a sheet; a sheet is three frames side by side
ADULT_H = 83  # median height of the adults already in the game
# Height compared to an adult. Everything not listed is an adult (1.0).
SIZES = {
    "npc_robot_vacuum": 0.55,
    "npc_robo_auntie": 1.0,
    "npc_dragon": 1.06,
    "npc_atlantean": 1.0,
    "npc_alien_tourist": 0.95,
    "npc_alien_ambassador": 1.0,
    "npc_retiree_2": 0.97,
    "npc_queen": 0.95,
    "npc_kid": 0.85,
    "npc_penguin": 0.66,
    "npc_ghost": 0.83,
}
MIN_FIT = 0.88  # a figure wider than a frame shrinks to at most this much, the rest is cut off


def stand_ins():
    """Looks the game already asks for (Looks.kt), mapped to the figure shown until now."""
    try:
        text = open(LOOKS_KT, encoding="utf-8").read()
    except OSError:
        return {}
    return dict(re.findall(r'"(npc_\w+)"\s+to\s+"(npc_\w+)"', text))


def look_name(path):
    name = os.path.splitext(os.path.basename(path))[0]
    name = re.sub(r"_\d{4,}_?$", "", name)  # ComfyUI counter: npc_cook_00001_
    name = re.sub(r"_v\d+$", "", name)  # batch variants: npc_cook_v2
    if not name.startswith("npc_"):
        name = "npc_" + name
    return name


def key(im):
    """RGBA picture and opacity 0..1: magenta is keyed out with a soft edge, magenta fringes are greyed."""
    # Nur ein echter Alphakanal zaehlt: ComfyUI speichert manchmal einen fast ueberall deckenden (184..243),
    # dann wird trotzdem das Magenta freigestellt.
    if im.mode in ("RGBA", "LA") and (np.asarray(im.convert("RGBA"))[..., 3] < 16).mean() > 0.05:
        rgba = np.asarray(im.convert("RGBA"))
        return rgba, rgba[..., 3] / 255.0
    a = np.asarray(im.convert("RGB")).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    # Die Modelle malen selten reines #FF00FF, oft ein dunkleres Magenta wie (190, 40, 200).
    # Darum die Hintergrundfarbe am Bildrand messen statt reines Magenta anzunehmen.
    border = np.concatenate([a[:4].reshape(-1, 3), a[-4:].reshape(-1, 3), a[:, :4].reshape(-1, 3), a[:, -4:].reshape(-1, 3)])
    bg = np.median(border, axis=0)
    if not (bg[0] > 120 and bg[2] > 120 and bg[1] < min(bg[0], bg[2]) - 60):
        bg = np.array([255, 0, 255])  # Rand ist kein Magenta: wie bisher reines Magenta
    d = np.sqrt((bg[0] - r) ** 2 + (bg[1] - g) ** 2 + (bg[2] - b) ** 2)
    alpha = np.clip((d - 40) / 40, 0, 1)
    spill = np.minimum(r, b) - g
    fringe = (alpha < 1) & (spill > 0)
    m = np.minimum(r, b)
    out = a.copy()
    out[..., 0] = np.where(fringe, np.minimum(r, g + (r - m)), r)
    out[..., 2] = np.where(fringe, np.minimum(b, g + (b - m)), b)
    rgba = np.dstack([out, (alpha * 255).astype(int)]).astype(np.uint8)
    return rgba, alpha


def solid(mask, axis, least):
    """Rows (axis=1) or columns (axis=0) with at least [least] opaque pixels: ignores specks and noise."""
    return np.where(mask.sum(axis) >= least)[0]


def pose_count(w, h):
    """3 Posen nebeneinander (1536x512, 1536x640) oder nur 2, Stehen und Gehen (1152x768): am Seitenverhaeltnis."""
    return 2 if w / h < 2 else 3


def poses(path, count=None):
    rgba, alpha = key(Image.open(path))
    mask = alpha > 0.5
    h, w = mask.shape
    count = count or pose_count(w, h)
    out = []
    for j in range(count):
        l, r = w * j // count, w * (j + 1) // count
        m = mask[:, l:r]
        ys, xs = solid(m, 1, 3), solid(m, 0, 3)
        if len(ys) == 0 or len(xs) == 0:
            sys.exit(f"{os.path.basename(path)}: im {j + 1}. Teil von {count} ist keine Figur zu finden.")
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        m = m[y0:y1, x0:x1]
        if m.mean() > 0.95:
            sys.exit(f"{os.path.basename(path)}: Hintergrund ließ sich nicht freistellen (Teil {j + 1} ist fast ganz deckend).")
        head = m[: max(8, (y1 - y0) // 4)]
        hx = np.where(head.any(0))[0]
        cx = (hx.min() + hx.max()) / 2 if len(hx) else (x1 - x0) / 2
        out.append(dict(img=Image.fromarray(rgba[y0:y1, l + x0:l + x1]), h=y1 - y0, w=x1 - x0, cx=cx))
    if count == 2:  # nur ein Schritt: er dient fuer beide Gehbilder, das Spiel wechselt Schritt und Stehen
        out.append(out[1])
    return out


def sheet(frames, factor):
    tallest = max(p["h"] for p in frames)
    widest = max(p["w"] for p in frames)
    s = ADULT_H * factor / tallest
    s = min(s, (FH - 2) / tallest)
    if widest * s > FW:  # props stick out: shrink a little, cut off the rest
        s = max(s * MIN_FIT, FW / widest)
    out = Image.new("RGBA", (FW * 3, FH), (0, 0, 0, 0))
    for j, p in enumerate(frames):
        w, h = max(1, round(p["w"] * s)), max(1, round(p["h"] * s))
        im = p["img"].resize((w, h), Image.LANCZOS)
        # centre between head and body, so the walking frames don't jump
        c = (p["cx"] + p["w"] / 2) / 2
        x, y = round(FW / 2 - c * s), FH - 1 - h
        wide = Image.new("RGBA", (FW + 2 * w, FH), (0, 0, 0, 0))
        wide.alpha_composite(im, (x + w, y))
        out.alpha_composite(wide.crop((w, 0, w + FW, FH)), (FW * j, 0))
    return out


INK = np.array([16, 14, 24])  # outline colour of the figures in the game


# Art direction (docs/art_direction_figuren.md): mean saturation and brightness (0..255) of the
# figures that were in the game before the restyle. New sheets are pulled towards these values.
STYLE_SATURATION = 113
STYLE_BRIGHTNESS = 137


def match_style(img, ref):
    """Pulls a new sheet towards the look of the old ones: same colour strength and brightness,
    a closed dark outline and hard edges, so generated figures of different runs fit together."""
    rgba = np.asarray(img).astype(float)
    alpha = rgba[..., 3]
    opaque = alpha > 200
    if ref is not None and opaque.any():
        hsv = np.asarray(img.convert("RGB").convert("HSV")).astype(float)
        s_gain = np.clip(ref[0] / max(hsv[..., 1][opaque].mean(), 1), 0.8, 1.6)
        v_gain = np.clip(ref[1] / max(hsv[..., 2][opaque].mean(), 1), 0.85, 1.2)
        hsv[..., 1] = np.clip(hsv[..., 1] * s_gain, 0, 255)
        hsv[..., 2] = np.clip(hsv[..., 2] * v_gain, 0, 255)
        rgb = np.asarray(Image.fromarray(hsv.astype(np.uint8), "HSV").convert("RGB")).astype(float)
        rgba[..., :3] = np.where(opaque[..., None], rgb, rgba[..., :3])
    inside = alpha >= 128
    # outline: inside pixels that touch the outside (the three frames never touch, so rolling is safe)
    edge = np.zeros_like(inside)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= inside & ~np.roll(np.roll(inside, dy, 0), dx, 1)
    rgba[..., :3] = np.where(edge[..., None], rgba[..., :3] * 0.25 + INK * 0.75, rgba[..., :3])
    rgba[..., 3] = np.where(inside, 255, 0)
    return Image.fromarray(rgba.clip(0, 255).astype(np.uint8), "RGBA")


def preview(rows, path):
    """Old figure (if any) and new figure next to each other, twice the size, on a lobby-like ground."""
    z, gap, label = 2, 16, 14
    width = FW * 3 * z * 2 + gap * 3
    img = Image.new("RGBA", (width, len(rows) * (FH * z + label + gap) + gap), (30, 34, 54, 255))
    d = ImageDraw.Draw(img)
    for i, (name, old, old_img, new) in enumerate(rows):
        y = gap + i * (FH * z + label + gap)
        d.text((gap, y), f"{name}   (vorher: {old or '-'})", fill=(79, 227, 193))
        for k, sheet_img in enumerate((old_img, new)):
            if sheet_img:
                big = sheet_img.resize((sheet_img.width * z, sheet_img.height * z), Image.NEAREST)
                img.alpha_composite(big, (gap + k * (FW * 3 * z + gap), y + label))
    img.save(path)


def main():
    ap = argparse.ArgumentParser(description="Generierte Figuren als Sprite-Blatt ins Spiel holen")
    ap.add_argument("inputs", nargs="+", help="Ordner mit den Bildern oder einzelne PNG-Dateien")
    ap.add_argument("--size", action="append", default=[], metavar="LOOK=FAKTOR",
                    help="Größe im Vergleich zu einem Erwachsenen, z.B. npc_dragon=1.1")
    ap.add_argument("--dry-run", action="store_true", help="nur die Vorschau schreiben, nichts ins Spiel")
    ap.add_argument("--repo", help="CrazyTower-Ordner, wenn das Skript nicht im Repo unter tools/art liegt")
    ap.add_argument("--posen", type=int, choices=(2, 3),
                    help="Posen pro Bild; sonst am Format erkannt (breit = 3, sonst 2: Stehen und Gehen)")
    ap.add_argument("--raw", action="store_true",
                    help="Stil nicht angleichen (Farbstärke, Helligkeit, Umriss bleiben wie generiert)")
    a = ap.parse_args()
    global PEOPLE, LOOKS_KT
    if a.repo:
        PEOPLE = os.path.join(a.repo, "game/src/main/resources/people")
        LOOKS_KT = os.path.join(a.repo, "core/src/main/kotlin/crazytower/core/Looks.kt")
    if not os.path.exists(os.path.join(PEOPLE, "index.txt")):
        sys.exit(f"Kein CrazyTower-Repo gefunden ({PEOPLE}). Mit --repo <Ordner> angeben.")

    sizes = dict(SIZES)
    for s in a.size:
        k, _, v = s.partition("=")
        sizes[look_name(k)] = float(v)

    files = {}
    for inp in a.inputs:
        if os.path.isdir(inp):
            found = [os.path.join(inp, f) for f in os.listdir(inp)
                     if f.lower().endswith(".png") and f != "people_preview.png"]
            found.sort(key=os.path.getmtime)  # newest last, so the newest variant of a look wins
        else:
            found = [inp]
        for f in found:
            files[look_name(f)] = f
    if not files:
        sys.exit("Keine PNG-Bilder gefunden.")

    known = stand_ins()
    ref = (STYLE_SATURATION, STYLE_BRIGHTNESS)
    index_path = os.path.join(PEOPLE, "index.txt")
    index = [l.strip() for l in open(index_path, encoding="utf-8") if l.strip()]
    rows = []
    for name, path in sorted(files.items()):
        new = sheet(poses(path, a.posen), sizes.get(name, 1.0))
        if not a.raw:
            new = match_style(new, ref)
        old = name if name in index else known.get(name)
        old_img = Image.open(os.path.join(PEOPLE, old + ".png")).convert("RGBA") if old in index else None
        rows.append((name, old, old_img, new))
        note = "" if name in known or name in index else "  (Achtung: kommt im Spiel noch nirgends vor)"
        print(f"  {name:28s} <- {os.path.basename(path)}{note}")
        if not a.dry_run:
            new.save(os.path.join(PEOPLE, name + ".png"))
            if name not in index:
                index.append(name)
    if not a.dry_run:
        open(index_path, "w", encoding="utf-8", newline="\n").write("\n".join(sorted(index)) + "\n")

    folder = a.inputs[0] if os.path.isdir(a.inputs[0]) else os.path.dirname(os.path.abspath(a.inputs[0]))
    shot = os.path.join(folder, "people_preview.png")
    preview(rows, shot)
    print(f"{len(rows)} Figuren {'geprüft' if a.dry_run else 'ins Spiel geholt'}. Vorschau: {shot}")


if __name__ == "__main__":
    main()
