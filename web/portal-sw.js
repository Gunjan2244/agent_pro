// The customer portal's service worker (DECISIONS #100, docs/WEB_RENDERER.md). Worker-side: generates the script text with the
// constants baked in (cache name, scope, localised offline page); the browser runs the generated script, never this module.
// Site pages have NO service worker; this one is scoped to the portal base only. Tiny on purpose (the client + SW stay <= 8 KB).
import { SECURITY_HEADERS } from "./headers.js";
import { makeT } from "./i18n.js";
import { esc } from "./sections/util.js";

// The offline page: no agent data, same CSS as the portal (cached by the SW once seen).
export const offlinePage = (t, lang) => `<!doctype html><html lang="${esc(lang)}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>${esc(t("portal.app_short_name"))}</title><link rel="stylesheet" href="/tokens.css"><link rel="stylesheet" href="/styles/base.css"><link rel="stylesheet" href="/styles/portal.css"></head><body><div class="tpl portal"><main class="pt-main"><div class="state"><h1>${esc(t("portal.app_short_name"))}</h1><p>${esc(t("portal.offline_page"))}</p></div></main></div></body></html>`;

// Constants are JSON-embedded; "<" is escaped so the text is also safe inside any script context.
const lit = (v) => JSON.stringify(v).replace(/</g, "\\u003c").replace(/[\u2028\u2029]/g, (c) => "\\u" + c.charCodeAt(0).toString(16));

export function swSource({ base, version, lang, offline, banner }) {
  return `"use strict";
const BASE=${lit(base)},C=${lit("portal:" + base + ":" + version)},DEV=${lit(version === "dev")},LANG=${lit(lang)},OFF=${lit(offline)},BANNER=${lit(banner)},HOME=location.origin+BASE;
const STATIC=/^\\/(styles\\/|fonts\\/|icons\\/|tokens\\.css$)/,HTML={"content-type":"text/html; charset=utf-8","cache-control":"no-store"};
const inScope=(p)=>p===BASE||p.startsWith(BASE+"/");
const isHome=(p)=>p===BASE||p===BASE+"/";
const mine=(k)=>k.startsWith("portal:"+BASE+":");
const wipe=()=>caches.keys().then((ks)=>Promise.all(ks.filter(mine).map((k)=>caches.delete(k))));
const forget=()=>caches.open(C).then((c)=>c.delete(HOME));
self.addEventListener("install",()=>self.skipWaiting());
self.addEventListener("activate",(e)=>e.waitUntil(caches.keys().then((ks)=>Promise.all(ks.filter((k)=>mine(k)&&k!==C).map((k)=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener("message",(e)=>{if(e.data&&e.data.type==="signout")e.waitUntil(wipe())});
self.addEventListener("fetch",(e)=>{
const r=e.request;if(r.method!=="GET")return;
const u=new URL(r.url);if(u.origin!==location.origin)return;
if(STATIC.test(u.pathname)){if(!DEV)e.respondWith(asset(r));return}
if(r.mode==="navigate"&&inScope(u.pathname))e.respondWith(nav(r,u));
});
async function asset(r){
const c=await caches.open(C),hit=await c.match(r.url);if(hit)return hit;
const res=await fetch(r);if(res.ok&&!res.headers.has("set-cookie"))await c.put(r.url,res.clone());return res;
}
async function nav(r,u){
let res;
try{res=await fetch(r)}catch{return offline(u)}
const gone=res.type==="opaqueredirect"||res.status===404||res.status===503||(res.redirected&&new URL(res.url).pathname===BASE+"/login");
if(gone)await forget();
else if(isHome(u.pathname)&&!u.search&&res.status===200&&!res.headers.has("set-cookie")&&/^text\\/html/.test(res.headers.get("content-type")||"")){
const h=new Headers(res.headers);h.set("x-cached-at",res.headers.get("date")||new Date().toUTCString());
const c=await caches.open(C);await c.put(HOME,new Response(await res.clone().arrayBuffer(),{status:200,headers:h}));
}
return res;
}
async function offline(u){
const hit=isHome(u.pathname)?await (await caches.open(C)).match(HOME):null;
if(!hit)return new Response(OFF,{status:200,headers:HTML});
const at=new Date(hit.headers.get("x-cached-at")||hit.headers.get("date")||Date.now());
let when="";try{when=new Intl.DateTimeFormat(LANG==="hi"?"hi-IN":"en-IN",{dateStyle:"medium",timeStyle:"short",timeZone:"Asia/Kolkata"}).format(at)}catch{}
const bar='<p class="pt-flash" role="status">'+BANNER.replace("{time}",when)+"</p>";
const html=await hit.text(),i=html.indexOf('<main class="pt-main">');
return new Response(i<0?bar+html:html.slice(0,i+22)+"\\n"+bar+html.slice(i+22),{status:200,headers:HTML});
}
`;
}

export function portalServiceWorker({ request, url, target, links, dict, locale = "en", config = {} }) {
  const t = makeT(dict || {});
  const lang = /^[a-z]{2,3}$/.test(locale) ? locale : "en";
  const body = swSource({
    base: links.base, version: (config && config.BUILD_TIME) || "dev", lang,
    offline: offlinePage(t, lang), banner: esc(t("portal.offline_banner", { time: "{time}" })),
  });
  return new Response(request.method === "HEAD" ? null : body, {
    headers: { ...SECURITY_HEADERS, "content-type": "application/javascript; charset=utf-8", "cache-control": "no-cache", "service-worker-allowed": links.base, "x-robots-tag": "noindex, nofollow" },
  });
}
