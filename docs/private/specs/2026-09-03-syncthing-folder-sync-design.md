# Sync via cartella Syncthing — design

Data: 2026-09-03. Stato: approvato da Marco in chat (brainstorming), non ancora scritto un piano di
implementazione dettagliato oltre a questo documento + il piano di fase. Prossimo passo:
`writing-plans` quando si decide di implementare.

## Perché

ProPortion è offline, un solo DB Room sul device (`architecture.md`). Marco vuole condividere le
ricette tra due device (es. telefono + tablet) usando Syncthing, che già usa per altri dati, senza
un server/account.

## Decisione architetturale: niente DB grezzo

Il file `proportion.db` di Room non va mai messo in una cartella sincronizzata da un'app esterna:

- Room scrive in **WAL** (`proportion.db` + `-wal` + `-shm`); Syncthing sincronizza file
  indipendentemente, non transazioni — una sync a metà scrittura produce un DB corrotto.
- Nessuna gestione conflitti: due scritture concorrenti sui due device finirebbero in
  last-write-wins **a livello di intero file**, cioè un device può perdere tutte le modifiche fatte
  dall'ultimo sync, non solo quelle in conflitto.
- Storage scoped (Android 11+) rende scomodo dare a Syncthing e a ProPortion accesso alla stessa
  cartella senza `MANAGE_EXTERNAL_STORAGE`, permesso pesante e problematico per un'eventuale
  pubblicazione.

Si riusa invece il formato `.proportion` già esistente (fase 5, `:core:transfer`), pensato apposta
per lasciare/rientrare nell'app in modo lossless.

## Meccanismo

**Un file per ricetta**: `recipe-<id>.proportion` nella cartella scelta (non uno snapshot unico).
Modificare ricette diverse su device diversi non tocca mai lo stesso file → Syncthing non genera mai
un file di conflitto per quel caso, che è la maggioranza dei casi d'uso reali. Riusa
`TransferRepository.exportRecipe(id)` già esistente per il contenuto.

**Cartella**: scelta dall'utente via Storage Access Framework
(`ActivityResultContracts.OpenDocumentTree`), permesso persistito
(`ContentResolver.takePersistableUriPermission`). Nessun permesso storage classico richiesto.

**Trigger export**: dopo ogni `upsert`/`delete` locale andato a buon fine, se il sync è abilitato,
scrivere/aggiornare il file di quella ricetta nella cartella. Fire-and-forget, non blocca la UI.

**Trigger import**: job periodico via `WorkManager`, **ogni 3-4 ore** — non serve un servizio sempre
attivo né un trigger ad ogni apertura dell'app (scelta esplicita di Marco): non c'è comunque nessuna
notifica push da un device all'altro, solo file che Syncthing sincronizza col suo passo, quindi un
controllo periodico è equivalente a uno "on resume" più aggressivo ma consuma meno batteria e non
richiede agganciarsi al ciclo di vita del processo. In più, un pulsante "Sincronizza ora" manuale in
Settings per non dover aspettare fino a 4 ore dopo un cambio di cartella o un primo setup.

**Conflitti** (stessa ricetta modificata su entrambi prima del sync): confronto `Recipe.updatedAt`
(campo già esistente), vince il più recente. Silenzioso — nessun dialog, coerente con la scelta di
Marco di privilegiare semplicità qui.

**Cancellazioni**: `RecipeRepository.delete` diventa soft-delete — imposta `Recipe.deletedAt`
invece di rimuovere la riga. Il file `recipe-<id>.proportion` viene riscritto con `deletedAt`
valorizzato (tombstone) invece di essere cancellato. L'altro device, importando, vede
`deletedAt != null`, cancella (hard) la propria riga se presente, e non la reintroduce mai più
(niente file quindi niente re-insert). Il file tombstone su disco viene ripulito (hard-delete del
file, non solo della riga DB) dopo una finestra di grazia fissa (es. 30 giorni dal `deletedAt`) al
prossimo import, tempo sufficiente perché anche un device usato di rado abbia fatto almeno un sync.
Nessun handshake a due vie: più semplice, coerente col resto del meccanismo che è già
eventually-consistent e non richiede conferme.

## Ingredienti e tag letterali

Sincronizzano anche loro, non solo le ricette. Motivo concreto trovato rileggendo il codice: un
ingrediente letterale referenziato da una ricetta *oggi non porta con sé densità/peso-pezzo* quando
la ricetta viaggia — `WireIngredient.density` esiste ma è marcato "v2 preparation: scritto da
nessuno" e `TransferRepositoryImpl.resolveIngredient` non lo applica mai a un ingrediente
risolto/creato. Quindi anche impostare la densità su un ingrediente già usato in una ricetta
sincronizzata non si propaga oggi. La sync dedicata per catalogo risolve questo alla radice, senza
toccare quel campo legacy (resta com'è, fuori scope).

**Cosa sincronizza**: solo le righe **non built-in** (`isBuiltIn == false`) — i built-in (477
ingredienti, 9 tag) sono identici su ogni installazione perché seedati dallo stesso asset, non serve
mai sincronizzarli.

**File**: come le ricette, un file per riga — `ingredient-<id>.proportion`, `tag-<id>.proportion`,
stessa cartella. Nuovi tipi wire in `:core:transfer` (`WireIngredientEntry`,
`WireTagEntry`), distinti da `WireIngredient` (quello resta il riferimento leggero dentro una
ricetta, non tocco quel formato).

**Campo `updatedAt`**: né `Ingredient` né `Tag` ce l'hanno oggi. Aggiunto a entrambi (mirror di
`Recipe.updatedAt`), popolato a `TimeProvider.now()` alla creazione e ad ogni modifica (per
`Ingredient`, `setDensityData`; per `Tag`, nessuna modifica esiste oggi oltre alla creazione — vedi
sotto). Stessa policy di conflitto delle ricette: `updatedAt` più recente vince.

**Deduplica per nome (il problema che le ricette non hanno)**: due ricette diverse hanno sempre id
diversi per costruzione, ma due device offline possono creare *lo stesso* ingrediente o tag letterale
(stesso nome) **indipendentemente**, con due id diversi, prima di aver mai sincronizzato — è
esattamente il caso che `findOrCreate`/`findOrCreateUserTag` già evitano dentro un solo device
tramite match su `normalisedName`, ma il sync per id da solo non lo vede. Risoluzione: in fase di
import, se un `ingredient-<remoteId>.proportion` non matcha nessun id locale, prima di inserirlo
come riga nuova si cerca un match per `normalisedName` (ingredienti) / nome normalizzato (tag) tra
le righe letterali locali. Se c'è, si tratta come la stessa entità concettuale: si aggiornano i
campi (densità, peso-pezzo, `updatedAt`) sulla riga **locale esistente**, mantenendo il suo id
locale — le ricette che già puntano a quell'id restano valide. Non è una fusione perfetta (in
cartella restano comunque due file, uno per ciascun id storico, che continueranno a fondersi ad ogni
sync in modo idempotente) ma niente riga doppia visibile nel catalogo. Limite noto, accettabile.

**Cancellazione**: fuori scope. `TagRepository.deleteUserTag` esiste nell'interfaccia ma **nessuna
UI la chiama oggi** (solo nei test) — e per gli ingredienti non esiste nemmeno un metodo di
cancellazione esposto dal repository. Senza un flusso utente reale da coprire, niente tombstone per
ingredienti/tag in questa fase: se in futuro si aggiunge una UI di cancellazione, questo design va
rivisto (stesso pattern tombstone di `Recipe.deletedAt`, riusabile).

## Modello dati

- **Nuovo campo** `Recipe.deletedAt: Long? = null` (mirror di `updatedAt`) —
  `core/model/Recipe.kt`, `RecipeEntity`. Migrazione schema **3→4**: aggiunge la colonna.
- Le query esistenti (`observeRecipes`, dashboard, liste) filtrano `deletedAt IS NULL` — una riga
  con tombstone non deve mai apparire nell'app che l'ha ricevuta né in quella che l'ha generata
  finché non viene ripulita.
- Formato wire (`:core:transfer`): `WireRecipe` guadagna `deletedAt: Long? = null`. Additivo,
  `ignoreUnknownKeys = true` lo rende retrocompatibile — non serve bump di `ProportionFile.version`.
- Preferenze (`:core:datastore`): `syncEnabled: Boolean`, `syncFolderUri: String?`, più un log di
  sync persistito (ultime ~50 `SyncLogEntry(timestamp, message, isError)`, capped) — sopravvive al
  processo perché il sync gira anche da un `WorkManager` job in background, non solo mentre l'app è
  in foreground.

## Moduli

Segue lo stesso schema di `:core:transfer`: logica pura separata dall'I/O Android.

- **Nuovo `:core:sync`** (pure Kotlin, no `android.*`, come `:core:transfer`/`:core:domain`):
  `SyncPlan`/`SyncAction` — decide, dato un file remoto decodificato e lo stato locale (esiste? quale
  `updatedAt`/`deletedAt`?), l'azione da compiere: `Insert`, `Overwrite`, `Delete`, `Skip`. Testabile
  senza Robolectric, senza SAF, senza DB.
- **`:core:domain`**: interfaccia `SyncRepository` (`exportRecipe(id)`, `syncNow(): SyncResult`,
  `deleteFolderTombstonesOlderThan(...)`).
- **`:core:data`**: `SyncRepositoryImpl` — I/O reale con `DocumentFile`/`ContentResolver` sull'URI
  persistito, usa `:core:transfer` per encode/decode e `:core:sync` per decidere l'azione. Un
  `RecipeRepository` decorator (o hook diretto in `RecipeRepositoryImpl`) chiama
  `SyncRepository.exportRecipe(id)` dopo ogni `upsert`/`delete` quando il sync è abilitato — i
  ViewModel non cambiano, resta un dettaglio del repository.
- **`:feature:settings`**: nuova sezione "Sincronizzazione" — toggle on/off, picker cartella (SAF),
  stato ultimo sync (data/ora, N ricette), pulsante "Sincronizza ora", e una sezione errori: se il
  log di sync (`SyncRepository.observeLog()`) contiene errori recenti, un banner con l'ultimo in
  breve e un pulsante "Condividi log" che apre l'intent di condivisione di sistema
  (`RecipeSharing.shareText`, `:core:ui`, lo stesso meccanismo già usato per condividere una
  ricetta come testo) col log completo formattato — utile per mandare a Marco un log di debug senza
  collegare il device. Riusa lo stile delle sezioni backup/restore già presenti.
- **`:app`**: job periodico (`WorkManager`, `PeriodicWorkRequest` ogni 4 ore) che chiama
  `SyncRepository.syncNow()` se abilitato, pianificato/cancellato quando il toggle cambia — vive qui
  perché non è responsabilità di nessuna feature singola.

## Cosa resta fuori (v1 di questo sync)

- **Ingredienti/tag custom creati offline**: non sincronizzati in questa fase. Un ingrediente
  letterale creato su un device e usato in una ricetta sincronizzata arriva comunque sull'altro
  device perché `TransferRepositoryImpl.resolveIngredient`/`resolveTag` già lo creano al volo
  durante l'import (stesso meccanismo del backup/restore) — quello che non sincronizza è un
  ingrediente/tag creato ma non ancora usato in nessuna ricetta esportata. Accettabile: si
  sincronizza comunque non appena finisce in una ricetta.
- **Foto/allegati**: la ricetta non ne ha oggi; non è nello scope.
- **Sync multi-device (>2)**: il modello last-write-wins per `updatedAt` funziona anche con N
  device, ma non è stato pensato/discusso oltre il caso a due.
- **Notifica push di un import**: nessuna, la UI si aggiorna quando l'utente apre l'app o preme
  "Sincronizza ora".
