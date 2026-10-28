# Shared definitions (`shared/`)

Every enumerable or configurable thing in the product is defined **once**, in `shared/`. The mobile app (Kotlin Multiplatform), web renderer and database are generated from these files or read them directly.

**Never hard-code these values in Kotlin, JS or SQL.**

## Files
| File | Contains | Consumed by |
|---|---|---|
| `catalog.json` | Enums and lists: `enquiryStatuses`, `interests`, `enquirySources`, `premiumModes`, `policyStatuses`, `dueBuckets`, `planStatuses`, `domainStatuses`, `planSuggestions`, `reservedSlugs`, `accentSwatches`, `locales`, `features`, `privacyNotices`, `dataRequestKinds`, `dataRequestStatuses`, `offerStatuses`, `offerPhases`, `offerCtaKinds`, `adminRoles`, `membershipStatuses`, `membershipRejectReasons`, `insuranceTypes`, `templateFamilies`, `publicTextRules`, `pricing`, `retention`, `offers` | Mobile app (generated `Catalog.kt` and `PrivacyNotices.kt` in `commonMain`), web (runtime JSON), SQL (generated CHECK constraints and catalog functions), `submit_enquiry`, `publish_site`, the slug guard, the consent RPCs |
| `site-schema.json` | Website sections and their fields | App (`SiteSchema.kt`, which drives the section editor), web (section order and rendering), `publish_site` validation |
| `design-tokens.json` | Colours (light/dark), tones, chart palette, type, shapes, spacing, motion | App (`Tokens.kt`), web (generated `tokens.css`) |
| `offer-schema.json` | The fields of a **company offer** (OFFERS.md): `localized` (stored per catalog locale) and `fields`. Optional to the codegen: everything about it is generated only when the file exists | The admin panel's offer form (8c), SQL (`offer_schema()` for the offers guard trigger, 8b-2), the web renderer's field limits (8b-3). **No Kotlin is generated** (DECISIONS #73) |
| `templates/index.json` | Template registry: key, family, name/description i18n keys, sections in their default order, default accent, preview image | App (template picker), web (template loader), SQL (the `sites.template_key` CHECK, `catalog_template_family()`) |
| `site-seed.json` | The **starter content** of a new site (DECISIONS #78): per section and field, i18n text variants, profile values, fixed values and list items. Optional to the codegen. See [site-seed.json](#site-seedjson) | App (generated `SiteSeed.kt`, used by `StarterContent`); not in SQL or the catalog hash |
| `i18n/en.json`, `i18n/hi.json` | Labels for every catalog/schema key, fixed copy used by site templates, and the privacy notice texts (`privacy.*`) | App (`strings_catalog.xml` per locale), web |
| `generated/privacy-notices.json` | **Generated, never hand-edited** (the only generated file in `shared/`): each active notice's version, text keys and per-locale hash, plus the release blockers. See [Privacy notices](#privacynotices) | Web (privacy page, enquiry consent); the SitePreview copy |

## Common entry shape (catalog lists)
```json
{ "key": "follow_up", "labelKey": "enquiry_status.follow_up", "icon": "event", "tone": "warning", "order": 3 }
```
- `key` is the stable machine key. It's what's stored in the database; never rename it, only add new ones.
- `labelKey` must exist in both `i18n/en.json` and `i18n/hi.json`.
- `icon` is a Material Symbols Rounded name.
- `tone` is one of `info|success|warning|danger|neutral` (see DESIGN_SYSTEM.md).
- `order` is the display order in chips, lists and charts.
- Some lists add extra fields: `terminal` (enquiry and data request statuses), `months` (premium modes), `minDays`/`maxDays` (due buckets; `minDays` is `null` for −∞), `hex` (swatches), `default` (features), `version` and `textKeys` (privacy notices), `agentAllowed` (offer CTA kinds), `descriptionKey` (template families; an i18n key, generated as `description: StringResource`), `image` and `insuranceType` (interests; see below).
- `catalog.json` also has a top-level `"version": 1`. `planSuggestions` and `reservedSlugs` are plain arrays of strings, and `pricing`, `retention` and `portal` are plain objects.
- Lists that have no status meaning (`interests`, `enquirySources`, `premiumModes`, `dataRequestKinds`) use tone `neutral`. `accentSwatches`, `locales` and `privacyNotices` have no `icon` or `tone`.

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
Tone is `neutral` for all. The icons are provisional. Since DECISIONS #93 the list covers every insurance type, and each entry names the **`insuranceType`** it belongs to (a `catalog.insuranceTypes` key, or `null` for `other`, which is available whatever the agent deals in). The five new interests (order 8–12) have **provisional labels** (EN + HI, for the user to review) and reuse the icons of their insurance types; `other` moved to order 13 so it stays last.

| key | en | hi | icon | insuranceType |
|---|---|---|---|---|
| `term` | Term insurance | टर्म बीमा | shield (provisional) | life |
| `endowment` | Endowment | एंडोमेंट | savings (provisional) | life |
| `money_back` | Money back | मनी बैक | payments (provisional) | life |
| `child` | Child plan | बाल योजना | child_care (provisional) | life |
| `pension` | Pension | पेंशन | elderly (provisional) | life |
| `health` | Health | स्वास्थ्य | health_and_safety (provisional) | health |
| `ulip` | ULIP | यूलिप | trending_up (provisional) | life |
| `motor` | Motor insurance | मोटर बीमा | shield (provisional) | motor |
| `home` | Home insurance | गृह बीमा | home (provisional) | home |
| `travel` | Travel insurance | यात्रा बीमा | public (provisional) | travel |
| `business` | Business insurance | व्यापार बीमा | work (provisional) | business |
| `accident` | Personal accident | व्यक्तिगत दुर्घटना बीमा | verified_user (provisional) | accident |
| `portal` | Site login | साइट लॉगिन | badge (provisional) |
| `other` | Other | अन्य | more_horiz (provisional) | `null` |

`Interest.insuranceType` is generated as `String?`. `verifyShared` requires the field on every interest once any interest has it or `site-seed.json` has an `insuranceTypes` block, and each value must be an `insuranceTypes` key or `null`.

Each interest also has an **`image`**: the base name (`interest-<key>` with `_` → `-`, e.g. `interest-money-back`) of the site illustration the web ships as `web/images/<name>-{400,800}.webp` (WP3; the files are made later). The five new interests already name theirs (`interest-motor`, `interest-home`, `interest-travel`, `interest-business`, `interest-accident`); until those files are generated (prompts in IMAGES.md) the renderer shows the generic `interest-other` illustration in their place (WEB_RENDERER.md, Bundled images), so no catalog change is needed when they arrive. `verifyShared` checks the name matches `^[a-z0-9-]+$` on any catalog entry that has `image`, but not that the files exist. `Interest.image` is generated as a `String`. The alt text is the one key `site.image_alt_interest` ("{interest} illustration" / "{interest} का चित्र"), with `{interest}` = the interest's label.

### `enquirySources`
Tone is `neutral` for all. The icons are provisional.

| key | en | hi | icon |
|---|---|---|---|
| `website` | Website | वेबसाइट | language (provisional) |
| `walk_in` | Walk-in | सीधे आए | directions_walk (provisional) |
| `referral` | Referral | रेफ़रल | group_add (provisional) |
| `phone` | Phone | फ़ोन | call (provisional) |
| `other` | Other | अन्य | more_horiz (provisional) |

### `premiumModes`
Tone is `neutral` for all. The icons are provisional.

| key | en | hi | months | icon |
|---|---|---|---|---|
| `yearly` | Yearly | वार्षिक | 12 | calendar_month (provisional) |
| `half_yearly` | Half-yearly | अर्धवार्षिक | 6 | date_range (provisional) |
| `quarterly` | Quarterly | त्रैमासिक | 3 | event_repeat (provisional) |
| `monthly` | Monthly | मासिक | 1 | today (provisional) |

### `policyStatuses`
| key | en | hi | tone | icon |
|---|---|---|---|---|
| `active` | Active | सक्रिय | success | check_circle (provisional) |
| `lapsed` | Lapsed | व्यपगत | danger | event_busy (provisional) |
| `matured` | Matured | परिपक्व | info | verified (provisional) |
| `surrendered` | Surrendered | समर्पित | neutral | assignment_return (provisional) |

### `dueBuckets`
The day ranges are relative to today.

Tones follow the urgency colours in DESIGN_SYSTEM.md. Hindi labels and icons are provisional. In JSON, −∞ is `"minDays": null`.

| key | en | hi (provisional) | minDays | maxDays | tone | icon (provisional) |
|---|---|---|---|---|---|---|
| `overdue` | Overdue | अतिदेय | −∞ (`null`) | −1 | danger | error |
| `this_week` | This week | इस सप्ताह | 0 | 7 | warning | today |
| `this_month` | This month | इस महीने | 8 | 30 | info | calendar_month |
| `next_3_months` | Next 3 months | अगले 3 महीने | 31 | 90 | neutral | date_range |

### `domainStatuses`
| key | en | hi | tone | icon (provisional) |
|---|---|---|---|---|
| `pending_dns` | Waiting for DNS | DNS की प्रतीक्षा | info | dns |
| `verifying` | Setting up | सेट हो रहा है | info | sync |
| `active` | Live | चालू | success | check_circle |
| `renew_soon` | Renew soon | जल्द नवीनीकरण करें | warning | autorenew |
| `expired` | Expired | समाप्त | danger | event_busy |
| `failed` | Failed | विफल | danger | error |

`orders.status` and `orders.step` values are listed in BILLING.md and DOMAINS.md. They're internal, so they have no catalog labels; the app maps them to `ProgressStepper` steps.

### `planSuggestions`
These are free-text autocomplete hints. Agents can always type something else.
- Starting list: Jeevan Anand, Jeevan Labh, Jeevan Umang, Jeevan Lakshya, Tech Term, Bima Jyoti, Jeevan Shanti, Amritbaal, New Endowment Plan, Saral Pension.
- **The user still needs to check this list for accuracy** (tracked in STATUS.md).

### `accentSwatches`
These are for agent sites. Each must keep at least 4.5:1 contrast with white text; verify with the contrast check in `verifyShared`.

`indigo #1F4AA8` · `navy #16325C` · `teal #0F6E66` · `green #1E6B34` · `maroon #8C1D3F` · `plum #5B2A86` · `rust #9A4A00` · `slate #37474F`

Each entry is `{ key, labelKey: "swatch.<key>", hex, order }`. The Hindi names are transliterations (इंडिगो, नेवी, टील, हरा, मरून, बैंगनी, रस्ट, स्लेट) and are provisional. Contrast against white is 6.10 to 12.77.

### `features`
Each entry has `key`, `labelKey` (`feature.<key>`), `icon`, `order` and `default`. The labels and icons are provisional.

| key | default | Notes |
|---|---|---|
| `siteBuilder` (web), `enquiries` (inbox), `customers` (group), `policies` (description), `dues` (event_upcoming), `insights` (bar_chart), `reminders` (notifications), `csvExport` (download) | on | v1 |
| `servicePlan` (workspace_premium) | on | Trial, ₹499/₹299 plan, entitlement and read-only on lapse (BILLING.md). Turning it off gives everyone full access, which is useful in development |
| `payments` (payments) | on | Razorpay checkout, receipts (BILLING.md) |
| `customDomains` (public) | on | Buying and connecting a domain at the registrar price plus a fee (DOMAINS.md) |
| `offers` (savings, provisional) | on | Company offers and agents' custom offers on the site (OFFERS.md). `flag_overrides` can switch it off |
| `customerPortal` (person, order 13) | on | The customer login on agents' websites, "My policies" (DECISIONS #96–#100; BACKEND.md "Customer portal"). Off: `portal_invite` returns `unavailable`, every anon portal RPC returns `unavailable`, the payload's `portal` is false and the app card says so |
| `customerSignup` (person_add, order 14) | on | The customer sign-up link: the agent sends a link, the customer fills in their details on the agent's site while the agent watches live, and Submit adds the customer (DECISIONS #108; BACKEND.md "Customer sign-up link"). Off: `customer_signup_link` returns `unavailable`, every anon sign-up RPC returns `unavailable` and the app hides "Send the customer a link". The agent's own unfinished forms (Incomplete forms) don't depend on it |

These are the **defaults**. `catalog_sync` writes them to `app_config.flags` and rewrites them on every catalog change. To override one on the server, set it in the separate `app_config.flag_overrides` row, which migrations never touch. The effective value is the default merged with the override (an override wins), returned by `effective_flags()` and used the same way by the app, the SQL and the Edge Functions (DECISIONS #35, BACKEND.md "Feature flags"). For example, `{"customDomains": false}` in `flag_overrides` hides the domain flow without an app update.

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

### `reservedSlugs` (provisional)
Slugs no agent can claim for their site, because they clash with routes, system words or the brand. A plain array of lowercase strings (`a-z`, `0-9`, `-`). `catalog_sync` emits them as `public.catalog_reserved_slugs()`, which the `sites.slug` trigger and `is_slug_available` use (the check ignores case). `Catalog.kt` has them as `ReservedSlugs.all` for the app's slug validator.

The starting list, moved here from the SQL: `a`, `s`, `www`, `api`, `app`, `apps`, `admin`, `administrator`, `root`, `auth`, `login`, `logout`, `signup`, `signin`, `register`, `account`, `accounts`, `settings`, `dashboard`, `billing`, `pay`, `payment`, `payments`, `checkout`, `help`, `support`, `contact`, `about`, `terms`, `privacy`, `policy`, `legal`, `blog`, `news`, `mail`, `email`, `static`, `assets`, `cdn`, `media`, `img`, `images`, `files`, `download`, `downloads`, `docs`, `status`, `test`, `demo`, `example`, `null`, `undefined`, `agentpro`, `agent-pro`, `lic`, `licindia`, `official`, `team`, `staff`. **The whole list is provisional** and needs the user's review. Removing a slug makes it claimable; adding one doesn't change sites that already use it, but they can't save that slug again.

### `locales`
The languages the app and the agent sites support. Each entry is `{ key, labelKey: "locale.<key>", order }`, with no icon or tone. `catalog_sync` turns the keys into the CHECK constraints on `sites.locale` and `profiles.preferred_language` (both nullable).

| key | en | hi |
|---|---|---|
| `en` | English | English |
| `hi` | हिन्दी | हिन्दी |

Each language is labelled in its own script in both files, so someone who can't read the current UI language can still find theirs (provisional).

### `templateFamilies` (DECISIONS #76)

Since DECISIONS #115 (2026-10-26) the app shows **no** family to the agent; families are only an internal grouping (the seed's `visibleOnlyInFamily`, the Appearance offers switch, web styling, SQL). The family labels, descriptions and `shows`/`bestFor` strings are no longer displayed.
The two design families of `templates/index.json`. Each entry is `{ key, labelKey, descriptionKey, showsKey, bestForKey, icon, order }`, with no tone (icons reused from imported ones). `showsKey` (what the site shows first) and `bestForKey` (who the style suits) explain the family on onboarding step 2, Choose a style (DECISIONS #113; `template_family.<key>.shows` / `.best_for`).

| key | en | hi | icon | Templates |
|---|---|---|---|---|
| `personal` | Meet me — "A personal page about you, your story and how you help" | मुझसे मिलिए | person | `classic`, `modern`, `minimal` |
| `offers` | Best offers — "Your company's best current offers up front, to win new clients" | बेहतरीन ऑफ़र | savings | `spotlight`, `guide` |

Generated as the enum `TemplateFamily` (with `description`, `shows` and `bestFor: StringResource`); `catalog_sync` emits `catalog_template_families()` and `catalog_template_family(text)`. Every template names a family and every active family has a template (`verifyShared`). The descriptions and Hindi are provisional.

### `publicTextRules` (DECISIONS #77; **the list is provisional**)
An object: `{ "blockedTerms": [ ... ] }`. Terms public offer text may not contain: insurer **product** names and insurer **company** names, in Latin and Devanagari (149 at present, e.g. `jeevan anand`, `tech term`, `saral jeevan bima`, `lic`, `life insurance corporation`, `hdfc life`, `star health`, `एलआईसी`, `भारतीय जीवन बीमा निगम`, `जीवन आनंद`, `अमृतबाल`). It covers the products in `planSuggestions` plus other well-known Indian life and health products and insurers. **The user and counsel still need to review it.**
- Every term is lowercase, trimmed, single-spaced, NFC-normalized and unique (`verifyShared`).
- **Never a single everyday word**: `jeevan`, `bima`, `जीवन`, `बीमा`, `life`, `health`, `insurance`, `plan`, `policy`, `term`, `care`, `max`, `star`, `digit`, `new`, `plus`, `smart`, `saral`, `सरल`, `dhan`, `धन` are refused on their own (`PublicText.GENERIC_SINGLE_WORDS`). Phrases that are also everyday Hindi (`धन संचय`, `धन वृद्धि`) are left out in Devanagari on purpose.
- **Matching rule** (the reference is `PublicText` in build-logic; the server check and the web tests must behave the same): NFC-normalize and lower-case both; the term's words may be separated by any whitespace; the character before and after the match must not be a letter, combining mark or digit (`\p{L}\p{M}\p{N}`, so a Devanagari vowel sign continues a word: `जीवन आनंदमय` doesn't match `जीवन आनंद`). Punctuation between words breaks a match.
- `verifyShared` fails when any `site.*` or `seed.*` i18n value contains a blocked term.
- Generated as `PublicTextRules.blockedTerms`; `catalog_sync` emits `catalog_blocked_terms()`. The server also blocks every company's own name (BACKEND.md, WP2), which isn't a definition.

### `planStatuses`
| key | en | hi | tone | icon (provisional) |
|---|---|---|---|---|
| `trial` | Free trial | निःशुल्क ट्रायल | info | hourglass_empty |
| `active` | Active | सक्रिय | success | verified |
| `grace` | Renew now | अभी नवीनीकरण करें | warning | autorenew |
| `lapsed` | Expired | समाप्त | danger | timer_off |

### `privacyNotices`
The versioned privacy notices of PRIVACY.md ("Notice and consent"). Each entry is `{ key, labelKey, order, version, textKeys }`, with no icon or tone:
- `labelKey` is the notice's title;
- `version` is a whole number ≥ 1. **Any change to the text of a notice that has been shown to anyone needs a new version** (`verifyShared` enforces it through the lockfile);
- `textKeys` is the ordered list of `shared/i18n` keys whose text **is** the notice: everything the person sees, including the checkbox line, link labels that are part of a sentence and button text. The hash covers exactly these values.

| key | Who sees it | Fiduciary | textKeys (in order; all `privacy.<key>.*` unless noted) | version |
|---|---|---|---|---|
| `agent_signup` | Agent, app Create account and Welcome's Continue with Google (checkbox + full notice screen) | Agent PRO | `consent`, `title`, `intro`, `privacy.notice.col_data/col_purpose/col_kept`, then `<row>_data/_purpose/_kept` for the rows `account`, `profile`, `website`, `billing`, `registrant`, `session`, `customers`, then `customers_note`, `lapsed`, `no_ads`, `processors`, `rights`, `withdraw`, `grievance`, `board` | 2 (2026-10-25, DECISIONS #112/#114: the account row can come from Google, Google is a processor, no designation) |
| `enquiry_form` | The public, site enquiry form (checkbox) and the site privacy page | The agent | `consent`, `link`, `title`, `what`, `why`, `who`, `how_long`, `rights` | 1 |
| `agent_customer_duty` | Agent, the one-time attestation sheet before the first customer / import / manual enquiry | The agent (their duty) | `title`, `text`, `action` | 1 |
| `guardian_consent` | Agent, customer form when the DOB gives an age under 18 (provisional; counsel) | The agent | `title`, `notice`, `consent` | 1 |
| `customer_portal` (order 5) | The customer, the portal's first-login form (checkbox) and the site privacy page's `#portal` section | The agent | `consent`, `link`, `title`, `what`, `why`, `who`, `how_long`, `reminders`, `rights` (all `privacy.customer_portal.*`; `consent` and `link` are the checkbox line and link, the rest the itemised notice rendered under the heading `title`) | 1 |
| `customer_signup` (order 6) | The customer, the sign-up page's first screen (checkbox, before any field can be filled) and the site privacy page's `#signup` section | The agent | `consent`, `link`, `title`, `what`, `why`, `who`, `how_long`, `rights` (all `privacy.customer_signup.*`; laid out like `customer_portal`). `how_long` says "30 days", which is `retention.customerDraftsDays`: changing one means changing the other and bumping the version (DECISIONS #108) | 1 |
| `customer_signup_guardian` (order 7) | The sign-up page, when the date of birth gives an age under 18: the line and the required checkbox before Submit | The agent | `title`, `notice`, `consent` (all `privacy.customer_signup_guardian.*`). A separate notice from `guardian_consent`, whose wording is the agent's toggle in the app; this one is said by the parent or guardian themselves | 1 |

- The checkbox, attestation and toggle wording is PRIVACY.md's, word for word. The itemised `agent_signup` table, its "Also" bullets and the Hindi of everything PRIVACY.md doesn't translate were written for P1-1 from PRIVACY.md's tables and are **provisional (counsel)**.
- `privacy.agent_signup.link_terms` ("Terms" / "शर्तों") and `link_notice` ("Privacy notice" / "गोपनीयता सूचना") are the clickable parts of the `consent` sentence. They are substrings of it, so they aren't in `textKeys`.
- Placeholders: `{name}`, `{phone}` and `{email}` (the agent's, filled in by the site at render time) are lower case. Upper-case tokens (`{GRIEVANCE_OFFICER_NAME}`, `{GRIEVANCE_EMAIL}`, `{GRIEVANCE_PHONE}`, `{EMAIL_PROVIDER}`) are **fill-before-release placeholders**: replace them with the real text (and bump the notice version) once decided. See [Release gate](#release-gate-verifyreleaseready).
- Some notice texts repeat `retention` values (12 months, 30 days / 7 days / 48 hours, 8 years, 90 days). Changing one of those values means editing the text too, with a new notice version.

**The hash (canonicalization v1).** For a notice `key`, its `version` and a locale (`en`, `hi`):
1. take the locale's values of `textKeys`, in order, exactly as stored (placeholders such as `{name}` are **not** substituted: the hash covers the template, not one agent's rendering);
2. in each value turn `\r\n` and `\r` into `\n`, then normalise to Unicode **NFC**;
3. join the values with one `\n`;
4. serialise `{"key":…,"locale":…,"text":…,"version":…}` as compact JSON with the keys in that (sorted) order and standard JSON string escaping (only `"`, `\` and control characters escaped; everything else raw). This is exactly JavaScript's `JSON.stringify({key, locale, text, version})`;
5. `sha256` of the UTF-8 bytes, as 64 lowercase hex characters.

So the hash changes when any text value, their order, the version, the key or the locale changes, and EN and HI always differ. Never change these rules; a new rule would need a new canonicalization number (`PrivacyNotice.CANONICALIZATION`, `canonicalization` in the JSON). A unit test checks the Kotlin hash against Node's `JSON.stringify` + `crypto` for every real notice.

**What a client sends with a consent:** `{notice_key, version, hash}` for the notice text it actually showed, in the language it showed it. The server checks it with the `catalog_privacy_notice*` functions (BACKEND.md, catalog_sync contract) and records the hash, so the database knows exactly which text was agreed to.

### `dataRequestKinds`
Requests a data principal can make (PRIVACY.md, Data principal rights): the public form on an agent's privacy page and an agent's own request to the platform. Tone `neutral` for all; icons and Hindi are provisional (no `delete` icon is imported, so erasure uses `close`).

| key | en | hi | icon |
|---|---|---|---|
| `withdraw_consent` | Withdraw my consent | मेरी सहमति वापस लें | block |
| `access` | See my data | मेरा डेटा दिखाएँ | visibility |
| `correction` | Correct my data | मेरा डेटा सुधारें | edit |
| `erasure` | Delete my data | मेरा डेटा हटाएँ | close |
| `grievance` | Complaint | शिकायत | support_agent |

`withdraw_consent` is first on purpose: withdrawing must be as easy as consenting.

### `dataRequestStatuses`
| key | en | hi | tone | icon | terminal |
|---|---|---|---|---|---|
| `open` | Open | खुला | warning | hourglass_empty | no |
| `done` | Done | पूरा हुआ | success | check_circle | yes |
| `not_genuine` | Not genuine | वास्तविक नहीं | neutral | block | yes |

Escalation is the `escalated_at` timestamp, not a status. The labels are provisional.

### `retention`
Server-authoritative periods (PRIVACY.md, Retention and erasure; DECISIONS #54). `catalog_sync` writes the object to `app_config.retention`; `Catalog.kt` has it as `Retention.*` (for example `Retention.LapsedAccount.deleteAfterMonths`). All values are provisional (counsel) except the two user decisions marked ✔.
```json
"retention": {
  "softDeletedDays": 30, "erasedTombstoneDays": 30,
  "lapsedAccount": { "deleteAfterMonths": 12, "noticeDaysBefore": [30, 7, 2] },
  "unconfirmedSignupDays": 7, "billingRecordsYearsAfterFinancialYear": 8,
  "registrantContactsDaysAfterLastDomain": 90, "domainQuotesDays": 30, "siteDailyViewsMonths": 25,
  "consentsYearsAfterData": 1,
  "dataRequests": { "keepYearsAfterClose": 3, "responseDays": 30, "reminderDays": [7, 21], "escalateAfterDays": 30 },
  "auditLogMonths": 13, "orphanMediaDays": 30, "endedPolicySuggestDeleteYears": 3,
  "portalSessionsDays": 30, "portalInvitesDays": 30, "customerDraftsDays": 30
}
```
- ✔ `lapsedAccount`: deleted 12 months after the plan lapses, with notices 30 days, 7 days and **2 days (48 hours)** before.
- ✔ **There is deliberately no enquiry period:** the agent, as the fiduciary, decides when enquiries are deleted (no automatic deletion, no setting). Never add one without a new decision.
- `dataRequests` also carries the request timing (30-day response target, digest reminders at 7 and 21 days, escalation at 30).
- `endedPolicySuggestDeleteYears` only *suggests* deletion (IRDAI record-keeping, counsel).
- `portalSessionsDays` and `portalInvitesDays`: how long expired or revoked customer-login sessions, and used, expired or revoked invites, are kept before the (planned) retention job deletes them. The `customer_portal` notice's "up to 90 days" is `portal.sessionDays`, not a retention value.
- `customerDraftsDays` (30, the user's choice, DECISIONS #108): an unfinished customer form (the agent's own, or a sign-up link the customer didn't finish) has its personal data erased this many days after its last change (the emptied row is soft-deleted, so the app drops it on its next sync), and a sign-up link stops working at that age. Completed, deleted and erased drafts are hard-deleted this many days after their last change (`private.purge_customer_drafts()`, BACKEND.md). The `customer_signup` notice states the number.
- Every value is a whole number ≥ 0 or a non-empty list of them (`verifyShared`).

### `customerSignup` (provisional; DECISIONS #108)
Limits of the customer sign-up link. An object, not a list: `catalog_sync` writes it to `app_config.customer_signup` (read at run time by `private.customer_signup_config()`); no icon, tone or i18n labels. All values are **provisional**.
```json
"customerSignup": { "linkDays": 7, "savesPerMinute": 60, "openLinksPerAgent": 25, "presenceSeconds": 30 }
```
- `linkDays`: how long a sign-up link works (a new link from the app restarts it). `savesPerMinute`: autosaves accepted per form per minute (the page saves about once per pause in typing, plus a heartbeat every 20 s). `openLinksPerAgent`: links an agent can have open (not finished, not deleted, not expired) at once. `presenceSeconds`: the app shows "On the form now" while the customer's page saved or sent a heartbeat within this many seconds.
- **Kotlin:** emitted as `object CustomerSignup` in `Catalog.kt`; the app reads `presenceSeconds`.
- `verifyShared` (`checkCustomerSignup`) requires exactly these four keys, each a whole number > 0.

### `portal` (provisional; DECISIONS #96–#100)
Limits of the customer login. An object, not a list: `catalog_sync` writes it to `app_config.portal` (read at run time by `private.portal_config()`); it has no icon, tone or i18n labels. All values are **provisional** (the user said "decide everything"); counsel and a real-use review may change them.
```json
"portal": { "pinLength": 6, "inviteDays": 7, "inviteMaxPhoneTries": 5, "sessionDays": 90,
            "maxFailedAttempts": 5, "lockMinutes": 15, "hardLockAfter": 20,
            "enquiriesPerHour": 5, "paymentsShown": 12, "remindDaysBefore": [7, 1],
            "failureWindowDays": 30, "hardLockHours": 24, "siteFailedLoginsPerMinute": 30,
            "blockedPins": ["000000", "111111", "123456", "…"] }
```
- `pinLength`: digits in the PIN (the web form's `pattern` is built in `portal.js` as 6; keep them equal). `inviteDays`: how long a login link works. `inviteMaxPhoneTries`: wrong mobile numbers before a link burns. `sessionDays`: how long a device stays signed in (the cookie's `Max-Age` comes from the RPC's `expires_at`).
- `maxFailedAttempts` and `lockMinutes`: every `maxFailedAttempts`-th wrong PIN locks the login for `lockMinutes`. `hardLockAfter`: the failure count, **within `failureWindowDays` (30)**, that hard-locks the login (DECISIONS #101). The hard lock lasts **`hardLockHours` (24)** and then clears by itself (it used to last until a new link). A correct PIN ends only the soft streak, never the window count.
- `siteFailedLoginsPerMinute`: failed logins a minute allowed on one site, all numbers together, checked before any bcrypt work (answers `rate_limited`). `blockedPins`: 111 common 6-digit PINs (repeats, runs, years, keyboard patterns) that `portal_activate` refuses, besides the pattern rule and the customer's date of birth (DDMMYY, YYMMDD, MMDDYY). The list is the only place the rule lives; the web form shows the generic hint `portal.err_pin_rule`.
- `enquiriesPerHour`: portal questions per site and mobile per hour. `paymentsShown`: recorded payments shown per policy.
- `remindDaysBefore`: days before a due date to send a push reminder (**reserved**; reminders are not built).
- **Kotlin:** `CatalogKotlinGenerator` emits `catalog.portal` as `object Portal` in `Catalog.kt` (like `Pricing` and `Retention`; `remindDaysBefore` is a `List<Int>`). The app reads only `pinLength` (the share message); the server enforces every limit.
- `verifyShared` (`checkPortal`) requires every key (now 14, including `failureWindowDays`, `hardLockHours`, `siteFailedLoginsPerMinute` and `blockedPins`), whole numbers > 0, no unknown keys and `hardLockAfter > maxFailedAttempts`; `remindDaysBefore` is a non-empty list of distinct whole numbers > 0; `blockedPins` is a non-empty list of distinct strings of exactly `pinLength` digits. The Kotlin `Portal` object carries the new keys too (`blockedPins` as a `List<String>`; the app reads none of them).

### Offers (OFFERS.md; Phase 8b-1)
**Companies and offers themselves are runtime data in the database**, not definitions: company names are stored per locale in `companies.name`, never as i18n keys. Everything below is provisional unless OFFERS.md says the user chose it. The icons are reused from ones already imported (`local_offer` would be better for offers, but importing it needs the icon package downloaded; DECISIONS #73).

| List | key: en / hi · icon · tone | Notes |
|---|---|---|
| `offerStatuses` | `draft` Draft / ड्राफ़्ट · edit · neutral; `live` Live / चालू · check_circle · success; `archived` Archived / संग्रहीत · block · neutral | Stored (`offers.status`) |
| `offerPhases` | `upcoming` Upcoming / आने वाला · schedule · info; `running` Running / चल रहा है · check_circle · success; `ended` Ended / समाप्त · event_busy · neutral | Derived from the dates, never stored; labels and tones only |
| `offerCtaKinds` | `enquire` Enquiry form / पूछताछ फ़ॉर्म · inbox; `call` Phone call / फ़ोन कॉल · call; `link` Website link / वेबसाइट लिंक · public (all neutral) | Extra field `agentAllowed`: true for `enquire` and `call`, **false for `link`** (admins only) |
| `adminRoles` | `owner` Owner / मालिक · verified_user; `editor` Editor / संपादक · edit (neutral) | Stored (`admins.role`) |
| `membershipStatuses` | `pending` Waiting for approval / स्वीकृति की प्रतीक्षा · hourglass_empty · info; `approved` Approved / स्वीकृत · verified · success; `rejected` Not approved / स्वीकृत नहीं · block · danger; `revoked` Removed / हटाया गया · close · danger; `withdrawn` Withdrawn / वापस लिया गया · assignment_return · neutral | Stored (`company_memberships.status`) |
| `membershipRejectReasons` | `not_found` Agent not found · search; `details_mismatch` Details don't match · error; `not_agent` Not an agent of this company · person; `other` Other reason · more_horiz (neutral) | Shown to the agent in their language |
| `insuranceTypes` (DECISIONS #89; **provisional**, values and Hindi for the user to review) | `life` Life / जीवन · favorite; `health` Health / स्वास्थ्य · health_and_safety; `motor` Motor / मोटर · shield; `home` Home / घर · home; `travel` Travel / यात्रा · public; `business` Business / व्यापार · work; `accident` Accident / दुर्घटना · verified_user (all neutral) | The types an agent deals in at each company, stored in `company_memberships.insurance_types` (≥ 1 per pending or approved link) and shown on sites with the approved companies' names (DECISIONS #90). A separate list from the enquiry `interests`. The icons reuse imported ones; `directions_car`, `flight` and `personal_injury` would fit better but need the icon package downloaded (as for `local_offer`). `catalog_sync` emits `catalog_insurance_types()` (active keys in `order`) |

**`offers`** (an object, like `pricing`): `{ "maxSectionItems": 6, "maxValidityDays": 366, "logoMaxBytes": 262144, "imageMaxBytes": 524288, "imageAspect": "16:9", "logoMaxPx": 512 }`. `Catalog.kt` has it as `Offers.*`; `catalog_sync` emits it as `catalog_offer_limits()`. `verifyShared` keeps the site schema within it (an `offerList`'s `maxItems` ≤ `maxSectionItems`, a `maxDaysAhead` ≤ `maxValidityDays`) and the offer image aspect equal to `imageAspect`.

### `offer-schema.json`
```json
{ "version": 1,
  "localized": [ { "key": "title", "type": "text", "required": true, "maxLength": 60, "labelKey": "offer_field.title" }, "… summary (160, required), details (600), badge (20), disclaimer (300)" ],
  "fields": [
    { "key": "internalName", "type": "text", "maxLength": 120, "adminOnly": true, "labelKey": "offer_field.internal_name" },
    { "key": "image",    "type": "image",  "aspect": "16:9", "labelKey": "offer_field.image" },
    { "key": "ctaKind",  "type": "select", "required": true, "optionsFrom": "catalog.offerCtaKinds", "labelKey": "offer_field.cta_kind" },
    { "key": "ctaUrl",   "type": "url",    "requiredIf": { "ctaKind": "link" }, "labelKey": "offer_field.cta_url" },
    { "key": "interest", "type": "select", "optionsFrom": "catalog.interests", "labelKey": "offer_field.interest" },
    { "key": "startsAt", "type": "date",   "required": true, "labelKey": "offer_field.starts_at" },
    { "key": "endsAt",   "type": "date",   "required": true, "labelKey": "offer_field.ends_at" } ] }
```
- `localized` fields are text or multiline with a `maxLength`; they're stored per locale in `offers.content` (`{"en": {...}, "hi": {...}}`). Going live needs every `required` one in every catalog locale (`catalog_locales()`).
- `fields` use `text`, `multiline`, `url`, `image`, `select` (with a catalog `optionsFrom`) or `date`. Here `optionsFrom: "catalog.offerCtaKinds"` means **all** active kinds (admins).
- `requiredIf: {"<field>": "<value>"}` (admin forms only): required when that other field has that value. `verifyShared` checks the field exists and, for a catalog select, that the value is one of its keys.
- `title` is the offer's **public, generic name** (label "Public name (generic — no product or company names)"; DECISIONS #77). `internalName` (not localized, ≤ 120) holds the real product name for the admin panel.
- `adminOnly: true` (non-localized `fields` only, boolean): the field is shown and stored for admins only and never reaches any public payload (column `offers.internal_name`, WP2). Localized fields are public text, so `adminOnly` is refused there.

### Privacy UI copy
`site.privacy_*` (the site privacy page's headings and request form) and `site.form_message_hint` belong to the web work package (P1-3, P2).

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
**Sections** (in this default order). Every section is `defaultVisible: true` except `offers`. Values marked (provisional) aren't set by the product docs. Section icons: hero `badge`, about `person`, portal `person`, services `work`, highlights `star`, achievements `trophy`, testimonials `format_quote`, faq `help`, contact `call` (all provisional).

| Section | Fields |
|---|---|
| `hero` (pinned) | headline (required, ≤ 70), subheadline (≤ 160), photo (image, 1:1), ctaLabel (≤ 30, `defaultKey: site.cta_default`), featuredOffer (offerRef) |
| `about` | bio (≤ 600), designation (≤ 60, provisional), yearsExperience (number 0–70, provisional), clientsServed (number 0–1,000,000, provisional), languagesSpoken (multiSelect, options below), showAgencyCode (toggle, default off) |
| `companies` (icon `handshake`, no fields) | **None.** The section's content is the payload's approved company links (DECISIONS #90), not agent-entered. It exists so agents can hide and reorder it; the app lists it in Content with a note instead of an editor. Sits after `about` in the default order. |
| `portal` (icon `person`, no fields) | **None.** "For my customers" (DECISIONS #102): the section's copy is fixed i18n (`site.portal_*`), not agent-entered. It is drawn only when the public payload's `portal` is true; the app lists it in Content with a note (like `companies`) so agents can move and hide it. Sits right before `contact` in every template's default order (`templates/index.json`); a saved order that doesn't name it gets it inserted before `contact`. Label `section.portal` ("For my customers" / "मेरे ग्राहकों के लिए") |
| `services` | offered (multiSelect from `interests`), items list (≤ 8, provisional): interest (select from `interests`, required), description (≤ 160) |
| `highlights` | items ≤ 4: icon (select from the fixed set below, required), title (required, ≤ 40), text (≤ 120) |
| `achievements` | items ≤ 8: title (required, ≤ 60), year (number 1950–2100, provisional) |
| `testimonials` | items ≤ 6: name (required, ≤ 40), city (≤ 30), quote (required, ≤ 280) |
| `faq` | items ≤ 8: question (required, ≤ 120), answer (required, ≤ 400) |
| `offers` (Phase 8b-1; `defaultVisible: false`, icon `savings` provisional) | title (≤ 40, `defaultKey: site.offers_title_default`), items (offerList, ≤ 6), custom list (≤ 6): id (generated uuid), title (required, ≤ 60), summary (≤ 160), endsOn (date, required, `maxDaysAhead` 366), ctaKind (select from `offerCtaKinds`, agent kinds only, required), interest (select from `interests`), confirmed (toggle, `equals: true`: "I confirm this offer is accurate and my company allows it"). Custom offers have no image. **Not shown in the app until 8b-4, nor rendered on the web until 8b-3** |
| `contact` (pinned) | phone (required, provisional), whatsapp, email, officeAddress (≤ 200, provisional), mapLink (url), officeHours (≤ 60, provisional), showEnquiryForm (toggle, default on), showInstallButton (toggle, default on: the site's "Add to home screen" button, DECISIONS #65), showCustomerLogin (toggle, default on: the whole customer login, which is the footer's "My policies" link, the hero's "Customer login" link and the "For my customers" section, DECISIONS #96 and #102; label `field.show_customer_login`; off = the portal reports `unavailable` for this site) |

**Option lists.** A field's `options` is a list of objects. An `optionsFrom` field reads another list instead.
- **Highlight icon set** (provisional): `shield`, `verified_user`, `support_agent`, `schedule`, `handshake`, `savings`, `favorite`, `thumb_up`. Each option is `{ key, icon, labelKey: "highlight_icon.<key>" }`, where `key` equals the icon name.
- **Languages spoken** (provisional): English, Hindi, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Odia, Urdu. Each option is `{ key, labelKey: "language.<key>" }`, with keys such as `english` and `hindi`.

**Field types:** `text`, `multiline`, `number`, `phone`, `email`, `url`, `image`, `select`, `multiSelect`, `toggle`, `swatch`, `date`, `list` (with `itemFields`), and for offers `offerRef` (one placed offer, `{"source": "company"|"agent", "id": "<uuid>"}`) and `offerList` (an array of them, with `maxItems`). `offerRef`/`offerList` only sit at section level, never inside a list.

**Field rules:** `required`, `maxLength`, `min`, `max`, `maxItems`, `options` (or `optionsFrom: "catalog.<list>"`), `default`/`defaultKey`, `aspect`, and:
- `generated: "uuid"` (text fields): the app fills the value with a new UUID and FormRenderer never shows the field;
- `equals: true` (toggles): the value must be true (the custom-offer confirmation);
- `maxDaysAhead: N` (dates): no later than today (Asia/Kolkata) + N days;
- `requiredIf` is **not** allowed here; it's for offer-schema.json (admin forms) only.

`optionsFrom` a list with an `agentAllowed` field (`offerCtaKinds`) is narrowed to the entries with `agentAllowed: true`, because every site field is filled in by the agent (`SiteSchema.kt` filters them; `publish_site` uses `catalog_offer_cta_kinds_agent()` from 8b-2).

**Stored content** (in `sites.draft_content` and `sites.published_content`):
```json
{ "sectionOrder": ["hero","about","services","testimonials","contact"], "hidden": ["faq"],
  "hero": { "headline": "…", "photo": "https://…/site-media/<agent>/hero.webp" },
  "testimonials": { "items": [ { "name": "…", "quote": "…" } ] } }
```
Content is independent of the template, so switching templates never loses data.

## `templates/index.json`
`{ "version": 1, "templates": [ { key, family, nameKey, descriptionKey, sections, defaultAccent, preview, order } ] }`. `family` is a `templateFamilies` key. `sections` lists the supported section keys (all 11 for every template, `offers`, `companies` and `portal` included) **in the order a new site starts with**: `companies` follows `about` on the personal templates and `highlights` (the trust strip) on the offers templates, matching where the web places it (StarterContent copies it into `sectionOrder`). `portal` sits right before `contact` in all five orders (DECISIONS #102). `defaultAccent` is a swatch key and `preview` is `web/templates/<key>/preview.webp` (the web work package makes the images; `verifyShared` doesn't check that they exist).

| key | family | name (en / hi) | default accent (provisional) |
|---|---|---|---|
| `classic` | personal | Trust / भरोसा | indigo |
| `modern` | personal | Fresh / ताज़ा | teal |
| `minimal` | personal | Simple / सरल | slate |
| `spotlight` | offers | Spotlight / स्पॉटलाइट — featured offer up front, then an offer grid | maroon |
| `guide` | offers | Guide / गाइड — visitors pick what to protect and see matching plans | green |

Section order: `personal` templates use hero, about, services, offers, highlights, achievements, testimonials, faq, **portal**, contact; `offers` templates use hero, **offers**, services, highlights, about, testimonials, achievements, faq, **portal**, contact (`companies` is placed as described above).

## `site-seed.json`
The starter content of a new site (DECISIONS #78). All text is i18n keys (`seed.*`); the codegen resolves it for **every** catalog locale into `SiteSeed.kt`, because the site's language can differ from the app's. Seeded content is stored as ordinary draft content, so the agent edits it like anything else.
```json
{ "version": 1,
  "placeholders": { "name": "fullName", "firstName": "firstName", "city": "city" },
  "requiredPlaceholders": ["name", "firstName"],
  "visibility": { "hidden": ["achievements", "testimonials"], "visibleOnlyInFamily": { "offers": "offers" } },
  "insuranceTypes": {
    "whenNone": ["life"],
    "seedInterests": { "life": ["term", "endowment", "child", "pension"], "health": ["health"], "motor": ["motor"],
                       "home": ["home"], "travel": ["travel"], "business": ["business"], "accident": ["accident"] },
    "serviceDescriptions": { "term": { "variants": ["seed.services.term"] }, "…": {}, "accident": { "variants": ["seed.services.accident"] } },
    "mixes": [ { "key": "life", "onlyTypes": ["life"] }, { "key": "general" } ] },
  "sections": {
    "hero":  { "headline": { "variants": ["seed.hero.headline_1", "seed.hero.headline_2", "seed.hero.headline_3"] },
               "subheadline": { "byMix": {
                 "life":    { "variants": [ { "key": "seed.hero.subheadline_1", "fallback": "seed.hero.subheadline_1_short" }, "…", "seed.hero.subheadline_3" ] },
                 "general": { "variants": [ { "key": "seed.hero.subheadline_general_1", "fallback": "seed.hero.subheadline_general_1_short" }, "…" ] } } } },
    "about": { "bio": { "byMix": { "life": { "variants": ["…seed.about.bio_1…"] }, "general": { "variants": ["…seed.about.bio_general_1…"] } } },
               "designation": { "variants": ["seed.about.designation"] },
               "languagesSpoken": { "byLocale": { "en": ["english"], "hi": ["hindi"] } } },
    "services": { "offered": { "fromInsuranceTypes": "interestKeys" }, "items": { "fromInsuranceTypes": "serviceItems" } },
    "faq": { "items": { "byMix": { "life": { "items": ["…"] }, "general": { "items": ["…"] } } } },
    "contact": { "phone": { "fromProfile": "phone" }, "whatsapp": { "fromProfile": "phone" }, "email": { "fromProfile": "email" } } } }
```
**Field forms** (exactly one per field):
- `variants`: text and multiline fields only. A list of i18n keys, or `{ "key", "fallback" }`. One variant is picked per site (below).
- `fromProfile`: a profile value: `fullName`, `firstName` (the first word of the full name), `phone`, `email` or `city`. Left out when blank. (Not `designation`: the agent no longer enters one, DECISIONS #114.)
- `value`: a fixed JSON value valid for the field (a select's option key, a multiSelect array, a toggle, a number, a string).
- `byLocale`: a value per site locale; every active locale must be present.
- `items`: list fields only. One object per item, each item field in one of the forms above (no nested `items`).
- `byMix` (DECISIONS #93): one form per copy mix (`insuranceTypes.mixes`), each any form above except `byMix`; it must name **every** mix and no other. The agent's mix picks the form; the salt stays the field's (so `faq.items.2.answer` is the same salt in both mixes).
- `fromInsuranceTypes` (DECISIONS #93): `"interestKeys"` on a multiSelect from `catalog.interests` (`services.offered`), or `"serviceItems"` on a list with an `interest` select from `catalog.interests` and a text `description` item field (`services.items`). The value is derived from the agent's types (below).

**Insurance types (DECISIONS #93).** The agent's types are the union of `insurance_types` over their **pending and approved** company links (`SiteServices.agentTypes`; rejected, revoked and withdrawn links don't count), reduced to active catalog types in catalog order; an agent with none gets `whenNone` (`life`, the platform's first market and the `{life}` backfill of #89). Then:
- **Copy mix:** the first of `mixes` whose `onlyTypes` holds every type of the agent, so `life` (the original life-and-family copy) exactly when every type is life, and `general` (copy for general and mixed agents) otherwise. The last mix must have no `onlyTypes`, so every agent gets one.
- **Services:** `seedInterests` lists, per insurance type, the interests a new site offers for it (life: term, endowment, child plan, pension; every other type: its one interest; money back and ULIP aren't seeded). They're picked **round-robin** over the agent's types in catalog order (each type's 1st interest, then each 2nd, …, skipping repeats) until the services list's `maxItems` (8) is reached, so with all seven types every type still appears (term, endowment, health, motor, home, travel, business, accident). `services.offered` keeps the round-robin order (so the hero's first 4 quick links span the types); `services.items` holds the same interests grouped by type in catalog order, each with its `serviceDescriptions` text (salt `services.items.<i>.description`). Health is its own type: a life-only agent no longer gets a Health service (it was in the v1 seed).
- **Where it's decided** (all single-sourced here): `whenNone`, `seedInterests`, `serviceDescriptions` and `mixes`; the round-robin and the mix rule are `SiteSeed.servicesFor` / `SiteSeed.mixFor` (generated) and mirrored in `scripts/site-seed.mjs`. The default list per type lives in site-seed.json, not on `catalog.insuranceTypes`, because it is starter content (nothing in SQL or the web renderer reads it).

**What's seeded:** hero headline (3 greeting variants, the same for every mix) and subheadline (3 per mix); about bio (3 per mix), designation (the fixed "Insurance agent" / "बीमा एजेंट", `seed.about.designation`; DECISIONS #114), languages spoken (from the site locale); services from the agent's types (above), one item and description each; 4 highlights (support_agent, schedule, handshake, verified_user; title + 3 text variants; worded for every type, so not per mix); 5 educational FAQs per mix, 3 answer variants each: **life** = how much life cover, term vs savings, documents, premium payment (with tax benefits "as per prevailing tax laws"), claim help; **general** = how to choose the right cover, looking at existing policies, documents (general wording: ID, address proof and details of what is insured), premium payment (single payment, yearly or instalments, no tax claim), claim help (the same texts as life); contact phone, WhatsApp (= phone) and email. The general copy names no insurance line (so it is never false for the agent's mix), no product, insurer, return or tax benefit; the five new service descriptions say "as per the policy (terms)" where cover depends on the policy, and the motor one states only that third-party cover is required by law. `hero.ctaLabel` keeps its `defaultKey`. **Testimonials, achievements, years of experience and clients served are never seeded** (no invented social proof). The copy is first person (the agent speaking); the Hindi avoids gendered first-person verb forms (करता/करती) so it fits every agent. All wording is provisional.

**Visibility and order.** A new site's `hidden` = `visibility.hidden` plus every section in `visibleOnlyInFamily` whose family isn't the chosen template's (so `offers` is visible only for the `offers` family): `SiteSeed.hiddenSections(family)`. `sectionOrder` = the chosen template's `sections`.

**Placeholders.** `{name}`, `{firstName}`, `{city}`, filled from the profile (trimmed). `requiredPlaceholders` (`name`, `firstName`) are always available. A text that uses an optional one (`{city}`) **must** have a `fallback` that uses only required ones; the bios say "an insurance agent" in their own words instead of a `{designation}` placeholder (DECISIONS #114); the fallback is used when any placeholder of the main text is blank (`SiteSeed.fill`). After filling, StarterContent cuts the text to the field's `maxLength`.

**Variant selection (SEO: sites don't duplicate each other).** Deterministic per agent and per field:
`index = fnv1a32(utf8(agentId + ":" + salt)) mod variantCount`, where FNV-1a 32 uses offset `2166136261` and prime `16777619` over the UTF-8 bytes, `agentId` is the agent's user id, and `salt` is `"<section>.<field>"` for a section field or `"<section>.<field>.<index>.<itemField>"` for a list item's field (e.g. `faq.items.2.answer`). The salt makes fields pick independently. Test vectors: `fnv1a32("") = 0x811c9dc5`, `fnv1a32("a") = 0xe40c292c`, `fnv1a32("foobar") = 0xbf9cf968`; for agent `00000000-0000-0000-0000-000000000001` with 3 variants, `hero.headline` → 1, `hero.subheadline` → 0, `about.bio` → 0, `faq.items.2.answer` → 2. (DECISIONS #78 says "from the slug"; the agent id is used because it exists before a slug is chosen and never changes.)

**`SiteSeed.kt`** (`core.catalog`, generated when the file exists):
- `SeedText(key, byLocale: Map<String, String>)` with `raw(locale)` (falls back to `DEFAULT_LOCALE`); `SeedVariant(text: SeedText, fallback: SeedText?)`; `sealed interface SeedValue` = `Variants(variants)`, `FromProfile(field)`, `Literal(value: JsonElement)`, `ByLocale(values)` with `value(locale)`, `Items(items: List<Map<String, SeedValue>>)`; `SeedSection(key, fields: Map<String, SeedValue>)`.
- `SeedValue` also has `ByMix(values: Map<String, SeedValue>)` with `value(mix)`, and `FromInsuranceTypes(kind)`; `SeedMix(key, onlyTypes: List<String>?)`; `SeedServices(offered, items)`.
- `object SiteSeed`: `VERSION`, `DEFAULT_LOCALE`, `LOCALES`, `PLACEHOLDERS`, `REQUIRED_PLACEHOLDERS`, `PROFILE_FIELDS`, `hidden`, `visibleOnlyInFamily`, `texts: Map<String, SeedText>`, `sections: List<SeedSection>`, `INSURANCE_TYPES` (active `catalog.insuranceTypes` keys in order), `WHEN_NONE`, `seedInterests`, `serviceDescriptions: Map<String, SeedValue.Variants>`, `mixes`, `section(key)`, `text(locale, key)`, `hiddenSections(family: String)`, `fnv1a32(value): UInt`, `salt(section, field, index?, itemField?)`, `pick(seed, salt, count): Int`, `placeholdersIn(text)`, `fill(text, values)`, `fill(variant, locale, values): String?`, `resolve(variants, seed, salt, locale, values): String?`, `typesOrDefault(types)`, `mixFor(types): String` and `servicesFor(types, max): SeedServices`.
- `StarterContent.from(profile, template, locale, agentId, insuranceTypes)` applies them; `SiteServices` (hand-written, `feature/website/data`) compares an existing site with the types and adds or removes services (UX.md, Settings → Profile).
- A build-logic reference (`SiteSeedJson.fnv1a32`/`pick`) is unit-tested against the vectors above.

## `design-tokens.json`
The structure is below; the values are in DESIGN_SYSTEM.md. The file also has `"version": 1`. Colour roles include `background`, `surface` (equal to `background`) and `muted`. Each `tone.<name>` is `{ light: {text, container}, dark: {text, container} }`. The dark `chart.ordinalRamp` (`#1c4f93 #2a6dc4 #3987e5 #6ba3ee`) isn't given in DESIGN_SYSTEM.md and is provisional.
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
- `field.offers_items` reads "Offers on your site" (HI "आपकी साइट पर ऑफ़र"): the Offers section's picker row (DECISIONS #104); the key is unchanged.
- Keys take the form `<group>.<key>`, for example `enquiry_status.follow_up`, `section.hero`, `field.headline` and `site.cta_default`.
- Groups: `enquiry_status`, `interest`, `enquiry_source`, `premium_mode`, `policy_status`, `due_bucket`, `plan_status`, `domain_status`, `swatch`, `locale`, `feature`, `section`, `field`, `highlight_icon`, `language`, `template` (`template.<key>.name` and `.description`), `site` (fixed site copy), `data_request_kind`, `data_request_status`, `offer_status`, `offer_phase`, `offer_cta_kind`, `admin_role`, `membership_status`, `membership_reject_reason`, `insurance_type`, `offer_field` (the offer form fields, shared by offer-schema.json and the custom offers), `template_family` (`template_family.<key>.name` and `.description`), `seed` (site-seed.json starter copy: `seed.<section>.<field>_<n>`, fallbacks `_short`, list items `seed.<section>.<item>.<field>`), and `privacy` (`privacy.<notice key>.<part>` for each notice's text, `privacy.notice.*` for parts shared by notices). `locale.*` labels the app and site languages (`catalog.locales`); `language.*` is the `languagesSpoken` field's options.
- `i18n/en.json` and `i18n/hi.json` are flat objects with identical key sets, sorted by key. Placeholders such as `{name}`, `{n}` and `{code}` are the same in both.
- **Upper-case placeholders** (`{GRIEVANCE_EMAIL}`, `{EMAIL_PROVIDER}`, …: `{[A-Z][A-Z0-9_]*}`) are never substituted at runtime. They mark text that must be filled in before a release, and `verifyReleaseReady` fails while any remains.
- `portal.*` is the customer login's web copy (89 keys: pages, errors, the forms, the install and offline texts, the reminder strings and the five dashboard tile labels `portal.summary_policies|premium_year|cover|next_due|people`), rendered by `web/portal.js` and the Worker; the app's login card uses `strings_portal.xml` instead (20 keys, EN + HI). Other portal keys elsewhere: `feature.customerPortal`, `enquiry_source.portal`, `field.show_customer_login`, `site.footer_portal`, `site.header_portal` (the hero's "Customer login" link), `section.portal`, the "For my customers" copy `site.portal_heading`, `site.portal_lead`, `site.portal_point_policies|dues|payments|ask`, `site.portal_sign_in` and `site.portal_first_time`, and `privacy.customer_portal.*`. `portal.next_premium` and `portal.next_premium_line` (the removed "Next premium" card) are **unused** and kept until the next i18n clean-up; `portal.next_due` and `portal.no_due_date` are still used on the policy cards. The site copy promises only what the portal does today (policies, next premium, recorded payments, asking about a policy; no reminders). The Hindi wording in `portal.*` and the notice is a **first draft for a native-speaker review** (reviewers flagged `पिन` for PIN, `कूटबद्ध रूप` for "scrambled form", `नॉमिनी`, the consent line's `दिखाएँ` and `बीमा राशि`).
- The templates' moving parts and new sections (DECISIONS #117) add `site.menu` (the top bar's phone menu label), `site.slider_prev`, `site.slider_next`, `site.slider_dot` ("Show item {n}"; the renderer passes `#` for `{n}` and `motion.js` fills the number), `site.see_plans` (guide tiles), `site.steps_heading` and `site.step_1..3` (guide's "How it works"; the strip is left out while any is missing) and `site.policyholders_heading` (classic's testimonials). Template names and descriptions stay `template.<key>.name|description`. The Hindi is a first draft.
- `join.*` is the customer sign-up page's web copy (31 keys, DECISIONS #108): `join.page_title`, `join.back`, `join.consent_heading`, `join.consent_intro`, `join.start`, `join.heading`, `join.intro`, `join.field_name|phone|email|dob|address|city|nominee`, `join.hint_phone`, `join.submit`, `join.status_saving|saved|failed` (the autosave line), `join.err_name|phone|email|dob|long|guardian|consent|rate|link|generic`, `join.done_title` and `join.done_text`. Rendered by `web/join.js` and the Worker (`join-client.js` gets the status strings as `data-*` from the server). The notice and guardian wording are `privacy.customer_signup.*` and `privacy.customer_signup_guardian.*`; the Call/WhatsApp buttons reuse `site.call` and `site.whatsapp`. The Hindi wording is a first draft for a native-speaker review.
- `site.*` holds the fixed copy the public site uses: the "This page isn't available" message, the enquiry form labels and errors (`site.form_*`), the thank-you and rate-limited messages (`site.form_thank_you`, `site.form_rate_limited`, both take `{name}`), the contact buttons, the footer (`site.footer_advisor` takes `{name}`; `site.footer_made_with`), `site.cta_default`, and Add to home screen (`site.install_button`, `site.install_title` takes `{name}`, `site.install_text`, `site.install_action`, `site.install_close`, `site.install_ios`, `site.install_menu`; the iOS and browser menu labels are quoted in English in the Hindi text too, because that's what most phones show), and offers (`site.offers_title_default`, `site.offer_agent_note` "Details provided by the agent", `site.offer_ends` takes `{date}`, `site.offer_pick` "My pick for you" (replaces the removed `site.offer_from`: no company name on sites, DECISIONS #77), `site.plans_heading` (plan-type cards when a Best offers site has no offers), and the buttons `site.offer_cta_enquire`, `site.offer_cta_call`, `site.offer_cta_link`), the conversational lines (DECISIONS #76: `site.say_greeting` and `site.say_thanks` take `{firstName}`; `site.say_about`, `say_services`, `say_offers`, `say_highlights`, `say_achievements`, `say_testimonials`, `say_faq`, `say_contact`; `site.quick_reply_heading`; `site.guide_question`), SEO titles (`site.seo_title_fallback` takes `{city}`, `site.seo_title_fallback_nocity`) and `site.image_alt_interest` (takes `{interest}`). The sticky call bar reuses `site.call` and `site.whatsapp`. No `site.*` or `seed.*` text may contain a blocked term. The wording of everything under `site.*` other than what WEB_RENDERER.md quotes is provisional, as is the Hindi for strings that this doc doesn't translate.
- The generated resource name is `cat_` plus the key with dots turned into underscores, for example `cat_enquiry_status_follow_up`, accessed as `Res.string.cat_enquiry_status_follow_up`.
- Plain app UI copy that isn't tied to catalog or schema entries lives in `mobile/composeApp/src/commonMain/composeResources/values/strings.xml` and `values-hi/strings.xml`, plus per-area files beside them (`strings_crm.xml` holds the enquiries, customers, policies and dues copy, with `<plurals>`). These are shared by Android and iOS. `verifyShared` reads every XML file in `values/` and `values-hi/`.
- **`verifyShared` checks that every key in `values/` also exists in `values-hi/`.** Compose resources have no Android lint `MissingTranslation` check, so this replaces it.
- **Icons:** each catalog and schema `icon` must exist as a vector in `composeResources/drawable/ic_<name>.xml`. Material Symbols Rounded vectors are imported only for the icons actually used.
  - They come from the npm package `@material-symbols/svg-400@0.48.0` (`rounded/<name>.svg`, weight 400, unfilled). Each SVG has `viewBox="0 -960 960 960"` and is converted to a 24dp Android vector with a 960×960 viewport and a `translateY=960` group.
  - `emoji_events` doesn't exist in this package version; the trophy icon is `trophy` (provisional).

## Codegen: `./gradlew generateShared` (run from `mobile/`)
The codegen lives in `mobile/build-logic` (an included build). The generators and the verifier are plain Kotlin classes (`codegen/`, `verify/`), unit-tested with fixtures. The Gradle tasks in `tasks/SharedTasks.kt` only wire them up. Two convention plugins apply them:
- **`agentpro.shared`** (on `:composeApp`)
- **`agentpro.site-preview`** (on `:androidApp`)

`./gradlew generateShared` runs the `generateShared` task of both modules.

| Task | What it does | When it runs |
|---|---|---|
| `:composeApp:generateSharedCode` | Outputs 1–3 below | **Automatically before compiling `composeApp` and processing its resources.** Its outputs are a `commonMain` source dir and the `commonMain` Compose resources dir. It is incremental (declared inputs and outputs) and not build-cached, because `AppConfig.kt` holds `local.properties` values |
| `:androidApp:copySitePreviewAssets` | Output 4 | Automatically before Android asset merging. It's a generated assets source of every variant |
| `:composeApp:writeCatalogSync` | Output 5 | **Only through `generateShared`.** It writes into the source tree, so normal builds never create migrations; `verifyShared` catches a stale one |
| `:composeApp:updateCatalogKeysLock` | Adds new catalog keys and new privacy notice versions to the keys lockfile (below) | Only through `generateShared` |
| `:composeApp:writePrivacyNotices` | Output 6 | Only through `generateShared`. It writes into the source tree (`shared/generated/`); `verifyShared` catches a stale file |
| `verifyReleaseReady` (`:composeApp` and `:androidApp`) | The [release gate](#release-gate-verifyreleaseready) | **Not** part of `check` or debug builds. Every Android release variant's `pre<Variant>ReleaseBuild` depends on `:androidApp:verifyReleaseReady`; run it by hand before any release, including a web deploy |

The outputs:
1. **`Catalog.kt`** in package `com.sledje.agentpro.core.catalog`. It's written to `composeApp/build/generated/agentpro/kotlin/commonMain/`.
   - **`Tone`** comes from the `tone` keys in `design-tokens.json`. The interfaces are `CatalogEntry` (`key`, `label: StringResource`, `order`, `deprecated`), `CatalogIconEntry` (adds `icon: DrawableResource`) and `CatalogTonedEntry` (adds `tone`).
   - **One enum per catalog list:** `EnquiryStatus`, `Interest`, `EnquirySource`, `PremiumMode`, `PolicyStatus`, `DueBucket`, `PlanStatus`, `DomainStatus`, `AccentSwatch`, `AppLocale`, `FeatureId`, `DataRequestKind`, `DataRequestStatus`, `OfferStatus`, `OfferPhase`, `OfferCtaKind` (with `agentAllowed`), `AdminRole`, `MembershipStatus`, `MembershipRejectReason`, `InsuranceType` (`Life`, `Health`, `Motor`, `Home`, `Travel`, `Business`, `Accident`) and `TemplateFamily` (with `description`). `Interest` also has `image`. `privacyNotices` is the exception: it gets its own file, `PrivacyNotices.kt` (below).
     - The type name is the singular of the list name (`…ies` → `…y`, so `templateFamilies` → `TemplateFamily`). Two exceptions: `features` becomes `FeatureId`, and `locales` becomes `AppLocale` so it never clashes with `java.util.Locale` or Compose's `Locale`.
     - A constant is the key in PascalCase, for example `follow_up` → `FollowUp` and `next_3_months` → `Next3Months`.
     - Each entry also carries the list's extra fields, typed from the JSON: `terminal`, `months`, `minDays: Int?`/`maxDays`, `hex`, `default` and `agentAllowed`. An extra field named `<name>Key` holds an i18n key and becomes `<name>: StringResource` (`descriptionKey` → `description`, `showsKey` → `shows`, `bestForKey` → `bestFor`; DECISIONS #113); `verifyShared` checks each such key exists in `en` and `hi`.
     - Each enum has `fromKey(key)` and `active`, which lists the non-deprecated entries in display order.
     - **Deprecated entries stay in the enum** (`deprecated = true`), so stored values still parse.
   - **Other top-level values:** string arrays become an object with `all` (`PlanSuggestions.all`, `ReservedSlugs.all`). Objects become nested objects of `const val`s and `listOf(...)` (for example `Pricing.Service.purchaseInr`, `Retention.LapsedAccount.noticeDaysBefore`, `PublicTextRules.blockedTerms`). `version` becomes `Catalog.VERSION`.
   - **Labels and icons** use `Res.string.cat_*` and `Res.drawable.ic_*`.
2. **The other generated Kotlin files:**
   - **`SiteSchema.kt`** (`core.catalog`): `SiteSchema.sections` is a `List<SectionSpec>` of `FieldSpec`s. The hand-written model is in `core/forms/FieldSpec.kt` and has a `FieldType` for each of the 15 field types; `list` is `FieldType.ItemList`, `offerRef`/`offerList` are `OfferRef`/`OfferList` (their values stay raw `JsonElement`s in `FormState` and round-trip unchanged; FormRenderer shows nothing for them until 8b-4 adds the pickers). The rules `generated`, `equals` and `maxDaysAhead` become `FieldSpec.generated`, `mustBeTrue` and `maxDaysAhead`; a `generated: "uuid"` field starts with `Uuid.random()` and is never rendered. `optionsFrom: "catalog.<list>"` is resolved to that list's active entries as `FieldOption`s, and `defaultKey` becomes `defaultText: StringResource`.
   - **`Tokens.kt`** (`core.designsystem`):
     - `Tokens.ColorLight`/`ColorDark` hold `Color`s, and `Tokens.Tones.of(tone)` returns the tone's colours.
     - `Tokens.Chart` holds the chart colours, and `Tokens.Font` the font families and weights.
     - `Tokens.Text.s|m|l` are `TextToken(size, lineHeight)` in sp. **`text.xl` is web-only and is not generated.**
     - `Tokens.Space` and `Tokens.Shape` are `Dp`, and `Tokens.Motion` is in milliseconds.
   - **`Templates.kt`** (`core.catalog`): `TemplateSpec(key, name: StringResource, description: StringResource, sections, defaultAccent: AccentSwatch, preview, order, family: TemplateFamily)` per entry of `templates/index.json` (`family` only when the catalog has `templateFamilies`), and `Templates.all` (sorted by `order`) with `fromKey(key)` and `inFamily(family)`. The template pickers (onboarding step 2, later Website → Appearance) read it; a `defaultAccent` that isn't an `accentSwatches` key fails generation.
   - **`SiteSeed.kt`** (`core.catalog`): the starter content, when `site-seed.json` exists. See [site-seed.json](#site-seedjson).
   - **`PrivacyNotices.kt`** (`core.catalog`): `enum class PrivacyNotice(key, label, order, version, texts: List<StringResource>, …) : CatalogEntry` with one constant per notice (`AgentSignup`, `EnquiryForm`, `AgentCustomerDuty`, `GuardianConsent`). `texts` are the `textKeys` resources in display order; `hash(locale: AppLocale)` returns the notice hash for that language; `consent(locale)` returns `NoticeConsent(noticeKey, version, hash)`, the payload of the consent RPCs. `PrivacyNotice.CANONICALIZATION` is the hash rule's number (1).
   - **`AppConfig.kt`** (`com.sledje.agentpro.app`): `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` come from `mobile/local.properties`. A missing key becomes `""`. The build never fails because of a missing key and never prints a value.
3. **Compose Multiplatform resources** in `composeApp/build/generated/agentpro/composeResources/commonMain/`.
   - That directory is a copy of the hand-written `src/commonMain/composeResources/`, plus `values/strings_catalog.xml` and `values-hi/strings_catalog.xml`, which hold every key of `i18n/en.json` and `hi.json` as `cat_<key with . → _>`.
   - It is registered with `compose.resources { customDirectory("commonMain", …) }`, which replaces the default directory, so hand-written strings, fonts and icons keep working.
   - The resource accessors are **public**, in package `com.sledje.agentpro.resources` (`Res`).
   - **Escaping follows the Compose resources format, not Android's.** `&`, `<` and `>` are XML-escaped, `\` is doubled, and newlines and tabs become `\n`/`\t`. **Apostrophes and quotes are written literally**, because Compose doesn't unescape `\'`.
4. **Web files for the in-app preview.** `web/` (without `dist/`, `node_modules/` and `config.js`) and `shared/` are copied to `androidApp/build/generated/agentpro/sitePreviewAssets/`. In the APK they appear as `assets/site/web/…` and `assets/site/shared/…`. `SitePreview` serves them from the reserved host `https://appassets.androidplatform.net/` (ARCHITECTURE.md, Platform seams), because the renderer's root-relative URLs and ES modules don't load from `file://`. The task also writes `site/web/tokens.css` from `design-tokens.json` (`TokensCssGenerator`, the same output as `scripts/gen-tokens.mjs`; Phase 8), since `web/` has no generated `tokens.css`. If `web/` doesn't exist, only `shared/` is copied (and no `tokens.css`). The iOS bundle comes in the iOS phase.
5. **`supabase/migrations/<yyyyMMddHHmmss>_catalog_sync.sql`.** Its exact contents are the contract in BACKEND.md, "catalog_sync contract":
   - the 13 CHECK constraints (drop if exists, then add): six from catalog lists on `enquiries`, `policies` and `site_domains`, plus `sites.template_key` (the keys of `templates/index.json`), `sites.accent` (`accentSwatches`), `sites.locale` and `profiles.preferred_language` (`locales`), and the three privacy ones, `consents_notice_key_check` (`privacyNotices`), `data_requests_kind_check` (`dataRequestKinds`) and `data_requests_status_check` (`dataRequestStatuses`). The privacy CHECKs are emitted only when the catalog has those lists; the tables come from `20261016220600_privacy.sql`, which sorts before the catalog_sync that adds them;
   - the functions `catalog_interest_keys()`, `catalog_interest_keys_all()`, `catalog_premium_mode_months(text)`, `catalog_reserved_slugs()`, `site_schema()`, `catalog_locales()` and `catalog_default_locale()`; from `templateFamilies`, `catalog_template_families()` (text[], active keys in order) and `catalog_template_family(p_template_key text)` (text, null for an unknown key); from `publicTextRules`, `catalog_blocked_terms()` (text[]); from offer-schema.json and the offers lists, `offer_schema()`, `catalog_offer_cta_kinds()`, `catalog_offer_cta_kinds_agent()`, `catalog_membership_reject_reasons()` and `catalog_offer_limits()`; from `insuranceTypes`, `catalog_insurance_types()` (text[], active keys in `order`, DECISIONS #89); and the CHECKs on `offers.status`/`cta_kind`, `admins.role` and `company_memberships.status`/`reject_reason` (tables from the 8b-2 `*_companies_offers.sql`, which sorts first; DECISIONS #72); and, from the privacy lists, `catalog_privacy_notice(text)`, `catalog_privacy_notice_hash(text, integer, text)`, `catalog_privacy_notice_locale(text, integer, text)`, `catalog_data_request_kinds()` and `catalog_data_request_statuses()`, each followed by `revoke all … from public, anon, authenticated`. The notice hash table holds **every version in the lockfile** plus the current ones, so consents given to an older text stay verifiable;
   - the `app_config` upsert of `flags` (defaults only), `pricing`, `retention` and, when they exist, `portal` (from `catalog.portal`) and `customer_signup` (from `catalog.customerSignup`). It never writes `flag_overrides`.

   The rules for writing it:
   - **It's written only once schema migrations exist** (DECISIONS #23): `supabase/migrations/` must contain at least one `*.sql` that isn't `*_catalog_sync.sql`. Until then the task logs "skipped" and writes nothing.
   - **It's written only when the catalog hash changes.** The hash is `sha256` over the canonical (key-sorted, compact) `catalog.json`, `site-schema.json`, `templates/index.json` and, when it exists, `offer-schema.json`: the files whose contents end up in the migration, so whitespace edits don't count. When the catalog has `privacyNotices`, the canonical JSON of the current notice hashes (`{key: {version: {locale: hash}}}`) is appended, so a notice text edit (which lives in `i18n/`) also produces a new migration; other i18n edits don't. It's stored on line 3 as `-- catalog-hash: sha256:<hex>`.
   - **The timestamp is UTC now,** or one second after the newest existing migration if that is later, so the file always sorts last.
   - **Deprecated keys** stay valid in the CHECK constraints. `catalog_interest_keys()` lists only active interests (for `submit_enquiry`), and `catalog_interest_keys_all()` lists them all (for `publish_site`, so existing content still publishes).
   - **Templates have no lockfile or `deprecated` flag.** Removing a template key that a site still uses makes the new `catalog_sync` fail to apply, so keep the key until no site uses it.
   - `site_schema()` embeds the file verbatim, with dollar-quote tags that can't collide with its content.

6. **`shared/generated/privacy-notices.json`** (committed; written only by `generateShared`). The web renderer fetches it as `/shared/generated/privacy-notices.json` (it's inside the `shared/` copy that `scripts/build-web.sh` and the SitePreview task already make), shows the notice from `shared/i18n` by `textKeys`, and sends `{notice_key, version, hash}` with a consent:
   ```json
   { "comment": "GENERATED …", "canonicalization": 1,
     "releaseBlockers": ["{EMAIL_PROVIDER}", "{GRIEVANCE_EMAIL}", "{GRIEVANCE_OFFICER_NAME}", "{GRIEVANCE_PHONE}"],
     "notices": { "enquiry_form": { "version": 1, "labelKey": "privacy.enquiry_form.title",
                                    "textKeys": ["privacy.enquiry_form.consent", "…"], "hash": { "en": "<64 hex>", "hi": "<64 hex>" } }, "…": {} } }
   ```
   Only active (non-deprecated) notices are listed. `releaseBlockers` lists the upper-case placeholders still in any i18n value, so a production web build can refuse to build while it isn't empty.

**Keys lockfile: `mobile/build-logic/catalog-keys.lock.json`** (committed). It lists every catalog key ever released, per list, and under `privacyNoticeHashes` the hash of every privacy notice version ever generated (`{key: {version: {en, hi}}}`).
- `generateShared` only ever adds keys to it.
- `verifyShared` fails when a locked key disappears from `catalog.json` (keep it and mark it `"deprecated": true`).
- It also fails when a catalog key isn't locked yet. Run `generateShared` and commit the lockfile.
- For notices, `generateShared` adds a version the first time it sees it and **never overwrites a locked version**. `verifyShared` fails when a notice's current text no longer matches its locked hash (give it a new `version`), when its version isn't locked yet, or when its version is lower than the newest locked one. Before a version has ever been deployed or shipped, its lock entry may be deleted by hand and regenerated; after that, never.
- It lives in `build-logic/` because `shared/` holds only the hand-edited definitions (plus the one generated JSON above).

`./gradlew verifyShared` (`:composeApp:verifyShared`) runs as part of `check`. It lists **every** violation, then fails the build if:
- the six files are missing or not valid JSON (or `offer-schema.json` / `site-seed.json`, when they exist, aren't valid JSON), or a required catalog list (including `locales`), `planSuggestions` or `pricing` is missing, or `templates/index.json` has no templates;
- `reservedSlugs` is missing, isn't an array of lowercase slugs (`a-z`, `0-9`, `-`), or has a duplicate;
- a `labelKey`, `nameKey`, `descriptionKey` or `defaultKey` is missing from `en` or `hi`. It also fails if `en` and `hi` have different key sets, a value is empty, or the placeholders (`{name}`) differ;
- a tone isn't a `design-tokens.json` tone, or an icon has no `composeResources/drawable/ic_<name>.xml` (catalog lists other than `accentSwatches` and `locales`, schema sections and option icons);
- a key is duplicated (catalog lists, sections, fields, options, templates), was removed, or isn't in the lockfile yet;
- the latest `catalog_sync` migration hash doesn't match `shared/`, or schema migrations exist with no `catalog_sync`. This check is skipped while there are no schema migrations;
- a swatch's contrast with white is below 4.5;
- the `text` sizes aren't exactly `round(base × ratio^n)`, there are more than 4 sizes, or `space` doesn't have exactly 3 values;
- a schema field has an unknown type, a select has no options, a list has no `itemFields`, or `optionsFrom` names no catalog list; or a template names an unknown section or accent;
- a field rule is misused: `generated` other than `"uuid"` or not on a text field, `equals` other than `true` or not on a toggle, `maxDaysAhead` not a whole number ≥ 1 on a date, any `requiredIf` in the site schema, an `offerRef`/`offerList` inside a list, or an `offerList` without `maxItems`;
- offers (when present): an `offerCtaKinds` entry without a boolean `agentAllowed` (or no active agent kind); an `insuranceTypes` list with no active entry (memberships need at least one type); a `catalog.offers` value that isn't a whole number ≥ 1 (or, for `imageAspect`, like `16:9`); the site schema exceeding those limits; or `offer-schema.json` with a bad `version`, an empty `localized`/`fields`, a duplicated key, a localized field that isn't text/multiline or has no `maxLength`, an unknown type, a select without a catalog `optionsFrom`, a bad `requiredIf`, an image aspect other than `imageAspect`, or a `labelKey` missing from `en`/`hi`;
- template families: with `templateFamilies`, a template without a known `family` or an active family without a template; without it, any `family`;
- a catalog entry's `image` isn't a base name (`^[a-z0-9-]+$`; the files aren't checked);
- `offer-schema.json`: `adminOnly` that isn't a boolean, or on a localized field;
- `publicTextRules.blockedTerms` empty, not strings, not lowercase/trimmed/single-spaced/NFC, duplicated or a single generic word; or a `site.*`/`seed.*` i18n value containing a blocked term;
- `site-seed.json` `insuranceTypes` (DECISIONS #93): an active insurance type with no seed interest; a seed interest that is unknown, deprecated, listed twice or of another insurance type; a seed interest without a service description (or a description that isn't `variants`, or fails the text rules for `services.items.description`); an empty `whenNone` or one naming an inactive type; no `mixes`, a duplicated mix, an `onlyTypes` naming an inactive type, or a mix other than the last without `onlyTypes` (or the last with one); a `byMix` that doesn't name exactly the declared mixes, or appears without the block; a `fromInsuranceTypes` without the block or on a field it doesn't fit; a catalog interest without `insuranceType` (once any has one or the block exists) or with one that isn't an `insuranceTypes` key;
- `site-seed.json`: a bad `version`, a placeholder that isn't a profile field, a `requiredPlaceholders` entry that isn't a placeholder, a hidden section that is unknown or pinned, a `visibleOnlyInFamily` naming an unknown section or family, an unknown section or field, a field without exactly one valid form, `variants` on a non-text field or `items` on a non-list field, more items than `maxItems` or an item missing a required item field, a fixed value that isn't valid for the field (option keys, toggle, number), a `byLocale` without every active locale, a text key not starting with `seed.` or missing in a locale, an unknown placeholder, an optional placeholder (`{city}`, `{designation}`) without a fallback, a fallback using one, or a text longer than its field's `maxLength` once filled with the sample values ("Ramesh Kumar Sharma", "Ramesh", "Thiruvananthapuram", "Insurance Advisor · Club Member") in any locale;
- a hand-written string in `composeResources/values/` is missing from `values-hi/`, or uses the reserved `cat_` prefix;
- `catalog.customerSignup` (`checkCustomerSignup`): not an object, an unknown or missing key, or a value that isn't a whole number > 0;
- `catalog.portal` (`checkPortal`): not an object, an unknown or missing key, a value that isn't a whole number > 0, `hardLockAfter` not greater than `maxFailedAttempts`, a bad `remindDaysBefore` list, or a `blockedPins` list that is empty, has duplicates or has an entry that is not exactly `pinLength` digits;
- a privacy notice has no whole `version` ≥ 1, an empty or duplicated `textKeys` list, a text key missing from `en` or `hi` (reported with the i18n rules), a text that changed since its version was locked, an unlocked version or a version lower than the newest locked one; `shared/generated/privacy-notices.json` is missing or stale; or `retention` isn't an object of whole numbers ≥ 0 (or non-empty lists of them);
- Kotlin in `composeApp/src` or `androidApp/src`, outside `core/designsystem`, contains a raw `<number>.sp` anywhere, or a raw `<number>.dp` inside `padding(`, `spacedBy(`, `Spacer(`, `height(` or `width(`.
  - `0.dp` is allowed, and so are other calls such as `size(48.dp)`.
  - Comments and strings are ignored.
  - Each problem is reported as `file:line:col`.

### Release gate: `verifyReleaseReady`
`./gradlew verifyReleaseReady` fails while **any** value in `i18n/en.json` or `hi.json` still contains an upper-case placeholder (`{GRIEVANCE_OFFICER_NAME}`, `{GRIEVANCE_EMAIL}`, `{GRIEVANCE_PHONE}`, `{EMAIL_PROVIDER}`, or any other `{[A-Z][A-Z0-9_]*}`), naming each key. It is deliberately **not** part of `verifyShared`/`check`, so debug builds, tests and CI checks keep passing while the grievance officer isn't chosen (PRIVACY.md, Contact details). Android release builds depend on it (`pre*ReleaseBuild`), and the web build reads the same list from `privacy-notices.json` (`releaseBlockers`) — `scripts/build-web.sh` should refuse a non-dev build while it's non-empty (owned by the web work package; until then, run `verifyReleaseReady` before any deploy).

## Recipes
**Add an enquiry status, interest, source, policy status or premium mode**
1. Add the entry to the right list in `catalog.json` with a new `key`.
2. Add its `labelKey` to `i18n/en.json` and `i18n/hi.json`.
3. Run `./gradlew generateShared`. A new `catalog_sync` migration appears (once schema migrations exist), and the new key is added to `mobile/build-logic/catalog-keys.lock.json`. Commit both.
4. Apply the migration to Supabase.
5. Check whether any `when` branches need handling; the compiler flags exhaustive `when` expressions.
6. Update this doc's tables and CHANGELOG.md.

**Change a privacy notice's text**
1. Edit the `privacy.<notice>.*` values in **both** `i18n/en.json` and `hi.json` (keep placeholders identical), and add or remove keys in its `textKeys` if the structure changed.
2. Increase the notice's `version` in `catalog.json` by 1 (`verifyShared` fails if you forget).
3. Run `./gradlew generateShared`: the lockfile gains the new version's hashes, `shared/generated/privacy-notices.json` and a new `catalog_sync` migration are written. Commit all three and apply the migration.
4. Clients send the new `{notice_key, version, hash}` automatically; the app shows the attestation again where the design says "shown again when the version changes".
5. Update PRIVACY.md if the wording there changed, and CHANGELOG.md.

**Add a website section or field**
1. Edit `site-schema.json` and add its i18n keys.
2. Render it in the shared section partial `web/sections/<key>.js`, so every template picks it up.
3. The app's section editor needs no change, because `FormRenderer` renders it from the schema.
4. Update this doc and CHANGELOG.md.

**Add a template**
1. Add `web/templates/<key>/` containing `render.js`, `style.css` and `preview.webp`.
2. Register it in `templates/index.json` with its `family` and section order, and add its i18n keys.
3. Run `./gradlew generateShared` and apply the new `catalog_sync` migration: the `sites.template_key` CHECK comes from this file.
4. Update WEB_RENDERER.md.

**Add a feature flag or feature**
1. Add the flag to `catalog.features`.
2. Create `feature/<name>/<Name>Feature.kt` and add it to `FeatureRegistry.all`.
3. Update ARCHITECTURE.md and PRODUCT.md.
