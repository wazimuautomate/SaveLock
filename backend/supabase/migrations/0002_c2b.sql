-- SaveLock C2B (offline till payments): one row per completed payment Safaricom pushes to our
-- Confirmation URL when a customer pays the Till/Paybill directly (M-Pesa menu / SIM Toolkit),
-- i.e. WITHOUT the app initiating an STK push. The app polls these (via /till-payments) to unlock
-- when it comes back online, as a server-side backup to the on-device SMS auto-unlock.
--
-- Like stk_transactions, only the Edge Functions (service role) touch this; RLS is on with no
-- public policies, so the anon/public key can never read it.

create table if not exists public.till_payments (
    trans_id            text primary key,           -- M-Pesa receipt (unique) — the dedup key
    trans_time          text,                       -- raw Daraja TransTime (yyyyMMddHHmmss)
    amount              numeric,                     -- KES paid
    business_short_code text,                        -- the till/paybill it was paid to
    bill_ref_number     text,                        -- account ref (paybill) — usually blank for a till
    msisdn              text,                        -- payer phone (may be masked/hashed by Safaricom)
    first_name          text,
    raw                 jsonb,                       -- full payload, for auditing / future fields
    created_at          timestamptz not null default now()
);

create index if not exists till_payments_created_at_idx on public.till_payments (created_at);
create index if not exists till_payments_msisdn_idx on public.till_payments (msisdn);

alter table public.till_payments enable row level security;
-- No policies on purpose: service role bypasses RLS; everyone else is denied.
