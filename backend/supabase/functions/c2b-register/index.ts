// POST /c2b-register — one-time (re-runnable) setup: registers our C2B Validation + Confirmation
// URLs with Safaricom so direct Till/Paybill payments notify us. Protected by the shared app key so
// only the owner can trigger it. Body (optional): { confirmationUrl, validationUrl } to override the
// defaults (which are derived from this project's own function base URL).
//
// Success looks like: { "ResponseDescription": "success", ... }. Anything else is an error to fix
// (e.g. sandbox creds in production, or a shortcode not yet Go-Live).

import { json, registerC2BUrls } from "../_shared/daraja.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  if (req.headers.get("x-app-key") !== Deno.env.get("APP_BACKEND_KEY")) {
    return json({ error: "Unauthorized" }, 401);
  }

  const body = await req.json().catch(() => ({}));
  // Default the URLs to sibling functions of whatever base URL called us.
  const origin = new URL(req.url).origin; // e.g. https://<ref>.functions.supabase.co
  const confirmationUrl = String(body.confirmationUrl ?? `${origin}/c2b-confirmation`);
  const validationUrl = String(body.validationUrl ?? `${origin}/c2b-validation`);

  try {
    const result = await registerC2BUrls(confirmationUrl, validationUrl);
    return json({ registered: { confirmationUrl, validationUrl }, daraja: result });
  } catch (e) {
    return json({ error: "C2B register failed", detail: String(e) }, 502);
  }
});
