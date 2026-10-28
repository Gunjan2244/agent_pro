#!/usr/bin/env python3
"""Full-page pictures of the 5 templates for the app's template grid (docs/WEB_RENDERER.md, Template page strips;
DECISIONS #120).

Builds a WEB_DEV=1 copy of the site in a temp dir (web/dist is backed up first and restored after, so a dev build is
never left behind), serves it on localhost:8080 (the host the dev build's SITE_BASE_URL expects, and the only host
where ?mock=1 works), opens /?mock=1&s=<fixture>&t=<key> at 390 px wide with motion reduced (tickers and sliders still),
loads every lazy image, takes a full-page screenshot and cuts it into strips of at most STRIP_PX tall (tall bitmaps
exceed GPU texture limits on some phones). Writes
mobile/composeApp/src/commonMain/composeResources/drawable/template_page_<key>_<n>.webp, n from 1, and removes stale
strips of the same key.

Run whenever a template's look changes, next to the preview.webp screenshots:
    python3 scripts/gen-template-pages.py [key ...]
Needs Python Playwright (with its bundled Chromium) and Pillow.
"""
import functools
import http.server
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading

from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, "web", "dist")
OUT = os.path.join(ROOT, "mobile", "composeApp", "src", "commonMain", "composeResources", "drawable")
PORT = 8080
WIDTH = 390  # CSS px, the phone width of the preview.webp shots
SCALE = 1  # device pixels per CSS px; the tile is ~170 dp wide, so 390 px is enough and keeps the decoded bitmaps small
STRIP_PX = 2048
QUALITY = 75
ARGS = ["--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage"]  # headless Chrome segfaults here without them
# The Meet me templates show the demo agent; the Best offers ones the offers fixture (as preview.webp).
FIXTURE = {"classic": "demo", "modern": "demo", "minimal": "demo", "spotlight": "offers", "guide": "offers"}

LOAD_IMAGES = """async () => {
  document.querySelectorAll('img').forEach((i) => { i.loading = 'eager'; });
  await Promise.all([...document.images].map((i) => i.complete ? null : new Promise((r) => { i.onload = i.onerror = r; })));
  await document.fonts.ready;
}"""


def chromium():
    """Playwright's own Chromium, else (CHROMIUM env, or a cached Playwright build) a fallback; None = Playwright's."""
    if os.environ.get("CHROMIUM"):
        return os.environ["CHROMIUM"]
    cache = os.path.expanduser("~/.cache/ms-playwright")
    for d in sorted(os.listdir(cache) if os.path.isdir(cache) else [], reverse=True):
        for exe in ("chrome-headless-shell-linux64/chrome-headless-shell", "chrome-linux64/chrome", "chrome-linux/chrome"):
            if os.path.isfile(os.path.join(cache, d, exe)):
                return os.path.join(cache, d, exe)
    return None


def template_keys():
    with open(os.path.join(ROOT, "shared", "templates", "index.json"), encoding="utf-8") as f:
        return [t["key"] for t in sorted(json.load(f)["templates"], key=lambda t: t["order"])]


def build_dev_copy(dest):
    backup = None
    if os.path.isdir(DIST):
        backup = tempfile.mkdtemp(prefix="dist-backup-")
        shutil.rmtree(backup)
        shutil.move(DIST, backup)
    try:
        env = dict(os.environ, WEB_DEV="1", SITE_BASE_URL=f"http://localhost:{PORT}")
        env.pop("RELEASE", None)
        subprocess.run(["sh", os.path.join(ROOT, "scripts", "build-web.sh")], check=True, env=env)
        shutil.move(DIST, dest)
    finally:
        if os.path.isdir(DIST):
            shutil.rmtree(DIST)
        if backup:
            shutil.move(backup, DIST)


def serve(directory):
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *args):
            pass

    handler = functools.partial(Quiet, directory=directory)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def write_strips(key, png):
    page = Image.open(io.BytesIO(png)).convert("RGB")
    for old in os.listdir(OUT):
        if old.startswith(f"template_page_{key}_") and old.endswith(".webp"):
            os.remove(os.path.join(OUT, old))
    n = 0
    for top in range(0, page.height, STRIP_PX):
        n += 1
        strip = page.crop((0, top, page.width, min(top + STRIP_PX, page.height)))
        strip.save(os.path.join(OUT, f"template_page_{key}_{n}.webp"), "WEBP", quality=QUALITY, method=6)
    size = sum(os.path.getsize(os.path.join(OUT, f"template_page_{key}_{i}.webp")) for i in range(1, n + 1))
    print(f"{key}: {page.width}x{page.height} px, {n} strips, {size // 1024} KB")


def main():
    keys = sys.argv[1:] or template_keys()
    work = tempfile.mkdtemp(prefix="template-pages-")
    site = os.path.join(work, "site")
    try:
        build_dev_copy(site)
        server = serve(site)
        try:
            with sync_playwright() as p:
                try:
                    browser = p.chromium.launch(args=ARGS)
                except Exception:  # Playwright's pinned build isn't downloaded: use a cached one
                    browser = p.chromium.launch(executable_path=chromium(), args=ARGS)
                ctx = browser.new_context(
                    viewport={"width": WIDTH, "height": 844}, device_scale_factor=SCALE,
                    reduced_motion="reduce", is_mobile=True, has_touch=True,
                )
                for key in keys:
                    page = ctx.new_page()
                    page.goto(f"http://localhost:{PORT}/?mock=1&s={FIXTURE[key]}&t={key}", wait_until="networkidle")
                    page.wait_for_selector("#root > *")
                    page.evaluate(LOAD_IMAGES)
                    page.wait_for_timeout(1000)
                    write_strips(key, page.screenshot(full_page=True, animations="disabled"))
                    page.close()
                browser.close()
        finally:
            server.shutdown()
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
