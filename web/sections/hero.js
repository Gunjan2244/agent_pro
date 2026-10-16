import { esc, url, has, textOr } from "./util.js";
export function hero(d, ctx) {
  const { profile: p } = ctx;
  const photo = url(d.photo) || url(p.photo_url);
  const sub = [p.designation, p.city].filter(has).map(esc).join(" · ");
  const cta = textOr(ctx, "hero", "ctaLabel", d.ctaLabel);
  const href = "#contact";
  return `<div class="hero-in">
${photo ? `<img class="hero-photo" src="${photo}" width="160" height="160" alt="${esc(p.full_name)}" fetchpriority="high" decoding="async">` : ""}
<div class="hero-text">
${has(p.full_name) ? `<p class="eyebrow">${esc(p.full_name)}</p>` : ""}
<h1>${esc(d.headline)}</h1>
${has(d.subheadline) ? `<p class="lead">${esc(d.subheadline)}</p>` : ""}
${sub ? `<p class="meta">${sub}</p>` : ""}
${cta ? `<a class="btn" href="${href}">${esc(cta)}</a>` : ""}
</div></div>`;
}
