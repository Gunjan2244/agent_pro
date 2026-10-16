// Generates tokens.css from shared/design-tokens.json. Usage: node scripts/gen-tokens.mjs <out.css>
import { readFileSync, writeFileSync } from "node:fs";
const tk = JSON.parse(readFileSync(new URL("../shared/design-tokens.json", import.meta.url)));
const L = [":root{"];
for (const [k, v] of Object.entries(tk.color.light)) L.push(`--c-${k}:${v};`);
L.push(`--accent:${tk.color.light.primary};`);
for (const k of ["s", "m", "l", "xl"]) L.push(`--text-${k}:${tk.text[k].size}px;--line-${k}:${tk.text[k].line}px;`);
for (const k of ["s", "m", "l"]) L.push(`--space-${k}:${tk.space[k]}px;`);
for (const [k, v] of Object.entries(tk.shape)) L.push(`--shape-${k}:${v}px;`);
for (const [k, v] of Object.entries(tk.motion)) L.push(`--motion-${k}:${v}ms;`);
L.push("}");
const x = tk.text.hiLineExtra || 0;
L.push(`:lang(hi){${["s", "m", "l", "xl"].map((k) => `--line-${k}:${tk.text[k].line + x}px;`).join("")}}`);
writeFileSync(process.argv[2], L.join("\n") + "\n");
