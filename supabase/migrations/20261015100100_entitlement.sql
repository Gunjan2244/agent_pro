-- Agent PRO — Phase 3 — entitlement (BILLING.md), can_write(), config helpers, signup trigger.
-- Reads app_config rows written by the generated *_catalog_sync.sql:
--   app_config('pricing').value = catalog.pricing  (service.trialDays, service.graceDays, service.renewWindowDays, ...)
--   app_config('flags').value   = { "<featureKey>": <bool>, ... }  (catalog defaults)
-- and the hand-set app_config('flag_overrides') row (seeded `{}` by the schema migration; migrations
-- never overwrite it). Flags are always read through public.effective_flags() (DECISIONS #35).

-- ---------------------------------------------------------------------------
-- Config helpers (private; fail loudly if catalog_sync has not been applied)
-- ---------------------------------------------------------------------------
create or replace function private.service_pricing()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v jsonb;
begin
  select c.value -> 'service' into v from public.app_config c where c.key = 'pricing';
  if v is null or v ->> 'graceDays' is null or v ->> 'trialDays' is null then
    raise exception 'app_config.pricing.service is missing: apply the generated catalog_sync migration'
      using errcode = 'P0001';
  end if;
  return v;
end;
$$;

-- Effective feature flags = catalog defaults (`flags`) merged with manual overrides (`flag_overrides`);
-- an override wins. Only boolean override values count; anything else is ignored. A missing or
-- non-object row counts as `{}`. The app's FeatureFlags and the Edge Functions use the same rule.
-- SECURITY INVOKER: app_config is readable by `authenticated` (RLS policy app_config_select_authenticated).
create or replace function public.effective_flags()
returns jsonb
language sql
stable
set search_path = ''
as $$
  with r as (
    select (select c.value from public.app_config c where c.key = 'flags')          as defaults,
           (select c.value from public.app_config c where c.key = 'flag_overrides') as overrides
  )
  select (case when jsonb_typeof(r.defaults) = 'object' then r.defaults else '{}'::jsonb end)
      || coalesce(
           (select jsonb_object_agg(o.key, o.value)
              from jsonb_each(case when jsonb_typeof(r.overrides) = 'object' then r.overrides else '{}'::jsonb end) o
             where jsonb_typeof(o.value) = 'boolean'),
           '{}'::jsonb)
    from r;
$$;

create or replace function private.flag_enabled(p_key text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  -- A missing key (or a non-boolean value) falls back to "enabled" (all catalog features default to on).
  select coalesce(
    (select case jsonb_typeof(f.v -> p_key) when 'boolean' then (f.v ->> p_key)::boolean end
       from (select public.effective_flags() as v) f),
    true);
$$;

-- ---------------------------------------------------------------------------
-- entitlement(agent_id): trial | active | grace | lapsed, derived from subscriptions dates.
--   active : paid and now() < current_period_end
--   trial  : never paid and now() < trial_ends_at
--   grace  : now() < coalesce(current_period_end, trial_ends_at) + graceDays
--   lapsed : otherwise, or no subscriptions row
-- With the `servicePlan` flag off, everyone is `active` (development switch; SHARED_DEFINITIONS.md).
-- ---------------------------------------------------------------------------
create or replace function public.entitlement(p_agent_id uuid)
returns text
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_sub   public.subscriptions%rowtype;
  v_grace interval;
begin
  if p_agent_id is null then
    return 'lapsed';
  end if;
  if not private.flag_enabled('servicePlan') then
    return 'active';
  end if;

  select * into v_sub from public.subscriptions s where s.agent_id = p_agent_id;
  if not found then
    return 'lapsed';
  end if;

  if v_sub.current_period_end is not null and now() < v_sub.current_period_end then
    return 'active';
  end if;
  if v_sub.current_period_end is null and now() < v_sub.trial_ends_at then
    return 'trial';
  end if;

  v_grace := make_interval(days => (private.service_pricing() ->> 'graceDays')::int);
  if now() < coalesce(v_sub.current_period_end, v_sub.trial_ends_at) + v_grace then
    return 'grace';
  end if;
  return 'lapsed';
end;
$$;

-- can_write(): used by every insert/update/delete RLS policy on client-writable tables.
create or replace function public.can_write()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select public.entitlement((select auth.uid())) in ('trial', 'active', 'grace');
$$;

-- my_entitlement(): what the app shows (EntitlementRepository).
create or replace function public.my_entitlement()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := auth.uid();
  v_sub     public.subscriptions%rowtype;
  v_pricing jsonb;
  v_state   text;
  v_end     timestamptz;
  v_grace_end timestamptz;
  v_renew   boolean;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  v_pricing := private.service_pricing();
  v_state   := public.entitlement(v_uid);
  select * into v_sub from public.subscriptions s where s.agent_id = v_uid;

  v_end := coalesce(v_sub.current_period_end, v_sub.trial_ends_at);
  v_grace_end := v_end + make_interval(days => (v_pricing ->> 'graceDays')::int);

  v_renew := case
    when not private.flag_enabled('servicePlan') then false
    when v_sub.agent_id is null then false
    when v_state = 'active' then
      now() >= v_sub.current_period_end - make_interval(days => (v_pricing ->> 'renewWindowDays')::int)
    else true   -- trial (first purchase), grace, lapsed
  end;

  return jsonb_build_object(
    'state',              v_state,
    'trial_ends_at',      v_sub.trial_ends_at,
    'current_period_end', v_sub.current_period_end,
    'grace_ends_at',      v_grace_end,
    'renew_available',    v_renew,
    'prices', jsonb_build_object(
      'purchase_inr',   (v_pricing ->> 'purchaseInr')::int,
      'renewal_inr',    (v_pricing ->> 'renewalInr')::int,
      'tax_inclusive',  (v_pricing ->> 'taxInclusive')::boolean,
      'next_order_kind', case when v_sub.current_period_end is null then 'service_purchase' else 'service_renewal' end
    )
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Signup trigger: auth.users insert -> profiles + subscriptions (trial of pricing.service.trialDays)
-- ---------------------------------------------------------------------------
create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_trial_days int := (private.service_pricing() ->> 'trialDays')::int;
begin
  insert into public.profiles (id, email, full_name, phone)
  values (
    new.id,
    new.email,
    nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''),
    nullif(btrim(new.raw_user_meta_data ->> 'phone'), '')
  )
  on conflict (id) do nothing;

  insert into public.subscriptions (agent_id, trial_started_at, trial_ends_at)
  values (new.id, now(), now() + make_interval(days => v_trial_days))
  on conflict (agent_id) do nothing;

  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- ---------------------------------------------------------------------------
-- Grants. entitlement() is internal: no API role may call it (it would reveal any agent's plan).
-- can_write() must be executable by `authenticated` because RLS policies call it as that role.
-- effective_flags() is read by the app (FeatureFlags) and by the Edge Functions (service role).
-- ---------------------------------------------------------------------------
revoke all on function private.service_pricing() from public, anon, authenticated;
revoke all on function private.flag_enabled(text) from public, anon, authenticated;
revoke all on function private.handle_new_user() from public, anon, authenticated;
revoke all on function public.entitlement(uuid) from public, anon, authenticated;
revoke all on function public.can_write() from public, anon, authenticated;
revoke all on function public.my_entitlement() from public, anon, authenticated;
revoke all on function public.effective_flags() from public, anon, authenticated;

grant execute on function public.can_write() to authenticated;
grant execute on function public.my_entitlement() to authenticated;
grant execute on function public.entitlement(uuid) to service_role;
grant execute on function public.can_write() to service_role;
grant execute on function public.effective_flags() to authenticated, service_role;
