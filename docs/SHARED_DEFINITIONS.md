# Shared definitions (`shared/`)

Every enumerable or configurable thing in the product is defined **once**, in `shared/`. The mobile app (Kotlin Multiplatform), web renderer and database are generated from these files or read them directly.

**Never hard-code these values in Kotlin, JS or SQL.**

## Files
| File | Contains | Consumed by |
|---|---|---|
| `catalog.json` | Enums and lists: `enquiryStatuses`, `interests`, `enquirySources`, `premiumModes`, `policyStatuses`, `dueBuckets`, `planStatuses`, `domainStatuses`, `planSuggestions`, `accentSwatches`, `features`, `pricing` | Mobile app (generated `Catalog.kt` in `commonMain`), web (runtime JSON), SQL (generated CHECK constraints), `submit_enquiry` |
| `site-schema.json` | Website sections and their fields | App (`SiteSchema.kt`, which drives the section editor), web (section order and rendering), `publish_site` validation |
| `design-tokens.json` | Colours (light/dark), tones, chart palette, type, shapes, spacing, motion | App (`Tokens.kt`), web (generated `tokens.css`) |
| `templates/index.json` | Template registry: key, name/description i18n keys, supported sections, default accent, preview image | App (template picker), web (template loader) |
| `i18n/en.json`, `i18n/hi.json` | Labels for every catalog/schema key, plus fixed copy used by site templates | App (`strings_catalog.xml` per locale), web |

## Common entry shape (catalog lists)
```json
{ "key": "follow_up", "labelKey": "enquiry_status.follow_up", "icon": "event", "tone": "warning", "order": 3 }
```
- `key` is the stable machine key. It's what's stored in the database; never rename it, only add new ones.
- `labelKey` must exist in both `i18n/en.json` and `i18n/hi.json`.
- `icon` is a Material Symbols Rounded name.
- `tone` is one of `info|success|warning|danger|neutral` (see DESIGN_SYSTEM.md).
- `order` is the display order in chips, lists and charts.
- Some lists add extra fields: `terminal` (statuses), `months` (premium modes), `minDays`/`maxDays` (due buckets), `hex` (swatches), `default` (features).

## Catalog contents (v1)
### `enquiryStatuses`
| key | en | hi | icon | tone | terminal |
|---|---|---|---|---|---|
| `new` | New | नई | fiber_new | info | no |
| `contacted` | Contacted | संपर्क किया | call_made | neutral | no |
| `follow_up` | Follow-up | फॉलो-अप | event | warning | no |
| `converted` | Converted | ग्राहक बने | how_to_reg | success | yes |
| `closed` | Closed | बंद | block | neutral | yes |

### `interests`
`term` टर्म बीमा · `endowment` एंडोमेंट · `money_back` मनी बैक · `child` बाल योजना · `pension` पेंशन · `health` स्वास्थ्य · `ulip` यूलिप · `other` अन्य

### `enquirySources`
`website` वेबसाइट · `walk_in` सीधे आए · `referral` रेफ़रल · `phone` फ़ोन · `other` अन्य

### `premiumModes`
| key | en | hi | months |
|---|---|---|---|
| `yearly` | Yearly | वार्षिक | 12 |
| `half_yearly` | Half-yearly | अर्धवार्षिक | 6 |
| `quarterly` | Quarterly | त्रैमासिक | 3 |
| `monthly` | Monthly | मासिक | 1 |

### `policyStatuses`
`active` (success) सक्रिय · `lapsed` (danger) व्यपगत · `matured` (info) परिपक्व · `surrendered` (neutral) समर्पित

### `dueBuckets`
The day ranges are relative to today.

| key | en | minDays | maxDays |
|---|---|---|---|
| `overdue` | Overdue | −∞ | −1 |
| `this_week` | This week | 0 | 7 |
| `this_month` | This month | 8 | 30 |
| `next_3_months` | Next 3 months | 31 | 90 |

### `domainStatuses`
| key | en | hi | tone |
|---|---|---|---|
| `pending_dns` | Waiting for DNS | DNS की प्रतीक्षा | info |
| `verifying` | Setting up | सेट हो रहा है | info |
| `active` | Live | चालू | success |
| `renew_soon` | Renew soon | जल्द नवीनीकरण करें | warning |
| `expired` | Expired | समाप्त | danger |
| `failed` | Failed | विफल | danger |

`orders.status` and `orders.step` values are listed in BILLING.md and DOMAINS.md. They're internal, so they have no catalog labels; the app maps them to `ProgressStepper` steps.

### `planSuggestions`
These are free-text autocomplete hints. Agents can always type something else.
- Starting list: Jeevan Anand, Jeevan Labh, Jeevan Umang, Jeevan Lakshya, Tech Term, Bima Jyoti, Jeevan Shanti, Amritbaal, New Endowment Plan, Saral Pension.
- **The user still needs to check this list for accuracy** (tracked in STATUS.md).

### `accentSwatches`
These are for agent sites. Each must keep at least 4.5:1 contrast with white text; verify with the contrast check in `verifyShared`.

`indigo #1F4AA8` · `navy #16325C` · `teal #0F6E66` · `green #1E6B34` · `maroon #8C1D3F` · `plum #5B2A86` · `rust #9A4A00` · `slate #37474F`

### `features`
| key | default | Notes |
|---|---|---|
| `siteBuilder`, `enquiries`, `customers`, `policies`, `dues`, `insights`, `reminders`, `csvExport` | on | v1 |
| `servicePlan` | on | Trial, ₹499/₹299 plan, entitlement and read-only on lapse (BILLING.md). Turning it off gives everyone full access, which is useful in development |
| `payments` | on | Razorpay checkout, receipts (BILLING.md) |
| `customDomains` | on | Buying and connecting a domain at the registrar price plus a fee (DOMAINS.md) |

Defaults can be overridden remotely through `app_config.flags` (see BACKEND.md). For example, turning `customDomains` off server-side hides the domain flow without an app update.

### `pricing`
These are the server-authoritative prices. Codegen writes them into `app_config.pricing` through the `catalog_sync` migration, and Edge Functions read them from there. The app displays the prices returned by the server.
```json
"pricing": {
  "service": {
    "purchaseInr": 499, "renewalInr": 299, "taxInclusive": true,
    "trialDays": 14, "graceDays": 15, "renewWindowDays": 60,
    "reminderDays": [30, 7, 1], "graceReminderDays": [1, 7, 14]
  },
  "domain": {
    "source": "registrar_live", "serviceFeeInr": 99, "feeAppliesTo": ["purchase", "renewal"],
    "connectInr": 0, "requiresPaidPlan": true,
    "allowedTlds": [".in", ".co.in", ".com"], "quoteValidMinutes": 15,
    "renewWindowDays": 60, "reminderDays": [30, 7, 1]
  }
}
```
- **Service prices** are fixed (BILLING.md).
- **Domain prices** are never stored. They're fetched live from the registrar, then `serviceFeeInr` is added (DOMAINS.md).
- **To change any value,** edit it here, run `generateShared`, apply the migration and record it in CHANGELOG.md. No app release is needed.

### `planStatuses`
| key | en | hi | tone |
|---|---|---|---|
| `trial` | Free trial | निःशुल्क ट्रायल | info |
| `active` | Active | सक्रिय | success |
| `grace` | Renew now | अभी नवीनीकरण करें | warning |
| `lapsed` | Expired | समाप्त | danger |

## `site-schema.json`
```json
{
  "version": 1,
  "sections": [
    { "key": "hero", "labelKey": "section.hero", "icon": "badge", "pinned": true, "defaultVisible": true,
      "fields": [
        { "key": "headline",    "type": "text",      "labelKey": "field.headline", "required": true, "maxLength": 70 },
        { "key": "subheadline", "type": "multiline", "labelKey": "field.subheadline", "maxLength": 160 },
        { "key": "photo",       "type": "image",     "labelKey": "field.photo", "aspect": "1:1" },
        { "key": "ctaLabel",    "type": "text",      "labelKey": "field.cta_label", "maxLength": 30,
          "defaultKey": "site.cta_default" }
      ] },
    { "key": "testimonials", "labelKey": "section.testimonials", "icon": "format_quote", "defaultVisible": true,
      "fields": [
        { "key": "items", "type": "list", "maxItems": 6, "labelKey": "field.testimonials",
          "itemFields": [
            { "key": "name",  "type": "text", "required": true, "maxLength": 40, "labelKey": "field.name" },
            { "key": "city",  "type": "text", "maxLength": 30, "labelKey": "field.city" },
            { "key": "quote", "type": "multiline", "required": true, "maxLength": 280, "labelKey": "field.quote" } ] } ] }
  ]
}
```
**Sections in v1** (in this default order):
| Section | Fields |
|---|---|
| `hero` (pinned) | headline, subheadline, photo, ctaLabel |
| `about` | bio (≤ 600), designation, yearsExperience, clientsServed, languagesSpoken (multiSelect), showAgencyCode (toggle) |
| `services` | offered (multiSelect from `interests`), items list (interest, description ≤ 160) |
| `highlights` | items ≤ 4 (icon from a fixed set, title ≤ 40, text ≤ 120) |
| `achievements` | items ≤ 8 (title ≤ 60, year) |
| `testimonials` | items ≤ 6 (name, city, quote) |
| `faq` | items ≤ 8 (question ≤ 120, answer ≤ 400) |
| `contact` (pinned) | phone, whatsapp, email, officeAddress, mapLink (url), officeHours, showEnquiryForm (toggle, default on) |

**Field types:** `text`, `multiline`, `number`, `phone`, `email`, `url`, `image`, `select`, `multiSelect`, `toggle`, `swatch`, `date`, and `list` (with `itemFields`).

**Field rules:** `required`, `maxLength`, `min`, `max`, `maxItems`, `options` (or `optionsFrom: "catalog.interests"`), `default`/`defaultKey`, `aspect`.

**Stored content** (in `sites.draft_content` and `sites.published_content`):
```json
{ "sectionOrder": ["hero","about","services","testimonials","contact"], "hidden": ["faq"],
  "hero": { "headline": "…", "photo": "https://…/site-media/<agent>/hero.webp" },
  "testimonials": { "items": [ { "name": "…", "quote": "…" } ] } }
```
Content is independent of the template, so switching templates never loses data.

## `design-tokens.json`
The structure is below; the values are in DESIGN_SYSTEM.md.
```json
{ "color": { "light": { "background": "#FFFFFF", "primary": "#1F4AA8", "...": "..." }, "dark": { "...": "..." } },
  "tone":  { "info": {...}, "success": {...}, "warning": {...}, "danger": {...}, "neutral": {...} },
  "chart": { "series": { "light": ["#2a78d6","#eb6834"], "dark": ["#3987e5","#d95926"] },
             "ordinalRamp": { "light": ["#86b6ef","#5598e7","#2a78d6","#1c5cab"], "dark": [...] } },
  "font":  { "latin": "Inter", "devanagari": "Noto Sans Devanagari", "weights": [400, 500, 600], "webHeroWeight": 700 },
  "text":  { "base": 14, "ratio": 1.67,
             "s":  { "size": 14, "line": 20 }, "m":  { "size": 23, "line": 30 },
             "l":  { "size": 39, "line": 46 }, "xl": { "size": 65, "line": 72, "webOnly": true },
             "hiLineExtra": 2 },
  "space": { "s": 8, "m": 16, "l": 24 },
  "shape": { "sm": 8, "md": 12, "lg": 16 },
  "motion": { "short": 150, "medium": 250 } }
```

## i18n key convention
- Keys take the form `<group>.<key>`, for example `enquiry_status.follow_up`, `section.hero`, `field.headline` and `site.cta_default`.
- The generated resource name is `cat_` plus the key with dots turned into underscores, for example `cat_enquiry_status_follow_up`, accessed as `Res.string.cat_enquiry_status_follow_up`.
- Plain app UI copy that isn't tied to catalog or schema entries lives in `mobile/composeApp/src/commonMain/composeResources/values/strings.xml` and `values-hi/strings.xml`. These are shared by Android and iOS.
- **`verifyShared` checks that every key in `values/` also exists in `values-hi/`.** Compose resources have no Android lint `MissingTranslation` check, so this replaces it.
- **Icons:** each catalog `icon` must exist as a vector in `composeResources/drawable/ic_<name>.xml`. Material Symbols Rounded vectors are imported only for the icons actually used.

## Codegen: `./gradlew generateShared` (run from `mobile/`)
This task lives in `mobile/build-logic`. It runs automatically before Kotlin compilation of `composeApp` and generates:
1. **`Catalog.kt`**, into a generated `commonMain` source dir. It holds enums such as `EnquiryStatus`, `Interest`, `EnquirySource`, `PremiumMode`, `PolicyStatus`, `PlanStatus`, `DomainStatus`, `DueBucket` and `FeatureId`, plus `PlanSuggestions`, `AccentSwatch` and `Pricing`.
   - Each entry carries `key`, `label: StringResource` (Compose MP resources), `icon: DrawableResource`, `tone` and its extras.
2. **`Tokens.kt`, `SiteSchema.kt`** (a list of `SectionSpec` and `FieldSpec`) **and `AppConfig.kt`** (from `local.properties`).
3. **Compose Multiplatform resources:** `composeResources/values/strings_catalog.xml` and `values-hi/strings_catalog.xml`, in a generated resource dir.
4. **Web files** for the in-app preview: a copy of the `web/` source plus `shared/`, placed where each platform's `SitePreview` loads them (Android assets now, the iOS bundle later).
5. `supabase/migrations/<timestamp>_catalog_sync.sql`. This file holds:
   - the CHECK constraints for `enquiries.status`, `enquiries.interest`, `enquiries.source`, `policies.premium_mode`, `policies.status` and `site_domains.status`,
   - the interest whitelist used by `submit_enquiry`, and
   - an upsert of `app_config.flags` and `app_config.pricing`. It's written **only when the catalog hash changes**, and the hash is stored in a comment in the file.

`./gradlew verifyShared` runs as part of `check` and fails the build if:
- a `labelKey` is missing from `en` or `hi`,
- a tone or icon is unknown,
- a key is duplicated or was removed (removed keys must be marked `"deprecated": true` instead),
- the latest `catalog_sync` migration hash doesn't match the catalog, or
- a swatch's contrast is below 4.5,
- the `text` sizes aren't exactly `round(base × ratio^n)`, there are more than 4 sizes, or `space` doesn't have exactly 3 values, or
- Kotlin outside `core/designsystem` uses raw `.dp` spacing or raw `.sp` (see DESIGN_SYSTEM.md, Enforcement), or
- a string key or icon referenced by the catalog is missing from the Compose resources.

## Recipes
**Add an enquiry status, interest, source, policy status or premium mode**
1. Add the entry to the right list in `catalog.json` with a new `key`.
2. Add its `labelKey` to `i18n/en.json` and `i18n/hi.json`.
3. Run `./gradlew generateShared`. A new `catalog_sync` migration appears.
4. Apply the migration to Supabase.
5. Check whether any `when` branches need handling; the compiler flags exhaustive `when` expressions.
6. Update this doc's tables and CHANGELOG.md.

**Add a website section or field**
1. Edit `site-schema.json` and add its i18n keys.
2. Render it in the shared section partial `web/sections/<key>.js`, so every template picks it up.
3. The app's section editor needs no change, because `FormRenderer` renders it from the schema.
4. Update this doc and CHANGELOG.md.

**Add a template**
1. Add `web/templates/<key>/` containing `render.js`, `style.css` and `preview.webp`.
2. Register it in `templates/index.json` and add its i18n keys.
3. Update WEB_RENDERER.md.

**Add a feature flag or feature**
1. Add the flag to `catalog.features`.
2. Create `feature/<name>/<Name>Feature.kt` and add it to `FeatureRegistry.all`.
3. Update ARCHITECTURE.md and PRODUCT.md.
