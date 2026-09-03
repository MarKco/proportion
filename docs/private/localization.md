# Localization

Italian and English at launch, structured so a third language is additive.

## Where strings live

Every module that has UI carries its own `src/main/res/values/strings.xml` (English, the default)
and `src/main/res/values-it/strings.xml` (Italian) side by side. There is no shared strings module —
each of `app`, `core/ui`, `feature/cook`, `feature/editor`, `feature/home`, `feature/recipes`,
`feature/settings`, `feature/shopping` owns its own pair. A key must exist in both files of a given
module; a key that exists only in `values/` silently falls back to English at runtime instead of
failing the build, which is exactly the kind of drift that goes unnoticed until a screen ships
half-translated.

## The parity check

`scripts/check-string-parity.sh` closes that gap mechanically: it walks every `*/res/values/strings.xml`
in the repo (skipping `build/`), diffs its set of `name="..."` attributes against the matching
`values-it/strings.xml`, and prints `MISMATCH: <path>` — either a missing Italian file or a set of
keys that don't match — for each module that has drifted, exiting non-zero if any did. It only checks
that the *same keys* exist on both sides; it does not check that a translation is non-empty, sensible,
or even different from the English string. Run it after touching any `strings.xml` and always as
part of the release checklist (`docs/private/release-checklist.md`), before `./gradlew verifyAll` —
it's instant and catches the mistake before a slower build does.

## Built-in tags vs. user tags

`builtInTagLabelRes` (`core/ui/src/main/kotlin/com/ilsecondodasinistra/proportion/core/ui/TagLabels.kt`)
maps the nine built-in tag keys (`appetizer`, `first_course`, `main_course`, `side_dish`, `dessert`,
`bread_and_leavened`, `preserves`, `drinks`, `oven`) to a `@StringRes` id; `tagLabel(tag)` resolves
through that map when the tag carries one of those keys, so a built-in tag's displayed name follows
the app's current language like any other string resource. A tag with no matching key — i.e. a
user-created tag — has no resource to resolve and falls through to `tag.name`, the literal text the
user typed: user tags are never translated, by construction, because there's no key to look one up
by.

## Per-app language selection

Settings has a language picker (System / Italiano / English) backed by `LocaleController`
(`core/domain/.../LocaleController.kt`, `currentTag()`/`setTag(tag)`) and
`SettingsViewModel.onLanguageChange`, implemented by `AppCompatLocaleController`
(`core/ui/.../AppCompatLocaleController.kt`). It uses two mechanisms together, because
`AppCompatDelegate.setApplicationLocales` alone does not take effect in the *running* process here
— its live-apply hooks target `AppCompatActivity`, and this app's Compose-only theme deliberately
isn't one (confirmed by a real crash during development: "You need to use a Theme.AppCompat theme
(or descendant) with this activity" — making `MainActivity` one just for this would need a
`Theme.AppCompat` parent for no other benefit):

- **API 33+**: `android.app.LocaleManager` is called directly — the same mechanism the device's own
  Settings > Apps > <App> > Language screen uses — proven on-device to update the running app
  immediately once the activity recreates.
- **Every API level**: `AppCompatDelegate.setApplicationLocales` is called too, purely so the choice
  persists (via its own `AppLocalesMetadataHolderService` storage) and is reapplied automatically on
  the next cold start. Below API 33 that persistence is the only effect: the language changes from
  the next app open, not immediately, since no platform API exists to apply it live pre-33.

The manifest's `android:localeConfig="@xml/locales_config"` (`app/src/main/res/xml/locales_config.xml`,
listing `en`/`it`) is what additionally exposes ProPortion in the *system* Settings > Apps > Language
screen on Android 13+ — a second, OS-level entry point onto the same underlying mechanism, not a
separate feature.

## Adding a third language

1. For every module listed above, add `src/main/res/values-<lang>/strings.xml` with the same key set
   as `values/strings.xml` — `scripts/check-string-parity.sh` only compares against `values-it`, so
   it would need a small extension (loop over every `values-<lang>` sibling it finds, not just `it`)
   to cover a third language automatically.
2. Add `<locale android:name="<lang>" />` to `app/src/main/res/xml/locales_config.xml`.
3. Add a `docs/public/<lang>/` and `docs/manual/<lang>/` pair, mirroring the existing `it`/`en`
   structure (`docs/public/it/`, `docs/public/en/`, `docs/manual/it/manual.md`,
   `docs/manual/en/manual.md`).
4. If the pre-33 in-app switch above gets built first, add the new language to its list of choices.
