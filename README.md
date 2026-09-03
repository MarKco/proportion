<img src="docs/assets/icon.png" alt="ProPortion icon" width="96" align="left" />

# ProPortion

ProPortion is an offline Android app for rescaling cooking recipes. Store a recipe with its
quantities and the number of people it serves, then re-derive everything from a single constraint:
a different serving count, a fixed amount of one ingredient, a plain multiplier, or the ingredients
you actually have in the cupboard.

What it adds over mental arithmetic is culinary correctness. It knows that eggs cannot be halved,
that "salt to taste" does not scale, and that a cake baked at 1.5x does not bake 1.5x longer.

- **Offline only.** No account, no sync, no tracking.
- **Italian and English**, with room for more languages.
- **Recipes are yours.** Share one as plain text or as a `.proportion` file, and back the whole
  database up wherever you like.

## Status

Phases 1 to 7 are done. The app builds, installs, and does the whole job: enter a recipe, find it,
rescale it four different ways, share it, back it up and restore it, see a dashboard, keep a
shopping list, follow a recipe in cooking mode.
Current progress lives in [`docs/private/STATUS.md`](docs/private/STATUS.md).

## Building

Requirements: JDK 21 and the Android SDK with platform 36.

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew detekt lint          # static analysis
./gradlew testAll              # every module's unit tests, JVM and Android alike
./gradlew verifyAll            # detekt + lint + every unit test + a debug APK
```

## Module map

| Module | Responsibility |
|---|---|
| `app` | Navigation host, DI composition, `MainActivity` |
| `core:model` | Plain data classes shared across layers |
| `core:domain` | Scaling engine, unit rules, repository interfaces — pure Kotlin, no Android |
| `core:data` | Repository implementations and mappers |
| `core:database` | Room entities, DAOs, migrations |
| `core:datastore` | User preferences |
| `core:designsystem` | Palette, typography, shapes, theme, motion |
| `feature:*` | One module per screen area |

## Documentation

- [`docs/public`](docs/public) — what the app is and does (Italian and English)
- [`docs/manual`](docs/manual) — user manual (Italian and English)
- [`docs/private`](docs/private) — for developers, English only:
  [status](docs/private/STATUS.md) ·
  [architecture](docs/private/ARCHITECTURE.md) ·
  [history](docs/private/HISTORY.md) ·
  [decisions](docs/private/DECISIONS.md) ·
  [contributing](docs/private/contributing.md)

## Licence

See [LICENSE](LICENSE).

## Author

Marco Zanetti
