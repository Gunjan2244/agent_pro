-- TESTING.md Database #5: get_public_site returns nothing for unpublished sites and leaks no private fields.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''

begin;
select tests.as_anon();
select tests.ok(public.get_public_site('sunita-rao') is null, '#5 unpublished site -> null');
select tests.ok(public.get_public_site('no-such-slug') is null, '#5 unknown slug -> null');
select tests.ok(public.get_public_site('ramesh-sharma') is not null, '#5 published site -> payload');
select tests.ok(public.get_public_site('RAMESH-SHARMA ') is not null, '#5 slug lookup is case/space-insensitive');
select tests.ok(
  (select array_agg(k order by k) from jsonb_object_keys(public.get_public_site('ramesh-sharma')) k)
  = array['accent', 'canonical_host', 'companies', 'company', 'locale', 'offers', 'plans', 'portal', 'profile', 'published_at', 'published_content', 'template_key'],
  '#5 top-level keys are exactly the documented ones',
  (select string_agg(k, ',') from jsonb_object_keys(public.get_public_site('ramesh-sharma')) k));
select tests.ok(
  (select array_agg(k order by k) from jsonb_object_keys(public.get_public_site('ramesh-sharma') -> 'profile') k)
  = array['city', 'designation', 'full_name', 'phone', 'photo_url'],
  '#5 profile keys exactly full_name, designation, city, photo_url, phone (agency_code hidden by default)');
select tests.ok(
  public.get_public_site('ramesh-sharma')::text not like '%private@example.com%'
  and public.get_public_site('ramesh-sharma')::text not like '%AGCODE-SECRET%'
  and public.get_public_site('ramesh-sharma')::text not like '%BRANCH-SECRET%'
  and public.get_public_site('ramesh-sharma')::text not like '%aaaaaaaa-0000%',
  '#5 no email, agency code, branch or agent id in payload');
select tests.ok(public.get_public_site('ramesh-sharma')::text not like '%SECRET DRAFT HEADLINE%'
  and public.get_public_site('ramesh-sharma') #>> '{published_content,hero,headline}' = 'Secure your family''s future',
  '#5 serves published_content, never the unpublished draft');
select tests.ok(public.get_public_site_by_host('unknown.example.in') is null, '#5 unknown host -> null');
reset role;
select tests.ok((public.get_public_site('ramesh-sharma') ->> 'published_at')::timestamptz
                  = (select published_at from public.sites where slug = 'ramesh-sharma'),
  '#5 published_at is the site''s last publish time (SSR Last-Modified)');
rollback;

-- publish_site validation (schema rules from site_schema())
begin;
select tests.as_agent(:A);
update public.sites set draft_content = '{"hero":{"headline":""},"contact":{"phone":"12"},
  "testimonials":{"items":[{"name":"x"}]},"about":{"yearsExperience":99},"services":{"offered":["term","crypto"]},
  "faq":{"items":[{"question":"q"}]},"hidden":["faq"]}';
select tests.ok(
  (select jsonb_agg(e order by e ->> 'section', e ->> 'field') from jsonb_array_elements(public.publish_site() -> 'errors') e)
  = '[{"rule": "range", "field": "yearsExperience", "section": "about", "item": null},
      {"rule": "format", "field": "phone", "section": "contact", "item": null},
      {"rule": "required", "field": "headline", "section": "hero", "item": null},
      {"rule": "option", "field": "offered", "section": "services", "item": null},
      {"rule": "required", "field": "quote", "section": "testimonials", "item": 0}]'::jsonb,
  '#5 publish_site reports required/format/range/option errors; hidden sections skipped',
  (select (public.publish_site() -> 'errors')::text));
select tests.ok((select published_content #>> '{hero,headline}' from public.sites) = 'Secure your family''s future',
  '#5 failed publish leaves published_content untouched');
update public.sites set draft_content = jsonb_set(draft_content, '{about}', '{"showAgencyCode":true}')
  || '{"hero":{"headline":"New headline"},"contact":{"phone":"9876543210"},"testimonials":{},"services":{}}';
select tests.ok((public.publish_site() ->> 'ok')::boolean, '#5 valid draft publishes');
reset role;
select tests.as_anon();
select tests.ok(public.get_public_site('ramesh-sharma') #>> '{profile,agency_code}' = 'AGCODE-SECRET-123'
  and public.get_public_site('ramesh-sharma') #>> '{published_content,hero,headline}' = 'New headline',
  '#5 agency_code returned only when about.showAgencyCode = true');
rollback;

-- Host resolution and canonical host
insert into public.site_domains (id, agent_id, site_id, hostname, kind, status, expires_at)
  select '20000000-0000-4000-8000-000000000001', :A, id, 'ramesh.example.in', 'custom', 'active', now() + interval '300 days'
    from public.sites where agent_id = :A;
begin;
select tests.as_anon();
select tests.ok(public.get_public_site_by_host('Ramesh.Example.in.') #>> '{published_content,hero,headline}' is not null,
  '#5 get_public_site_by_host resolves an active custom domain (case/trailing dot normalised)');
select tests.ok(public.get_public_site('ramesh-sharma') ->> 'canonical_host' = 'ramesh.example.in', '#5 canonical_host = active custom domain');
select tests.ok(
  public.submit_enquiry(null, 'ramesh.example.in', 'Host visitor', '9833300000', null, 'term', null, null, tests.consent('enquiry_form')) = '{"ok": true}'::jsonb,
  '#5 submit_enquiry works by host');
reset role;
update public.site_domains set status = 'renew_soon' where hostname = 'ramesh.example.in';
select tests.as_anon();
select tests.ok(public.get_public_site_by_host('ramesh.example.in') is not null, '#5 renew_soon domain still resolves');
reset role;
update public.site_domains set status = 'expired' where hostname = 'ramesh.example.in';
select tests.as_anon();
select tests.ok(public.get_public_site_by_host('ramesh.example.in') is null, '#5 expired domain -> null');
select tests.ok(public.get_public_site('ramesh-sharma') is not null and public.get_public_site('ramesh-sharma') ->> 'canonical_host' is null,
  '#5 free link keeps working after domain expiry; no canonical host');
reset role;
update public.site_domains set status = 'active', expires_at = now() - interval '1 day' where hostname = 'ramesh.example.in';
select tests.as_anon();
select tests.ok(public.get_public_site_by_host('ramesh.example.in') is null, '#5 active but past expires_at -> null');
select tests.lives($$select public.record_site_view('ramesh-sharma', null)$$, '#5 record_site_view by slug');
select public.record_site_view('ramesh-sharma', null);
select public.record_site_view('no-such-site', null);
reset role;
select tests.ok((select views from public.site_daily_views where site_id = (select id from public.sites where agent_id = :A)
  and day = (now() at time zone 'Asia/Kolkata')::date) = 2, '#5 record_site_view upserts +1 per call (IST day)');
rollback;
