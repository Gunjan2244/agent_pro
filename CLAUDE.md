# CLAUDE.md — Agent PRO

Agent PRO is a **Kotlin Multiplatform + Compose Multiplatform** app for LIC agents. Android ships first, and iOS comes later from the same `commonMain` code. It lets them build a website from templates, receive enquiries through it, manage customers, policies and premium dues, and see it all as charts. The backend is Supabase, and the public sites are served by one static renderer.

**The docs are the source of truth for the project's state. Read them before working, and update them with every change.**

## Start here, every session
1. Read [docs/STATUS.md](docs/STATUS.md) for where things stand, what's next, blockers and environment state.
2. Read the doc for the area you'll touch (see the map below).
3. If the code and the docs disagree, find out which one is wrong, fix it, and record the fix in the changelog.

## Documentation protocol (mandatory)
Every change is documented **in the same change** — not later, not "at the end".

1. **Update the topic doc** when a change alters behaviour, structure, schema, UI, a definition, configuration or the environment. Docs describe the **current truth**, so edit them in place rather than appending "update:" notes.
2. **Add a dated entry** (YYYY-MM-DD) to [docs/CHANGELOG.md](docs/CHANGELOG.md) for each meaningful change: what changed, why, and which files.
3. **Add a numbered entry** to [docs/DECISIONS.md](docs/DECISIONS.md) for any decision (a library, a pattern, a scope cut, a trade-off). Never delete one. If a decision is reversed, add a new entry that supersedes it.
4. **Update [docs/STATUS.md](docs/STATUS.md)** at the end of every task or session:
   - tick roadmap items,
   - rewrite "Current state" and "Next steps",
   - list open questions and blockers,
   - record environment changes (SDK installed, Supabase project created, deploys).

   Someone with no other context must be able to continue from STATUS.md alone.
5. **Write down new user requirements** in [docs/PRODUCT.md](docs/PRODUCT.md) before implementing them.
6. **Never leave a doc stale.** If you can't update it, add a `> TODO(doc):` line saying what's missing, and list it under Open questions in STATUS.md.

### Which doc to update
| If you change… | Update |
|---|---|
| Scope, features, requirements | `docs/PRODUCT.md` |
| Repo layout, packages, libraries, data flow, workers, config | `docs/ARCHITECTURE.md` |
| Anything in `shared/` (catalog, site schema, tokens, i18n, templates registry) or the codegen | `docs/SHARED_DEFINITIONS.md` |
| Colours, type, shapes, spacing, motion, chart styling, components | `docs/DESIGN_SYSTEM.md` |
| Screens, navigation, flows, copy rules, notifications UX | `docs/UX.md` |
| Tables, RLS, RPCs, storage, realtime, migrations | `docs/BACKEND.md` |
| Public site renderer, templates, hosting | `docs/WEB_RENDERER.md` |
| Service plan, trial, entitlement, payments, receipts | `docs/BILLING.md` |
| Custom domains | `docs/DOMAINS.md` |
| Companies, offers, offer slots, admin panel | `docs/OFFERS.md` |
| Personal data: notices, consent, retention, data-principal rights, breach handling | `docs/PRIVACY.md` |
| Site images: slots, rules, generation prompts | `docs/IMAGES.md` |
| Tests, verification steps | `docs/TESTING.md` |
| Progress, next steps, blockers, environment | `docs/STATUS.md` (always) |

## Engineering rules
- **Definitions live only in `shared/`.** Statuses, interests, premium modes, due buckets, plan suggestions, site sections and fields, templates, feature flags, design tokens and translated labels all live there. Never hard-code them in Kotlin, JS or SQL. Edit the JSON, run `./gradlew generateShared`, and apply any generated migration. See `docs/SHARED_DEFINITIONS.md`.
- **Only 3 spacing values** (`space.s` 8, `space.m` 16, `space.l` 24) and **only 4 type sizes** (14 / 23 / 39 / 65 sp, each 1.67× the previous; 65 is web-only). The fonts are Inter, with Noto Sans Devanagari for Hindi. Never add another spacing value, size or font. The build fails on raw `.dp` spacing or raw `.sp` outside `core/designsystem`.
- **Kotlin Multiplatform first.**
  - Put code in `composeApp/src/commonMain` unless it truly needs a platform API.
  - Platform APIs go behind an interface in `core/platform`, implemented in `androidMain`/`androidApp` (and `iosMain` later).
  - Only add a dependency if it supports KMP, or wrap it behind such an interface. See ARCHITECTURE.md, Platform seams.
- **No hard-coded colours, sizes or strings in UI code.** Use `AgentProTheme`, tokens and the components in `core/designsystem`.
  - Strings go in Compose Multiplatform resources: `composeResources/values` **and** `values-hi`.
  - Hindi is required, and `verifyShared` fails the build on a missing translation.
- **Features plug in through the `FeatureRegistry`** (`core/features`). The nav graph, bottom bar, Home cards, Settings and Website hub entries all come from it. A new feature is a new `feature/<name>/` package, one registry line and one flag in `catalog.json`.
- **Forms are built by `FormRenderer`** from `FieldSpec` lists. Don't hand-build form layouts.
- **Security:**
  - Every table has owner-only RLS, and `anon` never touches tables directly.
  - Public access goes only through the `SECURITY DEFINER` RPCs listed in `docs/BACKEND.md`.
  - Run Supabase advisors after each migration.
  - Never commit keys: they live in `mobile/local.properties` and `web/config.js` (template only).
- **Branding:** never bundle the LIC logo or any insurer's logo or trademark artwork in templates or app assets. Company logos may appear only as admin-uploaded content for companies that have given permission (DECISIONS #47).
- **Privacy:** every feature must comply with India's DPDP Act 2023 and Rules (DECISIONS #48, `docs/PRIVACY.md`): notice and consent, purpose limitation, data minimisation, data-principal rights, breach handling, and retention and erasure.
- **Outward-facing actions need the user's explicit approval each time.** These include creating or paying for cloud projects, deploying, pushing, and buying domains.
  - **Exception:** the web renderer's GitHub Actions pipeline (`.github/workflows/deploy-web.yml`) deploys automatically on every push to `main` that touches `web/`, `shared/` or its build scripts — no per-deploy approval. This is the one standing exception, decided by the user; see `docs/WEB_RENDERER.md` ("Preview deploy") and DECISIONS #118. It does not extend to any other deploy, domain purchase or cloud project action.
