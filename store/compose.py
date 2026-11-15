#!/usr/bin/env python3
"""
Makes the Google Play graphics from the store screenshots (store/screenshots.py, kept on the store-raw
branch): each screenshot in a frame with a caption on Lamplight's dark ground, and the feature graphic.
Uses the app's own fonts. Needs Pillow and numpy.

    git clone -b store-raw https://github.com/resz073/sleep-tracker.git raw
    python3 store/compose.py raw            # into store/
    python3 store/compose.py raw /tmp/out   # somewhere else, to look first
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "native", "app", "src", "main", "res", "font")

GROUND = (21, 16, 17)      # #151011, the app's dark screen
INK = (226, 209, 186)      # #E2D1BA
MUTE = (168, 147, 127)     # #A8937F
AMBER = (207, 149, 82)     # #CF9552
BEZEL = (43, 34, 32)       # #2B2220
EDGE = (58, 47, 44)        # #3A2F2C

# Play's phone screenshots: (file, raw screenshot, caption)
SHOTS = [
    ("01-today.png", "dark-today", "Log your night in two taps"),
    ("02-trends.png", "dark-trends-earlier", "See which nights met your goal"),
    ("03-schedule.png", "dark-schedule", "Get a bedtime that fits your wake-up time"),
    ("04-history.png", "light-history", "Every night, week by week"),
    ("05-light.png", "light-today", "In light mode, your wallpaper’s colours"),
    ("06-edit.png", "dark-edit", "Add or fix any night, and rate it"),
]


def serif(size, weight=450, opsz=48):
    f = ImageFont.truetype(os.path.join(FONTS, "literata.ttf"), size)
    f.set_variation_by_axes([opsz, weight])
    return f


def sans(size, weight=400):
    f = ImageFont.truetype(os.path.join(FONTS, "atkinson_next.ttf"), size)
    f.set_variation_by_axes([weight])
    return f


def ground(w, h, center, radius, strength=0.10):
    """The dark ground with the lamp's faint amber light"""
    y, x = np.mgrid[0:h, 0:w]
    d = np.sqrt((x - center[0]) ** 2 + (y - center[1]) ** 2) / radius
    a = np.clip(1 - d, 0, 1) ** 1.6 * strength
    px = np.empty((h, w, 3))
    for i in range(3):
        px[..., i] = GROUND[i] * (1 - a) + AMBER[i] * a
    return Image.fromarray(px.round().astype(np.uint8), "RGB")


def rounded_mask(size, radius, scale=4):
    """A rounded rectangle with smooth edges (drawn large, then made smaller)"""
    w, h = size
    m = Image.new("L", (w * scale, h * scale), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * scale - 1, h * scale - 1], radius * scale, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def phone(canvas, shot, left, top, height, bezel=14, crop_bottom=None):
    """The screenshot in a dark phone frame, its top left corner at (left, top)"""
    sh = height - 2 * bezel
    sw = round(shot.width * sh / shot.height)
    screen = shot.convert("RGB").resize((sw, sh), Image.LANCZOS)
    fw, fh = sw + 2 * bezel, height
    edge = rounded_mask((fw + 6, fh + 6), 64 + 3)
    canvas.paste(Image.new("RGB", edge.size, EDGE), (left - 3, top - 3), edge)
    canvas.paste(Image.new("RGB", (fw, fh), BEZEL), (left, top), rounded_mask((fw, fh), 64))
    canvas.paste(screen, (left + bezel, top + bezel), rounded_mask((sw, sh), 52))
    return fw


def wrap(draw, text, font, width):
    """One line if it fits, otherwise two lines of about the same length"""
    if draw.textlength(text, font=font) <= width:
        return [text]
    words = text.split()
    best = None
    for i in range(1, len(words)):
        a, b = " ".join(words[:i]), " ".join(words[i:])
        longest = max(draw.textlength(a, font=font), draw.textlength(b, font=font))
        if best is None or longest < best[0]:
            best = (longest, [a, b])
    return best[1]


def screenshot(raw, out, caption):
    W, H = 1080, 2100
    canvas = ground(W, H, (540, 330), 820)
    draw = ImageDraw.Draw(canvas)
    font = serif(68, 430, 60)
    lines = wrap(draw, caption, font, 900)
    step = 88
    block = step * len(lines)
    y = (330 - block) // 2 + 16
    for line in lines:
        draw.text((W / 2, y + step / 2), line, font=font, fill=INK, anchor="mm")
        y += step
    shot = Image.open(raw)
    height = 1700
    sw = round(shot.width * (height - 28) / shot.height) + 28
    phone(canvas, shot, (W - sw) // 2, 340, height)
    canvas.save(out, optimize=True)


def wave(draw, x0, x1, y, amp, periods, width, color):
    """The app's wave: a round pen moved along a sine curve"""
    r = width / 2
    n = int((x1 - x0) * 2)
    for i in range(n + 1):
        x = x0 + (x1 - x0) * i / n
        cy = y - amp * math.sin((x - x0) / (x1 - x0) * periods * 2 * math.pi)
        draw.ellipse([x - r, cy - r, x + r, cy + r], fill=color)


def feature(raw_today, out):
    W, H = 1024, 500
    canvas = ground(W, H, (330, 230), 560, 0.12)
    # The phone on the right, running off the bottom edge
    shot = Image.open(raw_today)
    fw = 360
    ph = round((fw - 28) * shot.height / shot.width) + 28
    layer = Image.new("RGB", (W, max(H, 56 + ph + 4)))
    layer.paste(canvas, (0, 0))
    phone(layer, shot, W - fw - 48, 56, ph)
    canvas = layer.crop((0, 0, W, H))
    # The wave mark, drawn large for smooth edges
    s = 4
    mark = Image.new("RGBA", (180 * s, 60 * s), (0, 0, 0, 0))
    wave(ImageDraw.Draw(mark), 8 * s, 172 * s, 30 * s, 13 * s, 3, 9 * s, AMBER + (255,))
    mark = mark.resize((180, 60), Image.LANCZOS)
    canvas.paste(mark, (64, 112), mark)
    draw = ImageDraw.Draw(canvas)
    draw.text((70, 268), "Sleep Tracker", font=serif(72, 420, 72), fill=INK, anchor="ls")
    y = 318
    for line in ["Log your sleep, see your sleep debt,", "and find a bedtime that fits."]:
        draw.text((72, y), line, font=sans(27), fill=MUTE, anchor="ls")
        y += 38
    canvas.save(out, optimize=True)


def main():
    raw = sys.argv[1] if len(sys.argv) > 1 else "raw"
    out = sys.argv[2] if len(sys.argv) > 2 else HERE
    shots = os.path.join(out, "screenshots")
    os.makedirs(shots, exist_ok=True)
    for name, src, caption in SHOTS:
        screenshot(os.path.join(raw, src + ".png"), os.path.join(shots, name), caption)
        print("made", name)
    feature(os.path.join(raw, "dark-today.png"), os.path.join(out, "feature-graphic.png"))
    print("made feature-graphic.png")


if __name__ == "__main__":
    main()
