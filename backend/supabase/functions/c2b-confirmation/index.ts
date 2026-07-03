// POST /c2b-confirmation — called by Safaricom AFTER a customer pays our Till/Paybill directly
// (offline, no STK push). We store the completed transaction so the app can reconcile + unlock when
// it next reaches the internet (backup to the on-device SMS auto-unlock). Registered once as the
// ConfirmationURL (see c2b-register). Unauthenticated by design (Safaricom sends no app key); we only
// insert a row keyed by the unique M-Pesa receipt and never expose anything back.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { json } from "../_shared/daraja.ts";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const p = await req.json().catch(() => null);
  const transId = p?.TransID ?? p?.transID ?? null;
  if (!transId) {
    // Always ack so Safaricom doesn't retry forever, even on an unexpected shape.
    return json({ ResultCode: 0, ResultDesc: "Ignored" });
  }

  // upsert (not insert) so a duplicate delivery of the same receipt is a harmless no-op.
  await supabase.from("till_payments").upsert({
    trans_id: String(transId),
    trans_time: p.TransTime ? String(p.TransTime) : null,
    amount: p.TransAmount != null ? Number(p.TransAmount) : null,
    business_short_code: p.BusinessShortCode ? String(p.BusinessShortCode) : null,
    bill_ref_number: p.BillRefNumber ? String(p.BillRefNumber) : null,
    msisdn: p.MSISDN ? String(p.MSISDN) : null,
    first_name: p.FirstName ? String(p.FirstName) : null,
    raw: p,
  });

  // Daraja expects this exact acknowledgement shape.
  return json({ ResultCode: 0, ResultDesc: "Accepted" });
});
