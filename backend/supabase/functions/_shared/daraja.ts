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

/** Trigger the STK Push (the M-Pesa PIN prompt on the user's phone). */
export async function initiateStkPush(phone: string, amount: number): Promise<StkPushResult> {
  const shortcode = Deno.env.get("DARAJA_SHORTCODE")!;
  const passkey = Deno.env.get("DARAJA_PASSKEY")!;
  const callbackUrl = Deno.env.get("DARAJA_CALLBACK_URL")!;
  // Paybill by default; set DARAJA_TX_TYPE=CustomerBuyGoodsOnline for a till.
  const txType = Deno.env.get("DARAJA_TX_TYPE") ?? "CustomerPayBillOnline";

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
    PartyB: shortcode,
    PhoneNumber: phone,
    CallBackURL: callbackUrl,
    AccountReference: "SaveLock",
    TransactionDesc: "SaveLock daily savings",
  };

  const res = await fetch(`${base()}/mpesa/stkpush/v1/processrequest`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return await res.json() as StkPushResult;
}

/** Small JSON response helper. */
export function json(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
