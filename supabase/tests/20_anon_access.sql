-- TESTING.md Database #1: anon has no table access; only the 4 public RPCs are callable.
begin;
select tests.as_anon();
do $$
declare
  t text;
begin
  for t in select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'public' and c.relkind in ('r', 'p', 'v', 'm') order by 1 loop
    perform tests.throws(format('select * from public.%I', t), '#1 anon select ' || t || ' denied', '%permission denied%');
    perform tests.throws(format('delete from public.%I', t), '#1 anon delete ' || t || ' denied', '%permission denied%');
  end loop;
end;
$$;
select tests.throws($$insert into public.enquiries (agent_id, name, source, status) values ('aaaaaaaa-0000-4000-8000-00000000000a', 'x', 'website', 'new')$$,
  '#1 anon insert enquiries denied', '%permission denied%');
select tests.throws('select public.publish_site()', '#1 anon cannot call publish_site', '%permission denied%');
select tests.throws('select public.my_entitlement()', '#1 anon cannot call my_entitlement', '%permission denied%');
select tests.throws($$select public.is_slug_available('abc')$$, '#1 anon cannot call is_slug_available', '%permission denied%');
select tests.throws($$select public.entitlement('aaaaaaaa-0000-4000-8000-00000000000a')$$, '#1 anon cannot call entitlement', '%permission denied%');
select tests.throws('select public.can_write()', '#1 anon cannot call can_write', '%permission denied%');
select tests.throws('select public.catalog_interest_keys()', '#1 anon cannot call catalog_interest_keys', '%permission denied%');
select tests.ok(tests.count('select * from storage.objects') = 0, '#1 anon sees no storage.objects rows');
select tests.lives($$select public.get_public_site('ramesh-sharma')$$, '#1 anon can call get_public_site');
rollback;

begin;
select tests.as_agent('bbbbbbbb-0000-4000-8000-00000000000b');
select tests.throws($$select public.entitlement('aaaaaaaa-0000-4000-8000-00000000000a')$$,
  '#1 authenticated cannot call internal entitlement(agent_id)', '%permission denied%');
select tests.throws($$select public.get_public_site('ramesh-sharma')$$,
  '#1 get_public_site granted to anon only', '%permission denied%');
rollback;
