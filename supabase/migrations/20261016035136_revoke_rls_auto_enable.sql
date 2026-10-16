-- Agent PRO — advisor fix (lints 0028/0029): Supabase's project-creation helper
-- public.rls_auto_enable() (an event-trigger function that turns on RLS for new tables) is
-- SECURITY DEFINER and was executable by anon/authenticated through /rest/v1/rpc. Nobody needs to
-- call it through the API; the event trigger keeps working without EXECUTE for API roles.
-- Guarded: the function only exists on hosted projects created with that option (not in local tests).
do $$
begin
  if to_regprocedure('public.rls_auto_enable()') is not null then
    revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
  end if;
end;
$$;
