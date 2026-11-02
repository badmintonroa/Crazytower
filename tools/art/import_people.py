"""Puts generated figure pictures (ComfyUI, Gemini, ...) into the game as people sprite sheets.

Each picture shows one figure three times side by side on a magenta background (#FF00FF): standing,
walking with the left leg forward, walking with the right leg forward. That is what the ComfyUI
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
    if im.mode in ("RGBA", "LA") and np.asarray(im.convert("RGBA"))[..., 3].min() < 250:
        rgba = np.asarray(im.convert("RGBA"))
        return rgba, rgba[..., 3] / 255.0
    a = np.asarray(im.convert("RGB")).astype(int)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    d = np.sqrt((255 - r) ** 2 + g ** 2 + (255 - b) ** 2)
    alpha = np.clip((d - 90) / 80, 0, 1)
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


def poses(path):
    rgba, alpha = key(Image.open(path))
    mask = alpha > 0.5
    h, w = mask.shape
    out = []
    for j in range(3):
        l, r = w * j // 3, w * (j + 1) // 3
        m = mask[:, l:r]
        ys, xs = solid(m, 1, 3), solid(m, 0, 3)
        if len(ys) == 0 or len(xs) == 0:
            sys.exit(f"{os.path.basename(path)}: im {j + 1}. Drittel ist keine Figur zu finden.")
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        m = m[y0:y1, x0:x1]
        head = m[: max(8, (y1 - y0) // 4)]
        hx = np.where(head.any(0))[0]
        cx = (hx.min() + hx.max()) / 2 if len(hx) else (x1 - x0) / 2
        out.append(dict(img=Image.fromarray(rgba[y0:y1, l + x0:l + x1]), h=y1 - y0, w=x1 - x0, cx=cx))
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
    a = ap.parse_args()

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
    index_path = os.path.join(PEOPLE, "index.txt")
    index = [l.strip() for l in open(index_path, encoding="utf-8") if l.strip()]
    rows = []
    for name, path in sorted(files.items()):
        new = sheet(poses(path), sizes.get(name, 1.0))
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
