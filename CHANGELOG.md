# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier dokumentiert.

Format: [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).

Das Projekt befindet sich in der Modernisierung und ist bislang unveröffentlicht; die
Abschnitte sind daher nach Datum/Phase gegliedert (neueste zuerst) statt nach
Release-Versionen. Feinkörnige technische Details stehen im Änderungslog in
[docs/kb/05-migration-plan.md](docs/kb/05-migration-plan.md), die verdichtete Journal-Sicht
im [Worklog](docs/worklog/README.md).

## [Unreleased]

### Security
- Auth & Security (Pre-Launch-Review AP4, Issues #21/#23/#24/#25/#26/#42/#44/#45/#46/#47/#48,
  ADR 0018): Kartenlogin sucht Kartennummern nicht mehr als regulären Ausdruck und validiert
  das Format streng – `cardId=".*"` meldet keinen beliebigen Benutzer mehr an (#21). Der
  Portal-Login hat jetzt ein Brute-Force-Limit (temporäre Sperre nach zu vielen Fehlversuchen,
  In-Memory), das „gesperrt" bewusst nicht von „falsches Passwort" unterscheidbar macht (#25).
- Passwort-Reset verrät nicht mehr, ob eine Adresse existiert (immer neutrale Meldung), und
  drosselt den Versand gegen Mail-Flooding (#24, ADR 0018); mehrere Konten je Adresse führen
  nicht mehr zum Absturz (#47). Passwörter erfordern serverseitig mindestens 8 Zeichen (#44).
- Fernwartungs-Antworten werden gegen den erwarteten Standort geprüft (#26); ein geleaktes
  Standort-Token bleibt ein bewusst akzeptiertes, dokumentiertes Restrisiko (#43, ADR 0018).

### Fixed
- Case-insensitiver Benutzername kollidiert nicht mehr mit dem case-sensitiven DB-Constraint –
  „Anna"/„anna" werden beim Anlegen abgewiesen statt beide Logins dauerhaft zu sperren (#23).
  Der deconz-uuid-Endpunkt validiert die Eingabe (400 statt 500, #42); die Terminal-Token-Auth
  schreibt `last_used_at` nur noch gedrosselt statt bei jedem Request (#45); der
  Passwortgenerator-Alphabet-Tippfehler ist behoben (#46). (Pre-Launch-Review AP4)
- Geld-/Abrechnungs-Integrität (Pre-Launch-Review AP3, Issues #20/#22/#29/#36/#41, ADR 0017):
  Geld-/Belegungspfade gegen Nebenläufigkeit abgesichert – pessimistische Nutzer-Zeilensperre
  (Guthabencheck/Auszahlung/Abbuchung), frisch gesperrte Ausführung beim Beenden und
  Advisory-Lock je Gerät im Start-Pfad verhindern Doppelstart, Doppelabrechnung und negatives
  Guthaben (#20).
- Idempotenz gehärtet: gleiche Schlüssel werden serialisiert (kein HTTP 500 mehr durch eine
  vergiftete Transaktion), ein Schlüssel > 64 Zeichen wird mit 400 abgelehnt (#29); ein Replay
  prüft den Vorgang (`operation`, sonst 409) und liefert die gespeicherte Antwort auch nach
  Löschung einer Referenz-Entität statt 404 (#41).
- Guthaben-Buchungen validieren den Betrag (`> 0`) in Service und Dialog – kein umgekehrter
  oder leerer Buchungssatz mehr (#22). Ende-/Abbruch-Benachrichtigungen (SMTP/Pushover) laufen
  per `AFTER_COMMIT`-Event außerhalb der DB-Transaktion; ein Rollback versendet nichts mehr (#36).
- Terminal-Stabilität & Aufräumen (Pre-Launch-Review AP2, Issues #19/#27/#28/#51/#52/#53/#55/#56/#57/#58/#61):
  deCONZ-WebSocket verbindet nach einem Abbruch/Neustart wieder neu, sodass die
  Programm-Ende-Erkennung nicht dauerhaft ausfällt (#19); ein portal-ausgelöster Neustart
  erzeugt keinen zweiten WebSocket-Client und keine doppelten Karten-/Klick-Listener mehr
  (#27); Terminal-Nebenläufigkeit gehärtet – `ConcurrentHashMap` und ein Retry unter Lock
  verhindern ein doppeltes Execution-Finish (#28); der Fremdeinschalt-Watchdog überspringt
  ein fehlerhaftes Gerät statt den ganzen Zyklus abzubrechen (#51).
- Weitere Terminal-Härtung: UI-Mutationen konsequent auf den JavaFX-Thread (#52); eine
  kaputte 2xx-Antwort löst nicht mehr fälschlich den Offline-Pfad aus (#53); das
  Offline-Journal schreibt mit `DSYNC` (kein Buchungsverlust bei Stromausfall, #55);
  RFID-Karten-Ids werden im Log maskiert und die Fernwartung liefert deterministisch das
  INFO-Log (#56); der Wiederaufnahme-Scan überspringt eine Ausführung mit entferntem Programm
  statt in den Fehlerzustand zu laufen (#57); das deCONZ-Passwort kommt aus einem CSPRNG
  (`openssl rand`, #58); tote SMTP-Konfiguration im Terminal-Client entfernt (#61).
- Offline-Replay-Kern gehärtet (Pre-Launch-Review AP1, Issues #16/#17/#18/#54/#59, ADR 0016):
  Nachmeldungen laufen über einen privilegierten Replay-Pfad (`replay`-Flag), der die
  fachlichen Wächter überspringt, statt das Journal bei einer zwischenzeitlichen
  Sperrung/Guthabenänderung dauerhaft zu verklemmen (#16).
- Client-Journal-Replay robust gemacht: Dead-Letter für dauerhaft abgelehnte Einträge,
  Paar-Reihenfolge (START erst mit vorliegendem FINISH), einzelnes Entfernen statt `clear()`
  (kein Verlust parallel hinzugekommener Enden), NPE-Absicherung beim Auflösen der
  Backend-Id (#17).
- Zeitstempel-Invariante `stop ≥ start` erzwungen und Abrechnungsdauer auf `maxDuration`
  gedeckelt – kein `stop < start`/0-€-Waschgang und keine Überberechnung mehr bei
  Offline-Nachmeldungen; der reale End-Zeitstempel bleibt als Audit-Record erhalten (#18).
- Terminal wertet einen Snapshot als unbrauchbar, wenn die lokale Uhr vor dem
  Snapshot-Zeitpunkt liegt (Raspberry Pi ohne RTC), statt falsch abzurechnen (#54).

### Added
- Demo-Datenbestand fürs visuelle UI-Prüfen: `DemoDataSeeder` (`backend/.../demo/`,
  `@Profile("demo")`) legt beim Start einen realistischen, idempotenten Beispielbestand an
  (4 Benutzergruppen mit allen Rabattarten, 3 Standorte, 5 Programme, 6 Geräte inkl. eines
  deaktivierten, 5 Benutzer inkl. gesperrtem Gast, Guthaben, Ausführungshistorie und laufende
  Ausführungen). Start über `backend/run-demo.sh` (Profil `demo`, Demo-DB `elwasys_demo`,
  Portal auf :8080, Login admin/admin bzw. `<benutzer>`/`demo`); Profil-Konfiguration
  `application-demo.yml`; Regressionstest `DemoDataSeederTest` (5 Tests). Keine
  Flyway-Migration (Demo-Daten gehören nicht ins Produktivschema). Siehe
  docs/kb/06-ui-tests.md „Demo-Daten" und docs/kb/04-build-and-run.md „Demo-Modus".
- agentic-baseline-Setup: `AGENTS.md` als Single Source of Truth (Instruktionen), thin
  `CLAUDE.md` mit `@AGENTS.md`-Import; `docs/`-Wissenssystem (`worklog/`, `specs/`,
  `architecture/` mit ADR 0001–0015, `agent-setup.md`); `.claude/` mit Commands
  (`adapt-baseline`, `audit-ai-docs`, `review`) und Agenten (`orchestrator`,
  `code-reviewer`, `backend`, `terminal`, `portal`, `devops`); `scripts/bootstrap.sh` +
  `scripts/check-ai-docs.sh`; Root-Configs (`.editorconfig`, `.gitattributes`,
  `.env.example`, `.vscode/`).

### Changed
- Knowledge Base von `kb/` nach `docs/kb/` verschoben und alle Repo-internen
  `kb/`-Referenzen (Kommentare/Doku) auf `docs/kb/` umgestellt.
- Provisionierungs-Artefakt `cloud-config.yaml` von `kb/cloud-init/` nach
  `deploy/cloud-init/` verschoben (Betriebs-/Infra-Artefakt statt Wissensdokument; das
  Erklär-Dokument `docs/kb/07-cloud-init.md` bleibt in der KB).
- KB entwirrt und nach Zweck getrennt: `docs/kb/` = Sollzustand + „Aktueller Stand";
  Historie (früher „Status-Log"/„Änderungslog") → append-only [Worklog](docs/worklog/README.md)
  und dieses CHANGELOG; Auftraggeber-/Architektur-Entscheidungen → ADRs. Wissen gehört ins
  Repo, nicht in lokalen User-Speicher.
- Portal-Design an das Terminal angeglichen: gemeinsame Farbpalette (Blau `#4488dd`, Status-
  Grün/-Rot/-Grau wie das Terminal); Dashboard-Gerätekarten responsiv (50 %/100 %) mit
  status-farbigem Oberrand und voller Breitennutzung.

### Fixed
- Portal-Erscheinungsbild wiederhergestellt (vorher nackte Lumo-Standardoptik ohne Stylesheet):
  vertrautes Design via Laufzeit-Inline-CSS statt kompiliertem `@Theme` (umgeht den
  Vaadin-24.10-Lizenzcheck). Details:
  [Worklog](docs/worklog/2026-07-22-portal-design.md), docs/kb/05-migration-plan.md.

## 2026-07-22 — Phase 6: Produktivumschaltung (AP1–AP7)

### Added
- `deploy/cutover/`: Cutover-DB-Werkzeuge – Preflight-Check, Token-/Admin-Passwort-Wrapper,
  Review-SQL und `verify-cutover-migration.sh` (21 Asserts, lokal 21/21 PASS).
- `deploy/cutover/rollback-cutover.{sql,sh}` + `verify-rollback.sh`: idempotentes Reverse-DDL
  (macht V3..V10 rückgängig, V2 unangetastet), lokal 29/29 + Idempotenz + Re-Cutover bewiesen.
- `deploy/terminal/`: `upgrade-jre.sh` (Java-21-Nachrüstung für Altgeräte), `update.sh`
  (Terminal-Update über `--version`/`--jar` mit latest/previous-Jar-Layout),
  `auto-update-watchdog.sh` (Auto-Update per Cron mit Rollback + Recovery) + README-Runbook.
- `Client-Raspi` Supervising-Loop `run.sh` (kein systemd) mit Symlink-basiertem Update-Vertrag.
- `TerminalReadinessMarker` (Client): Readiness-Marker beim Wechsel nach `SELECT_DEVICE`
  (medium+small) als Startnachweis für den Auto-Update-Watchdog.
- `deploy/smoke/post-deploy-smoke.sh` + `playwright.smoke.config.ts` + read-only
  `tests-smoke/smoke.spec.ts`: Post-Deploy-Smoke-Gate (Health-Check + schlanke Playwright-Teilmenge).
- `deploy/CUTOVER-RUNBOOK.md`: orchestrierendes Cutover-Runbook (Strangler-Reihenfolge,
  Gate/Rollback je Schritt, Entscheidungsbaum, Post-Cutover-Checkliste).

### Security
- Terminal-Neuaufsetzung setzt Java 21 voraus (`upgrade-jre.sh`); Java-17-Restrisiko aus
  Phase 1 aufgelöst.

### Fixed
- QA-Review Phase 5/6: im `deploy/terminal/auto-update-watchdog.sh` einen BLOCKER (reiner
  Fetch-Fehler löste Rollback + `java`-Kill aus → jetzt Symlink-Ziel-Vergleich, unverändert
  ⇒ nur Warnung) und einen MAJOR (kein Leerlauf-Check vor dem Kill → fail-safe Leerlauf-Gate)
  behoben; dazu MINOR/NITPICKs (`run.sh` Log-Append+Rotation, Feldtrenner in
  `02-issue-terminal-tokens.sh`, tote `getDatabase*`-Getter/`database.*`-Properties aus 16
  E2E-Fixtures, stale Doku-Refs). Backend 200/200, Client-UI 53/53.

## 2026-07-22 — Phase-5-Nachträge

### Changed
- `common`-Modul aufgelöst: die 6 Utility-Klassen nach `Client-Raspi/src/main/` verschoben,
  Root-Reactor von 3 auf 2 Module (Client-Raspi, backend). Der REST-/WS-Wire-Contract bleibt
  bewusst dupliziert (keine geteilte Klasse, keine Wiederkopplung Terminal↔Backend).
- Alt-Schema auf eine einzige Quelle konsolidiert: Flyway-Baseline `V1` ist die alleinige
  Quelle; Seed-Stellen spielen `V1` direkt per psql ein.

### Removed
- Duplikat-Fixture `database/` (`database-init.sql` + Archäologie) und das obsolete
  `backend/verify-schema-baseline.sh` (Phase-2-Relikt).

## 2026-07-21 — Phase 5: Aufräumen (AP1–AP6)

### Removed
- Alt-Portal-Modul `Portal/` (Vaadin 7) komplett aus dem Repo, inkl. `portal-legacy-build`-CI-Job.
- `Common.DataManager`, Alt-Entities, `DiscountType`, `NotEnoughCreditException` und das
  `maintenance/`-Alt-TCP-Protokoll (`Common` von 17 auf 6 Klassen).
- App-Reste `elwaapi` (Migration `V10`): Auth-Key-Trigger/-Funktionen, `users`-Spalten
  `app_id`/`access_key`/`auth_key`, Tabellen `reservations`/`foreign_authkeys`, Config-Schlüssel
  `authkey.prefix`/`reservation.duration`; tote Auth-Key-Anzeige aus der medium-UI.
- Obsolete `locations.client_*`-Fernwartungsspalten (Migration `V9`).

### Changed
- Spaltentypo `auto_end_power_threashold` → `auto_end_power_threshold` (Migration `V8`,
  Wire-Contract beidseitig synchron umbenannt).
- Release-Pipeline finalisiert (`maven-publish.yml`, `actions/*` v3 → v4, `-DskipTests` im
  Release-Build); Doku (Root-README, docs/kb, Setup, CLAUDE.md) auf die Zielarchitektur gebracht.

### Security
- DB-Rollen gehärtet (Migration `V6`): Alt-Rollen `elwaclient1`/`elwaapi` + Gruppe
  `elwaclients` samt Default-Passwörtern entfernt, `elwaportal` einziger Anwendungs-DB-User.
- Default-Admin-Passwort entfernt (Migration `V7`, nur bei unverändertem Default-Hash); Setzen
  ausschließlich über neues `admin-cli` (Argon2id).

### Fixed
- Kritischer Build-Fix: `backend/Dockerfile`/`.dockerignore` referenzierten noch das entfernte
  `Portal/pom.xml` → auf die drei Reactor-Module korrigiert.

## 2026-07-21 — Phase 4: Terminal-Modernisierung (AP1–AP6)

### Added
- Backend-API v1 additiv erweitert: anonymer `GET /api/v1/devices/overview`,
  Idempotenz-Schlüssel (`Idempotency-Key`, Migration `V4`) + optionaler `clientTimestamp` für
  die Execution-Endpunkte, `GET /api/v1/snapshot` (ohne Passwort-Hashes).
- Offline-Robustheit: `locations.offline_max_duration_minutes` (Migration `V5`), Backend
  `ClientTimestampPolicy`, Client-`offline/`-Package (Snapshot-Store, Journal, Pricing,
  Gateway) für laufende Ausführungen (Stufe A) und neue Buchungen (Stufe B) inkl. Replay.
- deCONZ-Simulator + deCONZ-E2E-Tests, erste `ui/small`-Smoke-Abdeckung.

### Changed
- Client-Cutover auf die REST-API: `api/ApiClient`- + `model/Client*`-Schicht ersetzt
  `Common.DataManager`; `elwasys.properties`/`setup.sh` nutzen `backend.url`/`backend.token`
  statt DB-Zugangsdaten. Terminal spricht keine direkte DB mehr.
- Fernwartung umgedreht: Terminal baut eine ausgehende, dauerhafte WebSocket-Verbindung zu
  `/api/v1/terminal-ws` auf (Reconnect+Backoff); Benachrichtigungsversand zentral im Backend.
- Client-Unterbau modernisiert: JavaFX 20 → 23.0.2, SLF4J → 2.0.18, Logback → 1.5.38.

### Removed
- `MaintenanceServerManager` (TCP-Server) und `LocationManager` (Direkt-DB-Registrierung) aus
  dem Client entfernt – letzter Direkt-DB-Zugriff des Terminals fällt weg.
- `unirest-java`, HttpComponents und `org.json` aus `Client-Raspi/pom.xml` (auf `java.net.http`).

### Fixed
- CI-Stabilität: Testharness-Pfadbug, `ApiClient`-Retry auf transiente `IOException`,
  reihenfolge-unabhängiger Fix des deCONZ-Fehlschlags + `DeconzDevicePowerManager`-Härtung
  (klarer Fehler bei leerer deCONZ-Id).

## 2026-07-20 — Phase 3: Portal-Neubau (AP1–AP6)

### Added
- Vaadin-Flow-Portal im Backend (Vaadin 24.10.8): Login + rollenbasierte Layouts, 5
  Stammdaten-Views mit Grids + CRUD-Dialogen, Admin-Dashboard, Guthaben aufladen/Historie,
  UserDashboard, Passwort ändern/zurücksetzen, UserSettings, Log-Viewer/Fernwartung.
- Live-Updates zwischen Sessions: Vaadin-freies `events`-Package (7 `DomainEvent`-Records),
  `UiBroadcaster` (`@TransactionalEventListener` + `@Push`).
- Playwright-E2E-Suite `backend/e2e/` (P1–P20, inkl. neuem P11), 20/20 grün.

### Changed
- Feature-Parität zum Alt-Portal hergestellt (deutsche Texte, Lösch-Wächter, Benutzer-Sperren
  1:1 übernommen); Alt-Portal aus dem CI-Playwright-Pfad genommen.

### Fixed
- Login-Seite: „Passwort vergessen?"-Knopf zeigte den englischen Vaadin-Default.

## 2026-07-20 — Phase 2: Backend-Gerüst (AP1–AP6)

### Added
- Neues Spring-Boot-Backend-Modul (`backend/`, Java 21) mit Flyway-Verwaltung des Schemas
  (Baseline `V1`), Actuator-Health.
- JPA-Entities/Repositories + verhaltenserhaltend portierte Geschäftslogik (`PricingService`,
  `CreditService`, `PermissionService`, `ExecutionService`) mit Alt-vs-Neu-Parity-Tests.
- Auth: Argon2id + SHA1-Migrationspfad (`V2` erweitert `users.password` auf `VARCHAR(255)`),
  Re-Hash hinter Flag (Default AUS).
- REST-API v1 + Standort-Token-Auth (`V3`, Bearer, `token-cli`) + WebSocket-Fundament
  `/api/v1/terminal-ws`; OpenAPI/Swagger.
- Benachrichtigungsdienst (SMTP/Pushover) hinter `elwasys.notifications.enabled` (Default AUS).
- Deployment: `Dockerfile`, `deploy/compose/` (+ TLS-Overlay), Helm-Chart
  `deploy/helm/elwasys-backend/`; Image-Publish nach GHCR im Release-Workflow.

## 2026-07-20 — Phase 0 & 1: Sicherheitsnetz und Fundament

### Added
- Build-/UI-/E2E-Test-Sicherheitsnetz (Client TestFX/Xvfb, Portal Playwright, Cross-Component):
  Client 18/18, Portal 18/18, Cross-Component grün.
- Aggregator-Parent-POM; isolierte Charakterisierungstests für `MainFormStateManager` (JUnit 5).

### Changed
- Modernisierungsplan auf die Zielarchitektur-Fassung gehoben (Roadmap Phasen 1–5, später 6).
- Common + Client-Raspi auf Java 21; `ElwaManager`-DI-Seam eingeführt.
- Testframeworks vereinheitlicht: einzige TestNG-Klasse nach JUnit 5 migriert; TestNG/JUnit-4
  entfernt.

### Fixed
- CI-/Release-Workflows von JDK 17 auf JDK 21 angehoben (Regression zum neuen Sprachlevel).
- `setup.sh` installiert Java-21-JRE (`bellsoft-java21-runtime-full`) statt Java 17.

## 2026-07-19 — Projektstart

### Added
- Knowledge Base (`docs/kb/`), Remote-Build-Umgebung (SessionStart-Hook, cloud-config),
  erste UI-/E2E-Testfälle für Client und Portal.

### Fixed
- `getDeconzServer`-Bug im Client (beim Hochfahren der echten App headless gefunden).
