// The sites' moving parts (DECISIONS #117; DESIGN_SYSTEM.md "Motion on sites"). Progressive enhancement over a page
// that is complete without it: sliders get arrows, dots and autoplay; numbers count up once when they come into view;
// the top bar gets a shadow once the page scrolls and its phone menu closes after a tap; the guide's need index
// follows the group in view. Under prefers-reduced-motion: reduce nothing autoplays or counts (tickers stop in CSS).
// Loaded after render on both paths (app.js for the server-rendered page, client.js for the client render).

const still = (win) => !win.matchMedia || win.matchMedia("(prefers-reduced-motion: reduce)").matches;

export function wireMotion({ doc = document, win = window } = {}) {
  const calm = still(win);
  for (const el of doc.querySelectorAll(".slider")) wireSlider(el, { doc, win, calm });
  if (!calm && win.IntersectionObserver) countUp(doc, win);
  topBar(doc, win);
  if (win.IntersectionObserver) needIndex(doc, win);
}

// A slider is a scroll-snap row (.sl-track) of .slide items. Positions are the slides the row can scroll to (on a
// wide screen the last few share the end position), one dot each. Autoplay moves to the next position every
// data-auto ms while the slider is in view and the tab is visible; it pauses while the pointer or focus is inside,
// and stops for good once the visitor uses the arrows, the dots or swipes (WCAG 2.2.2).
export function wireSlider(el, { doc, win, calm }) {
  const track = el.querySelector(".sl-track"), nav = el.querySelector(".sl-nav");
  const slides = track ? [...track.children] : [];
  if (!nav || slides.length < 2 || el.dataset.wired) return;
  el.dataset.wired = "1";
  const dotsBox = nav.querySelector(".sl-dots");
  const label = (dotsBox && dotsBox.dataset.label) || "#"; // "Show item #": # becomes the number
  let stops = [], dots = [], cur = 0, hover = false, stopped = calm, seen = false;
  const left = (s) => s.offsetLeft - slides[0].offsetLeft;
  const measure = () => {
    const max = track.scrollWidth - track.clientWidth;
    stops = slides.map(left).filter((x) => x <= max + 2);
    if (stops.length < 2 && max > 2) stops = [0, max];
    nav.hidden = stops.length < 2;
    dotsBox.textContent = "";
    dots = stops.map((_, i) => {
      const b = doc.createElement("button");
      b.type = "button"; b.className = "sl-dot";
      b.setAttribute("aria-label", label.replace("#", String(i + 1)));
      b.addEventListener("click", () => { stopped = true; go(i); });
      dotsBox.appendChild(b);
      return b;
    });
    mark();
  };
  const mark = () => dots.forEach((d, i) => d.setAttribute("aria-current", i === cur ? "true" : "false"));
  const go = (i) => {
    if (!stops.length) return;
    cur = (i + stops.length) % stops.length;
    track.scrollTo({ left: stops[cur], behavior: calm ? "auto" : "smooth" });
    mark();
  };
  nav.querySelector(".sl-prev").addEventListener("click", () => { stopped = true; go(cur - 1); });
  nav.querySelector(".sl-next").addEventListener("click", () => { stopped = true; go(cur + 1); });
  let raf = 0;
  track.addEventListener("scroll", () => {
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(() => {
      const x = track.scrollLeft;
      let best = 0;
      stops.forEach((s, i) => { if (Math.abs(s - x) < Math.abs(stops[best] - x)) best = i; });
      if (best !== cur) { cur = best; mark(); }
    });
  }, { passive: true });
  for (const ev of ["pointerdown", "touchstart", "wheel"]) track.addEventListener(ev, () => { stopped = true; }, { passive: true });
  el.addEventListener("pointerenter", () => { hover = true; });
  el.addEventListener("pointerleave", () => { hover = false; });
  el.addEventListener("focusin", () => { hover = true; });
  el.addEventListener("focusout", () => { hover = false; });
  measure();
  let rt = 0;
  win.addEventListener("resize", () => { clearTimeout(rt); rt = setTimeout(measure, 150); });
  const every = Number(el.dataset.auto) || 0;
  if (!every || calm) return;
  if (win.IntersectionObserver) new win.IntersectionObserver((es) => { seen = es.some((e) => e.isIntersecting); }, { threshold: 0.4 }).observe(el);
  else seen = true;
  win.setInterval(() => { if (!stopped && !hover && seen && !doc.hidden) go(cur + 1); }, every);
}

// Plain integers inside .cu count from 0 to their value once, as they come into view (about 1.2 s, eased).
export function countUp(doc, win) {
  const els = [...doc.querySelectorAll(".cu")].filter((e) => /^\d{1,7}$/.test(e.textContent.trim()));
  if (!els.length) return;
  const run = (el) => {
    const end = Number(el.textContent.trim()), t0 = win.performance.now(), dur = 1200;
    const step = (now) => {
      const p = Math.min(1, (now - t0) / dur);
      el.textContent = String(Math.round(end * (1 - Math.pow(1 - p, 3))));
      if (p < 1) win.requestAnimationFrame(step);
    };
    win.requestAnimationFrame(step);
  };
  const io = new win.IntersectionObserver((es) => {
    for (const e of es) if (e.isIntersecting) { io.unobserve(e.target); run(e.target); }
  }, { threshold: 0.6 });
  for (const el of els) io.observe(el);
}

// The top bar: a shadow once the page has scrolled; the phone menu closes after a link is chosen.
function topBar(doc, win) {
  const bar = doc.querySelector(".topbar");
  if (!bar) return;
  const set = () => bar.classList.toggle("up", win.scrollY > 8);
  win.addEventListener("scroll", set, { passive: true });
  set();
  const menu = bar.querySelector(".tb-menu");
  if (menu) menu.addEventListener("click", (e) => { if (e.target.closest("a")) menu.open = false; });
}

// Guide: the index link of the need group in view is marked (aria-current), and scrolled into view in the strip.
function needIndex(doc, win) {
  const idx = doc.querySelector(".need-idx");
  if (!idx) return;
  const links = new Map([...idx.querySelectorAll("a[href^='#need-']")].map((a) => [a.getAttribute("href").slice(1), a]));
  const io = new win.IntersectionObserver((es) => {
    for (const e of es) {
      if (!e.isIntersecting) continue;
      for (const [id, a] of links) a.setAttribute("aria-current", id === e.target.id ? "true" : "false");
      const a = links.get(e.target.id);
      if (a && idx.scrollWidth > idx.clientWidth) idx.scrollTo({ left: a.offsetLeft - idx.offsetLeft - 16, behavior: still(win) ? "auto" : "smooth" });
    }
  }, { rootMargin: "-30% 0px -60% 0px" });
  for (const id of links.keys()) { const g = doc.getElementById(id); if (g) io.observe(g); }
}
