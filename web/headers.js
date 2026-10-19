// Security headers for HTML responses made by the Worker (DECISIONS #75). `_headers` only applies to static-asset
// responses, so the Worker sets these itself. scripts/test-web.mjs asserts they equal the /* block of `_headers`
// (minus Cache-Control, which differs per path), so edit both together.
export const SECURITY_HEADERS = {
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": "DENY",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
  "Content-Security-Policy": "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data: https://*.supabase.co; connect-src 'self' https://*.supabase.co; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; object-src 'none'",
};
