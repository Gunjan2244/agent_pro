// Generates the Supabase Auth email templates from shared/design-tokens.json (docs/BACKEND.md, Auth emails).
// Usage: node scripts/gen-email-templates.mjs [outDir]   (default: supabase/email-templates)
// Each email shows English, then Hindi (Supabase takes one template per email type, not one per language).
// The {{ .Placeholders }} are Supabase's own and stay literal. Paste each file in Authentication → Emails.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const tk = JSON.parse(readFileSync(new URL("../shared/design-tokens.json", import.meta.url)));
const L = tk.color.light;
const D = tk.color.dark;
const { s, m, l } = tk.space;
const T = { s: tk.text.s, m: tk.text.m, l: tk.text.l };
const R = tk.shape;
const hiExtra = tk.text.hiLineExtra || 0;
const FONT = `${tk.font.latin}, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, '${tk.font.devanagari}', sans-serif`;

const tagline = {
  en: "Your website, enquiries and customers — in one place",
  hi: "आपकी वेबसाइट, पूछताछ और ग्राहक — एक ही जगह",
};

// Copy for emails lives here, next to the templates: it is server-side and has no app string resource.
const emails = {
  confirmation: {
    subject: "Confirm your Agent PRO email | ईमेल की पुष्टि करें",
    preheader: { en: "Confirm your email to finish creating your account.", hi: "अकाउंट बनाने के लिए अपने ईमेल की पुष्टि करें।" },
    en: {
      title: "Confirm your email",
      body: "Welcome to Agent PRO. Tap the button to confirm {{ .Email }} and finish creating your account.",
      button: "Confirm email",
      note: "Open this on the phone where Agent PRO is installed. The link works once.",
      ignore: "If you didn't create an account, you can ignore this email.",
    },
    hi: {
      title: "अपने ईमेल की पुष्टि करें",
      body: "Agent PRO में आपका स्वागत है। {{ .Email }} की पुष्टि करने और अकाउंट बनाना पूरा करने के लिए बटन दबाएँ।",
      button: "ईमेल की पुष्टि करें",
      note: "इसे उसी फ़ोन पर खोलें जिसमें Agent PRO इंस्टॉल है। लिंक एक ही बार काम करता है।",
      ignore: "अगर आपने अकाउंट नहीं बनाया है, तो इस ईमेल को अनदेखा करें।",
    },
  },
  recovery: {
    subject: "Reset your Agent PRO password | पासवर्ड रीसेट करें",
    preheader: { en: "Choose a new password for your account.", hi: "अपने अकाउंट के लिए नया पासवर्ड चुनें।" },
    en: {
      title: "Reset your password",
      body: "We got a request to reset the password for {{ .Email }}. Tap the button to choose a new one.",
      button: "Reset password",
      note: "Open this on the phone where Agent PRO is installed. The link expires after a short time.",
      ignore: "If you didn't ask for this, ignore this email. Your password stays the same.",
    },
    hi: {
      title: "अपना पासवर्ड रीसेट करें",
      body: "हमें {{ .Email }} का पासवर्ड रीसेट करने का अनुरोध मिला है। नया पासवर्ड चुनने के लिए बटन दबाएँ।",
      button: "पासवर्ड रीसेट करें",
      note: "इसे उसी फ़ोन पर खोलें जिसमें Agent PRO इंस्टॉल है। लिंक कुछ समय बाद खत्म हो जाता है।",
      ignore: "अगर आपने यह अनुरोध नहीं किया है, तो इस ईमेल को अनदेखा करें। आपका पासवर्ड वैसा ही रहेगा।",
    },
  },
  invite: {
    subject: "You're invited to Agent PRO | Agent PRO में आपका निमंत्रण",
    preheader: { en: "Accept the invite to set up your account.", hi: "अकाउंट बनाने के लिए निमंत्रण स्वीकार करें।" },
    en: {
      title: "You're invited",
      body: "You've been invited to join Agent PRO. Tap the button to accept and set up your account.",
      button: "Accept invite",
      note: "The link works once.",
      ignore: "If you weren't expecting this, you can ignore this email.",
    },
    hi: {
      title: "आपको निमंत्रण मिला है",
      body: "आपको Agent PRO से जुड़ने का निमंत्रण मिला है। स्वीकार करने और अकाउंट बनाने के लिए बटन दबाएँ।",
      button: "निमंत्रण स्वीकार करें",
      note: "लिंक एक ही बार काम करता है।",
      ignore: "अगर आप इसकी उम्मीद नहीं कर रहे थे, तो इस ईमेल को अनदेखा करें।",
    },
  },
  email_change: {
    subject: "Confirm your new email | नए ईमेल की पुष्टि करें",
    preheader: { en: "Confirm the change to your Agent PRO email.", hi: "अपने Agent PRO ईमेल में बदलाव की पुष्टि करें।" },
    en: {
      title: "Confirm your new email",
      body: "Tap the button to change the email of your Agent PRO account from {{ .Email }} to {{ .NewEmail }}.",
      button: "Confirm new email",
      note: "The link works once.",
      ignore: "If you didn't ask for this, ignore this email and your email stays the same.",
    },
    hi: {
      title: "अपने नए ईमेल की पुष्टि करें",
      body: "अपने Agent PRO अकाउंट का ईमेल {{ .Email }} से बदलकर {{ .NewEmail }} करने के लिए बटन दबाएँ।",
      button: "नए ईमेल की पुष्टि करें",
      note: "लिंक एक ही बार काम करता है।",
      ignore: "अगर आपने यह अनुरोध नहीं किया है, तो इस ईमेल को अनदेखा करें। आपका ईमेल वैसा ही रहेगा।",
    },
  },
  reauthentication: {
    subject: "Your Agent PRO code | आपका Agent PRO कोड",
    preheader: { en: "Your confirmation code.", hi: "आपका पुष्टि कोड।" },
    code: true,
    en: {
      title: "Your confirmation code",
      body: "Enter this code in Agent PRO to continue.",
      ignore: "If you didn't ask for this, ignore this email. Never share this code.",
    },
    hi: {
      title: "आपका पुष्टि कोड",
      body: "जारी रखने के लिए यह कोड Agent PRO में दर्ज करें।",
      ignore: "अगर आपने यह अनुरोध नहीं किया है, तो इस ईमेल को अनदेखा करें। यह कोड किसी को न बताएँ।",
    },
  },
};

const px = (n) => `${n}px`;
const spacer = (h) => `<tr><td height="${h}" style="height:${h}px;line-height:${h}px;font-size:1px">&nbsp;</td></tr>`;

function block(lang, c, hasButton, isCode) {
  const lh = (t) => t.line + (lang === "hi" ? hiExtra : 0);
  const out = [];
  out.push(`<tr><td lang="${lang}" class="ink" style="font-family:${FONT};font-size:${px(T.m.size)};line-height:${px(lh(T.m))};font-weight:600;color:${L.onSurface}">${c.title}</td></tr>`);
  out.push(spacer(s));
  out.push(`<tr><td lang="${lang}" class="sub" style="font-family:${FONT};font-size:${px(T.s.size)};line-height:${px(lh(T.s))};color:${L.onSurfaceVariant}">${c.body}</td></tr>`);
  if (isCode) {
    out.push(spacer(m));
    out.push(`<tr><td><table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td class="code" style="background:${L.primaryContainer};color:${L.onPrimaryContainer};border-radius:${px(R.md)};padding:${px(m)} ${px(l)};font-family:${FONT};font-size:${px(T.l.size)};line-height:${px(T.l.line)};font-weight:600;letter-spacing:8px">{{ .Token }}</td></tr></table></td></tr>`);
  }
  if (hasButton) {
    out.push(spacer(m));
    out.push(`<tr><td><table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr><td class="btn" style="background:${L.primary};border-radius:${px(R.md)}"><a href="{{ .ConfirmationURL }}" class="btn-a" lang="${lang}" style="display:inline-block;padding:${px(m)} ${px(l)};font-family:${FONT};font-size:${px(T.s.size)};line-height:${px(lh(T.s))};font-weight:600;color:${L.onPrimary};text-decoration:none">${c.button}</a></td></tr></table></td></tr>`);
  }
  if (c.note) {
    out.push(spacer(m));
    out.push(`<tr><td lang="${lang}" class="muted" style="font-family:${FONT};font-size:${px(T.s.size)};line-height:${px(lh(T.s))};color:${L.muted}">${c.note}</td></tr>`);
  }
  out.push(spacer(s));
  out.push(`<tr><td lang="${lang}" class="muted" style="font-family:${FONT};font-size:${px(T.s.size)};line-height:${px(lh(T.s))};color:${L.muted}">${c.ignore}</td></tr>`);
  return out.join("\n");
}

function render(e) {
  const hasButton = !e.code;
  const wordmark = `<table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
<td class="mark" width="40" height="40" align="center" valign="middle" style="width:40px;height:40px;background:${L.primary};border-radius:${px(R.md)};font-family:${FONT};font-size:${px(T.m.size)};line-height:40px;font-weight:700;color:${L.onPrimary}">A</td>
<td width="${s}" style="width:${s}px">&nbsp;</td>
<td class="ink" valign="middle" style="font-family:${FONT};font-size:${px(T.m.size)};line-height:40px;font-weight:600;color:${L.onSurface}">Agent PRO</td>
</tr></table>`;
  const fallback = hasButton
    ? `${spacer(m)}
<tr><td class="alt" style="background:${L.surfaceContainer};border-radius:${px(R.sm)};padding:${px(m)};font-family:${FONT};font-size:${px(T.s.size)};line-height:${px(T.s.line)};color:${L.onSurfaceVariant};word-break:break-all">Button not working? Copy this link into your browser:<br><a href="{{ .ConfirmationURL }}" class="lnk" style="color:${L.primary};text-decoration:underline">{{ .ConfirmationURL }}</a></td></tr>`
    : "";
  return `<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="supported-color-schemes" content="light dark">
<title>Agent PRO</title>
<style>
  @media (max-width:520px){ .wrap{padding:${px(m)} !important} .card{padding:${px(l)} ${px(m)} !important} }
  @media (prefers-color-scheme:dark){
    .page{background:${D.background} !important}
    .card{background:${D.surfaceContainerLow} !important;border-color:${D.outlineVariant} !important}
    .ink{color:${D.onSurface} !important}
    .sub{color:${D.onSurfaceVariant} !important}
    .muted{color:${D.muted} !important}
    .rule{border-color:${D.outlineVariant} !important}
    .mark,.btn{background:${D.primary} !important;color:${D.onPrimary} !important}
    .btn-a{color:${D.onPrimary} !important}
    .alt{background:${D.surfaceContainer} !important;color:${D.onSurfaceVariant} !important}
    .lnk{color:${D.primary} !important}
    .code{background:${D.primaryContainer} !important;color:${D.onPrimaryContainer} !important}
  }
</style>
</head>
<body class="page" style="margin:0;padding:0;background:${L.surfaceContainerLow};-webkit-text-size-adjust:100%">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent">${e.preheader.en} ${e.preheader.hi}</div>
<table role="presentation" class="page" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:${L.surfaceContainerLow}">
<tr><td align="center" class="wrap" style="padding:${px(l)} ${px(m)}">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px">
    <tr><td style="padding:0 0 ${px(m)} 0">${wordmark}</td></tr>
    <tr><td class="card" style="background:${L.surface};border:1px solid ${L.outlineVariant};border-radius:${px(R.lg)};padding:${px(l)}">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
${block("en", e.en, hasButton, e.code)}
${fallback}
        ${spacer(l)}
        <tr><td class="rule" style="border-top:1px solid ${L.outlineVariant};font-size:1px;line-height:1px">&nbsp;</td></tr>
        ${spacer(l)}
${block("hi", e.hi, hasButton, false)}
      </table>
    </td></tr>
    <tr><td class="muted" style="padding:${px(m)} ${px(s)} 0 ${px(s)};font-family:${FONT};font-size:${px(T.s.size)};line-height:${px(T.s.line)};color:${L.muted}">Agent PRO — ${tagline.en}<br><span lang="hi">${tagline.hi}</span></td></tr>
  </table>
</td></tr>
</table>
</body>
</html>
`;
}

const outDir = process.argv[2] || fileURLToPath(new URL("../supabase/email-templates", import.meta.url));
mkdirSync(outDir, { recursive: true });
const subjects = {};
for (const [name, e] of Object.entries(emails)) {
  const html = render(e);
  // Every template must keep Supabase's placeholders, and nothing unresolved may leak in.
  const used = [...html.matchAll(/\{\{ \.(\w+) \}\}/g)].map((x) => x[1]);
  const allowed = new Set(["ConfirmationURL", "Email", "NewEmail", "Token"]);
  if (used.some((u) => !allowed.has(u))) throw new Error(`${name}: unknown placeholder`);
  if (!e.code && !used.includes("ConfirmationURL")) throw new Error(`${name}: no ConfirmationURL`);
  if (e.code && !used.includes("Token")) throw new Error(`${name}: no Token`);
  if (/undefined|NaN/.test(html)) throw new Error(`${name}: unresolved value`);
  writeFileSync(`${outDir}/${name}.html`, html);
  subjects[name] = e.subject;
}
writeFileSync(`${outDir}/subjects.json`, JSON.stringify(subjects, null, 2) + "\n");
console.log(`wrote ${Object.keys(emails).length} templates to ${outDir}`);
