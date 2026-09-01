# 3. Type-safe navigation routes

**Status:** accepted, 2026-09-01 (replaces the initial string-route implementation)

## Context

The first pass used string routes with `navArgument`. It worked, but every argument existed twice —
once in the route pattern, once in the ViewModel's `SavedStateHandle` key — with nothing checking
that the two agreed.

## Decision

`@Serializable` route classes, `composable<T>`, `navigate(RouteKey(...))`, and
`savedStateHandle.toRoute<T>()` in the ViewModel.

## Consequences

A wrong argument is a compile error. Route classes are named `…RouteKey` where a composable already
owns the plain name (`CookRouteKey` / `CookRoute`). One wrinkle: `toRoute` decodes through
`android.os.Bundle`, so ViewModel tests that use it run under Robolectric rather than plain JUnit.
