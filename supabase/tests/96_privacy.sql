-- LOCAL TESTS ONLY. P1-2 privacy backend (PRIVACY.md; BACKEND.md "Privacy"): submit_enquiry consent,
-- consents rows, submit_data_request, update_data_request, record_consent, the signup consent,
-- payments.raw minimisation, RLS isolation and anon access. Every block rolls back.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set B '''bbbbbbbb-0000-4000-8000-00000000000b'''

-- ---------------------------------------------------------------------------
-- submit_enquiry: consent required and validated (after the honeypot, before the rate limit)
-- ---------------------------------------------------------------------------
begin;
create temp table c0 as select count(*) as n from public.consents;
grant select on c0 to anon;
select tests.as_anon();
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'No consent', '9700000001', null, null, null, null)
  = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb, '#P submit_enquiry without consent -> invalid/consent');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Null consent', '9700000001', null, null, null, null, null)
  = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb, '#P submit_enquiry with consent null -> invalid/consent');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Wrong notice', '9700000001', null, null, null, null, tests.consent('agent_signup'))
  ->> 'field' = 'consent', '#P another notice''s valid payload is rejected');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Wrong version', '9700000001', null, null, null, null,
    jsonb_set(tests.consent('enquiry_form'), '{version}', to_jsonb((tests.consent('enquiry_form') ->> 'version')::int + 1)))
  ->> 'field' = 'consent', '#P unknown version -> invalid/consent');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Wrong hash', '9700000001', null, null, null, null,
    jsonb_set(tests.consent('enquiry_form'), '{hash}', to_jsonb(repeat('0', 64))))
  ->> 'field' = 'consent', '#P hash matching no text -> invalid/consent');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Upper hash', '9700000001', null, null, null, null,
    jsonb_set(tests.consent('enquiry_form'), '{hash}', to_jsonb(upper(tests.consent('enquiry_form') ->> 'hash'))))
  ->> 'field' = 'consent', '#P upper-case hash -> invalid/consent');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'String version', '9700000001', null, null, null, null,
    jsonb_set(tests.consent('enquiry_form'), '{version}', to_jsonb(tests.consent('enquiry_form') ->> 'version')))
  ->> 'field' = 'consent', '#P version as a string -> invalid/consent');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Array', '9700000001', null, null, null, null, '[1]'::jsonb)
  ->> 'field' = 'consent', '#P non-object consent -> invalid/consent (no error)');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Bad phone', '123', null, null, null, null, null) ->> 'field' = 'phone',
  '#P field validation comes before the consent check');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Bot', '9700000002', null, null, null, 'filled', null) = '{"ok": true}'::jsonb,
  '#P honeypot without consent -> fake {ok:true} (honeypot first)');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Bot', '9700000002', null, null, null, 'filled', tests.consent('enquiry_form')) = '{"ok": true}'::jsonb,
  '#P honeypot with consent -> fake {ok:true}');
select tests.ok(
  public.submit_enquiry('sunita-rao', null, 'Visitor', '9700000003', null, null, null, null, tests.consent('enquiry_form'))
  = '{"ok": false, "error": "unavailable"}'::jsonb, '#P unpublished site -> unavailable (before consent)');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Consenting Visitor', '9700000004', null, 'term', 'Hi', null, tests.consent('enquiry_form'))
  = '{"ok": true}'::jsonb, '#P valid consent (en) -> {ok:true}');
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Hindi Visitor', '9700000005', null, null, null, null, tests.consent('enquiry_form', 'hi'))
  = '{"ok": true}'::jsonb, '#P valid consent (hi hash) -> {ok:true}');
reset role;
select tests.ok((select count(*) from public.enquiries where phone in ('+919700000001', '+919700000002', '+919700000003')) = 0,
  '#P rejected and honeypot submissions inserted no enquiry');
select tests.ok((select count(*) from public.consents) = (select n from c0) + 2,
  '#P exactly one consent row per accepted submission (none for rejected or honeypot ones)');
select tests.ok(
  (select k.agent_id = :A and k.principal_kind = 'enquirer' and k.channel = 'web' and k.locale = 'en'
          and k.notice_key = 'enquiry_form'
          and k.notice_version = (public.catalog_privacy_notice('enquiry_form') ->> 'version')::int
          and k.notice_hash = public.catalog_privacy_notice('enquiry_form') ->> 'hash_en'
          and k.customer_id is null and k.withdrawn_at is null and k.given_at is not null
     from public.consents k join public.enquiries e on e.id = k.enquiry_id
    where e.phone = '+919700000004'),
  '#P consent row: the enquiry, the agent, enquirer/web, current version, server hash, locale en');
select tests.ok(
  (select k.locale = 'hi' and k.notice_hash = public.catalog_privacy_notice('enquiry_form') ->> 'hash_hi'
     from public.consents k join public.enquiries e on e.id = k.enquiry_id where e.phone = '+919700000005'),
  '#P locale resolved from the hash (hi)');
delete from public.enquiries where phone = '+919700000004';
select tests.ok(
  exists (select 1 from public.consents k where k.notice_hash = public.catalog_privacy_notice('enquiry_form') ->> 'hash_en'
            and k.enquiry_id is null and k.agent_id = :A),
  '#P deleting the enquiry keeps the consent row (enquiry_id set null)');

-- The rate limit still applies (5 per phone per site per hour), with consent rows only for accepted ones.
select tests.as_anon();
select tests.ok(
  (select bool_and(public.submit_enquiry('ramesh-sharma', null, 'Repeat', '9700000006', null, null, null, null,
                     tests.consent('enquiry_form')) = '{"ok": true}'::jsonb) from generate_series(1, 5))
  and public.submit_enquiry('ramesh-sharma', null, 'Repeat', '9700000006', null, null, null, null, tests.consent('enquiry_form'))
      = '{"ok": false, "error": "rate_limited"}'::jsonb,
  '#P rate limit unchanged with consent (6th -> rate_limited)');
reset role;
select tests.ok(
  (select count(*) from public.consents k join public.enquiries e on e.id = k.enquiry_id where e.phone = '+919700000006') = 5,
  '#P rate-limited submission wrote no consent');
rollback;

-- Only the CURRENT version is accepted: simulate releasing version 2 of enquiry_form.
begin;
create temp table v1 as select tests.consent('enquiry_form') as c;
grant select on v1 to anon;
create or replace function public.catalog_privacy_notice(p_key text)
returns jsonb language sql immutable set search_path = '' as $$
  select case when p_key = 'enquiry_form'
              then jsonb_build_object('version', 2, 'hash_en', repeat('a', 64), 'hash_hi', repeat('b', 64))
              else null end
$$;
select tests.as_anon();
select tests.ok(
  public.submit_enquiry('ramesh-sharma', null, 'Old text', '9700000007', null, null, null, null, (select c from v1))
  ->> 'field' = 'consent', '#P a released but no longer current version is rejected');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- submit_data_request (anon)
-- ---------------------------------------------------------------------------
begin;
select tests.as_anon();
select tests.ok(
  public.submit_data_request('ramesh-sharma', null, 'Ramesh Visitor', '+91 97111 00001', 'erasure', 'Please delete my number', null)
  = '{"ok": true}'::jsonb, '#Q valid request -> {ok:true}');
select tests.ok(
  public.submit_data_request(null, null, 'X', '9711100002', 'access', null, 'filled') = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#Q no slug or host -> unavailable');
select tests.ok(
  public.submit_data_request('ramesh-sharma', null, 'Bot', '9711100002', 'access', null, 'http://spam') = '{"ok": true}'::jsonb,
  '#Q honeypot -> fake {ok:true}');
select tests.ok(
  public.submit_data_request('sunita-rao', null, 'X', '9711100003', 'access', null, null) = '{"ok": false, "error": "unavailable"}'::jsonb,
  '#Q unpublished site -> unavailable');
select tests.ok(public.submit_data_request('ramesh-sharma', null, ' ', '9711100003', 'access', null, null) ->> 'field' = 'name', '#Q blank name rejected');
select tests.ok(public.submit_data_request('ramesh-sharma', null, repeat('n', 81), '9711100003', 'access', null, null) ->> 'field' = 'name', '#Q name > 80 rejected');
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'X', '12345', 'access', null, null) ->> 'field' = 'phone', '#Q invalid phone rejected');
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'X', '9711100003', 'delete_everything', null, null)
  = '{"ok": false, "error": "invalid", "field": "kind"}'::jsonb, '#Q kind not in catalog rejected');
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'X', '9711100003', null, null, null) ->> 'field' = 'kind', '#Q missing kind rejected');
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'X', '9711100003', 'access', repeat('m', 501), null) ->> 'field' = 'message', '#Q message > 500 rejected');
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'X', '9711100003', 'withdraw_consent', repeat('m', 500), null) = '{"ok": true}'::jsonb,
  '#Q message = 500 accepted, withdraw_consent kind accepted');
reset role;
select tests.ok(
  (select r.agent_id = :A and r.addressed_to = 'agent' and r.status = 'open' and r.kind = 'erasure'
          and r.name = 'Ramesh Visitor' and r.message = 'Please delete my number'
          and r.resolved_at is null and r.enquiry_id is null
          and r.due_at between now() + make_interval(days => (select (value #>> '{dataRequests,responseDays}')::int from public.app_config where key = 'retention')) - interval '1 minute'
                           and now() + make_interval(days => (select (value #>> '{dataRequests,responseDays}')::int from public.app_config where key = 'retention')) + interval '1 minute'
     from public.data_requests r where r.phone = '+919711100001'),
  '#Q row: site''s agent, addressed_to agent, status open, phone normalised, due = now + retention.dataRequests.responseDays');
select tests.ok((select count(*) from public.data_requests where phone = '+919711100002') = 0, '#Q honeypot inserted nothing');

-- Rate limit: 3 per phone per site per 24 hours.
select tests.as_anon();
select tests.ok(
  (select bool_and(public.submit_data_request('ramesh-sharma', null, 'Repeat', '9711100004', 'access', null, null) = '{"ok": true}'::jsonb)
     from generate_series(1, 3)),
  '#Q requests 1-3 from one phone accepted');
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'Repeat', '+919711100004', 'access', null, null)
  = '{"ok": false, "error": "rate_limited"}'::jsonb, '#Q 4th request within 24 hours -> rate_limited');
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'Other', '9711100005', 'access', null, null) = '{"ok": true}'::jsonb,
  '#Q rate limit is per phone');
reset role;
update public.data_requests set created_at = now() - interval '25 hours' where phone = '+919711100004';
select tests.as_anon();
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'Repeat', '9711100004', 'access', null, null) = '{"ok": true}'::jsonb,
  '#Q accepted again after 24 hours');
reset role;
rollback;

-- A deprecated kind can't be used for new requests.
begin;
create or replace function public.catalog_data_request_kinds()
returns text[] language sql immutable set search_path = '' as $$ select array['access']::text[] $$;
select tests.as_anon();
select tests.ok(public.submit_data_request('ramesh-sharma', null, 'X', '9711100006', 'erasure', null, null) ->> 'field' = 'kind',
  '#Q a kind removed from the active list is rejected');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- Agent side: RLS isolation and update_data_request
-- ---------------------------------------------------------------------------
begin;
select tests.as_anon();
select public.submit_data_request('ramesh-sharma', null, 'Iso Person', '9722200001', 'access', null, null);
select public.submit_enquiry('ramesh-sharma', null, 'Iso Enquirer', '9722200002', null, null, null, null, tests.consent('enquiry_form'));
reset role;
create temp table dr as select id from public.data_requests where phone = '+919722200001';
grant select on dr to authenticated;

select tests.as_agent(:B);
select tests.ok(tests.count('select 1 from public.data_requests') = 0, '#R B sees none of A''s data requests');
select tests.ok(tests.count($q$select 1 from public.consents where agent_id = 'aaaaaaaa-0000-4000-8000-00000000000a'$q$) = 0,
  '#R B sees none of A''s consents');
select tests.throws($$select public.update_data_request((select id from dr), 'done', null)$$,
  '#R B cannot update A''s request', '%not_found%');
select tests.throws($$insert into public.data_requests (agent_id, kind, status, name, due_at) values ('bbbbbbbb-0000-4000-8000-00000000000b', 'access', 'open', 'x', now())$$,
  '#R authenticated cannot insert data_requests directly', '%permission denied%');
select tests.throws($$update public.consents set withdrawn_at = now()$$,
  '#R authenticated cannot update consents directly', '%permission denied%');
select tests.ok(tests.count('select * from public.audit_log') = 0, '#R an agent reads no audit_log rows (owners only, OFFERS.md)');
select tests.throws($$insert into public.audit_log (actor_kind, action) values ('agent', 'x')$$, '#R authenticated cannot write audit_log', '%permission denied%');
reset role;

select tests.as_agent(:A);
select tests.ok(tests.count('select 1 from public.data_requests where id = (select id from dr)') = 1, '#R A sees own request');
select tests.ok(tests.count($q$select 1 from public.consents where principal_kind = 'enquirer'$q$) >= 1, '#R A sees own consents');
select tests.throws($$update public.data_requests set status = 'done'$$, '#R authenticated cannot update data_requests directly', '%permission denied%');
select tests.throws($$select public.update_data_request((select id from dr), 'closed', null)$$, '#R status not in catalog -> invalid_status', '%invalid_status%');
select tests.throws($$select public.update_data_request((select id from dr), 'done', repeat('n', 501))$$, '#R note > 500 -> invalid_note', '%invalid_note%');
select tests.throws($$select public.update_data_request(gen_random_uuid(), 'done', null)$$, '#R unknown id -> not_found', '%not_found%');
select tests.ok(
  (select (r ->> 'status') = 'done' and (r ->> 'resolved_at') is not null and (r ->> 'id')::uuid = (select id from dr)
     from (select public.update_data_request((select id from dr), 'done', 'Called back, shared the data') as r) x),
  '#R update_data_request done -> {id, status, resolved_at}');
select tests.ok(
  (select status = 'done' and resolved_at is not null and resolution_note = 'Called back, shared the data'
     from public.data_requests where id = (select id from dr)),
  '#R row updated (status, resolved_at, resolution_note)');
select tests.ok((public.update_data_request((select id from dr), 'open', null) ->> 'resolved_at') is null,
  '#R reopening returns resolved_at null');
select tests.ok(
  (select status = 'open' and resolved_at is null and resolution_note is null from public.data_requests where id = (select id from dr)),
  '#R reopening clears resolved_at and the note');
reset role;
select tests.ok(
  (select count(*) = 2 and bool_and(actor_id = :A and actor_kind = 'agent' and entity = 'data_requests'
                                    and entity_id = (select id from dr) and not (changes ? 'note')
                                    and not (changes::text like '%Called back%'))
     from public.audit_log where action = 'data_request.update'),
  '#R each update wrote an audit_log row without the note');

-- A lapsed agent can still handle requests (privacy duties don't depend on the plan).
update public.subscriptions set trial_ends_at = now() - interval '100 days', current_period_end = null where agent_id = :A;
select tests.ok(public.entitlement(:A) = 'lapsed', '#R setup: A lapsed');
select tests.as_agent(:A);
select tests.lives($$select public.update_data_request((select id from dr), 'not_genuine', null)$$, '#R lapsed agent can still update a request');
reset role;

-- Account deletion cascades the agent's privacy rows.
delete from auth.users where id = :A;
select tests.ok(not exists (select 1 from public.data_requests where agent_id = :A)
  and not exists (select 1 from public.consents where agent_id = :A),
  '#R deleting the agent cascades consents and data_requests');
rollback;

-- ---------------------------------------------------------------------------
-- record_consent (authenticated)
-- ---------------------------------------------------------------------------
begin;
create temp table bc as select id from public.customers where agent_id = :B;
grant select on bc to authenticated;
select tests.as_agent(:A);
select tests.ok(
  public.record_consent('agent_customer_duty', (tests.consent('agent_customer_duty') ->> 'version')::int,
                        tests.consent('agent_customer_duty') ->> 'hash') = '{"ok": true}'::jsonb,
  '#K record_consent agent_customer_duty -> {ok:true}');
select tests.ok(
  public.record_consent('agent_customer_duty', (tests.consent('agent_customer_duty', 'hi') ->> 'version')::int,
                        tests.consent('agent_customer_duty', 'hi') ->> 'hash') = '{"ok": true}'::jsonb,
  '#K repeating it (another locale) -> {ok:true}');
select tests.ok(tests.count($q$select 1 from public.consents where notice_key = 'agent_customer_duty'$q$) = 1,
  '#K idempotent: one live consent per notice version');
select tests.ok(
  (select principal_kind = 'agent' and channel = 'app' and locale = 'en' and customer_id is null and enquiry_id is null
     from public.consents where notice_key = 'agent_customer_duty'),
  '#K row: agent/app, locale from the hash');
select tests.ok(
  public.record_consent('enquiry_form', (tests.consent('enquiry_form') ->> 'version')::int, tests.consent('enquiry_form') ->> 'hash')
  = '{"ok": false, "error": "invalid", "field": "consent"}'::jsonb, '#K enquiry_form can''t be recorded by the agent');
select tests.ok(
  public.record_consent('agent_signup', (tests.consent('agent_signup') ->> 'version')::int, repeat('0', 64)) ->> 'field' = 'consent',
  '#K wrong hash -> invalid/consent');
select tests.ok(
  public.record_consent('agent_signup', (tests.consent('agent_signup') ->> 'version')::int + 1, tests.consent('agent_signup') ->> 'hash') ->> 'field' = 'consent',
  '#K wrong version -> invalid/consent');
select tests.ok(public.record_consent('no_such_notice', 1, repeat('0', 64)) ->> 'field' = 'consent', '#K unknown notice -> invalid/consent');
select tests.ok(public.record_consent('agent_signup', null, null) ->> 'field' = 'consent', '#K nulls -> invalid/consent');
select tests.ok(
  public.record_consent('guardian_consent', (tests.consent('guardian_consent') ->> 'version')::int, tests.consent('guardian_consent') ->> 'hash')
  = '{"ok": false, "error": "invalid", "field": "customer_id"}'::jsonb, '#K guardian_consent needs a customer');
select tests.ok(
  public.record_consent('agent_customer_duty', (tests.consent('agent_customer_duty') ->> 'version')::int,
                        tests.consent('agent_customer_duty') ->> 'hash', (select id from public.customers limit 1)) ->> 'field' = 'customer_id',
  '#K an agent notice takes no customer');
select tests.ok(
  public.record_consent('guardian_consent', (tests.consent('guardian_consent', 'hi') ->> 'version')::int,
                        tests.consent('guardian_consent', 'hi') ->> 'hash', (select id from public.customers where name = 'A customer'))
  = '{"ok": true}'::jsonb, '#K guardian_consent for own customer -> {ok:true}');
select tests.ok(
  (select principal_kind = 'customer' and locale = 'hi' and customer_id is not null
     from public.consents where notice_key = 'guardian_consent'),
  '#K guardian row: principal customer, customer_id, locale hi');
select tests.throws(
  $$select public.record_consent('guardian_consent', (tests.consent('guardian_consent') ->> 'version')::int,
                                 tests.consent('guardian_consent') ->> 'hash', (select id from bc))$$,
  '#K guardian_consent for another agent''s customer -> not_found', '%not_found%');
reset role;
select tests.as_anon();
select tests.throws($$select public.record_consent('agent_signup', 1, repeat('0', 64))$$, '#K anon cannot call record_consent', '%permission denied%');
select tests.throws($$select public.update_data_request(gen_random_uuid(), 'done', null)$$, '#K anon cannot call update_data_request', '%permission denied%');
select tests.throws($$select * from public.consents$$, '#K anon cannot select consents', '%permission denied%');
select tests.throws($$select * from public.data_requests$$, '#K anon cannot select data_requests', '%permission denied%');
select tests.throws($$select * from public.audit_log$$, '#K anon cannot select audit_log', '%permission denied%');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- Signup trigger: raw_user_meta_data.consent (agent_signup)
-- ---------------------------------------------------------------------------
begin;
insert into auth.users (id, email, raw_user_meta_data) values
  ('e0000000-0000-4000-8000-000000000001', 'with.consent@example.com',
     jsonb_build_object('full_name', 'With Consent', 'consent', tests.consent('agent_signup', 'hi'))),
  ('e0000000-0000-4000-8000-000000000002', 'no.consent@example.com', '{"full_name":"No Consent"}'),
  ('e0000000-0000-4000-8000-000000000003', 'bad.consent@example.com',
     jsonb_build_object('consent', jsonb_set(tests.consent('agent_signup'), '{hash}', to_jsonb(repeat('f', 64))))),
  ('e0000000-0000-4000-8000-000000000004', 'wrong.notice@example.com',
     jsonb_build_object('consent', tests.consent('enquiry_form'))),
  ('e0000000-0000-4000-8000-000000000005', 'junk.consent@example.com', '{"consent":"yes"}');
select tests.ok(
  (select count(*) = 1 and bool_and(principal_kind = 'agent' and channel = 'app' and locale = 'hi' and notice_key = 'agent_signup'
                                    and notice_hash = public.catalog_privacy_notice('agent_signup') ->> 'hash_hi')
     from public.consents where agent_id = 'e0000000-0000-4000-8000-000000000001'),
  '#U signup with valid consent metadata -> one agent_signup consent (locale hi)');
select tests.ok(
  (select count(*) from public.profiles where id::text like 'e0000000-%') = 5
  and (select count(*) from public.subscriptions where agent_id::text like 'e0000000-%') = 5,
  '#U every signup creates profile and subscription, with or without a valid consent');
select tests.ok(
  not exists (select 1 from public.consents where agent_id in ('e0000000-0000-4000-8000-000000000002',
    'e0000000-0000-4000-8000-000000000003', 'e0000000-0000-4000-8000-000000000004', 'e0000000-0000-4000-8000-000000000005')),
  '#U missing, wrong-hash, wrong-notice or junk consent metadata records nothing');
select tests.as_agent('e0000000-0000-4000-8000-000000000002');
select tests.ok(
  public.record_consent('agent_signup', (tests.consent('agent_signup') ->> 'version')::int, tests.consent('agent_signup') ->> 'hash')
  = '{"ok": true}'::jsonb
  and tests.count($q$select 1 from public.consents where notice_key = 'agent_signup'$q$) = 1,
  '#U without signup consent, the app records it with record_consent');
reset role;
rollback;

-- ---------------------------------------------------------------------------
-- payments.raw keeps only the allow-listed keys (PRIVACY.md, Purpose limitation)
-- ---------------------------------------------------------------------------
begin;
insert into public.orders (id, agent_id, kind, amount_inr, status)
  values ('0a000000-0000-4000-8000-0000000000f1', :A, 'service_purchase', 499, 'paid');
insert into public.payments (agent_id, order_id, amount_paise, provider_order_id, provider_payment_id, status, raw)
values (:A, '0a000000-0000-4000-8000-0000000000f1', 49900, 'order_P1', 'pay_P1', 'captured',
  '{"id":"pay_P1","entity":"payment","order_id":"order_P1","status":"captured","method":"upi","amount":49900,
    "currency":"INR","fee":1180,"tax":180,"captured":true,"error_code":null,"error_reason":null,"created_at":1760000000,
    "email":"payer@example.com","contact":"+919000000000","vpa":"payer@upi","card":{"last4":"1111"},"bank":"HDFC",
    "wallet":null,"notes":{"agent":"Ramesh"},"description":"x","international":false,
    "acquirer_data":{"rrn":"123456789012","upi_transaction_id":"UPI123","auth_code":"A1"}}');
select tests.ok(
  (select array_agg(k order by k) from public.payments p, jsonb_object_keys(p.raw) k where p.provider_payment_id = 'pay_P1')
  = array['acquirer_data', 'amount', 'captured', 'created_at', 'currency', 'error_code', 'error_reason', 'fee', 'id',
          'method', 'order_id', 'status', 'tax'],
  '#M payments.raw keeps only the allow-listed top-level keys');
select tests.ok((select raw -> 'acquirer_data' = '{"rrn": "123456789012"}'::jsonb and raw ->> 'method' = 'upi'
                   from public.payments where provider_payment_id = 'pay_P1'),
  '#M acquirer_data keeps only rrn');
select tests.ok((select raw::text !~ '(payer|9000000000|1111|HDFC|Ramesh|UPI123)' from public.payments where provider_payment_id = 'pay_P1'),
  '#M no email, contact, vpa, card, bank, notes or other acquirer data stored');
update public.payments set raw = '{"email":"payer@example.com","contact":"+919000000000"}' where provider_payment_id = 'pay_P1';
select tests.ok((select raw is null from public.payments where provider_payment_id = 'pay_P1'),
  '#M update with nothing allow-listed stores null');
update public.payments set raw = '"a string"' where provider_payment_id = 'pay_P1';
select tests.ok((select raw is null from public.payments where provider_payment_id = 'pay_P1'), '#M a non-object payload stores null');
set local role service_role;
update public.payments set raw = '{"id":"pay_P1","vpa":"payer@upi"}' where provider_payment_id = 'pay_P1';
reset role;
select tests.ok((select raw = '{"id": "pay_P1"}'::jsonb from public.payments where provider_payment_id = 'pay_P1'),
  '#M the service role''s writes are minimised too');
rollback;
