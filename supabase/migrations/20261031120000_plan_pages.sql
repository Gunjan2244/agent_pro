-- Agent PRO — plan pages (DECISIONS #130, docs/OFFERS.md "Plans", docs/WEB_RENDERER.md "Plan pages").
--
--   * company_plans gains what a plan page shows: `slug` (its address, /insurance/<type>/<slug>; set once from the
--     name, unique everywhere), `key_features` (up to 5 points the admin copies WORD FOR WORD from the insurer's plan
--     page; every agent's default), `features_url` (where they were copied from; default the plan page) and
--     `features_checked_on`, and links to the insurer's documents (`brochure_url`, `policy_url`, `cis_url`,
--     `calculator_url`), each on the company's own website like `url`.
--   * public.agent_plan_features: an agent's own wording of a plan's key features for their site (1–5 points, no
--     product or insurer names: the public text check). No row = the insurer's wording. Written only by
--     set_plan_features(plan_id, features); null resets to the insurer's wording.
--   * public.agent_featured_plans: the plans the agent features on Home (up to 6, in order). No row = automatic (the
--     first 6 plans the site shows); an empty list = none. Written only by set_featured_plans(plan_ids).
--   * offers.plan_id: the official plan an offer is on (same company; the offer's interest follows the plan). A site
--     shows an offer on a plan only while it shows that plan (DECISIONS #130); offers without a plan are
--     company-wide, as before. The payload's company offers carry `plan_id`.
--   * The payload's plans gain slug, features (the agent's wording when they have one, else the insurer's),
--     features_own, features_url, features_checked_on, docs {brochure, policy, cis, calculator} and featured (Home
--     position 1–6, or null). my_company_plans gains each plan's default and own features and the featured choice.
--
-- Errors: 22023 invalid_slug / invalid_features / invalid_plan / unknown_plan:<id> / too_many_featured /
-- plan_wrong_company / blocked_term:<point>:<term> / url_not_company_site / invalid_url; 42501 not_authenticated /
-- plan_read_only.
-- Expected advisors: the accepted 0029 WARNs gain set_plan_features and set_featured_plans (authenticated, SECURITY
-- DEFINER).

-- ===========================================================================
-- Columns and tables
-- ===========================================================================
alter table public.company_plans
  add column slug text,
  add column key_features text[] not null default '{}',
  add column features_url text,
  add column features_checked_on date,
  add column brochure_url text,
  add column policy_url text,
  add column cis_url text,
  add column calculator_url text,
  add constraint company_plans_slug_format check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(slug) <= 80),
  add constraint company_plans_features_shape check (coalesce(array_ndims(key_features), 1) = 1 and cardinality(key_features) <= 5),
  add constraint company_plans_doc_urls_https check (
    (features_url is null or (features_url ~* '^https://[^[:space:]]+$' and char_length(features_url) <= 500)) and
    (brochure_url is null or (brochure_url ~* '^https://[^[:space:]]+$' and char_length(brochure_url) <= 500)) and
    (policy_url is null or (policy_url ~* '^https://[^[:space:]]+$' and char_length(policy_url) <= 500)) and
    (cis_url is null or (cis_url ~* '^https://[^[:space:]]+$' and char_length(cis_url) <= 500)) and
    (calculator_url is null or (calculator_url ~* '^https://[^[:space:]]+$' and char_length(calculator_url) <= 500)));

create table public.agent_plan_features (
  agent_id   uuid not null references auth.users (id) on delete cascade,
  plan_id    uuid not null references public.company_plans (id) on delete cascade,
  features   text[] not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (agent_id, plan_id),
  constraint agent_plan_features_shape check (coalesce(array_ndims(features), 1) = 1 and cardinality(features) between 1 and 5)
);
create index agent_plan_features_plan_idx on public.agent_plan_features (plan_id);
create index agent_plan_features_agent_updated_idx on public.agent_plan_features (agent_id, updated_at);

create table public.agent_featured_plans (
  agent_id   uuid primary key references auth.users (id) on delete cascade,
  plan_ids   uuid[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint agent_featured_plans_shape check (coalesce(array_ndims(plan_ids), 1) = 1 and cardinality(plan_ids) <= 6)
);
create index agent_featured_plans_agent_updated_idx on public.agent_featured_plans (agent_id, updated_at);

alter table public.offers add column plan_id uuid references public.company_plans (id) on delete restrict;
create index offers_plan_idx on public.offers (plan_id) where plan_id is not null;

create trigger set_updated_at before insert or update on public.agent_plan_features
  for each row execute function private.set_updated_at();
create trigger set_updated_at before insert or update on public.agent_featured_plans
  for each row execute function private.set_updated_at();

-- ===========================================================================
-- Slugs
-- ===========================================================================

-- "LIC's Jeevan Labh Plan" -> "lics-jeevan-labh-plan" (letters and digits, single hyphens, at most 72 characters).
create or replace function private.plan_slug_base(p_name text)
returns text
language sql
immutable
set search_path = ''
as $$
  select coalesce(nullif(left(btrim(regexp_replace(regexp_replace(lower(coalesce(p_name, '')), '[''’`]', '', 'g'),
                                                   '[^a-z0-9]+', '-', 'g'), '-'), 72), ''), 'plan')
$$;

-- A slug no other plan has: the base, else base-2, base-3, …
create or replace function private.unique_plan_slug(p_name text, p_id uuid)
returns text
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_base text := btrim(private.plan_slug_base(p_name), '-');
  v_slug text := v_base;
  v_n    integer := 1;
begin
  while exists (select 1 from public.company_plans p where p.slug = v_slug and p.id is distinct from p_id) loop
    v_n := v_n + 1;
    v_slug := v_base || '-' || v_n;
  end loop;
  return v_slug;
end;
$$;

-- One row at a time, so each slug sees the ones set before it (two names can share a base, e.g. "… Pro" and "… Pro+").
do $$
declare
  r record;
begin
  for r in select p.id, p.name from public.company_plans p where p.slug is null order by p.company_id, p.sort, lower(p.name), p.id loop
    update public.company_plans set slug = private.unique_plan_slug(r.name, r.id) where id = r.id;
  end loop;
end;
$$;
alter table public.company_plans alter column slug set not null;
create unique index company_plans_slug_key on public.company_plans (slug);

-- ===========================================================================
-- Guards
-- ===========================================================================

-- As 20261031090000_company_plans.sql, plus: the slug (set once from the name; kept when the name changes), the key
-- features (trimmed, blank points dropped, 3–400 characters each) and every document link on the company's website.
create or replace function private.company_plans_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_site text;
  v_host text;
  v_url  text;
  v_f    text[];
begin
  new.name := btrim(regexp_replace(new.name, '[[:space:]]+', ' ', 'g'));
  new.plan_no := nullif(btrim(new.plan_no), '');
  new.uin := nullif(upper(btrim(new.uin)), '');
  new.image_url := nullif(btrim(new.image_url), '');
  new.features_url := nullif(btrim(new.features_url), '');
  new.brochure_url := nullif(btrim(new.brochure_url), '');
  new.policy_url := nullif(btrim(new.policy_url), '');
  new.cis_url := nullif(btrim(new.cis_url), '');
  new.calculator_url := nullif(btrim(new.calculator_url), '');
  if char_length(new.name) < 2 or char_length(new.name) > 120 then
    raise exception 'invalid_name' using errcode = '22023';
  end if;
  if new.interest = 'other' or not new.interest = any (coalesce(public.catalog_interest_keys_all(), '{}'::text[])) then
    raise exception 'invalid_interest' using errcode = '22023';
  end if;
  if not new.insurance_type = any (coalesce(public.catalog_insurance_types(), '{}'::text[])) then
    raise exception 'invalid_insurance_type' using errcode = '22023';
  end if;
  if tg_op = 'UPDATE' and new.company_id is distinct from old.company_id then
    raise exception 'invalid_company' using errcode = '22023';
  end if;
  new.slug := nullif(btrim(lower(new.slug)), '');
  if new.slug is null then
    new.slug := case when tg_op = 'UPDATE' and old.slug is not null then old.slug else private.unique_plan_slug(new.name, new.id) end;
  elsif new.slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$' or char_length(new.slug) > 80 then
    raise exception 'invalid_slug' using errcode = '22023';
  end if;
  select coalesce(array_agg(btrim(regexp_replace(f, '[[:space:]]+', ' ', 'g')) order by o), '{}'::text[]) into v_f
    from unnest(coalesce(new.key_features, '{}'::text[])) with ordinality u(f, o)
   where nullif(btrim(f), '') is not null;
  if cardinality(v_f) > 5 or exists (select 1 from unnest(v_f) f where char_length(f) < 3 or char_length(f) > 400) then
    raise exception 'invalid_features' using errcode = '22023';
  end if;
  new.key_features := v_f;
  select private.url_host(c.website_url) into v_site from public.companies c where c.id = new.company_id;
  if v_site is null then
    raise exception 'company_website_missing' using errcode = '22023';
  end if;
  foreach v_url in array array[new.url, new.features_url, new.brochure_url, new.policy_url, new.cis_url, new.calculator_url] loop
    continue when v_url is null;
    v_host := private.url_host(v_url);
    if v_host is null then
      raise exception 'invalid_url' using errcode = '22023';
    end if;
    if not (v_host = v_site or right(v_host, char_length(v_site) + 1) = '.' || v_site) then
      raise exception 'url_not_company_site' using errcode = '22023';
    end if;
  end loop;
  if new.image_url is not null and position('/storage/v1/object/public/company-media/plans/' || new.id::text || '/' in new.image_url) = 0 then
    raise exception 'invalid_image_url' using errcode = '22023';
  end if;
  return new;
end;
$$;

-- An offer's plan must be the same company's; the offer's plan type follows the plan.
create or replace function private.offers_plan_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_plan public.company_plans;
begin
  if new.plan_id is null then
    return new;
  end if;
  select p.* into v_plan from public.company_plans p where p.id = new.plan_id;
  if v_plan.id is null or v_plan.company_id <> new.company_id then
    raise exception 'plan_wrong_company' using errcode = '22023';
  end if;
  new.interest := v_plan.interest;
  return new;
end;
$$;

create trigger offers_plan_guard before insert or update of plan_id, company_id, interest on public.offers
  for each row execute function private.offers_plan_guard();

-- ===========================================================================
-- RLS and grants
-- ===========================================================================
alter table public.agent_plan_features enable row level security;
alter table public.agent_featured_plans enable row level security;
revoke all on public.agent_plan_features, public.agent_featured_plans from anon, authenticated;
grant all on public.agent_plan_features, public.agent_featured_plans to service_role;
grant select on public.agent_plan_features, public.agent_featured_plans to authenticated;
create policy agent_plan_features_select_own on public.agent_plan_features
  for select to authenticated using (agent_id = (select auth.uid()));
create policy agent_featured_plans_select_own on public.agent_featured_plans
  for select to authenticated using (agent_id = (select auth.uid()));

grant insert (slug, key_features, features_url, features_checked_on, brochure_url, policy_url, cis_url, calculator_url)
  on public.company_plans to authenticated;
grant update (slug, key_features, features_url, features_checked_on, brochure_url, policy_url, cis_url, calculator_url)
  on public.company_plans to authenticated;
grant insert (plan_id), update (plan_id) on public.offers to authenticated;

-- ===========================================================================
-- Payload
-- ===========================================================================

-- One plan as a site sees it: facts, the insurer's links and the insurer's key features.
create or replace function private.plan_json(p public.company_plans, p_company jsonb, p_locale text, p_default text)
returns jsonb
language sql
stable
set search_path = ''
as $$
  select jsonb_build_object(
    'id', p.id,
    'company', coalesce(nullif(btrim(p_company ->> p_locale), ''), p_company ->> p_default),
    'name', p.name,
    'slug', p.slug,
    'plan_no', p.plan_no,
    'uin', p.uin,
    'interest', p.interest,
    'insurance_type', p.insurance_type,
    'url', p.url,
    'image_url', p.image_url,
    'features', to_jsonb(p.key_features),
    'features_url', coalesce(p.features_url, p.url),
    'features_checked_on', coalesce(p.features_checked_on, p.checked_on),
    'docs', jsonb_strip_nulls(jsonb_build_object('brochure', p.brochure_url, 'policy', p.policy_url,
                                                 'cis', p.cis_url, 'calculator', p.calculator_url)))
$$;

-- The site's plans as 20261031090000_company_plans.sql (chosen, else automatic; at most 100), each with the agent's
-- own wording when they have one (features_own) and its Home position (featured 1–6: the agent's featured plans
-- that the site shows, in their order; with no choice, the first 6 plans).
create or replace function private.site_plans_payload(p_agent uuid, p_locale text, p_default text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_choice uuid[];
  v_has    boolean;
  v_feat   uuid[];
  v_fhas   boolean;
  v_out    jsonb;
begin
  select c.plan_ids, true into v_choice, v_has from public.agent_plan_choices c where c.agent_id = p_agent;
  select f.plan_ids, true into v_feat, v_fhas from public.agent_featured_plans f where f.agent_id = p_agent;
  with shown as (
    select a.plan, a.company_name,
           row_number() over (order by case when coalesce(v_has, false) then array_position(v_choice, (a.plan).id) end,
                                       a.cpos, (a.plan).sort, lower((a.plan).name)) as pos
      from private.agent_available_plans(p_agent) a
     where not coalesce(v_has, false) or (a.plan).id = any (v_choice)
  ), capped as (
    select s.*, case when coalesce(v_fhas, false)
                     then (select count(*) from unnest(v_feat) with ordinality x(id, o)
                            where x.o <= array_position(v_feat, (s.plan).id)
                              and exists (select 1 from shown t where (t.plan).id = x.id))
                end as fpos
      from shown s
     where s.pos <= 100
  )
  select coalesce(jsonb_agg(private.plan_json(c.plan, c.company_name, p_locale, p_default)
                            || jsonb_build_object(
                                 'features', coalesce(to_jsonb(f.features), to_jsonb((c.plan).key_features)),
                                 'features_own', f.features is not null,
                                 'featured', case when coalesce(v_fhas, false)
                                                  then case when (c.plan).id = any (v_feat) then c.fpos end
                                                  when c.pos <= 6 then c.pos end)
                            order by c.pos), '[]'::jsonb)
    into v_out
    from capped c
    left join public.agent_plan_features f on f.agent_id = p_agent and f.plan_id = (c.plan).id;
  return v_out;
end;
$$;

-- An offer the site can show: a company offer on a plan only while the site shows that plan; any other offer
-- (company-wide, or the agent's own) as before.
create or replace function private.offer_plan_shown(p_offer jsonb, p_shown uuid[])
returns boolean
language sql
immutable
set search_path = ''
as $$
  select p_offer ->> 'plan_id' is null or (p_offer ->> 'plan_id')::uuid = any (coalesce(p_shown, '{}'::uuid[]))
$$;

-- As 20261022090000_multi_company.sql, plus `plan_id`.
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
      'plan_id', v_offer.plan_id,
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

-- As 20261031090000_company_plans.sql, plus: the plans first, and an offer on a plan only while the site shows it.
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
  v_show_code boolean := v_content #> '{about,showAgencyCode}' = 'true'::jsonb;
  v_feat_set  boolean;
  v_companies uuid[];
  v_list      jsonb;
  v_custom_ok boolean;
  v_first     public.companies;
  v_items     jsonb;
  v_featured  jsonb;
  v_auto      boolean;
  v_plans     jsonb;
  v_shown     uuid[];
begin
  v_plans := case when p_site.agent_id is null then '[]'::jsonb
                  else private.site_plans_payload(p_site.agent_id, v_locale, v_default) end;
  select coalesce(array_agg((e ->> 'id')::uuid), '{}'::uuid[]) into v_shown from jsonb_array_elements(v_plans) e;
  select coalesce(array_agg(c.id order by m.reviewed_at, m.requested_at, m.id), '{}'::uuid[]),
         coalesce(jsonb_agg(jsonb_build_object(
                    'name', coalesce(nullif(btrim(c.name ->> v_locale), ''), c.name ->> v_default),
                    'insurance_types', to_jsonb(private.ordered_insurance_types(m.insurance_types)))
                    || case when c.website_url is not null then jsonb_build_object('website', c.website_url) else '{}'::jsonb end
                    || case when v_show_code and nullif(btrim(m.agency_code), '') is not null
                            then jsonb_build_object('agency_code', btrim(m.agency_code)) else '{}'::jsonb end
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
                     where x.o is not null and private.offer_plan_shown(x.o, v_shown)) y) a
     where a.rn <= 3;
  else
    select coalesce(jsonb_agg(x.o order by x.idx), '[]'::jsonb) into v_items
      from (select private.resolve_offer_ref(e.value, v_custom, v_companies, v_custom_ok, v_locale, v_default, false) as o, e.ordinality as idx
              from jsonb_array_elements(case when jsonb_typeof(v_section -> 'items') = 'array'
                                             then v_section -> 'items' else '[]'::jsonb end) with ordinality e) x
     where x.o is not null and private.offer_plan_shown(x.o, v_shown);
  end if;

  if v_feat_set then
    v_featured := private.resolve_offer_ref(v_feat_ref, v_custom, v_companies, v_custom_ok, v_locale, v_default, true);
    if v_featured is not null and not private.offer_plan_shown(v_featured, v_shown) then
      v_featured := null;
    end if;
  elsif v_auto and public.catalog_template_family(p_site.template_key) = 'offers' and jsonb_array_length(v_items) > 0 then
    v_featured := (v_items -> 0) || jsonb_build_object('image_url', null);
    v_items := v_items - 0;
  end if;

  return jsonb_build_object(
    'companies', v_list,
    'company', case when v_first.id is null then null else jsonb_build_object(
      'disclaimer', coalesce(nullif(v_first.disclaimer ->> v_locale, ''), nullif(v_first.disclaimer ->> v_default, ''))) end,
    'offers', jsonb_build_object('featured', v_featured, 'items', v_items),
    'plans', v_plans);
end;
$$;
-- ===========================================================================
-- Agent RPCs
-- ===========================================================================

-- As 20261031090000_company_plans.sql, plus each plan's own_features (the agent's wording, or null) and the
-- featured choice: {choice, selected, featured: "auto" | "custom", featured_ids: [plan_id], plans: [...]}.
create or replace function public.my_company_plans()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid     uuid := auth.uid();
  v_default text := coalesce(public.catalog_default_locale(), 'en');
  v_locale  text;
  v_choice  uuid[];
  v_has     boolean;
  v_feat    uuid[];
  v_fhas    boolean;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  select s.locale into v_locale from public.sites s where s.agent_id = v_uid and s.deleted_at is null;
  v_locale := case when v_locale = any (coalesce(public.catalog_locales(), '{}'::text[])) then v_locale else v_default end;
  select c.plan_ids, true into v_choice, v_has from public.agent_plan_choices c where c.agent_id = v_uid;
  select f.plan_ids, true into v_feat, v_fhas from public.agent_featured_plans f where f.agent_id = v_uid;
  return jsonb_build_object(
    'choice', case when coalesce(v_has, false) then 'custom' else 'auto' end,
    'selected', case when coalesce(v_has, false)
                     then coalesce((select jsonb_agg(x.id order by x.ord)
                                      from unnest(v_choice) with ordinality x(id, ord)
                                     where exists (select 1 from private.agent_available_plans(v_uid) a where (a.plan).id = x.id)),
                                   '[]'::jsonb)
                     else '[]'::jsonb end,
    'featured', case when coalesce(v_fhas, false) then 'custom' else 'auto' end,
    'featured_ids', case when coalesce(v_fhas, false)
                         then coalesce((select jsonb_agg(x.id order by x.ord)
                                          from unnest(v_feat) with ordinality x(id, ord)
                                         where exists (select 1 from private.agent_available_plans(v_uid) a where (a.plan).id = x.id)),
                                       '[]'::jsonb)
                         else '[]'::jsonb end,
    'plans', coalesce((select jsonb_agg(private.plan_json(a.plan, a.company_name, v_locale, v_default)
                                        || jsonb_build_object('company_id', (a.plan).company_id, 'own_features', to_jsonb(f.features))
                                        order by a.cpos, (a.plan).sort, lower((a.plan).name))
                         from private.agent_available_plans(v_uid) a
                         left join public.agent_plan_features f on f.agent_id = v_uid and f.plan_id = (a.plan).id), '[]'::jsonb));
end;
$$;

-- The agent's own wording of a plan's key features on their site. features null (or no point left after trimming)
-- = back to the insurer's wording (the row is removed). 1–5 points of 3–400 characters; no product or insurer name
-- (the public text check, DECISIONS #77): blocked_term:<point>:<term>. Returns my_company_plans().
create or replace function public.set_plan_features(plan_id uuid, features text[])
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_f   text[];
  v_i   integer;
  v_hit text;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if not public.can_write() then
    raise exception 'plan_read_only' using errcode = '42501';
  end if;
  if set_plan_features.plan_id is null
     or not exists (select 1 from private.agent_available_plans(v_uid) a where (a.plan).id = set_plan_features.plan_id) then
    raise exception 'unknown_plan:%', set_plan_features.plan_id using errcode = '22023';
  end if;
  if coalesce(array_ndims(set_plan_features.features), 1) <> 1 then
    raise exception 'invalid_features' using errcode = '22023';
  end if;
  select coalesce(array_agg(btrim(regexp_replace(f, '[[:space:]]+', ' ', 'g')) order by o), '{}'::text[]) into v_f
    from unnest(coalesce(set_plan_features.features, '{}'::text[])) with ordinality u(f, o)
   where nullif(btrim(f), '') is not null;
  if cardinality(v_f) = 0 then
    delete from public.agent_plan_features x where x.agent_id = v_uid and x.plan_id = set_plan_features.plan_id;
    return public.my_company_plans();
  end if;
  if cardinality(v_f) > 5 or exists (select 1 from unnest(v_f) f where char_length(f) < 3 or char_length(f) > 400) then
    raise exception 'invalid_features' using errcode = '22023';
  end if;
  for v_i in 1 .. cardinality(v_f) loop
    v_hit := private.public_text_violation(v_f[v_i]);
    if v_hit is not null then
      raise exception 'blocked_term:%:%', v_i, v_hit using errcode = '22023';
    end if;
  end loop;
  insert into public.agent_plan_features (agent_id, plan_id, features) values (v_uid, set_plan_features.plan_id, v_f)
  on conflict on constraint agent_plan_features_pkey do update set features = excluded.features;
  return public.my_company_plans();
end;
$$;

-- The plans the agent features on Home: null = automatic again (the first 6 plans the site shows); an empty array =
-- none; otherwise up to 6 distinct plans the agent may show, in this order (a plan the site stops showing is simply
-- not featured). Returns my_company_plans().
create or replace function public.set_featured_plans(plan_ids uuid[])
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_ids uuid[];
  v_bad uuid;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if not public.can_write() then
    raise exception 'plan_read_only' using errcode = '42501';
  end if;
  if set_featured_plans.plan_ids is null then
    delete from public.agent_featured_plans f where f.agent_id = v_uid;
    return public.my_company_plans();
  end if;
  if coalesce(array_ndims(set_featured_plans.plan_ids), 1) <> 1 or array_position(set_featured_plans.plan_ids, null) is not null then
    raise exception 'invalid_plans' using errcode = '22023';
  end if;
  select coalesce(array_agg(x.id order by x.ord), '{}'::uuid[]) into v_ids
    from (select distinct on (u.id) u.id, u.ord from unnest(set_featured_plans.plan_ids) with ordinality u(id, ord)
           order by u.id, u.ord) x;
  if cardinality(v_ids) > 6 then
    raise exception 'too_many_featured' using errcode = '22023';
  end if;
  select x into v_bad from unnest(v_ids) x
   where not exists (select 1 from private.agent_available_plans(v_uid) a where (a.plan).id = x)
   limit 1;
  if v_bad is not null then
    raise exception 'unknown_plan:%', v_bad using errcode = '22023';
  end if;
  insert into public.agent_featured_plans (agent_id, plan_ids) values (v_uid, v_ids)
  on conflict (agent_id) do update set plan_ids = excluded.plan_ids;
  return public.my_company_plans();
end;
$$;

-- ===========================================================================
-- Function privileges
-- ===========================================================================
revoke all on function private.plan_slug_base(text) from public, anon, authenticated;
revoke all on function private.unique_plan_slug(text, uuid) from public, anon, authenticated;
revoke all on function private.offers_plan_guard() from public, anon, authenticated;
revoke all on function private.offer_plan_shown(jsonb, uuid[]) from public, anon, authenticated;
revoke all on function private.company_plans_guard() from public, anon, authenticated;
revoke all on function private.plan_json(public.company_plans, jsonb, text, text) from public, anon, authenticated;
revoke all on function private.site_plans_payload(uuid, text, text) from public, anon, authenticated;
revoke all on function private.site_offers_payload(public.sites) from public, anon, authenticated;
revoke all on function private.resolve_offer_ref(jsonb, jsonb, uuid[], boolean, text, text, boolean) from public, anon, authenticated;
revoke all on function public.my_company_plans() from public, anon, authenticated;
revoke all on function public.set_plan_features(uuid, text[]) from public, anon, authenticated;
revoke all on function public.set_featured_plans(uuid[]) from public, anon, authenticated;
grant execute on function public.my_company_plans() to authenticated;
grant execute on function public.set_plan_features(uuid, text[]) to authenticated;
grant execute on function public.set_featured_plans(uuid[]) to authenticated;
