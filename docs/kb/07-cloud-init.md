# 07 – Remote-/Cloud-Init-Umgebung

Ziel: Build und (headless) UI-Tests **remote** reproduzierbar ausführen können. Es gibt zwei
sich ergänzende Mechanismen:

## A) SessionStart-Hook (Claude Code on the web) — primär

Datei: `.claude/hooks/session-start.sh`, registriert in `.claude/settings.json`.

Dieser Hook bereitet **jede Remote-Session** automatisch vor:
- prüft Java/Maven,
- stellt Xvfb bereit (Fallback für headless JavaFX; Monocle ist die bevorzugte, voll
  headless Variante),
- installiert die **Aggregator-Parent-POM** ins lokale Maven-Repo (`mvn -N install`),
- wärmt die **Client-Raspi**-Dependencies vor (`dependency:go-offline`); die 6 Utility-Klassen
  des Package `org.kabieror.elwasys.common` liegen im Client-Raspi-Modul und werden dabei
  mitgebaut.

Der Container-Zustand wird nach dem Hook gecacht → Folge-Builds/Tests sind schnell.
Der Hook läuft **nur** remote (`$CLAUDE_CODE_REMOTE == true`), ist idempotent und
non-interaktiv. Ausführungsmodus **synchron** (garantiert fertige Abhängigkeiten
vor Session-Start; kann bei Bedarf auf async umgestellt werden).

> Wirksam für alle künftigen Sessions, sobald der Hook in den Default-Branch gemergt ist.

Lokaler Test des Hooks:
```bash
CLAUDE_CODE_REMOTE=true CLAUDE_PROJECT_DIR="$PWD" ./.claude/hooks/session-start.sh
```

## B) Portable `#cloud-config` (Ubuntu-VM) — für eigene Infrastruktur

Datei: `deploy/cloud-init/cloud-config.yaml`.

Provisioniert eine frische Ubuntu-VM mit JDK 21, Maven, PostgreSQL, Xvfb + GTK/Grafik-
Bibliotheken, klont das Repo, initialisiert die DB durch direktes Einspielen der Flyway-V1-Baseline
(`backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql` – die DB `elwasys` wird
vorher per `CREATE DATABASE` angelegt, da V1 keine `CREATE DATABASE`-Präambel hat) und wärmt den
Maven-Cache. Enthält `run-ui-tests.sh` zum headless Ausführen der Client-UI-Tests.

Beispiel (Multipass):
```bash
multipass launch --name elwasys --cloud-init deploy/cloud-init/cloud-config.yaml
```
Bei EC2/GCE/Hetzner als *user-data* verwenden.

## Umgebung

- **OS**: Ubuntu 24.04.4 LTS
- **Java**: OpenJDK 21.0.10
- **Maven**: 3.9.11
- **Xvfb**: vorhanden (`/usr/bin/Xvfb`, `xvfb-run`)
- **Docker-Daemon**: in dieser Sandbox nicht verfügbar (`docker ps` scheitert) – deshalb
  nutzen Backend-Tests hier den Local-PostgreSQL-Override statt Testcontainers (siehe
  docs/kb/04-build-and-run.md).

Ausgehende HTTPS-Verbindungen laufen über einen Agent-Proxy; der Java-Truststore ist per
`JAVA_TOOL_OPTIONS` gesetzt, Maven-Downloads funktionieren.

## Historie

- **2026-07-22** — Common-Modul aufgelöst; der Hook baut nur noch Client-Raspi/backend, die 6
  ehemaligen Common-Klassen liegen im Client-Raspi-Modul
  ([Worklog Phase-5-Nachtrag](../worklog/2026-07-22-phase-5-nachtrag-common-und-schema.md)).
- **2026-07-21** — Alt-Portal-Modul (Vaadin 7 WAR) aus dem Repo entfernt; das neue Portal-UI
  (Vaadin Flow) ist Teil von `backend` und wird normal mitgebaut
  ([Worklog Phase 5](../worklog/2026-07-21-phase-5-aufraeumen.md) ·
  [05-migration-plan.md](05-migration-plan.md)).
- **2026-07-19** — SessionStart-Hook und portable Cloud-Init-Konfiguration angelegt; Remote-Setup
  (JDK 21, Maven, Xvfb) verifiziert
  ([Worklog Setup](../worklog/2026-07-19-setup-und-sicherheitsnetz.md)).
