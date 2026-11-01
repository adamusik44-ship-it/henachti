"""Builds the adaptive launcher icon from Michael's artwork.

Usage:  python tools/make_icon.py <source image>

Finds the golden rounded square in the source, crops just inside its border, and writes
mipmap-*/ic_launcher_bg.png at the five densities (108dp canvas, artwork scaled to 86dp so the
launcher mask never cuts the tefillin). Also writes art/icon_source.jpg and art/icon_preview.png.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
ART = ROOT / "art"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
CANVAS_DP = 108
ART_DP = 82  # artwork span inside the 108dp canvas; the visible disc is 72dp


def find_gold_box(img: Image.Image):
    """Bounding box of the golden border: warm, saturated, bright pixels."""
    px = img.convert("RGB").load()
    w, h = img.size
    cols = [0] * w
    rows = [0] * h
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            if r > 120 and g > 90 and r > g > b and (r - b) > 40 and (r + g + b) > 330:
                cols[x] += 1
                rows[y] += 1
    # the border is a thin line, so a gold column/row has many hits (at least ~15% of the side)
    cx = [x for x in range(w) if cols[x] > h * 0.15]
    ry = [y for y in range(h) if rows[y] > w * 0.15]
    if not cx or not ry:
        raise SystemExit("could not find the golden border")
    return min(cx), min(ry), max(cx), max(ry)


def main(src: str):
    img = Image.open(src).convert("RGB")
    x0, y0, x1, y1 = find_gold_box(img)
    side = min(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    inset = max(6, int(side * 0.035))  # step well inside the border and its anti-aliasing
    half = side // 2 - inset
    box = (cx - half, cy - half, cx + half, cy + half)
    crop = img.crop(box)
    print(f"gold box: {(x0, y0, x1, y1)}  crop: {box}  size: {crop.size}")

    # background colour: average of the four corners of the crop
    cw, ch = crop.size
    k = max(6, cw // 40)
    samples = []
    for cx, cy in [(0, 0), (cw - k, 0), (0, ch - k), (cw - k, ch - k)]:
        region = crop.crop((cx, cy, cx + k, cy + k)).resize((1, 1), Image.LANCZOS)
        samples.append(region.getpixel((0, 0)))
    bg = tuple(sum(c[i] for c in samples) // len(samples) for i in range(3))
    print(f"background colour: #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")

    ART.mkdir(exist_ok=True)
    img.save(ART / "icon_source.jpg", quality=92)

    for name, scale in DENSITIES.items():
        canvas_px = int(CANVAS_DP * scale)
        art_px = int(ART_DP * scale)
        canvas = Image.new("RGB", (canvas_px, canvas_px), bg)
        art = crop.resize((art_px, art_px), Image.LANCZOS)
        off = (canvas_px - art_px) // 2
        canvas.paste(art, (off, off))
        out_dir = RES / f"mipmap-{name}"
        out_dir.mkdir(exist_ok=True)
        canvas.save(out_dir / "ic_launcher_bg.png", optimize=True)
        print(f"  {name}: {canvas_px}px")

    # preview: how a round launcher mask shows it (inner 72dp of 108dp)
    big = int(CANVAS_DP * 4)
    canvas = Image.new("RGB", (big, big), bg)
    art = crop.resize((ART_DP * 4, ART_DP * 4), Image.LANCZOS)
    off = (big - ART_DP * 4) // 2
    canvas.paste(art, (off, off))
    r = 72 * 4 // 2
    c = big // 2
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse((c - r, c - r, c + r, c + r), fill=255)
    preview = Image.new("RGBA", (big, big), (246, 240, 230, 255))
    preview.paste(canvas, (0, 0), mask)
    preview.crop((c - r - 8, c - r - 8, c + r + 8, c + r + 8)).save(ART / "icon_preview.png")
    print("preview written")


if __name__ == "__main__":
    main(sys.argv[1])
