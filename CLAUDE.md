# CLAUDE.md — SaveLock Rules & Project Map

This is the **rules file**. Read it at the start of every session before doing any work.

---

## 0. HARD RULES (never break these)

1. **Git branching:** All work is committed to the **`feature`** branch and pushed there. **Never push to `main`.** `main` is the stable branch; it only ever receives reviewed merges from `feature`. The remote is `https://github.com/wazimuautomate/SaveLock.git`.
2. **Secrets never touch the app or the repo.** Daraja (Safaricom) consumer key/secret/passkey and any Supabase service-role key live **only** in server-side environment variables on Supabase. They are never hard-coded in Kotlin, never in `strings.xml`, never committed. `.gitignore` blocks `.env`, `*.jks`, `*.keystore`, `google-services.json`, `node_modules`, and the Supabase env files.
3. **The app must always remain uninstallable.** SaveLock uses a *soft-lock* (it blocks the user's chosen distraction apps only). It may add Device-Admin "uninstall friction" (user must disable admin first), but it must **never** implement kiosk mode, lock-task, or any true anti-uninstall. Safe Mode and factory reset must always remove it. This is a deliberate **safety boundary**, not a missing feature.
4. **Never block emergency access.** The accessibility soft-lock must **never** intercept Phone/Dialer, Messages, Settings, or the launcher — only packages the user explicitly put on their distraction list. This exclusion is enforced in code, not just in the UI.
5. **Keep the existing UI contracts intact.** Do not redesign screens. The `UiState` data classes the Composables read (in `com.example.viewmodel.*`) must keep the same field names/types. Only their data *source* changes (mock → repository).
6. **Update `memory.md` after every work session.** It is the running log so context is never lost. `CHANGELOG.md` records user-facing changes.
7. **Explain to the user in simple English.** The owner is building their first mobile app, cannot run Android Studio, and cannot test on their phone. Correctness matters more than speed. When in doubt, ask.

---

## 1. What SaveLock is

A **personal savings-discipline app** (sideloaded, single user, not on Play Store). Each day the user must "save" a set amount via **M-Pesa (Safaricom Daraja STK Push)** before a scheduled time. If they miss it, their chosen **distraction apps get soft-locked** — opening one throws SaveLock's lock screen in front of it — until they either pay or enter an offline **recovery code**.

Target devices: Samsung A05/A06 and similar low-end phones, Android **13+** (built with minSdk 30 = Android 11, so 13+ is fully covered). These OEMs aggressively kill background apps, so scheduling is built around exact alarms + reboot re-arming + manual battery exemptions.

---

## 2. Key design decisions (locked with the owner)

| Topic | Decision |
|---|---|
| Lock strength | **Strong soft-lock + Device-Admin uninstall friction.** Blocks only chosen apps; re-appears instantly past deadline; re-arms after reboot. Device Admin means the user must disable admin before uninstalling. Still removable via Safe Mode / factory reset (documented). |
| Offline behaviour | **Recovery codes work fully offline.** Payments **queue and auto-retry** when signal returns. True airplane-mode M-Pesa payment is impossible and is not attempted. |
| Backend | **Supabase** — Edge Functions (`stk-push`, `stk-callback`, `stk-status`) + Postgres. Chosen for a free always-on HTTPS callback URL + DB + secret storage, nothing to self-host. |
| Slimming | **Firebase / Gemini AI / App Check / secrets-gradle-plugin removed** (unused template leftovers). Keeps the app lightweight. |
| Low-internet | Small JSON payloads, generous timeouts, polling with backoff so weak 2G/3G still works. |
| Lock modes | **Two modes** on `SavingsConfig.lockMode`. `CHOSEN_APPS`: block only ticked apps. `FULL_LOCKDOWN`: block everything incl. Settings; home shows the lock screen only (Call/Messages/pay/recovery). |
| Immovable rule | In BOTH modes, **emergency phone calls + the messaging app are never blockable** (hard-coded allow-list). Escape hatches: pay, recovery code, or **Safe Mode** (disables the accessibility service). Safe Mode + factory reset keep the app uninstallable. |

---

## 3. Tech stack

- **Language/UI:** Kotlin, Jetpack Compose, Material 3, Navigation-Compose.
- **Local data:** Room (SQLite) exposing Kotlin `Flow`s. Small flags via Room too (no DataStore, to stay light).
- **Networking:** Retrofit + OkHttp + Moshi → talks only to the Supabase backend over HTTPS (never Daraja directly).
- **Background:** AlarmManager (`setExactAndAllowWhileIdle`) for the daily lock trigger; WorkManager for reminder notifications; a foreground Service to supervise the day; BroadcastReceiver for `BOOT_COMPLETED`.
- **Enforcement:** AccessibilityService (detects foreground app) + an overlay Activity (`TYPE_APPLICATION_OVERLAY`).
- **Security:** recovery codes hashed with a per-code salt (PBKDF2/SHA-256); no plaintext stored.
- **DI:** tiny manual `ServiceLocator` (no Hilt/Dagger — keeps build light).
- **Backend:** Supabase Edge Functions (TypeScript/Deno) + Postgres.

---

## 4. Folder / file map

> Files marked **(new)** are created by the logic layer; **(mod)** are existing files being adapted; the rest are the original UI scaffold.

```
SaveLock/
├─ CLAUDE.md                 rules + map (this file)
├─ memory.md         (new)   running work log — update every session
├─ CHANGELOG.md      (new)   user-facing change history
├─ README.md         (mod)   project overview + setup + phone steps
├─ backend/          (new)   Supabase project (separate from the app)
│  ├─ README.md              env vars, deploy steps, Daraja callback URL
│  ├─ .env.example
│  └─ supabase/
│     ├─ functions/stk-push/index.ts
│     ├─ functions/stk-callback/index.ts
│     ├─ functions/stk-status/index.ts
│     └─ functions/_shared/daraja.ts
│     └─ migrations/0001_init.sql
├─ app/
│  ├─ build.gradle.kts               (mod) deps: -Firebase +WorkManager +security-crypto
│  └─ src/main/
│     ├─ AndroidManifest.xml         (mod) permissions + services/receivers
│     ├─ res/xml/accessibility_service_config.xml   (new)
│     ├─ res/xml/device_admin.xml                   (new)
│     └─ java/com/example/
│        ├─ MainActivity.kt          (mod) real ViewModels + onboarding
│        ├─ SaveLockApplication.kt   (new) app init (DB, channels, DI)
│        ├─ di/ServiceLocator.kt     (new) manual DI
│        ├─ data/
│        │  ├─ local/SaveLockDatabase.kt            (new)
│        │  ├─ local/entity/SavingsConfigEntity.kt  (new)
│        │  ├─ local/entity/SavingsLogEntity.kt     (new)
│        │  ├─ local/entity/RecoveryCodeEntity.kt   (new)
│        │  ├─ local/dao/SavingsConfigDao.kt        (new)
│        │  ├─ local/dao/SavingsLogDao.kt           (new)
│        │  ├─ local/dao/RecoveryCodeDao.kt         (new)
│        │  ├─ remote/PaymentApi.kt                 (new) Retrofit → Supabase
│        │  ├─ remote/dto/*.kt                      (new) request/response models
│        │  └─ repository/SaveLockRepository.kt     (new) Flows for the VMs
│        ├─ domain/
│        │  ├─ RecoveryCodeManager.kt   (new) generate/salt+hash/verify
│        │  └─ LockStateManager.kt      (new) "should we lock right now?" logic
│        ├─ scheduling/
│        │  ├─ AlarmScheduler.kt        (new)
│        │  ├─ LockCheckReceiver.kt     (new) fires at lock time
│        │  ├─ BootReceiver.kt          (new) re-arms after reboot
│        │  └─ ReminderWorker.kt        (new) WorkManager reminders
│        ├─ service/
│        │  ├─ SaveLockForegroundService.kt         (new) resilience anchor + ShadeGuard
│        │  ├─ AppBlockerAccessibilityService.kt    (new) drives the lock overlay
│        │  └─ LockScreenController.kt              (new) full-screen system-overlay lock window
│        ├─ admin/DeviceAdminReceiver.kt            (new) uninstall friction
│        ├─ ui/screens/LockScreenContent.kt         (new) inline lock UI (no dialogs; runs in overlay)
│        ├─ util/NotificationManagerHelper.kt       (mod) reminders/status channels
│        ├─ ui/…                        UI scaffold (unchanged look)
│        └─ viewmodel/*ViewModel.kt     (mod) consume repository Flows
```

---

## 5. Data flow (how a day works)

1. **Settings screen** writes `SavingsConfig` (amount, lock time, reminders, M-Pesa number, distraction apps, enabled flag) → Room → repository `Flow` → every screen updates.
2. `AlarmScheduler` sets an exact alarm at the lock time; WorkManager schedules reminders at each lead time; foreground service supervises.
3. **Reminder time:** notification on the `reminders` channel.
4. **Lock time:** if any active plan's current period is due-and-unpaid, the app enters *locked* state. The AccessibilityService puts up SaveLock's full-screen **system overlay** (`LockScreenController`, `TYPE_APPLICATION_OVERLAY`) — it stays over the launcher/recents and swallows Back. A `status` notification fires. (Design note: the lock is now per-plan, not a single global lock time.)
5. **User pays:** Payment sheet → backend `/stk-push` → poll `/stk-status` → on success write a `SavingsLog` (Saved), bump streak, clear lock, cancel remaining reminders/notifications.
6. **No signal:** user enters a **recovery code** → verified offline against hashed codes → mark used → today's `SavingsLog` = "Recovery code used" → lift lock. (Or the queued payment auto-retries when online.)
6. **Reboot:** `BootReceiver` re-arms alarms and restarts the service/lock state.

---

## 6. Manual phone setup (cannot be granted by code — see README for the click-by-click)

Notifications permission, Overlay ("Display over other apps") permission, Accessibility service toggle, and **Battery → Unrestricted** + **Auto-start** on Samsung. Device Admin activation (for uninstall friction) is also a manual toggle. None can be silently requested.

---

## 7. Android limits the owner should remember

- No sideloaded app can be truly unremovable (Safe Mode / factory reset always win). Uninstall friction ≠ unremovable.
- M-Pesa always needs a network; only recovery codes are truly offline.
- Samsung can still kill background work; the manual battery exemptions are what make it reliable.
