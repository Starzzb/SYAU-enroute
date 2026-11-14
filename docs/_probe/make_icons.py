"""把主人给的角色图切成 Android 各密度图标。

要点：
1. 原图已经自带圆角方框（四角是纯白），直接当 legend/legacy 图标没问题；
2. 但 adaptive icon 会被系统用圆形/方形遮罩再切一次，白色圆角会在边缘露出来 →
   做法是把图**略微放大（1.10×）**盖住四角，并取四角的白作为 adaptive 背景色，
   这样即使遮罩比图小，露出来的也是同色；
3. 前景按 Android 规范给 108dp 画布（内容居中），legacy 图标给 48dp。
"""
from PIL import Image, ImageDraw
import os

SRC = r"O:\dev\enroute-android\docs\assets\app-icon-source.png"
RES = r"O:\dev\enroute-android\app\src\main\res"

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

ZOOM = 0.66          # 主人反馈"图标还是有点大"：再留一圈白边，保证耳朵完整且不贴边
LEGACY_ZOOM = 1.0    # legacy 图标原样


def load():
    im = Image.open(SRC).convert("RGBA")
    # 裁成正方形（居中）
    w, h = im.size
    s = min(w, h)
    return im.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2))


def corner_color(im):
    """取四角 36x36 的平均色，作为 adaptive 背景色"""
    s = im.size[0]
    boxes = [(0, 0, 36, 36), (s - 36, 0, s, 36), (0, s - 36, 36, s), (s - 36, s - 36, s, s)]
    tot = [0, 0, 0]
    n = 0
    for b in boxes:
        patch = im.crop(b).convert("RGB")
        for px in patch.getdata():
            tot[0] += px[0]
            tot[1] += px[1]
            tot[2] += px[2]
            n += 1
    return tuple(v // n for v in tot)


def paste_centered(canvas_size, art, zoom, bg):
    canvas = Image.new("RGBA", (canvas_size, canvas_size), bg + (255,))
    target = int(canvas_size * zoom)
    scaled = art.resize((target, target), Image.LANCZOS)
    off = (canvas_size - target) // 2
    canvas.alpha_composite(scaled, (off, off))
    return canvas


def circle_mask(im):
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, im.size[0] - 1, im.size[1] - 1), fill=255)
    out.paste(im, (0, 0), mask)
    return out


def main():
    art = load()
    bg = corner_color(art)
    print("corner/background color =", bg)

    for name, scale in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)

        # legacy 图标（48dp）
        legacy_px = int(round(48 * scale))
        legacy = paste_centered(legacy_px, art, LEGACY_ZOOM, bg)
        legacy.save(os.path.join(d, "ic_launcher.png"), "PNG", optimize=True)
        circle_mask(legacy).save(os.path.join(d, "ic_launcher_round.png"), "PNG", optimize=True)

        # adaptive 前景（108dp 画布）
        fg_px = int(round(108 * scale))
        fg = paste_centered(fg_px, art, ZOOM, bg)
        fg.save(os.path.join(d, "ic_launcher_fg.png"), "PNG", optimize=True)
        print(f"{name}: legacy {legacy_px}px, fg {fg_px}px")

    # adaptive 背景色：colors.xml 里已有 ic_launcher_background，这里只校验不重写
    hexcol = "#%02X%02X%02X" % bg
    print("adaptive background should be:", hexcol, "(set in values/colors.xml)")

    # adaptive icon 描述（v26+）
    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    for fname, rounded in (("ic_launcher.xml", False), ("ic_launcher_round.xml", True)):
        with open(os.path.join(anydpi, fname), "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n')
            f.write('<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n')
            f.write('    <background android:drawable="@color/ic_launcher_background" />\n')
            f.write('    <foreground android:drawable="@mipmap/ic_launcher_fg" />\n')
            if rounded:
                f.write('    <monochrome android:drawable="@mipmap/ic_launcher_fg" />\n')
            f.write('</adaptive-icon>\n')
    print("adaptive xml + background color written:", hexcol)


if __name__ == "__main__":
    main()
