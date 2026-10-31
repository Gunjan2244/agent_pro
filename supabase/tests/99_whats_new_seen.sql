-- LOCAL TESTS ONLY. "What's new" seen on the account (supabase/migrations/20261029090000_whats_new_seen.sql,
-- DECISIONS #127): profiles.whats_new_seen and public.mark_whats_new_seen(seen). Every block rolls back.
\set A '''aaaaaaaa-0000-4000-8000-00000000000a'''
\set B '''bbbbbbbb-0000-4000-8000-00000000000b'''
\set C '''cccccccc-0000-4000-8000-00000000000c'''

select tests.ok((select whats_new_seen from public.profiles where id = :A) = 0, '#W a profile starts at 0 (nothing seen)');

begin;
select tests.as_agent(:A);
select tests.ok((public.mark_whats_new_seen(4) #>> '{profile,whats_new_seen}')::int = 4, '#W mark raises it and returns the row');
select tests.ok((public.mark_whats_new_seen(2) #>> '{profile,whats_new_seen}')::int = 4, '#W a lower mark never lowers it');
select tests.ok(public.mark_whats_new_seen(-1) = '{"ok": false, "error": "invalid", "field": "seen"}'::jsonb
  and public.mark_whats_new_seen(null) ->> 'error' = 'invalid', '#W negative or null is invalid');
select tests.ok((select whats_new_seen from public.profiles where id = :A) = 4, '#W the caller reads it back (owner RLS)');
reset role;
select tests.ok((select whats_new_seen from public.profiles where id = :B) = 0, '#W only the caller''s own row changes');
rollback;

begin;
update public.subscriptions set trial_ends_at = now() - interval '16 days' where agent_id = :C;
select tests.as_agent(:C);
select tests.ok(not public.can_write() and (public.mark_whats_new_seen(4) #>> '{profile,whats_new_seen}')::int = 4,
  '#W a lapsed agent can still mark (no can_write, like record_consent)');
rollback;

begin;
select tests.as_anon();
select tests.throws('select public.mark_whats_new_seen(4)', '#W anon cannot execute it', '%permission denied%');
rollback;
