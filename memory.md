# memory.md — SaveLock Running Work Log

This is my memory between sessions. I append a dated entry every time I do a chunk of work,
so I never lose track of what's done and what's next. Newest entries at the top.
For the rules, see [CLAUDE.md](CLAUDE.md).

---

## Legend
- ✅ done  · 🔄 in progress · ⏳ not started · ⚠️ needs owner action

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

**Next up**
- ⏳ Migrate the 5 ViewModels + MainActivity to consume the repository via ViewModel factories
  (keep the exact UiState contracts). Read `RecoveryCodesScreen.kt` first (not yet read).
- ⏳ Then scheduling, services, accessibility/overlay, device-admin, payment, backend.
- ⚠️ Owner to run `gh auth login` so I can push `feature` to GitHub.
- ⚠️ Will need: Supabase project URL + anon key (for the app) at payment stage; Daraja creds go only
  into Supabase env vars (never in the app).

**Open questions / watch-outs**
- Need owner's Supabase project details (URL + anon key) before the app can call the backend —
  will ask when we reach payment wiring. Daraja credentials go only into Supabase env vars.
