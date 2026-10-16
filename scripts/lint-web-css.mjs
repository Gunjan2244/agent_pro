// Fails on raw px/rem/em in margin/padding/gap (and raw sizes in font-size/font) in web/**/*.css. Run: node scripts/lint-web-css.mjs
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
const root = new URL("../web/", import.meta.url).pathname;
const skip = new Set(["dist", "node_modules", "dev-fixtures"]);
const files = [];
(function walk(d) { for (const n of readdirSync(d)) { if (skip.has(n)) continue; const p = join(d, n); statSync(p).isDirectory() ? walk(p) : n.endsWith(".css") && files.push(p); } })(root);
const SPACING = /^(margin|padding|gap|row-gap|column-gap)(-[a-z]+)*$/;
const FONT = /^(font-size|font)$/;
const RAW = /(?<![\w-])-?\d*\.?\d+(px|rem|em)\b/;
let bad = 0;
for (const f of files) {
  const css = readFileSync(f, "utf8").replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "));
  css.split("\n").forEach((line, i) => {
    for (const m of line.matchAll(/([a-z-]+)\s*:\s*([^;}{]+)/g)) {
      const [, prop, val] = m;
      if ((SPACING.test(prop) || FONT.test(prop)) && RAW.test(val)) { console.log(`${f.replace(root, "web/")}:${i + 1}: raw size in ${prop}: ${val.trim()}`); bad++; }
    }
  });
}
console.log(bad ? `FAIL: ${bad} violation(s)` : `OK: ${files.length} css files, no raw px/rem in margin/padding/gap/font-size`);
process.exit(bad ? 1 : 0);
