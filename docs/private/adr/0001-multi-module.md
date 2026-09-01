# 1. Multi-module by feature and core

**Status:** accepted, 2026-09-01

## Context

ProPortion is a single-developer app, so the cost of module boundaries has to earn its keep.

## Decision

Split into `:app`, `:feature:*` and `:core:*`, with convention plugins in `build-logic`, following
the Now-in-Android layout. Features may not depend on each other.

## Consequences

More Gradle files and a longer first build. In exchange, the scaling engine can be tested without an
Android runtime, a screen's dependencies are visible in its `build.gradle.kts`, and incremental
builds only rebuild what changed. The rule against feature-to-feature dependencies is what actually
prevents the graph from silently becoming a ball of mud; when two features need the same thing, it
moves down into a core module.
