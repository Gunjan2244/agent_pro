-- TESTING.md Database #3: agent A cannot see or change agent B's rows; client-read-only tables
-- and protected sites columns can't be written by the client.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set B '''bbbbbbbb-0000-4000-8000-00000000000b'''

-- Give B a row in every read-only table (as the service role would).
insert into public.orders (id, agent_id, kind, amount_inr, status)
  values ('0b000000-0000-4000-8000-000000000001', :B, 'service_purchase', 499, 'fulfilled');
insert into public.payments (agent_id, order_id, amount_paise, provider_order_id, status, receipt_no)
  values (:B, '0b000000-0000-4000-8000-000000000001', 49900, 'order_B1', 'captured', 'APRO-2026-000001');
insert into public.domain_quotes (agent_id, domain, kind, registrar_price_inr, service_fee_inr, total_inr, expires_at)
  values (:B, 'sunita.in', 'purchase', 700, 99, 799, now() + interval '15 minutes');
insert into public.site_domains (agent_id, site_id, hostname, kind, status)
  select :B, id, 'sunita.in', 'custom', 'pending_dns' from public.sites where agent_id = :B;
insert into public.registrant_contacts (agent_id, name, email, phone, address, city, state, pin)
  values (:B, 'Sunita', 's@example.com', '+919000000099', 'Addr', 'Pune', 'MH', '411001');
insert into public.site_daily_views (agent_id, site_id, day, views)
  select :B, id, current_date, 3 from public.sites where agent_id = :B;
insert into public.premium_payments (agent_id, policy_id, amount, paid_on, period_due_date)
  select :B, id, 12000, '2025-01-15', '2025-01-15' from public.policies where agent_id = :B;

begin;
select tests.as_agent(:A);
do $$
declare
  t text;
begin
  foreach t in array array['sites', 'site_domains', 'enquiries', 'customers', 'policies', 'premium_payments',
    'site_daily_views', 'subscriptions', 'orders', 'payments', 'domain_quotes', 'registrant_contacts'] loop
    perform tests.ok(
      tests.count(format($q$select 1 from public.%I where agent_id = 'bbbbbbbb-0000-4000-8000-00000000000b'$q$, t)) = 0,
      '#3 A cannot see B rows in ' || t);
  end loop;
  perform tests.ok(
    tests.count($q$select 1 from public.profiles where id = 'bbbbbbbb-0000-4000-8000-00000000000b'$q$) = 0,
    '#3 A cannot see B profile');
  perform tests.ok(tests.count('select 1 from public.profiles') = 1, '#3 A sees exactly one profile (own)');
  perform tests.ok(tests.count('select 1 from public.subscriptions') = 1, '#3 A sees own subscription');
  perform tests.ok(tests.count('select 1 from public.app_config') >= 2, '#3 authenticated can read app_config');
end;
$$;

-- Updates/deletes on B's rows affect nothing.
update public.enquiries set notes = 'hacked' where agent_id = :B;
update public.customers set name = 'hacked' where agent_id = :B;
update public.policies set notes = 'hacked' where agent_id = :B;
update public.sites set draft_content = '{"hacked":true}' where agent_id = :B;
update public.profiles set full_name = 'hacked' where id = :B;
delete from public.enquiries where agent_id = :B;
delete from public.customers where agent_id = :B;

select tests.throws($$insert into public.enquiries (agent_id, name, source, status) values ('bbbbbbbb-0000-4000-8000-00000000000b', 'x', 'walk_in', 'new')$$,
  '#3 A cannot insert a row owned by B', '%row-level security%');
select tests.throws($$update public.enquiries set agent_id = 'bbbbbbbb-0000-4000-8000-00000000000b' where agent_id = 'aaaaaaaa-0000-4000-8000-00000000000a'$$,
  '#3 A cannot hand a row to B', '%row-level security%');
select tests.throws($$insert into public.enquiries (agent_id, name, source, status, customer_id)
  select 'aaaaaaaa-0000-4000-8000-00000000000a', 'x', 'walk_in', 'new', 'ffffffff-0000-4000-8000-000000000000'$$,
  '#3 composite FK: cannot reference a customer that is not A''s', '%foreign key%');
select tests.throws($$select public.mark_premium_paid((select id from public.policies limit 1), 100, current_date)$$,
  '#3 A has no policies; mark_premium_paid on nothing -> not_found', '%not_found%');
rollback;

begin;
select tests.as_agent(:A);
select tests.throws(format('select public.mark_premium_paid(%L, 100, current_date)',
  (select id from public.policies where agent_id = 'bbbbbbbb-0000-4000-8000-00000000000b')),
  '#3 A cannot mark B''s policy paid', '%not_found%');
select tests.throws(format('select public.undo_premium_payment(%L)',
  (select id from public.premium_payments where agent_id = 'bbbbbbbb-0000-4000-8000-00000000000b')),
  '#3 A cannot undo B''s payment', '%not_found%');
-- Client-read-only tables
select tests.throws($$insert into public.orders (agent_id, kind, amount_inr) values ('aaaaaaaa-0000-4000-8000-00000000000a', 'service_purchase', 1)$$,
  '#3 client cannot insert orders', '%permission denied%');
select tests.throws($$update public.subscriptions set current_period_end = now() + interval '10 years'$$,
  '#3 client cannot extend own subscription', '%permission denied%');
select tests.throws($$insert into public.premium_payments (agent_id, policy_id, amount, paid_on, period_due_date) values ('aaaaaaaa-0000-4000-8000-00000000000a', gen_random_uuid(), 1, current_date, current_date)$$,
  '#3 client cannot insert premium_payments directly', '%permission denied%');
select tests.throws($$insert into public.site_domains (agent_id, site_id, hostname, kind, status) select agent_id, id, 'evil.in', 'custom', 'active' from public.sites$$,
  '#3 client cannot insert site_domains', '%permission denied%');
select tests.throws($$update public.payments set status = 'captured'$$, '#3 client cannot update payments', '%permission denied%');
select tests.throws($$insert into public.app_config (key, value) values ('flags', '{}')$$, '#3 client cannot write app_config', '%permission denied%');
-- Protected sites columns
select tests.throws($$update public.sites set published_content = '{"x":1}'$$, '#3 client cannot set sites.published_content', '%permission denied%');
select tests.throws($$update public.sites set published_at = now()$$, '#3 client cannot set sites.published_at', '%permission denied%');
select tests.throws($$update public.sites set primary_domain_id = null$$, '#3 client cannot set sites.primary_domain_id', '%permission denied%');
select tests.lives($$update public.sites set accent = 'navy', draft_content = draft_content$$, '#3 client can edit own site draft/appearance');
-- Soft delete + undo through RLS (select policy must not hide tombstones, see BACKEND.md)
select tests.lives($$update public.enquiries set deleted_at = now() where name = 'A manual enquiry'$$, '#3 owner can soft-delete');
select tests.ok(tests.count($$select 1 from public.enquiries where name = 'A manual enquiry' and deleted_at is not null$$) = 1,
  '#3 soft-deleted row still visible to owner for sync');
select tests.lives($$update public.enquiries set deleted_at = null where name = 'A manual enquiry'$$, '#3 owner can undo a soft delete');
-- Reserved / invalid / taken slugs
select tests.ok(public.is_slug_available('ramesh-sharma'), '#3 is_slug_available: own slug is available to self');
select tests.ok(not public.is_slug_available('sunita-rao'), '#3 is_slug_available: other agent''s slug taken');
select tests.ok(not public.is_slug_available('admin'), '#3 is_slug_available: reserved word');
select tests.ok(not public.is_slug_available('ab'), '#3 is_slug_available: too short');
select tests.ok(not public.is_slug_available('Bad_Slug'), '#3 is_slug_available: bad format');
select tests.ok(public.is_slug_available('fresh-slug-1'), '#3 is_slug_available: free slug');
select tests.throws($$update public.sites set slug = 'admin'$$, '#3 reserved slug blocked on direct write', '%slug_reserved%');
select tests.throws($$update public.sites set slug = 'sunita-rao'$$, '#3 duplicate slug blocked', '%duplicate key%');
rollback;

select tests.ok(
  (select count(*) from public.enquiries where agent_id = :B and notes is null and deleted_at is null) = 1
  and (select name from public.customers where agent_id = :B) = 'B customer'
  and (select notes from public.policies where agent_id = :B) is null
  and (select draft_content from public.sites where agent_id = :B) = '{"hero":{"headline":"Draft only"}}'::jsonb
  and (select full_name from public.profiles where id = :B) = 'Sunita Rao',
  '#3 B''s rows unchanged after A''s update/delete attempts');
