#!/usr/bin/env python3
"""A cheerful, slightly silly music bed for the CrazyTower trailer, synthesised from scratch
(no samples, no licences): bouncy bass, plucky chords, a whistling lead, drums and the odd honk.

    python3 tools/steam/music.py out.wav [seconds]

48 kHz, 16 bit stereo, ends with a final chord and fades out over the last two seconds.
"""
import sys
import wave

import numpy as np

SR = 48_000
BPM = 124
BEAT = 60 / BPM
rng = np.random.default_rng(7)


def note_hz(n):  # MIDI note number
    return 440.0 * 2 ** ((n - 69) / 12)


def env(length, attack=0.005, decay=0.15, sustain=0.0, release=0.05):
    n = int(length * SR)
    t = np.arange(n) / SR
    a = np.clip(t / attack, 0, 1)
    d = sustain + (1 - sustain) * np.exp(-np.maximum(t - attack, 0) / decay)
    r = np.clip((length - t) / release, 0, 1)
    return a * d * r


def osc(freq, length, kind="saw", vibrato=0.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = freq * (1 + vibrato * np.sin(2 * np.pi * 5.5 * t))
    phase = np.cumsum(f) / SR
    if kind == "sine":
        return np.sin(2 * np.pi * phase)
    if kind == "square":
        return np.sign(np.sin(2 * np.pi * phase)) * 0.6
    if kind == "tri":
        return 2 * np.abs(2 * (phase % 1) - 1) - 1
    return 2 * (phase % 1) - 1  # saw


def lowpass(x, cutoff):
    # one-pole low-pass, cheap and good enough for softening saws
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def add(buf, sig, start, gain=1.0, pan=0.0):
    i = int(start * SR)
    if i >= len(buf):
        return
    sig = sig[: len(buf) - i]
    left = np.cos((pan + 1) * np.pi / 4)
    right = np.sin((pan + 1) * np.pi / 4)
    buf[i:i + len(sig), 0] += sig * gain * left
    buf[i:i + len(sig), 1] += sig * gain * right


def kick():
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    f = 50 + 120 * np.exp(-t * 30)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)


def snare():
    n = int(0.22 * SR)
    t = np.arange(n) / SR
    noise = rng.uniform(-1, 1, n)
    return (noise * 0.7 + np.sin(2 * np.pi * 190 * t) * 0.5) * np.exp(-t * 18)


def hat(open_=False):
    n = int((0.18 if open_ else 0.05) * SR)
    t = np.arange(n) / SR
    noise = rng.uniform(-1, 1, n)
    noise = noise - lowpass(noise, 6000)  # rough high-pass
    return noise * np.exp(-t * (12 if open_ else 60))


def honk():
    # a clown horn: two detuned squares sliding down a little
    length = 0.32
    n = int(length * SR)
    t = np.arange(n) / SR
    f = 420 - 60 * t / length
    ph = np.cumsum(f) / SR
    s = np.sign(np.sin(2 * np.pi * ph)) + np.sign(np.sin(2 * np.pi * ph * 1.26))
    return lowpass(s * 0.4, 2500) * env(length, 0.01, 0.3, 0.6, 0.06)


# C major, I - vi - IV - V with a cheeky bVII for the bridge
PROGRESSION = [
    (48, [60, 64, 67]),  # C
    (45, [57, 60, 64]),  # Am
    (41, [57, 60, 65]),  # F
    (43, [55, 59, 62]),  # G
]
BRIDGE = [
    (41, [57, 60, 65]),  # F
    (43, [55, 59, 62]),  # G
    (46, [58, 62, 65]),  # Bb
    (43, [55, 59, 62]),  # G
]
# the whistled tune, (beat offset in the bar, midi note, length in beats)
MELODY = [
    [(0, 72, 0.5), (0.5, 74, 0.5), (1, 76, 1), (2, 79, 0.75), (3, 76, 0.5), (3.5, 74, 0.5)],
    [(0, 72, 1), (1, 69, 0.5), (1.5, 72, 0.5), (2, 76, 1.5), (3.5, 74, 0.5)],
    [(0, 72, 0.5), (0.5, 72, 0.5), (1, 77, 1), (2, 76, 0.5), (2.5, 74, 0.5), (3, 72, 1)],
    [(0, 71, 1.5), (1.5, 74, 0.5), (2, 79, 1), (3, 77, 0.25), (3.25, 76, 0.25), (3.5, 74, 0.5)],
]


def render(seconds):
    total = int((seconds + 1) * SR)
    buf = np.zeros((total, 2))
    bar = 4 * BEAT
    bars = int(seconds / bar)
    for b in range(bars):
        start = b * bar
        section = "intro" if b < 2 else ("bridge" if 10 <= b < 14 else "main")
        if b == bars - 1:
            section = "end"
        root, chord = (BRIDGE if section == "bridge" else PROGRESSION)[b % 4]
        if section == "end":
            root, chord = 48, [60, 64, 67, 72]
        # bass: bouncing octaves
        if section != "intro":
            pattern = [(0, 0), (0.5, 12), (1, 0), (1.5, 12), (2, 0), (2.5, 12), (3, 7), (3.5, 12)]
            if section == "end":
                pattern = [(0, 0)]
            for off, iv in pattern:
                length = 3.8 * BEAT if section == "end" else 0.45 * BEAT
                s = lowpass(osc(note_hz(root - 12 + iv), length, "saw"), 700) * env(length, 0.004, 0.12, 0.4, 0.03)
                add(buf, s, start + off * BEAT, 0.55)
        # plucked chords on the off-beats
        offs = [0.5, 1.5, 2.5, 3.5] if section != "end" else [0]
        for off in offs:
            length = 0.35 * BEAT if section != "end" else 3.8 * BEAT
            for k, n in enumerate(chord):
                s = osc(note_hz(n), length, "square") * env(length, 0.003, 0.08 if section != "end" else 1.2, 0.0, 0.03)
                add(buf, lowpass(s, 3000), start + off * BEAT, 0.09, pan=-0.4 + 0.4 * k)
        # drums
        if section in ("main", "bridge"):
            for beat in range(4):
                add(buf, kick(), start + beat * BEAT, 0.8)
                if beat in (1, 3):
                    add(buf, snare(), start + beat * BEAT, 0.35)
                add(buf, hat(), start + (beat + 0.5) * BEAT, 0.18, pan=0.3)
                add(buf, hat(), start + beat * BEAT, 0.1, pan=0.3)
            if b % 4 == 3:
                add(buf, hat(True), start + 3.5 * BEAT, 0.2, pan=0.3)
        elif section == "intro":
            for beat in range(4):
                add(buf, hat(), start + beat * BEAT, 0.12, pan=0.3)
        elif section == "end":
            add(buf, kick(), start, 0.9)
            add(buf, hat(True), start, 0.3)
        # whistle lead in the main parts
        if section == "main" and b >= 2:
            for off, n, beats in MELODY[b % 4]:
                length = beats * BEAT
                s = osc(note_hz(n), length, "tri", vibrato=0.006) + 0.3 * osc(note_hz(n + 12), length, "sine")
                add(buf, s * env(length, 0.02, 0.4, 0.6, 0.06), start + off * BEAT, 0.16, pan=0.15)
        if section == "bridge":
            # a counter line on the bridge, an octave lower and softer
            for off, n, beats in MELODY[(b + 2) % 4]:
                length = beats * BEAT
                s = osc(note_hz(n - 12), length, "tri", vibrato=0.01)
                add(buf, s * env(length, 0.02, 0.3, 0.5, 0.05), start + off * BEAT, 0.16, pan=-0.2)
        # the odd honk
        if b in (5, 9, 15, 21, 27):
            add(buf, honk(), start + 3.5 * BEAT, 0.3, pan=0.5)
            add(buf, honk(), start + 3.75 * BEAT, 0.3, pan=-0.5)
    buf = buf[: int(seconds * SR)]
    fade = int(2.0 * SR)
    buf[-fade:] *= np.linspace(1, 0, fade)[:, None]
    buf /= max(1e-9, np.abs(buf).max()) / 0.85
    return buf


def main():
    out = sys.argv[1]
    seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 72.0
    data = (render(seconds) * 32767).astype("<i2")
    with wave.open(out, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


if __name__ == "__main__":
    main()
