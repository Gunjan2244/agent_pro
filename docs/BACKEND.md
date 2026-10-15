# Backend — Supabase `agent-pro`

- **Project:** `agent-pro` in ap-south-1 (Mumbai). It's **not created yet**; get the user's cost confirmation first.
- **Migrations** live in `supabase/migrations/`. Files named `*_catalog_sync.sql` are generated from `shared/catalog.json`; never edit them by hand.
- **After every migration:** run `get_advisors` (security and performance), fix what they report, and update this doc.

## Conventions
- **Every agent-owned table has these columns:**
  - `id uuid pk default gen_random_uuid()`
  - `agent_id uuid not null references auth.users on delete cascade`
  - `created_at timestamptz default now()`
  - `updated_at timestamptz` (set by the `set_updated_at` trigger)
  - `deleted_at timestamptz` (soft delete, so incremental sync sees deletions)
- **RLS is on for every table.** The default policy is owner-only: `agent_id = (select auth.uid())` for select, insert, update and delete, and rows with `deleted_at` set are excluded from select.
- **Insert, update and delete policies** on client-writable tables also require `public.can_write()`. That's true when the entitlement is `trial`, `active` or `grace` (BILLING.md). Select policies have no such requirement, so lapsed agents can still read and export.
- **`anon` has no table privileges.** Public access goes only through the RPCs below.
- **Enum-like text columns** get CHECK constraints from the catalog sync migration.
- **Indexes:** `(agent_id, updated_at)` on every table, for sync.

## Tables
| Table | Key columns (beyond the conventions) |
|---|---|
| `profiles` | `id = auth.users.id` (pk); full_name, phone, email, designation, agency_code, branch, city, photo_url, preferred_language. Created by a trigger on signup |
| `sites` | slug (unique, `^[a-z0-9-]{3,30}$`), template_key, accent, locale, draft_content jsonb, published_content jsonb, is_published, published_at, primary_domain_id (fk `site_domains`, nullable). One row per agent; content is capped at 64KB |
| `site_domains` | site_id, hostname (unique, lowercase), kind (`path|subdomain|custom`), status (`pending_dns|verifying|active|renew_soon|expired|failed`), verification jsonb, provider, provider_ref, purchased_in_app, registered_at, expires_at, renewal_price_inr, auto_renew (false in v1) |
| `enquiries` | site_id, name, phone, email, interest, source, message, status, follow_up_at, notes, seen_at, customer_id |
| `customers` | name, phone, email, dob, address, city, nominee, notes, source_enquiry_id |
| `policies` | customer_id, policy_number, plan_name, sum_assured, premium_amount, premium_mode, start_date, next_due_date, status, notes |
| `premium_payments` | policy_id, amount, paid_on, period_due_date (the due date this payment covered, used by Undo) |
| `site_daily_views` | site_id, day, views; unique `(site_id, day)` |
| `subscriptions` | One per agent, created by the signup trigger: plan (`pro`), trial_started_at, trial_ends_at, current_period_start, current_period_end, last_order_id. **No stored status**; `entitlement()` derives it |
| `orders` | kind (`service_purchase|service_renewal|domain_purchase|domain_renewal`), amount_inr, status (`payment_pending|paid|fulfilling|fulfilled|failed|refunded|cancelled`), plus domain fields: quote_id, domain, site_domain_id, step (`registering|configuring|ssl_pending|live`), registrar_ref. Also error and attempts |
| `payments` | order_id (fk `orders`), amount_paise, currency (`INR`), provider (`razorpay`), provider_order_id (unique), provider_payment_id (unique), status (`created|captured|failed|refunded`), refund_id, receipt_no (unique), raw jsonb |
| `domain_quotes` | domain, kind (`purchase|renewal`), registrar_price_inr, renew_estimate_inr, service_fee_inr, total_inr, currency_source, expires_at, used_by_order_id |
| `registrant_contacts` | name, email, phone, address, city, state, pin, country (`IN`); one per agent |
| `app_config` | key (pk), value jsonb. Keys: `flags`, `pricing`, `min_app_version`. Readable by `authenticated` and writable only by the service role |

**Client write rules**
- **Client-writable** (by the owner, through RLS): `profiles`, `sites` (except `published_content`, `published_at` and `primary_domain_id`, which only RPCs set), `enquiries`, `customers`, `policies` and `registrant_contacts`.
- **Read-only for the client:** `subscriptions`, `orders`, `payments`, `domain_quotes`, `site_domains` and `site_daily_views`. These are written only by the service role or by `SECURITY DEFINER` functions.
- **Column-level grants** enforce the protected `sites` columns.

## RPCs
| Function | Caller | Behaviour |
|---|---|---|
| `entitlement(agent_id)` | internal (used by RLS and RPCs) | Returns `trial|active|grace|lapsed` from the `subscriptions` dates and `pricing.service.graceDays` |
| `can_write()` | internal | `entitlement(auth.uid()) in ('trial','active','grace')` |
| `my_entitlement()` | authenticated | `{state, trial_ends_at, current_period_end, grace_ends_at, renew_available, prices}` for the app |
| `get_public_site(slug)` | anon | Returns `{template_key, accent, locale, published_content, profile:{full_name, designation, city, photo_url, phone}}` for a published site whose owner isn't `lapsed`; otherwise null. **No private fields** |
| `get_public_site_by_host(host)` | anon | Same, resolved through `site_domains` where `status in ('active','renew_soon')` |
| `submit_enquiry(slug, host, name, phone, email, interest, message, honeypot)` | anon | Rejects the submission if the owner is `lapsed`. Otherwise validates (lengths, Indian phone, interest in the catalog whitelist). If the honeypot is filled, returns fake success without inserting. Limits each phone to 5 per site per hour. Inserts with `source = 'website'` and `status = 'new'`, then returns `{ok}` |
| `record_site_view(slug, host)` | anon | Upserts `site_daily_views` (+1) |
| `is_slug_available(slug)` | authenticated | Checks format, a reserved-words list, and uniqueness |
| `publish_site()` | authenticated, `can_write()` | Checks required fields against the schema rules in the sync migration, copies draft to published and sets `published_at` |
| `mark_premium_paid(policy_id, amount, paid_on)` | authenticated | In one transaction: inserts a `premium_payments` row and moves `next_due_date` forward by the mode's `months` |
| `undo_premium_payment(payment_id)` | authenticated | Deletes the payment and restores `next_due_date` from `period_due_date` |
| `delete_my_account()` | authenticated | Deletes the agent's storage objects and the auth user (cascading), after checking there's no `orders` row in `paid` or `fulfilling`. Payment records needed for tax are anonymised, not deleted |

All of these are `SECURITY DEFINER` with `set search_path = ''` and fully qualified names. `execute` is revoked from `public` and granted only to the roles listed.

## Edge Functions (see BILLING.md and DOMAINS.md)
| Function | Auth | Purpose |
|---|---|---|
| `billing` | JWT | `create-order` (the service amount comes from `app_config.pricing`; domains need a valid `quote_id`), `verify` (signature check, then fulfilment) |
| `domains` | JWT, paid plan | `search` (live registrar quotes plus fee, stored in `domain_quotes`), `quote` (renewal), `connect`, `status` |
| `razorpay-webhook` | none (verifies the Razorpay signature) | Handles `payment.captured`, `payment.failed` and `refund.processed` idempotently |
| `cron` | service (pg_cron through `net.http_post`) | Every 10 min: domain DNS and SSL status, and cancelling stale unpaid orders. Daily: plan and domain reminders, and domain status changes (`renew_soon`/`expired`) |

The shared modules live in `supabase/functions/_shared/`: `payments/razorpay.ts`, `registrar/*.ts`, `hosting/vercel.ts`, `fulfil.ts`, `pricing.ts` and `entitlement.ts`.

**Secrets** are listed in BILLING.md and DOMAINS.md. They're set through the Supabase secrets tool, and the user enters the values.

## Storage
- **Bucket `site-media`:** public read. Insert, update and delete are allowed only where `(storage.foldername(name))[1] = auth.uid()::text`.
- **Limits:** images only, 2MB maximum per file. The app compresses to WebP at 1024px or less before uploading.

## Realtime
Publication `supabase_realtime` includes `enquiries`, `orders`, `subscriptions` and `site_domains`, and RLS applies to realtime too.

## Auth
- Email and password, with **email confirmation on**.
- The redirect URL for password reset and confirmation is `agentpro://auth-callback`, the app's deep link.
- Password minimum is 8 characters. The site URL is set to the renderer domain.
