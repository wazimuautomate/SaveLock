// GET /stk-status/<checkoutRequestId>  ->  { checkoutRequestId, status, amount, resultDesc }
// The Android app polls this after starting a payment. Requires the shared app key header.
// status is one of: PENDING | SUCCESS | FAILED  (or NOT_FOUND if unknown).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { json } from "../_shared/daraja.ts";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async (req) => {
  if (req.method !== "GET") return json({ error: "Method not allowed" }, 405);
  if (req.headers.get("x-app-key") !== Deno.env.get("APP_BACKEND_KEY")) {
    return json({ error: "Unauthorized" }, 401);
  }

  // Accept the id from the trailing path segment (/stk-status/<id>) or ?id=<id>.
  const url = new URL(req.url);
  const fromPath = url.pathname.split("/").filter(Boolean).pop();
  const id = url.searchParams.get("id") ??
    (fromPath && fromPath !== "stk-status" ? decodeURIComponent(fromPath) : null);
  if (!id) return json({ error: "Missing checkoutRequestId" }, 400);

  const { data } = await supabase
    .from("stk_transactions")
    .select("checkout_request_id, status, amount, result_desc")
    .eq("checkout_request_id", id)
    .maybeSingle();

  if (!data) {
    return json({ checkoutRequestId: id, status: "NOT_FOUND" });
  }

  return json({
    checkoutRequestId: data.checkout_request_id,
    status: data.status,
    amount: data.amount,
    resultDesc: data.result_desc,
  });
});
