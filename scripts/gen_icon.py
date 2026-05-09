"""Generate launcher PNG icons (mipmap-mdpi..xxxhdpi) for API 24-25 fallback.

Design matches the adaptive icon: purple background + simple open-book glyph.
"""
import io
import sys
from pathlib import Path
from PIL import Image, ImageDraw

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).parent.parent / "app" / "src" / "main" / "res"

PURPLE = (91, 79, 207)        # #5B4FCF
WHITE = (255, 255, 255)
SHADOW = (224, 220, 245)      # #E0DCF5

# (folder, size_px)
SIZES = [
    ("mipmap-mdpi", 48),
    ("mipmap-hdpi", 72),
    ("mipmap-xhdpi", 96),
    ("mipmap-xxhdpi", 144),
    ("mipmap-xxxhdpi", 192),
]


def draw_book(d: ImageDraw.ImageDraw, size: int) -> None:
    """Draw the same open-book glyph used in the adaptive vector."""
    # 108x108 is the design canvas; scale into `size`
    s = size / 108.0
    def pt(x, y):
        return (x * s, y * s)

    # Left page
    d.polygon([pt(22, 38), pt(52, 42), pt(52, 76), pt(22, 72)], fill=WHITE)
    # Right page
    d.polygon([pt(86, 38), pt(56, 42), pt(56, 76), pt(86, 72)], fill=WHITE)
    # Spine shadow
    d.polygon([pt(52, 42), pt(56, 42), pt(56, 76), pt(52, 76)], fill=SHADOW)
    # Text lines on pages (3 each side)
    for y_off in (50, 58, 66):
        # left page line
        d.polygon([
            pt(28, y_off), pt(48 - (y_off - 50) * 0.25, y_off + 2),
            pt(48 - (y_off - 50) * 0.25, y_off + 4), pt(28, y_off + 2),
        ], fill=PURPLE)
        # right page line
        d.polygon([
            pt(60 + (y_off - 50) * 0.25, y_off + 2), pt(80 - (y_off - 50) * 0.5, y_off),
            pt(80 - (y_off - 50) * 0.5, y_off + 2), pt(60 + (y_off - 50) * 0.25, y_off + 4),
        ], fill=PURPLE)


def generate(folder: str, size: int) -> None:
    out_dir = ROOT / folder
    out_dir.mkdir(parents=True, exist_ok=True)

    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        img = Image.new("RGBA", (size, size), PURPLE)
        d = ImageDraw.Draw(img)
        if name.endswith("_round.png"):
            # Mask to a circle
            mask = Image.new("L", (size, size), 0)
            ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
            img.putalpha(mask)
        draw_book(d, size)
        img.save(out_dir / name)
        print(f"  {folder}/{name} ({size}x{size})")


def main() -> None:
    for folder, size in SIZES:
        generate(folder, size)
    print("done")


if __name__ == "__main__":
    main()
