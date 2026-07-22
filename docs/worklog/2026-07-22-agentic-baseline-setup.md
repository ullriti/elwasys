# 2026-07-22 — agentic-baseline-Setup übernehmen und Bestand einordnen

**Ziel:** Das Setup aus https://github.com/ullriti/agentic-baseline übernehmen
(AGENTS.md als Single Source of Truth, `docs/`-Wissenssystem, `.claude/`-Agenten/Commands,
Scripts, Root-Configs) und den bestehenden elwasys-Code/Doku sauber darin einordnen –
insbesondere die vermischte KB entwirren.

## Erledigt
- **AGENTS.md** als zentrale Instruktion (Baseline-Sektionen 0–8) angelegt und
  elwasys-spezifisch gefüllt (Sprachen, Stack, Build/Test, Arbeitsregeln, Konventionen,
  Verzeichnis-Guide, Guardrails). **CLAUDE.md** auf den Thin-Import `@AGENTS.md` reduziert.
- **KB nach `docs/kb/` migriert** (`git mv`, Historie als Renames erhalten) und alle
  541 externen `kb/`-Referenzen in 262 Dateien mechanisch auf `docs/kb/` umgebogen
  (nur Kommentare/Doku; verifiziert, dass keine funktionalen Strings betroffen sind).
- **KB entwirrt** (Kernauftrag): das große „Status-Log" aus `docs/kb/README.md` und der
  „Änderungslog" aus `docs/kb/05-migration-plan.md` sind ins **Worklog** (8 phasenweise
  Einträge + Index) bzw. ins **CHANGELOG.md** überführt; der Block „Entscheidungen
  (Auftraggeber)" ist als **14 ADRs** (0002–0015) nach `docs/architecture/` extrahiert.
  `docs/kb/README.md` auf Inhalt + „Aktueller Stand"-Snapshot eingedampft (Quell-KB-
  Fachdokumente 00–08 inhaltlich unverändert).
- **`.claude/`-Struktur**: Commands `adapt-baseline`/`audit-ai-docs`/`review`; Agenten
  `orchestrator` (mit konkretem Domänen-Routing) + `code-reviewer` + die vier
  Spezialisten `backend`/`terminal`/`portal`/`devops`; `settings.json` um `permissions`
  (allow git-read, deny Secrets/.env) ergänzt (bestehender SessionStart-Hook erhalten);
  `skills/README.md`, `settings.local.json.example`.
- **`docs/`-Gerüst**: `agent-setup.md`, `specs/` (README + Template), `architecture/`
  (ADR-0001 + Index), `worklog/`.
- **Root-Configs & Scripts**: `.editorconfig` (Java 4-Space, XML Tab), `.gitattributes`,
  `.env.example`, `.vscode/`, `scripts/bootstrap.sh` (elwasys-Build) und
  `scripts/check-ai-docs.sh` (an die deutsche KB-Überschrift „Aktueller Stand" angepasst).

## Entscheidungen
- **Volle Migration nach `docs/`** statt KB am Wurzelort belassen (Auftraggeber-Wunsch),
  inkl. mechanischer Referenz-Umstellung im gesamten Repo.
- **KB inhaltlich getrennt** nach Zweck: Sollzustand → `docs/kb/`, Historie → Worklog,
  veröffentlichte Änderungen → CHANGELOG, Entscheidungen → ADRs. Die historischen
  Modernisierungs-Arbeitspakete wurden **nicht** rückwirkend als Einzel-Specs angelegt
  (sie sind im Modernisierungsplan dokumentiert); `docs/specs/` ist für neue Vorhaben.
- Verzeichnis-Weiterleitung/Domänen-Routing der Agenten an den realen Modulzuschnitt
  (Root-Reactor = 2 Module) ausgerichtet.

## Offen / nächster Schritt
- `scripts/check-ai-docs.sh` grün; struktureller Doku-Audit besteht. Inhaltliche
  Feinabstimmung der KB-Fachdokumente (z. B. Alt-Erwähnungen des `Common`-Moduls) bleibt
  laufende Pflege, kein Teil dieses Setup-Umbaus.

## Referenzen
- Vorlage: https://github.com/ullriti/agentic-baseline
- AGENTS.md, docs/agent-setup.md, docs/architecture/ (ADR 0001–0015),
  docs/kb/05-migration-plan.md, CHANGELOG.md
