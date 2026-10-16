-- Signup trigger, storage policies and delete_my_account.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set B '''bbbbbbbb-0000-4000-8000-00000000000b'''
\set D '''dddddddd-0000-4000-8000-00000000000d'''

select tests.ok(
  (select full_name = 'Deepa Das' and email = 'delete.me@example.com' from public.profiles where id = :D),
  '#S signup trigger created profile (full_name from metadata, email)');
select tests.ok(
  (select trial_ends_at - trial_started_at = make_interval(days => (select (value #>> '{service,trialDays}')::int from public.app_config where key = 'pricing'))
     and current_period_end is null and plan = 'pro'
     from public.subscriptions where agent_id = :D),
  '#S signup trigger created subscription with pricing.service.trialDays trial');

-- Storage
select tests.ok((select public and file_size_limit = 2097152 and allowed_mime_types = array['image/*'] from storage.buckets where id = 'site-media'),
  '#S bucket site-media: public, 2MB, images only');
begin;
select tests.as_agent(:A);
select tests.lives($$insert into storage.objects (bucket_id, name) values ('site-media', 'aaaaaaaa-0000-4000-8000-00000000000a/hero.webp')$$,
  '#S agent can upload into own folder');
select tests.throws($$insert into storage.objects (bucket_id, name) values ('site-media', 'bbbbbbbb-0000-4000-8000-00000000000b/x.webp')$$,
  '#S agent cannot upload into another agent''s folder', '%row-level security%');
select tests.throws($$insert into storage.objects (bucket_id, name) values ('site-media', 'loose.webp')$$,
  '#S agent cannot upload outside a folder', '%row-level security%');
reset role;
insert into storage.objects (bucket_id, name) values ('site-media', 'bbbbbbbb-0000-4000-8000-00000000000b/b.webp');
select tests.as_agent(:A);
select tests.ok(tests.count('select 1 from storage.objects') = 1, '#S agent lists only own objects');
select tests.ok(tests.affected($$delete from storage.objects where name like 'bbbbbbbb%'$$) = 0, '#S agent cannot delete another agent''s object');
reset role;
select tests.as_anon();
select tests.throws($$insert into storage.objects (bucket_id, name) values ('site-media', 'aaaaaaaa-0000-4000-8000-00000000000a/anon.webp')$$,
  '#S anon cannot upload', '%row-level security%');
rollback;

-- delete_my_account (agent D)
insert into public.customers (agent_id, name) values (:D, 'D customer');
insert into public.orders (id, agent_id, kind, amount_inr, status)
  values ('0d000000-0000-4000-8000-000000000001', :D, 'service_purchase', 499, 'paid');
insert into public.payments (agent_id, order_id, amount_paise, provider_order_id, provider_payment_id, status, receipt_no, raw)
  values (:D, '0d000000-0000-4000-8000-000000000001', 49900, 'order_D1', 'pay_D1', 'captured', 'APRO-2026-000002',
          '{"email":"delete.me@example.com","contact":"+919000000000"}');
insert into storage.objects (bucket_id, name) values ('site-media', 'dddddddd-0000-4000-8000-00000000000d/hero.webp');

begin;
select tests.as_agent(:D);
select tests.throws('select public.delete_my_account()', '#S delete refused while an order is paid/fulfilling', '%orders_in_progress%');
rollback;
update public.orders set status = 'fulfilled' where id = '0d000000-0000-4000-8000-000000000001';
begin;
select tests.as_agent(:D);
select tests.throws('select public.delete_my_account()', '#S delete refused while media remains (Storage API must remove it first)', '%media_not_deleted%');
rollback;
delete from storage.objects where name like 'dddddddd%';   -- the app does this through the Storage API

begin;
select tests.as_agent(:D);
select tests.lives('select public.delete_my_account()', '#S delete_my_account succeeds');
commit;
select tests.ok(not exists (select 1 from auth.users where id = :D)
  and not exists (select 1 from public.profiles where id = :D)
  and not exists (select 1 from public.subscriptions where agent_id = :D)
  and not exists (select 1 from public.customers where agent_id = :D),
  '#S auth user, profile, subscription and data deleted (cascade)');
select tests.ok(
  (select agent_id is null and raw is null and receipt_no = 'APRO-2026-000002' and amount_paise = 49900
     from public.payments where provider_payment_id = 'pay_D1')
  and (select agent_id is null from public.orders where id = '0d000000-0000-4000-8000-000000000001'),
  '#S payment/order kept for tax but anonymised (agent_id null, raw cleared)');
