# Data model

Room, schema version 1, exported to `core/database/schemas`. Every primary key is a **UUID string**,
because ids travel inside `.proportion` files and have to mean the same thing on another device.

| Table | Notes |
|---|---|
| `recipes` | title, servings (nullable — a jam is not per person), steps as JSON, notes, favourite, cook count, timestamps |
| `ingredients` | catalogue; `normalised_name` is unique and is what lookup and de-duplication use; `density_g_per_ml` exists but is unused in v1 |
| `recipe_ingredients` | the interesting table: quantity, unit, optional display text, position; cascades from the recipe, restricted against the ingredient |
| `tags` | either a built-in `key` or a literal `name`, never both |
| `recipe_tags` | many-to-many join, cascading both ways |
| `scale_variants` | stores the **constraint**, not the computed numbers, so a saved scaling survives editing the recipe |
| `shopping_items` | one persistent list; source recipe ids as JSON |

## Decisions worth knowing

- **Steps are stored as JSON**, not joined on a separator: a step legitimately contains commas and
  newlines, and losing a boundary would silently corrupt a recipe.
- **Deleting a recipe never deletes ingredients.** They stay in the catalogue but drop out of the
  filter sheet, which queries only ingredients referenced by some recipe.
- **Built-in tags are seeded on database creation** with ids derived from their key
  (`builtin-dessert`), so an imported recipe binds to the same row on every install.
- **`density_g_per_ml` is v2 preparation.** It is created in schema 1 on purpose: adding the column
  later would mean a migration on databases already in users' hands. Do not remove it.

## Filtering

The three filters combine with AND. Within the ingredient filter a recipe must contain **every**
selected ingredient (`COUNT(DISTINCT …) = :ingredientCount`); within the tag filter it needs **any**
of the selected tags, because picking "first course" and "dessert" means either.
