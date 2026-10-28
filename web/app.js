// Entry point (a deferred module). Three cases:
//   1. #root[data-ssr]  the Worker already rendered the page: load hydrate.js, which only wires behaviour (DECISIONS #75).
//   2. ?preview=1       the in-app preview: window.renderSite(json) renders the unsaved draft in the browser.
//   3. otherwise        the static shell (backend error fallback, ?mock=1): client.js resolves, fetches and renders.
// client.js is loaded on demand so a server-rendered page never downloads the sections or the render path.
const root = document.getElementById("root");
const q = new URLSearchParams(location.search);

// Defined immediately, so the app can call it as soon as the page has loaded; the first call waits for client.js.
window.renderSite = async (input) => (await import("./client.js")).renderSite(input);

if (root && root.hasAttribute("data-ssr")) {
  import("./hydrate.js").then((m) => m.hydrate());
  import("./reveal.js").then((m) => m.revealSections()).catch(() => {}); // cosmetic: never breaks the page
  import("./motion.js").then((m) => m.wireMotion()).catch(() => {}); // sliders, count-up, top bar (DECISIONS #117)
  import("./callbar.js").then((m) => m.wireCallbar()).catch(() => {}); // sticky bar: past 70% or on scroll up (DECISIONS #123)
} else if (q.get("preview") !== "1") import("./client.js").then((m) => m.boot());
