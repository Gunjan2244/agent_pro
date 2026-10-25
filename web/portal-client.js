// Customer portal client (DECISIONS #100): the only script on portal pages. Registers the service worker (scope = the portal
// only), tells it when the customer signs out, and wires the install button (home page) with the strings the server rendered.
import { wireInstall } from "./install.js";

const root = document.querySelector(".portal"), sw = navigator.serviceWorker;
const base = root && root.dataset.base;
if (base && sw) {
  sw.register(root.dataset.sw, { scope: base }).catch(() => {});
  // Signed-out login page (data-signed-out): wipe the SW's cached home. The logout submit below does the same when the SW controls the page.
  if ("signedOut" in root.dataset) sw.ready.then((r) => r.active && r.active.postMessage({ type: "signout" })).catch(() => {});
  document.addEventListener("submit", (e) => {
    try { if (new URL(e.target.action).pathname === base + "/logout" && sw.controller) sw.controller.postMessage({ type: "signout" }); } catch { /* ignore */ }
  });
}
const box = base && document.getElementById("install");
if (box) {
  const d = box.dataset;
  try { wireInstall({ t: (k) => (k === "site.install_ios" ? `${d.ios} ${d.note}` : d.menu) }); } catch { /* never breaks the page */ }
}
