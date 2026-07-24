# R4 — Dokumentation

Track R4 der finalen Pre-Launch-Review (Prüffrage 4, [Spec 0001](../../specs/0001-finale-review.md)).
Modell: Sonnet. Kein Fix, nur Findings.

## Gesamturteil

Die Dokumentation ist insgesamt **gut bis sehr gut** und für ein Projekt dieser Größe
ungewöhnlich konsequent gepflegt: alle stichprobenartig geprüften KB-Sachaussagen (00–08)
stimmen mit dem Code überein, das ADR-0020-Regime (Body = Ist-Zustand, Historie nur als
Footer) wird eingehalten, und die Javadoc-Qualität im neu geschriebenen Code (Backend
`service`/`auth`/`offline`, Client `offline`) ist vorbildlich Warum-fokussiert und
angemessen dosiert. Die Schwachstellen liegen nicht in der KB selbst, sondern in den
Rändern: ein zentrales Cutover-Verifikationsskript ist nach der V11-Migration nicht
nachgezogen worden (Teilbereich c, das schwerste Finding dieses Tracks), das CHANGELOG
hält die selbstauferlegte Kurzform nicht ein, der Worklog-Index hat eine Lücke, und in
Alt-Code (`org.kabieror.elwasys.common`, `FhemDevicePowerManager`) finden sich vereinzelt
veraltete/leere Kommentare und ein aus der Modernisierung übrig gebliebenes totes
Exception-Konstrukt mit irreführendem Javadoc.

---

## (a) KB-Verifikation (docs/kb/00–08 gegen den Code)

**Vorgehen:** Je Artikel mindestens 3 zentrale Sachaussagen ausgewählt und im Code
verifiziert (Grep/Read gegen `backend/`, `Client-Raspi/`, Migrationen, `.claude/hooks/`,
Tests). Zusätzlich die ADR-0020-Regel geprüft: `grep` nach Strikethrough (`~~`) und nach
Phasen-/„seit"-Markern im Fließtext (außerhalb `## Historie`) über alle acht Artikel.

Geprüfte Beispiele (Auswahl, alle bestätigt):
- **00-overview.md**: 6 `common`-Klassen unter `Client-Raspi/src/main/org/kabieror/elwasys/common/`
  existieren 1:1; Backend hat keine `common`-Abhängigkeit (`grep` im `backend/pom.xml` liefert
  nur einen Kommentar-Treffer); DB-User `elwaportal` ist in `application.yml` und `V1`/`V6`
  verankert.
- **01-architecture.md**: Vaadin `24.10.8` im `backend/pom.xml` (`vaadin.version`); Flyway
  `V1`–`V11` vorhanden; `/api/v1/terminal-ws` als Konstante in
  `TerminalWebSocketConfig`/clientseitig in `TerminalWebSocketClient` identisch.
- **02-data-model.md**: `REVOKE UPDATE, DELETE ON credit_accounting` in `V1`; alle
  `@ManyToOne`/`@ManyToMany`-Assoziationen der Domain-Entities sind `FetchType.EAGER`;
  `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` in `ProgramEntity`/`UserGroupEntity`.
- **03-modules.md**: Token-Präfix `elwt_` in `TerminalTokenService`; `pg_advisory_xact_lock`
  in `AdvisoryLockService`; `IdempotencyKeyReusedException` existiert; der referenzierte
  Regressionstest `ExecutionControllerNotificationTest#executionAlreadyFinishedIsCheckedInsideTheIdempotencyBranch`
  existiert wortgleich.
- **04-build-and-run.md**: Build-Reihenfolge (`mvn -N install` → Modul-Builds) und
  Vaadin-Lizenzcheck-Beschreibung sind stimmig mit `pom.xml`-Kommentaren.
- **06-ui-tests.md**: `DemoDataSeederTest` hat exakt 5 `@Test`, `DemoDataSeederGuardTest`
  exakt 2 `@Test`; Marker-Username `anna` kommt in `UserService`/`ElwasysAuthenticationProvider`
  als Referenz vor.
- **07-cloud-init.md**: `.claude/hooks/session-start.sh` und
  `deploy/cloud-init/cloud-config.yaml` existieren wie beschrieben, Hook-Inhalt
  (`mvn -N install`, `dependency:go-offline`, Xvfb-Fallback) stimmt.
- **08-test-plan.md**: Portal-E2E-Zahlen exakt code-verifiziert (23 `test()` in
  `backend/e2e/tests/`, 4 in `tests-smoke/`).

**ADR-0020-Regel:** Alle acht Artikel (00–08 außer der bewusst ausgenommenen
`05-migration-plan.md`) haben genau einen `## Historie`-Footer und keine Strikethrough-/
„seit Phase X"-Marker im Fließtext davor — die einzigen Treffer für `~~`/Phasen-Marker im
Fließtext liegen in `05-migration-plan.md`, die laut Regel selbst die dokumentierte
Ausnahme ist. Die Regel wird also eingehalten.

**Urteil:** **Gut.** Von >25 geprüften Einzelaussagen ist nur eine (siehe Finding) leicht
ungenau; das ist im Rahmen einer lebenden Doku unauffällig.

### Findings

- **Niedrig** · Fundstelle: `docs/kb/08-test-plan.md:138` (Aussage) vs. Code
  (`Client-Raspi/src/test/**/*.java`) · Die KB behauptet „71 `@Test`" für den Client, der
  tatsächliche Zählstand ist **73** `@Test`-Annotationen (Zählung ohne
  `@TestFactory`/`@TestMethodOrder`, siehe `ExecutionFinisherRetryTest`,
  `ApiClientTransientRetryTest` u. a.). Vermutlich seit dem letzten „code-verifiziert"-Durchlauf
  zwei Tests hinzugekommen, ohne die Zahl nachzuziehen. Kein Zeichen für ein strukturelles
  Verifikationsproblem, da die Portal-Zahlen (23 + 4) exakt stimmen. · **Empfehlung:** Zahl bei
  nächster KB-Pflege nachziehen (kurzer `grep -c` reicht).

---

## (b) Javadoc/Kommentare (backend, Client-Raspi)

**Vorgehen:** Je Modul mehrere Pakete mit komplexen UND einfachen Klassen gelesen:
Backend `service` (`ExecutionService`), `domain` (`ExecutionEntity`, `TimeUnitType`),
`api/dto` (`CreditResponse`, Record), `ui/admin` (`AdminDashboardView`,
`AdminProgramsView`); Client `offline` (`OfflineGateway`, die komplexeste/kritischste
Klasse des Projekts), `common` (Alt-Utility-Klassen: `ProgramType`, `FormatUtilities`,
`NoDataFoundException`, `LocationOccupiedException`), `ui/scheduler`
(`IInactivityJobDoneListener`), `devices` (`FhemDevicePowerManager`). Zusätzlich
projektweit nach `TODO`/`FIXME`/`XXX` sowie nach offensichtlich englischen
Kommentarfragmenten in neueren Paketen (`auth/`, `offline/`) gesucht, um die
Sprachkonvention (Bezeichner Englisch/Kommentare Deutsch) stichprobenartig zu prüfen.

**Beobachtung, neu geschriebener Code (Backend `service`/`auth`/`offline`, Client
`offline`):** durchgehend hohe Qualität. Kommentare erklären fast ausnahmslos das
**Warum**, nicht das Was — z. B. `ExecutionService#resetExecution` (Javadoc erklärt, warum
`reset()` trotz des Namens `finished=true` setzt, mit Verweis auf die abhängige Logik in
`hasExpiredExecutions`), oder `OfflineGateway#replay` (vier benannte Unterpunkte:
Kommunikationsfehler vs. fachlicher Fehler vs. Paar-Reihenfolge vs. Kein-`clear()`-Race,
jeweils mit der Konsequenz bei Weglassen). Dichte und Länge sind an der Komplexität
kalibriert: ein Record (`CreditResponse`) hat null Kommentare, ein simples Enum
(`TimeUnitType`) einen Einzeiler, die Entity-Assoziationsanmerkung (`EAGER`) einen
begründenden Satz, während die sicherheits-/geldkritische `OfflineGateway`
(589 Zeilen) fast durchgehend mehrsatzige Warum-Blöcke hat. Kein Fall von
unkommentierter komplexer Logik oder übertrieben kommentierter Trivialität in den
geprüften neuen Paketen gefunden.

**Beobachtung, Alt-Code (`org.kabieror.elwasys.common`, `devices/FhemDevicePowerManager`):**
schwächer, aber als historischer Bestand erwartbar (die Klassen tragen weiterhin
`@author Oliver Kabierschke` und sind laut AGENTS.md/KB unverändert übernommener
Alt-Code). Ein Fund geht darüber hinaus: `LocationOccupiedException` beschreibt ein
Feature (Client registriert sich an einem Standort, an dem bereits ein anderer Client
läuft), das laut `docs/kb/01-architecture.md` „Historie" mit der
Direkt-DB-Registrierung (`LocationManager`/`MaintenanceServerManager`) in Phase 4/5
entfernt wurde — die Exception wird im aktuellen Code nirgends mehr geworfen
(nur deklariert/gefangen), ihr Javadoc beschreibt also ein nicht mehr existierendes
Verhalten.

**Urteil:** **Gut.** Der produktive, in der Modernisierung neu geschriebene Code
(die überwiegende Mehrheit) hat vorbildliche Warum-Kommentare in angemessener Dichte.
Die Funde unten sind alle niedrig bis mittel und konzentrieren sich auf Alt-Bestand bzw.
eine einzelne stale Stelle in neuerem Code.

### Findings

- **Mittel** · Fundstelle: `Client-Raspi/src/main/org/kabieror/elwasys/common/LocationOccupiedException.java:7`
  (Javadoc) + `Client-Raspi/.../application/ElwaManager.java:174` (deklariert im
  `throws`) · Die Klasse ist tote Fehlerbehandlung: `grep` nach
  `throw new LocationOccupiedException` liefert im gesamten Repo (main + test) **keinen**
  Treffer, der Javadoc beschreibt aber weiterhin ein Szenario der entfernten
  Direkt-DB-Standortregistrierung. Ein Leser, der auf diese Exception/den `catch`-Zweig in
  `AbstractMainFormController` stößt, geht von einem aktiven Fehlerpfad aus, den es nicht
  mehr gibt. · **Empfehlung:** Bei Gelegenheit klären, ob der `throws`/`catch`-Pfad noch
  gebraucht wird (dann Javadoc auf den echten heutigen Auslöser umschreiben) oder ob es
  sich um totes Aufräummaterial aus der Modernisierung handelt (dann im Rahmen eines
  R7-/Repo-Hygiene-Arbeitspakets entfernen).
- **Mittel** · Fundstelle: `backend/src/main/java/org/kabieror/elwasys/backend/ui/admin/AdminDashboardView.java:77-81`
  (Klassen-Javadoc) · Der Kommentar erklärt, warum die Fernwartungs-Toolbar „in der Praxis
  i.d.R. 'Nicht verbunden'" zeigt: weil sich Alt-Terminals „laut Roadmap ERST in Phase 4"
  über den WS-Kanal verbinden. Laut `docs/kb/README.md` „Aktueller Stand" ist die
  Modernisierung inkl. Cutover (Phase 6) und der kompletten Pre-Launch-Review (AP1–AP7)
  inzwischen abgeschlossen — alle Terminals sollten also längst umgestellt sein. Der
  Kommentar beschreibt damit einen Vor-Cutover-Zwischenzustand als wäre er weiterhin der
  Normalfall. · **Empfehlung:** Bei nächster Berührung der Klasse den Satz auf den
  Ist-Zustand aktualisieren oder als historische Randnotiz kennzeichnen.
- **Niedrig** · Fundstelle: `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/ui/scheduler/IInactivityJobDoneListener.java:4`
  · Klassen-Javadoc ist der Platzhalter `TODO: Describe` (Alt-Code-Rest). Trivial, aber
  genau die Sorte „unkommentiert wo eigentlich ein Kommentar stehen sollte" (das Interface
  selbst hat nur eine Methode, die immerhin dokumentiert ist). · **Empfehlung:** Einzeiler
  ergänzen, sobald die Datei ohnehin angefasst wird.
- **Niedrig** · Fundstelle: `Client-Raspi/src/main/org/kabieror/elwasys/common/NoDataFoundException.java:5`
  · Mojibake/Encoding-Artefakt im Javadoc-Text (`k?nnen` statt `können` — vermutlich
  Latin-1-Rest aus dem Alt-Repo). Rein kosmetisch, aber schlecht lesbar. ·
  **Empfehlung:** Bei Gelegenheit auf UTF-8 korrigieren.
- **Niedrig** · Fundstelle: `Client-Raspi/src/main/org/kabieror/elwasys/raspiclient/devices/FhemDevicePowerManager.java:135,155,258`
  · Vereinzelt englische Kommentare (`// Check response from server…`) in ansonsten
  durchgängig deutschsprachig kommentiertem Code — Rest aus dem vor-Modernisierungs-Bestand
  (fhem war schon vor der Umstellung vorhanden). Kein systematisches Problem: eine gezielte
  Stichprobe in neueren Paketen (`auth/`, `offline/`) fand ausschließlich deutsche
  Kommentare. · **Empfehlung:** Keine Aktion nötig, nur bei ohnehin anstehender
  Überarbeitung der Datei mitziehen.

---

## (c) Rest-Doku (README, Runbook, CHANGELOG, ADRs, Worklog, Specs, agent-setup)

**Vorgehen:** Wurzel-`README.md` gegen die Zielarchitektur gelesen; `deploy/CUTOVER-RUNBOOK.md`
komplett gelesen und alle referenzierten Dateien/Skripte auf Existenz geprüft (`ls`), dazu
das zentrale Verifikationsskript `deploy/cutover/verify-cutover-migration.sh` gegen den
aktuellen Migrationsstand abgeglichen; `CHANGELOG.md` komplett gelesen (Format,
`[Unreleased]`, Eintragslänge gegen die AGENTS.md-Vorgabe „1–2 Zeilen"); `docs/architecture/`
auf lückenlose Nummerierung + Status-Feld geprüft (alle 21 Dateien); `docs/worklog/README.md`-
Index gegen die tatsächlichen Dateien im Ordner abgeglichen (`ls` vs. Tabellenzeilen);
`docs/specs/README.md` und `docs/agent-setup.md` gelesen; projektweiter automatisierter
Link-Check (alle `.md`-internen relativen Links in `docs/kb/`, `docs/architecture/`,
`docs/worklog/`, `docs/specs/`, `CHANGELOG.md`, `README.md`, `docs/agent-setup.md`,
`deploy/CUTOVER-RUNBOOK.md` gegen das Dateisystem aufgelöst).

**Urteil:** **Mittel** (mit im Übrigen guter Substanz). README ist aktuell und beschreibt
korrekt die neue Architektur; ADRs sind lückenlos und mit Status versehen; die Struktur von
CHANGELOG/Worklog/Specs ist sauber angelegt. Das schwerste Finding dieses Tracks liegt aber
hier: das zentrale Cutover-Verifikationsskript ist nach der V11-Migration nicht
nachgezogen worden, wodurch eine im Runbook als „real ausgeführt/PASS" dargestellte
Verifikation aktuell nicht mehr reproduzierbar wäre. Dazu zwei spürbare, aber leicht
behebbare Lücken (Worklog-Index, CHANGELOG-Kürze) und zwei kosmetische kaputte Links.

### Findings

- **Hoch** · Fundstelle: `deploy/cutover/verify-cutover-migration.sh:17,20,119,170,172`
  (`EXPECTED_HISTORY` endet bei `"10|SQL|true"`, Kommentare sprechen durchgängig von
  „V2..V10") vs. `backend/src/main/resources/db/migration/` (enthält inzwischen auch
  `V11__add_performance_indexes.sql`, siehe `docs/kb/02-data-model.md`) ·
  Das Skript ist das im `CUTOVER-RUNBOOK.md` § „Geprobt vs. nur dokumentiert" als
  „real ausgeführt … 21/21 Asserts PASS" zitierte Verifikationswerkzeug für den
  Migrations-Dry-Run (auch Punkt 2 der Generalprobe-Liste in
  [Spec 0001](../../specs/0001-finale-review.md) verweist auf genau diesen Weg). Seit die
  Migration `V11` (Pre-Launch AP5, 2026-07-23) hinzukam, hätte ein echter Lauf gegen die
  Alt-Weg-DB eine Flyway-Historie mit **elf** angewendeten Versionen zur Folge — der
  hartcodierte `EXPECTED_HISTORY`-String (nur bis `V10`) würde den entsprechenden
  `assert_eq` zwangsläufig als FEHLGESCHLAGEN melden, obwohl die Migration selbst korrekt
  liefe. Das Runbook zitiert also einen Verifikationsstand, der mit dem heutigen
  Migrationsbestand nicht mehr reproduzierbar ist — genau die Art von „zentrale
  Betriebs-Doku ist so nicht mehr korrekt", die die Kalibrierung als hoch einstuft, zumal
  dieses Skript laut Spec vor dem Feldeinsatz nochmal real durchgespielt werden soll. ·
  **Empfehlung:** `EXPECTED_HISTORY` (und die Kommentare/den Docstring) um `V11` ergänzen,
  Skript einmal real gegenlaufen lassen und das Runbook-Zitat („21/21" bzw. neue Zahl)
  auffrischen, bevor die Migrations-Generalprobe vor dem Feldeinsatz stattfindet.
- **Mittel** · Fundstelle: `docs/worklog/README.md` (Index-Tabelle, Zeilen 38–60) vs.
  `docs/worklog/` (Verzeichnisinhalt) · Der Ordner enthält 24 datierte Einträge, der Index
  listet nur 23 — es fehlt die Zeile für
  `2026-07-23-offline-replay-haertung-ii.md` (die Follow-ups #67/#68/#69 zu ADR 0021, im
  KB-„Aktueller Stand" bereits als abgeschlossen beschrieben). Verstößt gegen die eigene
  Regel „Wissen aktuell halten … Worklog-Eintrag anlegen" (der Eintrag existiert, ist aber
  nicht verlinkt) und macht den Index als Vollständigkeits-Nachweis unzuverlässig. ·
  **Empfehlung:** Zeile im Index nachtragen (Datum 2026-07-23, Kurzbeschreibung analog zu
  den Nachbar-Einträgen).
- **Mittel** · Fundstelle: `CHANGELOG.md:16-156` (`## [Unreleased]`) vs.
  `AGENTS.md` § 4 „CHANGELOG-Einträge kurz halten – ein bis zwei Zeilen je Änderung“ ·
  Format (Keep a Changelog, `### Added/Changed/Fixed/Security`) und Pflege von
  `[Unreleased]` sind grundsätzlich in Ordnung, aber etliche Einträge sind 4–9 Zeilen lang
  und reproduzieren technische Details (z. B. der „Offline-Replay-Härtung II"-Eintrag,
  Zeilen 16–25, oder „Deployment & Betrieb“, Zeilen 26–32), die laut eigener Konvention in
  `docs/kb/05-migration-plan.md` stehen sollten und im CHANGELOG nur verdichtet auftauchen
  sollen. Kein Sachfehler, aber eine spürbare Abweichung vom selbst gesetzten Maßstab, die
  den Unterschied zum Worklog/Änderungslog verwischt. · **Empfehlung:** Bei künftigen
  Einträgen strenger auf 1–2 Zeilen kürzen; bestehende lange Einträge müssen nicht rückwirkend
  gekürzt werden (kein Fix-Auftrag laut Spec), aber als Leitplanke für neue Einträge im Kopf
  behalten.
- **Niedrig** · Fundstelle: `deploy/CUTOVER-RUNBOOK.md:15` (Link-Ziel `../kb/05-migration-plan.md`)
  · Von `deploy/` aus aufgelöst zeigt der Link auf das nicht existierende
  `elwasys/kb/05-migration-plan.md` (die KB liegt seit der in `CHANGELOG.md` dokumentierten
  Verschiebung unter `docs/kb/`); korrekt wäre `../docs/kb/05-migration-plan.md`. Der
  sichtbare Linktext zeigt bereits den richtigen Pfad, ein Leser findet die Datei also trotzdem
  mühelos — rein technisch ein toter Link. · **Empfehlung:** Linkziel korrigieren.
- **Niedrig** · Fundstelle: `docs/agent-setup.md:42` (Link-Ziel `docs/kb/README.md`) ·
  Die Datei liegt selbst unter `docs/`, das Linkziel müsste relativ `kb/README.md` heißen
  (aktuell verweist es auf das nicht existierende `docs/docs/kb/README.md`). Gleiches Muster
  wie beim Runbook-Fund oben (sichtbarer Pfad korrekt, `href` verdoppelt `docs/`). ·
  **Empfehlung:** Linkziel korrigieren.

**Sonstige, unauffällige Prüfpunkte (kein Finding):**
- Wurzel-`README.md`: Englisch, beschreibt korrekt die REST-API/WebSocket-Architektur ohne
  Direkt-DB-Zugriff, das eingebettete Vaadin-Portal und das aufgelöste `Common`-Modul — kein
  Alt-Stand-Relikt gefunden.
- `docs/architecture/`: `0001`–`0021` lückenlos durchnummeriert, jede Datei mit
  `Status: accepted` und Datum.
- `docs/specs/README.md`: Index (nur `0001`, Status `Draft`) stimmt mit dem tatsächlichen
  Dateibestand (`0000-template.md`, `0001-finale-review.md`) überein.
- Stichproben-Linkcheck über `docs/kb/*.md` (alle acht Artikel + `README.md`) ergab **keine**
  kaputten internen Links.
