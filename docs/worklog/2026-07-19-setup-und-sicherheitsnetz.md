# 2026-07-19 — Setup und Sicherheitsnetz

**Ziel:** Das Projekt für die Modernisierung erschließen: Knowledge Base anlegen, den
Build in der Remote-Umgebung zum Laufen bringen und ein automatisiertes UI-/E2E-Test-
Sicherheitsnetz für Client (JavaFX) und Portal (Vaadin) aufbauen, damit Umbauten das
nutzer-sichtbare Verhalten nachweisbar bewahren.

## Erledigt
- Knowledge Base angelegt, Projekt-Recherche und Übersicht erstellt (docs/kb/).
- Build in der Remote-Umgebung verifiziert (Common install, Client compile/test-compile).
- SessionStart-Hook + portable cloud-config für Remote-Build/Tests erstellt und validiert.
- Client-UI-Test-Harness aufgebaut (TestFX/Xvfb + JUnit 5); 2 headless Tests grün.
- Alt-Portal-Build repariert (Vaadin/GWT/War/JDBC/API-Drift); `jetty:run` liefert die Login-Seite.
- Portal-E2E-Suite aufgebaut (Playwright, Node/TS); Login-Smoke-Test grün (2/2).
- Client-E2E: echte App headless hochgefahren (fhem-Simulator + DB) bis `SELECT_DEVICE`;
  dabei `getDeconzServer`-Bug gefunden und gefixt.
- Client-E2E vertieft (C2–C5: Geräteliste, Karten-Login, Gerät buchen, Programmstart) → 7/7 grün.
- Portal-E2E vertieft (P3–P5: falsches Passwort, Logout, Navigation aller Admin-Views) → 5/5 grün.
- Weitere Testfälle grün: Client C6–C10 (Login-Varianten, zu wenig Guthaben, Auto-Logout),
  Portal P6/P9/P10/P12 (Benutzer-/Gruppen-/Geräte-CRUD, Programm); CI-Flakiness durch
  geteilte DB behoben. Stand Tagesende: Client 12 / Portal 9 Tests.

## Offen / nächster Schritt
- Test-Suiten weiter vertiefen und Phase 0 (Sicherheitsnetz) formal abschließen; danach
  der Modernisierungsplan mit Rahmenbedingungen und Roadmap.

## Referenzen
- docs/kb/README.md (Status-Log), docs/kb/06-ui-tests.md, docs/kb/08-test-plan.md,
  docs/kb/07-cloud-init.md
