-- Agent PRO — official plans per insurer, chosen by the agent for their website (DECISIONS #129, docs/OFFERS.md "Plans").
--
--   * public.company_plans: the insurer's own plans as published on its website (official name, LIC plan number,
--     UIN, plan type, the insurer's page, an optional admin-uploaded picture). Admins manage them per company,
--     like offers (admin-only RLS with MFA, actor stamps, audit). Never hard-deleted: is_active = false hides one.
--     No benefits, prices or descriptions are stored: a site shows only facts and links to the insurer's page.
--   * private.company_plans_guard: plan type and insurance type from the catalog (and consistent with each other),
--     https links on the company's own website (the company needs website_url), images in company-media/plans/<id>/.
--   * public.agent_plan_choices: the agent's choice of plans for their site (owner-only select; written only by
--     set_site_plans). No row = automatic (every plan of the agent's approved companies for the insurance types
--     they deal in there); an empty list = no plans on the site; otherwise the chosen plans, in the chosen order.
--   * Agent RPCs: my_company_plans() (what they can choose, and what they chose) and set_site_plans(plan_ids).
--   * The payload (get_public_site*, preview_site_offers) gains `plans` (resolved on every read, so an admin edit,
--     a deactivated plan or a revoked company link reaches every site at once), and each `companies` entry gains
--     `website` (the insurer's official site, for the disclosure footer) and, only when the agent shows their
--     agency code (about.showAgencyCode), `agency_code` for that company.
--   * company-media: admins of the plan's company may upload plans/<plan_id>/<file>.
--   * audit_row knows company_plans (plan.create / plan.update) and agent_plan_choices is not audited (agent content).
--
-- Errors: 22023 invalid_name / invalid_plan_no / invalid_uin / invalid_interest / invalid_insurance_type /
-- invalid_url / url_not_company_site / company_website_missing / invalid_image_url / too_many_plans /
-- unknown_plan:<id>; 42501 not_authenticated / plan_read_only.
-- Expected advisors: the accepted 0029 WARNs gain my_company_plans and set_site_plans (authenticated, SECURITY DEFINER).

-- ===========================================================================
-- Tables
-- ===========================================================================
create table public.company_plans (
  id             uuid primary key default gen_random_uuid(),
  company_id     uuid not null references public.companies (id) on delete restrict,
  name           text not null,
  plan_no        text,
  uin            text,
  interest       text not null,
  insurance_type text not null,
  url            text not null,
  image_url      text,
  is_active      boolean not null default true,
  sort           integer not null default 0,
  checked_on     date not null default current_date,
  created_by     uuid,
  updated_by     uuid,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  constraint company_plans_name_length check (char_length(btrim(name)) between 2 and 120),
  constraint company_plans_plan_no_format check (plan_no is null or plan_no ~ '^[A-Za-z0-9-]{1,12}$'),
  constraint company_plans_uin_format check (uin is null or uin ~ '^[A-Z0-9]{6,24}$'),
  constraint company_plans_url_https check (url ~* '^https://[^[:space:]]+$' and char_length(url) <= 500),
  constraint company_plans_image_url_https check (image_url is null or image_url ~* '^https://[^[:space:]]+$')
);
create index company_plans_company_idx on public.company_plans (company_id, is_active, insurance_type, sort);
create unique index company_plans_company_name_key on public.company_plans (company_id, lower(btrim(name)));

create table public.agent_plan_choices (
  agent_id   uuid primary key references auth.users (id) on delete cascade,
  plan_ids   uuid[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint agent_plan_choices_shape check (coalesce(array_ndims(plan_ids), 1) = 1 and cardinality(plan_ids) <= 100)
);

create index agent_plan_choices_agent_updated_idx on public.agent_plan_choices (agent_id, updated_at);

create trigger set_updated_at before insert or update on public.company_plans
  for each row execute function private.set_updated_at();
create trigger set_updated_at before insert or update on public.agent_plan_choices
  for each row execute function private.set_updated_at();
create trigger stamp_actor before insert or update on public.company_plans
  for each row execute function private.stamp_actor();

-- ===========================================================================
-- Guard
-- ===========================================================================

-- The host of an https URL, lower-cased, without a leading "www.", or null.
create or replace function private.url_host(p_url text)
returns text
language sql
immutable
set search_path = ''
as $$
  select nullif(regexp_replace(lower(substring(p_url from '^[hH][tT][tT][pP][sS]://([^/:?#]+)')), '^www\.', ''), '')
$$;

-- The plan type (catalog interest, not "other") and its insurance type come from the catalog; the admin panel
-- derives insurance_type from the interest (catalog.interests[].insuranceType), and the seed script checks the pair.
-- The link must be on the company's own website (its host or a sub-domain), so a site never links elsewhere.
create or replace function private.company_plans_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_site text;
  v_host text;
begin
  new.name := btrim(regexp_replace(new.name, '[[:space:]]+', ' ', 'g'));
  new.plan_no := nullif(btrim(new.plan_no), '');
  new.uin := nullif(upper(btrim(new.uin)), '');
  new.image_url := nullif(btrim(new.image_url), '');
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
  select private.url_host(c.website_url) into v_site from public.companies c where c.id = new.company_id;
  if v_site is null then
    raise exception 'company_website_missing' using errcode = '22023';
  end if;
  v_host := private.url_host(new.url);
  if v_host is null then
    raise exception 'invalid_url' using errcode = '22023';
  end if;
  if not (v_host = v_site or right(v_host, char_length(v_site) + 1) = '.' || v_site) then
    raise exception 'url_not_company_site' using errcode = '22023';
  end if;
  if new.image_url is not null and position('/storage/v1/object/public/company-media/plans/' || new.id::text || '/' in new.image_url) = 0 then
    raise exception 'invalid_image_url' using errcode = '22023';
  end if;
  return new;
end;
$$;

create trigger company_plans_guard before insert or update on public.company_plans
  for each row execute function private.company_plans_guard();

-- ===========================================================================
-- Audit: as 20261022183528_admin_function.sql, plus company_plans
-- ===========================================================================
create or replace function private.audit_row()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_new     jsonb := to_jsonb(new);
  v_old     jsonb := case when tg_op = 'UPDATE' then to_jsonb(old) else '{}'::jsonb end;
  v_skip    text[] := array['created_at', 'updated_at', 'created_by', 'updated_by'];
  v_changes jsonb;
  v_uid     uuid := auth.uid();
  v_kind    text;
  v_action  text;
  v_ip      inet;
  v_status  text := v_new ->> 'status';
  v_was     text := v_old ->> 'status';
begin
  if tg_table_name = 'admins' and current_setting('agentpro.audit_explicit', true) = 'on' then
    return null;
  end if;
  if tg_table_name = 'company_memberships' then
    v_skip := v_skip || array['agency_code', 'suggested_name', 'review_note'];
  end if;
  select jsonb_object_agg(e.key, e.value) into v_changes
    from jsonb_each(v_new) e
   where not e.key = any (v_skip)
     and (tg_op = 'INSERT' or e.value is distinct from v_old -> e.key);
  if tg_op = 'UPDATE' and v_changes is null then
    return null;
  end if;

  v_action := case tg_table_name
    when 'companies' then case when tg_op = 'INSERT' then 'company.create' else 'company.update' end
    when 'company_plans' then case when tg_op = 'INSERT' then 'plan.create' else 'plan.update' end
    when 'offers' then case
      when tg_op = 'INSERT' then 'offer.create'
      when v_status = 'live' and v_was = 'draft' then 'offer.go_live'
      when v_status = 'archived' and v_was = 'live' then 'offer.archive'
      when v_status = 'draft' and v_was = 'archived' then 'offer.restore'
      else 'offer.update' end
    when 'admins' then case
      when tg_op = 'INSERT' then 'admin.create'
      when v_new ->> 'revoked_at' is not null and v_old ->> 'revoked_at' is null then 'admin.revoke'
      else 'admin.update' end
    when 'company_memberships' then case
      when tg_op = 'INSERT' then 'membership.request'
      when v_status = 'approved' and v_was <> 'approved' then 'membership.approve'
      when v_status = 'rejected' and v_was <> 'rejected' then 'membership.reject'
      when v_status = 'revoked' and v_was <> 'revoked' then 'membership.revoke'
      when v_status = 'withdrawn' and v_was <> 'withdrawn' then 'membership.withdraw'
      when v_new -> 'company_id' is distinct from v_old -> 'company_id' then 'membership.link'
      else 'membership.update' end
  end;

  v_kind := case
    when v_uid is null then 'system'
    when exists (select 1 from public.admins a where a.user_id = v_uid and a.revoked_at is null) then 'admin'
    else 'agent'
  end;
  if v_kind = 'admin' then
    begin
      v_ip := nullif(btrim(split_part(current_setting('request.headers', true)::jsonb ->> 'x-forwarded-for', ',', 1)), '')::inet;
    exception when others then
      v_ip := null;
    end;
  end if;

  insert into public.audit_log (actor_id, actor_kind, action, entity, entity_id, changes, request_ip)
  values (v_uid, v_kind, v_action, tg_table_name,
          coalesce(v_new ->> 'id', v_new ->> 'user_id')::uuid, v_changes, v_ip);
  return null;
end;
$$;

create trigger audit_row after insert or update on public.company_plans
  for each row execute function private.audit_row();

-- ===========================================================================
-- RLS and grants
-- ===========================================================================
alter table public.company_plans enable row level security;
alter table public.agent_plan_choices enable row level security;

revoke all on public.company_plans, public.agent_plan_choices from anon, authenticated;
grant all on public.company_plans, public.agent_plan_choices to service_role;

-- company_plans: admins of the plan's company. The company can't change; stamps are trigger-only. No delete.
grant select on public.company_plans to authenticated;
grant insert (id, company_id, name, plan_no, uin, interest, insurance_type, url, image_url, is_active, sort, checked_on)
  on public.company_plans to authenticated;
grant update (name, plan_no, uin, interest, insurance_type, url, image_url, is_active, sort, checked_on)
  on public.company_plans to authenticated;
create policy company_plans_select_admin on public.company_plans
  for select to authenticated
  using (public.is_admin_for(company_id));
create policy company_plans_insert_admin on public.company_plans
  for insert to authenticated
  with check (public.is_admin_for(company_id));
create policy company_plans_update_admin on public.company_plans
  for update to authenticated
  using (public.is_admin_for(company_id))
  with check (public.is_admin_for(company_id));

-- agent_plan_choices: the agent reads their own row; writes only through set_site_plans().
grant select on public.agent_plan_choices to authenticated;
create policy agent_plan_choices_select_own on public.agent_plan_choices
  for select to authenticated
  using (agent_id = (select auth.uid()));

-- ===========================================================================
-- Storage: company-media/plans/<plan_id>/<file> for admins of the plan's company
-- ===========================================================================
create or replace function public.can_manage_company_media(p_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_parts text[] := string_to_array(coalesce(p_name, ''), '/');
  v_id    uuid;
begin
  if cardinality(v_parts) <> 3 or v_parts[3] = ''
     or v_parts[2] !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
    return false;
  end if;
  v_id := v_parts[2]::uuid;
  if v_parts[1] = 'logos' then
    return exists (select 1 from public.companies c where c.id = v_id) and public.is_admin_for(v_id);
  elsif v_parts[1] = 'offers' then
    return coalesce((select public.is_admin_for(o.company_id) from public.offers o where o.id = v_id), false);
  elsif v_parts[1] = 'plans' then
    return coalesce((select public.is_admin_for(p.company_id) from public.company_plans p where p.id = v_id), false);
  end if;
  return false;
end;
$$;

-- ===========================================================================
-- Plans an agent can show, and the payload
-- ===========================================================================

-- Every active plan of the agent's approved, active companies whose insurance type the agent deals in there, in
-- approval order, then the plan's sort and name. One row per plan (a plan belongs to one company).
create or replace function private.agent_available_plans(p_agent uuid)
returns table (plan public.company_plans, company_name jsonb, cpos bigint)
language sql
stable
security definer
set search_path = ''
as $$
  select p, c.name, m.pos
    from (select m.company_id, m.insurance_types,
                 row_number() over (order by m.reviewed_at, m.requested_at, m.id) as pos
            from public.company_memberships m
           where m.agent_id = p_agent and m.status = 'approved') m
    join public.companies c on c.id = m.company_id and c.is_active
    join public.company_plans p on p.company_id = m.company_id and p.is_active
                                and p.insurance_type = any (m.insurance_types)
$$;

-- One plan as a site sees it: facts and the insurer's link only.
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
    'plan_no', p.plan_no,
    'uin', p.uin,
    'interest', p.interest,
    'insurance_type', p.insurance_type,
    'url', p.url,
    'image_url', p.image_url)
$$;

-- The site's plans: the agent's chosen plans that are still available, in their order; with no choice made, every
-- available plan (automatic). At most 100.
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
  v_out    jsonb;
begin
  select c.plan_ids, true into v_choice, v_has from public.agent_plan_choices c where c.agent_id = p_agent;
  if coalesce(v_has, false) then
    select coalesce(jsonb_agg(private.plan_json(a.plan, a.company_name, p_locale, p_default)
                              order by array_position(v_choice, (a.plan).id)), '[]'::jsonb)
      into v_out
      from private.agent_available_plans(p_agent) a
     where (a.plan).id = any (v_choice);
  else
    select coalesce(jsonb_agg(x.j order by x.cpos, x.sort, x.name), '[]'::jsonb) into v_out
      from (select private.plan_json(a.plan, a.company_name, p_locale, p_default) as j,
                   a.cpos, (a.plan).sort, lower((a.plan).name) as name
              from private.agent_available_plans(p_agent) a
             order by a.cpos, (a.plan).sort, lower((a.plan).name)
             limit 100) x;
  end if;
  return v_out;
end;
$$;

-- As 20261022090000_multi_company.sql, plus: companies[].website and (when the agent shows their agency code)
-- companies[].agency_code; and `plans` (above).
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
begin
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
    'offers', jsonb_build_object('featured', v_featured, 'items', v_items),
    'plans', case when p_site.agent_id is null then '[]'::jsonb
                  else private.site_plans_payload(p_site.agent_id, v_locale, v_default) end);
end;
$$;

-- ===========================================================================
-- Agent RPCs
-- ===========================================================================

-- {choice: "auto" | "custom", selected: [plan_id], plans: [{id, company_id, company, name, plan_no, uin, interest,
--  insurance_type, url, image_url}]}: everything the agent may choose (approved companies x their insurance types),
-- in approval order, then sort and name. Company names in the agent's site language.
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
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  select s.locale into v_locale from public.sites s where s.agent_id = v_uid and s.deleted_at is null;
  v_locale := case when v_locale = any (coalesce(public.catalog_locales(), '{}'::text[])) then v_locale else v_default end;
  select c.plan_ids, true into v_choice, v_has from public.agent_plan_choices c where c.agent_id = v_uid;
  return jsonb_build_object(
    'choice', case when coalesce(v_has, false) then 'custom' else 'auto' end,
    'selected', case when coalesce(v_has, false)
                     then coalesce((select jsonb_agg(x.id order by x.ord)
                                      from unnest(v_choice) with ordinality x(id, ord)
                                     where exists (select 1 from private.agent_available_plans(v_uid) a where (a.plan).id = x.id)),
                                   '[]'::jsonb)
                     else '[]'::jsonb end,
    'plans', coalesce((select jsonb_agg(private.plan_json(a.plan, a.company_name, v_locale, v_default)
                                        || jsonb_build_object('company_id', (a.plan).company_id)
                                        order by a.cpos, (a.plan).sort, lower((a.plan).name))
                         from private.agent_available_plans(v_uid) a), '[]'::jsonb));
end;
$$;

-- Saves the agent's choice. plan_ids null = automatic again (the row is removed); an empty array = no plans on
-- the site; otherwise up to 100 distinct plans, each one the agent may choose. The live site follows at once
-- (plans resolve on every read, like company offers). Returns my_company_plans().
create or replace function public.set_site_plans(plan_ids uuid[])
returns jsonb
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
  v_uid  uuid := auth.uid();
  v_ids  uuid[];
  v_bad  uuid;
begin
  if v_uid is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;
  if not public.can_write() then
    raise exception 'plan_read_only' using errcode = '42501';
  end if;
  if set_site_plans.plan_ids is null then
    delete from public.agent_plan_choices c where c.agent_id = v_uid;
    return public.my_company_plans();
  end if;
  if coalesce(array_ndims(set_site_plans.plan_ids), 1) <> 1 or array_position(set_site_plans.plan_ids, null) is not null then
    raise exception 'invalid_plans' using errcode = '22023';
  end if;
  select coalesce(array_agg(x.id order by x.ord), '{}'::uuid[]) into v_ids
    from (select distinct on (u.id) u.id, u.ord from unnest(set_site_plans.plan_ids) with ordinality u(id, ord)
           order by u.id, u.ord) x;
  if cardinality(v_ids) > 100 then
    raise exception 'too_many_plans' using errcode = '22023';
  end if;
  select x into v_bad from unnest(v_ids) x
   where not exists (select 1 from private.agent_available_plans(v_uid) a where (a.plan).id = x)
   limit 1;
  if v_bad is not null then
    raise exception 'unknown_plan:%', v_bad using errcode = '22023';
  end if;
  insert into public.agent_plan_choices (agent_id, plan_ids) values (v_uid, v_ids)
  on conflict (agent_id) do update set plan_ids = excluded.plan_ids;
  return public.my_company_plans();
end;
$$;

-- ===========================================================================
-- Function privileges
-- ===========================================================================
revoke all on function private.url_host(text) from public, anon, authenticated;
revoke all on function private.company_plans_guard() from public, anon, authenticated;
revoke all on function private.agent_available_plans(uuid) from public, anon, authenticated;
revoke all on function private.plan_json(public.company_plans, jsonb, text, text) from public, anon, authenticated;
revoke all on function private.site_plans_payload(uuid, text, text) from public, anon, authenticated;
revoke all on function private.site_offers_payload(public.sites) from public, anon, authenticated;
revoke all on function public.can_manage_company_media(text) from public, anon, authenticated;
revoke all on function public.my_company_plans() from public, anon, authenticated;
revoke all on function public.set_site_plans(uuid[]) from public, anon, authenticated;
grant execute on function public.can_manage_company_media(text) to authenticated;
grant execute on function public.my_company_plans() to authenticated;
grant execute on function public.set_site_plans(uuid[]) to authenticated;
