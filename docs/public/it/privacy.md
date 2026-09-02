# Privacy

ProPortion è un'app offline. Questo documento dice esattamente cosa fa e cosa non fa con i tuoi
dati.

- **Nessun account.** Non esiste una registrazione, un login, o un profilo utente.
- **Nessuna sincronizzazione e nessun server.** L'app non ha un proprio backend: non c'è nessun
  server con cui parla, quindi non c'è nessun posto dove i tuoi dati vengano inviati.
- **Nessuna raccolta di dati e nessuna analisi d'uso.** L'app non include librerie di analytics,
  tracciamento o telemetria di alcun tipo.
- **Nessun accesso alla rete oltre a quanto richiede Android stesso.** L'app non apre connessioni
  di rete proprie.
- **Tutti i dati restano sul dispositivo**, in un unico database Room locale: ricette, ingredienti,
  tag, scalature salvate, lista della spesa e preferenze.
- **Un file `.proportion` viene scritto solo quando lo chiedi tu esplicitamente** — condividendo
  una ricetta, esportando la libreria, o facendo un backup. L'app non scrive né invia nulla in
  autonomia.
- **Il backup lo scegli tu.** Quando fai un backup, è Android stesso (tramite la finestra di
  sistema per scegliere dove salvare un file) a decidere dove va il file: l'app non ha un percorso
  o una cartella nascosta di suo, e non richiede permessi di accesso allo storage.

In breve: se non condividi o non fai un backup tu stesso, nessun dato della tua libreria esce mai
dal telefono.
