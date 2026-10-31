-- LOCAL TESTS ONLY. Plan pages (supabase/migrations/20261031120000_plan_pages.sql, DECISIONS #130): plan slugs, the
-- insurer's key features and document links, the agent's own wording (set_plan_features) and its reset, featured
-- plans (set_featured_plans), offers on a plan, and the payload. Everything runs in one transaction and rolls back.
\set G '''99999999-0000-4000-8000-0000000000a1'''
\set K1 '''9c0000a1-0000-4000-8000-0000000000a1'''
\set L1 '''9d0000a1-0000-4000-8000-0000000000a1'''
\set L2 '''9d0000a2-0000-4000-8000-0000000000a2'''
\set L3 '''9d0000a3-0000-4000-8000-0000000000a3'''
\set L4 '''9d0000a4-0000-4000-8000-0000000000a4'''
\set Q1 '''0f0000a1-0000-4000-8000-0000000000a1'''
\set Q2 '''0f0000a2-0000-4000-8000-0000000000a2'''
\set Q3 '''0f0000a3-0000-4000-8000-0000000000a3'''
\set CONTENT '''{"en":{"title":"Lower premium this month","summary":"For a limited time"},"hi":{"title":"इस महीने कम प्रीमियम","summary":"सीमित समय के लिए"}}'''

begin;
insert into auth.users (id, email, raw_user_meta_data) values (:G, 'gopal.private@example.com', '{"full_name":"Gopal Sen"}');
insert into public.companies (id, slug, name, website_url, sort) values
  (:K1, 'zeta-life', '{"en":"Zeta Life","hi":"ज़ेटा लाइफ"}', 'https://www.zeta.example', 1);

-- Slugs and the plan page's facts
insert into public.company_plans (id, company_id, name, interest, insurance_type, url, key_features, features_checked_on,
                                  brochure_url, policy_url, sort) values
  (:L1, :K1, 'Zeta''s Term Shield', 'term', 'life', 'https://www.zeta.example/term',
   array['  Pure   protection for the family ', '', 'Cover up to age 85'], '2026-10-30',
   'https://docs.zeta.example/term-brochure.pdf', 'https://www.zeta.example/term-policy.pdf', 1),
  (:L2, :K1, 'Zeta Pension Plus', 'pension', 'life', 'https://www.zeta.example/pension', '{}', null, null, null, 2),
  (:L3, :K1, 'Zeta Savings', 'endowment', 'life', 'https://www.zeta.example/savings', '{}', null, null, null, 3);
select tests.ok((select slug from public.company_plans where id = :L1) = 'zetas-term-shield'
                and (select slug from public.company_plans where id = :L2) = 'zeta-pension-plus',
  '#PP a plan''s slug comes from its name (apostrophes dropped, single hyphens)');
select tests.ok((select key_features from public.company_plans where id = :L1) = array['Pure protection for the family', 'Cover up to age 85'],
  '#PP key features are trimmed and blank points dropped');
insert into public.company_plans (id, company_id, name, interest, insurance_type, url)
values (:L4, :K1, 'ZETAS term shield!', 'term', 'life', 'https://www.zeta.example/term2');
select tests.ok((select slug from public.company_plans where id = :L4) = 'zetas-term-shield-2',
  '#PP a slug another plan has gets -2');
select tests.ok(private.plan_slug_base('Tata AIA Param Raksha Life Pro+') = 'tata-aia-param-raksha-life-pro'
                and private.plan_slug_base('  ''''  ') = 'plan', '#PP the slug base of names that differ only in punctuation is the same; an empty one is "plan"');
update public.company_plans set name = 'Zeta Term Shield Online' where id = :L1;
select tests.ok((select slug from public.company_plans where id = :L1) = 'zetas-term-shield',
  '#PP renaming a plan keeps its address');
select tests.throws($$update public.company_plans set slug = 'Bad Slug' where id = '9d0000a1-0000-4000-8000-0000000000a1'$$,
  '#PP a slug is lower-case letters, digits and hyphens', '%invalid_slug%');
select tests.throws($$update public.company_plans set brochure_url = 'https://evil.example/zeta.pdf' where id = '9d0000a1-0000-4000-8000-0000000000a1'$$,
  '#PP document links must be on the company''s website', '%url_not_company_site%');
select tests.throws($$update public.company_plans set features_url = 'http://www.zeta.example/x' where id = '9d0000a1-0000-4000-8000-0000000000a1'$$,
  '#PP document links are https', '%invalid_url%');
select tests.throws($$update public.company_plans set key_features = array['a','b','c','d','e','f'] where id = '9d0000a1-0000-4000-8000-0000000000a1'$$,
  '#PP at most 5 key features of 3 characters or more', '%invalid_features%');
update public.company_plans set is_active = false where id = :L4;

-- Offers on a plan
insert into public.offers (id, company_id, content, cta_kind, interest, plan_id, starts_at, ends_at, sort) values
  (:Q1, :K1, :CONTENT, 'enquire', 'health', :L1, now() - interval '1 day', now() + interval '30 days', 1),
  (:Q2, :K1, :CONTENT, 'enquire', 'pension', :L2, now() - interval '1 day', now() + interval '30 days', 2),
  (:Q3, :K1, :CONTENT, 'enquire', null, null, now() - interval '1 day', now() + interval '30 days', 3);
update public.offers set status = 'live' where id in (:Q1, :Q2, :Q3);
select tests.ok((select interest from public.offers where id = :Q1) = 'term', '#PP an offer''s plan type follows its plan');
insert into public.companies (id, slug, name, website_url, sort) values
  ('9c0000a2-0000-4000-8000-0000000000a2', 'eta-health', '{"en":"Eta Health","hi":"ईटा हेल्थ"}', 'https://eta.example', 2);
select tests.throws($$insert into public.offers (company_id, content, cta_kind, plan_id, starts_at, ends_at)
                      values ('9c0000a2-0000-4000-8000-0000000000a2', '{}', 'enquire', '9d0000a1-0000-4000-8000-0000000000a1', now(), now() + interval '1 day')$$,
  '#PP an offer''s plan must be the same company''s', '%plan_wrong_company%');

-- G: approved for Zeta (life); custom choice of L2 and L1 (not L3).
alter table public.company_memberships disable trigger memberships_guard;
insert into public.company_memberships (agent_id, company_id, insurance_types, status, requested_at, reviewed_at)
values (:G, :K1, '{life}', 'approved', now() - interval '3 days', now() - interval '2 days');
alter table public.company_memberships enable trigger memberships_guard;
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:G, 'gopal-sen', 'classic', 'teal', 'en', '{"hero":{"headline":"Gopal Sen"},"contact":{"phone":"9876500191"}}');
select tests.as_agent(:G);
select public.publish_site() ->> 'ok' as g_published;
reset role;
select tests.ok(jsonb_array_length(public.get_public_site('gopal-sen') -> 'plans') = 3
                and (select array_agg((e ->> 'featured')::int order by o) from jsonb_array_elements(public.get_public_site('gopal-sen') -> 'plans') with ordinality x(e, o)) = array[1, 2, 3],
  '#PP automatic: every plan, the first 6 featured in order');
select tests.as_agent(:G);
select public.set_site_plans(array[:L2, :L1]::uuid[]) is not null as chosen;
reset role;

select tests.as_anon();
select public.get_public_site('gopal-sen') as gp \gset
reset role;
select tests.ok((:'gp')::jsonb #>> '{plans,1,slug}' = 'zetas-term-shield'
                and (:'gp')::jsonb #> '{plans,1,features}' = '["Pure protection for the family", "Cover up to age 85"]'::jsonb
                and (:'gp')::jsonb #> '{plans,1,features_own}' = 'false'::jsonb
                and (:'gp')::jsonb #>> '{plans,1,features_checked_on}' = '2026-10-30'
                and (:'gp')::jsonb #>> '{plans,1,features_url}' = 'https://www.zeta.example/term'
                and (:'gp')::jsonb #> '{plans,1,docs}' = '{"brochure":"https://docs.zeta.example/term-brochure.pdf","policy":"https://www.zeta.example/term-policy.pdf"}'::jsonb
                and (:'gp')::jsonb #> '{plans,0,docs}' = '{}'::jsonb,
  '#PP a site plan carries its slug, the insurer''s key features with their source and date, and its document links', (:'gp')::jsonb ->> 'plans');
select tests.ok((select jsonb_agg(e -> 'plan_id' order by o) from jsonb_array_elements((:'gp')::jsonb #> '{offers,items}') with ordinality x(e, o))
                  = jsonb_build_array(:L1::uuid, :L2::uuid, null),
  '#PP auto offers: only offers on plans the site shows, and company-wide ones; each carries its plan_id', (:'gp')::jsonb ->> 'offers');

-- An offer on a plan the site doesn't show never appears
update public.offers set plan_id = :L3 where id = :Q2;
select tests.ok(not exists (select 1 from jsonb_array_elements(public.get_public_site('gopal-sen') #> '{offers,items}') e
                             where e ->> 'id' = trim(both '''' from :'Q2')),
  '#PP an offer on a plan the site doesn''t show is left out');
update public.sites set published_content = published_content
       || jsonb_build_object('offers', jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'company', 'id', trim(both '''' from :'Q2')),
                                                                                      jsonb_build_object('source', 'company', 'id', trim(both '''' from :'Q1')))))
       || jsonb_build_object('hero', jsonb_build_object('headline', 'Gopal Sen', 'featuredOffer', jsonb_build_object('source', 'company', 'id', trim(both '''' from :'Q2'))))
 where agent_id = :G;
select tests.ok((select array_agg(e ->> 'id') from jsonb_array_elements(public.get_public_site('gopal-sen') #> '{offers,items}') e) = array[trim(both '''' from :'Q1')]
                and public.get_public_site('gopal-sen') #> '{offers,featured}' = 'null'::jsonb,
  '#PP an offer the agent picked, or featured, on a plan the site doesn''t show is left out too');

-- The agent's own wording
select tests.as_agent(:G);
select tests.throws($$select public.set_plan_features('9d0000a4-0000-4000-8000-0000000000a4', array['My words'])$$,
  '#PP an inactive plan can''t be worded', '%unknown_plan%');
select tests.throws($$select public.set_plan_features('9d0000a1-0000-4000-8000-0000000000a1', array['Best plan from Zeta Life'])$$,
  '#PP the agent''s wording can''t name an insurer', '%blocked_term:1:%');
select tests.throws($$select public.set_plan_features('9d0000a1-0000-4000-8000-0000000000a1', array['ok', 'Fine words'])$$,
  '#PP each point is 3 characters or more', '%invalid_features%');
select public.set_plan_features(:L1, array[' Simple cover  for your family ', '', 'Ask me about the riders']) as sf \gset
select tests.ok((select e -> 'own_features' from jsonb_array_elements((:'sf')::jsonb -> 'plans') e where e ->> 'id' = trim(both '''' from :'L1'))
                  = '["Simple cover for your family", "Ask me about the riders"]'::jsonb
                and (select e -> 'features' from jsonb_array_elements((:'sf')::jsonb -> 'plans') e where e ->> 'id' = trim(both '''' from :'L1'))
                  = '["Pure protection for the family", "Cover up to age 85"]'::jsonb,
  '#PP set_plan_features keeps the agent''s words beside the insurer''s', (:'sf')::text);
reset role;
select tests.ok(public.get_public_site('gopal-sen') #> '{plans,1,features}' = '["Simple cover for your family", "Ask me about the riders"]'::jsonb
                and public.get_public_site('gopal-sen') #> '{plans,1,features_own}' = 'true'::jsonb,
  '#PP the site shows the agent''s words, marked as theirs');
select tests.as_agent(:G);
select tests.ok((select e -> 'own_features' from jsonb_array_elements(public.set_plan_features(:L1, null) -> 'plans') e
                  where e ->> 'id' = trim(both '''' from :'L1')) = 'null'::jsonb,
  '#PP null goes back to the insurer''s wording');
reset role;
select tests.ok(public.get_public_site('gopal-sen') #> '{plans,1,features_own}' = 'false'::jsonb
                and not exists (select 1 from public.agent_plan_features where agent_id = :G),
  '#PP after a reset the site shows the insurer''s wording again');

-- Featured plans
select tests.as_agent(:G);
select tests.throws($$select public.set_featured_plans(array['9d0000a1-0000-4000-8000-0000000000a1','9d0000a1-0000-4000-8000-0000000000a2','9d0000a1-0000-4000-8000-0000000000a3',
                                                              '9d0000a1-0000-4000-8000-0000000000a4','9d0000a1-0000-4000-8000-0000000000a5','9d0000a1-0000-4000-8000-0000000000a6',
                                                              '9d0000a1-0000-4000-8000-0000000000a7']::uuid[])$$,
  '#PP at most 6 featured plans', '%too_many_featured%');
select tests.throws($$select public.set_featured_plans(array['9d0000a4-0000-4000-8000-0000000000a4']::uuid[])$$,
  '#PP only plans the agent may show can be featured', '%unknown_plan%');
select public.set_featured_plans(array[:L1, :L3]::uuid[]) as fp \gset
select tests.ok((:'fp')::jsonb -> 'featured_ids' = jsonb_build_array(:L1, :L3) and (:'fp')::jsonb ->> 'featured' = 'custom',
  '#PP set_featured_plans keeps the order');
reset role;
select tests.ok((select array_agg(coalesce(e ->> 'featured', '-') order by o) from jsonb_array_elements(public.get_public_site('gopal-sen') -> 'plans') with ordinality x(e, o))
                  = array['-', '1'],
  '#PP Home features the chosen plans the site shows (L3 isn''t shown, so L1 is first)');
select tests.as_agent(:G);
select public.set_featured_plans('{}'::uuid[]) is not null as none_featured;
reset role;
select tests.ok(not exists (select 1 from jsonb_array_elements(public.get_public_site('gopal-sen') -> 'plans') e where e ->> 'featured' is not null),
  '#PP an empty choice features none');
select tests.as_agent(:G);
select tests.ok(public.set_featured_plans(null) ->> 'featured' = 'auto', '#PP null goes back to automatic');
reset role;

-- Access
select tests.as_anon();
select tests.throws($$select * from public.agent_plan_features$$, '#PP anon cannot read agent wording', '%permission denied%');
select tests.throws($$select public.set_plan_features(null, null)$$, '#PP anon cannot word plans', '%permission denied%');
select tests.throws($$select public.set_featured_plans(null)$$, '#PP anon cannot feature plans', '%permission denied%');
reset role;
select tests.as_agent(:G);
select tests.throws($$insert into public.agent_plan_features (agent_id, plan_id, features) values ('99999999-0000-4000-8000-0000000000a1', '9d0000a1-0000-4000-8000-0000000000a1', array['abc'])$$,
  '#PP agent wording is written only through set_plan_features', '%permission denied%');
reset role;
update public.subscriptions set trial_ends_at = now() - interval '16 days' where agent_id = :G;
select tests.as_agent(:G);
select tests.throws($$select public.set_plan_features('9d0000a1-0000-4000-8000-0000000000a1', array['abc'])$$, '#PP a lapsed agent can''t change wording', '%plan_read_only%');
select tests.throws($$select public.set_featured_plans(null)$$, '#PP a lapsed agent can''t change featured plans', '%plan_read_only%');
reset role;
rollback;
