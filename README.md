# Agent PRO

A mobile app for LIC agents, built with **Kotlin Multiplatform and Compose Multiplatform**. Android ships first, and iOS comes later from the same code. With it, an agent can:
- create a personal website from templates,
- receive enquiries through it,
- manage customers, policies and premium dues,
- track it all with charts, and
- add a custom domain to their site.

Pricing:
- **The service:** a 14-day free trial, then ₹499 for the first year and ₹299 per year to renew.
- **A custom domain:** extra, at the registrar's live price plus a small fee.

It's available in English and Hindi.

> **Status:** design is complete; implementation hasn't started. See [docs/STATUS.md](docs/STATUS.md).

## Repo layout
```
mobile/               Kotlin Multiplatform project: composeApp (shared code), androidApp, iosApp (later), build-logic
web/                  Static public-site renderer (templates), deployed to Cloudflare Workers (static assets)
shared/               Single source of truth: catalog, site schema, design tokens, i18n, templates registry
supabase/migrations/  Database schema, RLS, RPCs
scripts/              build-web.sh and helpers
docs/                 Project documentation (read STATUS.md first)
```

## Documentation
| Doc | What it covers |
|---|---|
| [STATUS.md](docs/STATUS.md) | **Start here.** Current state, roadmap checklist, next steps, blockers, environment |
| [PRODUCT.md](docs/PRODUCT.md) | Who it's for, features, scope, and what's out of scope |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | App structure, packages, data flow, offline cache, workers, libraries, config |
| [SHARED_DEFINITIONS.md](docs/SHARED_DEFINITIONS.md) | The `shared/` files, codegen, and recipes for adding a status, field or flag |
| [DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Theme "Clean Professional" and calm-screen rules: colours, type, shapes, spacing, motion, charts, components |
| [UX.md](docs/UX.md) | Navigation map, global UX rules, every screen, notifications |
| [BACKEND.md](docs/BACKEND.md) | Supabase tables, RLS, RPCs, storage, realtime |
| [WEB_RENDERER.md](docs/WEB_RENDERER.md) | Public sites: renderer, templates, hosting, SEO |
| [BILLING.md](docs/BILLING.md) | Service plan (trial, ₹499 / ₹299, grace, read-only), entitlement, payment pipeline, Razorpay, compliance |
| [DOMAINS.md](docs/DOMAINS.md) | Custom domains: live registrar price plus a fee, buy, renew and connect flows |
| [TESTING.md](docs/TESTING.md) | How to verify each layer |
| [DECISIONS.md](docs/DECISIONS.md) | Log of decisions and their reasons |
| [CHANGELOG.md](docs/CHANGELOG.md) | Dated log of changes |

Contributors, human or AI, follow the documentation protocol in [CLAUDE.md](CLAUDE.md): every change updates the relevant docs in the same change.

## Quick start (once implemented)
1. Copy `mobile/local.properties.example` to `mobile/local.properties`, then set `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL`.
2. Run `cd mobile && ./gradlew generateShared :androidApp:assembleDebug`.
3. For the web renderer, run `sh scripts/build-web.sh`, then serve `web/dist` (for example with `python3 -m http.server -d web/dist`).
