# Sync via cartella Syncthing (Phase 10) Implementation Plan

**Goal:** condividere ricette **e catalogo (ingredienti/tag letterali)** tra due device via una
cartella sincronizzata da Syncthing (o equivalente), senza toccare il DB grezzo — un file
`.proportion` per entità, export automatico, import on-resume/manuale, conflitti risolti per
timestamp, cancellazioni di ricette propagate via tombstone (ingredienti/tag non sono cancellabili
da UI oggi, quindi niente tombstone per loro — vedi spec).

**Architecture:** nuovo modulo puro `:core:sync` (policy: dato lo stato remoto e locale di una
ricetta, decide insert/overwrite/delete/skip) + `SyncRepositoryImpl` in `:core:data` (I/O reale via
SAF, riusa `:core:transfer` per encode/decode) + UI in `:feature:settings` + trigger di import in
`:app`.

**Tech Stack:** Kotlin, Room 2.8 (migration 3→4), `androidx.documentfile`, SAF
(`ActivityResultContracts.OpenDocumentTree`), Hilt, `ProcessLifecycleOwner`, Robolectric/JUnit4/Truth.

**Spec:** `docs/private/specs/2026-09-03-syncthing-folder-sync-design.md` — leggerlo insieme a questo
piano, spiega il *perché* di ogni decisione qui (in particolare perché niente DB grezzo, perché un
file per ricetta, perché niente handshake sui tombstone).

## Global Constraints

- **Mai `git commit` o `git push`.** Marco fa commit/push da solo.
- Mai menzionare il datore di lavoro di Marco.
- `:core:domain`, `:core:transfer`, `:core:sync` non importano mai `android.*`/`androidx.*` — test
  di guardia come per gli altri moduli puri.
- Nessuna feature dipende da un'altra feature.
- Nessuna stringa utente hardcoded — `values/strings.xml` + `values-it/strings.xml` in parità
  (`scripts/check-string-parity.sh`).
- detekt `maxIssues: 0`. Aggiornare `docs/private/IMPLEMENTATION-STATUS.md` man mano, non solo alla
  fine.
- `./gradlew verifyAll` verde a fine fase; verifica finale anche su device reale (Fairphone 3) con
  due cartelle locali che simulano due device (vedi Task 8).

---

### Task 1: `deletedAt`/`updatedAt` + migrazione schema 3→4

**Files:** `core/model/.../Recipe.kt` (+`Ingredient.kt`, `Tag.kt`),
`core/database/.../entity/Entities.kt` (`RecipeEntity`, `IngredientEntity`, `TagEntity`),
`core/database/.../ProPortionDatabase.kt` (nuova `Migration3to4`), `core/database/.../dao/RecipeDao.kt`
(filtrare `deletedAt IS NULL` in ogni query di lettura esistente + nuova query per hard-delete),
`core/database/.../dao/IngredientDao.kt`/`TagDao.kt` (nuova query `findById`, `updateDensity`/
`updateItemWeight`/`upsertAll` toccano anche `updated_at`), `core/data/.../RecipeRepositoryImpl.kt`
(`delete()` diventa soft), `core/data/.../CatalogueRepositoriesImpl.kt` (`setDensityData`,
`findOrCreate`, `findOrCreateUserTag` impostano `updatedAt`), test di migrazione
(`MigrationTestHelper`, come `Migration2to3`).

Un'unica migrazione perché nessuna delle tre modifiche è ancora implementata.

- [x] Aggiungere `deletedAt: Long? = null` a `Recipe`/`RecipeEntity`; `updatedAt: Long = 0L` a
      `Ingredient`/`IngredientEntity` e a `Tag`/`TagEntity`.
- [x] `Migration3to4`: `ALTER TABLE recipes ADD COLUMN deleted_at INTEGER DEFAULT NULL`,
      `ALTER TABLE ingredients ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0`,
      `ALTER TABLE tags ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0`.
- [x] Ogni query di lettura ricette (lista, dashboard, dettaglio, autocomplete) esclude
      `deleted_at IS NOT NULL`. Test di regressione: una ricetta soft-deleted non appare più in
      nessuna lista esistente.
- [x] `RecipeRepository.delete(id)` scrive `deletedAt = time.now()` invece di `DELETE`.
- [x] Nuovo `RecipeDao.hardDelete(id)` (o riuso di uno esistente) per la pulizia dei tombstone,
      usato solo dal sync (Task 5), non dalla UI.
- [x] `IngredientRepositoryImpl.findOrCreate`/`setDensityData` e
      `TagRepositoryImpl.findOrCreateUserTag` impostano/aggiornano `updatedAt = time.now()`
      (serve iniettare `TimeProvider`, già esistente, in entrambi i repository — oggi non lo
      ricevono).
- [x] Nuove query `IngredientDao.findById(id)`, `TagDao.findById(id)` (servono al Task 5 per il
      match per id prima del fallback per nome).
- [x] Bump `version = 4` su `ProPortionDatabase`, registrare `Migration3to4` in `DataModule`.

### Task 2: formato di trasferimento — tombstone ricette + entry di catalogo

**Files:** `:core:transfer` — `ProportionFile.kt`/wire models (nuovi `WireIngredientEntry`,
`WireTagEntry`, non toccare `WireIngredient` che resta il riferimento leggero dentro una ricetta),
`ProportionCodec.kt` (nuove `encodeIngredientEntry`/`decodeIngredientEntry`,
`encodeTagEntry`/`decodeTagEntry`, stesso pattern di `encode`/`decode` per le ricette),
`TransferRepositoryImpl.kt` (mappare `Recipe.deletedAt` in entrambe le direzioni; nuovi
`exportIngredient(id)`/`exportTag(id)` che producono `WireIngredientEntry`/`WireTagEntry`).

- [x] `WireRecipe` guadagna `deletedAt: Long? = null`. Test: round-trip con e senza tombstone.
- [x] `TransferRepositoryImpl.exportRecipe`/`import` propagano il campo. Nota: l'import "normale"
      (backup/restore manuale da parte dell'utente, non il sync) può ignorare `deletedAt` in
      arrivo o rifiutarlo — decidere in fase di implementazione se un utente che importa un backup
      con ricette tombstoned deve vederle sparire o no; non è il caso d'uso primario di questo
      task ma il campo deve comunque sopravvivere al round-trip senza rompere test esistenti.
- [x] `WireIngredientEntry(id, name, normalisedName, defaultUnit, category, densityGramsPerMl,
      itemWeightGrams, updatedAt)`, `WireTagEntry(id, name, colorIndex, updatedAt)`. Test:
      round-trip di entrambi, inclusi i campi nullable.
- [x] `TransferRepositoryImpl.exportIngredient(id)`/`exportTag(id)`: `null` se l'id non esiste o è
      built-in (i built-in non si esportano mai — vedi spec).
- **Bug reale trovato implementando questo task**: `exportRecipe` leggeva da
  `recipeRepository.observeRecipe(id)`, che (dal Task 1) filtra `deleted_at IS NULL` — dopo un
  `delete()` la ricetta era invisibile anche a `exportRecipe`, quindi impossibile esportarne il
  tombstone proprio quando serve di più. Fix: nuova `RecipeDao.findByIdIncludingDeleted(id)`
  (unfiltered, `suspend`, non `Flow` — un one-shot per l'export basta), `exportRecipe` la usa al
  posto del repository. Coperto da test (`a recipe's tombstone survives export and import`).

### Task 3: `:core:sync` — policy pura, generica per ricette/ingredienti/tag

**Files:** nuovo modulo `core/sync/` (`build.gradle.kts`, `SyncableState.kt`, `SyncAction.kt`),
`settings.gradle.kts`, test `core/sync/src/test/.../SyncPlanTest.kt`,
`core/sync/.../NoAndroidDependencyTest.kt` (come `:core:transfer`).

**Interfaces:**
- `sealed interface SyncAction { Insert; Overwrite; Delete; Skip }`
- `data class SyncableState(val updatedAt: Long, val deletedAt: Long? = null)` — entità-agnostico:
  per ingredienti/tag `deletedAt` è sempre `null` su entrambi i lati, quindi `Delete` non può mai
  uscirne (nessuna azione nuova da gestire nel resto del codice, riuso diretto).
- `fun decideSyncAction(local: SyncableState?, remote: SyncableState): SyncAction`

- [x] Scrivere i test prima: locale assente + remoto vivo → `Insert`; locale assente + remoto
      tombstone → `Skip`; locale più vecchio (`updatedAt`) + remoto vivo → `Overwrite`; locale più
      vecchio + remoto tombstone → `Delete`; locale più recente o uguale → `Skip` in ogni caso;
      locale già tombstoned + remoto vivo più recente → `Overwrite` (un "undelete" legittimo se
      l'altro device ha modificato dopo la cancellazione); caso ingrediente/tag (`deletedAt` sempre
      null su entrambi i lati) non produce mai `Delete`.
- [x] Implementare `decideSyncAction`.
- [x] Test "niente `android.*`" sul modulo.
- Nota: file rinominato `SyncableState.kt` (non `SyncPlan.kt`) — detekt (`MatchingDeclarationName`)
  vuole che il nome del file corrisponda alla dichiarazione top-level principale.

### Task 4: preferenze di sync

**Files:** `:core:datastore` — `UserPreferencesDataSource`/modello prefs (`syncEnabled: Boolean`,
`syncFolderUri: String?`), test.

- [x] Aggiungere i due campi seguendo il pattern delle preferenze esistenti (tema/lingua).

### Task 5: `SyncRepository` — interfaccia + impl reale

**Files:** `:core:domain` — `SyncRepository.kt` (interfaccia); `:core:data` —
`SyncRepositoryImpl.kt`, `DataModule`/`DataBindingsModule` binding, test Robolectric con una
`DocumentFile` su una cartella temporanea reale (non serve mockare SAF, `DocumentFile.fromFile`
funziona in Robolectric).

**Interfaces:**
- `suspend fun exportRecipe(id: String)` / `exportIngredient(id: String)` / `exportTag(id: String)`
  — scrivono/aggiornano `recipe-<id>.proportion` / `ingredient-<id>.proportion` /
  `tag-<id>.proportion` nella cartella configurata; no-op se sync disabilitato, cartella non
  impostata, o (per ingredienti/tag) riga built-in.
- `suspend fun syncNow(): SyncResult` — per ciascuna delle tre categorie di file
  (`recipe-*`/`ingredient-*`/`tag-*`) chiama `decideSyncAction` (Task 3) contro lo stato locale e
  applica l'azione. Per ricette: match sempre per id. Per ingredienti/tag: match per id: se non
  trovato, fallback per `normalisedName`/nome normalizzato tra le righe letterali locali (vedi
  spec, sezione "Ingredienti e tag letterali") — se trovato per nome, l'`Overwrite` aggiorna i
  campi sulla riga locale mantenendone l'id (non lo sostituisce mai con quello remoto). Poi ripulisce
  dal disco i tombstone locali di ricetta con `deletedAt` più vecchio della finestra di grazia
  (ingredienti/tag non hanno tombstone, niente da ripulire per loro).
  `SyncResult(recipesImported: Int, recipesDeleted: Int, catalogueImported: Int, exported: Int)` per
  la UI di stato.
- `fun observeLog(): Flow<List<SyncLogEntry>>` dove `SyncLogEntry(timestamp: Long, message: String,
  isError: Boolean)` — ogni tentativo di export/sync che fallisce (permesso SAF revocato, cartella
  sparita, file corrotto, eccezione di I/O) aggiunge una entry invece di propagare l'eccezione;
  anche i sync riusciti aggiungono una entry informativa breve (contatori di `SyncResult`), utile
  per capire "quando ha girato l'ultima volta" dal log stesso. Capped alle ultime ~50 entry (le più
  vecchie cadono), persistito in `:core:datastore` (non in memoria: deve sopravvivere al processo
  visto che il sync gira anche in background — Task 7).

- [x] Scrivere i test prima: export scrive il file giusto col contenuto atteso (ricetta, ingrediente,
      tag); import ricette applica `Insert`/`Overwrite`/`Delete`/`Skip` su un set di file preparati a
      mano; import ingrediente/tag con match per id; import ingrediente/tag **senza** match per id ma
      con match per nome locale (verifica che l'id locale non cambi e i campi si aggiornino); import
      ingrediente/tag senza alcun match (vero inserimento); pulizia tombstone di ricetta rispetta la
      finestra di grazia (parametrizzabile per test, es. 30 giorni, via `TimeProvider` già esistente);
      un errore di I/O durante l'export o l'import produce una `SyncLogEntry(isError = true)` e non
      fa fallire l'operazione locale chiamante; il log resta capped a ~50 entry anche dopo molte
      chiamate.
- [x] Implementare, usando `:core:transfer` (`ProportionCodec`) per encode/decode del contenuto file
      e `:core:sync` per la decisione.
- [x] Agganciare `RecipeRepositoryImpl.upsert`/`delete`, `IngredientRepositoryImpl.findOrCreate`/
      `setDensityData`, `TagRepositoryImpl.findOrCreateUserTag`: dopo il successo, se `syncEnabled`
      e la riga non è built-in, chiamare l'`export*` corrispondente (fire-and-forget, non deve far
      fallire l'operazione locale se la scrittura su SAF fallisce — loggare via `observeLog`, non
      propagare).
- **Ciclo Dagger**: iniettare `SyncRepository` direttamente in `RecipeRepositoryImpl`/
  `IngredientRepositoryImpl`/`TagRepositoryImpl` crea un ciclo (`SyncRepositoryImpl` dipende da
  `TransferRepository`, che dipende da `RecipeRepository`/`IngredientRepository`/`TagRepository`).
  Fix: `javax.inject.Provider<SyncRepository>` nei tre repository (`sync.get().exportX(...)`) —
  pattern standard Dagger per rompere un ciclo quando la dipendenza serve solo a runtime, non in
  fase di costruzione.
- **3 bug reali trovati dai test** di questo task (non solo dai test di `:core:sync`, che erano già
  verdi in isolamento):
  1. **`syncNow()` faceva push prima di pull.** Se un file per un id già presente anche in locale
     era più recente sul disco (scritto dall'altro device), il push lo sovrascriveva con lo stato
     locale (più vecchio) *prima* che il pull avesse la possibilità di leggerlo — un aggiornamento
     dell'altro device andava perso ad ogni sync in cui entrambi i device avevano toccato la stessa
     ricetta. Fix: pull sempre prima di push nell'ordine di `syncNow()`.
  2. **`TransferRepositoryImpl.WireRecipe.toRecipe()` non mappava `updatedAt`/`createdAt`** (aggiunti
     a `WireRecipe` in questo stesso task) — ogni ricetta risolta da `resolveRecipe` tornava con
     `updatedAt = 0`, quindi **ogni conflitto sarebbe stato deciso come se il file arrivato fosse
     sempre il più vecchio possibile**: la sincronizzazione sarebbe sembrata funzionare (nessun
     crash, nessun errore nel log) ma non avrebbe mai importato un aggiornamento vero. Trovato dai
     test dedicati di `SyncRepositoryTest`, non dai test isolati di `:core:transfer` (che testavano
     solo il round-trip del wire, non la sua applicazione).
  3. **SAF/`MimeTypeMap` può rinominare il file creato.** `DocumentFile.createFile(mimeType, name)`
     può appendere un'estensione dedotta dal mime type (osservato con `application/octet-stream` →
     `.bin` sotto Robolectric; un provider SAF reale può fare lo stesso per mime type generici) —
     il file finiva per chiamarsi `recipe-<id>.proportion.bin` invece di `recipe-<id>.proportion`,
     rompendo silenziosamente il successivo `findFile(name)` usato per sovrascrivere invece di
     duplicare. Fix: mime type vendor-specific (`application/x-proportion-sync`, mai in nessuna
     tabella `MimeTypeMap`) usato solo per `createFile`, non per la condivisione (`ProportionFile.
      MIME_TYPE` resta invariato lì).
- **Test seam**: `openFolder()`/`writeFile()`/`readFile()` trattano uno schema URI `file://` come
  percorso di test (`DocumentFile.fromFile`, I/O diretto via `java.io.File`, bypassando
  `ContentResolver`) — mai imboccato in produzione, dove il picker SAF restituisce sempre
  `content://`. Permette test Robolectric reali su una cartella temporanea senza mockare un
  `DocumentsProvider`.

### Task 6: UI Settings — sezione Sincronizzazione

**Files:** `feature/settings/.../SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`,
nuovo composable `SyncSection.kt`, `values/strings.xml` + `values-it/strings.xml`, test
(`SettingsScreenTest`, `SettingsViewModelTest`).

- [x] Toggle "Sincronizzazione abilitata". Accendendolo pianifica il job periodico (Task 7),
      spegnendolo lo cancella.
- [x] Picker cartella (`ActivityResultContracts.OpenDocumentTree`), persiste il permesso
      (`takePersistableUriPermission`), salva l'URI in preferenze.
- [x] Riga di stato: ultimo sync (data/ora), esito (`SyncResult`).
- [x] Pulsante "Sincronizza ora" → chiama `syncRepository.syncNow()` immediatamente (oltre al job
      periodico), per non dover aspettare fino a 4 ore dopo un cambio di cartella o un primo setup.
- [x] **Sezione errori**: se `observeLog()` contiene almeno una entry con `isError = true` dalle
      ultime N (es. 20) o dalle ultime 24-48h, mostra un banner/alert con l'ultimo errore in breve
      (non l'intero log). Un pulsante "Condividi log" apre l'intent di sistema
      (`RecipeSharing.shareText`, già esistente in `:core:ui`, stesso meccanismo già usato per
      condividere una ricetta come testo — nessun nuovo meccanismo di condivisione da scrivere) con
      tutte le entry del log formattate come testo semplice (timestamp leggibile + messaggio),
      utile per mandare il log a Marco per debug senza dover collegare il device.
- [x] Se sync disabilitato, la sezione mostra solo il toggle (coerente con lo stile esistente delle
      sezioni opzionali) — niente stato/log/errori quando non è mai stato attivato.

### Task 7: job periodico di sync (WorkManager)

**Files:** `:app` (o un nuovo, piccolo `:core:sync` companion se preferibile tenerlo fuori da
`:app` — valutare in fase di implementazione, non è una scelta architetturale critica) —
`SyncWorker.kt` (`CoroutineWorker`), aggancio Hilt (`HiltWorker`/`WorkManager` già richiede
`androidx.hilt:hilt-work` se non presente — verificare nel catalogo versioni), enqueue/cancel dal
punto dove il toggle "Sincronizzazione abilitata" cambia stato (Task 6).

Non serve un servizio sempre attivo: **periodico ogni 3-4 ore** è l'esplicita richiesta di Marco —
sufficiente perché non c'è nessuna notifica push da un device all'altro, solo file su una cartella
che Syncthing sincronizza per conto suo con il suo passo. `PeriodicWorkRequest` con
`repeatInterval` di 4 ore, nessun vincolo di rete (tutto locale/SAF), sopravvive a riavvii
(`ExistingPeriodicWorkPolicy.KEEP` sull'enqueue, così riattivare il toggle non duplica il job).

- [x] `SyncWorker.doWork()`: se `syncEnabled`, chiama `syncRepository.syncNow()`, cattura ogni
      eccezione (già in `observeLog()` via Task 5, ma un worker deve comunque non crashare —
      `Result.retry()` o `Result.failure()` a seconda del tipo di errore, mai propagare
      l'eccezione al processo).
- [x] Enqueue (`enqueueUniquePeriodicWork`, `ExistingPeriodicWorkPolicy.KEEP`) quando il toggle
      passa a on (sia all'avvio dell'app se già on da preferenze, sia dal punto dell'attivazione in
      Settings); `cancelUniqueWork` quando passa a off.
- [x] Test: `SyncWorker` con un `TestListenableWorkerBuilder` (pattern standard WorkManager test),
      verifica che chiami `syncNow()` e che un errore non faccia crashare il worker.
- Dove sono finiti: `SyncScheduler` (interfaccia, `:core:domain`), `SyncWorker` +
  `WorkManagerSyncScheduler` (`:core:data/sync/`, non `:app` — co-locati con `SyncRepositoryImpl`,
  che è già lì; `:app` non ha bisogno di conoscere WorkManager oltre alla riga di
  `Configuration.Provider`). `ProPortionApplication` implementa `Configuration.Provider` +
  `HiltWorkerFactory` iniettato. **Richiede anche una riga di manifest**: lint
  (`RemoveWorkManagerInitializer`) impone di rimuovere l'`androidx.startup.InitializationProvider`
  di default via `tools:node="remove"` su `androidx.work.WorkManagerInitializer` — senza,
  `verifyAll` fallisce in fase di lint (non a runtime). `SettingsViewModel.onSyncEnabledChange`
  chiama `syncScheduler.schedule()`/`cancel()`; `onSyncFolderChosen` esegue subito un
  `syncNow()` invece di aspettare fino a 4 ore.

### Task 8: verifica end-to-end su device reale

- [x] **Verificato (2026-09-03, singolo device, Fairphone 3, dati reali preesistenti — 2 ricette,
      "Torta di mele" e "Risotto allo zafferano")**: attivare il toggle + scegliere una cartella
      (SAF reale, `Download/proportion-sync/`) esporta subito tutto — 5 file (2 ricette + 3
      ingredienti letterali) apparsi sul filesystem con contenuto reale (`adb shell cat`
      confermato, JSON valido, ingredienti/densità corrette, nomi file esatti — niente `.bin`,
      conferma indiretta del fix mimetype). Poi, con la cartella già popolata: `pm clear`
      dell'app (equivalente a installazione pulita: DB vuoto, permesso SAF revocato), riapertura,
      toggle riattivato, stessa cartella riselezionata (nuovo consenso SAF) → "Sincronizza ora"
      automatico dopo la scelta cartella ha riportato **"5 esportate, 2 ricette importate, 0
      cancellate, 2 voci di catalogo"**, ed entrambe le ricette sono ricomparse nella Home. Le due
      cose che Marco ha chiesto esplicitamente di verificare sono confermate funzionanti.
  - **Nota, non un bug**: `cookCount`/`lastCookedAt`/`isFavourite` non sono nel formato wire (mai
    lo sono stati, nemmeno per backup/restore) — dopo l'import quei contatori ripartono da zero.
    Comportamento preesistente, coerente con backup/restore, ma vale la pena che Marco lo sappia:
    la sincronizzazione porta il contenuto della ricetta, non le statistiche d'uso locali.
  - **Non ancora verificato**: il test vero a due device via Syncthing (i quattro punti sotto)
    resta da fare — per esplicita scelta di Marco, tocca a lui.

- [ ] Sul Fairphone 3 (o due device/emulatori), configurare due cartelle sincronizzate (via
      Syncthing reale o anche solo una cartella condivisa via cavo/adb per il test, se Syncthing
      non è comodo da configurare in sessione): creare una ricetta su A, verificarla su B dopo
      "Sincronizza ora" (o attendendo il job periodico); modificarla su B, verificarla su A;
      cancellarla su A, verificarla sparita su B; modificare la stessa ricetta su entrambi offline
      prima del sync, verificare che vinca `updatedAt` più recente e che l'altra versione sia persa
      silenziosamente (comportamento atteso, da confermare che sia accettabile anche visto dal
      vivo).
- [ ] Creare un ingrediente letterale su A (o rispondere al prompt densità su una ricetta), impostare
      densità/peso-pezzo, verificare che compaiano su B dopo il sync senza dover ripassare dal
      prompt. Creare **lo stesso** ingrediente per nome indipendentemente su A e B prima di un
      sync, poi sincronizzare: verificare che sul catalogo non compaia una riga doppia.
      Analogo per un tag utente.
- [ ] Rimuovere/rinominare la cartella scelta (o revocare il permesso) e verificare che il prossimo
      sync produca una entry di errore visibile in Settings, e che "Condividi log" apra il chooser
      di sistema con un testo leggibile.
- [ ] Disattivare il toggle e verificare (via `adb shell dumpsys jobscheduler` o
      `WorkManager`/`adb shell cmd jobscheduler` sul device) che il job periodico risulti cancellato,
      non solo "non fa niente".
- [ ] `./gradlew verifyAll` verde.
- [ ] Aggiornare `docs/private/IMPLEMENTATION-STATUS.md`.
