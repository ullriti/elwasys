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
  Zuletzt wurde das Doku-/Agenten-Setup auf die **agentic-baseline**-Struktur umgestellt
  (AGENTS.md als Single Source of Truth, `docs/`-Wissenssystem, `.claude/`-Agenten).
- **Nächster Schritt:** Betrieb/Nachpflege auf der Zielarchitektur; neue Vorhaben vorab als
  Spec in [`../specs/`](../specs/README.md) und Entscheidungen als ADR festhalten. Die
  Detail-Roadmap/Restpunkte stehen in [05-migration-plan.md](05-migration-plan.md).
- **Details:** siehe den jeweils letzten Eintrag im [Worklog](../worklog/README.md).

## Regeln für die KB

- Nach jedem Arbeitspaket aktualisieren; „Aktueller Stand" oben überschreiben und einen
  Worklog-Eintrag anlegen.
- Entscheidungen des Auftraggebers/Teams als **ADR** in [`../architecture/`](../architecture/)
  festhalten (inkl. Begründung); der Roadmap-/Entscheidungskontext bleibt in
  [05-migration-plan.md](05-migration-plan.md).
- **Wissen gehört ins Repo, nicht in den lokalen User-Speicher** (`~/.claude/`,
  `#`-Memory) – siehe [`../worklog/README.md`](../worklog/README.md).
