# Changelog

All notable changes to SaveLock are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project uses date-based entries (personal, non-versioned build).

## [Unreleased]

### Added
- **Savings & Goals plans** — run many at once, side by side. A **Savings** plan repeats forever
  (save a set amount every period); a **Goal** works toward a target total. The Home screen now
  lists each plan with a live progress bar, its status, a Save/Pay button, and a delete option.
- **Create Savings/Goal screen**: pick an exact amount or a minimum, and a schedule of Daily,
  Every 2 days, Weekly, Monthly, Every N days, or Every N hours. Goals also take a target total
  and an optional deadline.
- The lock screen now shows every plan that is due, each with its own "Pay & unlock" button.

### Changed
- The phone now locks whenever **any** plan is due-and-unpaid for its current period, and unlocks
  when every due plan is paid (or a recovery code is used). Plans share one lock.
- A recovery code now clears the current period of **all** due plans at once (still fully offline).
- Removed the old single "daily lock time" and lead-time reminder settings — timing is now per plan.

### Added (earlier)
- Project documentation: `CLAUDE.md` (rules + full project map), `memory.md` (running work log),
  this `CHANGELOG.md`, and a rewritten `README.md`.
- Git repository initialized with `main` + `feature` branches and the GitHub remote
  `wazimuautomate/SaveLock`. Hardened `.gitignore` so secrets/keystores/backend env files
  are never committed.
- **Room data layer**: `SavingsConfig`, `SavingsLog`, `RecoveryCode` entities + DAOs + database;
  `SaveLockRepository` exposing Flows; offline `RecoveryCodeManager` (salted PBKDF2 hashing).
- **All 5 ViewModels migrated** to real repository data (UI contracts unchanged). Settings now
  persists to Room. Added a **Lock Strictness** selector (Chosen apps / Full lockdown).
- **Two lock modes** (`LockMode`): `CHOSEN_APPS` and `FULL_LOCKDOWN` (blocks all but calls/messages).
- **GitHub Actions cloud build** producing a downloadable debug APK on every push (Gradle 9.3.1).
- Env templates: `backend/.env.example` and `app/savelock.properties.example` (→ `BuildConfig`).

- **Scheduling**: exact daily lock alarm (`setExactAndAllowWhileIdle`) with boot re-arm
  (`BootReceiver`), WorkManager lead-time reminders, and a `LockStateManager`.
- **Foreground service** (special-use) as a resilience anchor + three notification channels
  (reminders / status / foreground).
- **Soft-lock**: `AppBlockerAccessibilityService` redirects blocked apps to a `LockOverlayActivity`,
  with a hard emergency allow-list (dialer/SMS/system UI/keyboard/self) that can never be blocked.
- **Device Admin** uninstall friction + a **Setup & Permissions** onboarding card in Settings.
- **Payments**: `PaymentRepository` (STK push + 60s status poll + weak-signal retry) → Supabase
  Edge Functions (`stk-push`, `stk-callback`, `stk-status`) + Postgres. Real flow when the backend
  is configured, demo flow otherwise. On success: writes the log, notifies, clears the lock.
- **Supabase backend project** under `backend/` with deploy README and Daraja callback URL.

### Changed
- Removed unused Firebase / Gemini / App-Check / secrets-gradle code (lighter app); added WorkManager.
- Debug builds now use the default auto-generated keystore (so any machine/CI can build).

### Notes
- Full lockdown blocks Settings too (owner's choice); escape via pay / recovery code / Safe Mode.
  Emergency calls are never blockable. See README "How to escape".

### Planned (next)
- Room data layer (SavingsConfig, SavingsLog, RecoveryCode) + repository exposing Flows.
- Migrate the 5 mock ViewModels to real repository data (UI contracts unchanged).
- Scheduling (exact alarms, boot re-arm, WorkManager reminders) + foreground service.
- Notifications (`reminders` + `status` channels).
- Accessibility soft-lock + overlay Activity (emergency apps always excluded).
- Device-Admin uninstall friction (app stays uninstallable via Safe Mode / factory reset).
- Recovery codes: generate 10, salt+hash, verify fully offline.
- Payment flow wired to the Supabase backend (STK push + poll + offline queue/retry).
- Supabase backend: `stk-push`, `stk-callback`, `stk-status` Edge Functions + Postgres.
- Removal of unused Firebase/Gemini template code.

### Notes
- Decision: the lock is a *soft-lock*; the app is always removable (Safe Mode / factory reset).
  This is an intentional safety boundary.
- Decision: true offline M-Pesa payment is impossible; only recovery codes are fully offline.
