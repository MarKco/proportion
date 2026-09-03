# Conversione di unità tra categorie (densità) — design

Data: 2026-09-03. Stato: approvato da Marco in chat (brainstorming), non ancora scritto un piano di
implementazione. Prossimo passo: `writing-plans`.

## Perché

Una delle funzionalità chiave promesse per v2. Oggi `UnitConverter.convert` rifiuta ogni conversione
tra `UnitCategory` diverse (MASS/VOLUME/COUNT) — 100 g di farina e 100 ml restano incommensurabili.
Marco vuole tre cose, tutte guidate dagli stessi dati di densità/peso-a-pezzo:

1. **Editor** — cambiare l'unità di una riga ricalcola la quantità per restare equivalente
   (3 cups zucchero → 300 g), invece di lasciare il numero invariato.
2. **Dettaglio ricetta** — vedere una riga in un'altra unità senza modificare la ricetta salvata
   (200 ml latte → "≈ 206 g", sola lettura).
3. **Cook, vincolo "per ingrediente"** — dichiarare quanto si ha di un ingrediente in un'unità
   diversa da quella della riga (ricetta vuole 3 cups zucchero, io ne ho 200 g).

Include anche COUNT (es. "300 g di pancetta a fette" → quante fette), non solo mass↔volume: dato
`itemWeightGrams` aggiunto su richiesta di Marco durante il brainstorming.

## Dati

`docs/densities.json` (477 voci, chiave = `Ingredient.key`) diventa la fonte per popolare il
catalogo built-in. Ogni voce ha `density` (g/ml) e, per le unità COUNT non-approssimate,
`itemWeightGrams`. Le unità APPROXIMATE (`TO_TASTE`/`PINCH`/`DRIZZLE`) non hanno né densità né peso
nel file — coerente con `MeasureUnit.isScalable == false`: restano escluse dalla conversione, nessun
cambiamento lì.

- **Nuovo campo** `Ingredient.itemWeightGrams: Double?` (mirror di `densityGramsPerMl`, stesso
  pattern nullable) — `core/model/Ingredient.kt`, `IngredientEntity` (`Entities.kt`).
- **Migrazione schema 2→3**: aggiunge la colonna `item_weight_grams`. A differenza della sola
  aggiunta di colonna, questa migrazione deve anche **fare backfill** di `density_g_per_ml` e
  `item_weight_grams` sulle righe built-in già esistenti (installazioni esistenti hanno le 477 righe
  seedate in fase 8 *senza* densità, perché il file non le portava ancora) — stesso problema già
  risolto in fase 8 per il seed iniziale: un `UPDATE` per `key` dentro la migrazione, non solo
  `Room.Callback.onCreate`.
- `core/database/src/main/assets/ingredients.json` (seed) si arricchisce con `density`/
  `itemWeightGrams` per ogni voce, letti da `docs/densities.json` (join per `key`; verificare che le
  477 chiavi combacino esattamente — un test di consistenza già esiste, `IngredientResourceConsistencyTest`,
  da estendere).
- **Ingredienti letterali** (non built-in): `densityGramsPerMl`/`itemWeightGrams` partono `null`;
  l'utente li imposta la prima volta che servono (vedi dialog sotto), scritti sulla riga
  `ingredients` via un nuovo metodo su `IngredientRepository` (es. `updateDensity(id, density,
  itemWeight)`), riusati per ogni ricetta futura che nomina lo stesso ingrediente.

## Motore di conversione (`:core:domain`)

`IngredientRef` (oggi solo `id`/`normalisedName`) guadagna `densityGramsPerMl: Double?`,
`itemWeightGrams: Double?`, `defaultUnit: MeasureUnit?`. `UnitConverter.convert` resta **sync, puro,
firma invariata** — passa dai grammi come hub:

```kotlin
private fun toGrams(qty: Double, unit: MeasureUnit, i: IngredientRef?): Double? = when (unit.category) {
    MASS -> qty * unit.baseFactor
    VOLUME -> i?.densityGramsPerMl?.let { qty * unit.baseFactor * it }
    COUNT -> if (unit == i?.defaultUnit) i.itemWeightGrams?.let { qty * it } else null
    APPROXIMATE -> null // già filtrato da isScalable a monte
}
// fromGrams: stessa forma, divisione invece di moltiplicazione

override fun convert(qty, from, to, ingredient): Double? {
    if (!from.isScalable || !to.isScalable) return null
    if (from.category == to.category) {
        if (from.category == COUNT && from != to) return null
        return qty * from.baseFactor / to.baseFactor   // invariato
    }
    val grams = toGrams(qty, from, ingredient) ?: return null
    return fromGrams(grams, to, ingredient)
}
```

COUNT resta legato all'unità nativa dell'ingrediente (`defaultUnit`): un pezzo e una fetta restano
incommensurabili *tra loro* come oggi (`PIECE↔SLICE` → null), cambia solo che ora un conteggio nella
sua unità nativa sa diventare grammi/ml.

**Nuova funzione pura**, stesso file o vicino, per distinguere "manca un dato" da "strutturalmente
non convertibile" (serve alla UI per decidere se mostrare il dialog densità):

```kotlin
enum class DensityRequirement { NONE, DENSITY, ITEM_WEIGHT, BOTH, UNSUPPORTED }
fun requirementFor(from: MeasureUnit, to: MeasureUnit, ingredient: IngredientRef?): DensityRequirement
```

Non tocca `UnitConverter.convert` né `ScaleError` — zero ripple sui call site e i test esistenti di
scaling engine.

**Bug/gap esistente da correggere in questo lavoro**: `DefaultRecipeScaler.resolveIngredient` chiama
oggi `converter.convert(constraint.qty, constraint.unit, line.unit)` **senza** passare l'ingrediente
(il parametro di default `null` viene usato implicitamente) — va costruito un `IngredientRef` da
`line.ingredient` e passato esplicitamente, altrimenti ogni conversione cross-categoria in Cook
fallirebbe sempre anche con densità nota.

## UX — regola unica, riusata nei tre flussi

Ad ogni cambio di unità (editor) o scelta di un'unità diversa (cook/detail):

1. Provo `converter.convert(qty, vecchia, nuova, ingredientRef)`.
2. Non-null → applico il risultato (sostituisce la quantità in editor; è il valore mostrato in
   detail; diventa il vincolo in cook). Questo estende anche casi già oggi ignorati, es. GRAM→KILOGRAM
   ricalcola invece di lasciare il numero invariato.
3. Null e `requirementFor(...)` ≠ `NONE`/`UNSUPPORTED` → dialog **"Densità sconosciuta"**
   (`AlertDialog`, stesso pattern di `SaveVariantDialog`/discard dialog): mostra solo il campo
   richiesto (g/ml, o g/unità-nativa, o entrambi per COUNT↔VOLUME), salva su `Ingredient` via
   `IngredientRepository`, riprova la conversione.
4. Null e `UNSUPPORTED` (es. PIECE↔SLICE, o unità APPROXIMATE coinvolta) → comportamento di oggi,
   nessun dialog, nessuna modifica.

Il lookup dell'`Ingredient` è **per nome** durante l'editing (`EditorLine` non porta un `ingredientId`
salvato — si lega al catalogo solo al salvataggio, `findOrCreate`), stesso meccanismo già usato
dall'autocomplete.

### Editor (Caso 1)
`onLineUnitChange` applica la regola sopra invece di limitarsi a sostituire `unit`.

### Cook — vincolo per ingrediente (Caso 3)
`IngredientConstraintInput` (oggi: solo chip riga + campo quantità, nessun selettore unità) guadagna
un `UnitPicker` accanto al campo "ne ho". Stessa regola.

### Dettaglio ricetta (Caso 2)
Tap su una riga ingrediente apre un bottom sheet (pattern già in uso per il filtro ingredienti nella
lista ricette) con quantità/unità originali + `UnitPicker` per vedere l'equivalente. Sola lettura,
nessuna scrittura sulla ricetta.

## Testing

- `:core:domain`, TDD: `toGrams`/`fromGrams`/`convert` cross-categoria (mass↔volume, count↔mass,
  count↔volume via catena, casi `UNSUPPORTED`), `requirementFor` per ogni combinazione.
- `DefaultRecipeScaler` — test di regressione per il bug del ref mancante in `resolveIngredient`.
- `core/database`: `MigrationTestHelper` per 2→3, incluso il backfill delle righe built-in esistenti.
- `IngredientResourceConsistencyTest` esteso: ogni chiave in `densities.json` esiste nel seed e
  viceversa, nessun buco di densità/peso sulle unità non-approssimate.
- ViewModel: `EditorViewModel.onLineUnitChange`, `CookViewModel` (nuovo stato dell'unità scelta),
  dialog densità (submit → repository → retry).
- On-device: farina cups→g in editor, pancetta g→fette in cook, latte ml→g nel dettaglio.

## Limitazioni note, non bloccanti

- Ingredienti letterali partono senza densità: prima conversione richiede l'input manuale
  dell'utente (poi persiste per sempre su quell'ingrediente).
- COUNT↔VOLUME richiede *entrambi* densità e peso-a-pezzo: se uno dei due manca il dialog chiede
  entrambi in un colpo solo, non due dialog in sequenza.
