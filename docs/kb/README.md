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
  (AGENTS.md als Single Source of Truth, `docs/`-Wissenssystem, `.claude/`-Agenten). Zusätzlich
  gibt es seit 2026-07-22 einen **Demo-Modus** (Profil `demo`, `DemoDataSeeder` + `backend/run-demo.sh`)
  mit wiederverwendbarem Beispielbestand fürs visuelle UI-Prüfen (siehe
  [06-ui-tests.md](06-ui-tests.md) „Demo-Daten", [04-build-and-run.md](04-build-and-run.md) „Demo-Modus").
  Seit 2026-07-22 läuft die **Pre-Launch-Review** (Epic #66): **AP1 (Offline-/Replay-Kern,
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
- **Nächster Schritt:** restliche Pre-Launch-Arbeitspakete – AP5 (Portal-Performance/CRUD/
  Datenmodell) und AP6 (Deployment/Betrieb/Cutover, inkl. Vaadin-Lizenz-🧩) – je als eigener
  PR, **AP7 (KB)** zuletzt; danach Betrieb/Nachpflege auf der Zielarchitektur; neue Vorhaben
  vorab als Spec in [`../specs/`](../specs/README.md) und Entscheidungen als ADR festhalten.
  Die Detail-Roadmap/Restpunkte stehen in [05-migration-plan.md](05-migration-plan.md).
- **Details:** siehe den jeweils letzten Eintrag im [Worklog](../worklog/README.md).

## Regeln für die KB

- Nach jedem Arbeitspaket aktualisieren; „Aktueller Stand" oben überschreiben und einen
  Worklog-Eintrag anlegen.
- Entscheidungen des Auftraggebers/Teams als **ADR** in [`../architecture/`](../architecture/)
  festhalten (inkl. Begründung); der Roadmap-/Entscheidungskontext bleibt in
  [05-migration-plan.md](05-migration-plan.md).
- **Wissen gehört ins Repo, nicht in den lokalen User-Speicher** (`~/.claude/`,
  `#`-Memory) – siehe [`../worklog/README.md`](../worklog/README.md).
