# elwasys – Knowledge Base (KB)

Diese Knowledge Base ist der zentrale Ablageort für die Überarbeitung / Modernisierung
des elwasys-Projekts. Hier werden Recherche, Architektur, Datenmodell, Migrationsplan,
Teststrategie und die Remote-/Cloud-Init-Umgebung dokumentiert und fortlaufend gepflegt.

> Jede Arbeits-Session nutzt ihren eigenen Branch (`claude/...`); die KB auf dem
> jeweils aktuellsten Stand (master + gemergte PRs) ist die zentrale Wahrheit.
> Einstieg für neue Sessions: [/AGENTS.md](../../AGENTS.md) → „Aktueller Stand" (unten)
> → [05-migration-plan.md](05-migration-plan.md).

## Inhalt

| Dokument | Beschreibung |
|----------|--------------|
| [00-overview.md](00-overview.md) | Was ist elwasys? Zweck, Features, High-Level-Bild |
| [01-architecture.md](01-architecture.md) | Module, Komponenten, Kommunikationswege, Technologie-Stack |
| [02-data-model.md](02-data-model.md) | Datenbankschema (PostgreSQL), Entitäten, Beziehungen |
| [03-modules.md](03-modules.md) | Detaillierte Beschreibung der Module (Client-Raspi, backend) |
| [04-build-and-run.md](04-build-and-run.md) | Build, Ausführung, Konfiguration, Deployment, CI |
| [05-migration-plan.md](05-migration-plan.md) | Modernisierungsplan: Rahmenbedingungen, Komponenten-Inventur, Zielarchitektur, Roadmap (lebendes Dokument) |
| [06-ui-tests.md](06-ui-tests.md) | UI-Test-Strategie für Client (JavaFX) und Portal (Vaadin) |
| [07-cloud-init.md](07-cloud-init.md) | Remote-/Cloud-Init-Umgebung zum Ausführen von Build & Tests |
| [08-test-plan.md](08-test-plan.md) | Testplan: Vertiefung der Frontend-Tests (Client & Portal) |

Verwandte Wissensablagen (außerhalb der KB): tragende Entscheidungen als ADRs in
[`../architecture/`](../architecture/), das chronologische Arbeitsjournal (früher
„Status-Log" hier) in [`../worklog/`](../worklog/README.md), nennenswerte Änderungen in
[`../../CHANGELOG.md`](../../CHANGELOG.md).

## Aktueller Stand

> **Der eine Snapshot** „Wo stehen wir / was ist der nächste Schritt". **Überschreiben**,
> nicht anhängen – die Historie liegt im [Worklog](../worklog/README.md). Erste
> Anlaufstelle für jede neue Session.

- **Stand:** Die Modernisierung ist durch alle Phasen der Roadmap gelaufen: Phase 0
  (Sicherheitsnetz) bis Phase 6 (Produktivumschaltung/Cutover) sind umgesetzt. Zielbild
  erreicht – zentrales Spring-Boot-Backend mit eingebautem Vaadin-Flow-Portal, Terminals
  ohne Direkt-DB-Zugriff (nur REST-API + WebSocket + Standort-Token), DB-Rollen gehärtet,
  Alt-Portal und `Common`-Modul entfernt (Root-Reactor = 2 Module: Client-Raspi, backend).
  Das Doku-/Agenten-Setup wurde auf die **agentic-baseline**-Struktur umgestellt
  (AGENTS.md als Single Source of Truth, `docs/`-Wissenssystem, `.claude/`-Agenten). Zusätzlich
  gibt es seit 2026-07-22 einen **Demo-Modus** (Profil `demo`, `DemoDataSeeder` + `backend/run-demo.sh`)
  mit wiederverwendbarem Beispielbestand fürs visuelle UI-Prüfen (siehe
  [06-ui-tests.md](06-ui-tests.md) „Demo-Daten", [04-build-and-run.md](04-build-and-run.md) „Demo-Modus").
  Seit 2026-07-22 lief die **Pre-Launch-Review** (Epic #66): **AP1 (Offline-/Replay-Kern,
  #16/#17/#18/#54/#59) ist behoben** – privilegierter Replay-Nachbuchungs-Pfad, Client-Replay
  robust (Dead-Letter/Paar-Reihenfolge/NPE/`clear()`-Race), Zeitstempel-Invariante `stop ≥ start`
  + Preis-Deckel, Uhren-Plausibilität (siehe [ADR 0016](../architecture/0016-offline-replay-haertung.md)).
  **AP2 (Terminal-Stabilität & Aufräumen, #19/#27/#28/#51/#52/#53/#55/#56/#57/#58/#61) ist
  behoben** – deCONZ-WS-Reconnect nach Abbruch (#19), kein WS-Client-/Listener-Leak beim
  portal-ausgelösten Neustart (#27), Terminal-Nebenläufigkeit gehärtet (`ConcurrentHashMap` +
  Retry-Guard gegen Doppel-Finish, #28), Watchdog-`continue` (#51), FX-Thread-Disziplin (#52),
  kaputte 2xx nicht mehr als Offline (#53), Journal-`DSYNC` (#55), RFID-Log-Maskierung +
  deterministisches INFO-Log (#56), Resume-Null-Check (#57), CSPRNG-deCONZ-Passwort (#58),
  toter SMTP-Code raus (#61).
  **AP3 (Geld-/Abrechnungs-Integrität, #20/#22/#29/#36/#41) ist behoben** – pessimistisches
  Nutzer-Zeilen-Locking auf den Geldpfaden + Advisory-Lock je Gerät im Start-Pfad und frisch
  gesperrter Finish (gegen Doppelstart/Doppelabrechnung/negatives Guthaben, #20), Idempotenz
  gehärtet (Key-Längen-400, Advisory-Lock gegen Race/kein 500, `operation`-Prüfung + Replay
  nach Löschung ohne 404, #29/#41), Betragsvalidierung in Service+Dialog (#22),
  Benachrichtigung per `AFTER_COMMIT`-Event außerhalb der Finish-Transaktion (#36) – siehe
  [ADR 0017](../architecture/0017-abrechnungs-integritaet-locking.md).
  **AP4 (Auth & Security, #21/#23/#24/#25/#26/#42/#44/#45/#46/#47/#48) ist behoben** – die drei
  🧩-Auftraggeberentscheidungen ([ADR 0018](../architecture/0018-ap4-auth-security-entscheidungen.md))
  umgesetzt (Reset-Enumeration neutralisiert + Rate-Limit #24, Passwort-Mindestlänge ≥ 8 #44,
  Standort-Token minimal/Restrisiko dokumentiert #43) samt Härtungen: regex-freier Kartenlogin
  mit strenger Formatvalidierung (#21), Guard gegen case-only-Username-Kollision (#23),
  In-Memory-Brute-Force-Limit für den Portal-Login mit **neutraler** Sperr-Meldung (kein
  Enumeration-Orakel, #25), verdrahtete Fernwartungs-Standortprüfung (#26), uuid-Validierung
  (#42), gedrosseltes Token-`last_used_at` (#45), Alphabet-Fix (#46), Reset-Robustheit bei
  doppelter Adresse (#47). Restrisiken (Timing-Orakel Login/Reset, #48 Sessions, #46 Klartext-
  Admin-Mail, #23 TOCTOU) bewusst akzeptiert und in
  [05-migration-plan.md](05-migration-plan.md) („Restrisiken Auth & Security") festgehalten.
  **AP5 (Portal-Performance/CRUD/Tests/Datenmodell, #30/#37/#38/#39/#40/#49/#50) ist behoben** –
  Dashboard-Historie lazy-paginiert und Guthaben-Spalte gebündelt geladen (`getCredits`, zwei
  statt 2·N Abfragen, fachlich identisch), V11-Indizes auf `executions`/`credit_accounting`
  (#30/#37); Demo-Seeder bricht gegen eine produktive DB ab (Signal: Admin-Passwort gesetzt,
  Marker `anna` fehlt, #38); `UserEntity.password` auf 255 + Soft-Delete-Username auf Spaltenbreite
  gekürzt/idempotent (#39); Testdeterminismus (#40); Geräte-Lösch-Wächter (`EntityInUseException`
  bei laufender Ausführung), Lösch-Bestätigung im ExpiredExecutions-Dialog und Doppelklick-Schutz
  auf Geldknöpfen (#49); RouteAccess per Classpath-Scan + neue E2E (Auszahlung/NotEnoughCredit,
  Benutzer-Löschung, Reset-Link, #50).
  **AP6 (Deployment/Betrieb/Cutover, #31/#32/#33/#34/#35/#60/#62/#63/#64) ist behoben** – Backend/
  Compose/Helm fest auf Zeitzone `Europe/Berlin` + Preflight-/Runbook-Gate (#31); Dauerbetrieb
  ausgearbeitet: Purge-Job für `terminal_idempotency_keys` (30 Tage, konfigurierbar) und zwei
  Custom-Health-Indicators (Standort ohne Terminal-WS, offene abgelaufene Executions) → 503/Alerting,
  Runbook-Kapitel „Dauerbetrieb" (Backup/Alerting/Log-Rotation/Retention) (#32); #60 (Terminal-
  Totalausfall, Steckdose bleibt an → unbeaufsichtigter Weiterlauf) als Betriebsrisiko + Health-Alert
  dokumentiert; Watchdog merkt fehlgeschlagene Zielversion (keine Update/Rollback-Endlosschleife, #34);
  TLS-Pflicht (Compose bindet nur `127.0.0.1`, `https://` als Runbook-Gate, #35); Client-Jar-Integrität
  per SHA-256 (#62); `setup.sh` idempotent + sudoers-Regel + Kill-Exit-Code geprüft (#63); Repo-/GHCR-
  Referenzen auf **`ullriti/elwasys`** vereinheitlicht, Preflight-Migrationsversion abgeleitet, Helm-
  Passwort-Guard + CI-`helm`-Lint (#64). **Vaadin-Lizenz-🧩 (#33): Restrisiko bewusst akzeptiert**
  ([ADR 0019](../architecture/0019-ap6-vaadin-lizenz-restrisiko.md)). Ein späterer Repo-Umzug nach
  `kabieror/elwasys` wird über Issue #75 nachgehalten.
  **AP7 (KB-Überarbeitung) ist behoben** – die Knowledge Base ist nach AP1–AP6 wieder auf dem
  Ist-Stand (u. a. `03-modules.md` code-verifiziert korrigiert – Replay-/Locking-/Idempotenz-/
  Dashboard-/Auth-Aussagen –, Health-Pfade + Compose-Härtung in `04-build-and-run.md`,
  Änderungslog AP4–AP6 in `05-migration-plan.md`); damit ist die Pre-Launch-Review
  (Epic #66, AP1–AP7) vollständig abgeschlossen.
  Nachgelagert wurden die drei aus AP1 ausgelagerten Defense-in-Depth-/Datenintegritäts-Follow-ups
  des Offline-/Replay-Pfads behoben (**#67/#68/#69**, [ADR 0021](../architecture/0021-offline-replay-haertung-ii.md)):
  der privilegierte Replay-Pfad verlangt jetzt einen plausiblen Zeitstempel und lehnt einen
  fehlenden/Zukunfts-Zeitstempel ab (`422`); ein „jetzt"/verdächtig aktueller wird angenommen und
  nur auditiert (legitime Sofort-Nachmeldung, Schwelle `elwasys.offline.replay-min-backdating`),
  ein zu alter auf Serverzeit gesetzt + auditiert jede Nachbuchung (#67); eine beim Replay entstehende Geister-Execution (START ok,
  FINISH fachlich abgelehnt) wird per kompensierendem `abort` aufgeräumt + laut alarmiert (#68);
  das Dead-Lettern verliert bei Write-Fehler keinen Eintrag mehr (Write-before-Remove) und
  begrenzt den Busy-Loop über einen neustartfesten Fehlversuchszähler (#69).
  Die **finale Review vor dem Feldeinsatz** nach
  [Spec 0001](../specs/0001-finale-review.md) ist **vollständig abgeschlossen** (alle
  neun Tracks + Synthese, Reports in [`../reviews/final/`](../reviews/final/)). Ergebnis
  ([SYNTHESE.md](../reviews/final/SYNTHESE.md)): Ziele erreicht, Geld-/Auth-Pfade halten
  stand, Code-/Doku-Qualität gut – aber **7 Hoch-Findings vor dem Feldeinsatz beheben**
  (H1 Replay-Paar-Atomizität `OfflineGateway`, H2 Listener-Leak `ExecutionManager:326`,
  H3 Fall-Through `DeviceListEntry`, H4 kein verdrahteter Alarmkanal, H5 kein geprobter
  Restore-Weg, H6 Cutover-Preflight kennt V11 nicht, H7 `ELWASYS_PORTAL_BASE_URL` fehlt
  in Compose/Helm) plus Regressionstest-Pflichten (deCONZ-Reconnect, Replay-Abbruch).
  **FR-1 (Terminal-Code-Fixes H1–H3, #80/#81/#82) ist behoben**: `OfflineGateway#replay`
  entfernt einen erfolgreich nachgemeldeten `START` erst zusammen mit seinem Terminator
  (Paar-Atomizität über Lauf-Grenzen, #80); `ExecutionManager`s sechs Register-/
  Unregister-Listener-Methoden laufen jetzt über zwei generische Helfer (behebt den
  Listener-Leak in `stopListenToExecutionStartedEvent` UND, zusätzlich beim Fix entdeckt,
  denselben Copy-Paste-Fehler in `stopListenToExecutionErrorEvent`, #81);
  `DeviceListEntry#refresh` nutzt einen Arrow-`switch` (schließt den Fall-Through
  `DISABLED`→`UNREGISTERED` strukturell aus, #82). Je ein gegen den Vor-Fix-Stand
  verifizierter Regressionstest.
  **FR-2 (Betrieb H4–H7 + #89-devops, #83–#86/#89) ist behoben**: der Alarmkanal ist jetzt
  **mitgeliefert und verdrahtet** (`deploy/monitoring/` pollt `/actuator/health/operational`
  – Nichterreichbarkeit zählt als Alarm –, plus Cert-Ablauf und Plattenplatz, Zustellung per
  Pushover/Mail, systemd-Timer/Cron; H4/#83); der **Restore** ist Schritt-für-Schritt
  ausgearbeitet und geskriptet (`deploy/backup/restore-db.sh`/`backup-db.sh`, RPO/RTO,
  Backup-Scope inkl. Secrets/Terminal-Properties/SD-Journal; H5/#84); der Cutover-Preflight
  `verify-cutover-migration.sh` leitet die erwartete Flyway-Historie aus dem Migrationsordner
  ab (kennt damit V11 und künftige Migrationen; H6/#85); `ELWASYS_PORTAL_BASE_URL` wird in
  Compose und Helm mit Guard durchgereicht (H7/#86). Dazu die Betriebs-Mittelfunde (#89):
  Compose zieht das GHCR-Release-Image (lokaler Build als Overlay), Helm-Image-Tag-Guard +
  `appVersion`-Bump im Release, NTP am Terminal (`setup.sh` + Watchdog). Drei neue **Offline-
  Selbsttests** (Alerting, Restore-Dry-Run, Flyway-Historie-Ableitung) hängen im neuen
  CI-Job `cutover-scripts`. Der echte Restore-/Alarm-/Cutover-Lauf bleibt bewusst ein
  Generalprobe-Schritt.
  Auf Auftraggeber-Entscheidung (2026-07-26) kamen zwei Punkte hinzu: der **externe
  Uptime-Monitor ist Pflicht-Stufe 2** neben dem lokalen Poll-Skript (fällt der Host ganz aus,
  schweigt der lokale Poller – Endpoint-Check oder Dead-Man's-Switch, Alarm-Probe für beide
  Stufen), und die **Dead-Letter-Sichtbarkeit (#89) ist umgesetzt** statt vertagt
  ([ADR 0022](../architecture/0022-dead-letter-sichtbarkeit.md)): das Terminal meldet
  Dead-Letter- und fehlgeschlagene Geister-Kompensationen über den bestehenden
  Wartungs-WebSocket (`OFFLINE_INCIDENT`, persistente Outbox mit Quittung), das Backend
  persistiert sie (Migration `V12`) und hält über den `OfflineIncidentHealthIndicator` den
  Betriebsalarm, bis ein Admin den Vorfall in der neuen Portal-Ansicht **Offline-Vorfälle**
  quittiert (Vorfälle bleiben als Beleg erhalten, kein Purge).
  **FR-3/FR-4/FR-5 (#87/#88/#90–#93) sind behoben** und schließen Epic #94 ab: der
  **deCONZ-WS-Reconnect hat jetzt seinen Regressionstest** (Simulator kappt die Verbindung
  serverseitig ohne Portverlust; geprüft werden Wiederverbinden UND genau ein Handshake,
  also der CAS-Guard gegen Doppel-Reconnect, T1/#87); Portal-E2E deckt die **DYNAMIC**-
  Programmanlage ab und zwei Testklassen warten auf Bedingungen statt auf Millisekunden-
  Fristen (#88); die Aufräum-Refactors sind erledigt – Backend `SnapshotService`/
  `ExecutionStartGuard`/entdoppelte Gruppen-Zuordnung (#90), Terminal `TerminalDataService`/
  `CardLoginOutcome`/Gerätekachel als Zustandstabelle plus ein echter Log-Bug in `ui/small`
  (#91), Portal `AbstractAdminListView` für die fünf Admin-Listen und gemeinsame
  `Notifications` (#92) –, dazu die Doku-/Repo-Hygiene (#93: Worklog-Index, gekürztes
  CHANGELOG mit Details im Migrationsplan-Änderungslog, tote Links/Dateien/Kommentare,
  zwei geschlossene „Offene Fragen"). **#92 ist inzwischen vollständig**: die neun
  Formular-Dialoge teilen sich `AbstractFormDialog` (Kopf-/Fußzeile) und `FormValidation`
  (Feldprüfungen, mit Unit-Test), die Fernwartungs-Kopfzeile des Dashboards liegt in
  `LocationMaintenanceHeader`. Ein Umstieg auf Vaadins `Binder` wurde geprüft und **begründet
  verworfen** (er prüfte beim Tippen statt beim Speichern und bräuchte pro Dialog eine
  künstliche Formular-Bean – siehe Worklog). Neu ist außerdem die **modulübergreifende
  Preis-Parität**: `OfflinePricing` (Terminal) und `PricingService` (Backend) prüfen dieselbe
  Fixture `test-fixtures/pricing-parity.csv`, eine Divergenz zwischen Offline- und
  Online-Abrechnung fällt damit als roter Test auf statt beim Nutzer (#91). Das
  `code-reviewer`-Gate lief über den gesamten Branch; seine Befunde (fehlende Tests für die
  Fehlermeldung des Dialog-Gerüsts und für die Fernwartungs-Kopfzeile, Detailtext vor dem
  Login) sind behoben. Suiten: **Backend 315/315, Client 106/106, Portal-E2E 27/27** (vor diesem Arbeitspaket: 287 / 88 / 26).
- **Versionierung & Pakete (seit 2026-07-27):** Die Version berechnet **GitVersion** aus Tags
  und Conventional Commits (`GitVersion.yml`); die Reihe startet bei **1.0.0** (letzter
  veröffentlichter Stand vor dem Umbau: 0.4.2), Tags tragen kein `v`-Präfix.
  `.github/workflows/release.yml` (ersetzt `maven-publish.yml`) bedient zwei Kanäle: jeder
  **relevante** Merge auf `master` erzeugt ein **Vorab-Release** (als Pre-Release markiert –
  `/releases/latest` liefert es nicht aus, die Terminals im Feld sehen es also nicht; ein
  Test-Terminal zieht es über `.update-target`), ein **stabiles Release** wird bewusst manuell
  ausgelöst. Veröffentlicht werden Terminal-fat-jar + `.sha256` als Release-Asset, das
  Backend-Image nach GHCR (Tag-Leiter, OCI-Labels, SBOM, Provenance) und der Helm-Chart als
  OCI-Artefakt. `Utilities.APP_VERSION` kommt jetzt aus dem Jar-Manifest statt aus einem
  `sed`-Eingriff, und reine `docs:`/`chore:`/`style:`/`test:`/`ci:`-Pushes auf `master` lösen
  weder CI noch Release aus (`scripts/commit-triggers-build.sh`). Siehe
  [ADR 0023](../architecture/0023-gitversion-und-paketbereitstellung.md) und
  [04-build-and-run.md](04-build-and-run.md).
- **Portal-Design (UI-Redesign v2, seit 2026-07-27):** Das Portal liefert das **Design v2**
  aus der vom Auftraggeber gelieferten Spezifikation
  [`docs/specs/0002-ui-design/v2/`](../specs/0002-ui-design/v2/README.md) aus – im **vollen**
  Umfang, also einschließlich der funktionalen Ergänzungen des Prototyps (Freitext-Filter je
  Liste, zusätzliche sortierbare Spalten) und des Restzeit-Fortschrittsbalkens auf den
  Gerätekarten. Kopfbalken 64 px mit Logo-Mark, Ansichtsname und Benutzer-Chip mit
  Initialen-Avatar; Pillen-Navigation; Grids ohne Zebra-Streifen und Spaltentrenner; Dialoge
  mit Schließen-Kreuz, feststehendem Kopf/Fuß und Abschnittsgliederung; Login als
  zweigeteilter Bildschirm. Weiterhin **Laufzeit-CSS statt kompiliertem Theme**
  (`ElwasysAppShell#configurePage` → `portal-theme.css`; Lizenz-Rahmenbedingung aus
  [ADR 0019](../architecture/0019-ap6-vaadin-lizenz-restrisiko.md) unverändert). Fachlich
  ändert sich nichts – gleiche Views, Felder, Validierung und Meldungen. Neu im UI-Paket:
  `ui/component/NavbarViewName` (Ansichtsname aus der `@PageTitle`-Annotation) und
  `ui/component/ListFilterField` (ein Filterfeld, eine Suchsemantik für alle drei Listen).
  **Für Tests wichtig:** durch `NavbarViewName` steht jeder Ansichtsname **zweimal** im DOM –
  ein `getByText('<Ansichtsname>', { exact: true })` scheitert am Strict Mode (Regel in
  [06-ui-tests.md](06-ui-tests.md)). Die optische Abnahme läuft über den Screenshot-Durchlauf
  `backend/e2e/tests-shots/portal-shots.spec.ts` (bewusst außerhalb des `testDir`, nicht im
  CI). Kein ADR nötig: Umsetzung einer gelieferten Design-Spezifikation, die tragenden
  Entscheidungen stehen in ADR 0003/0019. Details:
  [Worklog](../worklog/2026-07-27-ui-redesign-v2.md).
- **Standort-Token-Verwaltung im Portal (seit 2026-07-28):** Terminal-Tokens lassen sich jetzt
  im Admin-Portal verwalten – Standorte → Zeilenaktion „Terminal-Tokens verwalten" öffnet
  `TerminalTokenDialog` (auflisten, neu erzeugen mit einmaliger Klartext-Anzeige, widerrufen).
  Damit ist die Festlegung aus [ADR 0018](../architecture/0018-ap4-auth-security-entscheidungen.md)
  (#43: „kein Admin-UI, CLI genügt") auf Auftraggeber-Entscheidung **revidiert**
  ([ADR 0024](../architecture/0024-standort-token-verwaltung-im-portal.md)) – Anlass war die
  Inbetriebnahme eines weiteren Terminals, für die der CLI-Weg Server-Zugriff verlangt hätte.
  Token-Design unverändert (nur SHA-256-Hash in der DB, kein `expires_at`, gleicher
  Blast-Radius); die CLI (`TerminalTokenCliRunner`, `deploy/cutover/02-issue-terminal-tokens.sh`)
  bleibt für Erstinbetriebnahme/Cutover bestehen und nutzt denselben `TerminalTokenService`.
  Suiten: **Backend 341, Portal-E2E 32** (neu: P32/P33). Der Review-Nachlauf ergänzte die
  Fehlerbehandlung beider Schreibpfade des Dialogs, einen serverseitigen Test für das Leeren
  der Klartext-Anzeige und zwei Bestandsfixes, die dabei auffielen: Knöpfe mit
  `setDisableOnClick` blieben nach dem Klick tot (Guthaben „Buchen", Abrechnen der verfallenen
  Ausführungen) – die Regel dazu steht jetzt in [03-modules.md](03-modules.md), Portal-UI.
  Details: [Worklog](../worklog/2026-07-28-standort-token-portal-ui.md).
- **Nächster Schritt:** **Generalprobe nach [Spec 0001](../specs/0001-finale-review.md)**
  (Cutover-Probelauf, Migrations-Dry-Run mit Produktivdaten-Kopie, Backup-Restore-Probe,
  Ausfall-Drills, Alarm-Probe beider Stufen, Soak-Test, Vor-Ort-Kalibrierung), dann Pilotphase
  und **Live-Gang** (Cutover nach
  [`deploy/CUTOVER-RUNBOOK.md`](../../deploy/CUTOVER-RUNBOOK.md)). Davor steht die **optische
  Abnahme des Designs v2** durch den Auftraggeber (Screenshot-Durchlauf). Unabhängig davon offen:
  Issue #97 (Readiness-Gates des Testharness scheiterten still). Neue Vorhaben vorab als Spec
  in [`../specs/`](../specs/README.md) und Entscheidungen als ADR festhalten.
  Die Detail-Roadmap/Restpunkte stehen in [05-migration-plan.md](05-migration-plan.md).
- **Prozess:** PRs folgen seit 2026-07-27 dem
  [PR-Template](../../.github/pull_request_template.md) (Was · Warum ·
  nutzersichtbare Änderungen · wie getestet · Review-Gate · Sicherheit · offene
  Punkte); der PR-**Titel** folgt derselben Conventional-Commits-Konvention wie
  ein Commit-Subject.
- **Details:** siehe den jeweils letzten Eintrag im [Worklog](../worklog/README.md).

## Regeln für die KB

- Nach jedem Arbeitspaket aktualisieren; „Aktueller Stand" oben überschreiben und einen
  Worklog-Eintrag anlegen.
- **KB-Artikel = Ist-Zustand.** Der Body der Artikel (`00`–`08`) beschreibt nur den aktuellen
  Stand (Präsens) – **kein** Durchstreichen, **keine** „neu/entfernt/seit Phase X"-Marker im
  Fließtext, keine reproduzierten Alt-Artefakte. Historie je Artikel als datierter
  `## Historie`-Pointer-Footer (verlinkt auf Worklog/ADR/Änderungslog, dupliziert nicht).
  Details: [ADR 0020](../architecture/0020-kb-ist-zustand-und-historie-footer.md). Ausnahmen:
  [05-migration-plan.md](05-migration-plan.md) (bewusst historisch/Roadmap) und der „Aktueller
  Stand"-Snapshot oben.
- Entscheidungen des Auftraggebers/Teams als **ADR** in [`../architecture/`](../architecture/)
  festhalten (inkl. Begründung); der Roadmap-/Entscheidungskontext bleibt in
  [05-migration-plan.md](05-migration-plan.md).
- **Wissen gehört ins Repo, nicht in den lokalen User-Speicher** (`~/.claude/`,
  `#`-Memory) – siehe [`../worklog/README.md`](../worklog/README.md).
- **Terminal-Startbefehl & Cutover-Bereitschaft (seit 2026-07-27):** Vor der Umstellung fiel
  bei der Issue-Triage ein Blocker auf, der in keinem Issue stand: das Cutover-Runbook schickt
  Bestandsgeräte über `update.sh`, dessen Neustart (`sudo killall java`) aber die
  Supervisor-Schleife in `run.sh` voraussetzt. Die Feldgeräte fahren noch den **Einmalstart**
  aus dem ursprünglichen `setup.sh` (vor Phase 6 AP3) – dort hätte der Kill das Terminal
  dunkel zurückgelassen, samt Auto-Rollback des Watchdogs. Behoben (#101): Der `run.sh`-Generator
  liegt jetzt als gemeinsame Quelle in [`deploy/terminal/run-sh.lib.sh`](../../deploy/terminal/run-sh.lib.sh),
  die sich `Client-Raspi/setup.sh` und `deploy/terminal/update.sh` teilen – eine Änderung am
  Startbefehl wirkt damit auf beiden Wegen. Die erzeugte Datei trägt eine **Vertragsversion**
  (Markerzeile); `update.sh` erneuert sie bei Bedarf (Temp-Datei + Rename, weil bash einen
  Offset auf das Inode der laufenden Datei hält) und sichert die alte Fassung. Findet es eine
  `run.sh` **ohne** Schleife, unterlässt es den Neustart bewusst und endet mit **Exit 4** plus
  Anleitung; der Marker `.run-sh-pending-restart` hält den Zustand fest, bis die neue `run.sh`
  ihn beim Start selbst löscht – die einzige verlässliche Quittung, dass der *laufende Prozess*
  (nicht nur die Datei) aktuell ist. Truststore-Flags eines Altbestand-Geräts wandern mit
  (private CA ↔ https-Pflicht aus #35). Der Watchdog wertet Exit 4 weder als Rollback- noch als
  Fehlschlag-Fall, alarmiert aber bis zur Erledigung. Ergänzend liefert jedes Release die
  Betriebsskripte als Asset `elwasys-terminal-scripts-<version>.tar.gz` (+ `.sha256`, mit
  `VERSION`-Stempel) – vorher enthielt es nur das Jar, und die Skripte auf dem Gerät drifteten
  unkontrolliert. Selbst-Update der Skripte bewusst nicht. Ebenfalls erledigt: die Anzeige-Locale
  ist über `FormatUtilities.DISPLAY_LOCALE` vollständig festgenagelt (#100 – der Preis in der
  Programmliste des kleinen Displays und zwei Datumsstellen zogen noch die JVM-Locale). Der
  Terminal-Selftest deckt die neuen Fälle ab (`auto-update-selftest.sh`, 24/24).
  **Offen vor dem Feldeinsatz bleibt die Generalprobe (#104)** – zwölf Punkte, die Hardware,
  Produktivdaten und Anwesenheit vor Ort brauchen; der Exit-4-Weg ist nur offline trocken
  getestet. Details: [Worklog](../worklog/2026-07-27-cutover-blocker-terminal-startbefehl.md).
