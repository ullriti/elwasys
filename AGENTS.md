# AGENTS.md

> **Single Source of Truth** für die Agenten-Instruktionen dieses Repos.
> `CLAUDE.md` zieht diese Datei per `@AGENTS.md`-Import herein – **immer hier
> editieren**, nie in `CLAUDE.md`. Mechanik: [`docs/agent-setup.md`](docs/agent-setup.md).

## 0. Sprachen

Drei unabhängige Einstellungen für dieses Projekt:

- **Chat** (Agent ↔ Nutzer): **Deutsch**
- **Dokumentation** (docs/, KB, Worklog, Specs, ADRs, CHANGELOG, README): **Deutsch**
  (Ausnahme: die Wurzel-`README.md` ist bewusst Englisch – öffentliches Projekt-Readme.)
- **Code & Kommentare** (Bezeichner, Code-Kommentare, Commit-Messages): **Bezeichner
  Englisch**, **Kommentare Deutsch** (so wie im Bestand). Commit-Messages Englisch.

## 1. Projektüberblick

**Name:** elwasys
**Zweck:** elwasys verwaltet und rechnet Waschmaschinen in Gemeinschafts-Waschküchen
ab: Raspberry-Pi-Touch-Terminals an den Maschinen (RFID-Login, Prepaid-Abrechnung,
Programm-Ende-Erkennung über Stromverbrauch, E-Mail/Push-Benachrichtigung) + ein
zentrales Backend mit eingebautem Admin-Portal + gemeinsame PostgreSQL-DB.
**Stack:** Java 21 · Spring Boot (Backend + REST-API + WebSocket + Vaadin-Flow-Portal)
· JavaFX (Terminal) · PostgreSQL · Flyway · Maven · Docker/Helm · Playwright (E2E).

Die **zentrale Wahrheit für Architektur & Entscheidungen liegt in
[`docs/kb/`](docs/kb/README.md)** – der aktuelle Stand steht im „Current state"-
Snapshot in [`docs/kb/README.md`](docs/kb/README.md). **Vor größerer Arbeit dort
einlesen**, insbesondere [`docs/kb/05-migration-plan.md`](docs/kb/05-migration-plan.md)
(Modernisierungsplan: Rahmenbedingungen, Zielarchitektur, Roadmap).

## 2. Setup

Die Remote-Umgebung (Claude Code on the web) ist per SessionStart-Hook vorbereitet
(Parent-POM installiert, Client-Dependencies vorgewärmt, Xvfb für headless JavaFX) –
Details in [`docs/kb/07-cloud-init.md`](docs/kb/07-cloud-init.md). Lokal:

```bash
./scripts/bootstrap.sh   # installiert Parent-POM + wärmt Dependencies vor
```

## 3. Build, Test, Lint

Agenten **müssen** die relevanten Checks grün haben, bevor eine Aufgabe fertig ist.
Es gibt seit Phase 1 ein Aggregator-Parent-POM; ein Einzelmodul-Build braucht die
Parent-POM im lokalen Repo (`mvn -N install` installiert genau diese).

```bash
# Build (Root-Reactor = 2 Module: Client-Raspi, backend – das frühere "Common"-Modul
# wurde nach der Migration aufgelöst, seine Utility-Klassen liegen in Client-Raspi):
mvn -N install -DskipTests          # Parent-POM ins lokale Repo
mvn -f Client-Raspi/pom.xml package  # Terminal
mvn -f backend/pom.xml package       # Backend + Portal
# oder komplett von der Repo-Wurzel:
mvn install

# Client: UI-/E2E-Tests headless (startet PostgreSQL, seedet DB, Xvfb)
Client-Raspi/run-ui-tests.sh              # alle
Client-Raspi/run-ui-tests.sh <TestClass>  # einzelne Klasse
Client-Raspi/run-client-e2e.sh
Client-Raspi/run-cross-component-e2e.sh   # Fernwartung über Backend-WS-Kanal

# Backend: JUnit-Suite (Testcontainers bzw. lokales PostgreSQL)
backend/run-backend-tests.sh

# Backend-Portal: Playwright-E2E (P1–P20; Setup/DB/Jar siehe backend/e2e/)
cd backend/e2e && npm test
```

Es gibt keinen separaten Lint-/Format-Schritt; maßgeblich ist der Compiler
(`-Werror` wo gesetzt) plus die Test-Suiten. CI (`.github/workflows/ci.yml`) baut/testet
Client-Raspi (inkl. Cross-Component) und Backend (JUnit + Playwright-E2E) bei jedem PR.

## 4. Arbeitsregeln (für Menschen wie Agenten)

- **Verhalten bewahren:** Nutzer-sichtbares Verhalten darf sich nicht ändern
  (Rahmenbedingung des Auftraggebers). Die E2E-Suiten sind der Maßstab.
- **Tests grün vor und nach jedem Umbau** (Kommandos oben). Neues Verhalten braucht Tests.
- **Kleine, einzeln baubare Commits** – ein Commit = eine logische Änderung.
- **Wissen aktuell halten:** nach jedem Arbeitspaket einen Eintrag in
  [`docs/worklog/`](docs/worklog/README.md) anlegen, den „Current state" in
  [`docs/kb/README.md`](docs/kb/README.md) überschreiben und Nennenswertes in
  [`CHANGELOG.md`](CHANGELOG.md) unter `[Unreleased]` festhalten. Betroffene
  KB-Dokumente (00–08) mitpflegen.
- **Entscheidungen des Auftraggebers / Architektur** als **ADR** in
  [`docs/architecture/`](docs/architecture/) festhalten (fortlaufend nummeriert).
- **Wissen gehört ins Repo, nicht in den lokalen Speicher:** Erkenntnisse,
  Entscheidungen, Fortschritt **immer** ins Repo (`docs/worklog/`, `docs/kb/`, ADRs) –
  **nie** in den User-Profil-Speicher (`~/.claude/`, `#`-Memory). Der ist nicht
  committet, nicht geteilt und in ephemeren Remote-Umgebungen nach der Session weg.
- **Upkeep prüfen:** nach Umstrukturierungen `/audit-ai-docs` bzw.
  `scripts/check-ai-docs.sh` laufen lassen.

## 5. Konventionen

Projektweite Konventionen. **Modul-/pfadspezifische** Regeln gehören in eine
verschachtelte `CLAUDE.md` im jeweiligen Unterordner (Claude lädt sie automatisch beim
Arbeiten darunter – siehe [`docs/agent-setup.md`](docs/agent-setup.md)).

**Commits & Branches**

- **Conventional Commits**: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:` …
- Ein Commit = eine logische, einzeln baubare Änderung. Imperativ, Präsens:
  „add …", nicht „added …". Commit-Messages auf Englisch.
- Jede Session arbeitet auf ihrem eigenen Branch (`claude/…`); kein direkter Push /
  Force-Push auf `master` ohne Freigabe.

**Code-Stil**

- **Java 21** für Client-Raspi und Backend (Sprachlevel im Parent-POM gesetzt).
- Bezeichner Englisch, Kommentare Deutsch (bestehendes Muster). Kommentare erklären
  das **Warum**, nicht das Was.
- Bestehende Muster spiegeln (Namensgebung, Struktur, Kommentardichte) statt neue
  einzuführen. Backend folgt der Spring-Schichtung (`domain`/`repository`/`service`/
  `api`/`ui`/`auth`/`ws`), das Terminal seiner bestehenden Paketstruktur.
- **DB-Schema ausschließlich über Flyway-Migrationen** (`backend/src/main/resources/
  db/migration/`, `V<n>__…sql`, nur additiv/nachvollziehbar) – siehe
  [`docs/kb/02-data-model.md`](docs/kb/02-data-model.md).

**Tests**

- Neues Verhalten braucht Tests; jeder Bugfix einen **Regressionstest**, der ohne den
  Fix fehlschlägt. Tests prüfen Verhalten, sind deterministisch (kein `sleep`/Zufall/
  Datumslogik).
- Vor dem Abschluss: relevante Suite grün (siehe Abschnitt 3). Selektor-Strategie und
  Testinfrastruktur: [`docs/kb/06-ui-tests.md`](docs/kb/06-ui-tests.md),
  [`docs/kb/08-test-plan.md`](docs/kb/08-test-plan.md).

**Dependencies**

- Neue Abhängigkeiten begründen (Bundle-/Security-Kosten). Standardbibliothek /
  bestehende Utilities bevorzugen.

## 6. Verzeichnis-Guide

| Pfad | Inhalt |
|------|--------|
| `Client-Raspi/` | Terminal-Anwendung (JavaFX, RFID, deCONZ/Zigbee, REST-Client, Offline-Robustheit). Enthält auch die 6 früheren `Common`-Utility-Klassen. |
| `backend/` | Zentrales Spring-Boot-Backend: REST-API + WebSocket (Terminals), Vaadin-Flow-Admin-Portal (`ui/`), Geschäftslogik (`service/`), JPA (`domain`/`repository`), Auth, Flyway-Migrationen, Benachrichtigungsdienst. `backend/e2e/` = Playwright-Portal-E2E. |
| `deploy/` | Deployment/Betrieb: `compose/`, `helm/`, `terminal/` (Update/Watchdog), `cutover/` (Produktivumschaltung), `smoke/`. |
| `docs/kb/` | Knowledge Base – zentrale Projektwahrheit + „Current state"-Snapshot |
| `docs/worklog/` | Arbeitsjournal, ein Eintrag je Session/Arbeitspaket |
| `docs/specs/` | Spezifikationen – *was* gebaut wird (vor der Implementierung) |
| `docs/architecture/` | Architecture Decision Records (ADRs) – *warum* |
| `CHANGELOG.md` | Nennenswerte Änderungen (Keep a Changelog) |
| `scripts/` | `bootstrap.sh` (Setup), `check-ai-docs.sh` (Doku-Audit) |
| `.claude/commands/` | Slash-Commands (`adapt-baseline`, `review`, `audit-ai-docs`) |
| `.claude/agents/` | Subagenten (`orchestrator`, `code-reviewer`, `backend`, `terminal`, `portal`, `devops`) |
| `.claude/` | Claude-Code-Konfiguration (settings, commands, agents, skills, SessionStart-Hook) |

## 7. Guardrails & Sicherheit

- **Nie Secrets committen** (Schlüssel, Tokens, Passwörter) – siehe `.gitignore` und
  `.env.example`. DB-Zugangsdaten/Terminal-Tokens gehören in die Umgebung, nicht ins Repo.
- **Keine destruktiven Aktionen** (Daten löschen, Force-Push `master`) ohne
  ausdrückliche Freigabe.
- Terminals greifen **nicht mehr direkt auf die DB** zu (nur noch REST-API + WebSocket +
  Standort-Token) – dieses Prinzip nicht aufweichen. DB-Rollenhärtung: `elwaportal` ist
  der einzige Anwendungs-DB-User (siehe [`docs/kb/05-migration-plan.md`](docs/kb/05-migration-plan.md)).
- Externe Aufrufe / neue Dependencies begründen und minimal halten.
- Bei unklarem Scope oder Architektur: **fragen, nicht raten.**

## 8. Referenzen

- Knowledge Base: [`docs/kb/README.md`](docs/kb/README.md)
- Modernisierungsplan/Roadmap: [`docs/kb/05-migration-plan.md`](docs/kb/05-migration-plan.md)
- Agenten-Setup & Regel-Mechanik: [`docs/agent-setup.md`](docs/agent-setup.md)
