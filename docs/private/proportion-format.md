# The `.proportion` format

One JSON format, two uses: sharing a single recipe and backing up the whole library differ only in
how many recipes the file holds.

```json
{
  "format": "proportion",
  "version": 1,
  "exportedAt": "…",
  "recipes": [{
    "id": "9f2c…",
    "title": "Torta di mele",
    "servings": 4,
    "tags": ["builtin:dessert", "merenda"],
    "ingredients": [
      { "name": "Farina 00", "qty": 300, "unit": "GRAM" },
      { "name": "Sale", "qty": null, "unit": "TO_TASTE", "display": "q.b." }
    ],
    "steps": ["…"],
    "notes": null
  }]
}
```

## Rules the codec enforces

- **`format` must be `proportion`**, or the file is rejected as somebody else's.
- **A newer `version` is refused**, by number, rather than half-read.
- **Unknown fields are ignored** (`ignoreUnknownKeys`), so a file written by a later version still
  imports minus what this version cannot understand.
- **An unknown unit is refused.** Every other unreadable detail can be dropped, but silently
  substituting a unit would change a recipe.
- **Tags travel by kind**: `builtin:<key>` for the nine built-ins, so they stay translated on the
  other side, and literal text for user tags.
- **Ids travel with the recipe**, which is what lets the receiving app tell a duplicate from a new
  recipe.

## Import

Two steps, always. `preview()` reads the file and reports how many recipes it holds and how many ids
are already present, touching nothing. Then `import(text, mode)` runs with `MERGE` (skip ids already
here) or `REPLACE_ALL` (empty the library first; the UI asks twice).

On the way in, each ingredient name is resolved against the catalogue by its normalised name — so
importing a friend's recipe does not create a second "Farina 00" — and each built-in tag key binds
to the seeded tag of the same key.
