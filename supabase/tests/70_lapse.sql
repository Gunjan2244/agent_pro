-- Entitlement states and lapse behaviour (BILLING.md; TESTING.md "Service plan" #1 and #4, DB-level part).
-- Agent C has a published site and one customer. Time is moved by editing subscriptions dates.
\set C '''cccccccc-0000-4000-8000-00000000000c'''

-- Fresh signup -> trial
select tests.ok(public.entitlement(:C) = 'trial', '#L signup -> trial');
begin;
select tests.as_agent(:C);
select tests.ok(public.my_entitlement() ->> 'state' = 'trial'
  and (public.my_entitlement() ->> 'renew_available')::boolean
  and public.my_entitlement() #>> '{prices,next_order_kind}' = 'service_purchase'
  and (public.my_entitlement() #>> '{prices,purchase_inr}')::int = (select (value #>> '{service,purchaseInr}')::int from public.app_config where key = 'pricing'),
  '#L my_entitlement in trial: state, renew_available, purchase price from app_config',
  public.my_entitlement()::text);
rollback;

-- Never paid, trial ended 1 day ago -> grace (site live, writes allowed)
begin;
update public.subscriptions set trial_ends_at = now() - interval '1 day' where agent_id = :C;
select tests.ok(public.entitlement(:C) = 'grace', '#L trial ended, never paid, +1 day -> grace');
select tests.as_anon();
select tests.ok(public.get_public_site('chetan-lal') is not null, '#L grace: site still live');
reset role;
select tests.as_agent(:C);
select tests.ok(tests.affected($$insert into public.customers (agent_id, name) values ('cccccccc-0000-4000-8000-00000000000c', 'grace insert')$$) = 1,
  '#L grace: inserts allowed');
rollback;

-- Never paid, trial ended 16 days ago -> lapsed
begin;
update public.subscriptions set trial_ends_at = now() - interval '16 days' where agent_id = :C;
select tests.ok(public.entitlement(:C) = 'lapsed', '#L trial ended, never paid, +16 days -> lapsed');
rollback;

-- Paid: active; renew window
begin;
update public.subscriptions
   set current_period_start = now() - interval '100 days', current_period_end = now() + interval '200 days'
 where agent_id = :C;
select tests.ok(public.entitlement(:C) = 'active', '#L paid, before period end -> active');
select tests.as_agent(:C);
select tests.ok(not (public.my_entitlement() ->> 'renew_available')::boolean
  and public.my_entitlement() #>> '{prices,next_order_kind}' = 'service_renewal',
  '#L active, 200 days left -> renew not available yet');
reset role;
update public.subscriptions set current_period_end = now() + interval '30 days' where agent_id = :C;
select tests.as_agent(:C);
select tests.ok((public.my_entitlement() ->> 'renew_available')::boolean, '#L active, 30 days left -> renew available (60-day window)');
rollback;

-- Grace boundaries after a paid period
begin;
update public.subscriptions
   set current_period_start = now() - interval '366 days', current_period_end = now() - interval '14 days 23 hours'
 where agent_id = :C;
select tests.ok(public.entitlement(:C) = 'grace', '#L period end + 14d23h -> grace');
update public.subscriptions set current_period_end = now() - interval '15 days 1 minute' where agent_id = :C;
select tests.ok(public.entitlement(:C) = 'lapsed', '#L period end + 15d1m -> lapsed');
rollback;

-- Lapsed (period end + 16 days): site offline, enquiries rejected, writes blocked, reads allowed
begin;
update public.subscriptions
   set current_period_start = now() - interval '381 days', current_period_end = now() - interval '16 days'
 where agent_id = :C;
select tests.ok(public.entitlement(:C) = 'lapsed', '#L period end + 16 days -> lapsed');
select tests.as_anon();
select tests.ok(public.get_public_site('chetan-lal') is null, '#L lapsed: get_public_site -> null');
select tests.ok(public.submit_enquiry('chetan-lal', null, 'Visitor', '9844400000', null, 'term', null, null)
  = '{"ok": false, "error": "unavailable"}'::jsonb, '#L lapsed: submit_enquiry rejected (unavailable)');
select public.record_site_view('chetan-lal', null);
reset role;
select tests.ok((select count(*) from public.enquiries where phone = '+919844400000') = 0, '#L lapsed: no enquiry stored');
select tests.ok(not exists (select 1 from public.site_daily_views v join public.sites s on s.id = v.site_id where s.slug = 'chetan-lal'),
  '#L lapsed: views not recorded');
select tests.as_agent(:C);
select tests.ok(tests.count('select 1 from public.customers') = 1 and tests.count('select 1 from public.sites') = 1,
  '#L lapsed: select still works (read/export)');
select tests.throws($$insert into public.customers (agent_id, name) values ('cccccccc-0000-4000-8000-00000000000c', 'blocked')$$,
  '#L lapsed: insert blocked by RLS', '%row-level security%');
select tests.ok(tests.affected($$update public.customers set name = 'edited'$$) = 0, '#L lapsed: update affects 0 rows');
select tests.ok(tests.affected($$delete from public.customers$$) = 0, '#L lapsed: delete affects 0 rows');
select tests.ok(tests.affected($$update public.profiles set city = 'edited'$$) = 0, '#L lapsed: profile update affects 0 rows');
select tests.throws('select public.publish_site()', '#L lapsed: publish_site refused', '%plan_read_only%');
select tests.throws($$select public.mark_premium_paid(gen_random_uuid(), 1, current_date)$$, '#L lapsed: mark_premium_paid refused', '%plan_read_only%');
select tests.throws($$insert into storage.objects (bucket_id, name) values ('site-media', 'cccccccc-0000-4000-8000-00000000000c/new.webp')$$,
  '#L lapsed: media upload blocked', '%row-level security%');
select tests.ok(public.my_entitlement() ->> 'state' = 'lapsed' and (public.my_entitlement() ->> 'renew_available')::boolean,
  '#L lapsed: my_entitlement state lapsed, renew available');
reset role;
-- Renewing restores everything immediately
update public.subscriptions set current_period_end = now() + interval '349 days' where agent_id = :C;
select tests.as_anon();
select tests.ok(public.get_public_site('chetan-lal') is not null, '#L after renewal: site live again');
reset role;
select tests.as_agent(:C);
select tests.ok(tests.affected($$insert into public.customers (agent_id, name) values ('cccccccc-0000-4000-8000-00000000000c', 'after renew')$$) = 1,
  '#L after renewal: writes allowed again');
rollback;

-- servicePlan flag off -> everyone active (development switch)
begin;
update public.subscriptions set trial_ends_at = now() - interval '100 days' where agent_id = :C;
update public.app_config set value = jsonb_set(value, '{servicePlan}', 'false') where key = 'flags';
select tests.ok(public.entitlement(:C) = 'active', '#L servicePlan flag off -> active');
rollback;
select tests.ok(public.entitlement(:C) = 'trial', '#L rollbacks restored trial state');
