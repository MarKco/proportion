# Formato .proportion — guida per generazione ricette via AI

Usa `example-recipes.proportion` come esempio concreto insieme a questa guida quando chiedi a
un'AI di trasformare ricette esistenti (testo libero, foto, altri formati) in file `.proportion`
importabili in ProPortion. Questo file è autosufficiente: include più sotto anche il catalogo
completo degli ingredienti predefiniti dell'app, per il mapping descritto nella sezione
ingredienti.

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
{ "name": "builtin:flour_00", "qty": 350, "unit": "GRAM", "display": null, "note": null, "density": null }
```

- **`name`** — due casi:
  - **Ingrediente presente nel catalogo predefinito** (tabella completa in fondo a questa guida):
    usa `"builtin:<key>"`, es. `"builtin:flour_00"` per "Farina 00", `"builtin:egg"` per le uova.
    Fai il mapping per **significato**, non per uguaglianza
    testuale esatta (es. "farina tipo 00", "farina 0" e "farina 00" nel catalogo sono voci diverse:
    scegli quella semanticamente corretta, non affidarti al testo dell'originale). Una chiave
    sbagliata o inesistente non fa fallire l'import: l'app ricade su un ingrediente letterale con
    quella chiave come nome, quindi in caso di dubbio scegli comunque la voce più plausibile.
  - **Ingrediente non presente nel catalogo**: nome letterale in testo semplice (es.
    `"Colatura di alici"`, un ingrediente locale non catalogato — controlla comunque prima nella
    tabella, il catalogo è più ampio di quanto sembri). Mai un `"builtin:"` inventato.
  - Un nome letterale (senza prefisso) viene comunque abbinato al catalogo in fase di import, ma
    solo per **uguaglianza esatta** di testo normalizzato — meno affidabile del prefisso `builtin:`,
    che è per questo la scelta preferita quando l'ingrediente è in catalogo.
- **`qty`** — numero (anche decimale, es. `0.5`), oppure `null` **solo** se l'unità è una delle tre
  unità approssimative (`TO_TASTE`, `PINCH`, `DRIZZLE` — vedi sotto).
- **`unit`** — SEMPRE uno di questi valori esatti (case-sensitive, tutto maiuscolo). Lista completa,
  con nome italiano corrispondente (singolare) per il mapping da testo libero:

  | `unit` | Nome IT | Categoria | Numerabile |
  |---|---|---|---|
  | `GRAM` | grammo (g) | Massa | no |
  | `KILOGRAM` | chilogrammo (kg) | Massa | no |
  | `OUNCE` | oncia (oz) | Massa | no |
  | `POUND` | libbra (lb) | Massa | no |
  | `MILLILITRE` | millilitro (ml) | Volume | no |
  | `LITRE` | litro (l) | Volume | no |
  | `TEASPOON` | cucchiaino | Volume | no |
  | `TABLESPOON` | cucchiaio | Volume | no |
  | `GLASS` | bicchiere | Volume | no |
  | `CUP` | tazza | Volume | no |
  | `FLUID_OUNCE` | oncia fluida | Volume | no |
  | `PINT` | pinta | Volume | no |
  | `QUART` | quarto (di gallone) | Volume | no |
  | `GALLON` | gallone | Volume | no |
  | `PIECE` | pezzo | Conteggio | sì |
  | `EGG` | uovo | Conteggio | sì |
  | `CLOVE` | spicchio | Conteggio | sì |
  | `SLICE` | fetta | Conteggio | sì |
  | `LEAF` | foglia | Conteggio | sì |
  | `SACHET` | bustina | Conteggio | sì |
  | `JAR` | vasetto | Conteggio | sì |
  | `TO_TASTE` | q.b. | Approssimativo | — (`qty: null`) |
  | `PINCH` | un pizzico | Approssimativo | — (`qty: null`) |
  | `DRIZZLE` | un filo | Approssimativo | — (`qty: null`) |

  Un valore fuori da questa lista fa rifiutare l'intero file all'import. Scegli l'unità più
  specifica disponibile: uova → `EGG` non `PIECE`; uno spicchio d'aglio → `CLOVE`; una fetta di
  pane → `SLICE`; una bustina di lievito → `SACHET`; sale/pepe q.b. → `TO_TASTE` con `qty: null`.
  - **Massa/Volume = non numerabili (continue)**: `qty` decimale libero, es. `0.5`, `2.75`.
  - **Conteggio = numerabili (discrete)**: mezzo uovo/mezza fetta non esiste per davvero — un `qty`
    frazionario (es. `0.5`) è accettato ma l'app arrotonda e mostra un avviso. Preferisci interi;
    usa frazionari qui solo se proprio la ricetta originale dice "mezzo spicchio" o simile.
  - **Approssimativo = non ha una quantità**: `qty` dev'essere sempre `null`, il valore mostrato è
    `display`.
- **`display`** — testo alternativo mostrato al posto del numero+unità quando l'unità è
  approssimativa (es. `"q.b."`, `"un pizzico"`). `null` per le unità normali.
- **`note`** — nota libera sulla singola riga ingrediente (es. `"a temperatura ambiente"`), o
  `null`.
- **`density`** — lascia sempre `null`: è un dato interno (grammi per millilitro) che l'utente
  inserisce a mano nell'app quando serve convertire tra unità di massa e di volume per un
  ingrediente specifico; un'AI non ha modo di saperlo con certezza. Per gli ingredienti del
  catalogo predefinito (`"builtin:<key>"`) la densità è comunque già nota all'app internamente:
  non serve scriverla qui.

## Cosa può andare storto

- Un `unit` non nella tabella sopra → l'intero file viene rifiutato, non solo quella riga.
- `qty` numerico su un'unità approssimativa (o viceversa, `qty: null` su un'unità normale) → non fa
  fallire l'import, ma produce una riga insensata ("null g", o un numero che poi viene ignorato).
- `id` duplicati fra ricette diverse nello stesso file → la seconda ricetta con lo stesso id
  sovrascrive silenziosamente la prima in fase di parsing lato app.
- Tag builtin con una chiave inventata (es. `"builtin:snack"`, che non esiste) → l'app la importa
  come tag letterale con il testo grezzo `"builtin:snack"` visibile all'utente, invece di un tag
  tradotto — evitalo, usa solo le nove chiavi elencate sopra o un tag libero senza prefisso.
- Ingrediente builtin con una chiave inventata o non più esistente (es. `"builtin:farro_antico"`
  se non è nel catalogo) → non fallisce, ma l'ingrediente importato prende come nome letterale la
  chiave stessa (es. `"farro_antico"`, con underscore, non un nome leggibile) — verifica sempre la
  chiave contro la tabella in fondo a questa guida prima di usarla.

## File di riferimento

`example-recipes.proportion`, nella stessa cartella di questa guida, contiene tre ricette complete
e valide (un primo, un dolce con più unità diverse e un ingrediente approssimativo, una conserva
senza porzioni), tutte con ingredienti mappati sul catalogo tramite `"builtin:<key>"` — puoi
incollarlo così com'è in un prompt come esempio "few-shot", insieme a questa guida.

## Catalogo ingredienti predefiniti

477 voci. Colonne: `key` (usala come `"builtin:<key>"` nel campo `name`), nome IT corrispondente
(per il mapping semantico), categoria interna e unità predefinita dell'app per quella voce (non
vincolante: nella riga ricetta puoi comunque usare qualsiasi unità valida per la stessa categoria).

| key | nome IT | categoria | unità |
|---|---|---|---|
| `agar_agar` | Agar agar | LEAVENING_AND_BAKING | `GRAM` |
| `agave_syrup` | Sciroppo d'agave | SUGAR_AND_SWEETENER | `MILLILITRE` |
| `alchermes` | Alchermes | BEVERAGE | `MILLILITRE` |
| `allspice` | Pepe della Giamaica | HERB_AND_SPICE | `TEASPOON` |
| `almond` | Mandorle | NUT_AND_SEED | `GRAM` |
| `almond_essence` | Essenza di mandorla | OTHER | `TEASPOON` |
| `almond_flour` | Farina di mandorle | FLOUR_AND_GRAIN | `GRAM` |
| `almond_milk` | Latte di mandorla | BEVERAGE | `MILLILITRE` |
| `amaretto` | Amaretto | BEVERAGE | `MILLILITRE` |
| `anchovy` | Acciuga | FISH_AND_SEAFOOD | `PIECE` |
| `anchovy_paste` | Pasta di acciughe | CONDIMENT_AND_SAUCE | `TEASPOON` |
| `anise_seed` | Semi di anice | HERB_AND_SPICE | `TEASPOON` |
| `apple` | Mela | FRUIT | `PIECE` |
| `apple_juice` | Succo di mela | BEVERAGE | `MILLILITRE` |
| `apple_vinegar` | Aceto di mele | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `apricot` | Albicocca | FRUIT | `PIECE` |
| `apricot_jam` | Confettura di albicocche | SUGAR_AND_SWEETENER | `GRAM` |
| `arborio_rice` | Riso Arborio | FLOUR_AND_GRAIN | `GRAM` |
| `artichoke` | Carciofo | VEGETABLE | `PIECE` |
| `asiago` | Asiago | DAIRY_AND_EGG | `GRAM` |
| `asparagus` | Asparagi | VEGETABLE | `GRAM` |
| `avocado` | Avocado | FRUIT | `PIECE` |
| `bacon` | Bacon | MEAT | `SLICE` |
| `bakers_ammonia` | Ammoniaca per dolci | LEAVENING_AND_BAKING | `GRAM` |
| `baking_powder` | Lievito in polvere | LEAVENING_AND_BAKING | `GRAM` |
| `baking_soda` | Bicarbonato di sodio | LEAVENING_AND_BAKING | `GRAM` |
| `balsamic_glaze` | Glassa di aceto balsamico | CONDIMENT_AND_SAUCE | `DRIZZLE` |
| `balsamic_vinegar` | Aceto balsamico | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `banana` | Banana | FRUIT | `PIECE` |
| `basil` | Basilico | HERB_AND_SPICE | `LEAF` |
| `basmati_rice` | Riso basmati | FLOUR_AND_GRAIN | `GRAM` |
| `bay_leaf` | Alloro | HERB_AND_SPICE | `LEAF` |
| `bean_sprouts` | Germogli di soia | VEGETABLE | `GRAM` |
| `bechamel` | Besciamella | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `beef_ribs` | Costine di manzo | MEAT | `GRAM` |
| `beef_roast` | Arrosto di manzo | MEAT | `GRAM` |
| `beef_steak` | Bistecca di manzo | MEAT | `GRAM` |
| `beef_stock` | Brodo di manzo | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `beer` | Birra | BEVERAGE | `MILLILITRE` |
| `beetroot` | Barbabietola | VEGETABLE | `PIECE` |
| `bell_pepper` | Peperone | VEGETABLE | `PIECE` |
| `black_bean` | Fagioli neri | LEGUME | `GRAM` |
| `black_kale` | Cavolo nero | VEGETABLE | `GRAM` |
| `black_olive` | Olive nere | CONDIMENT_AND_SAUCE | `GRAM` |
| `black_pepper` | Pepe nero | HERB_AND_SPICE | `PINCH` |
| `black_tea` | Tè nero | BEVERAGE | `MILLILITRE` |
| `blackberry` | More | FRUIT | `GRAM` |
| `blueberry` | Mirtilli | FRUIT | `GRAM` |
| `borlotti_bean` | Fagioli borlotti | LEGUME | `GRAM` |
| `bottarga` | Bottarga | FISH_AND_SEAFOOD | `GRAM` |
| `brandy` | Brandy | BEVERAGE | `MILLILITRE` |
| `bread` | Pane | FLOUR_AND_GRAIN | `GRAM` |
| `breadcrumbs` | Pangrattato | FLOUR_AND_GRAIN | `GRAM` |
| `breadsticks` | Grissini | FLOUR_AND_GRAIN | `PIECE` |
| `bresaola` | Bresaola | MEAT | `SLICE` |
| `broad_bean` | Fave | LEGUME | `GRAM` |
| `broccoli` | Broccoli | VEGETABLE | `GRAM` |
| `brown_rice` | Riso integrale | FLOUR_AND_GRAIN | `GRAM` |
| `brown_sugar` | Zucchero di canna | SUGAR_AND_SWEETENER | `GRAM` |
| `brussels_sprout` | Cavoletti di Bruxelles | VEGETABLE | `GRAM` |
| `buckwheat_flour` | Farina di grano saraceno | FLOUR_AND_GRAIN | `GRAM` |
| `buffalo_mozzarella` | Mozzarella di bufala | DAIRY_AND_EGG | `GRAM` |
| `bulgur` | Bulgur | FLOUR_AND_GRAIN | `GRAM` |
| `burrata` | Burrata | DAIRY_AND_EGG | `GRAM` |
| `butter` | Burro | FAT_AND_OIL | `GRAM` |
| `buttermilk` | Latticello | DAIRY_AND_EGG | `MILLILITRE` |
| `cabbage` | Cavolo cappuccio | VEGETABLE | `PIECE` |
| `caciocavallo` | Caciocavallo | DAIRY_AND_EGG | `GRAM` |
| `candied_fruit` | Frutta candita | FRUIT | `GRAM` |
| `cannellini_bean` | Fagioli cannellini | LEGUME | `GRAM` |
| `caper` | Capperi | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `caramel` | Caramello | SUGAR_AND_SWEETENER | `GRAM` |
| `cardamom` | Cardamomo | HERB_AND_SPICE | `TEASPOON` |
| `cardoon` | Cardi | VEGETABLE | `GRAM` |
| `carnaroli_rice` | Riso Carnaroli | FLOUR_AND_GRAIN | `GRAM` |
| `carob_flour` | Farina di carrube | CHOCOLATE_AND_COCOA | `GRAM` |
| `carrot` | Carota | VEGETABLE | `PIECE` |
| `cashew` | Anacardi | NUT_AND_SEED | `GRAM` |
| `caster_sugar` | Zucchero semolato | SUGAR_AND_SWEETENER | `GRAM` |
| `cauliflower` | Cavolfiore | VEGETABLE | `PIECE` |
| `cayenne_pepper` | Pepe di Cayenna | HERB_AND_SPICE | `PINCH` |
| `celeriac` | Sedano rapa | VEGETABLE | `PIECE` |
| `celery` | Sedano | VEGETABLE | `PIECE` |
| `chard` | Bietola | VEGETABLE | `GRAM` |
| `cheddar` | Cheddar | DAIRY_AND_EGG | `GRAM` |
| `cherry` | Ciliegie | FRUIT | `GRAM` |
| `cherry_tomato` | Pomodorini | VEGETABLE | `GRAM` |
| `chestnut` | Castagne | FRUIT | `GRAM` |
| `chestnut_cream` | Crema di marroni | SUGAR_AND_SWEETENER | `GRAM` |
| `chestnut_flour` | Farina di castagne | FLOUR_AND_GRAIN | `GRAM` |
| `chia_seed` | Semi di chia | NUT_AND_SEED | `GRAM` |
| `chicken_breast` | Petto di pollo | MEAT | `GRAM` |
| `chicken_liver` | Fegatini di pollo | MEAT | `GRAM` |
| `chicken_stock` | Brodo di pollo | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `chicken_thigh` | Coscia di pollo | MEAT | `PIECE` |
| `chicken_wing` | Ali di pollo | MEAT | `GRAM` |
| `chickpea` | Ceci | LEGUME | `GRAM` |
| `chickpea_flour` | Farina di ceci | FLOUR_AND_GRAIN | `GRAM` |
| `chicory` | Cicoria | VEGETABLE | `GRAM` |
| `chili_flakes` | Peperoncino in fiocchi | HERB_AND_SPICE | `TEASPOON` |
| `chili_oil` | Olio piccante | FAT_AND_OIL | `DRIZZLE` |
| `chili_pepper` | Peperoncino | HERB_AND_SPICE | `PIECE` |
| `chive` | Erba cipollina | HERB_AND_SPICE | `TABLESPOON` |
| `chocolate_chips` | Gocce di cioccolato | CHOCOLATE_AND_COCOA | `GRAM` |
| `cinnamon` | Cannella | HERB_AND_SPICE | `TEASPOON` |
| `citric_acid` | Acido citrico | OTHER | `GRAM` |
| `clam` | Vongole | FISH_AND_SEAFOOD | `GRAM` |
| `clarified_butter` | Burro chiarificato | FAT_AND_OIL | `GRAM` |
| `clove_spice` | Chiodi di garofano | HERB_AND_SPICE | `PIECE` |
| `coarse_salt` | Sale grosso | CONDIMENT_AND_SAUCE | `GRAM` |
| `cocoa_butter` | Burro di cacao | CHOCOLATE_AND_COCOA | `GRAM` |
| `cocoa_powder` | Cacao amaro | CHOCOLATE_AND_COCOA | `GRAM` |
| `coconut_milk` | Latte di cocco | BEVERAGE | `MILLILITRE` |
| `coconut_oil` | Olio di cocco | FAT_AND_OIL | `GRAM` |
| `cod` | Merluzzo | FISH_AND_SEAFOOD | `GRAM` |
| `coffee` | Caffè | BEVERAGE | `MILLILITRE` |
| `condensed_milk` | Latte condensato | DAIRY_AND_EGG | `GRAM` |
| `cooking_cream` | Panna da cucina | DAIRY_AND_EGG | `MILLILITRE` |
| `coppa` | Coppa | MEAT | `SLICE` |
| `coriander_leaf` | Coriandolo fresco | HERB_AND_SPICE | `TABLESPOON` |
| `coriander_seed` | Semi di coriandolo | HERB_AND_SPICE | `TEASPOON` |
| `corn_flakes` | Corn flakes | FLOUR_AND_GRAIN | `GRAM` |
| `corn_oil` | Olio di mais | FAT_AND_OIL | `MILLILITRE` |
| `cornmeal` | Farina di mais per polenta | FLOUR_AND_GRAIN | `GRAM` |
| `cornstarch` | Amido di mais | FLOUR_AND_GRAIN | `GRAM` |
| `cotechino` | Cotechino | MEAT | `PIECE` |
| `couscous` | Couscous | FLOUR_AND_GRAIN | `GRAM` |
| `couverture_chocolate` | Cioccolato di copertura | CHOCOLATE_AND_COCOA | `GRAM` |
| `crab` | Granchio | FISH_AND_SEAFOOD | `GRAM` |
| `cracker` | Cracker | FLOUR_AND_GRAIN | `GRAM` |
| `cream_cheese` | Formaggio spalmabile | DAIRY_AND_EGG | `GRAM` |
| `cream_of_tartar` | Cremor tartaro | LEAVENING_AND_BAKING | `GRAM` |
| `crouton` | Crostini di pane | FLOUR_AND_GRAIN | `GRAM` |
| `cucumber` | Cetriolo | VEGETABLE | `PIECE` |
| `cumin` | Cumino | HERB_AND_SPICE | `TEASPOON` |
| `curry_powder` | Curry in polvere | HERB_AND_SPICE | `TEASPOON` |
| `custard` | Crema pasticcera | OTHER | `GRAM` |
| `cuttlefish` | Seppie | FISH_AND_SEAFOOD | `GRAM` |
| `dark_chocolate` | Cioccolato fondente | CHOCOLATE_AND_COCOA | `GRAM` |
| `date` | Datteri | FRUIT | `GRAM` |
| `desiccated_coconut` | Cocco rapé | FRUIT | `GRAM` |
| `dijon_mustard` | Senape di Digione | CONDIMENT_AND_SAUCE | `TEASPOON` |
| `dill` | Aneto | HERB_AND_SPICE | `TEASPOON` |
| `dried_apricot` | Albicocche secche | FRUIT | `GRAM` |
| `dried_cranberry` | Mirtilli rossi secchi | FRUIT | `GRAM` |
| `dried_fig` | Fichi secchi | FRUIT | `GRAM` |
| `dried_porcini` | Funghi porcini secchi | VEGETABLE | `GRAM` |
| `dry_biscuit` | Biscotti secchi | FLOUR_AND_GRAIN | `GRAM` |
| `dry_yeast` | Lievito di birra secco | LEAVENING_AND_BAKING | `GRAM` |
| `duck_breast` | Petto d'anatra | MEAT | `GRAM` |
| `durum_semolina` | Semola di grano duro | FLOUR_AND_GRAIN | `GRAM` |
| `egg` | Uovo | DAIRY_AND_EGG | `EGG` |
| `egg_pasta` | Pasta all'uovo | FLOUR_AND_GRAIN | `GRAM` |
| `egg_white` | Albume | DAIRY_AND_EGG | `PIECE` |
| `egg_yolk` | Tuorlo | DAIRY_AND_EGG | `PIECE` |
| `eggplant` | Melanzana | VEGETABLE | `PIECE` |
| `emmental` | Emmental | DAIRY_AND_EGG | `GRAM` |
| `endive` | Indivia | VEGETABLE | `PIECE` |
| `espresso_coffee` | Caffè espresso | BEVERAGE | `MILLILITRE` |
| `extra_virgin_olive_oil` | Olio extravergine d'oliva | FAT_AND_OIL | `MILLILITRE` |
| `farfalle` | Farfalle | FLOUR_AND_GRAIN | `GRAM` |
| `farro` | Farro | FLOUR_AND_GRAIN | `GRAM` |
| `fennel` | Finocchio | VEGETABLE | `PIECE` |
| `fennel_seed` | Semi di finocchio | HERB_AND_SPICE | `TEASPOON` |
| `feta` | Feta | DAIRY_AND_EGG | `GRAM` |
| `fig` | Fico | FRUIT | `PIECE` |
| `filo_pastry` | Pasta fillo | FLOUR_AND_GRAIN | `GRAM` |
| `fine_cornmeal` | Farina di mais fioretto | FLOUR_AND_GRAIN | `GRAM` |
| `fine_salt` | Sale fino | CONDIMENT_AND_SAUCE | `GRAM` |
| `fish_sauce` | Salsa di pesce | CONDIMENT_AND_SAUCE | `TEASPOON` |
| `fish_stock` | Fumetto di pesce | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `flaked_almond` | Mandorle a lamelle | NUT_AND_SEED | `GRAM` |
| `flax_seed` | Semi di lino | NUT_AND_SEED | `GRAM` |
| `flour_0` | Farina 0 | FLOUR_AND_GRAIN | `GRAM` |
| `flour_00` | Farina 00 | FLOUR_AND_GRAIN | `GRAM` |
| `flour_1` | Farina 1 | FLOUR_AND_GRAIN | `GRAM` |
| `fontina` | Fontina | DAIRY_AND_EGG | `GRAM` |
| `food_colouring` | Colorante alimentare | OTHER | `TO_TASTE` |
| `frankfurter` | Würstel | MEAT | `PIECE` |
| `fresh_tuna` | Tonno fresco | FISH_AND_SEAFOOD | `GRAM` |
| `fructose` | Fruttosio | SUGAR_AND_SWEETENER | `GRAM` |
| `fusilli` | Fusilli | FLOUR_AND_GRAIN | `GRAM` |
| `garlic` | Aglio | VEGETABLE | `CLOVE` |
| `garlic_powder` | Aglio in polvere | HERB_AND_SPICE | `TEASPOON` |
| `gelatin_powder` | Gelatina in polvere | OTHER | `GRAM` |
| `gelatin_sheet` | Colla di pesce | OTHER | `PIECE` |
| `giardiniera` | Giardiniera | CONDIMENT_AND_SAUCE | `GRAM` |
| `ginger` | Zenzero fresco | HERB_AND_SPICE | `GRAM` |
| `glucose_syrup` | Sciroppo di glucosio | SUGAR_AND_SWEETENER | `GRAM` |
| `gnocchi` | Gnocchi | FLOUR_AND_GRAIN | `GRAM` |
| `goat_cheese` | Formaggio di capra | DAIRY_AND_EGG | `GRAM` |
| `gorgonzola` | Gorgonzola | DAIRY_AND_EGG | `GRAM` |
| `grana_padano` | Grana Padano | DAIRY_AND_EGG | `GRAM` |
| `grape` | Uva | FRUIT | `GRAM` |
| `grapefruit` | Pompelmo | FRUIT | `PIECE` |
| `grappa` | Grappa | BEVERAGE | `MILLILITRE` |
| `grated_cheese` | Formaggio grattugiato | DAIRY_AND_EGG | `GRAM` |
| `greek_yogurt` | Yogurt greco | DAIRY_AND_EGG | `GRAM` |
| `green_bean` | Fagiolini | VEGETABLE | `GRAM` |
| `green_olive` | Olive verdi | CONDIMENT_AND_SAUCE | `GRAM` |
| `green_tea` | Tè verde | BEVERAGE | `MILLILITRE` |
| `ground_beef` | Macinato di manzo | MEAT | `GRAM` |
| `ground_chicken` | Macinato di pollo | MEAT | `GRAM` |
| `ground_ginger` | Zenzero in polvere | HERB_AND_SPICE | `TEASPOON` |
| `ground_pork` | Macinato di maiale | MEAT | `GRAM` |
| `grouper` | Cernia | FISH_AND_SEAFOOD | `GRAM` |
| `gruyere` | Groviera | DAIRY_AND_EGG | `GRAM` |
| `guanciale` | Guanciale | MEAT | `GRAM` |
| `hazelnut` | Nocciole | NUT_AND_SEED | `GRAM` |
| `hazelnut_spread` | Crema spalmabile alle nocciole | CHOCOLATE_AND_COCOA | `GRAM` |
| `heavy_cream` | Panna fresca | DAIRY_AND_EGG | `MILLILITRE` |
| `honey` | Miele | SUGAR_AND_SWEETENER | `GRAM` |
| `horseradish` | Rafano | HERB_AND_SPICE | `TEASPOON` |
| `hot_sauce` | Salsa piccante | CONDIMENT_AND_SAUCE | `TEASPOON` |
| `ice` | Ghiaccio | OTHER | `GRAM` |
| `icing_sugar` | Zucchero a velo | SUGAR_AND_SWEETENER | `GRAM` |
| `jam` | Marmellata | SUGAR_AND_SWEETENER | `GRAM` |
| `juniper_berry` | Bacche di ginepro | HERB_AND_SPICE | `PIECE` |
| `ketchup` | Ketchup | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `kidney_bean` | Fagioli rossi | LEGUME | `GRAM` |
| `kiwi` | Kiwi | FRUIT | `PIECE` |
| `ladyfinger` | Savoiardi | FLOUR_AND_GRAIN | `PIECE` |
| `lamb` | Agnello | MEAT | `GRAM` |
| `lamb_chop` | Costolette d'agnello | MEAT | `PIECE` |
| `lard` | Strutto | FAT_AND_OIL | `GRAM` |
| `lardo` | Lardo | MEAT | `GRAM` |
| `lasagna_sheet` | Sfoglie per lasagne | FLOUR_AND_GRAIN | `PIECE` |
| `leek` | Porro | VEGETABLE | `PIECE` |
| `lemon` | Limone | FRUIT | `PIECE` |
| `lemon_juice` | Succo di limone | FRUIT | `MILLILITRE` |
| `lemon_zest` | Scorza di limone | FRUIT | `TEASPOON` |
| `lentil` | Lenticchie | LEGUME | `GRAM` |
| `lettuce` | Lattuga | VEGETABLE | `PIECE` |
| `licorice` | Liquirizia | HERB_AND_SPICE | `GRAM` |
| `lime` | Lime | FRUIT | `PIECE` |
| `limoncello` | Limoncello | BEVERAGE | `MILLILITRE` |
| `linguine` | Linguine | FLOUR_AND_GRAIN | `GRAM` |
| `lobster` | Astice | FISH_AND_SEAFOOD | `PIECE` |
| `lupin_bean` | Lupini | LEGUME | `GRAM` |
| `macadamia` | Noci macadamia | NUT_AND_SEED | `GRAM` |
| `mackerel` | Sgombro | FISH_AND_SEAFOOD | `GRAM` |
| `mandarin` | Mandarino | FRUIT | `PIECE` |
| `mango` | Mango | FRUIT | `PIECE` |
| `manitoba_flour` | Farina Manitoba | FLOUR_AND_GRAIN | `GRAM` |
| `maple_syrup` | Sciroppo d'acero | SUGAR_AND_SWEETENER | `MILLILITRE` |
| `margarine` | Margarina | FAT_AND_OIL | `GRAM` |
| `marjoram` | Maggiorana | HERB_AND_SPICE | `TEASPOON` |
| `marsala` | Marsala | BEVERAGE | `MILLILITRE` |
| `marzipan` | Marzapane | OTHER | `GRAM` |
| `mascarpone` | Mascarpone | DAIRY_AND_EGG | `GRAM` |
| `mayonnaise` | Maionese | CONDIMENT_AND_SAUCE | `GRAM` |
| `melon` | Melone | FRUIT | `PIECE` |
| `milk` | Latte | DAIRY_AND_EGG | `MILLILITRE` |
| `milk_chocolate` | Cioccolato al latte | CHOCOLATE_AND_COCOA | `GRAM` |
| `millet` | Miglio | FLOUR_AND_GRAIN | `GRAM` |
| `mint` | Menta | HERB_AND_SPICE | `LEAF` |
| `miso` | Miso | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `mixed_herbs` | Erbe aromatiche miste | HERB_AND_SPICE | `TEASPOON` |
| `mixed_salad` | Insalata mista | VEGETABLE | `GRAM` |
| `molasses` | Melassa | SUGAR_AND_SWEETENER | `GRAM` |
| `monkfish` | Rana pescatrice | FISH_AND_SEAFOOD | `GRAM` |
| `mortadella` | Mortadella | MEAT | `SLICE` |
| `mozzarella` | Mozzarella | DAIRY_AND_EGG | `GRAM` |
| `mushroom` | Funghi | VEGETABLE | `GRAM` |
| `mussel` | Cozze | FISH_AND_SEAFOOD | `GRAM` |
| `mustard` | Senape | CONDIMENT_AND_SAUCE | `TEASPOON` |
| `mustard_seed` | Semi di senape | HERB_AND_SPICE | `TEASPOON` |
| `nduja` | 'Nduja | MEAT | `GRAM` |
| `nectarine` | Pesca noce | FRUIT | `PIECE` |
| `nutmeg` | Noce moscata | HERB_AND_SPICE | `PINCH` |
| `nutritional_yeast` | Lievito alimentare in scaglie | OTHER | `TABLESPOON` |
| `oat_flour` | Farina d'avena | FLOUR_AND_GRAIN | `GRAM` |
| `oat_milk` | Latte d'avena | BEVERAGE | `MILLILITRE` |
| `oats` | Fiocchi d'avena | FLOUR_AND_GRAIN | `GRAM` |
| `octopus` | Polpo | FISH_AND_SEAFOOD | `GRAM` |
| `olive_oil` | Olio d'oliva | FAT_AND_OIL | `MILLILITRE` |
| `onion` | Cipolla | VEGETABLE | `PIECE` |
| `onion_powder` | Cipolla in polvere | HERB_AND_SPICE | `TEASPOON` |
| `orange` | Arancia | FRUIT | `PIECE` |
| `orange_juice` | Succo d'arancia | BEVERAGE | `MILLILITRE` |
| `orange_zest` | Scorza d'arancia | FRUIT | `TEASPOON` |
| `oregano` | Origano | HERB_AND_SPICE | `TEASPOON` |
| `ossobuco` | Ossobuco | MEAT | `PIECE` |
| `oyster_sauce` | Salsa di ostriche | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `pancetta` | Pancetta | MEAT | `GRAM` |
| `paprika` | Paprika | HERB_AND_SPICE | `TEASPOON` |
| `parmesan` | Parmigiano | DAIRY_AND_EGG | `GRAM` |
| `parsley` | Prezzemolo | HERB_AND_SPICE | `TO_TASTE` |
| `passion_fruit` | Frutto della passione | FRUIT | `PIECE` |
| `pasta` | Pasta | FLOUR_AND_GRAIN | `GRAM` |
| `pea` | Piselli | VEGETABLE | `GRAM` |
| `peach` | Pesca | FRUIT | `PIECE` |
| `peanut` | Arachidi | NUT_AND_SEED | `GRAM` |
| `peanut_butter` | Burro di arachidi | NUT_AND_SEED | `GRAM` |
| `peanut_oil` | Olio di arachidi | FAT_AND_OIL | `MILLILITRE` |
| `pear` | Pera | FRUIT | `PIECE` |
| `pearl_barley` | Orzo perlato | FLOUR_AND_GRAIN | `GRAM` |
| `pearl_sugar` | Granella di zucchero | SUGAR_AND_SWEETENER | `GRAM` |
| `pecan` | Noci pecan | NUT_AND_SEED | `GRAM` |
| `pecorino` | Pecorino | DAIRY_AND_EGG | `GRAM` |
| `pectin` | Pectina | LEAVENING_AND_BAKING | `GRAM` |
| `peeled_tomatoes` | Pomodori pelati | VEGETABLE | `GRAM` |
| `penne` | Penne | FLOUR_AND_GRAIN | `GRAM` |
| `peppercorn` | Pepe in grani | HERB_AND_SPICE | `TEASPOON` |
| `persimmon` | Cachi | FRUIT | `PIECE` |
| `pesto` | Pesto alla genovese | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `piadina` | Piadina | FLOUR_AND_GRAIN | `PIECE` |
| `pickled_gherkin` | Cetriolini sottaceto | CONDIMENT_AND_SAUCE | `GRAM` |
| `pine_nut` | Pinoli | NUT_AND_SEED | `GRAM` |
| `pineapple` | Ananas | FRUIT | `PIECE` |
| `pink_pepper` | Pepe rosa | HERB_AND_SPICE | `PINCH` |
| `pistachio` | Pistacchi | NUT_AND_SEED | `GRAM` |
| `pizza_dough` | Impasto per pizza | FLOUR_AND_GRAIN | `GRAM` |
| `plum` | Prugna | FRUIT | `PIECE` |
| `pomegranate` | Melagrana | FRUIT | `PIECE` |
| `poppy_seed` | Semi di papavero | NUT_AND_SEED | `GRAM` |
| `porcini_mushroom` | Funghi porcini | VEGETABLE | `GRAM` |
| `pork_chop` | Braciola di maiale | MEAT | `PIECE` |
| `pork_loin` | Lonza di maiale | MEAT | `GRAM` |
| `pork_ribs` | Costine di maiale | MEAT | `GRAM` |
| `potato` | Patata | VEGETABLE | `PIECE` |
| `potato_starch` | Fecola di patate | FLOUR_AND_GRAIN | `GRAM` |
| `powdered_milk` | Latte in polvere | DAIRY_AND_EGG | `GRAM` |
| `prawn` | Gamberi | FISH_AND_SEAFOOD | `GRAM` |
| `prosciutto_cotto` | Prosciutto cotto | MEAT | `SLICE` |
| `prosciutto_crudo` | Prosciutto crudo | MEAT | `SLICE` |
| `prosecco` | Prosecco | BEVERAGE | `MILLILITRE` |
| `provolone` | Provolone | DAIRY_AND_EGG | `GRAM` |
| `prune` | Prugne secche | FRUIT | `GRAM` |
| `puff_pastry` | Pasta sfoglia | FLOUR_AND_GRAIN | `GRAM` |
| `pumpkin` | Zucca | VEGETABLE | `GRAM` |
| `pumpkin_seed` | Semi di zucca | NUT_AND_SEED | `GRAM` |
| `quail_egg` | Uovo di quaglia | DAIRY_AND_EGG | `PIECE` |
| `quince` | Mela cotogna | FRUIT | `PIECE` |
| `quinoa` | Quinoa | FLOUR_AND_GRAIN | `GRAM` |
| `rabbit` | Coniglio | MEAT | `GRAM` |
| `radicchio` | Radicchio | VEGETABLE | `PIECE` |
| `radish` | Ravanelli | VEGETABLE | `GRAM` |
| `raisin` | Uvetta | FRUIT | `GRAM` |
| `raspberry` | Lamponi | FRUIT | `GRAM` |
| `ravioli` | Ravioli | FLOUR_AND_GRAIN | `GRAM` |
| `red_lentil` | Lenticchie rosse | LEGUME | `GRAM` |
| `red_onion` | Cipolla rossa | VEGETABLE | `PIECE` |
| `red_wine` | Vino rosso | BEVERAGE | `MILLILITRE` |
| `redcurrant` | Ribes rosso | FRUIT | `GRAM` |
| `remilled_semolina` | Semola rimacinata | FLOUR_AND_GRAIN | `GRAM` |
| `rhubarb` | Rabarbaro | FRUIT | `GRAM` |
| `rice` | Riso | FLOUR_AND_GRAIN | `GRAM` |
| `rice_flour` | Farina di riso | FLOUR_AND_GRAIN | `GRAM` |
| `rice_milk` | Latte di riso | BEVERAGE | `MILLILITRE` |
| `rice_vinegar` | Aceto di riso | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `ricotta` | Ricotta | DAIRY_AND_EGG | `GRAM` |
| `rigatoni` | Rigatoni | FLOUR_AND_GRAIN | `GRAM` |
| `robiola` | Robiola | DAIRY_AND_EGG | `GRAM` |
| `rocket` | Rucola | VEGETABLE | `GRAM` |
| `rosemary` | Rosmarino | HERB_AND_SPICE | `TO_TASTE` |
| `rum` | Rum | BEVERAGE | `MILLILITRE` |
| `rum_essence` | Aroma di rum | OTHER | `TEASPOON` |
| `rye_flour` | Farina di segale | FLOUR_AND_GRAIN | `GRAM` |
| `saffron` | Zafferano | HERB_AND_SPICE | `SACHET` |
| `sage` | Salvia | HERB_AND_SPICE | `LEAF` |
| `salami` | Salame | MEAT | `SLICE` |
| `salmon` | Salmone | FISH_AND_SEAFOOD | `GRAM` |
| `salt` | Sale | CONDIMENT_AND_SAUCE | `PINCH` |
| `salt_cod` | Baccalà | FISH_AND_SEAFOOD | `GRAM` |
| `salted_butter` | Burro salato | FAT_AND_OIL | `GRAM` |
| `sandwich_bread` | Pancarré | FLOUR_AND_GRAIN | `SLICE` |
| `sardine` | Sardine | FISH_AND_SEAFOOD | `GRAM` |
| `sauerkraut` | Crauti | CONDIMENT_AND_SAUCE | `GRAM` |
| `sausage` | Salsiccia | MEAT | `PIECE` |
| `savoury_baking_powder` | Lievito istantaneo per torte salate | LEAVENING_AND_BAKING | `SACHET` |
| `savoy_cabbage` | Verza | VEGETABLE | `PIECE` |
| `scallop` | Capesante | FISH_AND_SEAFOOD | `PIECE` |
| `scamorza` | Scamorza | DAIRY_AND_EGG | `GRAM` |
| `scampi` | Scampi | FISH_AND_SEAFOOD | `PIECE` |
| `sea_bass` | Branzino | FISH_AND_SEAFOOD | `PIECE` |
| `sea_bream` | Orata | FISH_AND_SEAFOOD | `PIECE` |
| `seed_oil` | Olio di semi | FAT_AND_OIL | `MILLILITRE` |
| `seitan` | Seitan | OTHER | `GRAM` |
| `self_raising_flour` | Farina autolievitante | FLOUR_AND_GRAIN | `GRAM` |
| `semi_skimmed_milk` | Latte parzialmente scremato | DAIRY_AND_EGG | `MILLILITRE` |
| `sesame_oil` | Olio di sesamo | FAT_AND_OIL | `MILLILITRE` |
| `sesame_seed` | Semi di sesamo | NUT_AND_SEED | `GRAM` |
| `shallot` | Scalogno | VEGETABLE | `PIECE` |
| `shortcrust_pastry` | Pasta frolla | FLOUR_AND_GRAIN | `GRAM` |
| `shrimp` | Gamberetti | FISH_AND_SEAFOOD | `GRAM` |
| `skimmed_milk` | Latte scremato | DAIRY_AND_EGG | `MILLILITRE` |
| `smoked_paprika` | Paprika affumicata | HERB_AND_SPICE | `TEASPOON` |
| `smoked_ricotta` | Ricotta affumicata | DAIRY_AND_EGG | `GRAM` |
| `smoked_salmon` | Salmone affumicato | FISH_AND_SEAFOOD | `GRAM` |
| `sole` | Sogliola | FISH_AND_SEAFOOD | `PIECE` |
| `sour_cherry` | Amarene | FRUIT | `GRAM` |
| `sour_cream` | Panna acida | DAIRY_AND_EGG | `GRAM` |
| `sourdough_starter` | Lievito madre | LEAVENING_AND_BAKING | `GRAM` |
| `soy_milk` | Latte di soia | BEVERAGE | `MILLILITRE` |
| `soy_sauce` | Salsa di soia | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `soybean` | Soia | LEGUME | `GRAM` |
| `spaghetti` | Spaghetti | FLOUR_AND_GRAIN | `GRAM` |
| `sparkling_water` | Acqua frizzante | BEVERAGE | `MILLILITRE` |
| `speck` | Speck | MEAT | `SLICE` |
| `spelt_flour` | Farina di farro | FLOUR_AND_GRAIN | `GRAM` |
| `spinach` | Spinaci | VEGETABLE | `GRAM` |
| `split_pea` | Piselli spezzati | LEGUME | `GRAM` |
| `spring_onion` | Cipollotto | VEGETABLE | `PIECE` |
| `sprinkles` | Codette colorate | OTHER | `GRAM` |
| `squid` | Calamari | FISH_AND_SEAFOOD | `GRAM` |
| `stale_bread` | Pane raffermo | FLOUR_AND_GRAIN | `GRAM` |
| `star_anise` | Anice stellato | HERB_AND_SPICE | `PIECE` |
| `stevia` | Stevia | SUGAR_AND_SWEETENER | `GRAM` |
| `stewing_beef` | Spezzatino di manzo | MEAT | `GRAM` |
| `still_water` | Acqua naturale | BEVERAGE | `MILLILITRE` |
| `stock_cube` | Dado | CONDIMENT_AND_SAUCE | `PIECE` |
| `stockfish` | Stoccafisso | FISH_AND_SEAFOOD | `GRAM` |
| `stracchino` | Stracchino | DAIRY_AND_EGG | `GRAM` |
| `strawberry` | Fragole | FRUIT | `GRAM` |
| `strawberry_jam` | Confettura di fragole | SUGAR_AND_SWEETENER | `GRAM` |
| `sugar` | Zucchero | SUGAR_AND_SWEETENER | `GRAM` |
| `sugar_paste` | Pasta di zucchero | OTHER | `GRAM` |
| `sundried_tomato` | Pomodori secchi | VEGETABLE | `GRAM` |
| `sunflower_oil` | Olio di semi di girasole | FAT_AND_OIL | `MILLILITRE` |
| `sunflower_seed` | Semi di girasole | NUT_AND_SEED | `GRAM` |
| `sweet_baking_powder` | Lievito per dolci | LEAVENING_AND_BAKING | `SACHET` |
| `sweet_potato` | Patata dolce | VEGETABLE | `PIECE` |
| `sweetcorn` | Mais | VEGETABLE | `GRAM` |
| `sweetened_cocoa` | Cacao zuccherato | CHOCOLATE_AND_COCOA | `GRAM` |
| `swordfish` | Pesce spada | FISH_AND_SEAFOOD | `GRAM` |
| `tagliatelle` | Tagliatelle | FLOUR_AND_GRAIN | `GRAM` |
| `tahini` | Tahina | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `taleggio` | Taleggio | DAIRY_AND_EGG | `GRAM` |
| `tarragon` | Dragoncello | HERB_AND_SPICE | `TEASPOON` |
| `tempeh` | Tempeh | LEGUME | `GRAM` |
| `thyme` | Timo | HERB_AND_SPICE | `TO_TASTE` |
| `tofu` | Tofu | LEGUME | `GRAM` |
| `tomato` | Pomodoro | VEGETABLE | `PIECE` |
| `tomato_juice` | Succo di pomodoro | BEVERAGE | `MILLILITRE` |
| `tomato_passata` | Passata di pomodoro | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `tomato_paste` | Concentrato di pomodoro | CONDIMENT_AND_SAUCE | `TABLESPOON` |
| `tortellini` | Tortellini | FLOUR_AND_GRAIN | `GRAM` |
| `tortilla` | Tortilla | FLOUR_AND_GRAIN | `PIECE` |
| `tripe` | Trippa | MEAT | `GRAM` |
| `trout` | Trota | FISH_AND_SEAFOOD | `PIECE` |
| `truffle` | Tartufo | VEGETABLE | `GRAM` |
| `truffle_oil` | Olio al tartufo | FAT_AND_OIL | `DRIZZLE` |
| `tuna` | Tonno | FISH_AND_SEAFOOD | `GRAM` |
| `turkey_breast` | Petto di tacchino | MEAT | `GRAM` |
| `turmeric` | Curcuma | HERB_AND_SPICE | `TEASPOON` |
| `turnip` | Rapa | VEGETABLE | `PIECE` |
| `vanilla_bean` | Bacca di vaniglia | HERB_AND_SPICE | `PIECE` |
| `vanilla_extract` | Estratto di vaniglia | OTHER | `TEASPOON` |
| `vanilla_sugar` | Zucchero vanigliato | SUGAR_AND_SWEETENER | `SACHET` |
| `vanillin` | Vanillina | LEAVENING_AND_BAKING | `SACHET` |
| `veal` | Vitello | MEAT | `GRAM` |
| `veal_cutlet` | Fettina di vitello | MEAT | `PIECE` |
| `vegetable_stock` | Brodo vegetale | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `venere_rice` | Riso Venere | FLOUR_AND_GRAIN | `GRAM` |
| `vermouth` | Vermut | BEVERAGE | `MILLILITRE` |
| `vinegar` | Aceto | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `vodka` | Vodka | BEVERAGE | `MILLILITRE` |
| `walnut` | Noci | NUT_AND_SEED | `GRAM` |
| `water` | Acqua | OTHER | `MILLILITRE` |
| `watermelon` | Anguria | FRUIT | `GRAM` |
| `wheat_bran` | Crusca di frumento | FLOUR_AND_GRAIN | `GRAM` |
| `whipped_cream` | Panna montata | DAIRY_AND_EGG | `GRAM` |
| `whipping_cream` | Panna da montare | DAIRY_AND_EGG | `MILLILITRE` |
| `whisky` | Whisky | BEVERAGE | `MILLILITRE` |
| `white_chocolate` | Cioccolato bianco | CHOCOLATE_AND_COCOA | `GRAM` |
| `white_pepper` | Pepe bianco | HERB_AND_SPICE | `PINCH` |
| `white_wine` | Vino bianco | BEVERAGE | `MILLILITRE` |
| `white_wine_vinegar` | Aceto di vino bianco | CONDIMENT_AND_SAUCE | `MILLILITRE` |
| `whole_chicken` | Pollo intero | MEAT | `PIECE` |
| `whole_milk` | Latte intero | DAIRY_AND_EGG | `MILLILITRE` |
| `whole_wheat_flour` | Farina integrale | FLOUR_AND_GRAIN | `GRAM` |
| `worcestershire_sauce` | Salsa Worcestershire | CONDIMENT_AND_SAUCE | `TEASPOON` |
| `yeast` | Lievito di birra | LEAVENING_AND_BAKING | `GRAM` |
| `yogurt` | Yogurt bianco | DAIRY_AND_EGG | `GRAM` |
| `zucchini` | Zucchina | VEGETABLE | `PIECE` |
| `zucchini_flower` | Fiori di zucca | VEGETABLE | `PIECE` |
