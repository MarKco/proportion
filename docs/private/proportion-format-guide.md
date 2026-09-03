# Formato .proportion — guida per generazione ricette via AI

Usa `example-recipes.proportion` come esempio concreto insieme a questa guida quando chiedi a
un'AI di trasformare ricette esistenti (testo libero, foto, altri formati) in file `.proportion`
importabili in ProPortion.

## Struttura root (obbligatoria, esatta)

```json
{
    "format": "proportion",
    "version": 1,
    "exportedAt": "2026-09-03T12:00:00Z",
    "recipes": [ /* uno o più oggetti ricetta, vedi sotto */ ]
}
```

- `"format"` deve essere letteralmente `"proportion"` — qualsiasi altro valore fa rifiutare il file.
- `"version"` deve essere `1` (il numero di versione del formato attuale, non della ricetta).
- `"exportedAt"` è una stringa libera (di solito ISO-8601), può anche essere `null` o omessa.
- `"recipes"` è sempre una lista, anche per una sola ricetta.

## Un oggetto ricetta

```json
{
    "id": "<uuid-v4-univoco>",
    "title": "Nome della ricetta",
    "servings": 4,
    "tags": ["builtin:dessert", "Autunno"],
    "ingredients": [ /* vedi sotto */ ],
    "steps": ["Passo 1.", "Passo 2."],
    "notes": null,
    "variants": [],
    "deletedAt": null,
    "updatedAt": 1767441600000,
    "createdAt": 1767441600000
}
```

Regole per ogni campo:

- **`id`** — stringa univoca qualsiasi (idealmente un UUID v4, es.
  `b3b1a2e0-1a2b-4c3d-8e4f-5a6b7c8d9e01`). Genera un UUID diverso per ogni ricetta. Non deve
  coincidere con un id già presente nella libreria di chi importa, altrimenti l'import in modalità
  "Unisci" lo considera un duplicato e lo salta.
- **`title`** — testo libero.
- **`servings`** — intero, oppure `null` se la ricetta non è "per persone" (es. una marmellata: la
  resa dipende dalla quantità di frutta, non da un numero di commensali).
- **`tags`** — lista di stringhe. Due tipi:
  - **tag predefiniti**: prefisso `"builtin:"` seguito da una di queste 9 chiavi ESATTE (minuscolo,
    underscore): `appetizer`, `first_course`, `main_course`, `side_dish`, `dessert`,
    `bread_and_leavened`, `preserves`, `drinks`, `oven`. Non inventare altre chiavi builtin: se non
    è una di queste nove, dev'essere un tag libero.
  - **tag liberi**: qualunque altra stringa (es. `"Autunno"`, `"Veloce"`) — diventa un tag creato
    dall'utente, tradotto letteralmente così com'è, senza traduzione automatica.
- **`ingredients`** — vedi sezione dedicata sotto.
- **`steps`** — lista di stringhe, una per passo, nell'ordine di esecuzione. Niente numerazione
  manuale nel testo (l'app la aggiunge da sola).
- **`notes`** — stringa libera o `null`.
- **`variants`** — lascia sempre `[]` (lista vuota) per ricette generate da zero. Non è il posto
  dove mettere "raddoppia le dosi": è un meccanismo interno per salvare scalature già calcolate, con
  uno schema JSON interno (polimorfico) non pensato per essere scritto a mano.
- **`deletedAt`** — sempre `null` per una ricetta nuova.
- **`updatedAt`**, **`createdAt`** — timestamp Unix in **millisecondi** (non secondi). Usa l'istante
  corrente per entrambi, es. `Date.now()` in JS o `int(time.time()*1000)` in Python. Se non sai
  calcolarlo, `0` è accettato ma sconsigliato.

## Un oggetto ingrediente

```json
{ "name": "Farina 00", "qty": 350, "unit": "GRAM", "display": null, "note": null, "density": null }
```

- **`name`** — SEMPRE il nome letterale dell'ingrediente in testo semplice (es. `"Farina 00"`,
  `"Zucchero"`). **Non usare mai il prefisso `"builtin:"` in contenuto generato da un'AI**: quel
  prefisso serve solo a riferire il catalogo interno dell'app (centinaia di voci con chiavi interne
  che un'AI esterna non può conoscere). Un nome letterale viene comunque riconosciuto e abbinato
  automaticamente al catalogo esistente in fase di import, se corrisponde.
- **`qty`** — numero (anche decimale, es. `0.5`), oppure `null` **solo** se l'unità è una delle tre
  unità approssimative (`TO_TASTE`, `PINCH`, `DRIZZLE` — vedi sotto).
- **`unit`** — SEMPRE uno di questi valori esatti (case-sensitive, tutto maiuscolo):

  | Categoria | Valori validi |
  |---|---|
  | Massa | `GRAM`, `KILOGRAM`, `OUNCE`, `POUND` |
  | Volume | `MILLILITRE`, `LITRE`, `TEASPOON`, `TABLESPOON`, `GLASS`, `CUP`, `FLUID_OUNCE`, `PINT`, `QUART`, `GALLON` |
  | Conteggio (numeri interi o frazionari, es. mezza cipolla) | `PIECE`, `EGG`, `CLOVE`, `SLICE`, `LEAF`, `SACHET`, `JAR` |
  | Approssimativo (mai una quantità, `qty` deve essere `null`) | `TO_TASTE`, `PINCH`, `DRIZZLE` |

  Un valore fuori da questa lista fa rifiutare l'intero file all'import. Scegli l'unità più
  specifica disponibile: uova → `EGG` non `PIECE`; uno spicchio d'aglio → `CLOVE`; una fetta di
  pane → `SLICE`; una bustina di lievito → `SACHET`; sale/pepe q.b. → `TO_TASTE` con `qty: null`.
- **`display`** — testo alternativo mostrato al posto del numero+unità quando l'unità è
  approssimativa (es. `"q.b."`, `"un pizzico"`). `null` per le unità normali.
- **`note`** — nota libera sulla singola riga ingrediente (es. `"a temperatura ambiente"`), o
  `null`.
- **`density`** — lascia sempre `null`: è un dato interno (grammi per millilitro) che l'utente
  inserisce a mano nell'app quando serve convertire tra unità di massa e di volume per un
  ingrediente specifico; un'AI non ha modo di saperlo con certezza.

## Cosa può andare storto

- Un `unit` non nella tabella sopra → l'intero file viene rifiutato, non solo quella riga.
- `qty` numerico su un'unità approssimativa (o viceversa, `qty: null` su un'unità normale) → non fa
  fallire l'import, ma produce una riga insensata ("null g", o un numero che poi viene ignorato).
- `id` duplicati fra ricette diverse nello stesso file → la seconda ricetta con lo stesso id
  sovrascrive silenziosamente la prima in fase di parsing lato app.
- Tag builtin con una chiave inventata (es. `"builtin:snack"`, che non esiste) → l'app la importa
  come tag letterale con il testo grezzo `"builtin:snack"` visibile all'utente, invece di un tag
  tradotto — evitalo, usa solo le nove chiavi elencate sopra o un tag libero senza prefisso.

## File di riferimento

`example-recipes.proportion`, nella stessa cartella di questa guida, contiene tre ricette complete
e valide (un primo, un dolce con più unità diverse e un ingrediente approssimativo, una conserva
senza porzioni) — puoi incollarlo così com'è in un prompt come esempio "few-shot", insieme a questa
guida.
