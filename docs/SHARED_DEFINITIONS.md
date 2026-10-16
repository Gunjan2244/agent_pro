# Shared definitions (`shared/`)

Every enumerable or configurable thing in the product is defined **once**, in `shared/`. The mobile app (Kotlin Multiplatform), web renderer and database are generated from these files or read them directly.

**Never hard-code these values in Kotlin, JS or SQL.**

## Files
| File | Contains | Consumed by |
|---|---|---|
| `catalog.json` | Enums and lists: `enquiryStatuses`, `interests`, `enquirySources`, `premiumModes`, `policyStatuses`, `dueBuckets`, `planStatuses`, `domainStatuses`, `planSuggestions`, `reservedSlugs`, `accentSwatches`, `locales`, `features`, `privacyNotices`, `dataRequestKinds`, `dataRequestStatuses`, `pricing`, `retention` | Mobile app (generated `Catalog.kt` and `PrivacyNotices.kt` in `commonMain`), web (runtime JSON), SQL (generated CHECK constraints and catalog functions), `submit_enquiry`, `publish_site`, the slug guard, the consent RPCs |
| `site-schema.json` | Website sections and their fields | App (`SiteSchema.kt`, which drives the section editor), web (section order and rendering), `publish_site` validation |
| `design-tokens.json` | Colours (light/dark), tones, chart palette, type, shapes, spacing, motion | App (`Tokens.kt`), web (generated `tokens.css`) |
| `templates/index.json` | Template registry: key, name/description i18n keys, supported sections, default accent, preview image | App (template picker), web (template loader), SQL (the `sites.template_key` CHECK) |
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
- Some lists add extra fields: `terminal` (enquiry and data request statuses), `months` (premium modes), `minDays`/`maxDays` (due buckets; `minDays` is `null` for −∞), `hex` (swatches), `default` (features), `version` and `textKeys` (privacy notices).
- `catalog.json` also has a top-level `"version": 1`. `planSuggestions` and `reservedSlugs` are plain arrays of strings, and `pricing` and `retention` are plain objects.
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
Tone is `neutral` for all. The icons are provisional.

| key | en | hi | icon |
|---|---|---|---|
| `term` | Term insurance | टर्म बीमा | shield (provisional) |
| `endowment` | Endowment | एंडोमेंट | savings (provisional) |
| `money_back` | Money back | मनी बैक | payments (provisional) |
| `child` | Child plan | बाल योजना | child_care (provisional) |
| `pension` | Pension | पेंशन | elderly (provisional) |
| `health` | Health | स्वास्थ्य | health_and_safety (provisional) |
| `ulip` | ULIP | यूलिप | trending_up (provisional) |
| `other` | Other | अन्य | more_horiz (provisional) |

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
| `agent_signup` | Agent, app Create account (checkbox + full notice screen) | Agent PRO | `consent`, `title`, `intro`, `privacy.notice.col_data/col_purpose/col_kept`, then `<row>_data/_purpose/_kept` for the rows `account`, `profile`, `website`, `billing`, `registrant`, `session`, `customers`, then `customers_note`, `lapsed`, `no_ads`, `processors`, `rights`, `withdraw`, `grievance`, `board` | 1 |
| `enquiry_form` | The public, site enquiry form (checkbox) and the site privacy page | The agent | `consent`, `link`, `title`, `what`, `why`, `who`, `how_long`, `rights` | 1 |
| `agent_customer_duty` | Agent, the one-time attestation sheet before the first customer / import / manual enquiry | The agent (their duty) | `title`, `text`, `action` | 1 |
| `guardian_consent` | Agent, customer form when the DOB gives an age under 18 (provisional; counsel) | The agent | `title`, `notice`, `consent` | 1 |

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
  "auditLogMonths": 13, "orphanMediaDays": 30, "endedPolicySuggestDeleteYears": 3
}
```
- ✔ `lapsedAccount`: deleted 12 months after the plan lapses, with notices 30 days, 7 days and **2 days (48 hours)** before.
- ✔ **There is deliberately no enquiry period:** the agent, as the fiduciary, decides when enquiries are deleted (no automatic deletion, no setting). Never add one without a new decision.
- `dataRequests` also carries the request timing (30-day response target, digest reminders at 7 and 21 days, escalation at 30).
- `endedPolicySuggestDeleteYears` only *suggests* deletion (IRDAI record-keeping, counsel).
- Every value is a whole number ≥ 0 or a non-empty list of them (`verifyShared`).

### Planned additions (designed, not built)
None of these are in `shared/` yet; they're added by the work packages in OFFERS.md.
- **Offers (OFFERS.md):** catalog lists `offerStatuses` (`draft`, `live`, `archived`; stored), `offerPhases` (`upcoming`, `running`, `ended`; derived, labels only), `offerCtaKinds` (`enquire`, `call`, `link`, with an `agentAllowed` extra field), `adminRoles` (`owner`, `editor`), `membershipStatuses` (`pending`, `approved`, `rejected`, `revoked`, `withdrawn`; the admin-verified company link) and `membershipRejectReasons`; an `offers` limits object; the feature flag `offers`; a new file **`shared/offer-schema.json`** (the company offer fields, localized per catalog locale), which `catalog_sync` will emit as `offer_schema()` alongside a new `catalog_default_locale()`. **Companies and offers themselves are runtime data in the database**, not definitions: company names are stored per locale in `companies.name`, never as i18n keys.
- **Site schema (OFFERS.md):** `hero.featuredOffer` (new type `offerRef`) and a new `offers` section (`title`, `items` of new type `offerList`, and `custom` offers as a `list`), plus the field rules `generated: "uuid"`, `equals: true`, `maxDaysAhead` and (admin forms only) `requiredIf`.
- **Privacy UI copy (P1-3, P2):** `site.privacy_*` (the site privacy page's headings and request form) and `site.form_message_hint` are added by the web work package; the catalog lists, notices and `retention` above are built (P1-1).

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
**Sections in v1** (in this default order). Every section is `defaultVisible: true`. Values marked (provisional) aren't set by the product docs. Section icons: hero `badge`, about `person`, services `work`, highlights `star`, achievements `trophy`, testimonials `format_quote`, faq `help`, contact `call` (all provisional).

| Section | Fields |
|---|---|
| `hero` (pinned) | headline (required, ≤ 70), subheadline (≤ 160), photo (image, 1:1), ctaLabel (≤ 30, `defaultKey: site.cta_default`) |
| `about` | bio (≤ 600), designation (≤ 60, provisional), yearsExperience (number 0–70, provisional), clientsServed (number 0–1,000,000, provisional), languagesSpoken (multiSelect, options below), showAgencyCode (toggle, default off) |
| `services` | offered (multiSelect from `interests`), items list (≤ 8, provisional): interest (select from `interests`, required), description (≤ 160) |
| `highlights` | items ≤ 4: icon (select from the fixed set below, required), title (required, ≤ 40), text (≤ 120) |
| `achievements` | items ≤ 8: title (required, ≤ 60), year (number 1950–2100, provisional) |
| `testimonials` | items ≤ 6: name (required, ≤ 40), city (≤ 30), quote (required, ≤ 280) |
| `faq` | items ≤ 8: question (required, ≤ 120), answer (required, ≤ 400) |
| `contact` (pinned) | phone (required, provisional), whatsapp, email, officeAddress (≤ 200, provisional), mapLink (url), officeHours (≤ 60, provisional), showEnquiryForm (toggle, default on) |

**Option lists.** A field's `options` is a list of objects. An `optionsFrom` field reads another list instead.
- **Highlight icon set** (provisional): `shield`, `verified_user`, `support_agent`, `schedule`, `handshake`, `savings`, `favorite`, `thumb_up`. Each option is `{ key, icon, labelKey: "highlight_icon.<key>" }`, where `key` equals the icon name.
- **Languages spoken** (provisional): English, Hindi, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi, Odia, Urdu. Each option is `{ key, labelKey: "language.<key>" }`, with keys such as `english` and `hindi`.

**Field types:** `text`, `multiline`, `number`, `phone`, `email`, `url`, `image`, `select`, `multiSelect`, `toggle`, `swatch`, `date`, and `list` (with `itemFields`).

**Field rules:** `required`, `maxLength`, `min`, `max`, `maxItems`, `options` (or `optionsFrom: "catalog.interests"`), `default`/`defaultKey`, `aspect`.

**Stored content** (in `sites.draft_content` and `sites.published_content`):
```json
{ "sectionOrder": ["hero","about","services","testimonials","contact"], "hidden": ["faq"],
  "hero": { "headline": "…", "photo": "https://…/site-media/<agent>/hero.webp" },
  "testimonials": { "items": [ { "name": "…", "quote": "…" } ] } }
```
Content is independent of the template, so switching templates never loses data.

## `templates/index.json`
`{ "version": 1, "templates": [ { key, nameKey, descriptionKey, sections, defaultAccent, preview, order } ] }`. `sections` lists the supported section keys (all 8 for every v1 template), `defaultAccent` is a swatch key and `preview` is `web/templates/<key>/preview.webp` (the images are added in Phase 4).

| key | name (en / hi) | default accent (provisional) |
|---|---|---|
| `classic` | Trust / भरोसा | indigo |
| `modern` | Fresh / ताज़ा | teal |
| `minimal` | Simple / सरल | slate |

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
- Keys take the form `<group>.<key>`, for example `enquiry_status.follow_up`, `section.hero`, `field.headline` and `site.cta_default`.
- Groups: `enquiry_status`, `interest`, `enquiry_source`, `premium_mode`, `policy_status`, `due_bucket`, `plan_status`, `domain_status`, `swatch`, `locale`, `feature`, `section`, `field`, `highlight_icon`, `language`, `template` (`template.<key>.name` and `.description`), `site` (fixed site copy), `data_request_kind`, `data_request_status`, and `privacy` (`privacy.<notice key>.<part>` for each notice's text, `privacy.notice.*` for parts shared by notices). `locale.*` labels the app and site languages (`catalog.locales`); `language.*` is the `languagesSpoken` field's options.
- `i18n/en.json` and `i18n/hi.json` are flat objects with identical key sets, sorted by key. Placeholders such as `{name}`, `{n}` and `{code}` are the same in both.
- **Upper-case placeholders** (`{GRIEVANCE_EMAIL}`, `{EMAIL_PROVIDER}`, …: `{[A-Z][A-Z0-9_]*}`) are never substituted at runtime. They mark text that must be filled in before a release, and `verifyReleaseReady` fails while any remains.
- `site.*` holds the fixed copy the public site uses: the "This page isn't available" message, the enquiry form labels and errors (`site.form_*`), the thank-you and rate-limited messages (`site.form_thank_you`, `site.form_rate_limited`, both take `{name}`), the contact buttons, the footer (`site.footer_advisor` takes `{name}`; `site.footer_made_with`) and `site.cta_default`. The wording of everything under `site.*` other than what WEB_RENDERER.md quotes is provisional, as is the Hindi for strings that this doc doesn't translate.
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
   - **One enum per catalog list:** `EnquiryStatus`, `Interest`, `EnquirySource`, `PremiumMode`, `PolicyStatus`, `DueBucket`, `PlanStatus`, `DomainStatus`, `AccentSwatch`, `AppLocale`, `FeatureId`, `DataRequestKind` and `DataRequestStatus`. `privacyNotices` is the exception: it gets its own file, `PrivacyNotices.kt` (below).
     - The type name is the singular of the list name. Two exceptions: `features` becomes `FeatureId`, and `locales` becomes `AppLocale` so it never clashes with `java.util.Locale` or Compose's `Locale`.
     - A constant is the key in PascalCase, for example `follow_up` → `FollowUp` and `next_3_months` → `Next3Months`.
     - Each entry also carries the list's extra fields, typed from the JSON: `terminal`, `months`, `minDays: Int?`/`maxDays`, `hex` and `default`.
     - Each enum has `fromKey(key)` and `active`, which lists the non-deprecated entries in display order.
     - **Deprecated entries stay in the enum** (`deprecated = true`), so stored values still parse.
   - **Other top-level values:** string arrays become an object with `all` (`PlanSuggestions.all`, `ReservedSlugs.all`). Objects become nested objects of `const val`s and `listOf(...)` (for example `Pricing.Service.purchaseInr`, `Retention.LapsedAccount.noticeDaysBefore`). `version` becomes `Catalog.VERSION`.
   - **Labels and icons** use `Res.string.cat_*` and `Res.drawable.ic_*`.
2. **The other generated Kotlin files:**
   - **`SiteSchema.kt`** (`core.catalog`): `SiteSchema.sections` is a `List<SectionSpec>` of `FieldSpec`s. The hand-written model is in `core/forms/FieldSpec.kt` and has a `FieldType` for each of the 13 field types; `list` is `FieldType.ItemList`. `optionsFrom: "catalog.<list>"` is resolved to that list's active entries as `FieldOption`s, and `defaultKey` becomes `defaultText: StringResource`.
   - **`Tokens.kt`** (`core.designsystem`):
     - `Tokens.ColorLight`/`ColorDark` hold `Color`s, and `Tokens.Tones.of(tone)` returns the tone's colours.
     - `Tokens.Chart` holds the chart colours, and `Tokens.Font` the font families and weights.
     - `Tokens.Text.s|m|l` are `TextToken(size, lineHeight)` in sp. **`text.xl` is web-only and is not generated.**
     - `Tokens.Space` and `Tokens.Shape` are `Dp`, and `Tokens.Motion` is in milliseconds.
   - **`Templates.kt`** (`core.catalog`): `TemplateSpec(key, name: StringResource, description: StringResource, sections, defaultAccent: AccentSwatch, preview, order)` per entry of `templates/index.json`, and `Templates.all` (sorted by `order`) with `fromKey(key)`. The template pickers (onboarding step 2, later Website → Appearance) read it; a `defaultAccent` that isn't an `accentSwatches` key fails generation.
   - **`PrivacyNotices.kt`** (`core.catalog`): `enum class PrivacyNotice(key, label, order, version, texts: List<StringResource>, …) : CatalogEntry` with one constant per notice (`AgentSignup`, `EnquiryForm`, `AgentCustomerDuty`, `GuardianConsent`). `texts` are the `textKeys` resources in display order; `hash(locale: AppLocale)` returns the notice hash for that language; `consent(locale)` returns `NoticeConsent(noticeKey, version, hash)`, the payload of the consent RPCs. `PrivacyNotice.CANONICALIZATION` is the hash rule's number (1).
   - **`AppConfig.kt`** (`com.sledje.agentpro.app`): `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SITE_BASE_URL` come from `mobile/local.properties`. A missing key becomes `""`. The build never fails because of a missing key and never prints a value.
3. **Compose Multiplatform resources** in `composeApp/build/generated/agentpro/composeResources/commonMain/`.
   - That directory is a copy of the hand-written `src/commonMain/composeResources/`, plus `values/strings_catalog.xml` and `values-hi/strings_catalog.xml`, which hold every key of `i18n/en.json` and `hi.json` as `cat_<key with . → _>`.
   - It is registered with `compose.resources { customDirectory("commonMain", …) }`, which replaces the default directory, so hand-written strings, fonts and icons keep working.
   - The resource accessors are **public**, in package `com.sledje.agentpro.resources` (`Res`).
   - **Escaping follows the Compose resources format, not Android's.** `&`, `<` and `>` are XML-escaped, `\` is doubled, and newlines and tabs become `\n`/`\t`. **Apostrophes and quotes are written literally**, because Compose doesn't unescape `\'`.
4. **Web files for the in-app preview.** `web/` (without `dist/`, `node_modules/` and `config.js`) and `shared/` are copied to `androidApp/build/generated/agentpro/sitePreviewAssets/`. In the APK they appear as `assets/site/web/…` and `assets/site/shared/…`. `SitePreview` serves them from the reserved host `https://appassets.androidplatform.net/` (ARCHITECTURE.md, Platform seams), because the renderer's root-relative URLs and ES modules don't load from `file://`. The task also writes `site/web/tokens.css` from `design-tokens.json` (`TokensCssGenerator`, the same output as `scripts/gen-tokens.mjs`; Phase 8), since `web/` has no generated `tokens.css`. If `web/` doesn't exist, only `shared/` is copied (and no `tokens.css`). The iOS bundle comes in the iOS phase.
5. **`supabase/migrations/<yyyyMMddHHmmss>_catalog_sync.sql`.** Its exact contents are the contract in BACKEND.md, "catalog_sync contract":
   - the 10 CHECK constraints (drop if exists, then add): six from catalog lists on `enquiries`, `policies` and `site_domains`, plus `sites.template_key` (the keys of `templates/index.json`), `sites.accent` (`accentSwatches`), and `sites.locale` and `profiles.preferred_language` (`locales`);
   - the functions `catalog_interest_keys()`, `catalog_interest_keys_all()`, `catalog_premium_mode_months(text)`, `catalog_reserved_slugs()` and `site_schema()`, and, from the privacy lists, `catalog_privacy_notice(text)`, `catalog_privacy_notice_hash(text, integer, text)`, `catalog_privacy_notice_locale(text, integer, text)`, `catalog_data_request_kinds()` and `catalog_data_request_statuses()`, each followed by `revoke all … from public, anon, authenticated`. The notice hash table holds **every version in the lockfile** plus the current ones, so consents given to an older text stay verifiable;
   - the `app_config` upsert of `flags` (defaults only), `pricing` and `retention`. It never writes `flag_overrides`.
   - **No CHECKs for the privacy columns yet:** `consents.notice_key`, `data_requests.kind` and `data_requests.status` don't exist until P1-2's migration. P1-2 adds them to the generator's CHECK list (`consents_notice_key_check` from `privacyNotices`, `data_requests_kind_check` from `dataRequestKinds`, `data_requests_status_check` from `dataRequestStatuses`, all keys incl. deprecated) in the same change as the tables, then regenerates (BACKEND.md).

   The rules for writing it:
   - **It's written only once schema migrations exist** (DECISIONS #23): `supabase/migrations/` must contain at least one `*.sql` that isn't `*_catalog_sync.sql`. Until then the task logs "skipped" and writes nothing.
   - **It's written only when the catalog hash changes.** The hash is `sha256` over the canonical (key-sorted, compact) `catalog.json`, `site-schema.json` and `templates/index.json`, the three files whose contents end up in the migration, so whitespace edits don't count. When the catalog has `privacyNotices`, the canonical JSON of the current notice hashes (`{key: {version: {locale: hash}}}`) is appended, so a notice text edit (which lives in `i18n/`) also produces a new migration; other i18n edits don't. It's stored on line 3 as `-- catalog-hash: sha256:<hex>`.
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
- the six files are missing or not valid JSON, or a required catalog list (including `locales`), `planSuggestions` or `pricing` is missing, or `templates/index.json` has no templates;
- `reservedSlugs` is missing, isn't an array of lowercase slugs (`a-z`, `0-9`, `-`), or has a duplicate;
- a `labelKey`, `nameKey`, `descriptionKey` or `defaultKey` is missing from `en` or `hi`. It also fails if `en` and `hi` have different key sets, a value is empty, or the placeholders (`{name}`) differ;
- a tone isn't a `design-tokens.json` tone, or an icon has no `composeResources/drawable/ic_<name>.xml` (catalog lists other than `accentSwatches` and `locales`, schema sections and option icons);
- a key is duplicated (catalog lists, sections, fields, options, templates), was removed, or isn't in the lockfile yet;
- the latest `catalog_sync` migration hash doesn't match `shared/`, or schema migrations exist with no `catalog_sync`. This check is skipped while there are no schema migrations;
- a swatch's contrast with white is below 4.5;
- the `text` sizes aren't exactly `round(base × ratio^n)`, there are more than 4 sizes, or `space` doesn't have exactly 3 values;
- a schema field has an unknown type, a select has no options, a list has no `itemFields`, or `optionsFrom` names no catalog list; or a template names an unknown section or accent;
- a hand-written string in `composeResources/values/` is missing from `values-hi/`, or uses the reserved `cat_` prefix;
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
2. Register it in `templates/index.json` and add its i18n keys.
3. Run `./gradlew generateShared` and apply the new `catalog_sync` migration: the `sites.template_key` CHECK comes from this file.
4. Update WEB_RENDERER.md.

**Add a feature flag or feature**
1. Add the flag to `catalog.features`.
2. Create `feature/<name>/<Name>Feature.kt` and add it to `FeatureRegistry.all`.
3. Update ARCHITECTURE.md and PRODUCT.md.
