-- Self-review for common Supabase advisor findings (TESTING.md Database #6 can only run on a real project).
select tests.ok(not exists (
  select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity),
  '#A RLS enabled on every public table',
  (select string_agg(c.relname, ',') from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity));

select tests.ok(not exists (
  select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace,
         unnest(array['SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER']) p
   where n.nspname = 'public' and c.relkind in ('r','v','m') and has_table_privilege('anon', c.oid, p)),
  '#A anon holds no table privileges in public');

select tests.ok(not exists (
  select 1 from unnest(array['subscriptions','orders','payments','domain_quotes','site_domains','site_daily_views','premium_payments','app_config']) t,
         unnest(array['INSERT','UPDATE','DELETE','TRUNCATE']) p
   where has_table_privilege('authenticated', ('public.' || t)::regclass, p)),
  '#A authenticated cannot write client-read-only tables');

select tests.ok(not exists (
  select 1 from pg_proc f join pg_namespace n on n.oid = f.pronamespace
   where n.nspname in ('public', 'private')
     and not exists (select 1 from unnest(coalesce(f.proconfig, '{}')) c where c like 'search_path=%')),
  '#A every public/private function pins search_path',
  (select string_agg(f.proname, ',') from pg_proc f join pg_namespace n on n.oid = f.pronamespace
    where n.nspname in ('public', 'private')
      and not exists (select 1 from unnest(coalesce(f.proconfig, '{}')) c where c like 'search_path=%')));

select tests.ok(
  (select array_agg(f.proname::text order by f.proname) from pg_proc f join pg_namespace n on n.oid = f.pronamespace
    where n.nspname in ('public', 'private') and has_function_privilege('anon', f.oid, 'EXECUTE'))
  = array['get_public_site', 'get_public_site_by_host', 'record_site_view', 'submit_enquiry'],
  '#A anon can execute exactly the 4 public RPCs',
  (select string_agg(f.proname, ',' order by f.proname) from pg_proc f join pg_namespace n on n.oid = f.pronamespace
    where n.nspname in ('public', 'private') and has_function_privilege('anon', f.oid, 'EXECUTE')));

select tests.ok(
  (select array_agg(f.proname::text order by f.proname) from pg_proc f join pg_namespace n on n.oid = f.pronamespace
    where n.nspname in ('public', 'private') and has_function_privilege('authenticated', f.oid, 'EXECUTE'))
  = array['can_write', 'delete_my_account', 'effective_flags', 'is_slug_available', 'mark_premium_paid', 'my_entitlement', 'publish_site', 'undo_premium_payment'],
  '#A authenticated can execute exactly the documented RPCs (+ can_write for RLS, effective_flags for FeatureFlags)',
  (select string_agg(f.proname, ',' order by f.proname) from pg_proc f join pg_namespace n on n.oid = f.pronamespace
    where n.nspname in ('public', 'private') and has_function_privilege('authenticated', f.oid, 'EXECUTE')));

select tests.ok(not exists (
  select 1 from pg_policies p,
         lateral (select coalesce(p.qual, '') || ' ' || coalesce(p.with_check, '') as expr) e
   where p.schemaname in ('public', 'storage')
     and ((select count(*) from regexp_matches(e.expr, 'auth\.uid\(\)', 'g'))
            <> (select count(*) from regexp_matches(e.expr, 'SELECT auth\.uid\(\)', 'g'))
       or (select count(*) from regexp_matches(e.expr, 'can_write\(\)', 'g'))
            <> (select count(*) from regexp_matches(e.expr, 'SELECT (public\.)?can_write\(\)', 'g')))),
  '#A auth.uid() and can_write() are wrapped in (select ...) in every policy');

select tests.ok(not exists (
  select 1 from pg_constraint k join pg_namespace n on n.oid = k.connamespace
   where k.contype = 'f' and n.nspname = 'public'
     and not exists (
       select 1 from pg_index i
        where i.indrelid = k.conrelid
          and (select array_agg(x order by x) from unnest((i.indkey::int2[])[0:cardinality(k.conkey) - 1]) x)
            = (select array_agg(x order by x) from unnest(k.conkey) x))),
  '#A every foreign key has a covering index',
  (select string_agg(k.conname, ',') from pg_constraint k join pg_namespace n on n.oid = k.connamespace
    where k.contype = 'f' and n.nspname = 'public'
      and not exists (
        select 1 from pg_index i
         where i.indrelid = k.conrelid
           and (select array_agg(x order by x) from unnest((i.indkey::int2[])[0:cardinality(k.conkey) - 1]) x)
             = (select array_agg(x order by x) from unnest(k.conkey) x))));

select tests.ok(not exists (
  select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public' and c.relkind = 'r' and c.relname not in ('profiles', 'app_config')
     and not exists (select 1 from pg_indexes i where i.schemaname = 'public' and i.tablename = c.relname
                      and i.indexdef like '%(agent_id, updated_at)%')),
  '#A every agent table has an (agent_id, updated_at) index');

select tests.ok(
  (select array_agg(tablename::text order by tablename) from pg_publication_tables where pubname = 'supabase_realtime')
  = array['enquiries', 'orders', 'site_domains', 'subscriptions'],
  '#A realtime publication = enquiries, orders, site_domains, subscriptions');

select tests.ok(
  (select count(*) from pg_constraint where conname in ('enquiries_status_check', 'enquiries_interest_check', 'enquiries_source_check',
     'policies_premium_mode_check', 'policies_status_check', 'site_domains_status_check', 'sites_template_key_check',
     'sites_accent_check', 'sites_locale_check', 'profiles_preferred_language_check')) = 10,
  '#A catalog_sync CHECK constraints present (<table>_<column>_check)');
