# Architecture

ProPortion is an offline Android app. There is no server, no account, and no sync; everything the
app knows lives in one Room database on the device.

## The shape

```
:app                    navigation host, DI composition, MainActivity
:feature:*              one module per screen area — home, recipes, editor, cook, shopping, settings
:core:ui                components that know domain models (tag chips, unit picker, warning row)
:core:designsystem      palette, typography, shapes, motion, ProPortionTheme
:core:domain            scaling engine, unit rules, repository interfaces — pure Kotlin
:core:transfer          the .proportion file format and the plain-text share — pure Kotlin
:core:data              repository implementations, mappers, Hilt wiring
:core:database          Room entities, DAOs, migrations, seeding
:core:datastore         user preferences
:core:model             plain data classes shared by every layer
```

Two rules hold the whole thing together:

1. **A feature never depends on another feature.** Anything two features need moves into a core
   module. This is what keeps a screen's blast radius the size of one screen.
2. **`:core:domain` and `:core:transfer` never import `android.*` or `androidx.*`.** Both have a
   test that fails the build if they do. That is what makes the scaling rules testable in
   milliseconds and reusable if the app ever grows a second front end.

Dependencies point inwards: features depend on the domain, `:core:data` implements the domain's
repository interfaces. The domain knows nothing about Room, DataStore or Compose.

## How a screen is built

One `ViewModel` per screen, exposing a single `StateFlow<XUiState>` assembled from repository
flows. Composables are stateless: they take the state and a lambda per event. Every screen has a
stateful `XRoute` composable that talks to Hilt and a stateless `XScreen` that a test can render
with a hand-made state.

Errors are part of the state, never a crash and never a silent no-op — `CookUiState.error` and
`EditorUiState.errors` exist so the screen can say what went wrong.

## Where the rules live

The arithmetic is in `:core:domain` and nowhere else. A composable that computed a quantity would
be a bug: every number on screen comes out of `ScaledRecipe`. See `scaling-engine.md`.

The Home dashboard follows the same rule for a different kind of number: `DashboardSummariser`
(`:core:domain`) turns the recipe list into counts, course slices and picks once, in one pure
function, so `HomeViewModel` and its composables never total or filter a list themselves. A
donut chart is drawn by `DonutChart` in `:core:designsystem`, which knows nothing about recipes —
it takes plain `DonutSlice` values, so it stays reusable for any other proportional breakdown.

## Cooking mode is a second route, not a second feature

Cooking mode (the screen you actually follow at the stove, screen-always-on, big type, checkable
steps) lives inside `:feature:cook` as `CookingModeRouteKey`, alongside the existing "Cook this
recipe" scaling screen. It is not its own module: the two screens are two views onto the same
`RecipeScaler` output, and a feature module would have had to depend on `:feature:cook` to reuse
the scaling logic, which the "no feature depends on another feature" rule forbids. The scaling the
user chose travels between the two screens as URL-safe Base64 of the `ScaleConstraint`'s JSON in
the navigation route — a raw JSON string as a nav argument is a reliable source of escaping bugs,
so it goes through `Base64.getUrlEncoder().withoutPadding()` first.

## Navigation

Navigation Compose with **type-safe routes**: `@Serializable` route classes, `composable<T>`, and
`savedStateHandle.toRoute<T>()` in the ViewModel. There are no route strings and no
`navArgument` keys to keep in sync. Each feature exposes its graph as a `NavGraphBuilder`
extension; `:app` assembles them.

## Testing

- Domain and transfer: plain JVM tests, written first.
- Database and repositories: Robolectric with an in-memory Room database.
- Screens: Compose tests under Robolectric, rendering the stateless composable.
- `:app`: the real navigation graph over the real Hilt graph, so a screen whose ViewModel cannot be
  constructed fails in CI rather than on a device.
