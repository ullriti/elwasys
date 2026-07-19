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

### Phase 0 – Verständnis & Absicherung *(aktuell)*
- [x] KB anlegen, Projekt erforschen, Übersicht dokumentieren
- [ ] Cloud-Init/Remote-Umgebung für Build & (headless) UI-Tests
- [ ] UI-Tests für bestehende Software (Client zuerst) → Verhalten festhalten
- [ ] Reproduzierbarer Build aller Module in der Remote-Umgebung

### Phase 1 – Build & Konsolidierung
- [ ] Aggregator-Parent-POM, einheitliche Versionen/Properties
- [ ] Java-Level vereinheitlichen
- [ ] Test-Frameworks vereinheitlichen (JUnit 5), bestehende Tests lauffähig
- [ ] PR-CI (Build + Tests) einrichten

### Phase 2 – Abhängigkeiten & Security
- [ ] DB-Treiber/HTTP-Clients modernisieren
- [ ] Passwort-Hashing modernisieren (+ DB-Migration)
- [ ] Default-Secrets aus Init-SQL entfernen/härten

### Phase 3 – UI-Modernisierung (Portal)
- [ ] Vaadin-7-Ablösung evaluieren (Vaadin Flow vs. Alternativstack) und umsetzen

> Reihenfolge/Scope sind mit dem Auftraggeber abzustimmen, bevor Phase 1+ beginnt.

## Offene Fragen / mit Auftraggeber klären
- Zielsprachlevel: Java 17 oder 21?
- Portal: Vaadin-Upgrade vs. Neuaufbau des Frontends – gewünschter Aufwand?
- Ist die mobile App (`elwaapi`) noch relevant / im Scope?
- Soll produktives Verhalten 1:1 erhalten bleiben oder sind Funktionsänderungen erwünscht?

## Änderungslog
| Datum | Änderung |
|-------|----------|
| 2026-07-19 | Erstfassung des Plans erstellt |
