// "Add to home screen" (docs/WEB_RENDERER.md, DECISIONS #65). The page button comes from sections/contact.js
// (only when contact.showInstallButton is on); ?install=1 (the app's Website tab) also opens a sheet,
// whatever the toggle says. Nothing is stored: no cookie, no localStorage.
import { esc } from "./sections/util.js";

export const isIos = (nav) => /iphone|ipad|ipod/i.test(nav.userAgent || "") || (nav.platform === "MacIntel" && nav.maxTouchPoints > 1);
export const isStandalone = (win) => !!(win.matchMedia && win.matchMedia("(display-mode: standalone)").matches) || win.navigator.standalone === true;
// The steps shown when the browser gives no install prompt (iOS never does; Chrome only after some engagement).
export const howToKey = (nav) => (isIos(nav) ? "site.install_ios" : "site.install_menu");

export function sheetHtml(t, name) {
  return `<div class="sheet" id="install-sheet" role="dialog" aria-labelledby="install-title">
<h2 id="install-title">${esc(t("site.install_title", { name }))}</h2>
<p class="prose">${esc(t("site.install_text"))}</p>
<p class="hint" id="install-sheet-how" hidden></p>
<div class="actions"><button class="btn" type="button" id="install-sheet-go" hidden>${esc(t("site.install_action"))}</button><button class="btn ghost" type="button" id="install-sheet-close">${esc(t("site.install_close"))}</button></div>
</div>`;
}

// Returns null inside the installed app; otherwise { install, refresh } (used by tests).
export function wireInstall({ t, name = "", forced = false, doc = document, win = window }) {
  if (isStandalone(win)) return null;
  const nav = win.navigator;
  const box = doc.getElementById("install");
  const how = box && box.querySelector("#install-how");
  const touch = isIos(nav) || !!(win.matchMedia && win.matchMedia("(pointer: coarse)").matches);
  let deferred = null, sheet = null;

  const refresh = () => {
    // Phones always get the button (it explains the steps when there's no prompt); desktops only with a prompt.
    if (box) box.hidden = !(deferred || touch);
    if (sheet) {
      sheet.querySelector("#install-sheet-go").hidden = !deferred;
      const h = sheet.querySelector("#install-sheet-how");
      h.textContent = t(howToKey(nav));
      h.hidden = !!deferred;
    }
  };
  const install = async () => {
    if (!deferred) return false;
    const e = deferred;
    deferred = null; // a prompt can be shown only once
    e.prompt();
    try { await e.userChoice; } catch { /* dismissed */ }
    refresh();
    return true;
  };

  // We own the promotion: no Chrome mini-infobar, also when the agent turned the button off.
  win.addEventListener("beforeinstallprompt", (e) => { e.preventDefault(); deferred = e; refresh(); });
  win.addEventListener("appinstalled", () => { deferred = null; if (box) box.hidden = true; if (sheet) sheet.hidden = true; });
  if (box) box.querySelector("#install-btn").addEventListener("click", async () => {
    if (await install()) return;
    how.textContent = t(howToKey(nav));
    how.hidden = false;
  });
  if (forced) {
    // Inside .tpl so the sheet gets the site's --accent.
    (doc.querySelector(".tpl") || doc.body).insertAdjacentHTML("beforeend", sheetHtml(t, name));
    sheet = doc.getElementById("install-sheet");
    sheet.querySelector("#install-sheet-go").addEventListener("click", install);
    sheet.querySelector("#install-sheet-close").addEventListener("click", () => { sheet.hidden = true; });
  }
  refresh();
  return { install, refresh };
}
