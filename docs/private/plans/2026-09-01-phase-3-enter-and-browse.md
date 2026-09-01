# ProPortion Phase 3 — Enter and Browse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app genuinely usable: enter a recipe, find it again with combined filters, and read its card.

**Architecture:** One `ViewModel` per screen exposing `StateFlow<XUiState>` built from repository `Flow`s; stateless composables take state plus lambdas. A new `:core:ui` module holds components that know domain models (quantity text, tag chips, ingredient rows), so features never duplicate them and never depend on each other.

**Tech Stack:** Compose Material 3, Navigation Compose (type-safe routes), Hilt, Coroutines/Flow, Turbine for flow tests, Robolectric for Compose tests.

**Spec:** `docs/private/specs/2026-09-01-proportion-v1-design.md` (§7.1 list, §7.2 editor, §7.3 detail)

## Global Constraints

- Package root `com.ilsecondodasinistra.proportion`; author Marco Zanetti; never mention his employer.
- **Never `git commit` or `git push`.** Update `docs/private/IMPLEMENTATION-STATUS.md` at the end of each task instead.
- minSdk 26, compileSdk/targetSdk 36, JVM target 17, build with JDK 21 or Studio's JBR.
- AGP 9 has built-in Kotlin: convention plugins must not apply `org.jetbrains.kotlin.android`.
- No hardcoded user-facing strings: `values/` English, `values-it/` Italian, plurals where a count appears.
- Features never depend on other features; shared UI goes to `:core:ui`.
- `:core:domain` stays free of `android.*` (asserted by a test).
- Every ViewModel gets unit tests; the three critical paths get Compose tests.

---

## File structure

```
core/ui/                                     shared components that know domain models
  component/QuantityText.kt                  renders a RecipeIngredient or ScaledLine line
  component/TagChips.kt                      selectable and read-only tag chips, resolves built-in keys
  component/UiStateScaffold.kt               Loading / Empty / Error bodies used by every screen
  component/UnitPicker.kt                    unit dropdown grouped by category
  TagLabels.kt                               built-in tag key -> string resource
feature/recipes/
  list/RecipeListViewModel.kt                filters, search, sort, result count
  list/RecipeListScreen.kt                   search field, tag chips, ingredient sheet, FAB
  list/RecipeListUiState.kt
  detail/RecipeDetailViewModel.kt
  detail/RecipeDetailScreen.kt
  navigation/RecipesNavigation.kt            routes: recipes, recipe/{id}
feature/editor/
  EditorViewModel.kt                         draft state, validation, ingredient autocomplete
  EditorScreen.kt
  EditorUiState.kt
  navigation/EditorNavigation.kt             routes: editor (new), editor/{id}
app/navigation/ProPortionApp.kt              wires the new graphs
```

Rationale: list and detail live in the same feature module because they share the recipe repository and navigate to each other; the editor is its own module because it is a separate, heavier screen with its own draft state machine.

---

## Task 1: `:core:ui` module and shared components

**Files:**
- Create: `core/ui/build.gradle.kts`, `core/ui/src/main/res/values/strings.xml`, `values-it/strings.xml`
- Create: `TagLabels.kt`, `component/UiStateScaffold.kt`, `component/TagChips.kt`, `component/QuantityText.kt`, `component/UnitPicker.kt`
- Modify: `settings.gradle.kts`
- Test: `core/ui/src/test/kotlin/.../TagLabelsTest.kt`

**Interfaces:**
- Produces: `tagLabel(tag: Tag): String` (composable), `TagChipRow`, `SelectableTagChipRow`, `EmptyState`, `ErrorState`, `LoadingState`, `UnitPicker`, `IngredientLineRow`.

- [ ] **Step 1: Create the module** applying `proportion.android.library` + `proportion.android.library.compose`, depending on `:core:domain`, `:core:designsystem`.
- [ ] **Step 2: Write the built-in tag labels** — a `when` mapping each of the nine keys to a string resource, falling back to `tag.name` for user tags. Test asserts every key in `Tag.BUILT_IN_KEYS` resolves to a non-blank string in both locales.
- [ ] **Step 3: Write the state bodies** — `LoadingState` (centred progress), `EmptyState(title, message, actionLabel, onAction)`, `ErrorState(message, onRetry)`. All take a `testTag`.
- [ ] **Step 4: Write the tag chips** — read-only row for the detail screen, selectable row (`FilterChip`) for the list screen.
- [ ] **Step 5: Write `UnitPicker`** — an exposed dropdown grouping units by category with a localised category header.
- [ ] **Step 6: Run** `./gradlew :core:ui:testDebugUnitTest detekt` and update the status file.

---

## Task 2: Recipe list — ViewModel

**Files:**
- Create: `feature/recipes/.../list/RecipeListUiState.kt`, `RecipeListViewModel.kt`
- Test: `feature/recipes/src/test/kotlin/.../RecipeListViewModelTest.kt`

**Interfaces:**
- Consumes: `RecipeRepository`, `IngredientRepository`, `TagRepository`, `RecipeFilter`, `RecipeSort`.
- Produces: `RecipeListUiState(query, selectedTagIds, selectedIngredientIds, sort, recipes, availableTags, availableIngredients, resultCount, isEmptyLibrary)`, and the events `onQueryChange`, `onTagToggle`, `onIngredientToggle`, `onSortChange`, `onClearFilters`.

- [ ] **Step 1: Write the failing tests** covering: query debounce (200 ms) collapses rapid typing into one repository call; tag toggle adds then removes; ingredient toggle likewise; clearing resets every filter at once; `isEmptyLibrary` distinguishes "no recipes at all" from "no results for these filters" — the two need different empty states.
- [ ] **Step 2: Run them and watch them fail.**
- [ ] **Step 3: Implement** with a `MutableStateFlow<RecipeFilter>`, `debounce(200)` on the query only, `flatMapLatest` into `repository.observeRecipes`, combined with the tag and ingredient catalogues.
- [ ] **Step 4: Run the tests to green, then** `./gradlew :feature:recipes:testDebugUnitTest`.

---

## Task 3: Recipe list — screen

**Files:**
- Create: `list/RecipeListScreen.kt`, `navigation/RecipesNavigation.kt`
- Modify: `feature/recipes/build.gradle.kts`, strings
- Test: `feature/recipes/src/test/kotlin/.../RecipeListScreenTest.kt`

- [ ] **Step 1:** Search field with a clear button, `SelectableTagChipRow`, a filter button opening the ingredient bottom sheet, the visible result count, and a sort menu.
- [ ] **Step 2:** Recipe cards showing title, tags, servings and ingredient count; tapping one navigates to the detail route.
- [ ] **Step 3:** Two distinct empty states — empty library (invites adding the first recipe, opens the editor) versus no results (offers clearing filters).
- [ ] **Step 4:** Compose tests: typing filters the list; clearing filters restores it; the empty-library state shows its call to action.
- [ ] **Step 5:** Run `./gradlew :feature:recipes:testDebugUnitTest`.

---

## Task 4: Recipe detail

**Files:**
- Create: `detail/RecipeDetailUiState.kt`, `RecipeDetailViewModel.kt`, `RecipeDetailScreen.kt`
- Test: `feature/recipes/src/test/kotlin/.../RecipeDetailViewModelTest.kt`

- [ ] **Step 1: Failing tests** — the state exposes the recipe with its lines formatted through `QuantityFormatter`; a missing id yields `NotFound` rather than an empty screen; toggling the favourite writes through the repository.
- [ ] **Step 2: Implement the ViewModel** with `SavedStateHandle` for the recipe id.
- [ ] **Step 3: Implement the screen** — title, tag chips, servings, ingredient list, numbered steps, saved variants (list only in this phase), favourite toggle, overflow menu with edit and delete, and a prominent **Cook this recipe** button that is wired in phase 4 (disabled with a tooltip until then).
- [ ] **Step 4: Run** `./gradlew :feature:recipes:testDebugUnitTest`.

---

## Task 5: `:feature:editor`

**Files:**
- Create: the whole module: `build.gradle.kts`, `EditorUiState.kt`, `EditorViewModel.kt`, `EditorScreen.kt`, `navigation/EditorNavigation.kt`, strings
- Modify: `settings.gradle.kts`
- Test: `feature/editor/src/test/kotlin/.../EditorViewModelTest.kt`

**Interfaces:**
- Produces: routes `editor` (new recipe) and `editor/{recipeId}` (edit), plus `EditorUiState(title, servings, tags, lines, steps, suggestions, validation, isDirty, isSaving)`.

- [ ] **Step 1: Failing tests** — a new draft starts with one empty ingredient line; saving with a blank title reports `TitleRequired` and writes nothing; saving with no ingredient reports `IngredientsRequired`; a quantity is required unless the unit is approximate; ingredient names autocomplete from the catalogue and reuse the existing ingredient rather than creating a duplicate; editing an existing recipe loads its lines in order; `isDirty` flips on the first edit.
- [ ] **Step 2: Run them and watch them fail.**
- [ ] **Step 3: Implement the ViewModel** — draft held in a `MutableStateFlow`, line add/remove/move, tag add (built-in chips plus free text creating a user tag), step add/remove/move, `save()` mapping the draft to a `Recipe` and calling `RecipeRepository.upsert`.
- [ ] **Step 4: Implement the screen** — title field, servings stepper, tag chips plus "new tag" field, ingredient rows (name with autocomplete, quantity, `UnitPicker`, remove, drag handle), step fields, save action in the top bar, and a discard-changes dialog on back when `isDirty`.
- [ ] **Step 5: Run** `./gradlew :feature:editor:testDebugUnitTest`.

---

## Task 6: Wire the graphs and verify

**Files:**
- Modify: `app/navigation/ProPortionApp.kt`, `app/build.gradle.kts`, `settings.gradle.kts`, app strings
- Test: extend `app/src/test/kotlin/.../NavigationTest.kt`

- [ ] **Step 1:** Register the recipes graph (list plus detail) and the editor graph; the FAB and the detail screen's edit action navigate into the editor; saving pops back.
- [ ] **Step 2:** Compose test: from the recipes tab, the FAB opens the editor; back returns to the list.
- [ ] **Step 3:** Run the full check — `./gradlew detekt testDebugUnitTest :core:model:test :core:domain:test assembleDebug`.
- [ ] **Step 4:** Update `docs/private/IMPLEMENTATION-STATUS.md`: phase 3 complete, test count refreshed.

---

## Self-review notes

- Spec coverage: §7.1 list/search/filters → Tasks 2–3; §7.2 editor → Task 5; §7.3 detail → Task 4; §9 components → Task 1; §10 localisation → every task's strings step.
- Deliberately deferred: the **Cook this recipe** button exists in Task 4 but stays disabled until phase 4 builds the scale screen; variants are listed but not created; sharing and deletion confirmation land in phase 5.
