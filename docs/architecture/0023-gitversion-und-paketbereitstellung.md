# 23. Versionierung mit GitVersion und Paketbereitstellung über GHCR/GitHub-Releases

- **Status:** accepted
- **Datum:** 2026-07-27

## Kontext

Vor dieser Entscheidung gab es im Repo **keinen einzigen Tag und kein einziges Release**.
Die Version war der Sentinel `0.0.0-local-development` an vier Stellen (Root-POM inkl.
beider Modul-POMs, `Utilities.APP_VERSION` als Java-Konstante, `Chart.yaml` `appVersion`);
eine echte Version entstand ausschließlich im Release-Workflow, und zwar aus dem Tag, den
jemand von Hand in der GitHub-Oberfläche eingetippt hatte (`maven-publish.yml`, Trigger
`release: created`). Daraus folgten drei Probleme:

1. **Die Version war so gut wie der Tippfehler.** Nichts leitete sie aus dem tatsächlichen
   Inhalt der Änderungen ab, nichts prüfte sie gegen den Vorgänger.
2. **Alle anderen Builds logen.** Weil `APP_VERSION` nur im Release per `sed` ersetzt wurde,
   meldeten CI- und Entwicklungs-Builds dauerhaft den Sentinel – auch dort, wo die Version
   nutzersichtbar wird (die `clientVersion` im WebSocket-Hello, die das Portal anzeigt).
3. **Es gab keinen Zwischenschritt zwischen „gemerged" und „auf allen Terminals".** Ein
   Stand war entweder unveröffentlicht oder sofort das, was `releases/latest` liefert und
   der Auto-Update-Watchdog ausrollt.

Der Auftraggeber hat am 2026-07-27 vier Festlegungen getroffen (automatische Vorabversion +
manuelle Freigabe; Bump aus Conventional Commits; Tag-Leiter/OCI-Labels, Helm-Chart als
OCI-Artefakt und SBOM/Provenance als Ausbaustufen; `APP_VERSION` aus dem Jar-Manifest) und
ergänzt, dass ein Push auf `master`, der weder Code noch Paket verändert, gar keinen Lauf
auslösen soll. Multi-Arch-Images wurden bewusst **nicht** beauftragt.

## Entscheidung

**Versionierung** übernimmt **GitVersion** (`GitVersion.yml`, Werkzeug-Version 6.3.x über
`gittools/actions`). Der Bump kommt aus den **Conventional Commits** – möglich, weil PRs per
Squash gemerged werden und der PR-Titel dabei zur `master`-Commit-Message wird
(`feat:` → minor, `fix:`/`perf:`/`refactor:` → patch, `!`/`BREAKING CHANGE` → major); die
`+semver:`-Marker bleiben als Notausgang. `master` liefert fortlaufende Vorabversionen
(`0.5.0-rc.7`), Feature-Branches `-alpha.N`. Tags tragen **kein `v`-Präfix**, damit
durchgängig Tag == SemVer == Jar-Dateiname == Image-Tag == Chart-Version gilt.

**Zwei Kanäle** in einem Workflow (`.github/workflows/release.yml`):

- **Push auf `master` → Vorab-Release.** GitHub-Release als *Pre-Release* markiert,
  Image-Tags `<version>` und `edge`, **kein** `:latest`.
- **`workflow_dispatch` → stabiles Release.** Tag auf dem SemVer-Kern, `:latest`,
  Tag-Leiter `<major>.<minor>` und `<major>`, Terminal-Rollout.

Der Testkanal fällt dabei **ohne eigenen Mechanismus** ab: `/releases/latest` liefert nie
ein Pre-Release aus, also sehen die Terminals im Feld Vorabversionen nicht; ein
Test-Terminal zieht sie gezielt über die bereits existierende `.update-target`-Datei.

**Paketbereitstellung** je Auslieferungsweg:

- **Backend** → Container-Image in GHCR (`ghcr.io/<owner>/elwasys-backend`) mit Tag-Leiter,
  `org.opencontainers.image.*`-Labels, SBOM und Provenance-Attestation; dazu der
  **Helm-Chart als OCI-Artefakt** (`ghcr.io/<owner>/charts/elwasys-backend`), dessen
  Version und `appVersion` beim Paketieren an die Release-Version gekoppelt werden.
- **Terminal** → unverändert fat-jar + `.sha256` als GitHub-Release-Asset, weil genau dort
  `setup.sh` und der Auto-Update-Watchdog es abholen (nur HTTPS zu github.com, kein
  Registry-Login auf dem Pi).

**`Utilities.APP_VERSION`** liest die Version aus `Implementation-Version` im Jar-Manifest
(von Maven aus `${project.version}` gefüllt), mit dem Sentinel als Fallback für Läufe ohne
Jar. Der `sed`-Eingriff in eine Java-Quelldatei entfällt; der Release-Workflow prüft
stattdessen am gebauten Artefakt, dass die Kette `versions:set` → Manifest wirklich greift.

**Build-Relevanz auf `master`:** `scripts/commit-triggers-build.sh` entscheidet anhand der
Commit-Message, ob CI und Release überhaupt laufen (`docs:`/`style:`/`chore:`/`test:`/`ci:`
→ nein, alles andere und jeder Breaking-Change-Marker → ja). Tragfähig ist das, weil jeder
`master`-Commit über einen PR kam, dessen Lauf die vollständige Suite grün gesehen hat.

## Konsequenzen

- Die Version entsteht reproduzierbar aus Tags + Historie; sie stimmt in **jedem** Build,
  nicht nur im Release.
- Jeder relevante Merge erzeugt ein installierbares, aber für das Feld unsichtbares Paket –
  ein Freigabeschritt bleibt eine bewusste, manuelle Handlung.
- Vorab-Releases hinterlassen `-rc.N`-Tags im Repo. Das ist der Preis dafür, dass ein
  GitHub-Release zwingend einen Tag braucht, und zugleich die Grundlage dafür, dass
  GitVersion die Reihe fortschreibt.
- Die Versionsreihe startet über `next-version: 1.0.0` (Auftraggeber-Entscheidung
  2026-07-27): der letzte veröffentlichte Stand vor dem Umbau war 0.4.2, der erste Stand
  nach der Modernisierung ist die erste Vollversion. Sobald der Tag `1.0.0` steht, ist die
  Angabe wirkungslos.
- Verlässt sich auf die Commit-Disziplin: ein falsch typisierter PR-Titel bumpt falsch bzw.
  unterdrückt einen Lauf. Der Guard ist deshalb fail-safe gebaut (unbekanntes Format → bauen),
  und die berechnete Version steht in der PR-Job-Summary, bevor gemerged wird.
- Multi-Arch (arm64) bleibt offen – nachrüstbar über `platforms:` im Build-Schritt, kostet
  Buildzeit über QEMU.
