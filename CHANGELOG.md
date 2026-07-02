# Changelog

All notable changes to SaveLock are recorded here.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project uses date-based entries (personal, non-versioned build).

## [Unreleased]

### Added
- Project documentation: `CLAUDE.md` (rules + full project map), `memory.md` (running work log),
  this `CHANGELOG.md`, and a rewritten `README.md`.
- Git repository initialized with `main` + `feature` branches and the GitHub remote
  `wazimuautomate/SaveLock`. Hardened `.gitignore` so secrets/keystores/backend env files
  are never committed.

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
