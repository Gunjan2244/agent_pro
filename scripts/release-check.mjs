// Prints the unfilled release placeholders (space separated), empty when the build may be released.
// Sources: releaseBlockers in shared/generated/privacy-notices.json and any {UPPER_CASE} token in shared/i18n/*.json.
import { readFileSync, readdirSync } from "node:fs";
const root = new URL("../shared/", import.meta.url);
const found = new Set();
try { for (const b of JSON.parse(readFileSync(new URL("generated/privacy-notices.json", root))).releaseBlockers || []) found.add(b); }
catch { found.add("{privacy-notices.json missing}"); }
for (const f of readdirSync(new URL("i18n/", root)).filter((f) => f.endsWith(".json")))
  for (const m of readFileSync(new URL("i18n/" + f, root), "utf8").matchAll(/\{[A-Z][A-Z0-9_]*\}/g)) found.add(m[0]);
console.log([...found].sort().join(" "));
