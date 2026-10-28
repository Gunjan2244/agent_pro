// Scroll reveal (DESIGN_SYSTEM.md, Motion on sites): a section that starts below the fold fades in and rises
// --space-s once, as it enters the view. Progressive enhancement: the server-rendered page is complete and visible
// without this file; sections already in view are never hidden (no flash), only opacity and transform change (no
// layout shift), and nothing runs under prefers-reduced-motion: reduce. The CSS lives here, not in base.css, so the
// first-paint stylesheet (and a no-JS visitor) never carries a hidden state.
const CSS =
  "@media(prefers-reduced-motion:no-preference){" +
  ".rv{opacity:0;transform:translateY(var(--space-s));transition:opacity var(--motion-medium) ease-out,transform var(--motion-medium) ease-out}" +
  ".rv.rv-in{opacity:1;transform:none}" +
  // the cards of a grid marked .stg rise one after another (DECISIONS #117)
  ".rv .stg>*{opacity:0;transform:translateY(var(--space-m));transition:opacity 400ms ease-out,transform 400ms ease-out;transition-delay:calc(var(--i,0)*80ms)}" +
  ".rv.rv-in .stg>*{opacity:1;transform:none}}" +
  "@media print{.rv,.rv .stg>*{opacity:1;transform:none}}";

const styled = new WeakSet(); // the CSS goes in once per document (the in-app preview renders many times)

export function revealSections({ doc = document, win = window } = {}) {
  if (!win.IntersectionObserver || !win.matchMedia || win.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  const below = [...doc.querySelectorAll(".sec:not(.sec-hero)")].filter((el) => el.getBoundingClientRect().top > win.innerHeight);
  if (!below.length) return;
  if (!styled.has(doc)) {
    styled.add(doc);
    const style = doc.createElement("style");
    style.textContent = CSS;
    doc.head.appendChild(style);
  }
  const io = new win.IntersectionObserver(
    (entries) => {
      for (const e of entries) if (e.isIntersecting) { e.target.classList.add("rv-in"); io.unobserve(e.target); }
    },
    { rootMargin: "0px 0px -10% 0px" },
  );
  for (const el of below) {
    el.classList.add("rv");
    for (const g of el.querySelectorAll ? el.querySelectorAll(".stg") : []) [...g.children].forEach((c, i) => c.style.setProperty("--i", String(Math.min(i, 8))));
    io.observe(el);
  }
}
