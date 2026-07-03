// POST /c2b-validation — called by Safaricom BEFORE completing a direct Till/Paybill payment.
// We accept every payment (SaveLock never rejects money paid to the till), so this just ACKs.
// Registered once as the ValidationURL (see c2b-register). Unauthenticated by design — Safaricom
// does not send our app key, and this endpoint reveals nothing and changes nothing.

import { json } from "../_shared/daraja.ts";

Deno.serve((req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  // ResultCode 0 = accept. (Daraja also honours ResponseType=Completed if we were ever unreachable.)
  return json({ ResultCode: 0, ResultDesc: "Accepted" });
});
