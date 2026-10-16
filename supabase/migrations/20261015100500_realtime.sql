-- Agent PRO — Phase 3 — realtime (BACKEND.md, Realtime). RLS applies to realtime too:
-- each agent only receives changes to rows their select policy allows.
alter publication supabase_realtime
  add table public.enquiries, public.orders, public.subscriptions, public.site_domains;
