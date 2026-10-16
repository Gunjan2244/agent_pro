# Custom domains (v1 feature, extra cost)

An agent on a **paid plan** (`active` or `grace`; see BILLING.md) can buy a custom domain in the app, or connect a domain they already own. Their site is then served there with HTTPS.

The domain is an **additional charge** on top of the ₹499/₹299 service plan: **the registrar's live price plus a fixed service fee**. Payment goes through the shared pipeline in BILLING.md.

## Pricing (catalog → `pricing.domain`)
| Key | Default | Meaning |
|---|---|---|
| `source` | `registrar_live` | The price is fetched from the registrar API at search time; it's never hard-coded |
| `serviceFeeInr` | **99** (needs your confirmation) | Fixed fee added to each purchase and each renewal, incl. GST |
| `feeAppliesTo` | `["purchase", "renewal"]` | Which charges include the fee |
| `connectInr` | 0 | Connecting your own domain is free, but still needs a paid plan |
| `allowedTlds` | `[".in", ".co.in", ".com"]` | Only these are offered |
| `quoteValidMinutes` | 15 | How long a quoted price is locked for checkout |
| `renewWindowDays` | 60 | Renew is enabled this many days before expiry |
| `reminderDays` | `[30, 7, 1]` | Renewal reminders before expiry |

**What the agent pays:** **Total = registrar price (in ₹, including any registrar tax) + service fee.**
- If the registrar quotes in another currency, the adapter converts it with a stored rate and rounds **up** to the nearest rupee. The rate source is recorded on the quote.
- Every quote is stored in `domain_quotes` and locked for 15 minutes. `billing/create-order` accepts only a valid, unexpired `quote_id` belonging to the agent. The client never sends an amount.
- At purchase, the agent sees the **renewal estimate**: "Renews at about ₹{registrar renewal} + ₹99 / year (set by the registrar)". Renewal is re-quoted live at renewal time.

## Architecture
```
App ──JWT──▶ Edge Function `domains`   search (live quotes) · quote · connect · status
    ──JWT──▶ Edge Function `billing`   create-order {kind: domain_purchase|domain_renewal, quote_id} · verify
Razorpay ──▶ `razorpay-webhook` ──▶ fulfil(domain_*)
                                     ├─ Registrar: register / renew / set DNS  (idempotency key = order id)
                                     ├─ Cloudflare: create custom hostname on the platform zone (auto SSL)
                                     └─ DB: orders, site_domains (service role)
pg_cron ──▶ `cron`  every 10 min: DNS/SSL status · daily: expiry reminders, status changes
```
```ts
interface Registrar {               // first impl: TBD (ResellerClub or another) — open question
  check(domains: string[]): Promise<{ domain: string; available: boolean;
                                       priceInr: number; renewPriceInr: number; currency: string }[]>
  register(domain: string, years: 1, contact: RegistrantContact, idempotencyKey: string): Promise<{ ref: string; expiresAt: string }>
  renew(domain: string, years: 1, idempotencyKey: string): Promise<{ expiresAt: string }>
  setDns(domain: string, records: DnsRecord[]): Promise<void>
}
interface HostingAttach {           // impl: Cloudflare for SaaS custom hostnames (DECISIONS #59)
  addDomain(hostname: string): Promise<{ verification: DnsRecord[] }>   // POST /zones/{zone}/custom_hostnames; returns the CNAME to our fallback origin + any TXT validation records
  status(hostname: string): Promise<'pending_dns' | 'verifying' | 'active' | 'failed'>
}
```

## Flows
### Buy
1. **Search.** `POST domains/search {query}` (paid plan required):
   - builds name-based suggestions,
   - calls `Registrar.check` for the `allowedTlds`,
   - adds `serviceFeeInr`,
   - stores a `domain_quotes` row for each available result,
   - and returns `{domain, available, registrarInr, feeInr, totalInr, renewEstimateInr, quoteId, expiresAt}`.
2. **Checkout.** The agent picks a result and enters the registrant details, saved to `registrant_contacts`. Then `billing/create-order {kind: "domain_purchase", quoteId}` re-checks availability; if the registrar price has changed, it returns a **new quote** for the agent to confirm.
3. **Pay and verify,** as described in BILLING.md.
4. **Fulfil.** Each step updates `orders.step`, which the app follows over realtime:
   - `registering`: `Registrar.register`.
   - `configuring`: `Registrar.setDns` (a CNAME to our Cloudflare fallback origin), then `HostingAttach.addDomain`.
   - `ssl_pending`: the cron job polls `HostingAttach.status`.
   - `live`: upsert `site_domains` (`custom`, `active`, `expires_at`, `purchased_in_app = true`), set `sites.primary_domain_id`, and notify the agent.
5. **Failure.**
   - If registration fails, the server sends an **automatic full refund** (price plus fee) and notifies the agent.
   - If DNS or SSL fails, it retries for 24h and then flags the order for manual support. The domain is already bought by then, so there's no refund.

### Renew
1. **Reminders.** The cron job sets `renew_soon` at `reminderDays` before expiry, and the app notifies the agent.
2. **Pay.** The agent opens Domain details, where a live quote is fetched (registrar renewal price plus fee), and taps **Renew for ₹{total}**. This creates a `domain_renewal` order, followed by pay, verify, `Registrar.renew`, and `expires_at += 1 year`.
3. **After expiry,** the status becomes `expired` and the host stops resolving; the site stays available on the free link.
   - Renewal stays possible during the registrar's grace period (configured in the adapter). After that the domain is released, with warnings beforehand.
4. **Plan vs domain.** Renewing a domain is allowed even when the plan is `lapsed`, because the domain belongs to the agent. It won't serve the site until the plan is renewed.

### Connect own domain (free, paid plan required)
1. `POST domains/connect {hostname}` calls `HostingAttach.addDomain` and inserts `site_domains` (`custom`, `pending_dns`, `purchased_in_app = false`).
2. It returns the DNS records, which the app shows with copy buttons.
3. The cron job or **Check now** polls the custom hostname (hostname status and certificate status) and maps it to `site_domains.status`: `pending` to `pending_dns`, `pending` with a certificate in validation to `verifying`, `active` with an active certificate to `active`, `blocked`/`moved`/`deleted` or a validation timeout to `failed`.

### Cloudflare setup
- A Cloudflare zone for the platform domain (to be bought), with Cloudflare for SaaS enabled and a **fallback origin** (a proxied DNS record pointing at the `agentpro-sites` Worker). Agents CNAME their hostname to that fallback origin (apex domains use the CNAME-flattening or ALIAS option their DNS host offers; the app shows the exact record).
- Cloudflare issues and renews the certificate automatically (DCV by HTTP once the CNAME resolves).
- Pricing (checked 2026-10-17): the first 100 custom hostnames are free, then $0.10 per hostname per month.
- The registrar stays separate (open question below); Cloudflare is only hosting and certificates.

## Data (columns in BACKEND.md)
- `domain_quotes`: domain, kind (`purchase|renewal`), registrar_price_inr, renew_estimate_inr, service_fee_inr, total_inr, currency_source, expires_at, used_by_order_id.
- `orders` (shared with billing): kind `domain_purchase|domain_renewal`, quote_id, domain, `step` (`registering|configuring|ssl_pending|live`), registrar_ref.
- `site_domains`: hostname, kind, status (`pending_dns|verifying|active|renew_soon|expired|failed`), expires_at, purchased_in_app, provider_ref.
- `registrant_contacts`: the agent as registrant (name, email, phone, address, city, state, PIN). Private.

## App (`feature/domains/`)
`DomainsFeature` supplies the "Link & domain" `websiteHubEntries`. Its screens are Domain search, Checkout (shared with billing), Setup progress, Domain details and Connect domain; see UX.md.
- On a trial or lapsed plan, the Custom domain section shows "Available on the paid plan" with **See plan**.

## Secrets
`REGISTRAR_API_KEY` (plus a reseller ID if needed), `CLOUDFLARE_API_TOKEN` (scoped to custom hostnames on the platform zone), `CLOUDFLARE_ZONE_ID`. Razorpay secrets are in BILLING.md.

## Open questions (also in STATUS.md)
1. **Registrar:** ResellerClub (India, INR, `.in`) vs another. It needs API support for live pricing, register, renew and DNS.
2. **Fee amount:** confirm ₹99, including GST, for both purchase and renewal.
3. **Registrant:** the agent (assumed) vs the business.
4. **Play Billing policy** for domain sales (see BILLING.md, Compliance).
