-- SaveLock backend schema: one row per STK push transaction.
-- Only the Edge Functions (service role) read/write this; RLS is on with no public policies,
-- so the anon/public key can never touch it.

create table if not exists public.stk_transactions (
    checkout_request_id text primary key,
    merchant_request_id text,
    phone               text,
    amount              integer,
    status              text not null default 'PENDING', -- PENDING | SUCCESS | FAILED
    result_code         integer,
    result_desc         text,
    mpesa_receipt       text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index if not exists stk_transactions_status_idx on public.stk_transactions (status);

alter table public.stk_transactions enable row level security;
-- No policies on purpose: service role bypasses RLS; everyone else is denied.
