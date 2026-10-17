#!/usr/bin/env python3
"""Pixel judge: how much of a DriveDeck slot does the embedded app actually cover?

    judge.py <screenshot.png> --rect "L T R B" [--logical-width 1920] [--min-width 0.95]

--rect is the slot rectangle DriveDeck logs ("slot-rect <label> L T R B", logical px).
The screenshot is the physical panel (1280x480); the rect is scaled to it.
A column/row counts as covered when >= 5 % of its pixels differ from the slot's own
background (card #1C1C1E or black). Prints one line and exits 0 on PASS, 1 on FAIL.
No third-party modules: the PNG is converted with macOS `sips` and read as a BMP.
"""
import argparse
import os
import struct
import subprocess
import sys
import tempfile

BACKGROUNDS = [(0x1C, 0x1C, 0x1E), (0, 0, 0)]
TOLERANCE = 12


def read_bmp(path):
    with open(path, "rb") as f:
        data = f.read()
    offset = struct.unpack_from("<I", data, 10)[0]
    width, height = struct.unpack_from("<ii", data, 18)
    bpp = struct.unpack_from("<H", data, 28)[0]
    if bpp not in (24, 32):
        sys.exit(f"unsupported BMP depth {bpp}")
    step = bpp // 8
    stride = (width * step + 3) & ~3
    top_down = height < 0
    height = abs(height)

    def pixel(x, y):
        row = y if top_down else height - 1 - y
        i = offset + row * stride + x * step
        b, g, r = data[i], data[i + 1], data[i + 2]
        return r, g, b

    return width, height, pixel


def is_background(p):
    return any(all(abs(p[k] - bg[k]) <= TOLERANCE for k in range(3)) for bg in BACKGROUNDS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("png")
    ap.add_argument("--rect", required=True)
    ap.add_argument("--logical-width", type=int, default=1920)
    ap.add_argument("--min-width", type=float, default=0.95)
    ap.add_argument("--min-height", type=float, default=0.90)
    a = ap.parse_args()

    with tempfile.TemporaryDirectory() as tmp:
        bmp = os.path.join(tmp, "s.bmp")
        subprocess.run(["sips", "-s", "format", "bmp", a.png, "--out", bmp],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        w, h, px = read_bmp(bmp)

    k = w / a.logical_width
    l, t, r, b = (round(float(v) * k) for v in a.rect.split())
    inset = 3  # skip the card's rounded corners / anti-aliased edge
    l, t, r, b = l + inset, t + inset, min(r, w) - inset, min(b, h) - inset
    sw, sh = r - l, b - t

    cols = [0] * sw
    rows = [0] * sh
    covered = 0
    for y in range(t, b):
        for x in range(l, r):
            if not is_background(px(x, y)):
                cols[x - l] += 1
                rows[y - t] += 1
                covered += 1

    def span(counts, length):
        hit = [i for i, c in enumerate(counts) if c >= 0.05 * length]
        return (hit[-1] - hit[0] + 1) / len(counts) if hit else 0.0

    wr = span(cols, sh)
    hr = span(rows, sw)
    area = covered / (sw * sh)
    ok = wr >= a.min_width and hr >= a.min_height
    print(f"{'PASS' if ok else 'FAIL'} width={wr:.3f} height={hr:.3f} area={area:.3f} "
          f"slot={sw}x{sh}px@{l},{t}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
