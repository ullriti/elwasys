# 07 – Remote-/Cloud-Init-Umgebung

Ziel: Build und (headless) UI-Tests **remote** reproduzierbar ausführen können. Es gibt zwei
sich ergänzende Mechanismen:

## A) SessionStart-Hook (Claude Code on the web) — primär

Datei: `.claude/hooks/session-start.sh`, registriert in `.claude/settings.json`.

Dieser Hook bereitet **jede Remote-Session** automatisch vor:
- prüft Java/Maven,
- stellt Xvfb bereit (Fallback für headless JavaFX; Monocle ist die bevorzugte, voll
  headless Variante),
- installiert **Common** ins lokale Maven-Repo,
- wärmt die **Client-Raspi**-Dependencies vor (`dependency:go-offline`).

Der Container-Zustand wird nach dem Hook gecacht → Folge-Builds/Tests sind schnell.
Der Hook läuft **nur** remote (`$CLAUDE_CODE_REMOTE == true`), ist idempotent und
non-interaktiv. Ausführungsmodus derzeit **synchron** (garantiert fertige Abhängigkeiten
vor Session-Start; kann bei Bedarf auf async umgestellt werden).

> Wirksam für alle künftigen Sessions, sobald der Hook in den Default-Branch gemergt ist.

Lokaler Test des Hooks:
```bash
CLAUDE_CODE_REMOTE=true CLAUDE_PROJECT_DIR="$PWD" ./.claude/hooks/session-start.sh
```

## B) Portable `#cloud-config` (Ubuntu-VM) — für eigene Infrastruktur

Datei: `kb/cloud-init/cloud-config.yaml`.

Provisioniert eine frische Ubuntu-VM mit JDK 21, Maven, PostgreSQL, Xvfb + GTK/Grafik-
Bibliotheken, klont das Repo, initialisiert die DB (`database-init.sql`) und wärmt den
Maven-Cache. Enthält `run-ui-tests.sh` zum headless Ausführen der Client-UI-Tests.

Beispiel (Multipass):
```bash
multipass launch --name elwasys --cloud-init kb/cloud-init/cloud-config.yaml
```
Bei EC2/GCE/Hetzner als *user-data* verwenden.

## Verifizierter Zustand dieser Umgebung (2026-07-19)

| Check | Ergebnis |
|-------|----------|
| OS | Ubuntu 24.04.4 LTS |
| Java | OpenJDK 21.0.10 ✅ |
| Maven | 3.9.11 ✅ |
| Xvfb | vorhanden (`/usr/bin/Xvfb`, `xvfb-run`) ✅ |
| `mvn install` Common | ✅ erfolgreich |
| `mvn compile` Client-Raspi | ✅ erfolgreich |
| `mvn test-compile` Client-Raspi | ✅ erfolgreich |
| SessionStart-Hook end-to-end | ✅ Exit 0 |
| Linter | ❌ keiner konfiguriert (Kandidat für Phase 1) |
| Laufende Testsuite | ❌ noch keine (bestehende Tests sind TestNG-Simulatoren; UI-Tests folgen) |

## Hinweise / Stolperfallen
- **Portal** wird vom Hook bewusst **nicht** gebaut: Versionskonflikt
  (`common:0.3.4-SNAPSHOT` vs. `0.0.0-local-development`) + schwergewichtige Vaadin-/GWT-
  Widgetset-Compilation. Portal-Build/-Tests kommen in einer späteren Phase (siehe
  05-migration-plan.md).
- Ausgehende HTTPS-Verbindungen laufen in dieser Umgebung über einen Agent-Proxy; der
  Java-Truststore ist per `JAVA_TOOL_OPTIONS` gesetzt. Maven-Downloads funktionieren.
