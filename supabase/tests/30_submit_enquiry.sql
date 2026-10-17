-- TESTING.md Database #2: submit_enquiry (ok, honeypot, rate limit, interest whitelist, validation).
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''

begin;
select tests.as_anon();
select tests.ok(
  public.submit_enquiry(slug => 'ramesh-sharma', host => null, name => 'Visitor One', phone => '+91 98111 22333',
                        email => 'v1@example.com', interest => 'term', message => 'Call me', honeypot => '', consent => tests.consent('enquiry_form'))
  = '{"ok": true}'::jsonb, '#2 submit_enquiry valid -> {ok:true}');
reset role;
select tests.ok(
  (select count(*) from public.enquiries where agent_id = :A and phone = '+919811122333' and source = 'website'
      and status = 'new' and interest = 'term' and name = 'Visitor One') = 1,
  '#2 valid submission inserted one row (source=website, status=new, phone normalised +91)');

select tests.as_anon();
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Bot', '9811122334', null, 'term', 'spam', 'http://spam', tests.consent('enquiry_form'))
  = '{"ok": true}'::jsonb, '#2 honeypot filled -> fake {ok:true}');
reset role;
select tests.ok((select count(*) from public.enquiries where phone = '+919811122334') = 0,
  '#2 honeypot submission inserted nothing');

select tests.as_anon();
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '9811122335', null, 'crypto', null, null, tests.consent('enquiry_form'))
  = '{"ok": false, "error": "invalid", "field": "interest"}'::jsonb, '#2 interest not in catalog whitelist rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '12345', null, null, null, null, tests.consent('enquiry_form'))
  = '{"ok": false, "error": "invalid", "field": "phone"}'::jsonb, '#2 invalid phone rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, '', '9811122335', null, null, null, null, tests.consent('enquiry_form')) ->> 'field' = 'name',
  '#2 empty name rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, repeat('n', 81), '9811122335', null, null, null, null, tests.consent('enquiry_form')) ->> 'field' = 'name',
  '#2 name > 80 rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '9811122335', 'not-an-email', null, null, null, tests.consent('enquiry_form')) ->> 'field' = 'email',
  '#2 bad email rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '9811122335', repeat('e', 110) || '@example.com', null, null, null, tests.consent('enquiry_form')) ->> 'field' = 'email',
  '#2 email > 120 rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '9811122335', null, null, repeat('m', 501), null, tests.consent('enquiry_form')) ->> 'field' = 'message',
  '#2 message > 500 rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Visitor', '09811122335', null, null, repeat('m', 500), null, tests.consent('enquiry_form')) = '{"ok": true}'::jsonb,
  '#2 interest optional, message = 500 accepted, 0-prefixed phone accepted');
select tests.ok(
  public.submit_enquiry('sunita-rao', null, 'Visitor', '9811122336', null, 'term', null, null, tests.consent('enquiry_form'))
  = '{"ok": false, "error": "unavailable"}'::jsonb, '#2 unpublished site -> unavailable');
select tests.ok(
  public.submit_enquiry('no-such-site', null, 'Visitor', '9811122336', null, 'term', null, null, tests.consent('enquiry_form')) ->> 'error' = 'unavailable',
  '#2 unknown slug -> unavailable');

-- Rate limit: 5 per phone per site per hour; the 6th is rejected. Phone 9822200000 is fresh.
select tests.ok(
  (select bool_and(public.submit_enquiry('ramesh-sharma', null, 'Repeat', '9822200000', null, 'other', null, null, tests.consent('enquiry_form')) = '{"ok": true}'::jsonb)
     from generate_series(1, 5)),
  '#2 submissions 1-5 from one phone accepted');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Repeat', '+919822200000', null, 'other', null, null, tests.consent('enquiry_form'))
  = '{"ok": false, "error": "rate_limited"}'::jsonb, '#2 6th submission within an hour -> rate_limited');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Other phone', '9822200001', null, 'other', null, null, tests.consent('enquiry_form')) = '{"ok": true}'::jsonb,
  '#2 rate limit is per phone (another phone still accepted)');
reset role;
select tests.ok((select count(*) from public.enquiries where phone = '+919822200000') = 5,
  '#2 exactly 5 rows stored for the rate-limited phone');
-- Older submissions fall out of the window.
update public.enquiries set created_at = now() - interval '61 minutes' where phone = '+919822200000';
select tests.as_anon();
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Repeat', '9822200000', null, 'other', null, null, tests.consent('enquiry_form')) = '{"ok": true}'::jsonb,
  '#2 accepted again once the hour has passed');
reset role;
select tests.ok(
  exists (select 1 from public.enquiries where phone = '+919822200000' and site_id is not null),
  '#2 website enquiries carry site_id');
commit;
