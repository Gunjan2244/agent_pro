-- Daily retention run for unfinished customer forms (DECISIONS #108; docs/BACKEND.md "Customer sign-up link").
-- Live-project only (not a migration): the local test harness has no pg_cron. Applied to AGENT_PRO on 2026-10-25
-- with the user's approval. Re-running it is safe: cron.schedule with a job name replaces that job.
--
-- 23:00 IST every day. pg_cron runs on UTC (cron.timezone = GMT on Supabase), and IST is UTC+5:30, so 17:30 UTC.
-- private.purge_customer_drafts() erases drafts idle for retention.customerDraftsDays (30) and hard-deletes
-- completed, deleted and erased drafts 30 days after their last change; it writes a counts-only audit row.
create extension if not exists pg_cron with schema pg_catalog;
grant usage on schema cron to postgres;

select cron.schedule(
  'purge-customer-drafts',
  '30 17 * * *',
  $$select private.purge_customer_drafts()$$
);
