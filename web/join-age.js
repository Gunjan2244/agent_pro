// Under-18 check for the customer sign-up form (DECISIONS #108). Shared by join.js (server) and join-client.js (browser); pure.
// Dates are YYYY-MM-DD strings; "today" is India's date (Asia/Kolkata, UTC+5:30, no daylight saving).
export const todayIST = (now = Date.now()) => new Date(now + 19800000).toISOString().slice(0, 10);
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
// true when dob is a well-formed date that is after the day 18 years ago (a future date counts: the server rejects it anyway).
export function isMinor(dob, today = todayIST()) {
  const m = DATE.exec(String(dob || ""));
  if (!m || !DATE.test(today)) return false;
  const cutoff = `${String(+today.slice(0, 4) - 18).padStart(4, "0")}${today.slice(4)}`;
  return String(dob) > cutoff;
}
