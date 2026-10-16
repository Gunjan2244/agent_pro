-- TESTING.md Database #4: mark_premium_paid advances next_due_date per mode; undo restores it.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''

begin;
select tests.as_agent(:A);
insert into public.policies (id, agent_id, customer_id, plan_name, premium_mode, status, start_date, next_due_date, premium_amount)
select v.id::uuid, :A, c.id, 'Jeevan Labh', v.mode, 'active', v.start::date, v.due::date, 1000
  from public.customers c,
  (values ('10000000-0000-4000-8000-000000000001', 'yearly',      '2025-01-31', '2025-01-31'),
          ('10000000-0000-4000-8000-000000000002', 'half_yearly', '2025-01-31', '2025-01-31'),
          ('10000000-0000-4000-8000-000000000003', 'quarterly',   '2025-01-31', '2025-01-31'),
          ('10000000-0000-4000-8000-000000000004', 'monthly',     '2025-01-31', '2025-01-31'),
          ('10000000-0000-4000-8000-000000000005', 'monthly',     null,         '2024-01-31')) v(id, mode, start, due)
 where c.agent_id = :A;

select tests.ok(public.mark_premium_paid('10000000-0000-4000-8000-000000000001', 1000, '2025-01-30') ->> 'next_due_date' = '2026-01-31', '#4 yearly: 2025-01-31 -> 2026-01-31');
select tests.ok(public.mark_premium_paid('10000000-0000-4000-8000-000000000002', 1000, '2025-01-30') ->> 'next_due_date' = '2025-07-31', '#4 half_yearly: 2025-01-31 -> 2025-07-31');
select tests.ok(public.mark_premium_paid('10000000-0000-4000-8000-000000000003', 1000, '2025-01-30') ->> 'next_due_date' = '2025-04-30', '#4 quarterly: 2025-01-31 -> 2025-04-30 (month end)');
select tests.ok(public.mark_premium_paid('10000000-0000-4000-8000-000000000004', 1000, '2025-01-30') ->> 'next_due_date' = '2025-02-28', '#4 monthly: 2025-01-31 -> 2025-02-28 (month end)');
select tests.ok(public.mark_premium_paid('10000000-0000-4000-8000-000000000004', 1000, '2025-02-27') ->> 'next_due_date' = '2025-03-31', '#4 monthly again: 2025-02-28 -> 2025-03-31 (anchored on start date, no drift)');
select tests.ok(public.mark_premium_paid('10000000-0000-4000-8000-000000000005', 1000, '2024-01-30') ->> 'next_due_date' = '2024-02-29', '#4 monthly without start date: 2024-01-31 -> 2024-02-29 (leap year)');
select tests.ok((select next_due_date from public.policies where id = '10000000-0000-4000-8000-000000000004') = '2025-03-31', '#4 policy row updated in the same transaction');
select tests.ok((select count(*) from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000004') = 2
  and (select min(period_due_date) from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000004') = '2025-01-31',
  '#4 payments recorded with period_due_date');

select tests.throws($$select public.undo_premium_payment((select id from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000004' and period_due_date = '2025-01-31'))$$,
  '#4 undo of a non-latest payment refused', '%not_latest_payment%');
select tests.ok(public.undo_premium_payment((select id from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000004' and period_due_date = '2025-02-28')) ->> 'next_due_date' = '2025-02-28',
  '#4 undo returns restored due date');
select tests.ok((select next_due_date from public.policies where id = '10000000-0000-4000-8000-000000000004') = '2025-02-28', '#4 undo restored next_due_date 2025-03-31 -> 2025-02-28');
select tests.ok(tests.count($$select 1 from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000004' and deleted_at is not null$$) = 1,
  '#4 undone payment is soft-deleted (sync sees it)');
select tests.ok(public.undo_premium_payment((select id from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000004' and deleted_at is null)) ->> 'next_due_date' = '2025-01-31',
  '#4 undo again restores 2025-01-31');
select tests.ok(public.undo_premium_payment((select id from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000001')) ->> 'next_due_date' = '2025-01-31',
  '#4 yearly undo returns 2025-01-31');
select tests.ok((select next_due_date from public.policies where id = '10000000-0000-4000-8000-000000000001') = '2025-01-31',
  '#4 yearly undo restored next_due_date 2026-01-31 -> 2025-01-31');
select tests.throws($$select public.mark_premium_paid('10000000-0000-4000-8000-000000000001', 0, current_date)$$, '#4 amount must be > 0', '%invalid_amount%');
select tests.throws($$select public.undo_premium_payment((select id from public.premium_payments where policy_id = '10000000-0000-4000-8000-000000000001'))$$,
  '#4 undoing an already-undone payment -> not_found', '%not_found%');
rollback;
