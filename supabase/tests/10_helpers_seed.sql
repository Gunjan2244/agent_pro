-- LOCAL TESTS ONLY. Test helpers (schema `tests`) and seed data.
-- Every check prints a NOTICE "PASS | name" or "FAIL | name | detail"; run.sh counts them.

create schema tests;
grant usage on schema tests to anon, authenticated;

create function tests.ok(p_ok boolean, p_name text, p_detail text default null)
returns void language plpgsql as $$
begin
  if coalesce(p_ok, false) then
    raise notice 'PASS | %', p_name;
  else
    raise notice 'FAIL | % | %', p_name, coalesce(p_detail, 'condition was false/null');
  end if;
end;
$$;

-- Runs p_sql and expects an error whose message matches p_like (ILIKE), if given.
create function tests.throws(p_sql text, p_name text, p_like text default null)
returns void language plpgsql as $$
begin
  execute p_sql;
  raise notice 'FAIL | % | no error raised', p_name;
exception when others then
  if p_like is null or sqlerrm ilike p_like then
    raise notice 'PASS | % [%]', p_name, sqlerrm;
  else
    raise notice 'FAIL | % | unexpected error: %', p_name, sqlerrm;
  end if;
end;
$$;

-- Runs p_sql and expects no error.
create function tests.lives(p_sql text, p_name text)
returns void language plpgsql as $$
begin
  execute p_sql;
  raise notice 'PASS | %', p_name;
exception when others then
  raise notice 'FAIL | % | error: %', p_name, sqlerrm;
end;
$$;

create function tests.count(p_sql text)
returns bigint language plpgsql as $$
declare
  n bigint;
begin
  execute 'select count(*) from (' || p_sql || ') x' into n;
  return n;
end;
$$;

-- Runs a DML statement and returns the number of rows it affected.
create function tests.affected(p_sql text)
returns bigint language plpgsql as $$
declare
  n bigint;
begin
  execute p_sql;
  get diagnostics n = row_count;
  return n;
end;
$$;

-- Switch the current transaction to an API role / agent (like PostgREST does).
create function tests.as_agent(p_uid uuid)
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims',
    json_build_object('sub', p_uid, 'role', 'authenticated')::text, true);
  perform set_config('role', 'authenticated', true);
end;
$$;

create function tests.as_anon()
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '{"role":"anon"}', true);
  perform set_config('role', 'anon', true);
end;
$$;

-- A valid consent payload {notice_key, version, hash} for the CURRENT version of a notice in a locale,
-- read from the generated catalog functions (never a hard-coded hash). SECURITY DEFINER so anon and
-- authenticated test sessions can build one (the catalog functions are revoked from the API roles).
create function tests.consent(p_key text, p_locale text default 'en')
returns jsonb language sql stable security definer as $$
  select jsonb_build_object('notice_key', p_key,
                            'version', (public.catalog_privacy_notice(p_key) ->> 'version')::int,
                            'hash', public.catalog_privacy_notice(p_key) ->> ('hash_' || p_locale));
$$;

grant execute on all functions in schema tests to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Seed: agents A (published), B (unpublished), C (published; used for lapse), D (account deletion)
-- ---------------------------------------------------------------------------
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set B '''bbbbbbbb-0000-4000-8000-00000000000b'''
\set C '''cccccccc-0000-4000-8000-00000000000c'''
\set D '''dddddddd-0000-4000-8000-00000000000d'''

insert into auth.users (id, email, raw_user_meta_data) values
  (:A, 'ramesh.private@example.com', '{"full_name":"Ramesh Sharma"}'),
  (:B, 'sunita.private@example.com', '{"full_name":"Sunita Rao"}'),
  (:C, 'lapsed.private@example.com', '{"full_name":"Chetan Lal"}'),
  (:D, 'delete.me@example.com',      '{"full_name":"Deepa Das"}');

update public.profiles
   set designation = 'LIC Advisor', city = 'Pune', phone = '+919876543210',
       agency_code = 'AGCODE-SECRET-123', branch = 'BRANCH-SECRET', photo_url = 'https://cdn.example/a.webp'
 where id = :A;

-- Each agent creates their own site through RLS (the client path) and publishes through the RPC.
begin;
select tests.as_agent(:A);
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:A, 'ramesh-sharma', 'classic', 'indigo', 'en',
  '{"sectionOrder":["hero","about","contact"],"hidden":["faq"],
    "hero":{"headline":"Secure your family''s future"},
    "about":{"showAgencyCode":false,"bio":"Ten years of service"},
    "contact":{"phone":"9876543210","showEnquiryForm":true}}');
select public.publish_site() ->> 'ok' as a_published;
-- Draft-only edit after publishing must never leak through get_public_site.
update public.sites
   set draft_content = jsonb_set(draft_content, '{hero,headline}', '"SECRET DRAFT HEADLINE"')
 where agent_id = :A;
insert into public.customers (agent_id, name, phone) values (:A, 'A customer', '+919000000001');
insert into public.enquiries (agent_id, name, phone, source, status) values (:A, 'A manual enquiry', '+919000000002', 'walk_in', 'new');
commit;

begin;
select tests.as_agent(:B);
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:B, 'sunita-rao', 'modern', 'teal', 'hi', '{"hero":{"headline":"Draft only"}}');
insert into public.customers (agent_id, name, phone) values (:B, 'B customer', '+919000000011');
insert into public.enquiries (agent_id, name, phone, source, status) values (:B, 'B enquiry', '+919000000012', 'referral', 'new');
insert into public.policies (agent_id, customer_id, plan_name, premium_mode, status, start_date, next_due_date, premium_amount)
select :B, id, 'Jeevan Anand', 'yearly', 'active', '2025-01-15', '2026-01-15', 12000 from public.customers where agent_id = :B;
commit;

begin;
select tests.as_agent(:C);
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:C, 'chetan-lal', 'minimal', 'slate', 'en',
  '{"hero":{"headline":"Chetan Lal, advisor"},"contact":{"phone":"+91 98765 43211"}}');
select public.publish_site() ->> 'ok' as c_published;
insert into public.customers (agent_id, name) values (:C, 'C customer');
commit;
