// shared/catalog.json accentSwatches -> <out>/<swatch>-192.png and -512.png (web app manifest icons, DECISIONS #65).
// A white head-and-shoulders figure on the full-bleed accent colour, kept inside the maskable safe zone
// (a circle of radius 0.4). No insurer artwork (CLAUDE.md, Branding). No dependencies: a tiny PNG encoder.
// Usage: node scripts/gen-icons.mjs <out-dir>
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { deflateSync } from "node:zlib";

export const SIZES = [192, 512];
const SS = 4; // supersampling per axis, for smooth edges

// Unit-square geometry of the figure.
const HEAD = { cx: 0.5, cy: 0.4, r: 0.13 };
const BODY = { cx: 0.5, cy: 0.8, rx: 0.25, ry: 0.2, bottom: 0.7 };
export function inFigure(x, y) {
  const hx = x - HEAD.cx, hy = y - HEAD.cy;
  if (hx * hx + hy * hy <= HEAD.r * HEAD.r) return true;
  if (y > BODY.bottom) return false;
  const bx = (x - BODY.cx) / BODY.rx, by = (y - BODY.cy) / BODY.ry;
  return bx * bx + by * by <= 1;
}

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = (buf) => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

export function iconPng(hex, size) {
  const bg = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const rows = Buffer.alloc((size * 3 + 1) * size);
  for (let y = 0; y < size; y++) {
    const o = y * (size * 3 + 1); // filter byte 0 (None) at o
    for (let x = 0; x < size; x++) {
      let hit = 0;
      for (let sy = 0; sy < SS; sy++) for (let sx = 0; sx < SS; sx++) if (inFigure((x + (sx + 0.5) / SS) / size, (y + (sy + 0.5) / SS) / size)) hit++;
      const a = hit / (SS * SS);
      for (let c = 0; c < 3; c++) rows[o + 1 + x * 3 + c] = Math.round(bg[c] + (255 - bg[c]) * a);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 2; // 8-bit RGB; compression, filter, interlace = 0
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk("IHDR", ihdr), chunk("IDAT", deflateSync(rows, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const out = process.argv[2];
  if (!out) { console.error("usage: node scripts/gen-icons.mjs <out-dir>"); process.exit(2); }
  const catalog = JSON.parse(readFileSync(new URL("../shared/catalog.json", import.meta.url)));
  mkdirSync(out, { recursive: true });
  for (const s of catalog.accentSwatches) {
    if (!/^#[0-9a-fA-F]{6}$/.test(s.hex)) throw new Error(`swatch ${s.key}: bad hex ${s.hex}`);
    for (const n of SIZES) writeFileSync(`${out}/${s.key}-${n}.png`, iconPng(s.hex, n));
  }
}
