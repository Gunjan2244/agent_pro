// Sticky Call / WhatsApp bar visibility on phones (DECISIONS #123). Shown past 70% of the page and on any scroll up;
// hidden again on a scroll down below 70%. Without this script the bar stays visible (the hidden state is a class added here).
export const PAST = 0.7, STEP = 10; // share of the page scrolled; px of movement before a direction counts

// Pure: the next visibility from the scroll position `y`, the anchor `lastY` (where the last counted move ended),
// `max` (scrollHeight - innerHeight) and the current `visible`.
export function nextVisible({ y, lastY, max, visible }) {
  if (!(max > 0)) return true; // a page that cannot scroll
  y = Math.min(Math.max(y, 0), max); // iOS rubber-band
  if (y / max >= PAST) return true;
  const d = y - lastY;
  return d <= -STEP ? true : d >= STEP ? false : !!visible;
}

export function wireCallbar({ doc = document, win = window } = {}) {
  const bar = doc.querySelector(".callbar");
  if (!bar) return;
  const el = doc.documentElement, cl = bar.classList;
  const pos = () => win.scrollY || win.pageYOffset || 0, span = () => el.scrollHeight - win.innerHeight;
  let max = span(), last = pos(), vis = false, busy = false;
  const apply = (v) => { vis = v; cl.toggle("cb-off", !v); bar.setAttribute("aria-hidden", v ? "false" : "true"); };
  apply(nextVisible({ y: last, lastY: last, max, visible: false }));
  win.requestAnimationFrame(() => cl.add("cb-ready")); // the slide starts after the first state, so nothing moves on load
  const tick = () => {
    busy = false;
    const y = pos();
    apply(nextVisible({ y, lastY: last, max, visible: vis }));
    if (Math.abs(y - last) >= STEP || y <= 0) last = y; // the anchor moves only when a direction counted
  };
  const queue = () => { if (!busy) { busy = true; win.requestAnimationFrame(tick); } };
  const resize = () => { max = span(); queue(); };
  // The in-app preview re-renders on every page change (DECISIONS #122): the previous render's listeners go first.
  if (typeof win.__cbOff === "function") win.__cbOff();
  win.__cbOff = () => { win.removeEventListener("scroll", queue); win.removeEventListener("resize", resize); };
  win.addEventListener("scroll", queue, { passive: true });
  win.addEventListener("resize", resize, { passive: true });
}
