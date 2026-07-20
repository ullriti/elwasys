# 05 – Migrations-/Modernisierungsplan (lebendes Dokument)

> Dieses Dokument wird laufend fortgeschrieben. Es hält den **Zielzustand**, die
> **Reihenfolge** der Schritte und den **Fortschritt** fest.

## Leitgedanken

1. **Erst absichern, dann umbauen.** Bevor größere Refactorings passieren, brauchen wir ein
   Sicherheitsnetz: reproduzierbarer Build (Remote/Cloud-Init) + UI-/Integrationstests, die
   das bestehende Verhalten festhalten (Characterization Tests).
2. **Kleine, überprüfbare Schritte.** Jeder Migrationsschritt ist einzeln baubar/testbar und
   wird committet.
3. **Verhalten bewahren.** Die Modernisierung soll das Nutzerverhalten nicht verändern,
   solange nicht explizit gewünscht.

## Ist → Ziel (Kandidaten)

| Bereich | Ist | Ziel-Kandidat | Priorität |
|---------|-----|---------------|-----------|
| Build-Struktur | 3 lose POMs, kein Parent | Aggregator-Parent-POM, einheitliche Versionen | Hoch |
| Java-Level | 8 / 16 gemischt | einheitlich LTS (17 oder 21) | Hoch |
| Portal-UI | Vaadin 7.6.8 + GWT 2.7 | Vaadin (aktuell) oder Alternative (Spring Boot + Web-Frontend) | Hoch (aufwändig) |
| Test-Frameworks | JUnit4 + TestNG gemischt | JUnit 5 einheitlich | Mittel |
| UI-Tests | keine | TestFX (Client), Vaadin/Playwright (Portal) | Hoch (jetzt) |
| CI | nur Release-Build | PR-CI mit Build + Tests | Hoch |
| Passwörter | SHA1 | bcrypt/argon2 + Migrationspfad | Hoch (Security) |
| DB-Treiber | uneinheitlich (Portal 9.3) | einheitlicher aktueller Treiber | Mittel |
| HTTP-Clients | HttpComponents + unirest 1.x | ein moderner Client (java.net.http) | Mittel |
| Secrets/Defaults | schwache Default-PW im Init-SQL | Härten, keine Default-Klartext-PW | Hoch (Security) |

## Reihenfolge (Roadmap)

### Phase 0 – Verständnis & Absicherung *(abgeschlossen 2026-07-20)*
- [x] KB anlegen, Projekt erforschen, Übersicht dokumentieren
- [x] Cloud-Init/Remote-Umgebung für Build & (headless) UI-Tests (Hook + cloud-config)
- [x] UI-Tests Client (TestFX/Xvfb) – **21 Tests grün** (C1–C16 + Cross-Component P21/P22)
- [x] UI-Tests Portal (Playwright E2E) – **18 Tests grün** (P1–P20)
- [x] Reproduzierbarer Build: Common ✅, Client ✅, Portal ✅ (repariert)
- [x] Zustandsübergänge der Client-State-Machine über die E2E-Suite abgesichert
      (SELECT_DEVICE→CONFIRMATION, Auto-Logout, Abbruch, Auto-Ende, Resume, ERROR)

> **Fazit Phase 0:** Das Sicherheitsnetz (reproduzierbarer Build + verhaltensfixierende
> UI-/E2E-Tests beider Frontends inkl. Wartungsverbindung) steht. Die *isolierte*
> Unit-Charakterisierung der State-Machine (`MainFormStateManager`) wird bewusst nach
> Phase 1 verschoben, da sie die Entkopplung der harten `ElwaManager`-Kopplung (DI)
> voraussetzt – und dieser Umbau verändert Produktivcode und gehört damit in Phase 1.

### Phase 1 – Build & Konsolidierung
- [ ] Aggregator-Parent-POM, einheitliche Versionen/Properties
- [ ] Java-Level vereinheitlichen
- [ ] Test-Frameworks vereinheitlichen (JUnit 5), bestehende Tests lauffähig
- [x] PR-CI (Build + Tests) einrichten *(vorgezogen: ci.yml, 3 Jobs Common/Client/Portal grün)*
- [ ] `ElwaManager`-Kopplung per DI entkoppeln → ermöglicht isolierte
      State-Machine-Charakterisierung (aus Phase 0 übernommen)

### Phase 2 – Abhängigkeiten & Security
- [ ] DB-Treiber/HTTP-Clients modernisieren
- [ ] Passwort-Hashing modernisieren (+ DB-Migration)
- [ ] Default-Secrets aus Init-SQL entfernen/härten

### Phase 3 – UI-Modernisierung (Portal)
- [ ] Vaadin-7-Ablösung evaluieren (Vaadin Flow vs. Alternativstack) und umsetzen

> Reihenfolge/Scope sind mit dem Auftraggeber abzustimmen, bevor Phase 1+ beginnt.

## Entscheidungen (Auftraggeber)
- **2026-07-19**: UI-Tests **parallel** für Client (TestFX/Monocle) **und** Portal (E2E) aufbauen.
- **2026-07-19**: Java-Ziellevel **vorerst offen** – Fokus zunächst auf Tests.

## Offene Fragen / mit Auftraggeber klären
- Zielsprachlevel: Java 17 oder 21? *(vorerst offen gelassen)*
- Portal: Vaadin-Upgrade vs. Neuaufbau des Frontends – gewünschter Aufwand?
- Ist die mobile App (`elwaapi`) noch relevant / im Scope?
- Soll produktives Verhalten 1:1 erhalten bleiben oder sind Funktionsänderungen erwünscht?

## Änderungslog
| Datum | Änderung |
|-------|----------|
| 2026-07-19 | Erstfassung des Plans erstellt |
| 2026-07-20 | **Phase 0 abgeschlossen** (Build + UI/E2E-Sicherheitsnetz steht: Client 21, Portal 18, Cross-Component grün); isolierte State-Machine-Charakterisierung + ElwaManager-DI nach Phase 1 verschoben; PR-CI (vorgezogen) grün |
