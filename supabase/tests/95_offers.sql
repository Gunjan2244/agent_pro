-- LOCAL TESTS ONLY. Phase 8b-2 companies and offers (docs/OFFERS.md "Tests"; BACKEND.md "Companies and offers"):
-- access control (anon, agents, admins with and without MFA, scoped and revoked admins), the guards, the membership
-- flow, publish_site errors and warnings, the public payload, admin_offer_usage, storage and the audit trail.
-- The setup below is committed (agents E/F and admins only exist here); every test block rolls back.
\set E '''eeeeeeee-0000-4000-8000-00000000000e'''
\set F '''ffffffff-0000-4000-8000-00000000000f'''
\set O '''a1a1a1a1-0000-4000-8000-0000000000a1'''
\set S '''a2a2a2a2-0000-4000-8000-0000000000a2'''
\set X '''a3a3a3a3-0000-4000-8000-0000000000a3'''
\set C1 '''c1c1c1c1-0000-4000-8000-0000000000c1'''
\set C2 '''c2c2c2c2-0000-4000-8000-0000000000c2'''
\set C3 '''c3c3c3c3-0000-4000-8000-0000000000c3'''
\set O1 '''0f000001-0000-4000-8000-000000000001'''
\set O2 '''0f000002-0000-4000-8000-000000000002'''
\set O3 '''0f000003-0000-4000-8000-000000000003'''
\set O4 '''0f000004-0000-4000-8000-000000000004'''
\set O5 '''0f000005-0000-4000-8000-000000000005'''
\set P1 '''0f000011-0000-4000-8000-000000000011'''

-- ---------------------------------------------------------------------------
-- Setup (committed)
-- ---------------------------------------------------------------------------
-- An admin session: authenticated with the given assurance level (aal2 = MFA verified).
create function tests.as_admin(p_uid uuid, p_aal text default 'aal2')
returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims',
    json_build_object('sub', p_uid, 'role', 'authenticated', 'aal', p_aal)::text, true);
  perform set_config('role', 'authenticated', true);
end;
$$;

-- Replaces the caller's draft and publishes it (the app's save + publish).
create function tests.publish_with(p_content jsonb)
returns jsonb language plpgsql as $$
begin
  update public.sites set draft_content = p_content where agent_id = auth.uid();
  return public.publish_site();
end;
$$;

-- True when a publish result lists an error (or, with p_warning, a warning) matching p_issue.
create function tests.has_issue(p_result jsonb, p_issue jsonb, p_warning boolean default false)
returns boolean language sql as $$
  select exists (select 1 from jsonb_array_elements(coalesce(p_result -> case when p_warning then 'warnings' else 'errors' end, '[]'::jsonb)) e
                  where e @> p_issue);
$$;
grant execute on all functions in schema tests to anon, authenticated;

insert into auth.users (id, email, raw_user_meta_data) values
  (:E, 'esha.private@example.com', '{"full_name":"Esha Patel"}'),
  (:F, 'farhan.private@example.com', '{"full_name":"Farhan Ali"}');
insert into auth.users (id, email, raw_app_meta_data) values
  (:O, 'owner@agentpro.test', '{"account_kind":"admin"}'),
  (:S, 'insurer.editor@agentpro.test', '{"account_kind":"admin"}'),
  (:X, 'former.admin@agentpro.test', '{"account_kind":"admin"}');
update public.profiles set designation = 'Insurance agent', city = 'Ahmedabad', phone = '+919876500001', agency_code = 'E-123'
 where id = :E;

insert into public.companies (id, slug, name, disclaimer, logo_url, logo_permission_at, logo_permission_ref, sort) values
  (:C1, 'acme-life', '{"en":"Acme Life","hi":"एक्मे लाइफ"}',
   '{"en":"Insurance is the subject matter of solicitation.","hi":"बीमा आग्रह की विषय वस्तु है।"}',
   'https://cdn.example/company-media/logos/acme/1.webp', current_date - 10, 'Letter 42', 1),
  (:C2, 'bharat-life', '{"en":"Bharat Life","hi":"भारत लाइफ"}', null, null, null, null, 2),
  (:C3, 'old-life', '{"en":"Old Life","hi":"पुरानी लाइफ"}', null, null, null, null, 3);
update public.companies set is_active = false where id = :C3;

insert into public.admins (user_id, role, company_id, revoked_at) values
  (:O, 'owner', null, null),
  (:S, 'editor', :C2, null),
  (:X, 'editor', null, now());

\set CONTENT '''{"en":{"title":"Term cover offer","summary":"Lower premium this month","disclaimer":"T&C apply"},"hi":{"title":"टर्म कवर ऑफ़र","summary":"इस महीने कम प्रीमियम","disclaimer":"नियम व शर्तें लागू"}}'''
insert into public.offers (id, company_id, content, cta_kind, interest, starts_at, ends_at, sort) values
  (:O1, :C1, :CONTENT, 'enquire', 'term', now() - interval '1 day', now() + interval '30 days', 1),   -- live, running
  (:O2, :C1, :CONTENT, 'call', null, now() + interval '5 days', now() + interval '40 days', 2),       -- live, upcoming
  (:O3, :C1, :CONTENT, 'enquire', null, now() - interval '10 days', now() + interval '1 hour', 3),    -- live, ended (below)
  (:O4, :C1, :CONTENT, 'enquire', null, now() - interval '1 day', now() + interval '30 days', 4),     -- archived
  (:O5, :C1, :CONTENT, 'enquire', null, now() - interval '1 day', now() + interval '30 days', 5);     -- draft
insert into public.offers (id, company_id, content, cta_kind, cta_url, starts_at, ends_at) values
  (:P1, :C2, :CONTENT, 'link', 'https://bharat.example/offer', now() - interval '1 day', now() + interval '20 days');
update public.offers set status = 'live' where id in (:O1, :O2, :O3, :O4, :P1);
update public.offers set status = 'archived' where id = :O4;
-- O3 ends in the past: an offer can't go live already ended, so move its end with the guard off.
alter table public.offers disable trigger offers_guard;
update public.offers set ends_at = now() - interval '1 hour' where id = :O3;
alter table public.offers enable trigger offers_guard;

begin;
select tests.as_agent(:E);
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:E, 'esha-patel', 'modern', 'teal', 'hi', '{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"}}');
select public.publish_site() ->> 'ok' as e_published;
commit;
begin;
select tests.as_agent(:F);
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:F, 'farhan-ali', 'classic', 'indigo', 'en', '{"hero":{"headline":"Farhan Ali"},"contact":{"phone":"9876500002"}}');
select public.publish_site() ->> 'ok' as f_published;
commit;

-- ---------------------------------------------------------------------------
-- Access control
-- ---------------------------------------------------------------------------
begin;
select tests.as_anon();
select tests.throws($$select * from public.companies$$, '#O anon cannot read companies', '%permission denied%');
select tests.throws($$select * from public.offers$$, '#O anon cannot read offers', '%permission denied%');
select tests.throws($$select * from public.admins$$, '#O anon cannot read admins', '%permission denied%');
select tests.throws($$select * from public.company_memberships$$, '#O anon cannot read memberships', '%permission denied%');
select tests.throws($$select public.list_companies()$$, '#O anon cannot call list_companies', '%permission denied%');
select tests.throws($$select public.my_company_offers()$$, '#O anon cannot call my_company_offers', '%permission denied%');
reset role;

select tests.as_agent(:E);
select tests.ok(tests.count('select * from public.companies') = 0, '#O an agent reads no companies directly');
select tests.ok(tests.count('select * from public.offers') = 0, '#O an agent reads no offers directly');
select tests.ok(tests.count('select * from public.admins') = 0, '#O an agent reads no admin rows');
select tests.ok(not public.is_admin() and not public.is_admin_for(null), '#O an agent is not an admin');
select tests.throws($$insert into public.company_memberships (agent_id, company_id, agency_code)
  values ('eeeeeeee-0000-4000-8000-00000000000e', 'c1c1c1c1-0000-4000-8000-0000000000c1', 'X')$$,
  '#O an agent cannot insert a membership directly', '%permission denied%');
select tests.throws($$update public.company_memberships set status = 'approved'$$,
  '#O an agent cannot update memberships directly (no self-approval)', '%permission denied%');
select tests.throws($$insert into public.companies (slug, name) values ('x-life', '{"en":"X","hi":"X"}')$$,
  '#O an agent cannot create companies', '%row-level security%');
reset role;

select tests.as_admin(:O, 'aal1');
select tests.ok(not public.is_admin(), '#O an admin without MFA (aal1) is not an admin');
select tests.ok(tests.count('select * from public.companies') = 0, '#O an admin without aal2 reads no companies');
select tests.throws($$insert into public.companies (slug, name) values ('x-life', '{"en":"X","hi":"X"}')$$,
  '#O an admin without aal2 cannot create a company', '%row-level security%');
reset role;

select tests.as_admin(:X);
select tests.ok(not public.is_admin() and tests.count('select * from public.offers') = 0, '#O a revoked admin has no access');
reset role;

select tests.as_admin(:O);
select tests.ok(public.is_admin() and public.is_admin('owner') and public.is_admin_for(null) and public.is_admin_for(:C1),
  '#O the owner is an admin for everything');
select tests.ok(tests.count('select * from public.companies') = 3 and tests.count('select * from public.offers') = 6,
  '#O the owner reads every company and offer');
select tests.ok(tests.count('select * from public.admins') = 3, '#O the owner reads every admin row');
select tests.ok(tests.count('select * from public.audit_log') > 0, '#O the owner reads the audit log');
reset role;

select tests.as_admin(:S);
select tests.ok(public.is_admin() and not public.is_admin('owner') and public.is_admin_for(:C2)
                and not public.is_admin_for(:C1) and not public.is_admin_for(null),
  '#O a scoped editor is an admin for their company only');
select tests.ok(tests.count('select * from public.offers') = 1, '#O a scoped editor reads only their company''s offers');
select tests.ok(tests.count('select * from public.admins') = 1, '#O a scoped editor reads only their own admin row');
select tests.ok(tests.count('select * from public.audit_log') = 0, '#O editors read no audit log');
select tests.ok(tests.affected($$update public.offers set sort = 9 where company_id = 'c1c1c1c1-0000-4000-8000-0000000000c1'$$) = 0,
  '#O a scoped editor cannot edit another company''s offers');
select tests.throws($$insert into public.offers (company_id, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', now(), now() + interval '1 day')$$,
  '#O a scoped editor cannot add offers to another company', '%row-level security%');
select tests.lives($$insert into public.offers (company_id, starts_at, ends_at)
  values ('c2c2c2c2-0000-4000-8000-0000000000c2', now(), now() + interval '1 day')$$,
  '#O a scoped editor can add a draft to their company');
select tests.throws($$insert into public.companies (slug, name) values ('y-life', '{"en":"Y","hi":"Y"}')$$,
  '#O only unscoped admins create companies', '%row-level security%');
select tests.ok(tests.affected($$update public.companies set sort = 5 where id = 'c1c1c1c1-0000-4000-8000-0000000000c1'$$) = 0,
  '#O a scoped editor cannot edit another company');
select tests.ok(tests.affected($$update public.companies set sort = 5 where id = 'c2c2c2c2-0000-4000-8000-0000000000c2'$$) = 1,
  '#O a scoped editor can edit their own company');
rollback;

-- ---------------------------------------------------------------------------
-- Guards: companies and offers
-- ---------------------------------------------------------------------------
\set FULL '''{"en":{"title":"New offer","summary":"Summary"},"hi":{"title":"नया ऑफ़र","summary":"सारांश"}}'''
\set N1 '''0f0000a1-0000-4000-8000-0000000000a1'''
\set N2 '''0f0000a2-0000-4000-8000-0000000000a2'''
\set N3 '''0f0000a3-0000-4000-8000-0000000000a3'''
begin;
select tests.as_admin(:O);
select tests.throws($$insert into public.companies (slug, name) values ('z-life', '{"en":"Z Life"}')$$,
  '#O a company needs a name in every catalog locale', '%invalid_name%');
select tests.throws($$insert into public.companies (slug, name, disclaimer) values ('z-life', '{"en":"Z","hi":"Z"}', '{"en":5}')$$,
  '#O a disclaimer is text per locale', '%invalid_disclaimer%');
select tests.throws($$insert into public.companies (slug, name, logo_url) values ('z-life', '{"en":"Z","hi":"Z"}', 'https://cdn.example/z.webp')$$,
  '#O a logo needs a recorded permission', '%companies_logo_needs_permission%');
select tests.throws($$insert into public.companies (slug, name, website_url) values ('z-life', '{"en":"Z","hi":"Z"}', 'http://z.example')$$,
  '#O a company website must be https', '%companies_website_url_https%');
select tests.lives($$insert into public.companies (slug, name) values ('new-life', '{"en":"New Life","hi":"न्यू लाइफ"}')$$,
  '#O the owner creates a company');

select tests.throws($$insert into public.offers (company_id, status, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', 'live', now(), now() + interval '1 day')$$,
  '#O a new offer starts as a draft', '%invalid_transition%');
select tests.throws($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"fr":{"title":"x"}}', now(), now() + interval '1 day')$$,
  '#O content only in catalog locales', '%invalid_content%');
select tests.throws($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"colour":"red"}}', now(), now() + interval '1 day')$$,
  '#O content only with offer-schema localized keys', '%invalid_content%');
select tests.throws(format($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"title":"%s"}}', now(), now() + interval '1 day')$$, repeat('x', 61)),
  '#O content within maxLength (title 60)', '%invalid_content%');
select tests.throws($$insert into public.offers (company_id, cta_kind, cta_url, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', 'link', 'http://acme.example', now(), now() + interval '1 day')$$,
  '#O a link CTA must be https', '%offers_cta_url_rule%');
select tests.throws($$insert into public.offers (company_id, cta_url, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', 'https://acme.example', now(), now() + interval '1 day')$$,
  '#O a URL only with the link kind', '%offers_cta_url_rule%');
select tests.throws($$insert into public.offers (company_id, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', now(), now() + interval '400 days')$$,
  '#O validity at most catalog.offers.maxValidityDays', '%invalid_dates%');
select tests.throws($$insert into public.offers (company_id, interest, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', 'cars', now(), now() + interval '1 day')$$,
  '#O interest must be a catalog interest', '%invalid_interest%');
select tests.throws($$insert into public.offers (company_id, cta_kind, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', 'fax', now(), now() + interval '1 day')$$,
  '#O CTA kind must be a catalog kind', '%invalid_cta%');

insert into public.offers (id, company_id, content, starts_at, ends_at)
values (:N1, :C1, '{"en":{"title":"English only","summary":"S"}}', now(), now() + interval '10 days');
select tests.throws($$update public.offers set status = 'live' where id = '0f0000a1-0000-4000-8000-0000000000a1'$$,
  '#O going live needs every required field in Hindi too', '%incomplete_content%');
update public.offers set content = :FULL where id = :N1;
select tests.lives($$update public.offers set status = 'live' where id = '0f0000a1-0000-4000-8000-0000000000a1'$$,
  '#O a complete draft goes live');
select tests.ok((select published_at is not null and published_by = :O and created_by = :O and updated_by = :O
                   from public.offers where id = :N1),
  '#O going live stamps published_at/by; created_by/updated_by are the admin');
select tests.throws($$update public.offers set status = 'draft' where id = '0f0000a1-0000-4000-8000-0000000000a1'$$,
  '#O live -> draft is not allowed', '%invalid_transition%');
select tests.lives($$update public.offers set status = 'archived' where id = '0f0000a1-0000-4000-8000-0000000000a1'$$,
  '#O live -> archived');
select tests.ok((select archived_at is not null from public.offers where id = :N1), '#O archiving stamps archived_at');
select tests.lives($$update public.offers set status = 'draft' where id = '0f0000a1-0000-4000-8000-0000000000a1'$$,
  '#O archived -> draft (restore)');
select tests.ok((select archived_at is null and status = 'draft' from public.offers where id = :N1), '#O restoring clears archived_at');

insert into public.offers (id, company_id, content, cta_kind, starts_at, ends_at)
values (:N2, :C1, :FULL, 'link', now(), now() + interval '10 days');
select tests.throws($$update public.offers set status = 'live' where id = '0f0000a2-0000-4000-8000-0000000000a2'$$,
  '#O a link CTA can''t go live without its URL', '%invalid_cta%');
insert into public.offers (id, company_id, content, starts_at, ends_at)
values (:N3, :C1, :FULL, now() - interval '10 days', now() - interval '1 day');
select tests.throws($$update public.offers set status = 'live' where id = '0f0000a3-0000-4000-8000-0000000000a3'$$,
  '#O an ended offer can''t go live', '%offer_ended%');
select tests.throws($$update public.offers set published_at = now()$$, '#O stamps are not writable', '%permission denied%');
select tests.throws($$update public.offers set company_id = 'c2c2c2c2-0000-4000-8000-0000000000c2'$$,
  '#O an offer can''t move to another company', '%permission denied%');
select tests.throws($$delete from public.offers$$, '#O offers are never deleted', '%permission denied%');
reset role;
select tests.ok(exists (select 1 from public.audit_log where entity = 'offers' and entity_id = :N1 and action = 'offer.go_live'
                         and actor_kind = 'admin' and actor_id = :O),
  '#O audit: offer.go_live by the admin');
select tests.ok((select array_agg(action order by id) from public.audit_log where entity = 'offers' and entity_id = :N1)
                 = array['offer.create', 'offer.update', 'offer.go_live', 'offer.archive', 'offer.restore'],
  '#O audit: every offer write in order');
select tests.ok(exists (select 1 from public.audit_log where action = 'company.create' and actor_id = :O
                         and changes ->> 'slug' = 'new-life'),
  '#O audit: company.create with the changed columns');
rollback;

-- ---------------------------------------------------------------------------
-- Memberships (DECISIONS #53, #89: several companies per agent, each with its insurance types)
-- ---------------------------------------------------------------------------
begin;
select tests.as_agent(:E);
select tests.ok(jsonb_array_length(public.list_companies()) = 2
                and public.list_companies() -> 0 -> 'name' ->> 'en' = 'Acme Life'
                and public.list_companies() -> 0 ->> 'logo_url' is not null
                and public.list_companies() -> 1 ->> 'logo_url' is null,
  '#M list_companies: active companies in order, logos only with permission');
select tests.throws($$select public.request_company(null, null, null, '{life}')$$, '#M a request needs a company or a name', 'invalid');
select tests.throws($$select public.request_company('c1c1c1c1-0000-4000-8000-0000000000c1', 'Also this', 'E-1', '{life}')$$,
  '#M not both a company and a name', 'invalid');
select tests.throws($$select public.request_company('c3c3c3c3-0000-4000-8000-0000000000c3', null, 'E-1', '{life}')$$,
  '#M an inactive company can''t be requested', '%not_found%');
select tests.throws($$select public.request_company('c1c1c1c1-0000-4000-8000-0000000000c1', null, ' ', '{life}')$$,
  '#M a company request needs the agency code', 'invalid');
select tests.throws($$select public.request_company('c1c1c1c1-0000-4000-8000-0000000000c1', null, 'E-1', null)$$,
  '#M insurance types are required (null)', 'invalid_types');
select tests.throws($$select public.request_company('c1c1c1c1-0000-4000-8000-0000000000c1', null, 'E-1', '{}')$$,
  '#M insurance types are required (empty)', 'invalid_types');
select tests.throws($$select public.request_company('c1c1c1c1-0000-4000-8000-0000000000c1', null, 'E-1', '{life,cars}')$$,
  '#M every insurance type must be a catalog key', 'invalid_types');
select tests.throws($$select public.request_company('c1c1c1c1-0000-4000-8000-0000000000c1', null, 'E-1', array['life', null])$$,
  '#M a null insurance type is refused', 'invalid_types');
select tests.throws($$select public.request_company(null, 'Tiny Life', null, '{}')$$,
  '#M a "not listed" request needs insurance types too', 'invalid_types');
select public.request_company(:C1, null, 'E-123', array[' health', 'life', 'life']) ->> 'id' as m1 \gset
select tests.ok((select status = 'pending' and agency_code = 'E-123' and insurance_types = array['life', 'health']
                   from public.company_memberships where id = :'m1'),
  '#M request_company creates a pending request; types trimmed, unique, in catalog order');
select tests.ok(public.my_company_offers() -> 'memberships' -> 0 @> '{"status":"pending","insurance_types":["life","health"]}'
                and jsonb_array_length(public.my_company_offers() -> 'memberships') = 1
                and public.my_company_offers() -> 'offers' = '[]'::jsonb,
  '#M my_company_offers lists the pending request; no company offers while pending');
select tests.throws(format($$select public.admin_review_membership(%L, 'approve', null, null)$$, :'m1'),
  '#M an agent can''t approve themselves', '%forbidden%');
reset role;

select tests.as_admin(:S);
select tests.throws(format($$select public.admin_review_membership(%L, 'approve', null, null)$$, :'m1'),
  '#M a scoped editor can''t review another company''s request', '%not_found%');
select tests.ok(public.admin_membership_queue(null, null) = '[]'::jsonb, '#M a scoped editor''s queue holds only their company');
reset role;
select tests.as_admin(:O, 'aal1');
select tests.throws(format($$select public.admin_review_membership(%L, 'approve', null, null)$$, :'m1'),
  '#M reviewing needs MFA', '%forbidden%');
reset role;

select tests.as_admin(:O);
select tests.ok(jsonb_array_length(public.admin_membership_queue(null, null)) = 1
                and public.admin_membership_queue(null, null) -> 0 #>> '{agent,full_name}' = 'Esha Patel'
                and public.admin_membership_queue(null, null) -> 0 ->> 'agency_code' = 'E-123'
                and public.admin_membership_queue(null, null) -> 0 -> 'insurance_types' = '["life","health"]'::jsonb
                and (select array_agg(k order by k) from jsonb_object_keys(public.admin_membership_queue(null, null) -> 0 -> 'agent') k)
                    = array['city', 'designation', 'email', 'full_name'],
  '#M the queue shows only the fields needed to verify, with the insurance types');
select tests.ok(public.admin_review_membership(:'m1', 'approve', null, null) ->> 'status' = 'approved', '#M the owner approves');
select tests.throws(format($$select public.admin_review_membership(%L, 'approve', null, null)$$, :'m1'),
  '#M an approved request can''t be approved again', '%invalid_transition%');
reset role;
select reviewed_at as m1_reviewed from public.company_memberships where id = :'m1' \gset

select tests.as_agent(:E);
select tests.ok((select array_agg(o ->> 'id' order by o ->> 'id') from jsonb_array_elements(public.my_company_offers() -> 'offers') o)
                 = array[:O1, :O2]::text[],
  '#M approved: the company''s live offers that haven''t ended (running and upcoming; not ended, archived or draft)');
select tests.ok((select array_agg(o ->> 'phase' order by o ->> 'id') from jsonb_array_elements(public.my_company_offers() -> 'offers') o)
                 = array['running', 'upcoming']
                and (select bool_and(o ->> 'company_id' = trim(both '''' from :'C1')) from jsonb_array_elements(public.my_company_offers() -> 'offers') o),
  '#M offers carry their phase and their company_id');
select tests.ok(public.my_company_offers() #>> '{memberships,0,company,logo_url}' is not null
                and public.my_company_offers() #> '{memberships,0,company,allow_custom_offers}' = 'true'::jsonb,
  '#M the membership shows the company, its permitted logo and the custom-offer switch');
select public.request_company(:C1, null, 'E-123', '{life}')::text as m1again \gset
select tests.ok((:'m1again')::jsonb @> format('{"id":"%s","status":"approved","insurance_types":["life"]}', :'m1')::jsonb
                and (select insurance_types = array['life'] from public.company_memberships where id = :'m1')
                and (select reviewed_at = :'m1_reviewed'::timestamptz from public.company_memberships where id = :'m1'),
  '#M asking again for the approved company with the same code only updates the types (no new review)');
select public.request_company(:C2, null, 'E-456', '{motor}') ->> 'id' as m2 \gset
select tests.ok((select status = 'approved' from public.company_memberships where id = :'m1')
                and tests.count($$select * from public.company_memberships where status in ('pending', 'approved')$$) = 2,
  '#M a second company is added alongside the first (nothing withdrawn)');
select public.request_company(:C2, null, 'E-456', '{home,motor}') ->> 'id' as m2again \gset
select tests.ok(:'m2again' = :'m2'
                and (select insurance_types = array['motor', 'home'] and status = 'pending' from public.company_memberships where id = :'m2'),
  '#M re-requesting a pending company is a no-op that updates the types');
select tests.ok(jsonb_array_length(public.my_company_offers() -> 'memberships') = 2
                and (select bool_and(o ->> 'company_id' = trim(both '''' from :'C1')) from jsonb_array_elements(public.my_company_offers() -> 'offers') o),
  '#M both memberships listed; offers only from the approved company');
reset role;
select tests.throws(format($$insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types)
                             values (%L, %L, 'X', '{life}')$$, trim(both '''' from :'E'), trim(both '''' from :'C2')),
  '#M one pending or approved row per agent and company (unique index)', '%company_memberships_one_current_per_company_idx%');
select tests.throws(format($$insert into public.company_memberships (agent_id, company_id, agency_code) values (%L, %L, 'X')$$,
                           trim(both '''' from :'F'), trim(both '''' from :'C2')),
  '#M a pending row needs at least one type (CHECK)', '%company_memberships_insurance_types_required%');
select tests.throws(format($$insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values (%L, %L, 'X', '{cars}')$$,
                           trim(both '''' from :'F'), trim(both '''' from :'C2')),
  '#M the guard refuses an unknown type', 'invalid_types');
select tests.throws(format($$insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values (%L, %L, 'X', '{life,life}')$$,
                           trim(both '''' from :'F'), trim(both '''' from :'C2')),
  '#M the guard refuses a repeated type', 'invalid_types');

select tests.as_admin(:S);
select tests.ok(jsonb_array_length(public.admin_membership_queue(null, null)) = 1
                and public.admin_membership_queue(null, null) -> 0 ->> 'id' = :'m2'
                and public.admin_membership_queue(null, null) -> 0 -> 'insurance_types' = '["motor","home"]'::jsonb
                and public.admin_membership_queue(null, null) -> 0 -> 'previous' = 'null'::jsonb
                and public.admin_membership_queue(:C1, 'approved') = '[]'::jsonb,
  '#M the scoped editor sees only their company''s request (not the agent''s other company)');
select tests.ok(tests.count('select * from public.company_memberships') = 1, '#M a scoped editor reads only their company''s membership rows');
select tests.throws(format($$select public.admin_review_membership(%L, 'reject', null, null)$$, :'m2'),
  '#M a rejection needs a reason', '%invalid_reason%');
select tests.throws(format($$select public.admin_review_membership(%L, 'reject', 'bogus', null)$$, :'m2'),
  '#M the reason must be a catalog reason', '%invalid_reason%');
select tests.ok(public.admin_review_membership(:'m2', 'reject', 'details_mismatch', 'Check your agency code') ->> 'status' = 'rejected',
  '#M the scoped editor rejects with a reason');
reset role;

select tests.as_agent(:E);
select tests.ok(public.my_company_offers() -> 'memberships' @> '[{"status":"approved"},{"status":"rejected","reject_reason":"details_mismatch","review_note":"Check your agency code"}]',
  '#M the agent sees the approval and the rejection, its reason and the note');
select tests.throws(format($$select public.set_membership_types(%L, '{life}')$$, :'m2'),
  '#M a rejected membership''s types can''t change', '%invalid_transition%');
select tests.throws(format($$select public.withdraw_company_request(%L)$$, :'m2'),
  '#M a rejected membership can''t be withdrawn', '%invalid_transition%');
select public.request_company(null, 'Tiny Life', null, '{travel}') ->> 'id' as m3 \gset
select tests.ok(public.request_company(null, 'tiny life', null, '{travel,home}') ->> 'id' = :'m3',
  '#M the same "not listed" request again only updates the types');
select public.request_company(null, 'Other Life', null, '{travel}') ->> 'id' as m3x \gset
select tests.ok((select status = 'withdrawn' from public.company_memberships where id = :'m3')
                and tests.count($$select * from public.company_memberships where company_id is null and status = 'pending'$$) = 1,
  '#M at most one pending "not listed" request: a new one replaces it');
reset role;

select tests.as_admin(:S);
select tests.ok(public.admin_membership_queue(null, null) = '[]'::jsonb, '#M a scoped editor doesn''t see unlisted requests');
select tests.throws(format($$select public.admin_link_membership(%L, 'c2c2c2c2-0000-4000-8000-0000000000c2')$$, :'m3x'),
  '#M only unscoped admins link an unlisted request', '%forbidden%');
reset role;
select tests.as_admin(:O);
select tests.ok(public.admin_membership_queue(null, null) -> 0 ->> 'suggested_name' = 'Other Life'
                and public.admin_membership_queue(null, null) -> 0 -> 'previous' = 'null'::jsonb,
  '#M the owner sees the unlisted request; previous is per company (none for "not listed")');
select tests.throws(format($$select public.admin_link_membership(%L, 'c1c1c1c1-0000-4000-8000-0000000000c1')$$, :'m3x'),
  '#M linking to a company the agent already has is refused', 'duplicate_membership');
select tests.ok(public.admin_link_membership(:'m3x', :C2) @> format('{"status":"pending","company":{"id":"%s"}}', trim(both '''' from :'C2'))::jsonb,
  '#M linking sets the company and leaves the request pending');
reset role;
select tests.as_admin(:S);
select tests.ok(public.admin_membership_queue(null, null) -> 0 #>> '{previous,status}' = 'rejected',
  '#M after linking, the queue shows the agent''s previous decision for this company');
select tests.ok(public.admin_review_membership(:'m3x', 'approve', null, null) ->> 'status' = 'approved',
  '#M after linking, the company''s editor approves');
select tests.throws(format($$select public.admin_review_membership(%L, 'revoke', null, null)$$, :'m3x'),
  '#M a revocation needs a note', '%invalid_note%');
select tests.ok(public.admin_review_membership(:'m3x', 'revoke', null, 'Left the company') ->> 'status' = 'revoked',
  '#M the editor revokes an approved link');
reset role;

select tests.as_agent(:E);
select tests.ok(public.my_company_offers() -> 'memberships' @> format('[{"id":"%s","status":"approved"},{"id":"%s","status":"revoked"}]', :'m1', :'m3x')::jsonb
                and jsonb_array_length(public.my_company_offers() -> 'memberships') = 2
                and (select bool_and(o ->> 'company_id' = trim(both '''' from :'C1')) from jsonb_array_elements(public.my_company_offers() -> 'offers') o),
  '#M revoked: the agent sees it and gets no offers from that company; the other company stays');
select public.request_company(:C2, null, 'E-456', '{motor}') ->> 'id' as m4 \gset
select public.withdraw_company_request(:'m4') ->> 'status' as m4w \gset
select tests.ok(:'m4w' = 'withdrawn'
                and public.withdraw_company_request(:'m4') ->> 'status' = 'withdrawn'
                and not public.my_company_offers() -> 'memberships' @> format('[{"id":"%s"}]', :'m4')::jsonb
                and (select status = 'approved' from public.company_memberships where id = :'m1'),
  '#M withdraw_company_request withdraws that request only (again: a no-op); withdrawn rows aren''t listed');
select tests.throws($$select public.withdraw_company_request('00000000-0000-4000-8000-000000000000')$$,
  '#M withdrawing an unknown membership: not_found', '%not_found%');
select public.set_membership_types(:'m1', '{accident,health}')::text as m1t \gset
select tests.ok((:'m1t')::jsonb @> '{"status":"approved","insurance_types":["health","accident"]}'
                and (select insurance_types = array['health', 'accident'] from public.company_memberships where id = :'m1')
                and (select reviewed_at = :'m1_reviewed'::timestamptz and status = 'approved' from public.company_memberships where id = :'m1'),
  '#M set_membership_types changes an approved link''s types without a new review');
select tests.throws(format($$select public.set_membership_types(%L, '{}')$$, :'m1'), '#M set_membership_types needs a type', 'invalid_types');
select tests.throws(format($$select public.set_membership_types(%L, '{boats}')$$, :'m1'), '#M set_membership_types refuses an unknown type', 'invalid_types');
reset role;
select tests.as_agent(:F);
select tests.throws(format($$select public.set_membership_types(%L, '{life}')$$, :'m1'),
  '#M another agent can''t change the types (not_found)', '%not_found%');
select tests.throws(format($$select public.withdraw_company_request(%L)$$, :'m1'),
  '#M another agent can''t withdraw it (not_found)', '%not_found%');
reset role;
select tests.throws(format($$update public.company_memberships set insurance_types = '{life}' where id = %L$$, :'m4'),
  '#M the guard refuses a type change on a withdrawn row', '%invalid_transition%');

select tests.as_agent(:E);
select public.request_company(:C1, null, 'E-999', '{life}') ->> 'id' as m5 \gset
select tests.ok((select status = 'withdrawn' from public.company_memberships where id = :'m1')
                and (select status = 'pending' and agency_code = 'E-999' from public.company_memberships where id = :'m5'),
  '#M a new agency code for an approved company needs a new review (the link is withdrawn, a new request made)');
reset role;

select tests.ok((select array_agg(distinct action order by action) from public.audit_log where entity = 'company_memberships'
                   and entity_id in (:'m1', :'m2', :'m3', :'m3x', :'m4'))
                 = array['membership.approve', 'membership.link', 'membership.reject', 'membership.request', 'membership.revoke',
                         'membership.update', 'membership.withdraw'],
  '#M audit: request, approve, reject, link, revoke, withdraw and update');
select tests.ok(exists (select 1 from public.audit_log where action = 'membership.update' and entity_id = :'m1' and actor_id = :E
                         and changes -> 'insurance_types' = '["health","accident"]'::jsonb),
  '#M audit: a type change is membership.update with the new types');
select tests.ok(exists (select 1 from public.audit_log where action = 'membership.request' and actor_id = :E and actor_kind = 'agent')
                and exists (select 1 from public.audit_log where action = 'membership.approve' and actor_id = :O and actor_kind = 'admin'),
  '#M audit: the agent''s request and the admin''s decision, each with its actor');
select tests.ok(not exists (select 1 from public.audit_log where entity = 'company_memberships'
                             and (changes ? 'agency_code' or changes ? 'suggested_name' or changes ? 'review_note')),
  '#M audit: no agency code, suggested name or note in the trail');
select tests.ok(not exists (select 1 from public.profiles where id in (:O, :S, :X))
                and not exists (select 1 from public.subscriptions where agent_id in (:O, :S, :X)),
  '#M admin accounts get no profile and no subscription');

-- A lapsed agent can't change types (can_write) but can still withdraw.
update public.subscriptions set trial_ends_at = now() - interval '100 days', current_period_end = null where agent_id = :E;
select tests.as_agent(:E);
select tests.throws(format($$select public.set_membership_types(%L, '{life}')$$, :'m5'),
  '#M set_membership_types needs a writable plan', '%plan_read_only%');
select tests.ok(public.withdraw_company_request(:'m5') ->> 'status' = 'withdrawn', '#M a lapsed agent can still withdraw');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- Several approved companies: offers, custom-offer switch and the public `companies` list (DECISIONS #89, #90)
-- ---------------------------------------------------------------------------
\set C4 '''c4c4c4c4-0000-4000-8000-0000000000c4'''
\set C5 '''c5c5c5c5-0000-4000-8000-0000000000c5'''
\set R1 '''0f0000e1-0000-4000-8000-0000000000e1'''
begin;
insert into public.companies (id, slug, name, sort) values
  (:C4, 'zeta-life', '{"en":"Zeta Life","hi":"ज़ेटा लाइफ"}', 4),
  (:C5, 'omega-life', '{"en":"Omega Life","hi":"ओमेगा लाइफ"}', 5);
insert into public.offers (id, company_id, content, starts_at, ends_at) values
  (:R1, :C5, :CONTENT, now() - interval '1 day', now() + interval '10 days');
update public.offers set status = 'live' where id = :R1;
update public.companies set disclaimer = '{"en":"Bharat terms apply.","hi":"भारत की शर्तें लागू।"}' where id = :C2;
update public.companies set is_active = true where id = :C3;
-- E: approved for C1 (types stored out of order), C2 and C3; pending for C4. C2 was approved first (2 days ago),
-- then C1 (1 day ago), then C3 (now; inactive again afterwards).
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values
  (:E, :C1, 'E-123', '{health,life}'), (:E, :C2, 'E-456', '{motor}'), (:E, :C3, 'E-789', '{home}'), (:E, :C4, 'E-000', '{travel}');
update public.company_memberships set status = 'approved' where agent_id = :E and company_id in (:C1, :C2, :C3);
alter table public.company_memberships disable trigger memberships_guard;
update public.company_memberships set reviewed_at = now() - interval '2 days' where agent_id = :E and company_id = :C2;
update public.company_memberships set reviewed_at = now() - interval '1 day' where agent_id = :E and company_id = :C1;
alter table public.company_memberships enable trigger memberships_guard;
update public.companies set is_active = false where id = :C3;

select tests.as_agent(:E);
select tests.publish_with('{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"}}') ->> 'ok' as ok_mc_auto;
reset role;
select public.get_public_site('esha-patel') as mc \gset
select tests.ok((:'mc')::jsonb -> 'companies'
                  = '[{"name":"भारत लाइफ","insurance_types":["motor"]},{"name":"एक्मे लाइफ","insurance_types":["life","health"]}]'::jsonb,
  '#MC companies: approved, active companies, earliest approval first, names in the site''s language, types in catalog order (pending and inactive left out)',
  (:'mc')::jsonb ->> 'companies');
select tests.ok((select bool_and((select array_agg(k order by k) from jsonb_object_keys(e) k) = array['insurance_types', 'name'])
                   from jsonb_array_elements((:'mc')::jsonb -> 'companies') e)
                and (:'mc')::text not like '%acme-life%' and (:'mc')::text not like '%bharat-life%'
                and (:'mc')::text not like '%company-media/logos%' and (:'mc')::text not like '%c1c1c1c1%'
                and (:'mc')::text not like '%Zeta%' and (:'mc')::text not like '%ज़ेटा%' and (:'mc')::text not like '%पुरानी%',
  '#MC each company is {name, insurance_types} only: no logo, slug or id; the pending and inactive companies appear nowhere');
select tests.ok(((:'mc')::jsonb - 'companies')::text not like '%Acme Life%' and ((:'mc')::jsonb - 'companies')::text not like '%एक्मे लाइफ%'
                and ((:'mc')::jsonb - 'companies')::text not like '%भारत लाइफ%',
  '#MC company names appear only in companies (not in company or offers)');
select tests.ok((:'mc')::jsonb -> 'company' = '{"disclaimer":"भारत की शर्तें लागू।"}'::jsonb,
  '#MC the disclaimer is the earliest-approved company''s');
select tests.ok((select array_agg(e ->> 'id' order by n) from jsonb_array_elements((:'mc')::jsonb #> '{offers,items}') with ordinality x(e, n))
                  = array[trim(both '''' from :'P1'), trim(both '''' from :'O1')],
  '#MC auto offers come from every approved company, in turn (earliest-approved company first)');

select (public.get_public_site('esha-patel') -> 'companies')::text as mc_companies \gset
select tests.as_agent(:E);
select tests.ok(public.preview_site_offers('{"hero":{"headline":"Esha Patel"}}', 'classic') -> 'companies' = (:'mc_companies')::jsonb,
  '#MC preview_site_offers carries the same companies list');
select tests.ok((select array_agg(distinct o ->> 'company_id' order by o ->> 'company_id') from jsonb_array_elements(public.my_company_offers() -> 'offers') o)
                  = array[trim(both '''' from :'C1'), trim(both '''' from :'C2')]
                and jsonb_array_length(public.my_company_offers() -> 'memberships') = 4,
  '#MC my_company_offers: offers of both approved, active companies, each tagged with its company_id; every membership listed');
select tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel',
  'featuredOffer', jsonb_build_object('source', 'company', 'id', trim(both '''' from :'O1'))), 'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'company', 'id', trim(both '''' from :'P1')))))) as r \gset
select tests.ok((:'r')::jsonb ->> 'ok' = 'true' and not (:'r')::jsonb ? 'warnings',
  '#MC offers of either approved company can be picked (hero from one, section from the other)', :'r');
reset role;
select tests.ok(public.get_public_site('esha-patel') #>> '{offers,featured,id}' = trim(both '''' from :'O1')
                and public.get_public_site('esha-patel') #>> '{offers,items,0,id}' = trim(both '''' from :'P1')
                and public.get_public_site('esha-patel') #>> '{offers,items,0,cta,kind}' = 'link',
  '#MC both picked offers render');
select tests.as_agent(:E);
select tests.ok(tests.has_issue(tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel',
                  'featuredOffer', jsonb_build_object('source', 'company', 'id', trim(both '''' from :'R1'))), 'contact', '{"phone":"9876500001"}'::jsonb)),
                '{"section":"hero","field":"featuredOffer","rule":"offer_wrong_company"}'),
  '#MC an offer of a third company the agent never asked for is rejected (offer_wrong_company)');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel'), 'contact', '{"phone":"9876500001"}'::jsonb,
                  'offers', jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'company', 'id', 'c3000000-0000-4000-8000-000000000000'))))),
                '{"rule":"offer_wrong_company"}'),
  '#MC an unknown offer is still offer_wrong_company');
reset role;

-- Custom offers: allowed unless any approved, active company turned them off (the strictest wins).
create temp table mc_custom (c jsonb);
insert into mc_custom values (jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel'), 'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'agent', 'id', 'abcdef01-0000-4000-8000-000000000001')),
    'custom', jsonb_build_array(jsonb_build_object('id', 'abcdef01-0000-4000-8000-000000000001', 'title', 'Free policy review',
      'endsOn', (current_date + 5)::text, 'ctaKind', 'call', 'confirmed', true)))));
grant select on mc_custom to authenticated;
update public.companies set allow_custom_offers = false where id = :C3;
select tests.as_agent(:E);
select tests.ok(tests.publish_with((select c from mc_custom)) ->> 'ok' = 'true',
  '#MC an inactive company''s switch doesn''t count');
reset role;
select tests.ok(jsonb_array_length(public.get_public_site('esha-patel') #> '{offers,items}') = 1, '#MC ... and the custom offer renders');
update public.companies set allow_custom_offers = false where id = :C2;
select tests.ok(public.get_public_site('esha-patel') #> '{offers,items}' = '[]'::jsonb,
  '#MC one approved company turning custom offers off drops them from the site');
select tests.as_agent(:E);
select tests.ok(tests.has_issue(tests.publish_with((select c from mc_custom)), '{"section":"offers","field":"items","item":0,"rule":"custom_offers_off"}'),
  '#MC ... and publishing them is custom_offers_off (the strictest company wins)');
reset role;
update public.companies set allow_custom_offers = true where id = :C2;
update public.companies set allow_custom_offers = false where id = :C1;
select tests.as_agent(:E);
select tests.ok(tests.has_issue(tests.publish_with((select c from mc_custom)), '{"rule":"custom_offers_off"}'),
  '#MC ... whichever approved company it is');
reset role;

update public.company_memberships set status = 'revoked', review_note = 'Left' where agent_id = :E and company_id = :C2;
select tests.ok(public.get_public_site('esha-patel') -> 'companies' = '[{"name":"एक्मे लाइफ","insurance_types":["life","health"]}]'::jsonb
                and public.get_public_site('esha-patel') -> 'company' = '{"disclaimer":"बीमा आग्रह की विषय वस्तु है।"}'::jsonb,
  '#MC revoking one company removes it from companies; the disclaimer moves to the next-approved company');
select tests.ok(public.get_public_site('farhan-ali') -> 'companies' = '[]'::jsonb,
  '#MC an agent without an approved company: companies is []');
alter table public.companies disable trigger companies_guard;
update public.companies set name = '{"en":"Acme Life"}' where id = :C1;
alter table public.companies enable trigger companies_guard;
select tests.ok(public.get_public_site('esha-patel') #>> '{companies,0,name}' = 'Acme Life',
  '#MC a name missing in the site''s language falls back to the default locale');
rollback;

-- ---------------------------------------------------------------------------
-- publish_site and the public payload
-- ---------------------------------------------------------------------------
\set U1 '''abcdef01-0000-4000-8000-000000000001'''
\set U2 '''abcdef02-0000-4000-8000-000000000002'''
begin;
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values (:E, :C1, 'E-123', '{life}');
update public.company_memberships set status = 'approved' where agent_id = :E and status = 'pending';
create temp table good (c jsonb);
insert into good values (replace(replace(replace(replace($j$
{"hero":{"headline":"Esha Patel","featuredOffer":{"source":"company","id":"O1"}},
 "contact":{"phone":"9876500001"},
 "offers":{"items":[{"source":"company","id":"O2"},{"source":"company","id":"O3"},{"source":"agent","id":"U1"}],
           "custom":[{"id":"U1","title":"Free policy review","summary":"Bring your papers","endsOn":"OK","ctaKind":"call","confirmed":true}]}}
$j$, '"O1"', '"' || trim(both '''' from :'O1') || '"'), '"O2"', '"' || trim(both '''' from :'O2') || '"'),
     '"O3"', '"' || trim(both '''' from :'O3') || '"'), '"OK"', '"' || (current_date + 10)::text || '"')::jsonb);
update good set c = replace(c::text, '"U1"', '"' || trim(both '''' from :'U1') || '"')::jsonb;
grant select on good to authenticated;

select tests.as_agent(:E);
select tests.publish_with((select c from good)) as r \gset
select tests.ok((:'r')::jsonb ->> 'ok' = 'true', '#S a valid draft with offers publishes');
select tests.ok((:'r')::jsonb -> 'warnings' = '[{"section":"offers","field":"items","item":1,"rule":"offer_ended"}]'::jsonb,
  '#S an ended offer is a warning, not an error');
reset role;

select tests.as_anon();
select public.get_public_site('esha-patel') as p \gset
reset role;
select tests.ok((:'p')::jsonb -> 'company' = jsonb_build_object('disclaimer', 'बीमा आग्रह की विषय वस्तु है।'),
  '#S payload company: only the disclaimer, in the site''s language (no name, no logo; DECISIONS #77)');
select tests.ok(((:'p')::jsonb - 'companies')::text not like '%Acme Life%' and ((:'p')::jsonb - 'companies')::text not like '%एक्मे लाइफ%'
                and (:'p')::text not like '%company-media/logos%' and (:'p')::text not like '%acme-life%'
                and (:'p')::jsonb -> 'companies' = '[{"name":"एक्मे लाइफ","insurance_types":["life"]}]'::jsonb,
  '#S the company''s name only in companies (DECISIONS #90, in the site''s language); its slug and logo nowhere');
select tests.ok((:'p')::jsonb #> '{offers,featured}' @> jsonb_build_object(
                  'source', 'company', 'id', trim(both '''' from :'O1'), 'title', 'टर्म कवर ऑफ़र', 'summary', 'इस महीने कम प्रीमियम',
                  'image_url', null, 'cta', '{"kind":"enquire","interest":"term"}'::jsonb, 'from_agent', false,
                  'ends_on', (select (ends_at at time zone 'Asia/Kolkata')::date from public.offers where id = :O1)),
  '#S payload featured: the company offer in Hindi, no image, the enquire CTA with its interest, the IST end date');
select tests.ok(jsonb_array_length((:'p')::jsonb #> '{offers,items}') = 1
                and (:'p')::jsonb #> '{offers,items,0}' @> jsonb_build_object('source', 'agent', 'id', trim(both '''' from :'U1'),
                     'title', 'Free policy review', 'from_agent', true, 'image_url', null, 'cta', '{"kind":"call"}'::jsonb),
  '#S payload items: upcoming and ended offers skipped; the custom offer marked from_agent');
select tests.ok(public.get_public_site('farhan-ali') -> 'company' = 'null'::jsonb
                and public.get_public_site('farhan-ali') -> 'companies' = '[]'::jsonb
                and public.get_public_site('farhan-ali') -> 'offers' = '{"featured":null,"items":[]}'::jsonb,
  '#S an agent without a company: company null, companies [], no offers');

select tests.as_agent(:E);
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{hero,featuredOffer,id}', to_jsonb(trim(both '''' from :'P1')))),
                                '{"section":"hero","field":"featuredOffer","rule":"offer_wrong_company"}'),
  '#S another company''s offer -> offer_wrong_company');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{hero,featuredOffer,id}', '"0f0000ff-0000-4000-8000-0000000000ff"')),
                                '{"rule":"offer_wrong_company"}'),
  '#S an unknown company offer -> offer_wrong_company');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{hero,featuredOffer,source}', '"x"')),
                                '{"section":"hero","field":"featuredOffer","rule":"offer_shape"}'),
  '#S a reference with an unknown source -> offer_shape');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{hero,featuredOffer}', '"not an object"')),
                                '{"section":"hero","field":"featuredOffer","rule":"offer_shape"}'),
  '#S a reference that isn''t an object -> offer_shape');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,items}',
                                  ((select c from good) #> '{offers,items}') || ((select c from good) #> '{offers,items,0}'))),
                                '{"section":"offers","field":"items","item":3,"rule":"duplicate"}'),
  '#S an offer placed twice -> duplicate');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,items,2,id}', '"abcdef09-0000-4000-8000-000000000009"')),
                                '{"section":"offers","field":"items","item":2,"rule":"offer_custom_missing"}'),
  '#S a reference to a missing custom offer -> offer_custom_missing');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,custom,0,confirmed}', 'false')),
                                '{"section":"offers","field":"confirmed","item":0,"rule":"equals"}'),
  '#S an unconfirmed custom offer -> equals');
select tests.ok(tests.has_issue(tests.publish_with((select c from good) #- '{offers,custom,0,confirmed}'),
                                '{"section":"offers","field":"confirmed","item":0,"rule":"equals"}'),
  '#S a custom offer without the confirmation -> equals');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,custom,0,endsOn}', to_jsonb((current_date + 400)::text))),
                                '{"section":"offers","field":"endsOn","item":0,"rule":"max_days_ahead"}'),
  '#S a custom offer ending too far ahead -> max_days_ahead');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,custom,0,endsOn}', '"soon"')),
                                '{"section":"offers","field":"endsOn","item":0,"rule":"format"}'),
  '#S a custom offer end that isn''t a date -> format');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,custom,0,ctaKind}', '"link"')),
                                '{"section":"offers","field":"ctaKind","item":0,"rule":"option"}'),
  '#S an agent can''t use the link CTA -> option');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,custom,0,id}', '"abc"')),
                                '{"section":"offers","field":"id","item":0,"rule":"offer_shape"}'),
  '#S a custom offer id that isn''t a uuid -> offer_shape');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,custom}',
                                  ((select c from good) #> '{offers,custom}') || ((select c from good) #> '{offers,custom}'))),
                                '{"section":"offers","field":"id","item":1,"rule":"duplicate"}'),
  '#S two custom offers with one id -> duplicate');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from good), '{offers,items}',
                                  (select jsonb_agg(jsonb_build_object('source', 'agent', 'id', 'abcdef0' || g || '-0000-4000-8000-000000000000'))
                                     from generate_series(1, 7) g))),
                                '{"section":"offers","field":"items","rule":"maxItems"}'),
  '#S more than 6 placed offers -> maxItems');
select tests.ok(tests.publish_with(jsonb_set(jsonb_set((select c from good), '{hidden}', '["offers"]'), '{offers,items,0,id}', to_jsonb(trim(both '''' from :'P1'))))
                ->> 'ok' = 'true',
  '#S a hidden offers section''s references aren''t checked');
reset role;

update public.companies set allow_custom_offers = false where id = :C1;
select tests.as_agent(:E);
select tests.ok(tests.has_issue(tests.publish_with((select c from good)), '{"section":"offers","field":"items","item":2,"rule":"custom_offers_off"}'),
  '#S the company turned custom offers off -> custom_offers_off');
reset role;
rollback;

-- Payload after changes: an ended custom offer, the logo permission ending, an archived offer, a revoked link.
begin;
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values (:E, :C1, 'E-123', '{life}');
update public.company_memberships set status = 'approved' where agent_id = :E and status = 'pending';
create temp table good (c jsonb);
insert into good values (jsonb_build_object(
  'hero', jsonb_build_object('headline', 'Esha Patel', 'featuredOffer', jsonb_build_object('source', 'company', 'id', trim(both '''' from :'O1'))),
  'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object(
    'items', jsonb_build_array(jsonb_build_object('source', 'agent', 'id', trim(both '''' from :'U1')),
                               jsonb_build_object('source', 'agent', 'id', trim(both '''' from :'U2'))),
    'custom', jsonb_build_array(
      jsonb_build_object('id', trim(both '''' from :'U1'), 'title', 'Free review', 'endsOn', (current_date + 10)::text, 'ctaKind', 'enquire', 'interest', 'term', 'confirmed', true),
      jsonb_build_object('id', trim(both '''' from :'U2'), 'title', 'Old offer', 'endsOn', (current_date - 3)::text, 'ctaKind', 'call', 'confirmed', true)))));
grant select on good to authenticated;
select tests.as_agent(:E);
select tests.publish_with((select c from good)) as r \gset
reset role;
select tests.ok((:'r')::jsonb -> 'warnings' = '[{"section":"offers","field":"items","item":1,"rule":"offer_ended"}]'::jsonb,
  '#S an ended custom offer is a warning');
select tests.ok(jsonb_array_length(public.get_public_site('esha-patel') #> '{offers,items}') = 1
                and public.get_public_site('esha-patel') #> '{offers,items,0,cta}' = '{"kind":"enquire","interest":"term"}'::jsonb,
  '#S an ended custom offer is dropped from the payload');

update public.companies set logo_permission_until = current_date - 2 where id = :C1;
select tests.ok(public.get_public_site('esha-patel') -> 'company' = jsonb_build_object('disclaimer', 'बीमा आग्रह की विषय वस्तु है।'),
  '#S the site payload never carries the logo or the name, with or without a permission');
select tests.as_agent(:E);
select tests.ok(public.list_companies() -> 0 ->> 'logo_url' is null, '#S ... and from list_companies');
reset role;

update public.offers set status = 'archived' where id = :O1;
select tests.ok(public.get_public_site('esha-patel') #> '{offers,featured}' = 'null'::jsonb, '#S an archived offer leaves the site at once');
select tests.as_agent(:E);
select tests.ok(tests.has_issue(tests.publish_with((select c from good)), '{"section":"hero","field":"featuredOffer","rule":"offer_unavailable"}', true),
  '#S an archived offer is a warning (offer_unavailable)');
reset role;

update public.offers set status = 'draft' where id = :O1;
update public.offers set status = 'live' where id = :O1;
update public.company_memberships set status = 'revoked', review_note = 'Left' where agent_id = :E and status = 'approved';
select tests.ok(public.get_public_site('esha-patel') -> 'company' = 'null'::jsonb
                and public.get_public_site('esha-patel') #> '{offers,featured}' = 'null'::jsonb
                and jsonb_array_length(public.get_public_site('esha-patel') #> '{offers,items}') = 1,
  '#S revoked: no company or company offers on the site; custom offers stay');
select tests.as_agent(:E);
select tests.ok(tests.has_issue(tests.publish_with((select c from good)), '{"section":"hero","field":"featuredOffer","rule":"offer_unavailable"}', true)
                and tests.publish_with((select c from good)) ->> 'ok' = 'true',
  '#S after a revocation the old company''s offer is a warning, not an error');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- admin_offer_usage and storage
-- ---------------------------------------------------------------------------
begin;
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values (:E, :C1, 'E-123', '{life}');
update public.company_memberships set status = 'approved' where agent_id = :E and status = 'pending';
select tests.as_agent(:E);
select tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha',
  'featuredOffer', jsonb_build_object('source', 'company', 'id', trim(both '''' from :'O1'))), 'contact', '{"phone":"9876500001"}'::jsonb)) ->> 'ok' as ok1;
update public.sites set draft_content = jsonb_set(draft_content, '{offers}',
  jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'company', 'id', trim(both '''' from :'O2')))))
 where agent_id = :E;
select tests.throws(format($$select public.admin_offer_usage(%L)$$, trim(both '''' from :'O1')), '#U an agent can''t see offer usage', '%forbidden%');
reset role;
select tests.as_admin(:O);
select tests.ok(public.admin_offer_usage(:O1) = '{"published_sites":1,"draft_sites":1}'::jsonb
                and public.admin_offer_usage(:O2) = '{"published_sites":0,"draft_sites":1}'::jsonb,
  '#U admin_offer_usage returns counts only (published and draft)');
reset role;
select tests.as_admin(:S);
select tests.throws(format($$select public.admin_offer_usage(%L)$$, trim(both '''' from :'O1')), '#U a scoped editor can''t see another company''s usage', '%not_found%');
reset role;

select tests.as_admin(:O);
select tests.lives(format($$insert into storage.objects (bucket_id, name) values ('company-media', 'logos/%s/1.webp')$$, trim(both '''' from :'C1')),
  '#U the owner uploads a company logo');
select tests.throws($$insert into storage.objects (bucket_id, name) values ('company-media', 'misc/x.webp')$$,
  '#U only logos/<company>/ and offers/<offer>/ paths', '%row-level security%');
select tests.throws($$insert into storage.objects (bucket_id, name) values ('company-media', 'logos/not-a-uuid/1.webp')$$,
  '#U the folder must be a company id', '%row-level security%');
reset role;
select tests.as_admin(:S);
select tests.throws(format($$insert into storage.objects (bucket_id, name) values ('company-media', 'logos/%s/2.webp')$$, trim(both '''' from :'C1')),
  '#U a scoped editor can''t upload another company''s logo', '%row-level security%');
select tests.lives(format($$insert into storage.objects (bucket_id, name) values ('company-media', 'logos/%s/1.webp')$$, trim(both '''' from :'C2')),
  '#U a scoped editor uploads their company''s logo');
select tests.lives(format($$insert into storage.objects (bucket_id, name) values ('company-media', 'offers/%s/1.webp')$$, trim(both '''' from :'P1')),
  '#U a scoped editor uploads their offer''s image');
select tests.throws(format($$insert into storage.objects (bucket_id, name) values ('company-media', 'offers/%s/1.webp')$$, trim(both '''' from :'O1')),
  '#U a scoped editor can''t upload another company''s offer image', '%row-level security%');
reset role;
select tests.as_admin(:O, 'aal1');
select tests.throws(format($$insert into storage.objects (bucket_id, name) values ('company-media', 'logos/%s/3.webp')$$, trim(both '''' from :'C1')),
  '#U uploads need MFA', '%row-level security%');
reset role;
select tests.as_agent(:E);
select tests.throws(format($$insert into storage.objects (bucket_id, name) values ('company-media', 'logos/%s/4.webp')$$, trim(both '''' from :'C1')),
  '#U an agent can''t upload to company-media', '%row-level security%');
select tests.ok(tests.count($$select * from storage.objects where bucket_id = 'company-media'$$) = 0, '#U an agent can''t list company-media');
reset role;
select tests.ok((select public and file_size_limit = 524288 and allowed_mime_types = array['image/webp', 'image/png']
                   from storage.buckets where id = 'company-media'),
  '#U company-media: public, 512 KB, WebP and PNG only (no SVG)');
rollback;

-- ---------------------------------------------------------------------------
-- Public text check (DECISIONS #77): private.public_text_violation follows PublicText
-- (NFC + lowercase, whitespace runs, word boundaries that count Devanagari vowel signs and virama as letters)
-- ---------------------------------------------------------------------------
begin;
select tests.ok(private.public_text_violation('Buy an LIC policy today') = 'lic', '#T a blocked term is found (EN)');
select tests.ok(private.public_text_violation('JEEVAN    Anand plan') = 'jeevan anand'
                and private.public_text_violation('jeevan' || chr(10) || chr(9) || 'anand') = 'jeevan anand',
  '#T case-insensitive; a term''s words match across any run of whitespace');
select tests.ok(private.public_text_violation('jeevananand') is null, '#T a multi-word term needs whitespace between its words');
select tests.ok(private.public_text_violation('A public publication about policies') is null,
  '#T a term inside a longer word isn''t matched (lic in public, policies)');
select tests.ok(private.public_text_violation('lic123') is null and private.public_text_violation('2lic') is null,
  '#T a digit next to the term is part of the word');
select tests.ok(private.public_text_violation('(LIC)') = 'lic' and private.public_text_violation('lic-plan') = 'lic',
  '#T punctuation is a boundary');
select tests.ok(private.public_text_violation('Call L.I.C. now') = 'l.i.c.', '#T punctuation inside a term is matched literally');
select tests.ok(private.public_text_violation('Jeevan bhar ki suraksha, bima aur health plan') is null
                and private.public_text_violation('जीवन भर की सुरक्षा, बीमा') is null,
  '#T generic single words (jeevan, bima, जीवन, बीमा, health) are never blocked');
select tests.ok(private.public_text_violation('एलआईसी की पॉलिसी') = 'एलआईसी', '#T a Devanagari term is found (HI)');
select tests.ok(private.public_text_violation('यह एलआईसी।') = 'एलआईसी', '#T the danda is a boundary');
select tests.ok(private.public_text_violation('एलआईसीकरण') is null, '#T a Devanagari term inside a longer word isn''t matched');
select tests.ok(private.public_text_violation('एलआईसी' || chr(2366)) is null and private.public_text_violation('नि' || 'एलआईसी') is null,
  '#T a vowel sign (matra) after or before the term counts as part of the word');
select tests.ok(private.public_text_violation('एलआईसी' || chr(2381) || 'स') is null, '#T a virama after the term counts as part of the word');
select tests.ok(private.public_text_violation('जीवन आ' || chr(2395) || 'ाद योजना') is not null
                and private.public_text_violation('जीवन आज' || chr(2364) || 'ाद योजना') is not null,
  '#T NFC: precomposed and decomposed nukta forms both match');
select tests.ok(private.public_text_violation('Get Acme   Life cover') = 'acme life'
                and private.public_text_violation('एक्मे लाइफ का प्लान') = 'एक्मे लाइफ',
  '#T every company''s name in every locale is blocked');
select tests.ok(private.public_text_violation('Old Life savings') = 'old life', '#T inactive companies'' names are blocked too');
select tests.ok(private.public_text_violation('Acme Lifestyle tips') is null, '#T a company name inside a longer word isn''t matched');
select tests.ok(private.public_text_violation(null) is null and private.public_text_violation('  ') is null, '#T null or blank text passes');
select tests.ok(not has_function_privilege('anon', 'private.public_text_violation(text)', 'EXECUTE')
                and not has_function_privilege('authenticated', 'private.public_text_violation(text)', 'EXECUTE')
                and not (select prosecdef from pg_proc where oid = 'private.public_text_violation(text)'::regprocedure),
  '#T public_text_violation: no API role may call it; security invoker');
rollback;

-- Guards: offers and companies reject public text with a blocked term or a company name, in EN and HI
\set G1 '''0f0000b1-0000-4000-8000-0000000000b1'''
\set G2 '''0f0000b2-0000-4000-8000-0000000000b2'''
begin;
select tests.as_admin(:O);
select tests.throws($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"title":"LIC term plan"}}', now(), now() + interval '1 day')$$,
  '#T offer title with a blocked term (EN)', 'blocked_term:title:en:lic');
select tests.throws($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"title":"Plan"},"hi":{"title":"प्लान","summary":"एलआईसी का प्लान"}}', now(), now() + interval '1 day')$$,
  '#T offer summary with a blocked term (HI)', 'blocked_term:summary:hi:एलआईसी');
select tests.throws($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"title":"Plan","details":"Backed by Acme Life."}}', now(), now() + interval '1 day')$$,
  '#T offer details with a company name (EN)', 'blocked_term:details:en:acme life');
select tests.throws($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"hi":{"title":"प्लान","badge":"भारत लाइफ"}}', now(), now() + interval '1 day')$$,
  '#T offer badge with another company''s Hindi name', 'blocked_term:badge:hi:भारत लाइफ');
select tests.throws($$insert into public.offers (company_id, content, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"hi":{"title":"प्लान","disclaimer":"पुरानी लाइफ की शर्तें"}}', now(), now() + interval '1 day')$$,
  '#T offer disclaimer with an inactive company''s name (HI)', 'blocked_term:disclaimer:hi:पुरानी लाइफ');
select tests.throws($$insert into public.offers (company_id, content, cta_kind, cta_url, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"title":"Plan"}}', 'link', 'https://licindia.in/offer', now(), now() + interval '1 day')$$,
  '#T a link URL naming an insurer', 'blocked_term:cta_url::licindia');
select tests.throws($$insert into public.offers (company_id, content, image_url, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"title":"Plan"}}', 'https://cdn.example/offers/lic-plan.webp', now(), now() + interval '1 day')$$,
  '#T an image URL naming an insurer', 'blocked_term:image_url::lic');
select tests.lives($$insert into public.offers (company_id, content, internal_name, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '{"en":{"title":"Public sector employees'' plan"}}', 'Jeevan Anand (Plan 915)', now(), now() + interval '1 day')$$,
  '#T a term inside a longer word passes; internal_name (the real name) isn''t checked');
select tests.throws(format($$insert into public.offers (company_id, internal_name, starts_at, ends_at)
  values ('c1c1c1c1-0000-4000-8000-0000000000c1', '%s', now(), now() + interval '1 day')$$, repeat('x', 121)),
  '#T internal_name at most 120 characters', '%offers_internal_name_length%');
select tests.throws($$update public.offers set content = jsonb_set(content, '{en,title}', '"New Jeevan Anand offer"')
  where id = '0f000001-0000-4000-8000-000000000001'$$,
  '#T editing a live offer''s text is checked too', 'blocked_term:title:en:jeevan anand');

-- Going live re-checks (a company added meanwhile); archiving and other edits never do.
insert into public.offers (id, company_id, content, starts_at, ends_at)
values (:G1, :C1, '{"en":{"title":"Zeta Cover offer","summary":"S"},"hi":{"title":"ऑफ़र","summary":"सारांश"}}', now(), now() + interval '10 days'),
       (:G2, :C1, '{"en":{"title":"Omega Plus offer","summary":"S"},"hi":{"title":"ऑफ़र","summary":"सारांश"}}', now(), now() + interval '10 days');
update public.offers set status = 'live' where id = :G2;
insert into public.companies (slug, name) values ('zeta-cover', '{"en":"Zeta Cover","hi":"ज़ेटा कवर"}'),
                                                 ('omega-plus', '{"en":"Omega Plus","hi":"ओमेगा प्लस"}');
select tests.throws($$update public.offers set status = 'live' where id = '0f0000b1-0000-4000-8000-0000000000b1'$$,
  '#T going live re-checks the text against companies added since', 'blocked_term:title:en:zeta cover');
select tests.lives($$update public.offers set sort = 7 where id = '0f0000b2-0000-4000-8000-0000000000b2'$$,
  '#T other edits of an offer that now names a company still work');
select tests.lives($$update public.offers set status = 'archived' where id = '0f0000b2-0000-4000-8000-0000000000b2'$$,
  '#T archiving always works');

select tests.throws($$insert into public.companies (slug, name, disclaimer) values ('q-life', '{"en":"Q Life","hi":"क्यू लाइफ"}', '{"en":"LIC terms apply"}')$$,
  '#T company disclaimer with a blocked term (EN)', 'blocked_term:disclaimer:en:lic');
select tests.throws($$insert into public.companies (slug, name, disclaimer) values ('zed-life', '{"en":"Zed Life","hi":"ज़ेड लाइफ"}', '{"hi":"ज़ेड लाइफ की शर्तें"}')$$,
  '#T company disclaimer naming the company itself (HI)', 'blocked_term:disclaimer:hi:ज़ेड लाइफ');
select tests.throws($$update public.companies set disclaimer = '{"en":"See Acme Life for terms"}' where id = 'c2c2c2c2-0000-4000-8000-0000000000c2'$$,
  '#T company disclaimer naming another company', 'blocked_term:disclaimer:en:acme life');
select tests.throws($$update public.companies set name = '{"en":"Subject Matter","hi":"एक्मे लाइफ"}' where id = 'c1c1c1c1-0000-4000-8000-0000000000c1'$$,
  '#T renaming a company re-checks its own disclaimer', 'blocked_term:disclaimer:en:subject matter');
select tests.lives($$update public.companies set disclaimer = '{"en":"Insurance is the subject matter of solicitation."}' where id = 'c2c2c2c2-0000-4000-8000-0000000000c2'$$,
  '#T a clean disclaimer passes');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- internal_name never leaves the admin panel; the company payload is {disclaimer} only
-- ---------------------------------------------------------------------------
begin;
update public.offers set internal_name = 'SECRET-INTERNAL-' || sort where company_id in (:C1, :C2);
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values (:E, :C1, 'E-123', '{life}');
update public.company_memberships set status = 'approved' where agent_id = :E and status = 'pending';
select tests.as_agent(:E);
select tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha',
  'featuredOffer', jsonb_build_object('source', 'company', 'id', trim(both '''' from :'O1'))),
  'contact', '{"phone":"9876500001"}'::jsonb)) ->> 'ok' as ok_internal;
select tests.ok(public.my_company_offers()::text not like '%SECRET-INTERNAL%'
                and jsonb_array_length(public.my_company_offers() -> 'offers') = 2,
  '#I my_company_offers never carries internal_name');
select tests.ok(tests.count($$select internal_name from public.offers$$) = 0, '#I an agent reads no offers (or internal names) directly');
reset role;
select tests.as_anon();
select public.get_public_site('esha-patel') as pi \gset
reset role;
select tests.ok((:'pi')::text not like '%SECRET-INTERNAL%' and (:'pi')::text not like '%internal_name%'
                and public.get_public_site_by_host('nothing.example') is null,
  '#I the public payload never carries internal_name');
select tests.ok((select array_agg(k order by k) from jsonb_object_keys((:'pi')::jsonb -> 'company') k) = array['disclaimer'],
  '#I payload company has only the disclaimer key');
select tests.ok((select bool_and(o.v ->> 'title' is not null and o.v ->> 'from_agent' = 'false')
                   from (select (:'pi')::jsonb #> '{offers,featured}' as v
                         union all select e from jsonb_array_elements((:'pi')::jsonb #> '{offers,items}') e) o)
                and ((:'pi')::jsonb - 'companies')::text not like '%Acme Life%' and ((:'pi')::jsonb - 'companies')::text not like '%एक्मे लाइफ%',
  '#I offers carry their public title and no company name (names only in companies)');
select tests.as_admin(:O);
select tests.ok((select internal_name from public.offers where id = :O1) = 'SECRET-INTERNAL-1', '#I an admin reads internal_name');
reset role;
select tests.as_admin(:S);
select tests.ok(tests.count($$select internal_name from public.offers where company_id = 'c1c1c1c1-0000-4000-8000-0000000000c1'$$) = 0,
  '#I a scoped editor can''t read another company''s internal names');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- Auto offers (DECISIONS #79)
-- ---------------------------------------------------------------------------
\set A1 '''0f0000c1-0000-4000-8000-0000000000c1'''
\set A2 '''0f0000c2-0000-4000-8000-0000000000c2'''
\set A3 '''0f0000c3-0000-4000-8000-0000000000c3'''
\set A4 '''0f0000c4-0000-4000-8000-0000000000c4'''
begin;
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) values (:E, :C1, 'E-123', '{life}');
update public.company_memberships set status = 'approved' where agent_id = :E and status = 'pending';
-- Running live offers besides O1 (sort 1): A1 sort 0, A2 sort 2 (earlier start), A3 sort 2 (later start), A4 sort 9.
insert into public.offers (id, company_id, content, image_url, starts_at, ends_at, sort) values
  (:A1, :C1, :CONTENT, 'https://cdn.example/company-media/offers/a1/1.webp', now() - interval '2 days', now() + interval '30 days', 0),
  (:A2, :C1, :CONTENT, null, now() - interval '3 days', now() + interval '30 days', 2),
  (:A3, :C1, :CONTENT, null, now() - interval '1 day', now() + interval '30 days', 2),
  (:A4, :C1, :CONTENT, null, now() - interval '1 day', now() + interval '30 days', 9);
update public.offers set status = 'live' where id in (:A1, :A2, :A3, :A4);
create temp table ids (k text, id text);
insert into ids values ('O1', trim(both '''' from :'O1')), ('A1', trim(both '''' from :'A1')), ('A2', trim(both '''' from :'A2')),
                       ('A3', trim(both '''' from :'A3')), ('U1', 'abcdef01-0000-4000-8000-000000000001');
grant select on ids to anon, authenticated;
create function tests.offer_keys(p_offers jsonb) returns text[] language sql as $$
  select coalesce(array_agg(coalesce(i.k, e.v ->> 'id') order by e.n), '{}')
    from jsonb_array_elements(coalesce(p_offers, '[]'::jsonb)) with ordinality e(v, n) left join ids i on i.id = e.v ->> 'id';
$$;
grant execute on function tests.offer_keys(jsonb) to anon, authenticated;

select tests.as_agent(:E);
select tests.publish_with('{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"}}') ->> 'ok' as ok_auto;
reset role;
select tests.ok(tests.offer_keys(public.get_public_site('esha-patel') #> '{offers,items}') = array['A1', 'O1', 'A2']
                and public.get_public_site('esha-patel') #> '{offers,featured}' = 'null'::jsonb,
  '#A no picks: up to 3 running offers by sort then start (upcoming, ended, archived and draft left out); no featured on a "Meet me" template');
update public.offers set cta_kind = 'call', interest = 'health' where id = :A2;
select tests.ok((select array_agg(e ->> 'interest' order by n) from jsonb_array_elements(public.get_public_site('esha-patel') #> '{offers,items}') with ordinality x(e, n))
                  = array[null, 'term', 'health']
                and public.get_public_site('esha-patel') #> '{offers,items,2,cta}' = '{"kind":"call"}'::jsonb
                and public.get_public_site('esha-patel') #> '{offers,items,1,cta}' = '{"kind":"enquire","interest":"term"}'::jsonb,
  '#A every offer carries its interest (null when unset), whatever the CTA kind; cta.interest stays on enquire');
select tests.ok(public.get_public_site('esha-patel') #>> '{offers,items,0,title}' = 'टर्म कवर ऑफ़र'
                and public.get_public_site('esha-patel') #>> '{offers,items,0,image_url}' = 'https://cdn.example/company-media/offers/a1/1.webp',
  '#A auto offers are resolved like picked ones (site language, image in the section)');

update public.sites set template_key = 'spotlight' where agent_id = :E;
select tests.ok(public.get_public_site('esha-patel') #>> '{offers,featured,id}' = trim(both '''' from :'A1')
                and public.get_public_site('esha-patel') #> '{offers,featured,image_url}' = 'null'::jsonb
                and tests.offer_keys(public.get_public_site('esha-patel') #> '{offers,items}') = array['O1', 'A2'],
  '#A "Best offers" family: the first auto offer is featured (no image) and the rest are the items (3 in all)');
update public.sites set template_key = 'guide' where agent_id = :E;
select tests.ok(public.get_public_site('esha-patel') #>> '{offers,featured,id}' = trim(both '''' from :'A1'),
  '#A the guide template (offers family) features too');

select tests.as_agent(:E);
select tests.publish_with('{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"},"hidden":["offers"]}') ->> 'ok' as ok_hidden;
reset role;
select tests.ok(public.get_public_site('esha-patel') -> 'offers' = '{"featured":null,"items":[]}'::jsonb,
  '#A a hidden offers section gets no auto offers and no auto featured');

select tests.as_agent(:E);
select tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel',
  'featuredOffer', jsonb_build_object('source', 'company', 'id', trim(both '''' from :'O1'))), 'contact', '{"phone":"9876500001"}'::jsonb)) ->> 'ok' as ok_feat;
reset role;
select tests.ok(public.get_public_site('esha-patel') #>> '{offers,featured,id}' = trim(both '''' from :'O1')
                and tests.offer_keys(public.get_public_site('esha-patel') #> '{offers,items}') = array['A1', 'A2', 'A3'],
  '#A a picked hero offer stays featured; the auto items leave it out');
select tests.ok(public.get_public_site('esha-patel') #>> '{offers,featured,interest}' = 'term',
  '#A the featured offer carries its interest');

select tests.as_agent(:E);
select tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel'), 'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'company', 'id', trim(both '''' from :'A3')))))) ->> 'ok' as ok_pick;
reset role;
select tests.ok(tests.offer_keys(public.get_public_site('esha-patel') #> '{offers,items}') = array['A3']
                and public.get_public_site('esha-patel') #> '{offers,featured}' = 'null'::jsonb,
  '#A picked offers replace the auto ones (and no auto featured)');

select tests.as_agent(:E);
select tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel'), 'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object('items', '[]'::jsonb, 'custom', jsonb_build_array(jsonb_build_object('id', 'abcdef01-0000-4000-8000-000000000001',
    'title', 'Free review', 'endsOn', (current_date + 5)::text, 'ctaKind', 'call', 'confirmed', true))))) ->> 'ok' as ok_custom;
reset role;
select tests.ok(public.get_public_site('esha-patel') -> 'offers' = '{"featured":null,"items":[]}'::jsonb,
  '#A a custom offer (even unplaced) turns auto offers off');
select tests.as_agent(:E);
select tests.publish_with(jsonb_build_object('hero', jsonb_build_object('headline', 'Esha Patel'), 'contact', '{"phone":"9876500001"}'::jsonb,
  'offers', jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'agent', 'id', 'abcdef01-0000-4000-8000-000000000001'),
                                                          jsonb_build_object('source', 'agent', 'id', 'abcdef02-0000-4000-8000-000000000002')),
    'custom', jsonb_build_array(
      jsonb_build_object('id', 'abcdef01-0000-4000-8000-000000000001', 'title', 'Free review', 'endsOn', (current_date + 5)::text,
                         'ctaKind', 'call', 'interest', 'pension', 'confirmed', true),
      jsonb_build_object('id', 'abcdef02-0000-4000-8000-000000000002', 'title', 'Ask me', 'endsOn', (current_date + 5)::text,
                         'ctaKind', 'call', 'confirmed', true))))) ->> 'ok' as ok_custom_interest;
reset role;
select tests.ok(public.get_public_site('esha-patel') #>> '{offers,items,0,interest}' = 'pension'
                and public.get_public_site('esha-patel') #> '{offers,items,0,cta}' = '{"kind":"call"}'::jsonb
                and public.get_public_site('esha-patel') #> '{offers,items,1}' ? 'interest'
                and public.get_public_site('esha-patel') #> '{offers,items,1,interest}' = 'null'::jsonb,
  '#A custom offers carry their stored interest (key present, null when unset)');

update public.company_memberships set status = 'revoked', review_note = 'Left' where agent_id = :E and status = 'approved';
select tests.as_agent(:E);
select tests.publish_with('{"hero":{"headline":"Esha Patel"},"contact":{"phone":"9876500001"}}') ->> 'ok' as ok_revoked;
reset role;
select tests.ok(public.get_public_site('esha-patel') -> 'offers' = '{"featured":null,"items":[]}'::jsonb
                and public.get_public_site('esha-patel') -> 'company' = 'null'::jsonb,
  '#A without an approved company there are no auto offers');
rollback;

-- ---------------------------------------------------------------------------
-- publish: custom offers' title and summary pass the public text check (rule blocked_term)
-- ---------------------------------------------------------------------------
begin;
create temp table cust (c jsonb);
insert into cust values (jsonb_build_object('hero', jsonb_build_object('headline', 'Farhan Ali'), 'contact', '{"phone":"9876500002"}'::jsonb,
  'offers', jsonb_build_object('items', jsonb_build_array(jsonb_build_object('source', 'agent', 'id', 'abcdef01-0000-4000-8000-000000000001')),
    'custom', jsonb_build_array(jsonb_build_object('id', 'abcdef01-0000-4000-8000-000000000001', 'title', 'Free policy review',
      'summary', 'Bring your papers', 'endsOn', (current_date + 5)::text, 'ctaKind', 'call', 'confirmed', true)))));
grant select on cust to authenticated;
select tests.as_agent(:F);
select tests.ok(tests.publish_with((select c from cust)) ->> 'ok' = 'true', '#P a clean custom offer publishes');
select tests.ok((select jsonb_agg(e) from jsonb_array_elements(tests.publish_with(jsonb_set((select c from cust), '{offers,custom,0,title}', '"LIC special review"')) -> 'errors') e)
                = '[{"section":"offers","field":"title","item":0,"rule":"blocked_term"}]'::jsonb,
  '#P a blocked term in a custom title -> {section:offers, field:title, item:0, rule:blocked_term}');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from cust), '{offers,custom,0,summary}', '"एलआईसी पॉलिसी की समीक्षा"')),
                                '{"section":"offers","field":"summary","item":0,"rule":"blocked_term"}'),
  '#P a blocked term in a custom summary (HI)');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set((select c from cust), '{offers,custom,0,title}', '"Acme Life cover check"')),
                                '{"section":"offers","field":"title","item":0,"rule":"blocked_term"}'),
  '#P a company name in a custom title (even without a company link)');
select tests.ok(tests.publish_with(jsonb_set((select c from cust), '{offers,custom,0,title}', '"Public sector policy review"')) ->> 'ok' = 'true',
  '#P a term inside a longer word doesn''t block publishing');
select tests.ok(tests.publish_with(jsonb_set(jsonb_set((select c from cust), '{offers,custom,0,title}', '"LIC special"'), '{hidden}', '["offers"]')) ->> 'ok' = 'true',
  '#P a hidden offers section''s custom offers aren''t checked');
select tests.ok(tests.has_issue(tests.publish_with(jsonb_set(jsonb_set(jsonb_set((select c from cust), '{offers,custom,0,title}', '"LIC special"'),
                                  '{hidden}', '["offers"]'), '{hero,featuredOffer}', '{"source":"agent","id":"abcdef01-0000-4000-8000-000000000001"}')),
                                '{"section":"offers","field":"title","item":0,"rule":"blocked_term"}'),
  '#P ... unless the hero features that custom offer');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- published_at and list_public_sites (the sitemap RPC)
-- ---------------------------------------------------------------------------
begin;
select tests.as_anon();
select tests.ok((public.get_public_site('esha-patel') ->> 'published_at') is not null, '#P published_at is in the public payload');
select tests.lives($$select * from public.list_public_sites()$$, '#P anon can execute list_public_sites');
select tests.ok(exists (select 1 from public.list_public_sites() where slug = 'esha-patel')
                and exists (select 1 from public.list_public_sites() where slug = 'chetan-lal')
                and not exists (select 1 from public.list_public_sites() where slug = 'sunita-rao'),
  '#P list_public_sites lists published sites and not unpublished ones');
select tests.ok(not exists (select 1 from public.list_public_sites() where slug = 'ramesh-sharma')
                and public.get_public_site('ramesh-sharma') ->> 'canonical_host' = 'ramesh.example.in',
  '#P a site with an active custom domain is left out (its domain serves its own sitemap)');
select tests.ok((select bool_and(r.published_at = s.published_at)
                   from public.list_public_sites() r join (select 'esha-patel' as slug, (public.get_public_site('esha-patel') ->> 'published_at')::timestamptz as published_at) s using (slug)),
  '#P list_public_sites returns the publish time');
select tests.ok(not exists (select 1 from public.list_public_sites() r
                             where exists (select 1 from jsonb_object_keys(to_jsonb(r)) k where k not in ('slug', 'published_at')))
                and (select count(*) from jsonb_object_keys((select to_jsonb(r) from public.list_public_sites() r limit 1))) = 2,
  '#P each row is exactly {slug, published_at}');
select tests.ok((select array_agg(published_at order by n) = array_agg(published_at order by published_at desc)
                   from (select published_at, row_number() over () as n from public.list_public_sites()) x),
  '#P newest first');
select tests.throws($$select * from public.sites$$, '#P anon still can''t read sites', '%permission denied%');
select tests.throws($$select private.public_text_violation('x')$$, '#P anon can''t call private helpers', '%permission denied%');
reset role;
update public.site_domains set status = 'renew_soon' where hostname = 'ramesh.example.in';
select tests.as_anon();
select tests.ok(not exists (select 1 from public.list_public_sites() where slug = 'ramesh-sharma'), '#P ... also while renew_soon');
reset role;
update public.site_domains set status = 'expired' where hostname = 'ramesh.example.in';
select tests.as_anon();
select tests.ok(exists (select 1 from public.list_public_sites() where slug = 'ramesh-sharma'), '#P an expired custom domain puts the free link back in the sitemap');
reset role;
update public.site_domains set status = 'active', expires_at = now() - interval '1 day' where hostname = 'ramesh.example.in';
select tests.as_anon();
select tests.ok(exists (select 1 from public.list_public_sites() where slug = 'ramesh-sharma'), '#P ... as does one past expires_at');
reset role;
update public.site_domains set status = 'active', expires_at = now() + interval '300 days', deleted_at = now() where hostname = 'ramesh.example.in';
select tests.as_anon();
select tests.ok(exists (select 1 from public.list_public_sites() where slug = 'ramesh-sharma'), '#P ... and a deleted one');
reset role;
update public.subscriptions set trial_ends_at = now() - interval '100 days' where agent_id = :F;
update public.sites set deleted_at = now() where agent_id = :E;
select tests.as_anon();
select tests.ok(not exists (select 1 from public.list_public_sites() where slug in ('farhan-ali', 'esha-patel'))
                and public.get_public_site('farhan-ali') is null,
  '#P lapsed owners and deleted sites are not listed (the get_public_site rule)');
reset role;
select tests.as_agent(:F);
select tests.lives($$select * from public.list_public_sites()$$, '#P authenticated can execute list_public_sites');
reset role;
select tests.ok(pg_get_function_result('public.list_public_sites()'::regprocedure) = 'TABLE(slug text, published_at timestamp with time zone)'
                and (select prosecdef and proconfig @> array['search_path=""'] from pg_proc where oid = 'public.list_public_sites()'::regprocedure)
                and not has_function_privilege('public', 'public.list_public_sites()', 'EXECUTE'),
  '#P list_public_sites: SECURITY DEFINER, search_path pinned, two columns, not executable by PUBLIC');
rollback;

-- ---------------------------------------------------------------------------
-- Demo seed (supabase/demo/demo_offers.sql): applies, is idempotent, and a "Best offers" demo site shows the
-- disguised offers with no company name or internal name anywhere
-- ---------------------------------------------------------------------------
\set G '''99999999-0000-4000-8000-000000000099'''
begin;
\i :demo_offers
select tests.ok((select count(*) from public.offers o join public.companies c on c.id = o.company_id
                  where c.slug = 'demo-life' and o.internal_name is not null) = 6,
  '#D demo seed: the demo company with 6 offers and their internal names');
select tests.ok((select bool_and((o.status = 'live') = (o.ends_at > now())) from public.offers o join public.companies c on c.id = o.company_id
                  where c.slug = 'demo-life'),
  '#D demo offers are live (unless already ended)');
\i :demo_offers
select tests.ok((select count(*) from public.companies where slug = 'demo-life') = 1
                and (select count(*) from public.offers o join public.companies c on c.id = o.company_id where c.slug = 'demo-life') = 6,
  '#D the demo seed is idempotent');
select tests.ok((select bool_and(o.image_url is null) from public.offers o join public.companies c on c.id = o.company_id where c.slug = 'demo-life'),
  '#D the demo seed leaves image_url null (the JSON image is for the web fixtures)');
select tests.ok((select bool_and(a.actor_kind = 'system' and a.actor_id is null) from public.audit_log a
                  where a.entity = 'offers' and a.entity_id in (select o.id from public.offers o join public.companies c on c.id = o.company_id where c.slug = 'demo-life')),
  '#D run as postgres: the audit trail records actor_kind system');

insert into auth.users (id, email, raw_user_meta_data) values (:G, 'demo.agent@example.com', '{"full_name":"Demo Agent"}');
update public.profiles set designation = 'Insurance advisor', city = 'Pune', phone = '+919876500009' where id = :G;
insert into public.company_memberships (agent_id, company_id, agency_code, insurance_types) select :G, id, 'D-1', '{life}' from public.companies where slug = 'demo-life';
update public.company_memberships set status = 'approved' where agent_id = :G;
select tests.as_agent(:G);
insert into public.sites (agent_id, slug, template_key, accent, locale, draft_content)
values (:G, 'demo-agent', 'spotlight', 'teal', 'en', '{"hero":{"headline":"Demo Agent"},"contact":{"phone":"9876500009"}}');
select public.publish_site() ->> 'ok' as demo_published;
reset role;
select tests.as_anon();
select public.get_public_site('demo-agent') as pd \gset
reset role;
create temp table demo_running as
  select o.id, o.content #>> '{en,title}' as title, row_number() over (order by o.sort, o.starts_at, o.id) as rn
    from public.offers o join public.companies c on c.id = o.company_id
   where c.slug = 'demo-life' and o.status = 'live' and o.starts_at <= now() and o.ends_at > now();
select tests.ok((:'pd')::jsonb -> 'company' = jsonb_build_object('disclaimer',
                  (select disclaimer ->> 'en' from public.companies where slug = 'demo-life')),
  '#D demo payload: company is {disclaimer} only');
select tests.ok(((:'pd')::jsonb - 'companies')::text not like '%Demo Life Insurer%' and ((:'pd')::jsonb - 'companies')::text not like '%डेमो जीवन बीमा कंपनी%'
                and (:'pd')::text not like '%demo-life%'
                and not exists (select 1 from public.offers o where o.internal_name is not null and strpos((:'pd')::text, o.internal_name) > 0)
                and private.public_text_violation(((:'pd')::jsonb - 'companies')::text) is null
                and (:'pd')::jsonb -> 'companies' = jsonb_build_array(jsonb_build_object('name',
                      (select name ->> 'en' from public.companies where slug = 'demo-life'), 'insurance_types', '["life"]'::jsonb)),
  '#D demo payload: the company name only in companies; no slug or internal name, and no blocked term elsewhere');
select tests.ok(case when (select count(*) from demo_running) = 0
                     then (:'pd')::jsonb -> 'offers' = '{"featured":null,"items":[]}'::jsonb
                     else (:'pd')::jsonb #>> '{offers,featured,title}' = (select title from demo_running where rn = 1)
                          and (:'pd')::jsonb #> '{offers,featured,image_url}' = 'null'::jsonb
                          and jsonb_array_length((:'pd')::jsonb #> '{offers,items}') = least(3, (select count(*) from demo_running)::int) - 1
                end,
  '#D demo "Best offers" site: the first running demo offer is featured, up to 3 in all, with their public titles',
  (:'pd')::jsonb ->> 'offers');
rollback;
