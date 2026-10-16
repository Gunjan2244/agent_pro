-- Agent PRO — Phase 3 — RLS policies and table grants (BACKEND.md, Conventions + Client write rules).
--
-- * RLS is enabled on every table; anon has no table privileges at all.
-- * Select: owner-only. Rows with deleted_at set are NOT hidden by the policy: the app's SyncEngine
--   must receive tombstones, and Postgres checks UPDATE results against SELECT policies, so a
--   `deleted_at is null` select policy would make every soft delete fail. Clients filter
--   `deleted_at is null` themselves (see BACKEND.md, "Soft delete and RLS").
-- * Insert/update/delete on client-writable tables also require public.can_write() (trial/active/grace).
-- * auth.uid() and can_write() are wrapped in (select ...) so they are evaluated once per statement.

-- Start from zero for the API roles, then grant explicitly.
revoke all on all tables in schema public from anon, authenticated;
grant usage on schema public to anon, authenticated, service_role;
grant all on all tables in schema public to service_role;

do $$
declare
  t text;
begin
  foreach t in array array[
    'profiles', 'sites', 'site_domains', 'customers', 'enquiries', 'policies', 'premium_payments',
    'site_daily_views', 'domain_quotes', 'orders', 'payments', 'subscriptions', 'registrant_contacts',
    'app_config'
  ] loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- profiles (key = id). Row is created by the signup trigger; the client only reads/updates it.
-- ---------------------------------------------------------------------------
grant select, update on public.profiles to authenticated;

create policy profiles_select_own on public.profiles
  for select to authenticated
  using (id = (select auth.uid()));

create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid()) and (select public.can_write()))
  with check (id = (select auth.uid()) and (select public.can_write()));

-- ---------------------------------------------------------------------------
-- Client-writable agent tables: enquiries, customers, policies, registrant_contacts, sites
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['enquiries', 'customers', 'policies', 'registrant_contacts', 'sites'] loop
    execute format($f$
      create policy %1$s_select_own on public.%1$I
        for select to authenticated
        using (agent_id = (select auth.uid()));
      create policy %1$s_insert_own on public.%1$I
        for insert to authenticated
        with check (agent_id = (select auth.uid()) and (select public.can_write()));
      create policy %1$s_update_own on public.%1$I
        for update to authenticated
        using (agent_id = (select auth.uid()) and (select public.can_write()))
        with check (agent_id = (select auth.uid()) and (select public.can_write()));
      create policy %1$s_delete_own on public.%1$I
        for delete to authenticated
        using (agent_id = (select auth.uid()) and (select public.can_write()));
    $f$, t);
  end loop;
end;
$$;

grant select, insert, update, delete on public.enquiries, public.customers, public.policies,
  public.registrant_contacts to authenticated;

-- sites: published_content, published_at and primary_domain_id are set only by RPCs / service role.
grant select, delete on public.sites to authenticated;
grant insert (id, agent_id, slug, template_key, accent, locale, draft_content, is_published)
  on public.sites to authenticated;
grant update (slug, template_key, accent, locale, draft_content, is_published, deleted_at)
  on public.sites to authenticated;

-- ---------------------------------------------------------------------------
-- Read-only for the client: written by the service role (Edge Functions) or SECURITY DEFINER RPCs.
-- premium_payments is written only by mark_premium_paid / undo_premium_payment.
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array[
    'subscriptions', 'orders', 'payments', 'domain_quotes', 'site_domains', 'site_daily_views',
    'premium_payments'
  ] loop
    execute format($f$
      create policy %1$s_select_own on public.%1$I
        for select to authenticated
        using (agent_id = (select auth.uid()));
      grant select on public.%1$I to authenticated;
    $f$, t);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- app_config: readable by authenticated, writable only by the service role.
-- ---------------------------------------------------------------------------
grant select on public.app_config to authenticated;

create policy app_config_select_authenticated on public.app_config
  for select to authenticated
  using (true);
