// GET /till-payments?sinceMinutes=1440  ->  { payments: [{ transId, amount, msisdn, billRef, createdAt }] }
// The Android app polls this when online to catch direct Till/Paybill payments (C2B) it may have
// missed — a server-side backup to the on-device M-Pesa SMS auto-unlock. Requires the shared app key.
// Deduping/crediting happens in the app (by transId), so this only needs to return recent rows.

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

  const url = new URL(req.url);
  // Look back a bounded window (default 24h, max 7 days) so the response stays small on weak networks.
  const sinceMinutes = Math.min(
    Math.max(Number(url.searchParams.get("sinceMinutes") ?? 1440) || 1440, 1),
    7 * 24 * 60,
  );
  const sinceIso = new Date(Date.now() - sinceMinutes * 60_000).toISOString();

  const { data } = await supabase
    .from("till_payments")
    .select("trans_id, amount, msisdn, bill_ref_number, created_at")
    .gte("created_at", sinceIso)
    .order("created_at", { ascending: false })
    .limit(50);

  const payments = (data ?? []).map((r) => ({
    transId: r.trans_id,
    amount: r.amount,
    msisdn: r.msisdn,
    billRef: r.bill_ref_number,
    createdAt: r.created_at,
  }));

  return json({ payments });
});
