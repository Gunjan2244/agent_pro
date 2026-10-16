-- Agent PRO — Phase 3 — schema: tables, constraints, indexes, updated_at triggers.
-- Spec: docs/BACKEND.md (Tables, Conventions). RLS/grants: *_rls.sql. RPCs: *_rpcs.sql.
--
-- Catalog-driven CHECK constraints (enquiries.status/interest/source, policies.premium_mode/status,
-- site_domains.status, sites.template_key/accent/locale, profiles.preferred_language) are NOT defined
-- here: the generated *_catalog_sync.sql adds them as <table>_<column>_check. Never add them by hand.

-- ---------------------------------------------------------------------------
-- Exposure defaults. Supabase grants every new table/function in `public` to anon and
-- authenticated by default; this project opts out so that exposure is always explicit
-- (grants live in *_rls.sql and *_rpcs.sql). service_role keeps its table defaults.
-- ---------------------------------------------------------------------------
alter default privileges for role postgres in schema public
  revoke select, insert, update, delete, truncate, references, trigger on tables from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke usage, select on sequences from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke execute on functions from anon, authenticated;
alter default privileges for role postgres
  revoke execute on functions from public;

-- Internal helpers live in `private`, which the Data API does not expose.
create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- updated_at trigger (set on insert and update so incremental sync sees every row)
-- ---------------------------------------------------------------------------
create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- profiles (id = auth.users.id; created by the signup trigger)
-- ---------------------------------------------------------------------------
create table public.profiles (
  id                 uuid primary key references auth.users (id) on delete cascade,
  full_name          text,
  phone              text,
  email              text,
  designation        text,
  agency_code        text,
  branch             text,
  city               text,
  photo_url          text,
  preferred_language text,                       -- CHECK from catalog_sync (locales); null allowed
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);
create index profiles_updated_at_idx on public.profiles (updated_at);

-- ---------------------------------------------------------------------------
-- sites (one per agent)
-- ---------------------------------------------------------------------------
create table public.sites (
  id                uuid primary key default gen_random_uuid(),
  agent_id          uuid not null references auth.users (id) on delete cascade,
  slug              text not null,
  template_key      text not null,             -- CHECK from catalog_sync (templates/index.json keys)
  accent            text,                      -- CHECK from catalog_sync (accentSwatches keys); null allowed
  locale            text,                      -- CHECK from catalog_sync (locales); null allowed
  draft_content     jsonb not null default '{}'::jsonb,
  published_content jsonb,
  is_published      boolean not null default false,
  published_at      timestamptz,
  primary_domain_id uuid,                      -- fk added after site_domains exists
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  deleted_at        timestamptz,
  constraint sites_agent_id_key unique (agent_id),
  constraint sites_slug_key unique (slug),
  constraint sites_id_agent_id_key unique (id, agent_id),
  constraint sites_slug_format check (slug ~ '^[a-z0-9-]{3,30}$'),
  constraint sites_draft_content_size check (octet_length(draft_content::text) <= 65536),
  constraint sites_published_content_size check (published_content is null or octet_length(published_content::text) <= 65536)
);
create index sites_agent_id_updated_at_idx on public.sites (agent_id, updated_at);

-- ---------------------------------------------------------------------------
-- site_domains (written by service role / Edge Functions only)
-- ---------------------------------------------------------------------------
create table public.site_domains (
  id                uuid primary key default gen_random_uuid(),
  agent_id          uuid not null references auth.users (id) on delete cascade,
  site_id           uuid not null,
  hostname          text not null,
  kind              text not null,
  status            text not null,             -- CHECK from catalog_sync (domainStatuses)
  verification      jsonb,
  provider          text,
  provider_ref      text,
  purchased_in_app  boolean not null default false,
  registered_at     timestamptz,
  expires_at        timestamptz,
  renewal_price_inr integer,
  auto_renew        boolean not null default false,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  deleted_at        timestamptz,
  constraint site_domains_hostname_key unique (hostname),
  constraint site_domains_hostname_lower check (hostname = lower(hostname)),
  constraint site_domains_kind_values check (kind in ('path', 'subdomain', 'custom')),
  constraint site_domains_renewal_price_nonneg check (renewal_price_inr is null or renewal_price_inr >= 0),
  constraint site_domains_site_fk foreign key (site_id, agent_id)
    references public.sites (id, agent_id) on delete cascade
);
create index site_domains_agent_id_updated_at_idx on public.site_domains (agent_id, updated_at);
create index site_domains_site_id_idx on public.site_domains (site_id, agent_id);

alter table public.sites
  add constraint sites_primary_domain_id_fkey foreign key (primary_domain_id)
  references public.site_domains (id) on delete set null;
create index sites_primary_domain_id_idx on public.sites (primary_domain_id);

-- ---------------------------------------------------------------------------
-- customers / enquiries (mutual references; composite FKs keep references inside one agent)
-- ---------------------------------------------------------------------------
create table public.customers (
  id                uuid primary key default gen_random_uuid(),
  agent_id          uuid not null references auth.users (id) on delete cascade,
  name              text not null,
  phone             text,
  email             text,
  dob               date,
  address           text,
  city              text,
  nominee           text,
  notes             text,
  source_enquiry_id uuid,                      -- fk added after enquiries exists
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  deleted_at        timestamptz,
  constraint customers_id_agent_id_key unique (id, agent_id)
);
create index customers_agent_id_updated_at_idx on public.customers (agent_id, updated_at);

create table public.enquiries (
  id           uuid primary key default gen_random_uuid(),
  agent_id     uuid not null references auth.users (id) on delete cascade,
  site_id      uuid,
  name         text not null,
  phone        text,
  email        text,
  interest     text,                           -- CHECK from catalog_sync (interests); null allowed
  source       text not null,                  -- CHECK from catalog_sync (enquirySources)
  message      text,
  status       text not null,                  -- CHECK from catalog_sync (enquiryStatuses)
  follow_up_at timestamptz,
  notes        text,
  seen_at      timestamptz,
  customer_id  uuid,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz,
  constraint enquiries_id_agent_id_key unique (id, agent_id),
  constraint enquiries_site_fk foreign key (site_id, agent_id)
    references public.sites (id, agent_id) on delete set null (site_id),
  constraint enquiries_customer_fk foreign key (customer_id, agent_id)
    references public.customers (id, agent_id) on delete set null (customer_id)
);
create index enquiries_agent_id_updated_at_idx on public.enquiries (agent_id, updated_at);
create index enquiries_site_id_idx on public.enquiries (site_id, agent_id);
create index enquiries_customer_id_idx on public.enquiries (customer_id, agent_id);
-- submit_enquiry rate limit lookup (phone per site per hour)
create index enquiries_site_phone_created_idx on public.enquiries (site_id, phone, created_at);

alter table public.customers
  add constraint customers_source_enquiry_fk foreign key (source_enquiry_id, agent_id)
  references public.enquiries (id, agent_id) on delete set null (source_enquiry_id);
create index customers_source_enquiry_id_idx on public.customers (source_enquiry_id, agent_id);

-- ---------------------------------------------------------------------------
-- policies / premium_payments
-- ---------------------------------------------------------------------------
create table public.policies (
  id             uuid primary key default gen_random_uuid(),
  agent_id       uuid not null references auth.users (id) on delete cascade,
  customer_id    uuid not null,
  policy_number  text,
  plan_name      text,
  sum_assured    numeric(14, 2),
  premium_amount numeric(12, 2),
  premium_mode   text not null,                -- CHECK from catalog_sync (premiumModes)
  start_date     date,
  next_due_date  date,
  status         text not null,                -- CHECK from catalog_sync (policyStatuses)
  notes          text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  deleted_at     timestamptz,
  constraint policies_id_agent_id_key unique (id, agent_id),
  constraint policies_amounts_nonneg check (coalesce(sum_assured, 0) >= 0 and coalesce(premium_amount, 0) >= 0),
  constraint policies_customer_fk foreign key (customer_id, agent_id)
    references public.customers (id, agent_id) on delete cascade
);
create index policies_agent_id_updated_at_idx on public.policies (agent_id, updated_at);
create index policies_customer_id_idx on public.policies (customer_id, agent_id);

create table public.premium_payments (
  id              uuid primary key default gen_random_uuid(),
  agent_id        uuid not null references auth.users (id) on delete cascade,
  policy_id       uuid not null,
  amount          numeric(12, 2) not null,
  paid_on         date not null,
  period_due_date date not null,               -- the due date this payment covered (used by Undo)
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  deleted_at      timestamptz,
  constraint premium_payments_amount_positive check (amount > 0),
  constraint premium_payments_policy_fk foreign key (policy_id, agent_id)
    references public.policies (id, agent_id) on delete cascade
);
create index premium_payments_agent_id_updated_at_idx on public.premium_payments (agent_id, updated_at);
create index premium_payments_policy_id_idx on public.premium_payments (policy_id, agent_id);

-- ---------------------------------------------------------------------------
-- site_daily_views (written only by record_site_view)
-- ---------------------------------------------------------------------------
create table public.site_daily_views (
  id         uuid primary key default gen_random_uuid(),
  agent_id   uuid not null references auth.users (id) on delete cascade,
  site_id    uuid not null,
  day        date not null,                    -- Asia/Kolkata calendar day
  views      integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint site_daily_views_site_id_day_key unique (site_id, day),
  constraint site_daily_views_views_nonneg check (views >= 0),
  constraint site_daily_views_site_fk foreign key (site_id, agent_id)
    references public.sites (id, agent_id) on delete cascade
);
create index site_daily_views_agent_id_updated_at_idx on public.site_daily_views (agent_id, updated_at);
create index site_daily_views_site_agent_idx on public.site_daily_views (site_id, agent_id);

-- ---------------------------------------------------------------------------
-- Billing / domains (written only by the service role or SECURITY DEFINER functions).
-- orders and payments keep their rows when the account is deleted (tax records):
-- agent_id is nullable and set to null on delete (anonymised).
-- Status/kind/step values below are internal (BILLING.md, DOMAINS.md), not catalog lists.
-- ---------------------------------------------------------------------------
create table public.domain_quotes (
  id                  uuid primary key default gen_random_uuid(),
  agent_id            uuid not null references auth.users (id) on delete cascade,
  domain              text not null,
  kind                text not null,
  registrar_price_inr integer not null,
  renew_estimate_inr  integer,
  service_fee_inr     integer not null,
  total_inr           integer not null,
  currency_source     text,
  expires_at          timestamptz not null,
  used_by_order_id    uuid,                    -- fk added after orders exists
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  deleted_at          timestamptz,
  constraint domain_quotes_kind_values check (kind in ('purchase', 'renewal')),
  constraint domain_quotes_amounts_nonneg check (registrar_price_inr >= 0 and service_fee_inr >= 0 and total_inr >= 0),
  constraint domain_quotes_domain_lower check (domain = lower(domain))
);
create index domain_quotes_agent_id_updated_at_idx on public.domain_quotes (agent_id, updated_at);

create table public.orders (
  id             uuid primary key default gen_random_uuid(),
  agent_id       uuid references auth.users (id) on delete set null,
  kind           text not null,
  amount_inr     integer not null,
  status         text not null default 'payment_pending',
  quote_id       uuid references public.domain_quotes (id) on delete set null,
  domain         text,
  site_domain_id uuid references public.site_domains (id) on delete set null,
  step           text,
  registrar_ref  text,
  error          text,
  attempts       integer not null default 0,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  deleted_at     timestamptz,
  constraint orders_kind_values check (kind in ('service_purchase', 'service_renewal', 'domain_purchase', 'domain_renewal')),
  constraint orders_status_values check (status in ('payment_pending', 'paid', 'fulfilling', 'fulfilled', 'failed', 'refunded', 'cancelled')),
  constraint orders_step_values check (step is null or step in ('registering', 'configuring', 'ssl_pending', 'live')),
  constraint orders_amount_positive check (amount_inr > 0),
  constraint orders_attempts_nonneg check (attempts >= 0)
);
create index orders_agent_id_updated_at_idx on public.orders (agent_id, updated_at);
create index orders_quote_id_idx on public.orders (quote_id);
create index orders_site_domain_id_idx on public.orders (site_domain_id);

alter table public.domain_quotes
  add constraint domain_quotes_used_by_order_id_fkey foreign key (used_by_order_id)
  references public.orders (id) on delete set null;
create index domain_quotes_used_by_order_id_idx on public.domain_quotes (used_by_order_id);

create table public.payments (
  id                  uuid primary key default gen_random_uuid(),
  agent_id            uuid references auth.users (id) on delete set null,
  order_id            uuid not null references public.orders (id) on delete restrict,
  amount_paise        integer not null,
  currency            text not null default 'INR',
  provider            text not null default 'razorpay',
  provider_order_id   text,
  provider_payment_id text,
  status              text not null default 'created',
  refund_id           text,
  receipt_no          text,
  raw                 jsonb,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  deleted_at          timestamptz,
  constraint payments_provider_order_id_key unique (provider_order_id),
  constraint payments_provider_payment_id_key unique (provider_payment_id),
  constraint payments_receipt_no_key unique (receipt_no),
  constraint payments_currency_values check (currency in ('INR')),
  constraint payments_provider_values check (provider in ('razorpay')),
  constraint payments_status_values check (status in ('created', 'captured', 'failed', 'refunded')),
  constraint payments_amount_positive check (amount_paise > 0),
  constraint payments_receipt_no_format check (receipt_no is null or receipt_no ~ '^APRO-[0-9]{4}-[0-9]{6}$')
);
create index payments_agent_id_updated_at_idx on public.payments (agent_id, updated_at);
create index payments_order_id_idx on public.payments (order_id);

create table public.subscriptions (
  id                   uuid primary key default gen_random_uuid(),
  agent_id             uuid not null references auth.users (id) on delete cascade,
  plan                 text not null default 'pro',
  trial_started_at     timestamptz not null,
  trial_ends_at        timestamptz not null,
  current_period_start timestamptz,
  current_period_end   timestamptz,
  last_order_id        uuid references public.orders (id) on delete set null,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  deleted_at           timestamptz,
  constraint subscriptions_agent_id_key unique (agent_id),
  constraint subscriptions_plan_values check (plan in ('pro')),
  constraint subscriptions_period_order check (current_period_end is null or current_period_start is null or current_period_end > current_period_start)
);
create index subscriptions_agent_id_updated_at_idx on public.subscriptions (agent_id, updated_at);
create index subscriptions_last_order_id_idx on public.subscriptions (last_order_id);

create table public.registrant_contacts (
  id         uuid primary key default gen_random_uuid(),
  agent_id   uuid not null references auth.users (id) on delete cascade,
  name       text not null,
  email      text not null,
  phone      text not null,
  address    text not null,
  city       text not null,
  state      text not null,
  pin        text not null,
  country    text not null default 'IN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint registrant_contacts_agent_id_key unique (agent_id),
  constraint registrant_contacts_pin_format check (pin ~ '^[1-9][0-9]{5}$'),
  constraint registrant_contacts_country_values check (country in ('IN'))
);
create index registrant_contacts_agent_id_updated_at_idx on public.registrant_contacts (agent_id, updated_at);

-- ---------------------------------------------------------------------------
-- app_config (rows `flags` and `pricing` are upserted by the generated catalog_sync;
-- `flag_overrides` and `min_app_version` are set by hand / service role and never touched by
-- migrations). Effective flags = public.effective_flags() (DECISIONS #35).
-- ---------------------------------------------------------------------------
create table public.app_config (
  key        text primary key,
  value      jsonb not null,
  updated_at timestamptz not null default now()
);

-- Seeded once; `do nothing` so re-running never resets an override someone has set.
insert into public.app_config (key, value) values ('flag_overrides', '{}'::jsonb)
on conflict (key) do nothing;

-- ---------------------------------------------------------------------------
-- updated_at triggers on every table
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array[
    'profiles', 'sites', 'site_domains', 'customers', 'enquiries', 'policies', 'premium_payments',
    'site_daily_views', 'domain_quotes', 'orders', 'payments', 'subscriptions', 'registrant_contacts',
    'app_config'
  ] loop
    execute format(
      'create trigger set_updated_at before insert or update on public.%I
         for each row execute function private.set_updated_at()', t);
  end loop;
end;
$$;
