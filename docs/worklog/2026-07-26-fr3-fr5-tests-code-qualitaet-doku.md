# 2026-07-26 — FR-3/FR-4/FR-5: Testlücken, Code-Qualität, Doku-Hygiene (#87/#88/#90–#93)

**Ziel:** Den Rest von Epic #94 (finale Review vor dem Feldeinsatz) abschließen: die
Test-Pflicht T1 (#87), die Test-Mittelfunde (#88) sowie die Sammelpakete Code-Qualität
Backend/Terminal/Portal (#90/#91/#92) und Doku-/Repo-Hygiene (#93). FR-1 (#80–#82) und FR-2
(#83–#86, #89) waren bereits gemergt.

## Vorgehen

Zerlegt und an die Spezialisten `terminal` und `backend` delegiert (AGENTS.md §4), Portal und
Doku im Hauptkontext. **Beide Subagenten brachen mitten in der Arbeit am API-Monatslimit ab** —
ihre Änderungen lagen unvollständig und ungeprüft im Arbeitsbaum. Der Hauptkontext hat sie
deshalb durchgesehen, die Lücken geschlossen (u. a. den fehlerhaften Test-Fixture-Aufbau in
`ExecutionStartGuardTest`, den kompletten deCONZ-Reconnect-Test) und alle Suiten selbst grün
gefahren. Konsequenz für künftige Arbeitspakete: Ergebnisse delegierter Agenten immer selbst
bauen und testen, nie auf den Abschlussbericht vertrauen.

## Erledigt

### Tests (#87 T1, #88 FR-3)
- **Regressionstest deCONZ-WS-Reconnect** (`DeconzEventListenerReconnectTest`): Der Simulator
  kann die WebSocket-Verbindung jetzt serverseitig kappen, ohne den Port aufzugeben
  (`DeconzSimulator#dropWebSocketConnections`, Handshake-Zähler für Assertions). Der Test
  belegt: nach dem Abbruch verbindet der `DeconzEventListener` selbständig neu und empfängt
  wieder Leistungsmessungen — und zwar mit **genau einem** neuen Handshake, was den
  `isReconnectRunning`-CAS gegen den Doppel-Reconnect absichert. Gegen den Vor-Fix-Stand
  (Issue #19 zurückgedreht) verifiziert: rot.
- **`InactivitySchedulerTest`** wartet auf Bedingungen statt auf Fristen von 1–2 ms Marge; die
  „noch nicht passiert"-Prüfungen laufen gegen eine 500-ms- statt 50-ms-Periode. Eine injizierte
  Uhr wurde bewusst verworfen: sie hieße, das `Thread.sleep` im Terminal-Kern nur für den Test
  zu ersetzen.
- **`CreditServiceAccountingHistoryTest`** erzeugt die Reihenfolge über gesetzte Zeitstempel
  statt über `Thread.sleep(5)` (Muster aus #40).
- **Portal-E2E**: Programmanlage für **DYNAMIC** ergänzt (P12b) — bis dahin deckte kein Test den
  Dynamisch-Zweig des Dialogs ab (andere Felder, eigene Validierung).

### Code-Qualität Backend (#90)
`syncGroupMembership` entdoppelt das `setValid*`-Triplikat in `UserGroupService`; die
Snapshot-Assembly liegt jetzt in einem `SnapshotService` und nutzt die Batch-Methode
`CreditService.getCredits(List)` (2·N → 2 Abfragen); die fachlichen Start-Wächter sind als
`ExecutionStartGuard` isoliert testbar (neuer `ExecutionStartGuardTest`, u. a. für den bis dahin
ungetesteten Standort-Wächter und die Prüf-Reihenfolge); `TerminalMaintenanceService` führt
statt zweier paralleler Maps eine Map auf einen Record.

### Code-Qualität Terminal (#91)
`TerminalDataService` trägt den Online/Offline-Datenzugriff aus dem `ElwaManager`-Singleton;
`CardLoginOutcome` bündelt Klassifikation und maskiertes Logging des Kartenlogins für beide
UI-Größen; `DeviceListEntry` beschreibt die Kachel-Darstellung als Zustandstabelle
(`APPEARANCES`) statt als `switch`-Anweisungsfolge — genau die Eigenschaft, deren Fehlen den
Fall-Through #82 verdeckte — und beendet den Steckdosen-Suchlauf-Executor beim Terminieren.
Dazu der echte Log-Bug in `ui/small` (`catch (FhemException e1)` loggte den `ActionEvent` der
äußeren Lambda), Währungsformatierung über `FormatUtilities`, `Vector`→`CopyOnWriteArrayList`,
`printStackTrace`→Logger, toter `Utilities.sha1` entfernt, eigener Monitor statt autoboxter
`Integer`-Konstante, Default statt `NumberFormatException` bei `fhem.port`, Tippfehler in
Bezeichnern.

**Nicht ganz verhaltensneutral, deshalb ausdrücklich benannt:** die Umstellung von `ui/small` auf
`FormatUtilities` ersetzt die Standard-Locale der JVM durch fest deutsche Währungsschreibweise.
Auf einem Pi-Image mit `LANG=C` sah der Bestätigungsdialog des kleinen Displays dadurch vorher
anders aus als das mittlere Display, das `FormatUtilities` längst nutzte – die Vereinheitlichung
ist gewollt. Damit die Anzeige nicht weiter vom Image abhängt, setzt der Startbefehl in
`setup.sh` jetzt `-Duser.language=de -Duser.country=DE`; `FormatUtilities` erzeugt seinen
Formatter außerdem je Aufruf statt einen geteilten, nicht threadsicheren Formatter zu halten.

### Code-Qualität Portal (#92)
`AbstractAdminListView<T>` trägt Kopfzeile, Grid, Aktionsspalte, Lösch-Bestätigung samt
`EntityInUseException`-Behandlung und das Broadcaster-Wiring; die fünf `Admin*View`-Klassen
liefern nur noch ihre Besonderheiten (Benutzer: zwei zusätzliche Aktionsknöpfe, gebündelte
Guthaben-Map). `Notifications` löst zehn wortgleiche `showError`/`showSuccess`-Kopien und fünf
Inline-Stellen ab. `PlaceholderView` (toter Code) entfernt.

### Doku & Repo-Hygiene (#93)
Fehlende Worklog-Index-Zeile nachgetragen; `CHANGELOG.md`-`[Unreleased]` von 197 auf 73 Zeilen
gekürzt (1–2 Zeilen je Eintrag, doppelte Rubriken zusammengeführt) und die technischen Details
als fünf Änderungslog-Zeilen in `05-migration-plan.md` nachgetragen (AP7/KB-Refactor,
Offline-Replay-Härtung II, finale Review, FR-1, FR-2 fehlten dort komplett); zwei tote
Markdown-Links, stale `Common`-Erwähnung in der Root-`pom.xml`, toter `doc/`-Eintrag in
`.dockerignore`, DB-Env-Var-Doppelbenennung in `.env.example`; zwei gegenstandslose „Offene
Fragen" im Migrationsplan mit Begründung geschlossen; `LocationOccupiedException` samt toter
`SQLException`-Deklarationen und die wirkungslose `MANIFEST.MF` entfernt; Mojibake,
TODO-Javadoc und englische Rest-Kommentare im Alt-fhem-Code bereinigt; Testzahlen in
`08-test-plan.md` neu am Code gezählt.

## Entscheidungen

- **Kein Vaadin-`Binder`-Umbau der neun Dialoge** (#92, letzte Checkbox): das ist ein eigener,
  verhaltensnaher Umbau der Formularvalidierung und gehört nicht in ein Aufräum-Paket kurz vor
  dem Feldeinsatz. Bleibt als benannte Rest-Aufgabe in #92.
- **Fernwartungs-Toolbar des Dashboards nicht ausgelagert** (#92): `AdminDashboardView` ist mit
  der Toolbar eng über Ladeindikator und Broadcaster-Zustand verzahnt; der Nutzen (~65 Zeilen)
  rechtfertigt das Risiko am Ende des Pakets nicht. Ebenfalls in #92 vermerkt.

## Nachtrag: zweites Review-Gate über den gesamten Branch

Der `code-reviewer` fand über den kompletten Branch-Diff nichts Blockierendes, aber zwei
Testlücken, die beide echt waren:

- **Der Tiebreak-Fix hatte keinen wirksamen Test.** `findByUser_IdOrderByDateDescIdDesc`
  entstand, damit zwei Buchungen mit **identischem** Zeitstempel eine feste Reihenfolge haben -
  die neuen Tests arbeiteten aber mit weit auseinanderliegenden Zeitstempeln und wären auch ohne
  den Tiebreak grün gewesen. Jetzt gibt es zwei Fälle mit demselben Zeitpunkt; gegen den
  Vor-Fix-Stand verifiziert (Historie kommt dort in umgekehrter Reihenfolge zurück).
- **Die Paritäts-Fixture ließ genau die zwei Stellen aus, an denen die Implementierungen
  strukturell verschieden sind:** es gab keinen Fall mit `abgerechnete Dauer < Maximaldauer`
  und keinen mit „Gruppe vorhanden, aber rabattfrei" (das Backend fängt das über den
  `else`-Zweig seiner Rabattkette ab, das Terminal über eine eigene Vorab-Prüfung). Die Fixture
  hat dafür zwei zusätzliche Spalten (`billedDurationSeconds`, `groupPresent`) und fünf neue
  Fälle bekommen: 18 statt 13.

Weiter behoben: Vollständigkeits-Guard für die Zustandstabelle der Gerätekachel (ein künftiger
Zustand ohne Tabellenzeile fällt beim Klassenladen auf statt als NPE im FX-Thread), `try/finally`
im Steckdosen-Suchlauf (eine gescheiterte Registrierung ließ die Kachel sonst im
„suche gerade"-Zustand hängen), entdoppelte `showFailure`-Überladung, toter `Utilities.sha1`
entfernt, `.vaadin-node-tasks.lock` in `.gitignore`.

## Tests

- Backend-JUnit: **315/315** grün (`backend/run-backend-tests.sh`; 287 vor diesem AP).
- Client-Suite: **106/106** grün (`Client-Raspi/run-ui-tests.sh`; 88 vor diesem AP, dazu die 18
  Paritätsfälle).
- Portal-E2E (Playwright, `backend/e2e`): **27/27** grün, inkl. P12b und der Prüfung der
  ausgelagerten Fernwartungs-Kopfzeile.

## Offen / nächster Schritt

> **Nachtrag (später am selben Tag):** die zwei oben vertagten #92-Punkte sind inzwischen
> erledigt – siehe
> [portal-dialog-geruest-und-fernwartungs-toolbar](2026-07-26-portal-dialog-geruest-und-fernwartungs-toolbar.md).
> Der Vaadin-`Binder` wurde dabei geprüft und **begründet verworfen** (Prüfzeitpunkt und
> fehlende Formular-Bean), die Fernwartungs-Kopfzeile liegt in `LocationMaintenanceHeader`.
> Ebenfalls nachgezogen: die Preis-Parität Terminal ↔ Backend über die gemeinsame Fixture
> `test-fixtures/pricing-parity.csv` (der letzte „erwägen"-Punkt aus #91).

- Epic #94 ist damit inhaltlich abgeschlossen.
- Issue #97 (stiller Fehlschlag der Readiness-Gates im Testharness) ist unabhängig und offen.
- Als Nächstes: Generalprobe nach Spec 0001 (Cutover-Probelauf, Migrations-Dry-Run,
  Restore-Probe, Ausfall-Drills, Soak-Test), dann Pilotphase.

## Referenzen

- `docs/reviews/final/` (R3a/R3b/R3c/R4/R6/R7 + SYNTHESE) · Epic #94 · ADRs 0016–0021
- `docs/kb/05-migration-plan.md` (Änderungslog) · `docs/kb/08-test-plan.md`
