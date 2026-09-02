# Release checklist

Run in order. Every step should genuinely pass before moving to the next one — none of these
substitute for another.

1. **`scripts/check-string-parity.sh`** — confirms every module's `values/strings.xml` and
   `values-it/strings.xml` declare the same set of keys. Run it explicitly even though `verifyAll`
   doesn't call it yet; it's instant and catches a half-translated screen before a slower build
   does. See `docs/private/localization.md` for what it does and doesn't check.
2. **`./gradlew verifyAll`** — detekt and lint across every module, every unit test (JVM and Android
   alike, via the generated `testAll`), and a debug APK build (`:app:assembleDebug`). This is the
   same gate CI runs; if it's red here it'll be red there.
3. **Bump the version.** `versionCode` and `versionName` are not in `app/build.gradle.kts` — they're
   set in `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`, inside
   `AndroidApplicationConventionPlugin`'s `defaultConfig` block (currently `versionCode = 1`,
   `versionName = "1.0"`). Bump both there.
4. **Update the changelog, both languages.** `docs/public/en/changelog.md` and
   `docs/public/it/changelog.md` already exist (with a `1.0` entry each) — add a new dated entry to
   both for the version being released, not a fresh file. Keep the two in sync the same way the
   strings are kept in sync: same content, translated.
5. **`./gradlew assembleRelease`.** As of Task 7's conditional signing config, this produces a signed
   APK when `local.properties` (repo root, already gitignored) carries all four of
   `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` — the
   keystore file itself lives wherever you keep it outside the repo, only its path goes in
   `local.properties` — and falls back to an unsigned APK exactly as before if any of the four is
   missing. Confirm which one you got before shipping it: an unsigned APK is fine for a local
   walkthrough, not for distribution.
6. **Install on a real device and walk all ten flows** in `docs/manual/en/manual.md` (or
   `docs/manual/it/manual.md`) end to end: enter a recipe; find it again; rescale by servings; by
   fixing one ingredient; by what's in the cupboard; save a scaling and set one as default; cook it;
   share as text and as a file, receive one back; back up and restore the library; the dashboard and
   the shopping list. A passing `verifyAll` does not substitute for this — it's the difference
   between "the code does what the tests say" and "the app does what a cook needs."
7. **Tag the release** once everything above is green, e.g. `git tag v<versionName>` against the
   commit that was actually built and walked. Marco tags and pushes himself.
