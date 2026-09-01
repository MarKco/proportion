# Module map

| Module | Depends on | Holds |
|---|---|---|
| `:app` | every feature, `:core:data`, `:core:ui`, `:core:designsystem` | `MainActivity`, `ProPortionApp`, `TopLevelDestination`, Hilt application |
| `:feature:home` | `:core:domain`, `:core:ui`, `:core:designsystem` | dashboard (placeholder until phase 6) |
| `:feature:recipes` | + `:core:transfer` | list with filters, recipe detail, sharing |
| `:feature:editor` | `:core:domain`, `:core:ui`, `:core:designsystem` | recipe editor and its draft state |
| `:feature:cook` | `:core:domain`, `:core:ui`, `:core:designsystem` | the four constraint modes, warnings, scaled card |
| `:feature:shopping` | `:core:domain`, `:core:ui` | shopping list (placeholder until phase 6) |
| `:feature:settings` | + `:core:transfer`, `:core:data` | appearance, backup and restore |
| `:core:ui` | `:core:domain`, `:core:designsystem` | tag chips, unit picker, warning row, state bodies, `RecipeSharing`, `FileProvider` |
| `:core:designsystem` | `:core:model` | colours, type, shapes, motion, `ProPortionTheme` |
| `:core:data` | `:core:domain`, `:core:transfer`, `:core:database`, `:core:datastore` | repository implementations, mappers, `DataModule` |
| `:core:transfer` | `:core:domain`, `:core:model` | `.proportion` codec, plain-text formatter, `TransferRepository` |
| `:core:domain` | `:core:model` | scaling engine, unit rules, repository interfaces |
| `:core:database` | `:core:model` | Room entities, DAOs, converters, seeding |
| `:core:datastore` | `:core:model` | `UserPreferencesDataSource` |
| `:core:model` | — | `Recipe`, `Ingredient`, `MeasureUnit`, `Tag`, … |

Build logic lives in the included build `build-logic`, as convention plugins:
`proportion.android.application`, `proportion.android.library`, `proportion.android.library.compose`,
`proportion.jvm.library`, `proportion.hilt`.

**AGP 9 note:** AGP ships built-in Kotlin support, so a convention plugin must *not* apply
`org.jetbrains.kotlin.android` — doing so fails the build.
