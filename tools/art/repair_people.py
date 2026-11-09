"""Repariert Figuren-Blaetter, bei denen der Hintergrund beim Import nicht freigestellt wurde.

Jedes 48x96-Bild des Blatts wird einzeln behandelt: die Hintergrundfarbe wird aus dem Rand des
undurchsichtigen Rechtecks geschaetzt, von aussen her (Flood-Fill) weggenommen, Farbsaeume
werden entfaerbt und der dunkle Umriss neu gezogen. Pinke Kleidung innen bleibt, weil nur
zusammenhaengender Hintergrund vom Rand her entfernt wird.

    python repair_people.py <people-Ordner> [--dry-run]
"""
import os, sys
from collections import deque
import numpy as np
from PIL import Image

FW, FH = 48, 96
INK = np.array([16, 14, 24], float)


def broken(a):
    """Ein Bild ist kaputt, wenn sein undurchsichtiger Teil fast ein volles Rechteck ist."""
    for j in range(a.shape[1] // FW):
        op = a[:, j * FW:(j + 1) * FW, 3] > 0
        ys, xs = np.where(op)
        if len(ys) and op.sum() > 0.9 * (np.ptp(ys) + 1) * (np.ptp(xs) + 1) and op.sum() > 400:
            return True
    return False


def fix_frame(f):
    rgb = f[..., :3].astype(float)
    op = f[..., 3] > 0
    ys, xs = np.where(op)
    if not len(ys):
        return f
    y0, y1, x0, x1 = ys.min(), ys.max(), xs.min(), xs.max()
    ring = np.zeros_like(op)
    ring[y0:y1 + 1, x0:x1 + 1] = True
    ring[y0 + 2:y1 - 1, x0 + 2:x1 - 1] = False
    ring &= op
    # Hintergrundfarbe: Median des Rings ohne den dunklen Umriss
    cand = rgb[ring]
    bright = cand[cand.sum(1) > cand.sum(1).max() * 0.6]
    bg = np.median(bright if len(bright) else cand, axis=0)
    line = bg * 0.25 + INK * 0.75  # so hat der alte Import den Rand des Rechtecks abgedunkelt
    dbg = np.sqrt(((rgb - bg) ** 2).sum(-1))
    dline = np.sqrt(((rgb - line) ** 2).sum(-1))
    like = op & ((dbg < 70) | (dline < 20))
    # Flood-Fill von aussen
    h, w = op.shape
    gone = ~op.copy()
    q = deque((y, x) for y in range(h) for x in range(w) if gone[y, x])
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not gone[ny, nx] and like[ny, nx]:
                gone[ny, nx] = True
                q.append((ny, nx))
    # eingeschlossene Luecken (zwischen Arm und Koerper, zwischen den Beinen): nur sehr genau Hintergrundfarbe
    gone |= op & (dbg < 35)
    # eine Lage Saum: halb-pinke Pixel direkt am Hintergrund
    near = np.zeros_like(gone)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(gone, dy, 0), dx, 1)
    gone |= near & (dbg < 110) & ~gone
    inside = ~gone
    # Entfaerben: Magenta-Stich (R und B ueber G) am Rand wegnehmen
    near = np.zeros_like(gone)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(gone, dy, 0), dx, 1)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    spill = np.minimum(r, b) - g
    fringe = inside & near & (spill > 30)
    m = np.minimum(r, b)
    rgb[..., 0] = np.where(fringe, np.minimum(r, g + (r - m)), r)
    rgb[..., 2] = np.where(fringe, np.minimum(b, g + (b - m)), b)
    # Umriss neu
    edge = inside & near
    rgb = np.where(edge[..., None], rgb * 0.25 + INK * 0.75, rgb)
    out = np.dstack([rgb.clip(0, 255), np.where(inside, 255, 0)]).astype(np.uint8)
    out[~inside] = 0
    return out


def main():
    folder = sys.argv[1]
    dry = "--dry-run" in sys.argv
    n = 0
    for name in sorted(os.listdir(folder)):
        if not (name.startswith("npc_") and name.endswith(".png")):
            continue
        path = os.path.join(folder, name)
        a = np.asarray(Image.open(path).convert("RGBA")).copy()
        if not broken(a):
            continue
        for j in range(a.shape[1] // FW):
            a[:, j * FW:(j + 1) * FW] = fix_frame(a[:, j * FW:(j + 1) * FW])
        n += 1
        print("  repariert:", name)
        if not dry:
            Image.fromarray(a, "RGBA").save(path)
    print(n, "Figuren repariert" + (" (nur Probe)" if dry else ""))


if __name__ == "__main__":
    main()
