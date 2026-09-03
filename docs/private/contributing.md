# Contributing

## Building

JDK 21 (or Android Studio's bundled JBR) and the Android SDK with platform 36.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest :core:model:test :core:domain:test :core:transfer:test
./gradlew detekt lint
./gradlew installDebug        # with a device attached
```

`compileSdk`/`targetSdk` stay at 36 while `platforms;android-37` is absent from the stable SDK
channel; AndroidX versions are pinned to the last ones that allow compiling against 36.

## House rules

- **Tests first for anything in `:core:domain` or `:core:transfer`.** Those two modules are the
  reason the app is trustworthy.
- **No hardcoded user-facing strings.** `values/` is English, `values-it/` is Italian, and plurals
  go through `<plurals>`.
- **No arithmetic in composables.** Quantities come from `ScaledRecipe`.
- **Features do not depend on features.** Shared UI goes to `:core:ui`.
- **Routes stay type-safe.** No route strings, no `navArgument`.
- Run `./gradlew detekt` before pushing; the config in `config/detekt/detekt.yml` documents each
  deviation from the defaults and why it exists.

## Layout of the docs

- `docs/private` — this folder: `STATUS.md` (living checklist), `HISTORY.md` (phase-by-phase
  record), `ARCHITECTURE.md`, `DECISIONS.md`, plus `contributing.md`, `localization.md` and
  `release-checklist.md`.
- `docs/public` — what the app is, for users (Italian and English).
- `docs/manual` — the user manual with real screenshots (Italian and English).
