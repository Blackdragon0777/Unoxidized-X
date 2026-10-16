# NACHT X progress (v15)

## Done this session
- Build error fixed at the source: confirmed no stray `app/src/main/res/layout/*.xml` remains in the project (that file caused the AAPT `ic_menu_close_clear_cancel` error). `update.sh` also strips that folder on sync so it can't come back.
- New visual identity: original silver/steel emblem (two crossing blades in a ring) and an original cloaked-silhouette background, in a cold black/silver/steel-blue palette. I did not reproduce the Final Fantasy artwork you shared — that's copyrighted character art, so I drew new, unrelated designs in the same color mood instead.
- Theme colors (`Brand.acc` / `Brand.glow` / `Ln`) updated across every screen and the floating panel to the new palette.
- Floating panel: added a small watermark line ("NACHT X · Made by @UnoxidizedX"), and smoothed dragging by batching window-layout updates to the display's animation frame instead of firing on every touch event (removes the stutter/lag).

## Added in v17
- Per-feature Free/Premium switches: Owner > Server Control > Feature access lets you decide which of Graphics tuner, Game profiles, Session tracking, Import and export, Auto Boost need Premium. The apps read this from the control file on each check-in. (Device-side, not server-enforced.)
- Floating panel: live RAM/battery line, plus Game (launch target game), DND on/off (needs root or Shizuku), and Home buttons.

## Still not done (said plainly, not glossed over)
- **Real backend (Section 3 of Zyy.txt):** user accounts, pending-approval states, server-enforced roles that survive reinstall, and an Owner app that approves/suspends/revokes specific users — this needs a hosted service (Firebase/Supabase or similar) with a real database and authentication. Nothing in this project currently has that; access is still: (a) a 3-day free key checked on the phone, and (b) Owner-signed Premium keys. Both are device-side checks, not a server-enforced authority.
- I have **not** wired any Firebase/Supabase SDK into the build, because that requires your own project credentials (a `google-services.json` or project URL/keys) which I don't have and can't generate for you — adding the dependency without real credentials would break the build.
- Per-feature Free/Premium toggles configurable live from Owner (Section 4) are not built — today Premium is all-or-nothing (gated by `PremiumGate`).

## Next action (pick one, see my message)
1. I design the backend data model + Firebase project setup steps for you to create (needs your Google account, takes ~10 minutes), then I wire the app to read real approval/role state from it.
2. I keep building out the on-device feature set (more floating-panel tools, per-feature Free/Premium flags controlled from the Owner app's existing GitHub control file).

## v15 update
- Build: removed `app/src/main/java/com/shell/space/MainActivity.kt` (referenced AppCompat, R.layout.activity_main, FloatingPanelService, InformationActivity that do not exist, so it would fail compilation). Removed the duplicate `build_apks.yml` (Gradle 8.2, release/unsigned, ran on every push alongside build.yml). Kotlin 1.9.24 + Compose Compiler 1.5.14 was already set in `build.gradle.kts`.
- Branding: new logo (square crop, used by all `R.drawable.logo` call sites), portrait background `bg_char.jpg` (blurred fill, no stretch), `nachtx_banner.jpg` on Home (main) and Dashboard (owner), launcher icons (owner has a blue band), GCash QR on the Premium screen.
- Login: SheleSpace-style flow (device ID, register name/Gmail/Telegram, Submit ID to admin, Pending, Refresh status) on top of the existing signed-key system. Free 3-day access kept.
- Premium: plans/price/GCash number editable by the owner; proof form (reference, amount, date); statuses Pending / Approved / Rejected; manual owner approval only.
- Owner: Requests page (parse message, approve = signed device-bound key published as `grants`, reject, duplicate-reference block, plans editor).
- NOT done: Firebase/real database backend (google-services files were supplied but not wired: needs the Gradle plugin, Auth + Firestore setup and security rules, and cannot be tested here). Approval transport is the existing GitHub control file; it is not a live server.
- NOT verified: no Android SDK/Gradle in the authoring sandbox, so the code was not compiled or run. The GitHub Actions run is the first real test.

## v15.1 update
- BUILD FIX (confirmed from your GitHub log, run #23): `app/.../Tuner.kt:112:128 Unexpected tokens`. `boostPlus()` had `return } val s = Sys.snap(c); ...` on one line: a statement after a closing brace with no separator. Split onto its own line. (The log says Tuner.kt, not Timer.kt.) The same pattern was scanned for in every Kotlin file: no other occurrence. Kotlin reports all syntax errors in a build at once and listed only this one.
- The Owner module already compiled in that run (warnings only). Fixed the 4 warnings: `onCreate(savedInstanceState)`, 2 unused `g` variables, redundant `out` initializer.
- Workflow: only root `.github/workflows/build.yml` runs. `Staging/` is a stub with no Kotlin source and its workflow is never executed by GitHub (only the root `.github/workflows` counts); left untouched.
- Secrets moved out of source into GitHub Actions secrets (keystore, passwords, Owner key, Owner hash). Owner APK is uploaded password-protected on public repos.
- Server: Firebase Auth + Firestore over REST (no SDK, no Gradle plugin). Rules in `firestore.rules`, setup in `CLOUD_SETUP.md`. Owner > Accounts approves payments and accounts; the user's app picks the signed key up within ~10 s. GitHub control.json route kept as a fallback.
- Floating panel: silver theme, Premium/Free label, Premium Game Boost, Wi-Fi / Bluetooth / Airplane shortcuts.
- NOT verified: no compiler or Android SDK in the authoring sandbox, so only the GitHub run can confirm a green check.

## v15.2 update
- Workflow: the Owner zip password is now taken from the first line of the OWNER_ZIP_PASS secret with spaces/newlines removed (a pasted note line could make the real password longer than the one in secrets_to_add.txt). The log prints the lengths (never the value). Artifacts use normal zip compression instead of "stored" (stored zips with data descriptors fail CRC checks in some phone file managers). Run summary lists SHA-256 for both APKs.
- Floating panel redesigned as NACHT X DECK (new file `Deck.kt`, wired from `FloatingService.buildPanel`). Header with logo, Free/Premium label, clock and battery; favourite apps row; six tabs:
  PULSE (CPU / RAM / TEMP gauges, live HUD pill with FPS + temperature), QUICK (steady refresh lock, Purge RAM, game launch, settings shortcuts, transparency), COOL (temperature card, Cool / Balanced / Performance profiles),
  GUARD (Quiet Mode, No pop-ups, Match Mode Premium), AIM (crosshair overlay: dot / cross / ring, size, colour), LAB (Max FPS Premium, Purge RAM, Cooldown, Reset all, game target).
  Shell-based actions need root or Shizuku and only change normal Android settings. No fake "AI" tab. Not verified on a device.
