-- LOCAL TESTS ONLY. WP5a: public.preview_site_offers(content, template_key) (DECISIONS #84; BACKEND.md "RPCs";
-- TESTING.md "Database"). Uses 95_offers.sql's committed setup: agent E (site esha-patel, locale hi, published),
-- companies C1 (Acme Life, active, with a disclaimer) and C2, and C1's offers O1 (live, running, sort 1, enquire
-- 'term'), O2 (upcoming), O3 (ended), O4 (archived) and O5 (draft). Agent A (seed) has no company; B (seed) has an
-- unpublished site in hi. Everything here rolls back.
\set PV_A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set PV_B '''bbbbbbbb-0000-4000-8000-00000000000b'''
\set PV_E '''eeeeeeee-0000-4000-8000-00000000000e'''
\set PV_G '''9a9a9a9a-0000-4000-8000-00000000009a'''
\set PV_C1 '''c1c1c1c1-0000-4000-8000-0000000000c1'''
\set PV_O1 '''0f000001-0000-4000-8000-000000000001'''
\set PV_O4 '''0f000004-0000-4000-8000-000000000004'''
\set PV_Q1 '''0f0000d1-0000-4000-8000-0000000000d1'''
\set PV_Q2 '''0f0000d2-0000-4000-8000-0000000000d2'''
\set PV_Q3 '''0f0000d3-0000-4000-8000-0000000000d3'''
\set PV_U1 '''abcdef01-0000-4000-8000-000000000001'''
\set PV_CONTENT '''{"en":{"title":"Term cover offer","summary":"Lower premium this month","disclaimer":"T&C apply"},"hi":{"title":"टर्म कवर ऑफ़र","summary":"इस महीने कम प्रीमियम","disclaimer":"नियम व शर्तें लागू"}}'''
\set PV_BASE '''{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"}}'''
\set PV_NONE '''{"companies":[],"company":null,"offers":{"featured":null,"items":[]},"plans":[]}'''

begin;
-- Setup: G (no site row), E and B are approved for C1. Running live offers besides O1 (sort 1): Q1 sort 0 (image,
-- 'health'), Q2 sort 2 (call), Q3 sort 3 ('pension'). Auto order: Q1, O1, Q2 (Q3 is the 4th). Every C1 offer gets an
-- internal_name that must never show.
insert into auth.users (id, email, raw_user_meta_data) values (:PV_G, 'gita.private@example.com', '{"full_name":"Gita Nair"}');
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values
  (:PV_E, :PV_C1, 'E-123', '{life}'), (:PV_G, :PV_C1, 'G-1', '{life}'), (:PV_B, :PV_C1, 'B-1', '{life}');
update public.company_memberships set status = 'approved' where agent_id in (:PV_E, :PV_G, :PV_B) and status = 'pending';
insert into public.offers (id, company_id, content, image_url, cta_kind, interest, starts_at, ends_at, sort) values
  (:PV_Q1, :PV_C1, :PV_CONTENT, 'https://cdn.example/company-media/offers/q1/1.webp', 'enquire', 'health', now() - interval '2 days', now() + interval '30 days', 0),
  (:PV_Q2, :PV_C1, :PV_CONTENT, null, 'call', null, now() - interval '3 days', now() + interval '30 days', 2),
  (:PV_Q3, :PV_C1, :PV_CONTENT, null, 'enquire', 'pension', now() - interval '1 day', now() + interval '30 days', 3);
update public.offers set status = 'live' where id in (:PV_Q1, :PV_Q2, :PV_Q3);
update public.offers set internal_name = 'INTERNAL-ONLY-PRODUCT-NAME' where company_id = :PV_C1;

create temp table pv_ids (k text, id text);
insert into pv_ids values ('O1', trim(both '''' from :'PV_O1')), ('O4', trim(both '''' from :'PV_O4')),
  ('Q1', trim(both '''' from :'PV_Q1')), ('Q2', trim(both '''' from :'PV_Q2')), ('Q3', trim(both '''' from :'PV_Q3')),
  ('U1', trim(both '''' from :'PV_U1'));
grant select on pv_ids to anon, authenticated;
create function tests.pv_keys(p_offers jsonb) returns text[] language sql as $$
  select coalesce(array_agg(coalesce(i.k, e.v ->> 'id') order by e.n), '{}')
    from jsonb_array_elements(coalesce(p_offers, '[]'::jsonb)) with ordinality e(v, n) left join pv_ids i on i.id = e.v ->> 'id';
$$;
-- The featured offer's key, or null.
create function tests.pv_featured(p_result jsonb) returns text language sql as $$
  select coalesce(i.k, p_result #>> '{offers,featured,id}')
    from (select 1) one left join pv_ids i on i.id = p_result #>> '{offers,featured,id}';
$$;
grant execute on function tests.pv_keys(jsonb), tests.pv_featured(jsonb) to anon, authenticated;

\set PV_PICKED '''{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"},"offers":{"items":[{"source":"company","id":"0f0000d3-0000-4000-8000-0000000000d3"},{"source":"company","id":"0f000004-0000-4000-8000-000000000004"}]}}'''
\set PV_HERO '''{"hero":{"headline":"Esha Patel","featuredOffer":{"source":"company","id":"0f000001-0000-4000-8000-000000000001"}},"contact":{"phone":"9876500001"}}'''
\set PV_HIDDEN '''{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"},"hidden":["offers"]}'''
select jsonb_build_object('hero', '{"headline":"Esha Patel"}'::jsonb, 'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object(
    'items', jsonb_build_array(jsonb_build_object('source', 'agent', 'id', trim(both '''' from :'PV_U1')),
                               jsonb_build_object('source', 'company', 'id', trim(both '''' from :'PV_Q3'))),
    'custom', jsonb_build_array(jsonb_build_object('id', trim(both '''' from :'PV_U1'), 'title', 'Free review',
      'summary', 'Bring your policies', 'endsOn', (current_date + 5)::text, 'ctaKind', 'enquire', 'interest', 'pension',
      'confirmed', true))))::text as pv_custom \gset
select jsonb_build_object('hero', '{"headline":"Esha Patel"}'::jsonb, 'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object('items', '[]'::jsonb,
    'custom', jsonb_build_array(jsonb_build_object('id', trim(both '''' from :'PV_U1'), 'title', 'Free review',
      'endsOn', (current_date + 5)::text, 'ctaKind', 'call', 'confirmed', true))))::text as pv_custom_unplaced \gset

-- ---------------------------------------------------------------------------
-- Access
-- ---------------------------------------------------------------------------
select tests.as_anon();
select tests.throws($$select public.preview_site_offers('{}', 'spotlight')$$, '#PV anon cannot call preview_site_offers', '%permission denied%');
reset role;
select set_config('request.jwt.claims', '{"role":"authenticated"}', true), set_config('role', 'authenticated', true);
select tests.throws($$select public.preview_site_offers('{}', 'spotlight')$$, '#PV authenticated without a user id: not_authenticated',
  'not_authenticated');
reset role;
select tests.ok(not has_function_privilege('anon', 'public.preview_site_offers(jsonb, text)', 'EXECUTE')
                and not has_function_privilege('public', 'public.preview_site_offers(jsonb, text)', 'EXECUTE')
                and has_function_privilege('authenticated', 'public.preview_site_offers(jsonb, text)', 'EXECUTE')
                and (select p.prosecdef and p.proconfig @> array['search_path=""'] from pg_proc p
                      where p.oid = 'public.preview_site_offers(jsonb, text)'::regprocedure),
  '#PV execute for authenticated only; SECURITY DEFINER with search_path pinned');

-- ---------------------------------------------------------------------------
-- No approved company
-- ---------------------------------------------------------------------------
select tests.as_agent(:PV_A);
select tests.ok(public.preview_site_offers(:PV_BASE, 'spotlight') = :PV_NONE::jsonb
                and public.preview_site_offers(:PV_BASE, 'classic') = :PV_NONE::jsonb
                and public.preview_site_offers(:'pv_custom', 'spotlight') -> 'company' = 'null'::jsonb,
  '#PV no approved company: company null, no offers (offers family or not)',
  public.preview_site_offers(:PV_BASE, 'spotlight')::text);
reset role;

-- ---------------------------------------------------------------------------
-- Approved agent E (site locale hi)
-- ---------------------------------------------------------------------------
select tests.as_agent(:PV_E);
select public.preview_site_offers(:PV_BASE, 'spotlight')::text as pv_spot \gset
select tests.ok(tests.pv_featured(:'pv_spot'::jsonb) = 'Q1'
                and (:'pv_spot')::jsonb #> '{offers,featured,image_url}' = 'null'::jsonb
                and tests.pv_keys((:'pv_spot')::jsonb #> '{offers,items}') = array['O1', 'Q2'],
  '#PV offers family, offers shown, nothing picked: the first auto offer featured (no image) + 2 items (3 in all)', :'pv_spot');
select tests.ok((select array(select jsonb_object_keys(:'pv_spot'::jsonb) order by 1)) = array['companies', 'company', 'offers', 'plans']
                and (:'pv_spot')::jsonb -> 'companies' = '[{"name":"एक्मे लाइफ","insurance_types":["life"]}]'::jsonb
                and (:'pv_spot')::jsonb -> 'company' = '{"disclaimer":"बीमा आग्रह की विषय वस्तु है।"}'::jsonb
                and (:'pv_spot')::jsonb #>> '{offers,featured,title}' = 'टर्म कवर ऑफ़र'
                and (:'pv_spot')::jsonb #>> '{offers,featured,interest}' = 'health'
                and (:'pv_spot')::jsonb #> '{offers,items,1,cta}' = '{"kind":"call"}'::jsonb,
  '#PV only {companies, company, offers, plans}; companies = [{name, insurance_types}] and company = {disclaimer} in the site''s language; offers resolved in it with interest and CTA', :'pv_spot');
select tests.ok(tests.pv_featured(public.preview_site_offers(:PV_BASE, 'guide')) = 'Q1',
  '#PV the guide template (offers family) features too');
select tests.ok(public.preview_site_offers(:PV_BASE, 'classic') #> '{offers,featured}' = 'null'::jsonb
                and tests.pv_keys(public.preview_site_offers(:PV_BASE, 'classic') #> '{offers,items}') = array['Q1', 'O1', 'Q2']
                and public.preview_site_offers(:PV_BASE, 'classic') #>> '{offers,items,0,image_url}' = 'https://cdn.example/company-media/offers/q1/1.webp'
                and public.preview_site_offers(:PV_BASE, 'modern') = public.preview_site_offers(:PV_BASE, 'classic'),
  '#PV personal family: no featured offer, up to 3 auto items (with their images)');
select tests.ok(public.preview_site_offers(:PV_BASE, 'no-such-template') = public.preview_site_offers(:PV_BASE, 'classic')
                and public.preview_site_offers(:PV_BASE, null) = public.preview_site_offers(:PV_BASE, 'classic')
                and public.preview_site_offers(:PV_BASE, '') = public.preview_site_offers(:PV_BASE, 'classic'),
  '#PV an unknown, empty or null template_key behaves like the personal family');
select tests.ok(public.preview_site_offers(:PV_HIDDEN, 'spotlight') -> 'offers' = '{"featured":null,"items":[]}'::jsonb
                and public.preview_site_offers(:PV_HIDDEN, 'spotlight') -> 'company' <> 'null'::jsonb,
  '#PV offers section hidden: no items and no auto featured (company still set)');
select tests.ok(tests.pv_keys(public.preview_site_offers(:PV_PICKED, 'spotlight') #> '{offers,items}') = array['Q3']
                and public.preview_site_offers(:PV_PICKED, 'spotlight') #> '{offers,featured}' = 'null'::jsonb,
  '#PV picked offers win over the auto ones (no auto featured; an archived pick is skipped)');
select tests.ok(tests.pv_featured(public.preview_site_offers(:PV_HERO, 'spotlight')) = 'O1'
                and tests.pv_keys(public.preview_site_offers(:PV_HERO, 'spotlight') #> '{offers,items}') = array['Q1', 'Q2', 'Q3']
                and public.preview_site_offers(:PV_HERO, 'spotlight') #> '{offers,featured,image_url}' = 'null'::jsonb,
  '#PV a picked hero offer stays featured; the auto items leave it out');
select tests.ok(tests.pv_keys(public.preview_site_offers(:'pv_custom', 'spotlight') #> '{offers,items}') = array['U1', 'Q3']
                and public.preview_site_offers(:'pv_custom', 'spotlight') #> '{offers,items,0}' @> '{"source":"agent","title":"Free review","summary":"Bring your policies","interest":"pension","from_agent":true,"cta":{"kind":"enquire","interest":"pension"},"image_url":null}'::jsonb
                and public.preview_site_offers(:'pv_custom', 'spotlight') #> '{offers,featured}' = 'null'::jsonb,
  '#PV custom offers are included as the public payload does', public.preview_site_offers(:'pv_custom', 'spotlight')::text);
select tests.ok(public.preview_site_offers(:'pv_custom_unplaced', 'spotlight') -> 'offers' = '{"featured":null,"items":[]}'::jsonb,
  '#PV an unplaced custom offer turns auto offers off (as on the live site)');
select tests.ok(public.preview_site_offers('[]', 'spotlight') = :'pv_spot'::jsonb
                and public.preview_site_offers('"text"', 'spotlight') = :'pv_spot'::jsonb
                and public.preview_site_offers('42', 'spotlight') = :'pv_spot'::jsonb
                and public.preview_site_offers('null', 'spotlight') = :'pv_spot'::jsonb
                and public.preview_site_offers(null, 'spotlight') = :'pv_spot'::jsonb
                and public.preview_site_offers('{"offers":"x","hidden":"offers","hero":[1]}', 'spotlight') = :'pv_spot'::jsonb,
  '#PV content that isn''t an object (or has mistyped parts) is treated like {}');
select tests.throws(format($$select public.preview_site_offers(%L::jsonb, 'spotlight')$$,
                           jsonb_build_object('hero', jsonb_build_object('headline', repeat('x', 66000)))::text),
  '#PV content over 64KB: invalid_content', 'invalid_content');

-- No internal_name, company name, slug or logo anywhere in the JSON text.
select string_agg((public.preview_site_offers(c::jsonb, t) - 'companies')::text, ' ') as pv_all
  from (values (:PV_BASE, 'spotlight'), (:PV_BASE, 'classic'), (:PV_PICKED, 'spotlight'), (:PV_HERO, 'guide'),
               (:'pv_custom', 'spotlight'), (:PV_HIDDEN, 'spotlight')) v(c, t) \gset
reset role;
select tests.ok(:'pv_all' not ilike '%INTERNAL-ONLY%' and :'pv_all' not ilike '%internal_name%'
                and :'pv_all' not ilike '%acme%' and :'pv_all' not like '%एक्मे%'
                and :'pv_all' not ilike '%logo%' and :'pv_all' not ilike '%company-media/logos%'
                and :'pv_all' not like '%"name"%' and :'pv_all' not ilike '%c1c1c1c1%',
  '#PV no internal_name, company name (outside companies), slug, id or logo anywhere in the preview JSON');

-- ---------------------------------------------------------------------------
-- No site row; unpublished site; a deleted site
-- ---------------------------------------------------------------------------
select tests.ok(not exists (select 1 from public.sites where agent_id = :PV_G), '#PV (setup) G has no site row');
select tests.as_agent(:PV_G);
select tests.ok(tests.pv_featured(public.preview_site_offers(:PV_BASE, 'spotlight')) = 'Q1'
                and tests.pv_keys(public.preview_site_offers(:PV_BASE, 'spotlight') #> '{offers,items}') = array['O1', 'Q2']
                and public.preview_site_offers(:PV_BASE, 'spotlight') -> 'company' = '{"disclaimer":"Insurance is the subject matter of solicitation."}'::jsonb
                and public.preview_site_offers(:PV_BASE, 'spotlight') #>> '{offers,featured,title}' = 'Term cover offer',
  '#PV no site row (onboarding): works, in the default language');
reset role;
select tests.as_agent(:PV_B);
select tests.ok((select not is_published from public.sites where agent_id = :PV_B)
                and tests.pv_featured(public.preview_site_offers(:PV_BASE, 'guide')) = 'Q1'
                and public.preview_site_offers(:PV_BASE, 'guide') #>> '{offers,featured,title}' = 'टर्म कवर ऑफ़र',
  '#PV an unpublished site previews, in its own language');
reset role;

-- ---------------------------------------------------------------------------
-- Parity with get_public_site: publish E's site with the same content and template, then compare
-- ---------------------------------------------------------------------------
-- Runs as postgres with E's claims (get_public_site is anon-only; publish_site and the preview read auth.uid()).
-- Returns null when the preview equals the live payload's company and offers, else what differs.
create function tests.pv_parity(p_content jsonb, p_template text, p_locale text)
returns text language plpgsql as $$
declare
  v_pub  jsonb;
  v_live jsonb;
  v_prev jsonb;
begin
  perform set_config('request.jwt.claims', '{"sub":"eeeeeeee-0000-4000-8000-00000000000e","role":"authenticated"}', true);
  update public.sites set draft_content = p_content, template_key = p_template, locale = p_locale
   where agent_id = 'eeeeeeee-0000-4000-8000-00000000000e';
  v_pub := public.publish_site();
  if v_pub ->> 'ok' is distinct from 'true' then
    return 'publish failed: ' || v_pub::text;
  end if;
  v_live := public.get_public_site('esha-patel');
  v_prev := public.preview_site_offers(p_content, p_template);
  if v_live is null then
    return 'no live site';
  end if;
  if jsonb_build_object('companies', v_live -> 'companies', 'company', v_live -> 'company', 'offers', v_live -> 'offers', 'plans', v_live -> 'plans') is distinct from v_prev then
    return 'live ' || jsonb_build_object('companies', v_live -> 'companies', 'company', v_live -> 'company', 'offers', v_live -> 'offers', 'plans', v_live -> 'plans')::text
           || ' <> preview ' || coalesce(v_prev::text, 'null');
  end if;
  return null;
end;
$$;
create temp table pv_cases (n int, label text, content jsonb, template text, locale text);
insert into pv_cases values
  (1, 'auto, spotlight, hi',                          :PV_BASE,               'spotlight', 'hi'),
  (2, 'auto, guide, en',                              :PV_BASE,               'guide',     'en'),
  (3, 'auto, classic (personal), hi',                 :PV_BASE,               'classic',   'hi'),
  (4, 'auto, minimal (personal), en',                 :PV_BASE,               'minimal',   'en'),
  (5, 'offers hidden, spotlight',                     :PV_HIDDEN,             'spotlight', 'hi'),
  (6, 'picked items (one archived), spotlight',       :PV_PICKED,             'spotlight', 'hi'),
  (7, 'picked hero + auto items, guide, en',          :PV_HERO,               'guide',     'en'),
  (8, 'custom + company items, spotlight',            :'pv_custom',           'spotlight', 'hi'),
  (9, 'unplaced custom offer, spotlight',             :'pv_custom_unplaced',  'spotlight', 'en'),
  (10, 'picked hero, modern (personal)',              :PV_HERO,               'modern',    'hi'),
  (11, 'no locale (default language), spotlight',     :PV_BASE,               'spotlight', null);
create temp table pv_res as select n, label, tests.pv_parity(content, template, locale) as diff from pv_cases order by n;
select tests.ok(diff is null, '#PV parity with get_public_site ' || n || ': ' || label, diff) from pv_res order by n;
select tests.ok((select count(*) from pv_res) = 11, '#PV parity: all 11 cases ran');
select set_config('request.jwt.claims', '', true);

-- ---------------------------------------------------------------------------
-- A lapsed agent still previews (read-only plan; the live site is off)
-- ---------------------------------------------------------------------------
update public.sites set template_key = 'modern', locale = 'hi' where agent_id = :PV_E;
update public.subscriptions set trial_ends_at = now() - interval '100 days', current_period_end = null where agent_id = :PV_E;
select tests.ok(public.entitlement(:PV_E) = 'lapsed' and public.get_public_site('esha-patel') is null,
  '#PV (setup) E is lapsed and the live site is off');
select tests.as_agent(:PV_E);
select tests.ok(public.preview_site_offers(:PV_BASE, 'spotlight') = :'pv_spot'::jsonb,
  '#PV a lapsed agent still gets the same preview offers');
reset role;
rollback;
