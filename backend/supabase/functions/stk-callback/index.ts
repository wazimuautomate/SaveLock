// POST /stk-callback  — called by Safaricom Daraja when the payment completes (success or fail).
// Register this function's URL as the callback URL in your Daraja app AND in DARAJA_CALLBACK_URL.
// Daraja does not send our app key, so this endpoint is unauthenticated by design; it only updates
// a row keyed by the CheckoutRequestID that we created, and never exposes data back.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { json } from "../_shared/daraja.ts";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const payload = await req.json().catch(() => null);
  const cb = payload?.Body?.stkCallback;
  if (!cb?.CheckoutRequestID) {
    // Always ack so Daraja doesn't retry forever, even on unexpected shapes.
    return json({ ResultCode: 0, ResultDesc: "Ignored" });
  }

  const resultCode = Number(cb.ResultCode);
  const success = resultCode === 0;

  // Pull the receipt + amount out of the metadata (only present on success).
  let receipt: string | null = null;
  let amount: number | null = null;
  const items = cb.CallbackMetadata?.Item ?? [];
  for (const it of items) {
    if (it.Name === "MpesaReceiptNumber") receipt = String(it.Value);
    if (it.Name === "Amount") amount = Number(it.Value);
  }

  await supabase.from("stk_transactions").update({
    status: success ? "SUCCESS" : "FAILED",
    result_code: resultCode,
    result_desc: cb.ResultDesc ?? null,
    mpesa_receipt: receipt,
    amount: amount ?? undefined,
    updated_at: new Date().toISOString(),
  }).eq("checkout_request_id", cb.CheckoutRequestID);

  // Daraja expects this exact acknowledgement shape.
  return json({ ResultCode: 0, ResultDesc: "Accepted" });
});
