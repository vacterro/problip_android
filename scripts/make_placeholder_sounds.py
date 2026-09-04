"""Generate placeholder premium sounds for Problip (Wave 5).

These are synthesized stand-ins so the catalog/random-pool architecture can be
built and tested before final audio assets exist. Replace the WAVs in
app/src/main/res/raw/ (blip_glass/wood/soft_bell/bonk/space) with properly
licensed final assets before any store release.

Stdlib only: python scripts/make_placeholder_sounds.py
"""

import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
PEAK = 0.45  # keep placeholders comfortably below full scale

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def write_wav(name: str, mono: list[float]) -> None:
    peak = max(abs(s) for s in mono) or 1.0
    frames = bytearray()
    for s in mono:
        v = int(max(-1.0, min(1.0, s / peak * PEAK)) * 32767)
        frames += struct.pack("<hh", v, v)  # stereo duplicate
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"blip_{name}.wav"
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(bytes(frames))
    print(f"{path.name}: {len(mono) / RATE * 1000:.0f} ms")


def env(t: float, attack: float, decay: float) -> float:
    if t < attack:
        return t / attack
    return math.exp(-(t - attack) / decay)


def glass() -> list[float]:
    n = int(RATE * 0.14)
    return [
        0.6 * math.sin(2 * math.pi * 2200 * i / RATE) * env(i / RATE, 0.001, 0.025)
        + 0.25 * math.sin(2 * math.pi * 3310 * i / RATE) * env(i / RATE, 0.001, 0.015)
        for i in range(n)
    ]


def wood() -> list[float]:
    n = int(RATE * 0.09)
    out = []
    for i in range(n):
        t = i / RATE
        body = math.sin(2 * math.pi * (620 - 120 * t * 10) * t) * env(t, 0.0005, 0.012)
        knock = 0.4 * math.sin(2 * math.pi * 180 * t) * env(t, 0.0005, 0.02)
        out.append(0.7 * body + knock)
    return out


def soft_bell() -> list[float]:
    n = int(RATE * 0.45)
    return [
        0.5 * math.sin(2 * math.pi * 880 * i / RATE) * env(i / RATE, 0.01, 0.12)
        + 0.3 * math.sin(2 * math.pi * 1320 * i / RATE) * env(i / RATE, 0.01, 0.08)
        + 0.15 * math.sin(2 * math.pi * 1760 * i / RATE) * env(i / RATE, 0.01, 0.05)
        for i in range(n)
    ]


def bonk() -> list[float]:
    n = int(RATE * 0.16)
    out = []
    for i in range(n):
        t = i / RATE
        f = 300 * math.exp(-t * 8) + 160
        out.append(math.sin(2 * math.pi * f * t) * env(t, 0.002, 0.045))
    return out


def space() -> list[float]:
    n = int(RATE * 0.5)
    rng = random.Random(7)
    noise = [rng.uniform(-1, 1) for _ in range(n)]
    out = []
    for i in range(n):
        t = i / RATE
        sweep = math.sin(2 * math.pi * (150 + 900 * t - 1800 * t * t) * t)
        shimmer = 0.08 * noise[i] * env(t, 0.05, 0.25)
        out.append(0.4 * sweep * env(t, 0.02, 0.2) + shimmer)
    return out


if __name__ == "__main__":
    write_wav("glass", glass())
    write_wav("wood", wood())
    write_wav("soft_bell", soft_bell())
    write_wav("bonk", bonk())
    write_wav("space", space())
