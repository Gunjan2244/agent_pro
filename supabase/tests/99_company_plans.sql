-- LOCAL TESTS ONLY. Official plans per insurer, chosen by the agent (supabase/migrations/20261031090000_company_plans.sql,
-- DECISIONS #129): company_plans guard, RLS and audit; my_company_plans / set_site_plans; the payload's `plans` and
-- companies[].website / agency_code; the company-media plans/ path. Admins O (owner), S (editor of C2) and X (revoked)
-- come from 95_offers.sql. Everything below runs in one transaction and rolls back.
\set G '''99999999-0000-4000-8000-000000000091'''
\set H '''99999999-0000-4000-8000-000000000092'''
\set O '''a1a1a1a1-0000-4000-8000-0000000000a1'''
\set S '''a2a2a2a2-0000-4000-8000-0000000000a2'''
\set K1 '''9c000001-0000-4000-8000-000000000001'''
\set K2 '''9c000002-0000-4000-8000-000000000002'''
\set K3 '''9c000003-0000-4000-8000-000000000003'''
\set L1 '''9d000001-0000-4000-8000-000000000001'''
\set L2 '''9d000002-0000-4000-8000-000000000002'''
\set L3 '''9d000003-0000-4000-8000-000000000003'''
\set L4 '''9d000004-0000-4000-8000-000000000004'''
\set L5 '''9d000005-0000-4000-8000-000000000005'''

begin;
insert into auth.users (id, email, raw_user_meta_data) values
  (:G, 'gita.private@example.com', '{"full_name":"Gita Rao"}'),
  (:H, 'hari.private@example.com', '{"full_name":"Hari Das"}');
insert into public.companies (id, slug, name, website_url, sort) values
  (:K1, 'kone-life', '{"en":"Kone Life","hi":"कोन लाइफ"}', 'https://www.kone.example', 1),
  (:K2, 'ktwo-health', '{"en":"Ktwo Health","hi":"केटू हेल्थ"}', 'https://ktwo.example', 2),
  (:K3, 'kthree-life', '{"en":"Kthree Life","hi":"केथ्री लाइफ"}', null, 3);

-- Guard
select tests.throws($$insert into public.company_plans (company_id, name, interest, insurance_type, url)
                      values ('9c000003-0000-4000-8000-000000000003', 'A plan', 'term', 'life', 'https://x.example/a')$$,
  '#PL a company without a website can''t have plans', '%company_website_missing%');
select tests.throws($$insert into public.company_plans (company_id, name, interest, insurance_type, url)
                      values ('9c000001-0000-4000-8000-000000000001', 'A plan', 'term', 'life', 'https://kone.example.evil.test/a')$$,
  '#PL the link must be on the company''s own site', '%url_not_company_site%');
select tests.throws($$insert into public.company_plans (company_id, name, interest, insurance_type, url)
                      values ('9c000001-0000-4000-8000-000000000001', 'A plan', 'term', 'life', 'http://www.kone.example/a')$$,
  '#PL https only', '%invalid_url%');
select tests.throws($$insert into public.company_plans (company_id, name, interest, insurance_type, url)
                      values ('9c000001-0000-4000-8000-000000000001', 'A plan', 'other', 'life', 'https://www.kone.example/a')$$,
  '#PL "other" is not a plan type', '%invalid_interest%');
select tests.throws($$insert into public.company_plans (company_id, name, interest, insurance_type, url)
                      values ('9c000001-0000-4000-8000-000000000001', 'A plan', 'term', 'pets', 'https://www.kone.example/a')$$,
  '#PL the insurance type comes from the catalog', '%invalid_insurance_type%');
select tests.throws($$insert into public.company_plans (company_id, name, uin, interest, insurance_type, url)
                      values ('9c000001-0000-4000-8000-000000000001', 'A plan', 'bad uin!', 'term', 'life', 'https://www.kone.example/a')$$,
  '#PL UIN format', '%company_plans_uin_format%');
select tests.throws($$insert into public.company_plans (id, company_id, name, interest, insurance_type, url, image_url)
                      values ('9d0000ff-0000-4000-8000-0000000000ff', '9c000001-0000-4000-8000-000000000001', 'A plan', 'term', 'life',
                              'https://www.kone.example/a', 'https://cdn.example/storage/v1/object/public/company-media/plans/someone-else/1.webp')$$,
  '#PL the picture must be in the plan''s own folder', '%invalid_image_url%');

insert into public.company_plans (id, company_id, name, plan_no, uin, interest, insurance_type, url, image_url, sort) values
  (:L1, :K1, '  Kone   Term Shield ', '914', '512n339v02', 'term', 'life', 'https://www.kone.example/term',
   'https://cdn.example/storage/v1/object/public/company-media/plans/9d000001-0000-4000-8000-000000000001/1.webp', 2),
  (:L2, :K1, 'Kone Pension Plus', null, null, 'pension', 'life', 'https://plans.kone.example/pension', null, 1),
  (:L3, :K1, 'Kone Health Cash', null, null, 'health', 'health', 'https://www.kone.example/health', null, 3),
  (:L4, :K2, 'Ktwo Family Floater', null, 'KTWHLIP26001V012526', 'health', 'health', 'https://ktwo.example/floater', null, 1),
  (:L5, :K2, 'Ktwo Old Plan', null, null, 'health', 'health', 'https://ktwo.example/old', null, 2);
update public.company_plans set is_active = false where id = :L5;
select tests.ok((select name = 'Kone Term Shield' and uin = '512N339V02' from public.company_plans where id = :L1),
  '#PL names are trimmed and spaces collapsed; UINs upper-cased');
select tests.ok((select count(*) from public.audit_log where entity = 'company_plans' and action = 'plan.create') = 5
                and exists (select 1 from public.audit_log where entity = 'company_plans' and action = 'plan.update' and entity_id = :L5),
  '#PL creates and updates are audited');
select tests.throws($$update public.company_plans set company_id = '9c000002-0000-4000-8000-000000000002' where id = '9d000001-0000-4000-8000-000000000001'$$,
  '#PL a plan can''t move to another company', '%invalid_company%');

-- RLS
select tests.as_anon();
select tests.throws($$select * from public.company_plans$$, '#PL anon cannot read plans', '%permission denied%');
select tests.throws($$select * from public.agent_plan_choices$$, '#PL anon cannot read choices', '%permission denied%');
reset role;
select tests.as_agent(:G);
select tests.ok((select count(*) from public.company_plans) = 0, '#PL an agent reads no plans directly');
select tests.throws($$insert into public.company_plans (company_id, name, interest, insurance_type, url)
                      values ('9c000001-0000-4000-8000-000000000001', 'Mine', 'term', 'life', 'https://www.kone.example/m')$$,
  '#PL an agent cannot add plans', '%row-level security%');
select tests.throws($$insert into public.agent_plan_choices (agent_id, plan_ids) values ('99999999-0000-4000-8000-000000000091', '{}')$$,
  '#PL choices are written only through set_site_plans', '%permission denied%');
reset role;
select tests.as_admin(:O);
select tests.ok((select count(*) from public.company_plans where company_id in (:K1, :K2)) = 5, '#PL an owner reads every plan');
reset role;
select tests.as_admin(:O, 'aal1');
select tests.ok((select count(*) from public.company_plans) = 0, '#PL without MFA an admin reads nothing');
reset role;
select tests.as_admin(:S);
select tests.ok((select count(*) from public.company_plans where company_id in (:K1, :K2)) = 0, '#PL a scoped admin of another company reads none of these');
select tests.ok(not public.can_manage_company_media('plans/' || trim(both '''' from :'L1') || '/1.webp'),
  '#PL a scoped admin of another company can''t upload this plan''s picture');
reset role;
select tests.as_admin(:O);
select tests.ok(public.can_manage_company_media('plans/' || trim(both '''' from :'L1') || '/1.webp')
                and not public.can_manage_company_media('plans/9d0000ee-0000-4000-8000-0000000000ee/1.webp'),
  '#PL an admin of the company can upload plans/<id>/; an unknown plan id is refused');
reset role;

-- Memberships: G approved for K1 (life only, agency code) and K2 (health); H none.
alter table public.company_memberships disable trigger memberships_guard;
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types, status, requested_at, reviewed_at) values
  (:G, :K1, 'KN-77', '{life}', 'approved', now() - interval '3 days', now() - interval '2 days'),
  (:G, :K2, 'KT-12', '{health}', 'approved', now() - interval '3 days', now() - interval '1 day');
alter table public.company_memberships enable trigger memberships_guard;
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:G, 'gita-rao', 'classic', 'teal', 'en', '{"hero":{"headline":"Gita Rao"},"contact":{"phone":"9876500091"}}'),
       (:H, 'hari-das', 'classic', 'teal', 'en', '{"hero":{"headline":"Hari Das"},"contact":{"phone":"9876500092"}}');
select tests.as_agent(:G);
select public.publish_site() ->> 'ok' as g_published;
reset role;
select tests.as_agent(:H);
select public.publish_site() ->> 'ok' as h_published;
reset role;

-- Agent RPCs
select tests.as_agent(:G);
select public.my_company_plans() as mp \gset
select tests.ok((:'mp')::jsonb ->> 'choice' = 'auto' and (:'mp')::jsonb -> 'selected' = '[]'::jsonb
                and (select array_agg(e ->> 'name' order by o) from jsonb_array_elements((:'mp')::jsonb -> 'plans') with ordinality x(e, o))
                    = array['Kone Pension Plus', 'Kone Term Shield', 'Ktwo Family Floater'],
  '#PL my_company_plans: approved companies x their insurance types, active only, approval order then sort (K1 health and the inactive plan left out)',
  (:'mp')::text);
select tests.ok((:'mp')::jsonb #> '{plans,0}' ? 'company_id' and (:'mp')::jsonb #>> '{plans,1,company}' = 'Kone Life'
                and (:'mp')::jsonb #>> '{plans,1,plan_no}' = '914',
  '#PL each plan carries its company and facts');
select tests.throws($$select public.set_site_plans(array['9d000003-0000-4000-8000-000000000003']::uuid[])$$,
  '#PL a plan of a type the agent does not deal in there can''t be chosen', '%unknown_plan:9d000003%');
select tests.throws($$select public.set_site_plans(array['9d000005-0000-4000-8000-000000000005']::uuid[])$$,
  '#PL an inactive plan can''t be chosen', '%unknown_plan%');
select tests.throws($$select public.set_site_plans(array[null]::uuid[])$$, '#PL null ids are invalid', '%invalid_plans%');
select tests.ok(public.set_site_plans(array[:L4, :L1, :L4]::uuid[]) = (:'mp')::jsonb || jsonb_build_object('choice', 'custom', 'selected', jsonb_build_array(:L4, :L1)),
  '#PL set_site_plans keeps the agent''s order, drops duplicates, returns my_company_plans');
reset role;

select tests.as_anon();
select public.get_public_site('gita-rao') as gp \gset
reset role;
select tests.ok((select array_agg(e ->> 'name' order by o) from jsonb_array_elements((:'gp')::jsonb -> 'plans') with ordinality x(e, o))
                  = array['Ktwo Family Floater', 'Kone Term Shield'],
  '#PL the site shows the chosen plans in the chosen order', (:'gp')::jsonb ->> 'plans');
select tests.ok((select array_agg(k order by k) from jsonb_object_keys((:'gp')::jsonb #> '{plans,1}') k)
                  = array['company', 'docs', 'featured', 'features', 'features_checked_on', 'features_own', 'features_url', 'id', 'image_url',
                          'insurance_type', 'interest', 'name', 'plan_no', 'slug', 'uin', 'url']
                and (:'gp')::jsonb #>> '{plans,1,url}' = 'https://www.kone.example/term',
  '#PL a site plan is facts, the insurer''s links and key features (DECISIONS #130 adds slug, features, docs, featured)');
select tests.ok((:'gp')::jsonb -> 'companies'
                  = '[{"name":"Kone Life","website":"https://www.kone.example","insurance_types":["life"]},{"name":"Ktwo Health","website":"https://ktwo.example","insurance_types":["health"]}]'::jsonb,
  '#PL companies carry the insurer''s website; no agency code unless the agent shows it', (:'gp')::jsonb ->> 'companies');

select tests.as_agent(:G);
update public.sites set draft_content = '{"hero":{"headline":"Gita Rao"},"contact":{"phone":"9876500091"},"about":{"showAgencyCode":true}}' where agent_id = :G;
select public.publish_site() ->> 'ok' as g_republished;
reset role;
select tests.ok(public.get_public_site('gita-rao') #>> '{companies,0,agency_code}' = 'KN-77'
                and public.get_public_site('gita-rao') #>> '{companies,1,agency_code}' = 'KT-12',
  '#PL with showAgencyCode each company shows the agent''s code there');

update public.company_plans set is_active = false where id = :L4;
select tests.ok((select array_agg(e ->> 'name') from jsonb_array_elements(public.get_public_site('gita-rao') -> 'plans') e) = array['Kone Term Shield'],
  '#PL a plan the admin deactivates leaves the site at once');
update public.company_memberships set status = 'revoked' where agent_id = :G and company_id = :K1;
select tests.ok(public.get_public_site('gita-rao') -> 'plans' = '[]'::jsonb,
  '#PL a revoked company link takes its plans off the site');
alter table public.company_memberships disable trigger memberships_guard;
update public.company_memberships set status = 'approved' where agent_id = :G and company_id = :K1;
alter table public.company_memberships enable trigger memberships_guard;
update public.company_plans set is_active = true where id = :L4;

select tests.as_agent(:G);
select tests.ok(public.set_site_plans('{}'::uuid[]) ->> 'choice' = 'custom', '#PL an empty choice is kept');
reset role;
select tests.ok(public.get_public_site('gita-rao') -> 'plans' = '[]'::jsonb, '#PL an empty choice shows no plans');
select tests.as_agent(:G);
select tests.ok(public.set_site_plans(null) ->> 'choice' = 'auto', '#PL null goes back to automatic');
select tests.ok(jsonb_array_length(public.preview_site_offers('{}'::jsonb, 'classic') -> 'plans') = 3,
  '#PL the in-app preview shows the same plans');
reset role;
select tests.ok(jsonb_array_length(public.get_public_site('gita-rao') -> 'plans') = 3
                and public.get_public_site('hari-das') -> 'plans' = '[]'::jsonb,
  '#PL automatic: every available plan; an agent with no company has none');

update public.subscriptions set trial_ends_at = now() - interval '16 days' where agent_id = :G;
select tests.as_agent(:G);
select tests.throws($$select public.set_site_plans(null)$$, '#PL a lapsed agent can''t change the choice', '%plan_read_only%');
reset role;
select tests.as_anon();
select tests.throws($$select public.my_company_plans()$$, '#PL anon cannot execute my_company_plans', '%permission denied%');
reset role;
rollback;
