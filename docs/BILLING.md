# Billing — the Agent PRO service plan and payments

This doc covers two things:
1. the **Agent PRO service plan** (₹499 for the first year, ₹299 to renew each year), and
2. the **shared payment pipeline**, which the plan and custom domains (DOMAINS.md) both use.

All prices and periods are defined once, in `shared/catalog.json` → `pricing.service` (see SHARED_DEFINITIONS.md). They're server-authoritative.

## Service plan
| Item | Value (catalog key) |
|---|---|
| Free trial | **14 days** of full access from sign-up (`trialDays`). No card needed |
| First year | **₹499 incl. GST** (`purchaseInr`) |
| Renewal | **₹299 incl. GST per year** (`renewalInr`) |
| Renew window | Renew is enabled from 60 days before the period ends (`renewWindowDays`). An early renewal adds a year to the **current end date**, so no days are lost |
| Paying during the trial | The paid year starts **when the trial ends**, so no trial days are lost |
| Grace period | **15 days** after the period ends (`graceDays`), with full access and a renew banner |
| Reminders | 30, 7 and 1 days before the end (`reminderDays`), then on days 1, 7 and 14 of grace (`graceReminderDays`) |
| Renewal method | Manual in v1. The agent taps **Renew for ₹299**; there's no auto-charge |

**What the plan includes:** everything in the app (website, enquiries, customers, policies, dues, insights, reminders, export). It also makes the agent **eligible** for a custom domain, which is a separate charge (DOMAINS.md).

## Entitlement states
| State | When | App | Public site | Custom domain |
|---|---|---|---|---|
| `trial` | Sign-up until `trial_ends_at` | Full access | Live | Not available (needs a paid plan) |
| `active` | Paid, before `current_period_end` | Full access | Live | Available |
| `grace` | Up to 15 days after the period ends (or after the trial if they never paid) | Full access, plus a renew banner | Live | Keeps serving |
| `lapsed` | Grace is over | **Read-only:** viewing, export, renewing, settings and sign-out work; creating, editing and publishing are disabled | **Offline:** "This page isn't available" | Not served (stays registered until its own expiry) |

- **The state is derived, never stored.** The SQL function `public.entitlement(agent_id)` works it out from the `subscriptions` dates, so it can't drift.
- **Data is never deleted on lapse.** Paying brings everything back instantly. The retention policy for long-lapsed accounts is an open question.
- **Catalog:** `planStatuses` holds the labels and tones (trial = info, active = success, grace = warning, lapsed = danger).

### Where it's enforced (server first, the UI only reflects it)
- **RLS.** Insert and update policies on agent tables add `and public.can_write()`, which is true in `trial`, `active` and `grace`. Select policies are unchanged, so a lapsed agent can still read and export.
- **`get_public_site*` and `submit_enquiry`** return "unavailable" when the owner is `lapsed`.
- **`publish_site`** and the domain functions check entitlement. Buying or connecting a domain requires `active` or `grace`.
- **App.**
  - `EntitlementRepository` exposes the state, cached in Room and refreshed on start and after payment.
  - `FeatureGate` turns edit actions into a read-only explanation with **Renew**.
  - Workers keep running reminders but skip actions the agent can't take.

## Payment pipeline (shared by the plan and domains)
```
App: PaymentLauncher ──JWT──▶ Edge Function `billing`
   1. POST billing/create-order {kind, ...}      → server computes amount from app_config.pricing
                                                   (or a locked domain quote), inserts orders + payments,
                                                   creates Razorpay order → {razorpayOrderId, keyId, amountPaise, prefill}
   2. Razorpay Checkout (UPI, cards, netbanking, wallets) → {payment_id, order_id, signature}
   3. POST billing/verify                         → HMAC-SHA256(order_id|payment_id, key_secret) check
                                                   → payments.captured → orders.paid → fulfil(kind)
Razorpay ──webhook──▶ `razorpay-webhook` (signature-verified): same capture + fulfil, idempotent
```
| Order kind | Amount | Fulfilment |
|---|---|---|
| `service_purchase` | `pricing.service.purchaseInr` | Set `current_period_start` and `current_period_end` (from the later of now and the trial end, plus 1 year) |
| `service_renewal` | `pricing.service.renewalInr` | `current_period_end += 1 year` (from the later of now and the current end) |
| `domain_purchase` | Locked quote total (registrar price + fee) | Register the domain, set DNS, attach it to hosting (DOMAINS.md) |
| `domain_renewal` | Locked quote total | Renew at the registrar, extend `expires_at` |

### Rules
- **The client never sends or decides an amount.** It sends the `kind` and, for domains, a `quote_id`.
- **Only the service role** (Edge Functions) writes `orders`, `payments`, `subscriptions` and `site_domains`. Agents can read their own rows.
- **Idempotency.**
  - `payments.provider_order_id` and `provider_payment_id` are unique.
  - Fulfilment uses the order ID as its idempotency key, so verify and the webhook can both run safely.
- **Order status:** `payment_pending` → `paid` → `fulfilling` → `fulfilled`. The failure path is `failed` → `refunded`, and unpaid orders become `cancelled` after 30 minutes (cron).
  - Domain orders also expose finer steps (see DOMAINS.md).
- **Refunds.** If fulfilment fails after capture and can't be retried, the server issues a full refund automatically through `PaymentProvider.refund`.
- **Receipts.** Each captured payment gets a `receipt_no` (`APRO-YYYY-NNNNNN`), listed in **Plan & billing → Receipts**, rendered as a PDF and shareable. They aren't GST tax invoices until the GSTIN is configured (open question).

### Provider interface
```ts
interface PaymentProvider {         // impl: Razorpay. Google Play Billing can be added behind this (see Compliance)
  createOrder(amountPaise: number, receipt: string, notes: object): Promise<{ orderId: string }>
  verifySignature(orderId: string, paymentId: string, signature: string): boolean
  verifyWebhook(body: string, signature: string): boolean
  refund(paymentId: string, amountPaise: number): Promise<{ refundId: string }>
}
```

## App (`feature/billing/`)
- **`BillingFeature`** supplies:
  - the Settings entry **Plan & billing**,
  - a Home row during the last 5 days of the trial or during grace,
  - the lapsed screen,
  - `PaymentLauncher`, which wraps `com.razorpay:checkout`, and
  - `EntitlementRepository` and `FeatureGate`.
- The screens are specified in UX.md: Plan & billing, Checkout, Payment result, Receipts, and the Trial-ended / Lapsed screen.

## Secrets (Edge Function secrets only)
`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`.
- The app receives the public key ID from `create-order`.
- Use Razorpay **test mode** until launch.

## Compliance and business prerequisites
- ❗ **Google Play payments policy.** Selling the app's own service (a digital subscription or plan) inside an Android app normally requires **Google Play Billing**. India has a user-choice / alternative billing programme that may allow Razorpay alongside it.
  - Confirm the exact current policy before release.
  - `PaymentProvider` lets a `PlayBillingProvider` take over the service plan if needed. It would map to a one-time product per year, matching the manual renewal model.
- ❗ **iOS (later phase).** Apple requires **in-app purchase (StoreKit)** for digital services sold inside the app. The iOS `PaymentLauncher` will need StoreKit for the plan, with server-side receipt validation feeding the same `orders` and `subscriptions` tables, unless the plan is sold only outside the app. Decide this in the iOS phase.
- **GST.** Prices include GST. Tax invoices need the GSTIN, invoice numbering and the place of supply.
- **Razorpay:** account, KYC and business activation; a webhook pointing at `razorpay-webhook`.
- **Pages:** the plan terms (trial, renewal, grace, refunds) and the privacy policy, linked from Checkout and Settings.
