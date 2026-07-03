# memory.md — SaveLock Running Work Log

This is my memory between sessions. I append a dated entry every time I do a chunk of work,
so I never lose track of what's done and what's next. Newest entries at the top.
For the rules, see [CLAUDE.md](CLAUDE.md).

---

## Legend
- ✅ done  · 🔄 in progress · ⏳ not started · ⚠️ needs owner action

---

## 2026-07-03 — Session 2: Owner test feedback round 1 (CI green, commit bb98ea6)

Fixed from owner testing:
- ✅ Distraction apps are now DYNAMIC (real installed apps via `InstalledAppsProvider`); config stores
  `restrictedPackages: List<String>` (removed hardcoded `DistractionAppRecord`); DB bumped to v2.
  Distraction list is hidden when Full Lockdown is selected.
- ✅ Payment sheet number is pay-FROM only now — editing it no longer overwrites the saved primary
  number (`DashboardViewModel.updateMpesaNumber` no longer persists).
- ✅ Recovery Codes screen has an explicit "Generate New Codes" button (revokes old, reveals+stores
  new, copiable). Removed the racy auto-generate-on-open.
- ✅ Removed the "Need emergency reset? Use Recovery Code" link from Home.
- ✅ Stronger lock: `LockOverlayActivity` immersive full-screen (hides nav+status bars), swallows
  Back, keep-screen-on, sticky immersive; `ShadeGuard` overlay blocks the notification shade during
  full lockdown (via the foreground service). NOTE: true kiosk (fully killing home/recents) needs
  Device Owner (factory reset + PC) — out of scope; documented as best-effort.

⏳ STILL PENDING from feedback (the big one): **Savings vs Goals redesign** + remove global lock-time
("everything locks once not paid") + per-item schedules + home progress bars. Waiting on owner
answers to 4 design questions before building (asked at end of this session).

---

## 2026-07-03 — Session 1 (later): FULL FEATURE BUILD COMPLETE ✅ (CI green throughout)

All 14 planned todos are done and every batch was verified compiling via GitHub Actions.

**Built & pushed (all on `feature`):**
- Recovery screens wired (generate reveals plaintext once, then masked; offline redeem). History
  chart guarded against <2 points.
- Scheduling: `AlarmScheduler` (exact daily alarm + WorkManager reminders), `LockCheckReceiver`,
  `BootReceiver`, `ReminderWorker`, `LockStateManager` (live lock decision). Application re-arms on
  startup + on schedule-setting changes.
- `SaveLockForegroundService` (special-use FGS, crash-safe start) + notification channels/builders.
- `AppBlockerAccessibilityService` (redirects blocked apps; HARD emergency allow-list:
  dialer/SMS/systemUI/keyboard/self) + `LockOverlayActivity` (hosts OverlayScreen + recovery entry;
  full-lockdown Call/Messages buttons; closes when day resolved).
- Device Admin (`SaveLockDeviceAdminReceiver` + device_admin.xml) uninstall friction.
- `PermissionsHelper` + Settings "Setup & Permissions" card (live status + Grant buttons).
- Payments: `PaymentApi`/`PaymentRepository` (retry-on-weak-signal push + 60s poll) → wired into
  DashboardViewModel (real when backend configured via `ServiceLocator.isBackendConfigured`, else demo).
- Backend `backend/supabase`: stk-push/stk-callback/stk-status Edge Functions + `_shared/daraja.ts`,
  `0001_init.sql` (stk_transactions, RLS on), config.toml (verify_jwt off), README (deploy + callback).
- Manifest fully wired: permissions (boot/exact-alarm/FGS special-use/overlay/query-packages) +
  service/receivers/activity + accessibility service + device admin.

**Toolchain facts (for CI):** JDK 21 + Gradle 9.3.1; debug uses default keystore; KSP AWT NPE is
non-fatal noise. Latest green run built the APK with everything included.

**⚠️ Owner actions still needed to make it FULLY live on the phone:**
1. Create a Supabase project; deploy `backend/` (see backend/README); register Daraja callback URL.
2. Fill `app/savelock.properties` (SUPABASE_FUNCTIONS_URL + APP_BACKEND_KEY) and `backend/.env`.
3. Install the APK; grant the manual permissions (Settings → Setup & Permissions card).
Until step 1–2, payments run the DEMO flow (always succeeds, writes a real log).

**Possible next polish (not blocking):** live midnight rollover for `todayLog`; nicer status-screen
copy; app-picker for distraction apps (currently a fixed default list the user toggles).

---

## 2026-07-03 — Session 1: Discovery, planning, docs, git setup

**What I did**
- ✅ Read the entire existing app (all 5 ViewModels, all screens, PaymentSheet, MainActivity,
  manifest, gradle files). Confirmed every screen is driven by mock data with fixed `UiState`
  contracts I must preserve.
- ✅ Initialized git, added remote `wazimuautomate/SaveLock`, created `main` + `feature`
  branches, made the initial commit, switched to `feature`. Hardened `.gitignore` for secrets.
- ✅ Flagged conflicts to the owner and got decisions:
  1. Lock strength → **strong soft-lock + Device-Admin uninstall friction** (still uninstallable).
  2. Offline → **recovery codes offline + payment queue/auto-retry** (no true offline pay).
  3. **Remove Firebase/Gemini** template leftovers (lightweight).
  4. Backend → **Supabase** (Edge Functions + Postgres). GitHub → owner logs in, then I push.
- ✅ Wrote `CLAUDE.md` (rules + full project map), this `memory.md`, `CHANGELOG.md`,
  and rewrote `README.md`.

**Key facts to remember**
- App package/namespace: `com.example` (applicationId `com.aistudio.savelock.rvyqpk`).
- minSdk 30, targetSdk 36. Room, Retrofit, OkHttp, Moshi already in the dependency list.
- ViewModel contracts that MUST stay stable:
  - `DashboardUiState` (totalSaved, todaysTarget, isSavedToday, timeUntilLock, isSavingEnabled,
    streakDays, showDisablingConfirmation, mpesaNumber, chargeAmount, paymentStatus, paymentPhoneError)
    + `PaymentStatus` sealed interface (Idle/Requesting/WaitingForSTK/Success/Failed/Timeout).
  - `SettingsUiState` (dailySavingsAmount, lockScheduleTime "HH:mm", reminderLeadHours: List<Int>,
    mpesaNumber, distractionApps: List<DistractionApp(packageName,name,isRestricted)>,
    isSavingEnabled, showGenerateRecoveryWarning, amountError, mpesaError, themeMode).
  - `HistoryUiState` (historyItems: List<HistoryItem(date,targetAmount,savedAmount,status)>,
    trendData: List<Float>); `HistoryStatus` = Saved/Missed/RecoveryUsed.
  - `RecoveryUiState` (codes: List<RecoveryCode(code,isUsed)>, enteredCode, codeValidationError,
    codeValidationSuccess). NOTE: real codes are hashed — the list screen shows masked/used state only.
  - `LockOverlayUiState` (amountDue, deadlinePassed, bannerMessage).
- Phone number format enforced by UI regex: `^2547\d{8}$`.
- Recovery code display format: `XXXX-XXXX` (UI shows `SLxx-xxxx` style).

**Progress later on 2026-07-03**
- ✅ Gradle slimmed: removed Firebase/Gemini/App-Check/secrets & google-services plugins; added
  WorkManager (`androidx.work:work-runtime-ktx` 2.9.1). Decided to SKIP security-crypto (per-code
  PBKDF2 salt is enough — keeps it light).
- ✅ Room data layer built & committed (74b5f0c):
  - Entities: `SavingsConfigEntity` (single row id=0, holds distractionApps as JSON via Moshi),
    `SavingsLogEntity` (PK = date "yyyy-MM-dd", status enum), `RecoveryCodeEntity` (hash+salt+masked).
  - `Converters` (CSV for List<Int>, Moshi for apps, enum-by-name), 3 DAOs, `SaveLockDatabase`.
  - `SaveLockRepository` exposes Flows: config, logs, todayLog, totalSaved, streak, recoveryCodes;
    suspend writers for every config field + markSavedToday/markRecoveryUsedToday/markMissed +
    redeemRecoveryCode (offline).
  - `RecoveryCodeManager` (offline PBKDF2WithHmacSHA256, 120k iters, unambiguous alphabet, XXXX-XXXX).
  - `ServiceLocator` (manual DI) + `SaveLockApplication` (registered in manifest android:name) seeds
    config + inits notification channels at startup.
- Decision: recovery codes are NOT auto-generated at startup (user must see them once) — generation
  handled at the Recovery screen / Settings action in the next step.

**Progress (continued, 2026-07-03)**
- ✅ Pushed `main` + `feature` to GitHub (owner logged into gh as wazimuautomate).
- ✅ Created env templates: `backend/.env.example` (Daraja + Supabase server secrets) and
  `app/savelock.properties.example` (backend URL + APP_BACKEND_KEY) → wired into `BuildConfig`
  via gradle (`savelockProp`). Real `app/savelock.properties` is gitignored.
- ✅ Two lock modes decided & scaffolded: `LockMode.CHOSEN_APPS` vs `FULL_LOCKDOWN`. Owner chose
  FULL_LOCKDOWN = block everything incl. Settings; home shows lock screen only (Call/Messages).
  Hard rule: emergency calls + messaging never blockable; escape via pay/recovery/Safe Mode.
- ✅ Migrated ALL 5 ViewModels + MainActivity to repository Flows (commit 3a354ca). Added
  `SaveLockViewModels.Factory`. Added Lock Strictness selector to Settings screen. Dashboard
  payment still SIMULATED (writes real log on success) until task 10.

**Known small limitation to revisit**
- `repository.todayLog`/`totalSaved` compute "today" once at repo-singleton creation. Fine for daily
  restarts; revisit if we need live midnight rollover.

**✅ CI IS GREEN (build succeeds, APK produced)** — commit cc47377, run 28627754372.
- Working toolchain for CI: **JDK 21 + Gradle 9.3.1** (AGP 9.1.1 requires ≥9.3.1). Workflow
  `.github/workflows/android-build.yml` generates the wrapper (repo has none) then `assembleDebug`.
- Fixes that made it build: (1) Gradle 9.3.1, (2) removed custom `debugConfig` signing → debug uses
  AGP default keystore, (3) `fallbackToDestructiveMigration(dropAllTables = true)`.
- The KSP `AWT-EventQueue-0 NullPointerException` in logs is a NON-FATAL KSP2 shutdown quirk; ignore.
- ALL Kotlin compiled clean (data layer + VMs + screens). APK downloadable from GitHub → Actions →
  run → Artifacts → `savelock-debug-apk`.

**Next up (core engine — none built yet)**
- ⏳ Recovery wiring (read RecoveryCodesScreen + HistoryScreen first — still not read).
- ⏳ Scheduling (AlarmScheduler/LockCheckReceiver/BootReceiver/ReminderWorker), foreground service,
  accessibility soft-lock + LockOverlayActivity (two modes), device-admin, payment, Supabase backend,
  manifest permissions/registrations.
- ⚠️ Will need Supabase project URL + APP_BACKEND_KEY at payment stage; Daraja creds go only into
  Supabase env vars.
- After each new native piece, keep pushing so CI compile-checks it.

**Open questions / watch-outs**
- Need owner's Supabase project details (URL + anon key) before the app can call the backend —
  will ask when we reach payment wiring. Daraja credentials go only into Supabase env vars.
