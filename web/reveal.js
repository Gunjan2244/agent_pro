// Scroll reveal (DESIGN_SYSTEM.md, Motion on sites): a section that starts below the fold fades in and rises
// --space-s once, as it enters the view. Progressive enhancement: the server-rendered page is complete and visible
// without this file; sections already in view are never hidden (no flash), only opacity and transform change (no
// layout shift), and nothing runs under prefers-reduced-motion: reduce. The CSS lives here, not in base.css, so the
// first-paint stylesheet (and a no-JS visitor) never carries a hidden state.
const CSS =
  "@media(prefers-reduced-motion:no-preference){" +
  ".rv{opacity:0;transform:translateY(var(--space-s));transition:opacity var(--motion-medium) ease-out,transform var(--motion-medium) ease-out}" +
  ".rv.rv-in{opacity:1;transform:none}}" +
  "@media print{.rv{opacity:1;transform:none}}";

export function revealSections({ doc = document, win = window } = {}) {
  if (!win.IntersectionObserver || !win.matchMedia || win.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  const below = [...doc.querySelectorAll(".sec:not(.sec-hero)")].filter((el) => el.getBoundingClientRect().top > win.innerHeight);
  if (!below.length) return;
  const style = doc.createElement("style");
  style.textContent = CSS;
  doc.head.appendChild(style);
  const io = new win.IntersectionObserver(
    (entries) => {
      for (const e of entries) if (e.isIntersecting) { e.target.classList.add("rv-in"); io.unobserve(e.target); }
    },
    { rootMargin: "0px 0px -10% 0px" },
  );
  for (const el of below) { el.classList.add("rv"); io.observe(el); }
}
