-- Agent PRO — "What's new" seen, kept on the account (DECISIONS #127; docs/BACKEND.md, RPCs).
--
-- The app used to keep the highest What's new version an agent had seen only on the phone (DataStore, no backup), so a
-- reinstall, "Clear storage" or a new phone showed every item again. It now also keeps it here:
--   * profiles.whats_new_seen: the highest version seen (0 = none). Synced into the app's cache with the profile.
--   * public.mark_whats_new_seen(seen): raises it to `seen`, never lowers it (an older build can't undo a newer mark).
--     No can_write(), like record_consent: a lapsed agent can still dismiss What's new. Only the caller's own row.
-- Not personal data (an app version number); nothing to add to exports or retention.

alter table public.profiles
  add column whats_new_seen integer not null default 0
  constraint profiles_whats_new_seen_check check (whats_new_seen >= 0);

-- {ok:true, profile:<the caller's profiles row>} | {ok:false, error:'invalid', field:'seen'}. Raises 42501 without a session.
-- Touches the row (and so updated_at and sync) only when the value actually goes up.
create or replace function public.mark_whats_new_seen(seen integer)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_row public.profiles;
begin
  if seen is null or seen < 0 then
    return jsonb_build_object('ok', false, 'error', 'invalid', 'field', 'seen');
  end if;
  update public.profiles p set whats_new_seen = mark_whats_new_seen.seen
   where p.id = v_uid and p.whats_new_seen < mark_whats_new_seen.seen
  returning p.* into v_row;
  if not found then
    select p.* into v_row from public.profiles p where p.id = v_uid;
  end if;
  return jsonb_build_object('ok', true, 'profile', to_jsonb(v_row));
end;
$$;

revoke all on function public.mark_whats_new_seen(integer) from public, anon, authenticated;
grant execute on function public.mark_whats_new_seen(integer) to authenticated;
