# SaveLock 🔒💰

A personal **savings-discipline** app for Android. Every day you must "save" a set amount via
**M-Pesa** before a time you choose. If you don't, the **distraction apps you picked** get
soft-locked — opening one shows a lock screen until you either pay or use a recovery code.

> Personal, sideloaded app (not on the Play Store). Built for low-end phones (Samsung A05/A06)
> on **Android 13+**.

---

## What it does

- ⏰ **Daily deadline.** Pick a daily amount, a lock time, and reminder lead times (e.g. 2h / 1h before).
- 📲 **M-Pesa saving.** Pay through Safaricom **Daraja STK Push** (a prompt pops on your phone; you enter your PIN).
- 🚫 **Soft-lock.** Miss the deadline and your chosen distraction apps are blocked (the lock screen keeps coming back). **Phone, Messages, and Settings are never blocked** — emergency access always works.
- 🔑 **Recovery codes.** 10 one-time codes, generated and shown once, stored only as salted hashes. They unlock the app **fully offline** (works in airplane mode).
- 🔁 **Bad-signal friendly.** Small data usage; if a payment can't go through on weak signal it queues and retries automatically when signal returns.
- 🔔 **Status-bar notifications** for reminders and lock/payment events.
- 🔐 **Uninstall friction.** The app registers as a Device Admin, so you must turn admin off before uninstalling — a speed bump against impulse-quitting. (It is still removable via Safe Mode or factory reset; see *Safety boundary* below.)

---

## How it's built (plain overview)

```
Your phone (SaveLock app)  ──HTTPS──▶  Supabase backend  ──▶  Safaricom Daraja (M-Pesa)
   Room database (local)                Edge Functions + Postgres     STK Push
   Alarms + Foreground service          holds the secret keys
   Accessibility soft-lock              stores payment results
```

- **App:** Kotlin + Jetpack Compose, Room (local database), Retrofit (networking), AlarmManager +
  WorkManager (scheduling), an AccessibilityService + overlay (the soft-lock).
- **Backend:** **Supabase** — three small functions (`stk-push`, `stk-callback`, `stk-status`) plus a
  Postgres table. **All Daraja secret keys live only on the server**, never in the app. See
  [`backend/README.md`](backend/README.md).

Full internal map and the project rules are in [`CLAUDE.md`](CLAUDE.md).

---

## Safety boundary (please read)

SaveLock is a **soft-lock**, not a device lock. By design it can **always be removed** (turn off
Device Admin, or boot into Safe Mode, or factory reset). It **never** blocks the dialer, messages,
or Settings. This is intentional — you must never be trapped out of your own phone in an emergency.
No sideloaded app can be made truly unremovable without wiping the phone.

---

## Building the APK (the installable app file)

Android apps are normally built in **Android Studio**. If your PC can't run it, the plan is to set up
a **free GitHub Actions cloud build** so every push to the `feature` branch automatically produces an
installable `app-debug.apk` you can download and sideload — no Android Studio needed. (Ask and I'll add this.)

If you *do* have Android Studio:
1. Open the project folder in Android Studio and let it sync.
2. Create a `.env` if needed (no Gemini key is required anymore — that template code was removed).
3. Build ▸ Build APK, then copy the APK to your phone and install it.

---

## One-time setup on the phone (manual — Android won't let an app grant these itself)

After installing, the app will guide you, but here is the checklist:

1. **Notifications** — allow when asked (needed for reminders/alerts).
2. **Display over other apps** (overlay) — Settings ▸ Apps ▸ SaveLock ▸ *Display over other apps* ▸ Allow.
3. **Accessibility** — Settings ▸ Accessibility ▸ SaveLock ▸ turn on. (This is what detects when you open a blocked app.)
4. **Battery** — Settings ▸ Apps ▸ SaveLock ▸ Battery ▸ **Unrestricted**. On Samsung also enable **Auto-start** and remove SaveLock from "Sleeping apps".
5. **Device Admin** (optional friction) — the app will prompt; enabling it means you must disable admin before uninstalling.
6. **Exact alarms** — on Android 13/14 the app requests "Alarms & reminders"; allow it so the daily lock fires on time.

Without steps 3–4 the lock may not trigger reliably on Samsung, because the system puts the app to sleep.

---

## Backend setup

See [`backend/README.md`](backend/README.md) for Supabase deployment, the environment variables to set,
and the exact **Daraja callback URL** to register in your Safaricom Daraja app.

---

## Project docs

- [`CLAUDE.md`](CLAUDE.md) — rules and full architecture map.
- [`memory.md`](memory.md) — running build log.
- [`CHANGELOG.md`](CHANGELOG.md) — what changed.
