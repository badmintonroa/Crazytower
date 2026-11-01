#!/usr/bin/env python3
"""Cuts the Steam trailer and the gameplay video from the clips of tools/steam/film.sh.

    python3 -I tools/steam/trailer.py <clips-folder> <out-folder> [de|en]

Writes trailer_<lang>.mp4 (about 75 s, title cards and captions) and gameplay_<lang>.mp4
(about 70 s, no text), both 1920 x 1080, 30 fps, H.264 + AAC 48 kHz, as Steam recommends.
The music comes from tools/steam/music.py.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import capsules  # noqa: E402  (logo and colours)

W, H, FPS = 1920, 1080, 30
FADE = 0.4  # crossfade between shots
FONT_BOLD = capsules.FONT
FONT_MEDIUM = capsules.ROOT / "game/src/main/resources/fonts/SpaceGrotesk-Medium.ttf"

TEXT = {
    "de": {
        "open1": "Ein Turm. 100 Etagen.",
        "open2": "Null Normalität.",
        "build": "Bau deinen Turm, Etage für Etage",
        "morph": "Aus dem Steuerbüro wird über Nacht eine Clownsagentur",
        "rush": "Jeder hat seinen eigenen Tagesablauf",
        "night": "Früh-, Spät- und Nachtschicht: Der Turm schläft nie",
        "giantcat": "Eine Riesenkatze auf dem Dach?",
        "moon": "Der ganze Turm auf dem Mond?",
        "celebrate": "Sterne sammeln, neue Mieter freischalten",
        "inspect": "Vampir-Versicherung, Socken-Archiv, Weltherrschaft Holding …",
        "tall": "Von der Bruchbude zum CrazyTower",
        "end1": "Jetzt auf die Wunschliste",
        "end2": "Demo bis 2 Sterne · Windows, Linux, Steam Deck",
    },
    "en": {
        "open1": "One tower. 100 floors.",
        "open2": "Zero normality.",
        "build": "Build your tower, floor by floor",
        "morph": "Overnight, the tax office becomes a clown agency",
        "rush": "Everyone has their own daily routine",
        "night": "Early, late and night shifts: the tower never sleeps",
        "giantcat": "A giant cat on the roof?",
        "moon": "The whole tower on the moon?",
        "celebrate": "Earn stars, unlock new tenants",
        "inspect": "Vampire insurance, odd sock archive, world domination …",
        "tall": "From shack to CrazyTower",
        "end1": "Wishlist now",
        "end2": "Demo up to 2 stars · Windows, Linux, Steam Deck",
    },
}

# (clip, start, length, caption key) — None as clip is a title card
TRAILER = [
    (None, 0, 2.6, "open1"),
    ("build", 0.0, 9.0, "build"),
    (None, 0, 2.0, "open2"),
    ("morph", 0.0, 9.0, "morph"),
    ("rush", 0.0, 6.0, "rush"),
    ("night", 0.0, 8.5, "night"),
    ("giantcat", 0.0, 6.0, "giantcat"),
    ("moon", 0.4, 6.4, "moon"),
    ("celebrate", 0.0, 5.0, "celebrate"),
    ("inspect", 0.0, 6.5, "inspect"),
    ("tall", 0.0, 10.0, "tall"),
    ("END", 0, 5.0, None),
]

GAMEPLAY = [
    ("play", 0.0, 24.0, None),
    ("morph", 0.0, 9.0, None),
    ("rush", 0.0, 7.0, None),
    ("inspect", 0.0, 7.0, None),
    ("night", 0.0, 9.0, None),
    ("giantcat", 0.0, 7.0, None),
    ("celebrate", 0.0, 6.0, None),
    ("moon", 0.0, 7.0, None),
]


def run(cmd):
    subprocess.run(cmd, check=True)


def caption(text, path):
    """A caption bar for the lower middle of the screen, transparent around it."""
    font = ImageFont.truetype(str(FONT_BOLD), 52)
    pad_x, pad_y = 44, 24
    w = int(font.getlength(text)) + pad_x * 2
    h = 52 + pad_y * 2 + 8
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x = (W - w) // 2
    y = 770
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((x + 6, y + 8, x + w + 6, y + h + 8), radius=18, fill=(0, 0, 0, 120))
    d.rounded_rectangle((x, y, x + w, y + h), radius=18, fill=capsules.INK + (235,))
    d.rounded_rectangle((x, y + h - 8, x + w, y + h), radius=4, fill=capsules.MINT + (255,))
    d.text((x + pad_x, y + pad_y - 4), text, font=font, fill=capsules.WHITE)
    img.save(path)


def card(text, path, sub=None, with_logo=False):
    """A full-screen title card in the game's colours."""
    img = Image.new("RGBA", (W, H), capsules.INK + (255,))
    d = ImageDraw.Draw(img)
    # a faint grid of windows, like the tower at night
    for row in range(0, H, 72):
        for col in range(0, W, 120):
            if (row // 72 * 7 + col // 120 * 3) % 5 == 0:
                d.rectangle((col + 20, row + 18, col + 92, row + 54), fill=(30, 36, 66))
    if with_logo:
        mark = capsules.logo(220)
        img.alpha_composite(mark, ((W - mark.width) // 2, 300))
        y = 300 + mark.height + 40
    else:
        y = 0
    if text:
        font = ImageFont.truetype(str(FONT_BOLD), 96 if not with_logo else 64)
        tw = font.getlength(text)
        ty = (H - 96) // 2 - 20 if not with_logo else y
        d.text(((W - tw) / 2, ty), text, font=font, fill=capsules.MINT if with_logo else capsules.WHITE)
        y = ty + 110
    if sub:
        font = ImageFont.truetype(str(FONT_MEDIUM), 40)
        sw = font.getlength(sub)
        d.text(((W - sw) / 2, y), sub, font=font, fill=(163, 168, 195))
    img.convert("RGB").save(path)


def segment(i, clip, start, length, cap, clips, tmp, lang):
    """One shot as a normalised mp4 (no sound) with its caption faded in and out."""
    out = tmp / f"seg{i:02d}.mp4"
    texts = TEXT[lang]
    if clip is None or clip == "END":
        png = tmp / f"card{i:02d}.png"
        if clip == "END":
            card(texts["end1"], png, texts["end2"], with_logo=True)
        else:
            card(texts[cap], png)
        run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-t", str(length), "-i", str(png),
             "-vf", f"fps={FPS},format=yuv420p", "-c:v", "libx264", "-crf", "14", "-preset", "medium", str(out)])
        return out
    src = clips / f"{clip}.mp4"
    base = f"[0:v]trim=start={start}:duration={length},setpts=PTS-STARTPTS,fps={FPS},scale={W}:{H}:flags=lanczos,format=yuva420p[v]"
    if cap:
        png = tmp / f"cap{i:02d}.png"
        caption(texts[cap], png)
        f_in, f_out = 0.5, length - 0.9
        filt = (f"{base};[1:v]format=rgba,fade=t=in:st={f_in}:d=0.35:alpha=1,fade=t=out:st={f_out}:d=0.35:alpha=1[c];"
                f"[v][c]overlay=0:0:shortest=1,format=yuv420p[o]")
        cmd = ["ffmpeg", "-v", "error", "-y", "-i", str(src), "-loop", "1", "-t", str(length), "-i", str(png),
               "-filter_complex", filt, "-map", "[o]"]
    else:
        cmd = ["ffmpeg", "-v", "error", "-y", "-i", str(src), "-filter_complex", base.replace("yuva420p", "yuv420p"), "-map", "[v]"]
    run(cmd + ["-c:v", "libx264", "-crf", "14", "-preset", "medium", "-t", str(length), str(out)])
    return out


def cut(plan, clips, out, lang, tmp):
    segs = [segment(i, *shot, clips, tmp, lang) for i, shot in enumerate(plan)]
    lengths = [shot[2] for shot in plan]
    total = sum(lengths) - FADE * (len(plan) - 1)
    music = tmp / "music.wav"
    run([sys.executable, "-I", str(HERE / "music.py"), str(music), f"{total:.2f}"])
    # chain the crossfades: each xfade starts FADE before the end of everything so far
    parts = []
    prev = "[0:v]"
    offset = 0.0
    for k in range(1, len(segs)):
        offset += lengths[k - 1] - FADE
        label = f"[x{k}]"
        parts.append(f"{prev}[{k}:v]xfade=transition=fade:duration={FADE}:offset={offset:.3f}{label}")
        prev = label
    filt = ";".join(parts) + f";{prev}fade=t=in:st=0:d=0.5,fade=t=out:st={total - 0.8:.3f}:d=0.8,format=yuv420p[v]"
    cmd = ["ffmpeg", "-v", "error", "-y"]
    for s in segs:
        cmd += ["-i", str(s)]
    cmd += ["-i", str(music), "-filter_complex", filt, "-map", "[v]", "-map", f"{len(segs)}:a",
            "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-maxrate", "16M", "-bufsize", "32M",
            "-profile:v", "high", "-pix_fmt", "yuv420p", "-r", str(FPS),
            "-c:a", "aac", "-b:a", "320k", "-ar", "48000", "-ac", "2", "-shortest", "-movflags", "+faststart", str(out)]
    run(cmd)
    print(f"{out.name}: {total:.1f} s")


def main():
    clips = Path(sys.argv[1])
    out = Path(sys.argv[2])
    lang = sys.argv[3] if len(sys.argv) > 3 else "de"
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        cut(TRAILER, clips, out / f"trailer_{lang}.mp4", lang, tmp)
        cut(GAMEPLAY, clips, out / f"gameplay_{lang}.mp4", lang, tmp)


if __name__ == "__main__":
    main()
