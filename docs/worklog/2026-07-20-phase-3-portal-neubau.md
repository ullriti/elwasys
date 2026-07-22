# 2026-07-20 — Phase 3: Portal-Neubau (AP1–AP6)

**Ziel:** Das Alt-Portal (Vaadin 7) als Vaadin-Flow-UI im Backend neu bauen – mit voller
Feature-Parität zum bestehenden Admin- und Benutzer-Portal – und die Feature-Parität durch
eine portierte Playwright-E2E-Suite (P1–P20) nachweisen. Weiter additiv, ohne die
Altmodule zu ändern. (AP1–AP3 am 2026-07-20, AP4–AP6 am 2026-07-21.)

## Erledigt
- **AP1 – Vaadin-Flow-Grundgerüst**: `vaadin-spring-boot-starter` (Vaadin 24.10.8) im
  Backend; Login-View (deutsche Texte 1:1 aus dem Alt-Portal), rollenbasierte Weiterleitung
  (`RootView`), Admin-Layout (`AppLayout`+`SideNav`) und Benutzer-Layout, abgesichert über
  `VaadinSecurityConfigurer`. Zwei Fallstricke behoben (Vaadin-Autokonfig bricht
  `webEnvironment=NONE`-Tests; `VaadinSecurityConfigurer` braucht echten `ServletContext`).
  **Befund**: Vaadin 24.10.x verlangt im Dev-Modus einen Online-Lizenzcheck gegen
  vaadin.com, den die netzwerk-eingeschränkte Sandbox nicht bedienen kann (Tests laufen mit
  `vaadin.productionMode=true`); als offene Frage an den Auftraggeber vermerkt. 116/116 grün.
- **AP2 – Stammdaten-Views mit Feature-Parität**: die 5 Admin-Views (Benutzer,
  Benutzergruppen, Geräte, Programme, Standorte) mit echten Vaadin-Grids + modalen CRUD-
  Dialogen (Nachfolger der Alt-`*Window`-Fenster) inkl. Lösch-Wächtern und Benutzer-Sperren.
  5 neue Services + 2 Exceptions, 19 neue Tests → 135/135. **De-Risking**: Produktionsmodus-
  Build (`-Pproduction`) liefert nachweislich echte Vaadin-Seiten aus (entschärft das AP1-
  Lizenzrisiko technisch, lässt die rechtliche Frage offen).
- **AP3 – Dashboard, Guthaben, UserDashboard**: `AdminDashboardView` (je Standort Geräte
  „Frei"/„Besetzt" + laufende Ausführung + Historie) über Vaadin-freien `DashboardService`;
  Guthaben aufladen (`CreditTopUpDialog`, P8) und Historie (`CreditHistoryDialog`) über die
  bestehenden Phase-2-Methoden `CreditService#inpayment`/`#payout` – Buchungen bleiben
  unveränderlich. `UserDashboardView` (eigenes Guthaben + Historie, P15). 9 neue Tests → 144/144.
- **AP4 – Dialoge/Funktionen**: Passwort ändern (P16, `PasswordService`), UserSettings
  (P17), Passwort per E-Mail zurücksetzen (P19, `PasswordResetService`, wiederverwendet
  `password_reset_key`/`-timeout`; eigener Schalter `elwasys.password-reset.enabled`,
  Default AN), ExpiredExecutions, Log-Viewer/Fernwartung über den Backend-WS-Kanal
  (`TerminalMaintenanceService`, WS additiv um `LOG_REQUEST`/`RESTART_REQUEST` erweitert;
  Alt-TCP-Protokoll bewusst NICHT portiert, Alt-Portal bleibt bis Cutover in Betrieb).
  17 neue Tests → 161/161.
- **AP5 – Live-Updates zwischen Sessions**: **Befund** – das Alt-Portal aktiviert trotz
  `vaadin-push`-Dependency nirgends `@Push`; die `events/`-Listener sind reine Same-Session-
  Callbacks. Neues Vaadin-freies `events`-Package (7 `DomainEvent`-Records); alle 7 Services
  publizieren über `ApplicationEventPublisher` (auch REST-API-ausgelöste Änderungen feuern
  Ereignisse). `UiBroadcaster` verteilt über `@TransactionalEventListener(AFTER_COMMIT)` +
  `UI#access(...)`; `@Push` auf `ElwasysAppShell`. Views abonnieren gezielt mit sauberem
  attach/detach. 12 neue Tests → 173/173.
- **AP6 – Playwright-E2E P1–P20 portiert**: neue Suite `backend/e2e/` (Node/TS) startet
  PostgreSQL + dedizierte E2E-DB, baut/startet das Backend-Jar im Produktionsmodus und fährt
  alle 20 Testfälle gegen das neue Portal – inklusive **P11** („Gerät aktiv/inaktiv"), das
  in der Alt-Suite nie umgesetzt war. Selektor-Strategie auf Vaadin Flow umgestellt
  (Fallstrick: `vaadin-grid`-Zellen liegen im Light-DOM). **20/20 grün, ≥5 Läufe ohne
  Fehlschlag/Retry.** Bugfix: „Passwort vergessen?"-Knopf hatte englischen Vaadin-Default.
  Alt-Portal aus dem CI-Playwright-Pfad genommen (schlanker `portal-legacy-build`-Job bleibt
  bis Phase 5).

## Offen / nächster Schritt
- Phase 4 (Terminal-Modernisierung): JavaFX-Update, Client-Cutover auf die REST-API,
  Fernwartung umdrehen, Offline-Robustheit.

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog), docs/kb/03-modules.md, docs/kb/06-ui-tests.md,
  docs/kb/02-data-model.md, docs/kb/08-test-plan.md
