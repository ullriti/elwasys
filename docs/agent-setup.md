# Agenten-Setup

Dieses Repo hält die Agenten-Instruktionen an **einer** Stelle.

## Prinzip: eine Wahrheit, ein Import

[`AGENTS.md`](../AGENTS.md) ist die **Single Source of Truth**. Claude Code liest
`CLAUDE.md`, das `AGENTS.md` nur per Import hereinzieht:

```markdown
# CLAUDE.md
… @AGENTS.md …
```

Claude Code löst `@AGENTS.md` beim Laden auf und übernimmt den Inhalt – **kein Symlink,
kein Sync-Skript, keine Kopie**. Editiert wird also ausschließlich `AGENTS.md`.

Warum `AGENTS.md` als Basis statt alles in `CLAUDE.md`? So bleibt die Quelle
werkzeug-neutral (`AGENTS.md` ist ein offener Standard): kommt später ein weiteres
Werkzeug dazu, referenziert es einfach ebenfalls `AGENTS.md`.

Werkzeug-spezifisches **Verhalten** für Claude liegt in `.claude/` (settings, commands,
agents, skills, SessionStart-Hook). Persönliche, nicht committete Overrides
(Zusatz-Permissions o. Ä.) gehören in `.claude/settings.local.json` – git-ignoriert,
Startpunkt ist `.claude/settings.local.json.example`.

## Regeln/Instruktionen mit Claude

Es gibt **keinen `rules/`- oder `instructions/`-Ordner** (das ist ein Copilot/Cursor-
Konzept). Claude kennt zwei Ebenen:

- **Projektweit** → `AGENTS.md` (Wurzel), per `CLAUDE.md` hereingezogen. Gilt immer.
- **Modul-/pfadspezifisch** → eine **`CLAUDE.md` im jeweiligen Unterordner**. Claude Code
  lädt sie automatisch, sobald an Dateien darunter gearbeitet wird (z. B.
  `backend/CLAUDE.md` für Backend-Regeln). Das ist Claudes Entsprechung zu Copilots
  `applyTo`-Globs – ordnerbasiert statt glob-basiert.

## Struktur des Wissenssystems

| Ort | Zweck |
|-----|-------|
| [`docs/kb/`](docs/kb/README.md) | Knowledge Base – zentrale Projektwahrheit + „Current state" |
| [`docs/worklog/`](worklog/README.md) | Arbeitsjournal, ein Eintrag je Session |
| [`docs/specs/`](specs/README.md) | Spezifikationen – *was* gebaut wird |
| [`docs/architecture/`](architecture/) | ADRs – *warum* entschieden wurde |
| [`CHANGELOG.md`](../CHANGELOG.md) | Nennenswerte Änderungen (Keep a Changelog) |

**Wissen gehört ins Repo, nicht in den lokalen User-Speicher** (`~/.claude/`,
`#`-Memory): nur im Repo überlebt es Session- und Maschinenwechsel und ist für alle
sichtbar. Nach Umstrukturierungen `/audit-ai-docs` bzw. `scripts/check-ai-docs.sh`
laufen lassen.

## Ein weiteres Werkzeug hinzufügen (falls je nötig)

- **Werkzeug unterstützt Imports** (wie Claude Code) → dünne Einstiegsdatei, die
  `AGENTS.md` referenziert.
- **Werkzeug liest nur eine feste Datei ohne Imports** → Symlink auf `AGENTS.md` oder
  eine (synchron gehaltene) Kopie.
