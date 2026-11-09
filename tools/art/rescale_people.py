"""Skaliert Figuren-Blaetter neu: jede Figur wird ausgeschnitten, um [Faktor] vergroessert
(harte Pixel), auf den Boden des 48x96-Bildes gestellt und zwischen Kopf und Koerper zentriert,
damit Stehen und Gehen nicht springen.

    python tools/art/rescale_people.py <people-Ordner> 1.5 [npc_name ...]
"""
import os, sys
import numpy as np
from PIL import Image

FW, FH = 48, 96


def frame(img):
    a = np.asarray(img)[..., 3] > 0
    ys, xs = np.where(a)
    if not len(ys):
        return None
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    m = a[y0:y1, x0:x1]
    head = m[: max(4, (y1 - y0) // 4)]
    hx = np.where(head.any(0))[0]
    cx = (hx.min() + hx.max() + 1) / 2 if len(hx) else (x1 - x0) / 2
    return img.crop((x0, y0, x1, y1)), cx


def rescale(sheet, factor):
    out = Image.new("RGBA", sheet.size, (0, 0, 0, 0))
    for j in range(sheet.width // FW):
        got = frame(sheet.crop((j * FW, 0, (j + 1) * FW, FH)))
        if not got:
            continue
        im, cx = got
        w, h = max(1, round(im.width * factor)), max(1, round(im.height * factor))
        if h > FH - 1:
            w, h = max(1, round(w * (FH - 1) / h)), FH - 1
        big = im.resize((w, h), Image.NEAREST)
        c = (cx + im.width / 2) / 2 * (w / im.width)
        x, y = round(FW / 2 - c), FH - h
        cell = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
        cell.alpha_composite(big.crop((max(0, -x), 0, min(w, FW - x), h)), (max(0, x), y))
        out.alpha_composite(cell, (j * FW, 0))
    return out


def main():
    folder, factor, names = sys.argv[1], float(sys.argv[2]), sys.argv[3:]
    files = [n + ".png" for n in names] or sorted(f for f in os.listdir(folder) if f.startswith("npc_") and f.endswith(".png"))
    for f in files:
        p = os.path.join(folder, f)
        rescale(Image.open(p).convert("RGBA"), factor).save(p)
        print("  skaliert:", f)
    print(len(files), "Figuren um", factor, "skaliert")


if __name__ == "__main__":
    main()
