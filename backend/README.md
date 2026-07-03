# SaveLock Backend (Supabase)

A tiny, secure proxy between the SaveLock Android app and Safaricom Daraja (M-Pesa).
**All Daraja secrets live here on the server — never in the app.**

Three Edge Functions + one Postgres table:

| Endpoint | Who calls it | What it does |
|---|---|---|
| `POST /stk-push` | the app | Triggers the M-Pesa PIN prompt; stores a `PENDING` row; returns `checkoutRequestId`. |
| `POST /stk-callback` | Safaricom | Receives the final result; updates the row to `SUCCESS`/`FAILED`. |
| `GET /stk-status/<id>` | the app | Returns the current status so the app can stop waiting. |

The app authenticates with a shared `x-app-key` header (so strangers can't trigger prompts). The
callback is unauthenticated by design (Safaricom can't send our key) but only updates a row keyed by
an ID we created.

---

## What you need first
1. A free **Supabase** account + a new project (note its **Project URL** and **service role key**).
2. The **Supabase CLI** installed: https://supabase.com/docs/guides/cli
3. Your **Daraja** app credentials (Consumer Key, Consumer Secret, Passkey, Shortcode).

---

## Deploy (one time)

```bash
# 1. Log in and link this folder to your project
supabase login
supabase link --project-ref YOUR-PROJECT-REF

# 2. Create the database table
supabase db push

# 3. Put your secrets on the server (copy backend/.env.example -> backend/.env and fill it in first)
supabase secrets set --env-file backend/.env

# 4. Deploy the three functions
supabase functions deploy stk-push
supabase functions deploy stk-callback
supabase functions deploy stk-status
```

Your function base URL will be:
```
https://YOUR-PROJECT-REF.functions.supabase.co
```

---

## The Daraja callback URL to register

In the **Daraja developer portal** (and in your `backend/.env` as `DARAJA_CALLBACK_URL`), set the
Lipa na M-Pesa Online callback URL to:

```
https://YOUR-PROJECT-REF.functions.supabase.co/stk-callback
```

---

## Environment variables (set via `supabase secrets set`)

See [`.env.example`](.env.example). Summary:

| Variable | Meaning |
|---|---|
| `DARAJA_CONSUMER_KEY` / `DARAJA_CONSUMER_SECRET` | Your Daraja app API credentials. |
| `DARAJA_PASSKEY` | Lipa na M-Pesa Online passkey. |
| `DARAJA_SHORTCODE` | Your paybill/business short code. |
| `DARAJA_ENV` | `sandbox` while testing, `production` when live. |
| `DARAJA_TX_TYPE` | `CustomerPayBillOnline` (paybill, default) or `CustomerBuyGoodsOnline` (till). |
| `DARAJA_CALLBACK_URL` | The `/stk-callback` URL above. |
| `APP_BACKEND_KEY` | Shared secret; must match `app/savelock.properties`. |
| `SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY` | Auto-provided to functions in production. |

---

## Connect the app

In `app/savelock.properties` (copy from `app/savelock.properties.example`) set:
```
SUPABASE_FUNCTIONS_URL=https://YOUR-PROJECT-REF.functions.supabase.co
APP_BACKEND_KEY=<the same value you put in backend/.env>
```
Rebuild the app (the GitHub Actions build picks these up).

---

## Quick test (sandbox)

```bash
curl -X POST https://YOUR-PROJECT-REF.functions.supabase.co/stk-push \
  -H "x-app-key: YOUR_APP_BACKEND_KEY" \
  -H "Content-Type: application/json" \
  -d '{"phone":"2547XXXXXXXX","amount":1}'
```
You should get back a `checkoutRequestId` and a prompt on the test phone. Then:
```bash
curl https://YOUR-PROJECT-REF.functions.supabase.co/stk-status/<checkoutRequestId> \
  -H "x-app-key: YOUR_APP_BACKEND_KEY"
```
