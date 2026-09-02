# ProPortion Phase 7 — Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ProPortion stops being a working build and becomes a finished v1: nothing left in English
by accident, nothing invisible to TalkBack, the two moments the design spec singles out for motion
actually animate, the two documentation folders the spec promises exist with real screenshots, and
a release build that installs somewhere other than this laptop.

**Architecture:** This phase touches presentation and documentation, not the domain. No new module,
no new screen, no new repository method. Every task is either a small, targeted code change inside
an existing file, or a documentation folder filled in from what the app already does — there is
nothing left to design, only to finish and verify.

**Tech Stack:** Compose animation APIs (`AnimatedVisibility`, `AnimatedContent`), Android's
`AppCompatDelegate`/`localeConfig` for per-app language (already wired in phase 1–6, verified not
re-plumbed here), Gradle signing config, GitHub Actions.

**Spec:** `docs/private/specs/2026-09-01-proportion-v1-design.md` (§10 Localisation, §11
Documentation and repository layout, §14 Testing and CI, §15 phase 7)

## Global Constraints

- Package root `com.ilsecondodasinistra.proportion`; author Marco Zanetti; **never mention his
  employer** anywhere, including in `docs/public` or the README.
- **Never `git commit` or `git push`** — Marco does that himself.
- No hardcoded user-facing strings: every new or changed string goes in `values/strings.xml`
  (English) **and** `values-it/strings.xml` (Italian), same key in both.
- No arithmetic in composables — this still applies to the animation task: a transition between two
  already-formatted strings is not arithmetic; computing an interpolated number in Compose would be.
- minSdk 26, compileSdk/targetSdk 36, JVM target 17. detekt `maxIssues: 0`.
- `docs/public` and `docs/manual` are Italian-first: Italian is the source language, English is the
  reference translation, and every file that exists in `it/` must have an identically-named sibling
  in `en/` (§11). `docs/private` stays English only.
- Full check, from the repo root, JDK 21:
  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  ./gradlew verifyAll
  ```

## What phase 6's walkthrough already found and closed

Two gaps phase 6 surfaced were fixed on 2026-09-02, before this plan was written: the missing
"set as default" checkbox in the save-scaling dialog, and the double-cook-count bug. Both are done;
neither is a phase 7 task. See `docs/private/IMPLEMENTATION-STATUS.md` for the detail.

## What this plan is based on — a direct audit of the current tree (2026-09-02)

Before writing tasks, the app was checked directly rather than assumed clean:

- **Translations are essentially complete already** — a scripted key-parity check across every
  `values/strings.xml` / `values-it/strings.xml` pair in the repo found zero mismatches, and zero
  plural-key mismatches. Six string values are byte-identical between English and Italian, and all
  six are legitimately identical (`ProPortion`, `Home`, `Volume`, the author line, a `%d / %d`
  format string, `Home` again) — not untranslated placeholders.
- **Two real hardcoded strings exist**: `Text("OK")` appears twice in
  `feature/settings/src/main/kotlin/.../RestoreDialogs.kt` (the restore-done and restore-failed
  dialogs), never routed through `stringResource`.
- **23 icons carry `contentDescription = null`.** Most sit beside a visible text label (legitimately
  decorative), but several are the *only* affordance on their button — the search field's leading
  icon, the ingredient-filter "tune" icon, three overflow (`MoreVert`) buttons, a sort icon, and the
  cook screen's `+`/`−` servings steppers — and TalkBack currently has nothing to say about any of
  them.
- **`ProPortionMotion.QUANTITY_COUNT_MILLIS` and `BADGE_ENTER_MILLIS` are declared and documented
  ("numbers counting to their new value when the scale changes", "a warning badge arriving") but
  used nowhere.** `CookLineRow` (`feature/cook/.../CookScreen.kt`) swaps `scaledText` instantly on
  every keystroke; `WarningRow` (three call sites, all in `feature/cook`) appears and disappears
  with no transition at all.
- **`docs/public/{it,en}` and `docs/manual/{it,en}` exist as empty directories.** Nothing to migrate,
  nothing to reconcile — they are genuinely unwritten.
- **`README.md` is stale**: it still says "Under construction. Phases 1 and 2 are done" and its build
  commands predate the `verifyAll`/`testAll` aggregate tasks phase 6 added.
- **`.github/workflows/ci.yml` hand-lists which modules' tests to run** (`testDebugUnitTest
  :core:model:test :core:domain:test`) — exactly the staleness `testAll` was built to prevent, and
  it has already drifted: `:core:transfer:test` is missing from that list.
- **The release build type has no signing config.** `isMinifyEnabled = true` and the ProGuard files
  are wired, but `assembleRelease` cannot produce a distributable APK without one.
- `docs/private/localization.md` and `docs/private/release-checklist.md`, both named in the spec's
  §11 file tree, do not exist yet.

---

## File structure

```
feature/settings/src/main/kotlin/.../RestoreDialogs.kt   two Text("OK") -> stringResource
feature/settings/src/main/res/values{,-it}/strings.xml   + settings_restore_ok
core/ui/src/main/kotlin/.../component/WarningRow.kt       wrapped in AnimatedVisibility
feature/cook/src/main/kotlin/.../CookScreen.kt            CookLineRow's scaledText in AnimatedContent
core/ui, feature/*                                        contentDescription audit (see task 2)
docs/public/{it,en}/                                       what the app is, screenshots, privacy, changelog
docs/manual/{it,en}/                                       step-by-step manual, real screenshots
docs/private/localization.md                              new
docs/private/release-checklist.md                         new
README.md                                                  refreshed status + build commands
.github/workflows/ci.yml                                   uses verifyAll instead of a hand list
app/build.gradle.kts, app/keystore.properties.example      release signing config (mechanism only)
```

Rationale: the code tasks (1–3) are independent of each other and of the documentation tasks, so
they can run in any order; the documentation tasks (4–5) depend on the app being visually final,
which it is as of phase 6, but benefit from running after tasks 1–3 so the screenshots show the
finished animations and corrected strings.

---

## Task 1: Close the last translation and CI-hygiene gaps

**Files:**
- Modify: `feature/settings/src/main/kotlin/com/ilsecondodasinistra/proportion/feature/settings/RestoreDialogs.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`, `values-it/strings.xml`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Test: `feature/settings/src/test/kotlin/com/ilsecondodasinistra/proportion/feature/settings/*` (existing tests, re-run only — no new ones needed for a string swap, but the CI change needs proving)

**Interfaces:**
- Consumes: `stringResource` (existing pattern in the same file).
- Produces: nothing new — this task removes a defect, it does not add API surface.

- [ ] **Step 1:** In `RestoreDialogs.kt`, add `<string name="settings_restore_ok">OK</string>` to
  `values/strings.xml` and `<string name="settings_restore_ok">OK</string>` to `values-it/strings.xml`
  ("OK" is one of the identical-by-design cases — it is not an English word borrowed into Italian
  UI copy, it is used identically in both languages — so an identical translation here is correct,
  unlike the two `Text("OK")` calls it replaces, which were never resources at all).
- [ ] **Step 2:** Replace both `Text("OK")` calls (the `RestoreStep.Done` and `RestoreStep.Failed`
  dialogs) with `Text(stringResource(R.string.settings_restore_ok))`.
- [ ] **Step 3:** Run the scripted parity check that found the earlier gaps, so it is repeatable
  rather than a one-off manual audit:
  ```bash
  for en in $(find . -path "*/res/values/strings.xml" -not -path "*/build/*"); do
    it="${en/values\/strings.xml/values-it/strings.xml}"
    diff <(grep -o 'name="[^"]*"' "$en" | sort -u) <(grep -o 'name="[^"]*"' "$it" | sort -u) \
      && true || echo "MISMATCH: $en"
  done
  ```
  Expected: no output. Put this loop (or an equivalent Gradle task, implementer's choice — a plain
  shell script committed at `scripts/check-string-parity.sh` is enough, no need to wire it into
  Gradle) somewhere it can be re-run before every release; mention it in
  `docs/private/release-checklist.md` (Task 6).
- [ ] **Step 4:** In `.github/workflows/ci.yml`, replace the "Unit tests" step's
  `./gradlew testDebugUnitTest :core:model:test :core:domain:test --stacktrace` with
  `./gradlew testAll --stacktrace` (the aggregate task phase 6 added, which already asks every
  module for whichever test task it actually has). Keep the "Static analysis" and "Assemble" steps
  as they are, or fold them into one `./gradlew verifyAll --stacktrace` step if that reads more
  clearly — either is acceptable, but do not reintroduce a hand-maintained module list.
- [ ] **Step 5:** Refresh `README.md`'s "Status" section to say what is actually true — phases 1
  through 6 are done, phase 7 (this one) is in progress — and update its "Building" section's
  example commands to include `verifyAll`/`testAll` alongside the existing ones, matching
  `docs/private/IMPLEMENTATION-STATUS.md`'s wording rather than inventing new phrasing.
- [ ] **Step 6:** `./gradlew :feature:settings:testDebugUnitTest detekt`. All existing
  `RestoreDialogs`-adjacent tests must still pass — a `Text("OK")` swapped for a `stringResource`
  changes nothing a test asserts on unless a test literally matched the string "OK" by text; if one
  does, update it to match the resource's value instead of hardcoding "OK" a second time in the test.

---

## Task 2: Accessibility pass — content descriptions, large text, TalkBack

**Files:**
- Modify: `feature/settings/src/main/kotlin/.../SettingsScreen.kt`
- Modify: `feature/shopping/src/main/kotlin/.../ShoppingScreen.kt`
- Modify: `feature/cook/src/main/kotlin/.../CookScreen.kt`, `CookingModeScreen.kt`
- Modify: `feature/recipes/src/main/kotlin/.../list/RecipeListScreen.kt`, `.../detail/RecipeDetailScreen.kt`
- Modify string files in each touched module (`values/strings.xml`, `values-it/strings.xml`)
- Test: a Compose test per touched screen asserting the new content descriptions exist, added to
  that screen's existing `*ScreenTest.kt`

**Interfaces:**
- Consumes: nothing new — `Icon(imageVector, contentDescription = ...)` is the existing Compose API.
- Produces: nothing new — this task assigns real values to an existing, already-used parameter.

- [ ] **Step 1: Find every icon-only affordance.** Run:
  ```bash
  grep -rn "contentDescription = null" --include="*.kt" feature core | grep -v "/test/"
  ```
  For each of the 23 hits, judge it against one rule: if the icon sits next to visible text that
  already says what the control does (a `ListItem`'s `leadingIcon` beside a `headlineContent` with
  the same meaning, e.g. "Restore from a backup" next to a download icon), `null` is correct and
  stays — do not add a redundant description that would make TalkBack read the same thing twice.
  If the icon is the *only* content of its clickable surface, it needs a real description. At
  minimum, this list needs one:
  - `RecipeListScreen.kt`'s search field leading icon (`Icons.Filled.Search`) — the field already
    has a placeholder, but a leading icon inside a text field is still commonly announced alone by
    TalkBack; verify on-device in Step 4 whether it needs one once the placeholder is in place, and
    add `content_description_search` if so.
  - `RecipeListScreen.kt`'s ingredient-filter icon (`Icons.Filled.Tune`) — no adjacent text.
  - `RecipeListScreen.kt`'s sort icon (`Icons.AutoMirrored.Filled.Sort`) — no adjacent text.
  - `RecipeDetailScreen.kt`'s overflow (`Icons.Filled.MoreVert`) and `ShoppingScreen.kt`'s overflow —
    both icon-only.
  - `CookScreen.kt`'s servings stepper `Icons.Filled.Remove` / `Icons.Filled.Add` — icon-only
    buttons that change a number with no visible label of their own.
  Use your own judgement for the rest of the 23 — the list above is the minimum, not the ceiling.
- [ ] **Step 2:** For each icon that needs one, add a string resource (e.g.
  `content_description_filter_by_ingredient`, `content_description_sort`,
  `content_description_more_actions`, `content_description_decrease_servings`,
  `content_description_increase_servings`) in that module's `values/strings.xml` **and**
  `values-it/strings.xml`, and pass it as `contentDescription = stringResource(R.string.…)`.
- [ ] **Step 3:** Add one assertion per new description to that screen's existing Compose test file,
  using `onNodeWithContentDescription(...)` rather than a fresh test file — these are one-line
  additions to tests that already render the screen.
- [ ] **Step 4: On-device TalkBack and large-text check** (Fairphone 3). Enable TalkBack
  (Settings → Accessibility → TalkBack) and swipe through: Home → a recipe → Cook this recipe →
  cooking mode → back → Shopping → Settings. Note anything TalkBack reads as silence, "button", or
  a raw icon name, and fix it before moving on — that is a real miss the grep in Step 1 will not
  have caught (a `Modifier.clickable` with no semantics, for instance). Then set the system font
  size to the largest available "Extra large" step (Settings → Display → Font size) and repeat the
  same walk: nothing should truncate mid-word, clip behind another element, or push the primary
  action off-screen without scrolling. Record what you found and fixed in
  `docs/private/IMPLEMENTATION-STATUS.md` under this task's line, the same way phase 6's task 9 did.
- [ ] **Step 5:** `./gradlew verifyAll`.

---

## Task 3: The two animations the design system already promises

**Files:**
- Modify: `core/ui/src/main/kotlin/com/ilsecondodasinistra/proportion/core/ui/component/WarningRow.kt`
- Modify: `feature/cook/src/main/kotlin/com/ilsecondodasinistra/proportion/feature/cook/CookScreen.kt`
  (the `CookLineRow` private composable and its three `WarningRow(...)` call sites)
- Modify: `feature/cook/src/main/kotlin/com/ilsecondodasinistra/proportion/feature/cook/ScaledCardBody.kt`
  (its `WarningRow(...)` call site)
- Test: a Compose test in `CookScreenTest.kt` asserting a warning's appearance and disappearance
  still leaves the right text visible once any transition settles (Compose test rules run
  animations to completion by default, so this is a correctness check, not a timing check)

**Interfaces:**
- Consumes: `ProPortionMotion.QUANTITY_COUNT_MILLIS` (420), `ProPortionMotion.BADGE_ENTER_MILLIS`
  (220), both in `core/designsystem/src/main/kotlin/.../theme/ProPortionMotion.kt` — already defined,
  never imported by any of these files today.
- Produces: no new public API. `WarningRow` keeps its exact current signature — the animation wraps
  its call sites' visibility, not the composable's parameter list, so nothing that already calls
  `WarningRow` needs to change beyond what this task touches.

Two real amounts to animate, and one honest constraint on how: by the time a scaled quantity
reaches Compose it is already a formatted `String` (`CookLine.scaledText`, e.g. `"640 g"`), not a
`Double` — computing an interpolated number inside the composable to draw a digit-rolling counter
would put arithmetic in the UI layer, which the project's rules forbid. The animation this task
builds is a **transition between two already-computed strings** (a subtle slide/fade when the text
changes), not a numeric count-up. That still delivers the design system's intent — a rescale reads
as something happening rather than a value silently snapping — without moving any arithmetic out of
`:core:domain`.

- [ ] **Step 1: Failing Compose test first.**

```kotlin
@Test
fun `a warning appears and disappears without leaving stale text behind`() {
    val withWarning = baseState.copy(
        lines = listOf(lineOf(baseState, "Farina")),
        error = null,
    ).let { state ->
        state.copy(lines = state.lines.map { it.copy(hasWarning = true, warningText = "Arrotonda a 1 uovo") })
    }

    render(withWarning)
    composeTestRule.onNodeWithText("Arrotonda a 1 uovo").assertIsDisplayed()

    render(withWarning.copy(lines = withWarning.lines.map { it.copy(hasWarning = false, warningText = null) }))
    composeTestRule.onNodeWithText("Arrotonda a 1 uovo").assertDoesNotExist()
}
```

  Adjust the state-building to whatever `CookUiState`/`CookLine` actually require (read the top of
  `CookScreenTest.kt` for `baseState` and `lineOf` — this plan does not repeat their exact shape
  since the test file already defines it, and duplicating it here risks drifting from the real
  fields). The point of the test is behavioural: a warning that was showing and stops must not
  leave its text behind mid-animation once Compose's idle-wait settles.

- [ ] **Step 2: Run it, confirm it already passes today** (there is no animation yet, so visibility
  is instant and this specific assertion should already hold) — this test's purpose is to survive
  the animation you are about to add, not to fail first. Note in the task report that this is a
  guard test, not a red-green TDD step, and why: the current behaviour is already correct, only
  un-animated.

- [ ] **Step 3: Wrap `WarningRow`'s three call sites in `AnimatedVisibility`.** In
  `CookScreen.kt`'s `CookLineRow` (the per-line warning) and `ScaledCardBody.kt`'s oven-advisory
  warning:

```kotlin
AnimatedVisibility(
    visible = /* the existing boolean condition that today gates whether WarningRow is called at all */,
    enter = fadeIn(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
        expandVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
    exit = fadeOut(tween(ProPortionMotion.BADGE_ENTER_MILLIS)) +
        shrinkVertically(tween(ProPortionMotion.BADGE_ENTER_MILLIS)),
) {
    WarningRow(/* unchanged arguments */)
}
```

  Read each call site's surrounding `if`/`when` first — some are already inside a conditional block
  (`if (line.hasWarning) { WarningRow(...) }`), in which case the condition moves onto
  `AnimatedVisibility`'s `visible` parameter and the surrounding `if` is removed, rather than
  nesting an `if` inside an always-composed `AnimatedVisibility`.

- [ ] **Step 4: Animate `CookLineRow`'s quantity text.** Wrap the `Text(text = line.scaledText, ...)`
  call in `AnimatedContent`:

```kotlin
AnimatedContent(
    targetState = line.scaledText,
    transitionSpec = {
        (fadeIn(tween(ProPortionMotion.QUANTITY_COUNT_MILLIS)) +
            slideInVertically(tween(ProPortionMotion.QUANTITY_COUNT_MILLIS)) { it / 4 })
            .togetherWith(fadeOut(tween(ProPortionMotion.QUANTITY_COUNT_MILLIS)))
    },
    label = "scaled_quantity",
) { text ->
    Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
}
```

  Keep the existing `testTag`/`Modifier` on the surrounding row exactly where it is now — the
  `AnimatedContent` wraps only the `Text`, so anything asserting on the row's test tag is
  unaffected; anything asserting on the text's own tag (if one exists) needs that tag to move onto
  the `Text` inside the lambda, not onto `AnimatedContent` itself.

- [ ] **Step 5:** Re-run the Step 1 test and the rest of `CookScreenTest.kt` and
  `ScaledCardBodyTest.kt` (if one exists — check) unchanged:
  ```bash
  ./gradlew :feature:cook:testDebugUnitTest :core:ui:testDebugUnitTest detekt
  ```
  All must still pass. Compose's test clock advances animations to completion automatically inside
  `composeTestRule` blocks, so no test needs an explicit wait.
- [ ] **Step 6: Verify by eye on the device.** Change servings a few times on the scale screen and
  watch the quantities transition instead of snapping; trigger a warning (e.g. a fractional egg)
  and watch it fade in rather than appear instantly. This is the one part of this task no test can
  confirm — say in the report whether it read as intended or too slow/fast, and adjust the
  millisecond constants in `ProPortionMotion.kt` if 420 ms/220 ms feel wrong once seen moving,
  updating both the constant and its doc comment if you do.

---

## Task 4: `docs/public` — what the app is, in Italian and English

**Files:**
- Create: `docs/public/it/README.md`, `docs/public/en/README.md`
- Create: `docs/public/it/privacy.md`, `docs/public/en/privacy.md`
- Create: `docs/public/it/changelog.md`, `docs/public/en/changelog.md`
- Modify: `docs/private/IMPLEMENTATION-STATUS.md` (link these from the "Read next" list)

**Interfaces:** None — this is prose, not code.

Italian is the source language (§11): write the Italian file first, then translate it into
`en/` under the identical filename. A missing English sibling is a defect, not a TODO.

- [ ] **Step 1: `README.md` (per language).** What ProPortion is, in plain language: enter a recipe
  once, rescale it four ways (by servings, by fixing one ingredient, by a plain multiplier, by what
  is actually in the cupboard), share it as text or as a `.proportion` file, back the whole library
  up. Pull the actual feature list from `docs/private/architecture.md` and
  `docs/private/specs/2026-09-01-proportion-v1-design.md` rather than re-describing from memory — a
  public doc that oversells a feature the app does not have is worse than a short one. Include at
  least three screenshots taken from `docs/private/screenshots/` (or fresher ones captured for this
  task): the Home dashboard, the scale screen mid-rescale, and cooking mode. State the licence
  (`LICENSE` at the repo root — GPLv3) and that the app is offline-only with no account and no
  tracking. **Never name Marco's employer.**
- [ ] **Step 2: `privacy.md` (per language).** Short and factual, matching what the code actually
  does: no network access beyond what Android itself requires, no analytics, no account, all data
  in one on-device Room database, a `.proportion` export is written only when the user explicitly
  shares or backs up, nothing is ever sent anywhere by the app itself. Do not write boilerplate
  privacy-policy legalese that promises things (a contact email, a data-retention period) the
  project has no mechanism to honour — if a claim would need infrastructure that does not exist,
  leave it out rather than invent it.
- [ ] **Step 3: `changelog.md` (per language).** One entry, "1.0", dated with today's date, listing
  what shipped: recipe entry and browsing, four scaling modes with the oven advisory, sharing and
  backup, the dashboard, the shopping list, cooking mode. Pull the list from
  `docs/private/IMPLEMENTATION-STATUS.md`'s completed phases rather than reconstructing it from
  git history — the status file is already the authoritative "what's done" record.
- [ ] **Step 4:** Read both language versions of each file side by side and confirm they say the
  same thing — a translation that adds or drops a claim is a defect in a public-facing document
  more than it would be anywhere else in the codebase, since this is what a stranger reads to decide
  whether to trust the app.

---

## Task 5: `docs/manual` — the step-by-step manual, with real screenshots

**Files:**
- Create: `docs/manual/it/manuale.md`, `docs/manual/en/manual.md`
- Create: `docs/manual/it/screenshots/`, `docs/manual/en/screenshots/` (or one shared screenshot
  folder referenced by both language files — implementer's call, but state the choice and keep it
  consistent; do not create screenshots in two places with the same content)

**Interfaces:** None.

The spec is explicit about what this document is for: "a worked example (a recipe entered, then
rescaled by servings, by a fixed ingredient, and by what is in the cupboard)" with screenshots
"taken from a physical device, not mockups."

- [ ] **Step 1: Capture fresh screenshots on the Fairphone 3**, after tasks 1–3 land, so they show
  the corrected strings and the new animations' settled state (a screenshot cannot show motion, but
  it should not show an obviously-mid-transition frame either — screenshot after the UI has
  settled). Reuse the adb pattern from phase 6's walkthrough:
  ```bash
  adb shell screencap -p /sdcard/x.png && adb pull /sdcard/x.png docs/manual/<lang>/screenshots/
  ```
  Capture, at minimum: the empty Home state, Home with a populated dashboard, the recipe editor,
  the recipe list with a filter applied, the recipe detail, all four scale-screen modes, the oven
  advisory banner, cooking mode, the shopping list, and the settings/backup screen.
- [ ] **Step 2: Write the Italian manual first.** Structure it as one section per flow, each with
  numbered steps and an inline screenshot:
  1. Enter a recipe (a full walkthrough of the editor: title, servings, tags, ingredients, steps).
  2. Find it again (search, tag filter, ingredient filter).
  3. Rescale by servings.
  4. Rescale by fixing one ingredient ("I only have 2 eggs").
  5. Rescale by what is in the cupboard (pantry mode, including reading a leftover).
  6. Save a scaling, and set one as the recipe's default.
  7. Cook it (cooking mode: the always-on screen, checking off steps).
  8. Share a recipe as text and as a file; receive one back.
  9. Back up and restore the whole library.
  10. The dashboard and the shopping list.
  Use one worked example recipe throughout (a simple one, e.g. a risotto or a cake) so the reader
  follows the same recipe from entry through to cooking rather than a new example per section.
- [ ] **Step 3: Translate into English**, same structure, same screenshots (screenshots are
  language-agnostic since the app itself is captured in whichever language the device was set to —
  if captured in Italian, note in the English manual that the screenshots show Italian UI text, or
  recapture the walkthrough with the device's language set to English; state which approach was
  taken and why in the task report).
- [ ] **Step 4:** Cross-check both manuals describe the same ten flows in the same order — this is
  the artefact a real user other than Marco is most likely to actually read, so a gap here is a
  real gap.

---

## Task 6: Remaining `docs/private` pieces and the release checklist

**Files:**
- Create: `docs/private/localization.md`
- Create: `docs/private/release-checklist.md`
- Modify: `docs/private/IMPLEMENTATION-STATUS.md` (link both from "Read next")

**Interfaces:** None.

- [ ] **Step 1: `localization.md`.** Document the mechanism, not a restatement of the spec: where
  strings live (per-module `values/` and `values-it/`), the rule that a key must exist in both, the
  scripted parity check from Task 1 Step 3 (`scripts/check-string-parity.sh`) and when to run it,
  how built-in tag names are resolved (`builtInTagLabelRes` in `core/ui`, never translated for user
  tags), and how a third language would be added (a new `values-<lang>/` per module, a new
  `docs/public/<lang>/` and `docs/manual/<lang>/` pair, the `localeConfig` entry in the manifest).
- [ ] **Step 2: `release-checklist.md`.** A concrete, ordered list a future release actually follows:
  run `./gradlew verifyAll`, run the string-parity script, bump `versionCode`/`versionName` in
  `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`, confirm
  `docs/public/changelog.md` (both languages) has an entry for the new version, build
  `./gradlew assembleRelease` and confirm it produces a signed APK (Task 7), install it on a real
  device and walk the ten flows from `docs/manual`, tag the release.
- [ ] **Step 3:** Link both new files from `docs/private/IMPLEMENTATION-STATUS.md`'s "Read next"
  list, in the same style as the existing links.

---

## Task 7: Release signing — the mechanism, not a keystore

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/keystore.properties.example`
- Modify: `.gitignore` (ensure `keystore.properties` and `*.jks`/`*.keystore` are ignored — check
  first, several Android `.gitignore` templates already cover this)

**Interfaces:**
- Produces: `android.signingConfigs.release`, read from `app/keystore.properties` if that file
  exists, falling back to no signing config (an unsigned `assembleRelease` output, exactly today's
  behaviour) if it does not — this task must not require a keystore to exist for the build to keep
  working, since none exists yet and creating one is Marco's decision, not something to generate
  unattended.

- [ ] **Step 1:** Check `.gitignore` for `*.jks`, `*.keystore`, `keystore.properties` — if any is
  missing, add it. **Do not create, generate, or ask for an actual keystore or its passwords** —
  this task wires the plumbing so that when Marco creates one (via Android Studio's "Generate
  Signed Bundle/APK" or `keytool`), dropping `keystore.properties` next to `app/build.gradle.kts`
  is all that is needed. Creating a signing key is a decision with long consequences (losing it
  means every future update needs a new `applicationId`) that is his to make outside this task.
- [ ] **Step 2:** Add to `app/build.gradle.kts`, before the `android { }` block or inside it per
  Gradle Kotlin DSL convention:

```kotlin
val keystoreProperties = java.util.Properties().apply {
    val file = rootProject.file("app/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    // ...existing namespace, buildTypes...
    if (keystoreProperties.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

- [ ] **Step 3:** Create `app/keystore.properties.example` (committed, template only — no real
  values):
  ```properties
  storeFile=/absolute/path/to/your.jks
  storePassword=changeme
  keyAlias=proportion
  keyPassword=changeme
  ```
- [ ] **Step 4:** Verify the fallback path still works with no keystore present:
  ```bash
  ./gradlew assembleRelease
  ```
  Expected: `BUILD SUCCESSFUL`, producing an unsigned release APK exactly as before this task —
  confirming the conditional wiring does not break the existing build when the file is absent.
- [ ] **Step 5:** `./gradlew verifyAll` — the whole build, one more time, to close the phase.

---

## Self-review notes

- **Spec coverage:** §10 localisation → Task 1 (the two real hardcodes) + Task 6 (`localization.md`)
  — the translation *content* itself needed no work, since the audit found it already complete.
  §11 documentation layout → Tasks 4, 5, 6. §14/CI → Task 1 Step 4. §15's "accessibility (TalkBack,
  large text)" → Task 2. §15's "final animations" → Task 3. §15's "release preparation" → Tasks 6–7.
- **Deliberately scoped down:** this plan does not create a real signing keystore, does not publish
  to any store or distribution channel (none is named anywhere in the spec or the repo), and does
  not invent app-store metadata (screenshots for a listing, a privacy-policy URL) beyond what
  `docs/public` itself provides — nothing in the project states an intended distribution channel
  beyond "share the APK/repo," so building for one would be scope invented past what was asked.
- **Type/name consistency:** `ProPortionMotion.QUANTITY_COUNT_MILLIS` and `BADGE_ENTER_MILLIS`,
  `CookLine.scaledText`, `WarningRow`'s existing parameters, and `testAll`/`verifyAll` are all used
  here exactly as they already exist in the codebase (verified by reading each file directly before
  writing the task that touches it) — nothing in this plan renames or reshapes an existing symbol.
