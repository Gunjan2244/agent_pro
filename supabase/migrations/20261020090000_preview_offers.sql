-- WP5a (DECISIONS #84): in-app previews get their offers from a signed-in RPC.
--
-- public.preview_site_offers(content, template_key) returns {company, offers} exactly as get_public_site would
-- resolve them for a site of the caller with this content as its published content and this template. It reuses
-- private.site_offers_payload unchanged (no existing function is replaced): it builds an in-memory public.sites row
-- (never written) and hands it over, so the preview and the live site can't drift apart.
--
-- Apply after 20261017132241_companies_offers.sql and 20261018073025_catalog_sync.sql. Expected advisors: one more
-- lint 0029 WARN (authenticated can execute a SECURITY DEFINER function), accepted by design (docs/BACKEND.md).

-- {company, offers:{featured, items}} for the caller's draft (docs/BACKEND.md, RPCs; OFFERS.md, Public payload).
--   * The caller is auth.uid(); null raises not_authenticated (42501), like private.require_uid().
--   * content: the draft as it would be published; anything but a JSON object is treated as {}. Over 64KB (the
--     sites content cap, so no published site can be larger) raises invalid_content (22023).
--   * template_key: any text; an unknown key or null has no family, so it behaves like the personal family
--     (no automatic featured offer).
--   * The language is the caller's site's locale (a site that isn't deleted), else the catalog default, as
--     get_public_site would serve it. Nothing else about the site matters: no site row, an unpublished site or a
--     lapsed plan (read-only) still previews.
--   * Same output rules as the public payload: company = {disclaimer} only, never a company name, slug or logo, and
--     never an offer's internal_name; only the caller's approved, active company's live, running offers.
create or replace function public.preview_site_offers(content jsonb, template_key text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := auth.uid();
  v_site public.sites;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if octet_length(preview_site_offers.content::text) > 65536 then
    raise exception 'invalid_content' using errcode = '22023';
  end if;

  v_site.agent_id := v_uid;
  v_site.template_key := preview_site_offers.template_key;
  v_site.published_content := case when jsonb_typeof(preview_site_offers.content) = 'object'
                                   then preview_site_offers.content else '{}'::jsonb end;
  v_site.locale := (select s.locale from public.sites s where s.agent_id = v_uid and s.deleted_at is null);

  return private.site_offers_payload(v_site);
end;
$$;

revoke all on function public.preview_site_offers(jsonb, text) from public, anon, authenticated;
grant execute on function public.preview_site_offers(jsonb, text) to authenticated;
