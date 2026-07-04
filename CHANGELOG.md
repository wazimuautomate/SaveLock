# Changelog

All notable changes to SaveLock are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project uses date-based entries (personal, non-versioned build).

## [Unreleased]

### Fixed (M-Pesa STK prompt visibility)
- **The M-Pesa STK PIN prompt is no longer hidden behind the lock screen.** During an in-progress
  STK payment, the lock overlay now stays attached as a dimmed visible lock page while becoming
  tap-through, so the system M-Pesa prompt can appear and accept the PIN. The broad startup window is
  short, and after that the accessibility service only keeps pass-through active for recognized
  M-Pesa/STK payment surfaces.

### Fixed (whitelisted apps and keyboard)
- **Keyboard/IME packages are no longer treated as blocked foreground apps.** SaveLock ignores the
  active keyboard package so recovery-code entry, M-Pesa number entry, and M-Pesa PIN entry are not
  covered by a fresh overlay reassert.
- **Messages, SIM Toolkit and Phone/Dialer get a short exact-package launch window** when opened from
  the lock screen. This prevents Samsung SystemUI/launcher transition events from immediately
  re-covering those allowed apps, while any app opened from inside them is still blocked immediately.
- **Allowed-app detection is broader on Samsung/low-end Android phones.** SaveLock now includes common
  Samsung/Google/AOSP Messages and Dialer packages plus more SIM Toolkit package candidates and a
  launcher-label scan for SIM Toolkit.
- **Unlocking the phone now forces the lock overlay back on top.** This closes the Samsung A05 path
  where Settings was opened from the phone lock screen before drawing the pattern and then stayed open
  after unlock.
- **Start Locking now requires uninstall protection first.** If Device Admin is not active, tapping
  Start Locking opens the uninstall-protection prompt instead of starting a weak lock.

### Changed (Samsung A05 lock hardening)
- **Locking now starts only when you tap "Start locking now".** Creating a Savings plan or Goal saves
  it, but does not automatically arm the lock. Starting the lock also switches enforcement to Full
  Lockdown so only SIM Toolkit, Messages and Phone/Dialer are usable until payment or recovery.
- **Full Lockdown no longer treats the notification shade as an allowed app.** On Samsung A05 the
  shade reports as `com.android.systemui`; SaveLock now reasserts the overlay instead of hiding it
  when that system surface appears.
- **Accessibility enforcement listens to more window/system events** so recents, notification shade,
  and system-window changes are reacted to faster.
- **Payment modal is centered** with a max-width content container on both Home and the lock screen.

### Fixed
- **Lock screen crash fixed.** The latest lock-screen WiFi/data buttons checked network and WiFi state
  while the overlay was opening, but the app did not declare the harmless state-read permissions Android
  requires. On some phones that throws a security error and closes SaveLock right when the lock should
  appear. Added the missing state permissions and made those checks fail-safe, so the lock page can open
  even if an OEM blocks one of the status APIs.
- **Recovery code now actually removes the lock.** Previously it said "Unlocked" but the lock screen
  stayed up — the lock is now re-checked from fresh data the instant a code (or any payment) is
  accepted, so the overlay comes straight down. Same fix makes STK / SMS / paste-code unlocks instant.

### Added (lock-screen usability while locked)
- **The M-Pesa PIN now shows over the lock.** During an STK payment the lock steps out of the PIN
  dialog's way (stays visible, becomes tap-through) so you can actually enter your PIN, then re-arms.
- **"I paid — enter M-Pesa code"** on the lock screen: paste the code from the M-Pesa SMS to confirm a
  payment when auto-detect hasn't fired. It's checked against the real message in your inbox (a made-up
  code won't work) and, if you set a till name, must be a payment to your till.
- **Turn on WiFi / mobile data from the lock screen** when you're offline — the icons appear only when
  that radio is off and there's no internet, and open the system quick-panel.
- **Allowed-app shortcuts on the lock screen** (SIM Toolkit, Messages, Phone) so you can pay offline,
  read the code, or dial without hunting for them.

### Changed (Full Lockdown is now escape-proof)
- In **Full Lockdown**, only **SIM Toolkit, Messages and the Phone/Dialer** (plus emergency) can be
  opened — everything else, including the launcher and recents, is blocked, and the lock slams back the
  instant any other app is opened (so Messages can't be used as a back door to other apps).

### Added (offline & low-data payments)
- **Pay your till directly and unlock offline.** New **Settings → Offline M-Pesa Unlock**: enter your
  till/business name and turn on auto-unlock. Now you can pay your till straight from the phone's
  M-Pesa menu (works on GSM signal even with **no mobile data**), and SaveLock reads Safaricom's
  confirmation SMS and unlocks automatically — **no internet needed on the phone**. It also rescues a
  normal STK payment if your data drops mid-transaction. (True *no-signal* unlock still uses a
  recovery code — M-Pesa itself always needs the network.)
- **Server-side backup (C2B):** the backend can now catch direct till/paybill payments via Safaricom's
  C2B webhook and the app reconciles them when it's back online — so a missed SMS still unlocks you.

### Fixed (payment errors were hidden)
- The payment screen used to say **"Timeout"** for almost any failure (wrong keys, wrong Daraja
  environment, rejected push). It now shows the **real reason** from the backend/M-Pesa so a failing
  payment can actually be diagnosed and fixed.

### Changed (lock-screen motivation)
- **The lock screen now speaks to you.** The old generic "your distraction apps are locked out"
  line is replaced by a bold, deliberately provoking message with a supporting sub-line — and it
  **rotates to a different one every day**. Savings plans and Goals each have their own set of
  messages; goal messages weave in the goal's name, days left, amount remaining and percent done
  (e.g. "You're only Ksh 3,500 away from Bike."). Long titles auto-shrink so they always fit.

### Fixed (backend wiring)
- Corrected the GitHub secret name so the released APK is actually wired to Supabase (the URL secret
  had been named `SUPABASE_FUNCTIONS_KEY` instead of `SUPABASE_FUNCTIONS_URL`, so builds shipped
  "unconfigured" and payments showed the "not set up" message).

### Changed (payments + final hardening)
- **Real M-Pesa payments** — the app now performs a genuine STK Push through the backend (to your
  **Till / Buy Goods** number). The old demo/auto-success is gone; if the backend isn't set up yet,
  the payment screen says so instead of pretending to succeed.
- **Flexible plans let you type the amount** on the payment screen (blank field, must be at least the
  minimum you set). Fixed plans still show the set amount.
- **Harder to bypass with the power button** — after you unlock the screen, the lock re-appears on top
  immediately, and a background check re-locks within ~1.5s if anything ever slips through.
- **Deleting a plan keeps its payment history** — your past savings still show under History.
- The APK is now published to the **Releases page** on each update to the main branch (no need to open
  Actions → Artifacts).

### Changed (lock strength + UX)
- **The lock is now far harder to bypass.** It's a full-screen overlay that stays on top of the
  home screen and recent apps, and the Back button no longer closes it. You can still type your
  recovery code / phone number on it, and the notification shade stays blocked.
- **Removed the Emergency button** from the lock screen (it was letting people slip past the lock).
  To get out: pay, use a recovery code, or restart the phone in Safe Mode.
- **Home is now just your progress.** Creating, editing, deleting plans and the master
  Saving-Enabled switch moved to **Settings → Savings & Goals**.
- **Setup & Permissions** only lists what's still off (each with a warning + "Turn on"), and shows
  step-by-step help for turning on Uninstall protection.
- Phone numbers can be typed as **07…, 01…, +254…, or 254…** — they're normalised automatically.
- The app-blocking list now shows each app's **icon** for easy identification.

### Added
- **Edit a plan**: each Savings/Goal on Home now has an edit button to change its name, amount,
  schedule, or goal target without deleting it.
- **Payment history**: the History tab now lists every real payment (which plan, when, how much)
  plus recovery-code unlocks, with a recent-payments trend and your total saved.

### Changed
- **Payments now go to a Till (Buy Goods)** via `TILL_NUMBER`, with the reference shown as
  "save" or "goal" depending on the plan. (Set `DARAJA_TX_TYPE=CustomerPayBillOnline` to use a paybill.)

### Added (Savings & Goals)
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
