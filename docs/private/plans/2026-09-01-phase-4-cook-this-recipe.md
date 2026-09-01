# ProPortion Phase 4 — Cook This Recipe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The feature the app exists for — fix one thing, rescale everything else, and say clearly when the result is not practical.

**Architecture:** A new `:feature:cook` module with one `CookViewModel` that owns the constraint and re-runs the already-tested `RecipeScaler` on every change. The screen has two faces driven by the same state — the adjust view (mode chips plus live list) and the scaled card (same layout as the recipe detail, new quantities) — so no constraint ever has to travel through a navigation route.

**Tech Stack:** Compose Material 3, type-safe Navigation Compose routes, Hilt, Coroutines/Flow, Turbine, Robolectric.

**Spec:** `docs/private/specs/2026-09-01-proportion-v1-design.md` (§5 engine, §7.4 scale screen)

## Global Constraints

- Package root `com.ilsecondodasinistra.proportion`; author Marco Zanetti; never mention his employer.
- **Never `git commit` or `git push`.** Update `docs/private/IMPLEMENTATION-STATUS.md` at the end of each task.
- minSdk 26, compileSdk/targetSdk 36, JVM target 17; build with JDK 21 or Studio's JBR.
- Navigation stays **type-safe**: `@Serializable` route classes, `composable<T>`, `savedStateHandle.toRoute<T>()`.
- No hardcoded user-facing strings; `values/` English, `values-it/` Italian, plurals where a count appears.
- The scaling engine is done and tested — this phase adds **no arithmetic**. If a rule seems missing, it belongs in `:core:domain` with its own test, not in a composable.
- Features never depend on other features; shared UI goes to `:core:ui`.

---

## File structure

```
feature/cook/
  CookUiState.kt            mode, raw inputs, scaled result, warnings, save-dialog state
  CookViewModel.kt          owns the constraint, re-runs RecipeScaler, saves variants
  CookScreen.kt             adjust view: mode chips + live list + warning rows
  ScaledCardBody.kt         the scaled card face, same shape as the recipe detail
  navigation/CookRouteKey.kt
core/ui/component/
  WarningRow.kt             amber advisory row with an optional action chip
feature/recipes/detail/     the Cook button becomes enabled and navigates
```

Rationale: `CookScreen` and `ScaledCardBody` are separate files because they are two distinct layouts over one state; keeping them together would produce a single composable long enough to be hard to hold in view.

---

## Task 1: Warning row in `:core:ui`

**Files:**
- Create: `core/ui/src/main/kotlin/.../component/WarningRow.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`, `values-it/strings.xml`

**Interfaces:**
- Produces: `WarningRow(text: String, actionLabel: String?, onAction: (() -> Unit)?, testTag: String)`.

- [ ] **Step 1:** Amber container (`AmberContainerLight`/`AmberContainerDark` from the design system), warning icon, text, optional action chip on the right.
- [ ] **Step 2:** `./gradlew :core:ui:assembleDebug detekt`.

---

## Task 2: `CookViewModel`

**Files:**
- Create: `feature/cook/build.gradle.kts`, `CookUiState.kt`, `CookViewModel.kt`, `navigation/CookRouteKey.kt`
- Modify: `settings.gradle.kts`
- Test: `feature/cook/src/test/kotlin/.../CookViewModelTest.kt`, `CookTestDoubles.kt`

**Interfaces:**
- Consumes: `RecipeRepository`, `ScaleVariantRepository`, `RecipeScaler`, `QuantityFormatter`.
- Produces: `CookUiState(recipe, mode, servingsInput, factorInput, ingredientConstraint, pantryAmounts, scaled, warnings, snaps, bottleneckLineId, leftovers, showCard, saveDialogVisible, suggestedLabel)` plus events `onModeChange`, `onServingsChange`, `onFactorChange`, `onIngredientConstraint`, `onPantryAmountChange`, `onSnapAccept`, `onShowCard`, `onSaveVariantRequested`, `onSaveVariant`, `onMarkCooked`.

- [ ] **Step 1: Write the failing tests.** Cover: opening starts at the recipe's own servings with factor 1.0; raising servings to 6 scales every line and reports factor 1.5; fixing an ingredient rescales the rest; a plain factor works; pantry mode reports the bottleneck and the achievable servings; a discrete warning surfaces with its snap options; accepting a snap switches the constraint to the resulting factor and clears that warning; the oven advisory appears for an oven recipe beyond the band; switching modes resets the previous mode's input rather than mixing them; saving a variant stores the **constraint** and the suggested label matches the mode.
- [ ] **Step 2: Run them and watch them fail.**
- [ ] **Step 3: Implement.** State is a `MutableStateFlow`; every event recomputes through `RecipeScaler.scale(recipe, constraint)` and maps the result into the state. `ScaleResult.Failure` becomes a message in state, never a crash.
- [ ] **Step 4:** `./gradlew :feature:cook:testDebugUnitTest`.

---

## Task 3: The adjust view

**Files:**
- Create: `CookScreen.kt`, strings
- Test: `feature/cook/src/test/kotlin/.../CookScreenTest.kt`

- [ ] **Step 1:** Mode chips (Servings / Ingredient / Factor / Pantry) and a mode-specific input: stepper plus field, a tappable ingredient list, a multiplier field with quick presets, and a "how much do you have" list.
- [ ] **Step 2:** The live list: original quantity struck through on the left, new quantity in bold on the right, approximate lines shown unchanged.
- [ ] **Step 3:** Warnings inline — `WarningRow` with a snap chip per option; the oven advisory as a banner above the list, carrying the tin-diameter hint.
- [ ] **Step 4:** Bottom actions: view the card, save this scaling.
- [ ] **Step 5: Compose tests** — changing servings updates the shown quantities; a discrete warning renders with its snap chip; tapping the chip reports the accepted snap.
- [ ] **Step 6:** `./gradlew :feature:cook:testDebugUnitTest`.

---

## Task 4: The scaled card and saving a variant

**Files:**
- Create: `ScaledCardBody.kt`
- Modify: `CookScreen.kt`, strings

- [ ] **Step 1:** Card face: title, the new servings, tag chips, scaled ingredient lines, the unchanged numbered steps, and the oven advisory when present.
- [ ] **Step 2:** A dialog to save the scaling, prefilled with a suggested label ("Per 6 persone", "×1,5", "Con quello che ho"), writing through `ScaleVariantRepository.save`.
- [ ] **Step 3:** Entering the card marks the recipe cooked (`markCooked`), which feeds the dashboard in phase 6.
- [ ] **Step 4: Compose test** — switching to the card shows the scaled quantities and the original steps.

---

## Task 5: Wire it up and fix the keyboard overlap

**Files:**
- Modify: `feature/recipes/detail/RecipeDetailScreen.kt` (enable the Cook button), `app/navigation/ProPortionApp.kt`, `app/build.gradle.kts`, `settings.gradle.kts`
- Modify: `feature/editor/EditorScreen.kt` (IME padding)
- Test: `app/src/test/kotlin/.../NavigationTest.kt`

- [ ] **Step 1:** Enable the Cook button, remove the "coming later" caption, navigate to `CookRouteKey(recipeId)`.
- [ ] **Step 2:** Register the cook graph in the app `NavHost`.
- [ ] **Step 3:** Add `imePadding()` to the editor and cook scroll containers: on a real phone the keyboard covered the quantity field and the save action.
- [ ] **Step 4: Navigation test** — from a recipe card, the Cook button opens the cook screen.
- [ ] **Step 5:** Full check, then install on the device and walk the flow by hand.
- [ ] **Step 6:** Update `docs/private/IMPLEMENTATION-STATUS.md`.

---

## Self-review notes

- Spec coverage: §7.4 modes and live list → Tasks 2–3; warnings and snaps → Tasks 2–3; oven advisory UI → Task 3; scaled card and variants → Task 4; entry point → Task 5.
- Deliberately deferred: "add to shopping list" (phase 6, with the list screen), cooking mode (phase 6), sharing the scaled recipe (phase 5).
- No arithmetic is added here: every number on screen comes from `ScaledRecipe`.
