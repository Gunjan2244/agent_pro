// Customer sign-up client (DECISIONS #108): the only script on the sign-up form. Autosaves what the customer types (debounced) so the
// agent can watch it live, tells the server which field is focused, sends a heartbeat while the page is visible, and shows the
// guardian checkbox as soon as the date of birth makes the person under 18. It never blocks Submit: the form posts normally.
import { isMinor, todayIST } from "./join-age.js";

const form = document.getElementById("join-form");
if (form) {
  const KEYS = ["name", "phone", "email", "dob", "address", "city", "nominee"];
  const statusEl = document.getElementById("join-status"), guardian = document.getElementById("guardian"), gbox = document.getElementById("f-guardian");
  const url = form.dataset.save, msg = form.dataset;
  const el = (k) => form.elements[k];
  const sent = {}; // the last values the server confirmed
  for (const k of KEYS) sent[k] = el(k) ? el(k).value : "";
  let active = "", timer = 0, inflight = false, dirty = false;
  const status = (text) => { if (statusEl) statusEl.textContent = text || ""; };

  const flush = async (heartbeat) => {
    clearTimeout(timer); timer = 0;
    if (inflight) { timer = setTimeout(() => flush(heartbeat), 300); return; }
    const body = new URLSearchParams(), changed = {};
    for (const k of KEYS) if (el(k) && el(k).value !== sent[k]) { changed[k] = el(k).value; body.set(k, changed[k]); }
    if (active) body.set("active", active);
    if (!heartbeat) status(msg.saving);
    inflight = true;
    try {
      const r = await fetch(url, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body, credentials: "same-origin" });
      const j = await r.json().catch(() => null);
      if (j && j.ok === true) { Object.assign(sent, changed); dirty = false; if (!heartbeat || Object.keys(changed).length) status(msg.saved); }
      else { dirty = true; status(msg.failed); }
    } catch { dirty = true; status(msg.failed); } // retried on the next change, focus or heartbeat
    finally { inflight = false; }
  };
  const later = () => { clearTimeout(timer); timer = setTimeout(() => flush(false), 600); };

  const checkMinor = () => {
    if (!guardian || !el("dob")) return;
    const minor = isMinor(el("dob").value, todayIST());
    guardian.hidden = !minor;
    if (!minor && gbox) gbox.checked = false;
  };
  form.addEventListener("input", (e) => { if (e.target && e.target.name === "dob") checkMinor(); later(); });
  form.addEventListener("focusin", (e) => { const n = e.target && e.target.name; active = KEYS.includes(n) ? n : ""; later(); });
  form.addEventListener("focusout", (e) => { if (!form.contains(e.relatedTarget)) { active = ""; later(); } });
  setInterval(() => { if (document.visibilityState === "visible") flush(!dirty); }, 20000);
  checkMinor();
}
