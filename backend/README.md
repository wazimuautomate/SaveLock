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

# 3. Put your secrets on the server (copy backend/.env.example -> backend/.env and fill it in first).
#    NOTE: Supabase rejects secrets that start with SUPABASE_ (it injects those itself), so filter them:
grep -vE '^SUPABASE_' backend/.env > backend/.env.secrets
supabase secrets set --env-file backend/.env.secrets
rm backend/.env.secrets

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
| `DARAJA_SHORTCODE` | Head-office/store short code, used only to build the Lipa na M-Pesa password. |
| `TILL_NUMBER` | The Till (Buy Goods) number the money actually lands in (`PartyB`). |
| `DARAJA_ENV` | `sandbox` while testing, **`production` when live**. ⚠️ If this is `sandbox` (or unset), the STK push goes to Safaricom's sandbox and **never prompts a real phone/till** — the app just times out. For real payments this MUST be `production` with your production/Go-Live keys, passkey and shortcode. |
| `DARAJA_TX_TYPE` | `CustomerBuyGoodsOnline` (till, default) or `CustomerPayBillOnline` (paybill). |
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

---

## Offline / low-data payments (C2B — pay the till directly)

STK push needs the phone online. To also catch payments a customer makes **directly** to the till
from the M-Pesa menu (works on GSM with no mobile data), register the C2B webhooks **once**:

```bash
curl -X POST https://YOUR-PROJECT-REF.functions.supabase.co/c2b-register \
  -H "x-app-key: YOUR_APP_BACKEND_KEY"
```
A successful response contains `"ResponseDescription":"success"`. This points Safaricom at:
```
Confirmation: https://YOUR-PROJECT-REF.functions.supabase.co/c2b-confirmation
Validation:   https://YOUR-PROJECT-REF.functions.supabase.co/c2b-validation
```
After that, every direct till payment is stored in `till_payments`, and the app polls
`/till-payments` (while locked, when online) to reconcile and unlock. Requires production Go-Live
creds — sandbox shortcodes cannot register live C2B URLs. On-device, the app **also** reads the
M-Pesa confirmation SMS to unlock with no internet at all (Settings → Offline M-Pesa Unlock).

> Deploy note: deploy the four new functions too —
> `supabase functions deploy c2b-validation c2b-confirmation c2b-register till-payments`
> and run the new migration (`supabase db push`).
