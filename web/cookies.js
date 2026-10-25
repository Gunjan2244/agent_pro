// The customer portal's session cookie (DECISIONS #98). Pure helpers, used by the Worker only.
// The token never reaches page JS: HttpOnly; SameSite=Lax so a link from WhatsApp still arrives signed in, with an
// Origin check on every POST in the Worker against cross-site form posts. Path-scoped to one site's portal.
export const COOKIE_NAME = "ap_portal";
const TOKEN = /^[A-Za-z0-9_-]{20,128}$/;

export function parseCookies(header) {
  const out = {};
  for (const part of String(header || "").split(";")) {
    const i = part.indexOf("=");
    if (i < 0) continue;
    const k = part.slice(0, i).trim();
    if (k && !(k in out)) out[k] = part.slice(i + 1).trim();
  }
  return out;
}

// The session token from a request's Cookie header, or null when absent or malformed.
export const portalToken = (header) => { const v = parseCookies(header)[COOKIE_NAME]; return v && TOKEN.test(v) ? v : null; };

export const portalCookie = (token, path, maxAgeSeconds) =>
  `${COOKIE_NAME}=${token}; Path=${path}; Max-Age=${Math.max(0, Math.floor(maxAgeSeconds))}; HttpOnly; Secure; SameSite=Lax`;

export const clearPortalCookie = (path) => `${COOKIE_NAME}=; Path=${path}; Max-Age=0; HttpOnly; Secure; SameSite=Lax`;

// The invite token's short-lived cookie (DECISIONS #101, H1): GET <base>/start?t=<token> moves the token out of the URL into
// this cookie (303 to the bare /start), so it never sits in the address bar, history, Referer or logs. Path-scoped to the start
// page so it is sent only to /start; 15 minutes; HttpOnly. The POST reads it and clears it.
export const INVITE_COOKIE = "ap_invite";
export const inviteToken = (header) => { const v = parseCookies(header)[INVITE_COOKIE]; return v && TOKEN.test(v) ? v : null; };
export const isInviteToken = (v) => TOKEN.test(String(v || ""));
export const inviteCookie = (token, path) => `${INVITE_COOKIE}=${token}; Path=${path}; Max-Age=900; HttpOnly; Secure; SameSite=Lax`;
export const clearInviteCookie = (path) => `${INVITE_COOKIE}=; Path=${path}; Max-Age=0; HttpOnly; Secure; SameSite=Lax`;

// The customer sign-up link's token (DECISIONS #108): GET <base>/join?t=<token> moves it out of the URL into this cookie
// (303 to the bare /join). Path-scoped to one site's sign-up page, 7 days (the link's life), HttpOnly.
export const JOIN_COOKIE = "ap_join";
export const joinToken = (header) => { const v = parseCookies(header)[JOIN_COOKIE]; return v && TOKEN.test(v) ? v : null; };
export const joinCookie = (token, path) => `${JOIN_COOKIE}=${token}; Path=${path}; Max-Age=604800; HttpOnly; Secure; SameSite=Lax`;
export const clearJoinCookie = (path) => `${JOIN_COOKIE}=; Path=${path}; Max-Age=0; HttpOnly; Secure; SameSite=Lax`;
