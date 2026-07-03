// Shared Safaricom Daraja (M-Pesa) helpers.
// SECURITY: every secret is read from environment variables only. Nothing here is ever shipped to
// the Android app.

function base(): string {
  return Deno.env.get("DARAJA_ENV") === "production"
    ? "https://api.safaricom.co.ke"
    : "https://sandbox.safaricom.co.ke";
}

/** OAuth token (client-credentials) used to authorize the STK push call. */
export async function getAccessToken(): Promise<string> {
  const key = Deno.env.get("DARAJA_CONSUMER_KEY")!;
  const secret = Deno.env.get("DARAJA_CONSUMER_SECRET")!;
  const auth = btoa(`${key}:${secret}`);
  const res = await fetch(
    `${base()}/oauth/v1/generate?grant_type=client_credentials`,
    { headers: { Authorization: `Basic ${auth}` } },
  );
  if (!res.ok) throw new Error(`Daraja token failed: ${res.status} ${await res.text()}`);
  const data = await res.json();
  return data.access_token as string;
}

/** yyyyMMddHHmmss timestamp Daraja expects. */
function timestamp(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}` +
    `${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

export interface StkPushResult {
  CheckoutRequestID?: string;
  MerchantRequestID?: string;
  ResponseCode?: string;
  CustomerMessage?: string;
  errorMessage?: string;
}

/**
 * Trigger the STK Push (the M-Pesa PIN prompt on the user's phone).
 *
 * PAYMENT TARGET: the money lands in a **Till (Buy Goods)**. `DARAJA_SHORTCODE` is used only to build
 * the Lipa na M-Pesa password (the store's Head Office number); `TILL_NUMBER` is the actual till the
 * funds go to (PartyB). Set `DARAJA_TX_TYPE=CustomerPayBillOnline` to fall back to a paybill instead.
 *
 * @param accountReference short label shown on the statement — we pass "save" or "goal".
 */
export async function initiateStkPush(
  phone: string,
  amount: number,
  accountReference = "SaveLock",
): Promise<StkPushResult> {
  const shortcode = Deno.env.get("DARAJA_SHORTCODE")!;
  const passkey = Deno.env.get("DARAJA_PASSKEY")!;
  const callbackUrl = Deno.env.get("DARAJA_CALLBACK_URL")!;
  const tillNumber = Deno.env.get("TILL_NUMBER");
  // Buy Goods (till) by default; set DARAJA_TX_TYPE=CustomerPayBillOnline for a paybill.
  const txType = Deno.env.get("DARAJA_TX_TYPE") ?? "CustomerBuyGoodsOnline";
  const isBuyGoods = txType === "CustomerBuyGoodsOnline";
  // For Buy Goods the funds go to the till (PartyB = TILL_NUMBER); for a paybill they go to the shortcode.
  const partyB = isBuyGoods ? (tillNumber ?? shortcode) : shortcode;

  const ts = timestamp();
  const password = btoa(`${shortcode}${passkey}${ts}`);
  const token = await getAccessToken();

  const body = {
    BusinessShortCode: shortcode,
    Password: password,
    Timestamp: ts,
    TransactionType: txType,
    Amount: amount,
    PartyA: phone,
    PartyB: partyB,
    PhoneNumber: phone,
    CallBackURL: callbackUrl,
    AccountReference: accountReference.slice(0, 12),
    TransactionDesc: isBuyGoods ? "Paying Vendor Till" : "SaveLock savings",
  };

  const res = await fetch(`${base()}/mpesa/stkpush/v1/processrequest`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return await res.json() as StkPushResult;
}

/**
 * Register the C2B Validation + Confirmation URLs with Daraja so Safaricom notifies us whenever a
 * customer pays our Till/Paybill directly (offline, without an STK push). Run ONCE (re-runnable).
 *
 * Uses the same account/creds as the STK push. ShortCode is the Till/Paybill number that receives
 * the money (TILL_NUMBER for Buy Goods, else DARAJA_SHORTCODE). ResponseType "Completed" means
 * Safaricom auto-completes a payment if our validation URL is slow/unreachable (we never reject).
 */
export async function registerC2BUrls(confirmationUrl: string, validationUrl: string): Promise<unknown> {
  const shortCode = Deno.env.get("TILL_NUMBER") ?? Deno.env.get("DARAJA_SHORTCODE")!;
  const token = await getAccessToken();
  const res = await fetch(`${base()}/mpesa/c2b/v1/registerurl`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      ShortCode: shortCode,
      ResponseType: "Completed",
      ConfirmationURL: confirmationUrl,
      ValidationURL: validationUrl,
    }),
  });
  return await res.json();
}

/** Small JSON response helper. */
export function json(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
