-- Agent PRO — several companies per agent, each with its insurance types (DECISIONS #89), and the approved companies'
-- names on sites (DECISIONS #90). docs/OFFERS.md, docs/BACKEND.md "Companies and offers".
--   * company_memberships.insurance_types text[] (keys of catalog.insuranceTypes; at least one on pending and
--     approved rows). Existing rows get {life}: every link so far was made by an LIC agent, the app's audience, and
--     the demo company is a life insurer. Set through the column default, so no trigger or audit row fires.
--   * One pending or approved row per (agent, company), plus at most one pending "not listed" row per agent
--     (replaces "one current membership per agent").
--   * request_company(company_id, suggested_name, agency_code, insurance_types) no longer withdraws the agent's other
--     companies; withdraw_company_request(membership_id) and set_membership_types(membership_id, insurance_types)
--     work on one membership; my_company_offers() returns {memberships, offers} for every approved company.
--   * Offers from every approved, active company can be placed and appear (validate_site_offers, resolve_offer_ref,
--     site_offers_payload, auto offers taken in turn from each company); the disclaimer is the earliest-approved
--     company's; custom offers are off when any approved, active company turned them off (the strictest wins).
--   * The payload (get_public_site*, preview_site_offers) gains companies: [{name, insurance_types}] (DECISIONS #90:
--     names as text only, never a logo, slug, id or link).
--   * The admin queue shows the insurance types; `previous` is the agent's last decision for the same company only.
--
-- Needs public.catalog_insurance_types() from 20261022074432_catalog_sync.sql (applied first; plpgsql resolves it
-- lazily, and nothing here calls it at apply time). No table, policy or grant changes beyond the function list
-- below; expected advisors: the same accepted 0029 WARNs, with set_membership_types added (docs/BACKEND.md).
-- Errors: as before, plus invalid_types (22023) and duplicate_membership (P0001).

-- ===========================================================================
-- Column, constraints, indexes
-- ===========================================================================
alter table public.company_memberships add column insurance_types text[] not null default '{life}';
alter table public.company_memberships alter column insurance_types set default '{}';
alter table public.company_memberships
  -- One-dimensional, no null elements (CASE: array_position refuses multi-dimensional arrays).
  add constraint company_memberships_insurance_types_shape
    check (case when coalesce(array_ndims(insurance_types), 1) <> 1 then false
                else array_position(insurance_types, null) is null end),
  -- A request or link names at least one type (DECISIONS #89); history rows keep theirs.
  add constraint company_memberships_insurance_types_required
    check (status not in ('pending', 'approved') or cardinality(insurance_types) >= 1);

drop index public.company_memberships_one_current_idx;
create unique index company_memberships_one_current_per_company_idx on public.company_memberships (agent_id, company_id)
  where status in ('pending', 'approved') and company_id is not null;
create unique index company_memberships_one_pending_unlisted_idx on public.company_memberships (agent_id)
  where status = 'pending' and company_id is null;

-- ===========================================================================
-- Helpers
-- ===========================================================================

-- Types in catalog display order (catalog_insurance_types()); a key no longer in it (deprecated) goes last.
create or replace function private.ordered_insurance_types(p_types text[])
returns text[]
language sql
stable
set search_path = ''
as $$
  select coalesce(array_agg(u.t order by array_position(coalesce(public.catalog_insurance_types(), '{}'::text[]), u.t) nulls last, u.n),
                  '{}'::text[])
    from unnest(coalesce(p_types, '{}'::text[])) with ordinality u(t, n);
$$;

-- An agent's chosen types: trimmed, de-duplicated, in catalog order. Raises invalid_types (22023) when none is given
-- or one isn't an active catalog.insuranceTypes key.
create or replace function private.normalize_insurance_types(p_types text[])
returns text[]
language plpgsql
stable
set search_path = ''
as $$
declare
  v_types text[];
begin
  select coalesce(array_agg(distinct btrim(t)), '{}'::text[]) into v_types from unnest(coalesce(p_types, '{}'::text[])) t;
  if cardinality(v_types) = 0 or array_position(v_types, null) is not null
     or not v_types <@ coalesce(public.catalog_insurance_types(), '{}'::text[]) then
    raise exception 'invalid_types' using errcode = '22023';
  end if;
  return private.ordered_insurance_types(v_types);
end;
$$;

-- What the app shows about a membership (unchanged, plus insurance_types in catalog order). Company name per
-- locale; the logo only while permitted. App only: never in a public payload.
create or replace function private.membership_json(p_m public.company_memberships)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select case when p_m.id is null then null else jsonb_build_object(
    'id',              p_m.id,
    'status',          p_m.status,
    'company',         (select jsonb_build_object('id', c.id, 'name', c.name, 'logo_url', private.company_logo_url(c),
                                                  'allow_custom_offers', c.allow_custom_offers)
                          from public.companies c where c.id = p_m.company_id),
    'suggested_name',  p_m.suggested_name,
    'agency_code',     p_m.agency_code,
    'insurance_types', to_jsonb(private.ordered_insurance_types(p_m.insurance_types)),
    'reject_reason',   p_m.reject_reason,
    'review_note',     p_m.review_note,
    'requested_at',    p_m.requested_at,
    'reviewed_at',     p_m.reviewed_at)
  end;
$$;

-- "The" current membership no longer exists (several companies per agent).
drop function private.current_membership(uuid);

-- ===========================================================================
-- Guard
-- ===========================================================================

-- company_memberships (written only by the RPCs; defence in depth): as before, plus insurance_types: on insert and
-- whenever they change, unique active catalog keys; they change only on a pending or approved row whose status
-- stays the same (no re-review: reviewed_at/by are kept).
create or replace function private.memberships_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    if new.status <> 'pending' then
      raise exception 'invalid_transition' using errcode = 'P0001';
    end if;
  else
    if new.agent_id <> old.agent_id then
      raise exception 'invalid_transition' using errcode = 'P0001';
    end if;
    if new.company_id is distinct from old.company_id
       and not (old.company_id is null and old.status = 'pending' and new.status = 'pending') then
      raise exception 'invalid_transition' using errcode = 'P0001', detail = 'the company can only be set on a pending unlisted request';
    end if;
    if new.status <> old.status and not (
         (old.status = 'pending' and new.status in ('approved', 'rejected', 'withdrawn'))
      or (old.status = 'approved' and new.status in ('revoked', 'withdrawn'))) then
      raise exception 'invalid_transition' using errcode = 'P0001', detail = old.status || ' -> ' || new.status;
    end if;
    if new.insurance_types is distinct from old.insurance_types
       and not (old.status in ('pending', 'approved') and new.status = old.status) then
      raise exception 'invalid_transition' using errcode = 'P0001', detail = 'insurance types change only on a pending or approved membership';
    end if;
    if new.status in ('approved', 'rejected', 'revoked') and new.status <> old.status then
      new.reviewed_at := now();
      new.reviewed_by := auth.uid();
    else
      new.reviewed_at := old.reviewed_at;
      new.reviewed_by := old.reviewed_by;
    end if;
    new.requested_at := old.requested_at;
  end if;
  if (tg_op = 'INSERT' or new.insurance_types is distinct from old.insurance_types)
     and (not new.insurance_types <@ coalesce(public.catalog_insurance_types(), '{}'::text[])
          or cardinality(new.insurance_types) <> (select count(distinct t) from unnest(new.insurance_types) t)) then
    raise exception 'invalid_types' using errcode = '22023';
  end if;
  if new.company_id is not null and new.status in ('pending', 'approved')
     and (tg_op = 'INSERT' or new.company_id is distinct from old.company_id or new.status <> old.status)
     and not exists (select 1 from public.companies c where c.id = new.company_id and c.is_active) then
    raise exception 'not_found' using errcode = 'P0002', detail = 'company';
  end if;
  if new.status = 'approved' and new.company_id is null then
    raise exception 'invalid_transition' using errcode = 'P0001', detail = 'link the request to a company first';
  end if;
  return new;
end;
$$;

-- ===========================================================================
-- Publish validation and the payload
-- ===========================================================================

-- As before, with every approved company: a company offer must belong to one of the agent's approved companies
-- (inactive company or not live: offer_unavailable; ended: offer_ended); a company the agent asked for before but
-- isn't approved for now: offer_unavailable; any other company: offer_wrong_company. Custom offers are refused
-- (custom_offers_off) when any approved, active company has allow_custom_offers = false.
create or replace function private.validate_site_offers(p_agent uuid, p_content jsonb)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uuid       text := '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
  v_errs       jsonb := '[]'::jsonb;
  v_warn       jsonb := '[]'::jsonb;
  v_hidden     jsonb := case when jsonb_typeof(p_content -> 'hidden') = 'array' then p_content -> 'hidden' else '[]'::jsonb end;
  v_section    jsonb := case when jsonb_typeof(p_content -> 'offers') = 'object' then p_content -> 'offers' else '{}'::jsonb end;
  v_custom     jsonb := case when jsonb_typeof(v_section -> 'custom') = 'array' then v_section -> 'custom' else '[]'::jsonb end;
  v_approved   uuid[];
  v_custom_off boolean;
  v_offer      public.offers;
  v_ids        text[] := '{}';
  v_seen       text[] := '{}';
  v_refs       jsonb := '[]'::jsonb;
  r            record;
  v_src        text;
  v_id         text;
  v_issue      jsonb;
  v_feat_id    text := case when jsonb_typeof(p_content #> '{hero,featuredOffer}') = 'object'
                                 and p_content #>> '{hero,featuredOffer,source}' = 'agent'
                            then p_content #>> '{hero,featuredOffer,id}' end;
  v_terms      text[];
  v_field      text;
begin
  select coalesce(array_agg(m.company_id), '{}'::uuid[]) into v_approved
    from public.company_memberships m
   where m.agent_id = p_agent and m.status = 'approved' and m.company_id is not null;
  v_custom_off := exists (select 1 from public.company_memberships m join public.companies c on c.id = m.company_id
                           where m.agent_id = p_agent and m.status = 'approved' and c.is_active and not c.allow_custom_offers);

  -- Custom offer ids are what references point at: uuids, unique.
  for r in select e.value as item, (e.ordinality - 1)::int as idx from jsonb_array_elements(v_custom) with ordinality e loop
    v_id := case when jsonb_typeof(r.item) = 'object' then r.item ->> 'id' end;
    if v_id is null or v_id !~ v_uuid then
      v_errs := v_errs || jsonb_build_array(jsonb_build_object('section', 'offers', 'field', 'id', 'item', r.idx, 'rule', 'offer_shape'));
    elsif v_id = any (v_ids) then
      v_errs := v_errs || jsonb_build_array(jsonb_build_object('section', 'offers', 'field', 'id', 'item', r.idx, 'rule', 'duplicate'));
    else
      v_ids := v_ids || v_id;
    end if;
    -- Public text (DECISIONS #77) for what can render: the section is shown, or the hero features this offer.
    if jsonb_typeof(r.item) = 'object' and (not v_hidden ? 'offers' or (v_id is not null and v_id = v_feat_id)) then
      foreach v_field in array array['title', 'summary'] loop
        if jsonb_typeof(r.item -> v_field) = 'string' then
          if v_terms is null then
            v_terms := private.public_text_terms();
          end if;
          if private.public_text_match(r.item ->> v_field, v_terms) is not null then
            v_errs := v_errs || jsonb_build_array(jsonb_build_object('section', 'offers', 'field', v_field, 'item', r.idx, 'rule', 'blocked_term'));
          end if;
        end if;
      end loop;
    end if;
  end loop;

  if p_content #> '{hero,featuredOffer}' is not null and jsonb_typeof(p_content #> '{hero,featuredOffer}') <> 'null' then
    v_refs := v_refs || jsonb_build_array(jsonb_build_object('section', 'hero', 'field', 'featuredOffer', 'item', null,
                                                             'ref', p_content #> '{hero,featuredOffer}'));
  end if;
  if not v_hidden ? 'offers' and jsonb_typeof(v_section -> 'items') = 'array' then
    for r in select e.value as ref, (e.ordinality - 1)::int as idx from jsonb_array_elements(v_section -> 'items') with ordinality e loop
      v_refs := v_refs || jsonb_build_array(jsonb_build_object('section', 'offers', 'field', 'items', 'item', r.idx, 'ref', r.ref));
    end loop;
  end if;

  for r in select e.value as x from jsonb_array_elements(v_refs) e loop
    v_issue := (r.x - 'ref');
    v_src := case when jsonb_typeof(r.x -> 'ref') = 'object' then r.x #>> '{ref,source}' end;
    v_id := case when jsonb_typeof(r.x -> 'ref') = 'object' then r.x #>> '{ref,id}' end;
    if v_src is null or v_src not in ('company', 'agent') or v_id is null or v_id !~ v_uuid then
      v_errs := v_errs || jsonb_build_array(v_issue || '{"rule": "offer_shape"}');
      continue;
    end if;
    if r.x ->> 'section' = 'offers' then
      if (v_src || ':' || v_id) = any (v_seen) then
        v_errs := v_errs || jsonb_build_array(v_issue || '{"rule": "duplicate"}');
        continue;
      end if;
      v_seen := v_seen || (v_src || ':' || v_id);
    end if;

    if v_src = 'agent' then
      if not v_id = any (v_ids) then
        v_errs := v_errs || jsonb_build_array(v_issue || '{"rule": "offer_custom_missing"}');
      elsif v_custom_off then
        v_errs := v_errs || jsonb_build_array(v_issue || '{"rule": "custom_offers_off"}');
      elsif (select private.try_date(e ->> 'endsOn') < private.today_ist()
               from jsonb_array_elements(v_custom) e where e ->> 'id' = v_id limit 1) then
        v_warn := v_warn || jsonb_build_array(v_issue || '{"rule": "offer_ended"}');
      end if;
    else
      select o.* into v_offer from public.offers o where o.id = v_id::uuid;
      if v_offer.id is null then
        v_errs := v_errs || jsonb_build_array(v_issue || '{"rule": "offer_wrong_company"}');
      elsif v_offer.company_id = any (v_approved) then
        if not exists (select 1 from public.companies c where c.id = v_offer.company_id and c.is_active)
           or v_offer.status <> 'live' then
          v_warn := v_warn || jsonb_build_array(v_issue || '{"rule": "offer_unavailable"}');
        elsif v_offer.ends_at <= now() then
          v_warn := v_warn || jsonb_build_array(v_issue || '{"rule": "offer_ended"}');
        end if;
      elsif exists (select 1 from public.company_memberships m
                     where m.agent_id = p_agent and m.company_id = v_offer.company_id) then
        -- A company the agent asked for before but isn't approved for now: keep it, skip it at render.
        v_warn := v_warn || jsonb_build_array(v_issue || '{"rule": "offer_unavailable"}');
      else
        v_errs := v_errs || jsonb_build_array(v_issue || '{"rule": "offer_wrong_company"}');
      end if;
    end if;
  end loop;

  return jsonb_build_object('errors', v_errs, 'warnings', v_warn);
end;
$$;

-- One placed offer as the renderer sees it (as before), now against a set of companies: p_companies are the
-- agent's approved, active companies; p_custom_ok is false when any of them turned custom offers off.
create or replace function private.resolve_offer_ref(
  p_ref jsonb, p_custom jsonb, p_companies uuid[], p_custom_ok boolean, p_locale text, p_default text, p_featured boolean)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_offer public.offers;
  v_item  jsonb;
  v_ends  date;
  v_title text;
begin
  if jsonb_typeof(p_ref) <> 'object' or p_ref ->> 'id' is null then
    return null;
  end if;

  if p_ref ->> 'source' = 'company' then
    if cardinality(coalesce(p_companies, '{}'::uuid[])) = 0
       or p_ref ->> 'id' !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
      return null;
    end if;
    select o.* into v_offer from public.offers o
     where o.id = (p_ref ->> 'id')::uuid and o.company_id = any (p_companies)
       and o.status = 'live' and o.starts_at <= now() and o.ends_at > now();
    if v_offer.id is null then
      return null;
    end if;
    v_title := coalesce(nullif(btrim(v_offer.content #>> array[p_locale, 'title']), ''),
                        nullif(btrim(v_offer.content #>> array[p_default, 'title']), ''));
    if v_title is null then
      return null;
    end if;
    return jsonb_build_object(
      'source', 'company', 'id', v_offer.id, 'title', v_title,
      'summary', coalesce(nullif(v_offer.content #>> array[p_locale, 'summary'], ''), nullif(v_offer.content #>> array[p_default, 'summary'], '')),
      'details', coalesce(nullif(v_offer.content #>> array[p_locale, 'details'], ''), nullif(v_offer.content #>> array[p_default, 'details'], '')),
      'badge', coalesce(nullif(v_offer.content #>> array[p_locale, 'badge'], ''), nullif(v_offer.content #>> array[p_default, 'badge'], '')),
      'disclaimer', coalesce(nullif(v_offer.content #>> array[p_locale, 'disclaimer'], ''), nullif(v_offer.content #>> array[p_default, 'disclaimer'], '')),
      'image_url', case when p_featured then null else v_offer.image_url end,
      'cta', case v_offer.cta_kind
               when 'link' then jsonb_build_object('kind', 'link', 'url', v_offer.cta_url)
               when 'enquire' then jsonb_build_object('kind', 'enquire', 'interest', v_offer.interest)
               else jsonb_build_object('kind', v_offer.cta_kind) end,
      'ends_on', (v_offer.ends_at at time zone 'Asia/Kolkata')::date,
      'interest', v_offer.interest,
      'from_agent', false);
  elsif p_ref ->> 'source' = 'agent' then
    if not coalesce(p_custom_ok, true) then
      return null;
    end if;
    select e into v_item from jsonb_array_elements(case when jsonb_typeof(p_custom) = 'array' then p_custom else '[]'::jsonb end) e
     where jsonb_typeof(e) = 'object' and e ->> 'id' = p_ref ->> 'id' limit 1;
    v_ends := private.try_date(v_item ->> 'endsOn');
    v_title := nullif(btrim(coalesce(v_item ->> 'title', '')), '');
    if v_item is null or v_title is null or v_ends is null or v_ends < private.today_ist()
       or v_item -> 'confirmed' is distinct from 'true'::jsonb
       or not coalesce(v_item ->> 'ctaKind', '') = any (coalesce(public.catalog_offer_cta_kinds_agent(), '{}'::text[])) then
      return null;
    end if;
    return jsonb_build_object(
      'source', 'agent', 'id', v_item ->> 'id', 'title', v_title,
      'summary', nullif(btrim(coalesce(v_item ->> 'summary', '')), ''),
      'details', null, 'badge', null, 'disclaimer', null, 'image_url', null,
      'cta', case when v_item ->> 'ctaKind' = 'enquire'
                  then jsonb_build_object('kind', 'enquire', 'interest', v_item ->> 'interest')
                  else jsonb_build_object('kind', v_item ->> 'ctaKind') end,
      'ends_on', v_ends,
      'interest', nullif(btrim(coalesce(v_item ->> 'interest', '')), ''),
      'from_agent', true);
  end if;
  return null;
end;
$$;

-- {companies, company, offers:{featured, items}} for a site, in its language (docs/BACKEND.md, Public payload):
--   * companies (DECISIONS #90): the approved links to active companies, earliest approval first:
--     [{name, insurance_types}], the name in the site's language (fallback catalog_default_locale()), the types in
--     catalog order. Nothing else about a company (no logo, slug, id or link).
--   * company: {disclaimer} of the earliest-approved active company, else null (DECISIONS #77, #89).
--   * Picked offers resolved against every approved, active company; custom offers unless one of them turned them off.
--   * Auto offers (DECISIONS #79): up to 3 running offers, taken in turn from each company (each company's own order
--     sort, starts_at, id; companies in approval order), so one company never fills the section alone. Featured as
--     before for the `offers` family.
create or replace function private.site_offers_payload(p_site public.sites)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_default   text := coalesce(public.catalog_default_locale(), 'en');
  v_locale    text := case when p_site.locale = any (coalesce(public.catalog_locales(), '{}'::text[])) then p_site.locale else v_default end;
  v_content   jsonb := coalesce(p_site.published_content, '{}'::jsonb);
  v_section   jsonb := case when jsonb_typeof(v_content -> 'offers') = 'object' then v_content -> 'offers' else '{}'::jsonb end;
  v_custom    jsonb := v_section -> 'custom';
  v_hidden    jsonb := case when jsonb_typeof(v_content -> 'hidden') = 'array' then v_content -> 'hidden' else '[]'::jsonb end;
  v_feat_ref  jsonb := v_content #> '{hero,featuredOffer}';
  v_feat_set  boolean;
  v_companies uuid[];
  v_list      jsonb;
  v_custom_ok boolean;
  v_first     public.companies;
  v_items     jsonb;
  v_featured  jsonb;
  v_auto      boolean;
begin
  select coalesce(array_agg(c.id order by m.reviewed_at, m.requested_at, m.id), '{}'::uuid[]),
         coalesce(jsonb_agg(jsonb_build_object(
                    'name', coalesce(nullif(btrim(c.name ->> v_locale), ''), c.name ->> v_default),
                    'insurance_types', to_jsonb(private.ordered_insurance_types(m.insurance_types)))
                  order by m.reviewed_at, m.requested_at, m.id), '[]'::jsonb),
         coalesce(bool_and(c.allow_custom_offers), true)
    into v_companies, v_list, v_custom_ok
    from public.company_memberships m
    join public.companies c on c.id = m.company_id
   where m.agent_id = p_site.agent_id and m.status = 'approved' and c.is_active;
  if cardinality(v_companies) > 0 then
    select c.* into v_first from public.companies c where c.id = v_companies[1];
  end if;

  v_feat_set := v_feat_ref is not null and jsonb_typeof(v_feat_ref) <> 'null';
  v_auto := cardinality(v_companies) > 0
        and not v_hidden ? 'offers'
        and coalesce(jsonb_array_length(case when jsonb_typeof(v_section -> 'items') = 'array' then v_section -> 'items' end), 0) = 0
        and coalesce(jsonb_array_length(case when jsonb_typeof(v_custom) = 'array' then v_custom end), 0) = 0;

  if v_auto then
    select coalesce(jsonb_agg(a.o order by a.rn), '[]'::jsonb) into v_items
      from (select y.o, row_number() over (order by y.rc, y.cpos) as rn
              from (select x.o, x.cpos, row_number() over (partition by x.company_id order by x.sort, x.starts_at, x.id) as rc
                      from (select private.resolve_offer_ref(jsonb_build_object('source', 'company', 'id', o.id), null, v_companies,
                                                             v_custom_ok, v_locale, v_default, false) as o,
                                   o.company_id, array_position(v_companies, o.company_id) as cpos, o.sort, o.starts_at, o.id
                              from public.offers o
                             where o.company_id = any (v_companies) and o.status = 'live'
                               and o.starts_at <= now() and o.ends_at > now()
                               and not (v_feat_set and jsonb_typeof(v_feat_ref) = 'object' and v_feat_ref ->> 'source' = 'company'
                                        and o.id::text = v_feat_ref ->> 'id')) x
                     where x.o is not null) y) a
     where a.rn <= 3;
  else
    select coalesce(jsonb_agg(x.o order by x.idx), '[]'::jsonb) into v_items
      from (select private.resolve_offer_ref(e.value, v_custom, v_companies, v_custom_ok, v_locale, v_default, false) as o, e.ordinality as idx
              from jsonb_array_elements(case when jsonb_typeof(v_section -> 'items') = 'array'
                                             then v_section -> 'items' else '[]'::jsonb end) with ordinality e) x
     where x.o is not null;
  end if;

  if v_feat_set then
    v_featured := private.resolve_offer_ref(v_feat_ref, v_custom, v_companies, v_custom_ok, v_locale, v_default, true);
  elsif v_auto and public.catalog_template_family(p_site.template_key) = 'offers' and jsonb_array_length(v_items) > 0 then
    v_featured := (v_items -> 0) || jsonb_build_object('image_url', null);
    v_items := v_items - 0;
  end if;

  return jsonb_build_object(
    'companies', v_list,
    'company', case when v_first.id is null then null else jsonb_build_object(
      'disclaimer', coalesce(nullif(v_first.disclaimer ->> v_locale, ''), nullif(v_first.disclaimer ->> v_default, ''))) end,
    'offers', jsonb_build_object('featured', v_featured, 'items', v_items));
end;
$$;

-- The old single-company resolver (site_offers_payload no longer calls it).
drop function private.resolve_offer_ref(jsonb, jsonb, public.companies, text, text, boolean);

-- ===========================================================================
-- Agent RPCs
-- ===========================================================================
drop function public.request_company(uuid, text, text);
drop function public.withdraw_company_request();

-- Ask to be linked to a company (agency_code required, <= 30) or, with suggested_name (<= 80), to a company that isn't
-- listed; exactly one of the two. insurance_types: at least one catalog.insuranceTypes key (stored unique, in catalog
-- order). The agent's other companies are never touched. Asking again for a company with a pending or approved row
-- and the same agency code (for "not listed": the same name and code) changes only the types, with no new review;
-- a different agency code (or name) withdraws that row and makes a new pending request. At most one "not listed"
-- request is pending: a new one replaces it. Returns the membership (private.membership_json).
-- Errors: invalid / invalid_types (22023), not_found (P0002: no active company with that id), plan_read_only.
create or replace function public.request_company(company_id uuid, suggested_name text, agency_code text, insurance_types text[])
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := private.require_uid();
  v_company uuid := request_company.company_id;
  v_name    text := nullif(btrim(coalesce(request_company.suggested_name, '')), '');
  v_code    text := nullif(btrim(coalesce(request_company.agency_code, '')), '');
  v_types   text[];
  v_cur     public.company_memberships;
  v_new     public.company_memberships;
begin
  perform private.require_can_write();
  if (v_company is null) = (v_name is null) then
    raise exception 'invalid' using errcode = '22023', detail = 'give either company_id or suggested_name';
  end if;
  if v_company is not null then
    if not exists (select 1 from public.companies c where c.id = v_company and c.is_active) then
      raise exception 'not_found' using errcode = 'P0002';
    end if;
    if v_code is null or char_length(v_code) > 30 then
      raise exception 'invalid' using errcode = '22023', detail = 'agency_code';
    end if;
  elsif char_length(v_name) > 80 or (v_code is not null and char_length(v_code) > 30) then
    raise exception 'invalid' using errcode = '22023', detail = 'suggested_name';
  end if;
  v_types := private.normalize_insurance_types(request_company.insurance_types);

  -- One membership write at a time per agent (the partial unique indexes are the backstop).
  perform pg_advisory_xact_lock(hashtextextended('company_memberships:' || v_uid::text, 0));
  if v_company is not null then
    select m.* into v_cur from public.company_memberships m
     where m.agent_id = v_uid and m.company_id = v_company and m.status in ('pending', 'approved')
     for update;
  else
    select m.* into v_cur from public.company_memberships m
     where m.agent_id = v_uid and m.company_id is null and m.status = 'pending'
     for update;
  end if;

  if v_cur.id is not null and v_cur.agency_code is not distinct from v_code
     and (v_company is not null or lower(v_cur.suggested_name) = lower(v_name)) then
    if v_cur.insurance_types is distinct from v_types then
      update public.company_memberships m set insurance_types = v_types where m.id = v_cur.id
      returning * into v_cur;
    end if;
    return private.membership_json(v_cur);
  end if;
  if v_cur.id is not null then
    update public.company_memberships m set status = 'withdrawn' where m.id = v_cur.id;
  end if;

  insert into public.company_memberships (agent_id, company_id, suggested_name, agency_code, insurance_types)
  values (v_uid, v_company, case when v_company is null then v_name end, v_code, v_types)
  returning * into v_new;
  return private.membership_json(v_new);
end;
$$;

-- Withdraw one of the caller's requests or links (pending or approved -> withdrawn); already withdrawn is a no-op.
-- Not the caller's: not_found (P0002); rejected or revoked: invalid_transition (P0001). No can_write(): a lapsed
-- agent can still remove a company. Returns the membership.
create or replace function public.withdraw_company_request(membership_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_m   public.company_memberships;
begin
  select m.* into v_m from public.company_memberships m
   where m.id = withdraw_company_request.membership_id and m.agent_id = v_uid
   for update;
  if v_m.id is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if v_m.status in ('pending', 'approved') then
    update public.company_memberships m set status = 'withdrawn' where m.id = v_m.id
    returning * into v_m;
  elsif v_m.status <> 'withdrawn' then
    raise exception 'invalid_transition' using errcode = 'P0001', detail = v_m.status || ' -> withdrawn';
  end if;
  return private.membership_json(v_m);
end;
$$;

-- Change the insurance types of one of the caller's pending or approved memberships. No new review: an approved link
-- stays approved (DECISIONS #89). Errors: not_found (P0002), invalid_transition (P0001, not pending or approved),
-- invalid_types (22023), plan_read_only. Returns the membership.
create or replace function public.set_membership_types(membership_id uuid, insurance_types text[])
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid   uuid := private.require_uid();
  v_m     public.company_memberships;
  v_types text[];
begin
  perform private.require_can_write();
  select m.* into v_m from public.company_memberships m
   where m.id = set_membership_types.membership_id and m.agent_id = v_uid
   for update;
  if v_m.id is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if v_m.status not in ('pending', 'approved') then
    raise exception 'invalid_transition' using errcode = 'P0001', detail = v_m.status;
  end if;
  v_types := private.normalize_insurance_types(set_membership_types.insurance_types);
  if v_m.insurance_types is distinct from v_types then
    update public.company_memberships m set insurance_types = v_types where m.id = v_m.id
    returning * into v_m;
  end if;
  return private.membership_json(v_m);
end;
$$;

-- {memberships, offers} (works on a lapsed plan):
--   * memberships: per company (and one entry for "not listed"), the pending or approved row, else the latest row if
--     it's rejected or revoked (withdrawn ones are left out), each as private.membership_json, oldest request first;
--   * offers: the live offers that haven't ended (upcoming included) of every approved, active company, each with its
--     company_id, companies in approval order, then sort, starts_at, id.
create or replace function public.my_company_offers()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid    uuid := private.require_uid();
  v_list   jsonb;
  v_offers jsonb;
begin
  select coalesce(jsonb_agg(private.membership_json(x.m) order by (x.m).requested_at, (x.m).id), '[]'::jsonb)
    into v_list
    from (select distinct on (coalesce(m.company_id::text, '')) m
            from public.company_memberships m
           where m.agent_id = v_uid
           order by coalesce(m.company_id::text, ''), (m.status in ('pending', 'approved')) desc, m.requested_at desc, m.id desc) x
   where (x.m).status <> 'withdrawn';

  select coalesce(jsonb_agg(jsonb_build_object(
           'id', o.id, 'company_id', o.company_id, 'content', o.content, 'image_url', o.image_url, 'cta_kind', o.cta_kind,
           'cta_url', o.cta_url, 'interest', o.interest, 'starts_at', o.starts_at, 'ends_at', o.ends_at,
           'phase', private.offer_phase(o))
           order by m.reviewed_at, m.requested_at, o.sort, o.starts_at, o.id), '[]'::jsonb)
    into v_offers
    from public.company_memberships m
    join public.companies c on c.id = m.company_id and c.is_active
    join public.offers o on o.company_id = m.company_id
   where m.agent_id = v_uid and m.status = 'approved' and o.status = 'live' and o.ends_at > now();

  return jsonb_build_object('memberships', v_list, 'offers', v_offers);
end;
$$;

-- ===========================================================================
-- Admin RPCs
-- ===========================================================================

-- As before, plus: refused (duplicate_membership, P0001) when the agent already has a pending or approved row for
-- that company (one per agent and company).
create or replace function public.admin_link_membership(membership_id uuid, company_id uuid)
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_admin();
  v_m   public.company_memberships;
begin
  if not public.is_admin_for(null) then
    raise exception 'forbidden' using errcode = '42501';
  end if;
  select m.* into v_m from public.company_memberships m where m.id = admin_link_membership.membership_id for update;
  if v_m.id is null then
    raise exception 'not_found' using errcode = 'P0002';
  end if;
  if v_m.status <> 'pending' or v_m.company_id is not null then
    raise exception 'invalid_transition' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.companies c where c.id = admin_link_membership.company_id and c.is_active) then
    raise exception 'not_found' using errcode = 'P0002', detail = 'company';
  end if;
  if exists (select 1 from public.company_memberships x
              where x.agent_id = v_m.agent_id and x.company_id = admin_link_membership.company_id
                and x.status in ('pending', 'approved')) then
    raise exception 'duplicate_membership' using errcode = 'P0001',
      detail = 'the agent already has a request or link for this company; reject this one';
  end if;
  update public.company_memberships m set company_id = admin_link_membership.company_id
   where m.id = v_m.id
  returning * into v_m;
  return private.membership_json(v_m);
end;
$$;

-- The review queue (as before), plus each request's insurance_types (catalog order). `previous` is the agent's last
-- decided request for the same company (or, for a "not listed" request, the last decided "not listed" one), so an
-- admin scoped to one company never learns about the agent's other companies.
create or replace function public.admin_membership_queue(company_id uuid, status text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid    uuid := private.require_admin();
  v_status text := coalesce(nullif(btrim(coalesce(admin_membership_queue.status, '')), ''), 'pending');
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', q.id, 'company_id', q.company_id, 'suggested_name', q.suggested_name, 'status', q.status,
             'agency_code', q.agency_code, 'insurance_types', to_jsonb(private.ordered_insurance_types(q.insurance_types)),
             'requested_at', q.requested_at, 'reviewed_at', q.reviewed_at,
             'reject_reason', q.reject_reason, 'review_note', q.review_note,
             'agent', jsonb_build_object('full_name', p.full_name, 'designation', p.designation, 'city', p.city, 'email', p.email),
             'previous', (select jsonb_build_object('status', x.status, 'company_id', x.company_id,
                                                    'reject_reason', x.reject_reason, 'reviewed_at', x.reviewed_at)
                            from public.company_memberships x
                           where x.agent_id = q.agent_id and x.id <> q.id
                             and x.company_id is not distinct from q.company_id
                             and x.status in ('approved', 'rejected', 'revoked')
                           order by coalesce(x.reviewed_at, x.requested_at) desc limit 1))
             order by q.requested_at, q.id)
      from (select m.* from public.company_memberships m
             where m.status = v_status
               and public.is_admin_for(m.company_id)
               and (admin_membership_queue.company_id is null or m.company_id = admin_membership_queue.company_id)
             order by m.requested_at, m.id
             limit 200) q
      left join public.profiles p on p.id = q.agent_id), '[]'::jsonb);
end;
$$;

-- ===========================================================================
-- Grants
-- ===========================================================================
revoke all on function private.ordered_insurance_types(text[]) from public, anon, authenticated;
revoke all on function private.normalize_insurance_types(text[]) from public, anon, authenticated;
revoke all on function private.membership_json(public.company_memberships) from public, anon, authenticated;
revoke all on function private.memberships_guard() from public, anon, authenticated;
revoke all on function private.validate_site_offers(uuid, jsonb) from public, anon, authenticated;
revoke all on function private.resolve_offer_ref(jsonb, jsonb, uuid[], boolean, text, text, boolean) from public, anon, authenticated;
revoke all on function private.site_offers_payload(public.sites) from public, anon, authenticated;

revoke all on function public.request_company(uuid, text, text, text[]) from public, anon, authenticated;
revoke all on function public.withdraw_company_request(uuid) from public, anon, authenticated;
revoke all on function public.set_membership_types(uuid, text[]) from public, anon, authenticated;
revoke all on function public.my_company_offers() from public, anon, authenticated;
revoke all on function public.admin_link_membership(uuid, uuid) from public, anon, authenticated;
revoke all on function public.admin_membership_queue(uuid, text) from public, anon, authenticated;

grant execute on function public.request_company(uuid, text, text, text[]) to authenticated;
grant execute on function public.withdraw_company_request(uuid) to authenticated;
grant execute on function public.set_membership_types(uuid, text[]) to authenticated;
grant execute on function public.my_company_offers() to authenticated;
grant execute on function public.admin_link_membership(uuid, uuid) to authenticated;
grant execute on function public.admin_membership_queue(uuid, text) to authenticated;
