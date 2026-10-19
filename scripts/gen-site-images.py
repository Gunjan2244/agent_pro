#!/usr/bin/env python3
"""Bundled site illustrations: PNG masters -> WebP + manifest (docs/IMAGES.md, DECISIONS #81).

Usage (from the repo root; Pillow 12):
    python3 -I scripts/gen-site-images.py [--src design/site-images/source] [--out web/images]

- Reads only the top-level ``<src>/*.png`` (``spare/`` and ``needs-fix/`` are ignored).
- ``og-*`` masters become one 1200x630 WebP (centre crop to 1.905:1): ``og-<name>-1200.webp``.
- Every other master becomes ``<name>-400.webp`` and ``<name>-800.webp`` at the master's own aspect
  (16:9 or 4:3, rounded to whole pixels).
- Size budgets: 800 px <= 45 KB, 400 px <= 18 KB, OG <= 80 KB. A file over budget is re-encoded at a lower
  quality (a warning is printed) until it fits, down to quality 40; then the build fails.
- Writes ``<out>/manifest.json`` = {"<name>": {"w": 800, "h": 450, "sizes": [400, 800]}, ...}, where w/h are the
  largest file's pixel size. The renderer draws a bundled image only when its name is listed there.
- Old generated files for names that no longer have a master are deleted, so the folder matches the manifest.
"""
import argparse
import json
import re
import sys
from io import BytesIO
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
NAME = re.compile(r"^[a-z0-9-]+$")
WIDTHS = (400, 800)
OG_SIZE = (1200, 630)
BUDGET = {400: 18 * 1024, 800: 45 * 1024, "og": 80 * 1024}
START_Q, MIN_Q = 82, 40


def encode(img, budget, label):
    """WebP bytes at the highest quality (from START_Q, step 4) that fits the budget."""
    q = START_Q
    while True:
        buf = BytesIO()
        img.save(buf, "WEBP", quality=q, method=6)
        data = buf.getvalue()
        if len(data) <= budget:
            return data, q
        if q - 4 < MIN_Q:
            sys.exit(f"ERROR: {label} is {len(data)} B at quality {q}, over the {budget} B budget")
        print(f"  warn: {label} {len(data) // 1024} KB at q{q} > {budget // 1024} KB, re-encoding lower", file=sys.stderr)
        q -= 4


def cover(img, size):
    """Centre crop to the target aspect, then resize (LANCZOS)."""
    tw, th = size
    w, h = img.size
    if w * th > h * tw:  # too wide
        nw = round(h * tw / th)
        img = img.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = round(w * th / tw)
        img = img.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    return img.resize(size, Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=str(ROOT / "design/site-images/source"))
    ap.add_argument("--out", default=str(ROOT / "web/images"))
    a = ap.parse_args()
    src, out = Path(a.src), Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    manifest, written = {}, set()
    for p in sorted(src.glob("*.png")):
        name = p.stem
        if not NAME.match(name):
            print(f"  skip {p.name}: name must match ^[a-z0-9-]+$", file=sys.stderr)
            continue
        img = Image.open(p).convert("RGB")
        if name.startswith("og-"):
            data, q = encode(cover(img, OG_SIZE), BUDGET["og"], f"{name}-1200")
            f = f"{name}-1200.webp"
            (out / f).write_bytes(data)
            written.add(f)
            manifest[name] = {"w": OG_SIZE[0], "h": OG_SIZE[1], "sizes": [OG_SIZE[0]]}
            print(f"{f}: {len(data) / 1024:.1f} KB q{q}")
            continue
        w0, h0 = img.size
        for w in WIDTHS:
            h = round(w * h0 / w0)
            data, q = encode(img.resize((w, h), Image.LANCZOS), BUDGET[w], f"{name}-{w}")
            f = f"{name}-{w}.webp"
            (out / f).write_bytes(data)
            written.add(f)
            print(f"{f}: {w}x{h} {len(data) / 1024:.1f} KB q{q}")
        manifest[name] = {"w": WIDTHS[-1], "h": round(WIDTHS[-1] * h0 / w0), "sizes": list(WIDTHS)}
    for old in out.glob("*.webp"):
        if old.name not in written:
            old.unlink()
            print(f"  removed stale {old.name}")
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1, sort_keys=True) + "\n")
    print(f"manifest: {len(manifest)} images -> {out / 'manifest.json'}")


if __name__ == "__main__":
    main()
