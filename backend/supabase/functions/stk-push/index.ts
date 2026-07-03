// POST /stk-push  { phone: "2547XXXXXXXX", amount: 500 }  ->  { checkoutRequestId }
// Called by the Android app. Requires the shared app key header. Triggers the M-Pesa prompt and
// records a PENDING transaction row.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { initiateStkPush, json } from "../_shared/daraja.ts";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  if (req.headers.get("x-app-key") !== Deno.env.get("APP_BACKEND_KEY")) {
    return json({ error: "Unauthorized" }, 401);
  }

  const body = await req.json().catch(() => ({}));
  const phone = String(body.phone ?? "");
  const amount = Number(body.amount ?? 0);
  if (!/^2547\d{8}$/.test(phone) || !(amount > 0)) {
    return json({ error: "Invalid phone or amount" }, 400);
  }

  let result;
  try {
    result = await initiateStkPush(phone, amount);
  } catch (e) {
    return json({ error: "STK push error", detail: String(e) }, 502);
  }

  const checkoutId = result.CheckoutRequestID;
  if (!checkoutId) {
    return json({ error: "STK push rejected", detail: result }, 502);
  }

  await supabase.from("stk_transactions").upsert({
    checkout_request_id: checkoutId,
    merchant_request_id: result.MerchantRequestID ?? null,
    phone,
    amount,
    status: "PENDING",
    updated_at: new Date().toISOString(),
  });

  return json({
    checkoutRequestId: checkoutId,
    merchantRequestId: result.MerchantRequestID ?? null,
    message: result.CustomerMessage ?? "STK push sent",
  });
});
