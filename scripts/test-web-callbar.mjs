// Sticky Call / WhatsApp bar visibility (DECISIONS #123): the pure state rule, the wiring with a fake document and
// window, and the CSS (visible without the script's class, slide with transform, unfocusable when hidden).
// Run by scripts/test-web.mjs.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

export async function run({ ok }) {
  const { nextVisible, wireCallbar, PAST, STEP } = await import("../web/callbar.js");
  const max = 1000, n = (y, lastY, visible) => nextVisible({ y, lastY, max, visible });

  ok("callbar rule: hidden when the page opens", () => assert.equal(n(0, 0, false), false));
  ok("callbar rule: past 70% shows and stays shown", () => {
    assert.equal(PAST, 0.7);
    assert.equal(n(699, 699, false), false);
    assert.equal(n(700, 690, false), true);
    assert.equal(n(900, 700, true), true);
    assert.equal(n(1000, 900, false), true);
    assert.equal(n(800, 700, true), true, "scrolling down past 70% keeps it");
  });
  ok("callbar rule: scroll up shows it", () => {
    assert.equal(n(295, 300, false), false, "jitter (under the step)");
    assert.equal(n(300 - STEP, 300, false), true);
    assert.equal(n(100, 300, false), true);
  });
  ok("callbar rule: down after up hides it below 70%", () => {
    assert.equal(n(305, 300, true), true, "jitter keeps it");
    assert.equal(n(300 + STEP, 300, true), false);
    assert.equal(n(500, 300, true), false);
  });
  ok("callbar rule: scrolling up from past 70% stays shown, then below 70% down hides", () => {
    assert.equal(n(650, 800, false), true);
    assert.equal(n(600, 650, true), true, "still shown while moving up");
    assert.equal(n(655, 650, true), true, "jitter keeps it");
    assert.equal(n(520, 500, true), false, "a clear scroll down below 70% hides it");
  });
  ok("callbar rule: jitter ignored both ways and over-scroll is clamped", () => {
    assert.equal(n(405, 400, false), false);
    assert.equal(n(395, 400, false), false);
    assert.equal(n(405, 400, true), true);
    assert.equal(n(-50, 0, false), false, "rubber-band at the top");
    assert.equal(n(1080, 1000, false), true, "rubber-band at the bottom");
  });
  ok("callbar rule: a page too short to scroll shows the bar", () => {
    assert.equal(nextVisible({ y: 0, lastY: 0, max: 0, visible: false }), true);
    assert.equal(nextVisible({ y: 0, lastY: 0, max: -20, visible: false }), true);
  });

  const fake = ({ y = 0, height = 2000, inner = 1000, bar = true } = {}) => {
    const cls = new Set(), attrs = {}, on = {}, raf = [];
    const barEl = bar ? { classList: { add: (c) => cls.add(c), toggle: (c, f) => (f ? cls.add(c) : cls.delete(c)) }, setAttribute: (k, v) => (attrs[k] = v) } : null;
    const win = { scrollY: y, innerHeight: inner, addEventListener: (t, f, o) => { on[t] = { f, o }; }, requestAnimationFrame: (f) => raf.push(f) };
    const doc = { querySelector: (s) => (s === ".callbar" ? barEl : null), documentElement: { scrollHeight: height } };
    const flush = () => raf.splice(0).forEach((f) => f());
    const scroll = (to) => { win.scrollY = to; on.scroll.f(); flush(); };
    return { win, doc, cls, attrs, on, flush, scroll };
  };
  ok("wireCallbar: hides at the top, adds the enhancement class, labels it hidden", () => {
    const t = fake(); wireCallbar({ doc: t.doc, win: t.win });
    assert.ok(t.cls.has("cb-off") && t.attrs["aria-hidden"] === "true");
    assert.ok(!t.cls.has("cb-ready")); t.flush(); assert.ok(t.cls.has("cb-ready"), "transition enabled after the first state");
    assert.equal(t.on.scroll.o.passive, true); assert.equal(t.on.resize.o.passive, true);
  });
  ok("wireCallbar: the scroll sequence toggles it", () => {
    const t = fake(); wireCallbar({ doc: t.doc, win: t.win }); t.flush();
    const hidden = () => t.cls.has("cb-off");
    t.scroll(300); assert.ok(hidden(), "down at 30%");
    t.scroll(200); assert.ok(!hidden() && t.attrs["aria-hidden"] === "false", "up shows it");
    t.scroll(260); assert.ok(hidden(), "down again hides it");
    t.scroll(750); assert.ok(!hidden(), "past 70%");
    t.scroll(900); assert.ok(!hidden(), "stays past 70%");
    t.scroll(895); assert.ok(!hidden());
  });
  ok("wireCallbar: slow scrolling accumulates towards the step; scroll events are throttled to one frame", () => {
    const t = fake(); wireCallbar({ doc: t.doc, win: t.win }); t.flush();
    t.scroll(300); t.scroll(200);
    for (let y = 205; y <= 240; y += 2) { t.win.scrollY = y; t.on.scroll.f(); t.on.scroll.f(); }
    assert.equal(t.cls.has("cb-off"), false, "nothing ran before the frame");
    t.flush(); assert.equal(t.cls.has("cb-off"), true, "40 px down since the anchor hides it");
  });
  ok("wireCallbar: resize recomputes the page height; opening past 70% or on a short page shows it", () => {
    const t = fake({ y: 800 }); wireCallbar({ doc: t.doc, win: t.win }); assert.ok(!t.cls.has("cb-off"));
    const s = fake({ height: 900, inner: 900 }); wireCallbar({ doc: s.doc, win: s.win }); assert.ok(!s.cls.has("cb-off"));
    const r = fake({ y: 0 }); wireCallbar({ doc: r.doc, win: r.win }); r.flush();
    r.doc.documentElement.scrollHeight = 900; r.on.resize.f(); r.flush();
    assert.ok(!r.cls.has("cb-off"), "the page no longer scrolls");
  });
  ok("wireCallbar: no bar, no listeners", () => {
    const t = fake({ bar: false }); wireCallbar({ doc: t.doc, win: t.win });
    assert.deepEqual(Object.keys(t.on), []);
  });

  const css = readFileSync(new URL("../web/styles/base.css", import.meta.url), "utf8");
  ok("callbar CSS: visible without the script's classes, hidden only by .cb-off", () => {
    const base = css.match(/\.callbar\{[^}]*\}/)[0];
    assert.ok(!/visibility|transform|translate/.test(base), "the plain bar is always visible");
    assert.ok(/\.callbar\.cb-off\{transform:translateY\(100%\);visibility:hidden\}/.test(css));
    assert.ok(/\.callbar\.cb-ready\{transition:transform var\(--motion-medium\)/.test(css));
    assert.ok(/\.callbar\.cb-ready\.cb-off\{[^}]*visibility 0s linear var\(--motion-medium\)/.test(css), "unfocusable only after the slide");
    assert.ok(/\.tpl\.has-bar\{padding-bottom:/.test(css), "the space stays reserved");
    assert.ok(/prefers-reduced-motion:reduce\)\{\*\{transition:none!important/.test(css), "no slide under reduced motion");
  });
  ok("callbar.js is tiny and loaded on both paths and in the build", () => {
    assert.ok(readFileSync(new URL("../web/callbar.js", import.meta.url)).length < 2500);
    for (const f of ["app.js", "client.js"]) assert.ok(/import\("\.\/callbar\.js"\)\.then\(\(m\) => m\.wireCallbar\(\)\)/.test(readFileSync(new URL("../web/" + f, import.meta.url), "utf8")), f);
    assert.ok(/ callbar\.js /.test(readFileSync(new URL("./build-web.sh", import.meta.url), "utf8")));
  });
}
