-- Agent PRO — Phase 3 — storage (BACKEND.md, Storage).
-- Bucket `site-media`: public read (served by public URL, no select policy needed for that),
-- images only, 2 MB max. Writes only inside the caller's own folder `<auth.uid()>/...`.
-- Insert/update also require can_write() (lapsed = read-only); delete does not, so a lapsed
-- agent can still clean up media and delete their account.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('site-media', 'site-media', true, 2097152, array['image/*'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Listing is limited to the owner's folder (also needed by the Storage API for upsert).
create policy site_media_select_own on storage.objects
  for select to authenticated
  using (bucket_id = 'site-media' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy site_media_insert_own on storage.objects
  for insert to authenticated
  with check (bucket_id = 'site-media'
              and (storage.foldername(name))[1] = (select auth.uid())::text
              and (select public.can_write()));

create policy site_media_update_own on storage.objects
  for update to authenticated
  using (bucket_id = 'site-media'
         and (storage.foldername(name))[1] = (select auth.uid())::text
         and (select public.can_write()))
  with check (bucket_id = 'site-media'
              and (storage.foldername(name))[1] = (select auth.uid())::text
              and (select public.can_write()));

create policy site_media_delete_own on storage.objects
  for delete to authenticated
  using (bucket_id = 'site-media' and (storage.foldername(name))[1] = (select auth.uid())::text);
