#!/usr/bin/env python3
"""Deterministically export canonical Problip brand assets for GitHub.

Sources of truth (never redraw by hand):
- geometry: app/src/main/res/drawable/ic_problip_mark.xml
  (viewport 24x24: ring outer 2..22, ring inner 5..19, core 9..15, even-odd)
- colors: app/src/main/java/com/vacster/problip/ui/theme/Palettes.kt
  (Golden Default: Bg #1A1810, Bevel #75663D, Gold #F0D060, TextMain #D4C89A)

Outputs:
- docs/images/problip-mark.png            (512x512, transparent)
- docs/images/github-social-preview.png    (1280x640)

Run from the repository root: python scripts/make_github_assets.py
"""

from PIL import Image, ImageDraw, ImageFont
import os

GOLD = (240, 208, 96, 255)        # #F0D060  Palettes.kt Gold
BG = (26, 24, 16, 255)           # #1A1810  Palettes.kt Bg
BEVEL = (117, 102, 61, 255)      # #75663D  Palettes.kt Bevel
BDARK = (16, 14, 8, 255)         # #100E08  Palettes.kt BDark
TEXT_MAIN = (212, 200, 154, 255)  # #D4C89A  Palettes.kt TextMain

VERDANA_BOLD = r"C:\Windows\Fonts\verdanab.ttf"
VERDANA = r"C:\Windows\Fonts\verdana.ttf"

# ic_problip_mark.xml geometry on the 24-unit viewport.
RING_OUTER = (2, 2, 22, 22)
RING_INNER = (5, 5, 19, 19)
CORE = (9, 9, 15, 15)

SUPERSAMPLE = 16


def _rect(box, scale):
    return [box[0] * scale, box[1] * scale, box[2] * scale, box[3] * scale]


def render_mark(size: int) -> Image.Image:
    big = size * SUPERSAMPLE
    scale = big / 24.0
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))

    mask = Image.new("L", (big, big), 0)
    dm = ImageDraw.Draw(mask)
    dm.rectangle(_rect(RING_OUTER, scale), fill=255)
    dm.rectangle(_rect(RING_INNER, scale), fill=0)
    gold_layer = Image.new("RGBA", (big, big), GOLD)
    img.paste(gold_layer, (0, 0), mask)

    d = ImageDraw.Draw(img)
    d.rectangle(_rect(CORE, scale), fill=GOLD)

    return img.resize((size, size), Image.LANCZOS)


def make_mark_png(path: str) -> None:
    render_mark(512).save(path)


def _draw_tracked(d: ImageDraw.ImageDraw, xy_center, text, font, tracking, fill):
    widths = [d.textlength(ch, font=font) for ch in text]
    total = sum(widths) + tracking * (len(text) - 1)
    x = xy_center[0] - total / 2
    ascent, descent = font.getmetrics()
    y = xy_center[1] - (ascent + descent) / 2
    for ch, w in zip(text, widths):
        d.text((x, y), ch, font=font, fill=fill)
        x += w + tracking


def make_social_preview(path: str) -> None:
    w, h = 1280, 640
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)

    # Vintage bevel frame: one Bevel band with a BDark inner hairline.
    d.rectangle([20, 20, w - 21, h - 21], outline=BEVEL, width=6)
    d.rectangle([30, 30, w - 31, h - 31], outline=BDARK, width=2)

    # Canonical mark.
    mark = render_mark(170)
    img.alpha_composite(mark, (w // 2 - mark.width // 2, 150 - mark.height // 2 + 60))

    # Wordmark + subtitle.
    title_font = ImageFont.truetype(VERDANA_BOLD, 92)
    sub_font = ImageFont.truetype(VERDANA, 30)
    _draw_tracked(d, (w // 2, 385), "PROBLIP", title_font, 14, GOLD)
    d.text(
        (w // 2, 470),
        "Native Android random-beep meditation timer",
        font=sub_font,
        fill=TEXT_MAIN,
        anchor="ma",
    )

    img.convert("RGB").save(path)


def main() -> None:
    out_dir = os.path.join("docs", "images")
    os.makedirs(out_dir, exist_ok=True)
    make_mark_png(os.path.join(out_dir, "problip-mark.png"))
    make_social_preview(os.path.join(out_dir, "github-social-preview.png"))
    print("wrote docs/images/problip-mark.png and docs/images/github-social-preview.png")


if __name__ == "__main__":
    main()
