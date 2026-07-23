# 08 – Testplan: Frontend-Tests

Dieser Plan beschreibt den Umfang der UI-/E2E-Tests beider Frontends. Die Infrastruktur ist in
[06-ui-tests.md](06-ui-tests.md) dokumentiert:
- **Client**: TestFX + Xvfb, echte App über `Main`, `FhemSimulator`/`DeconzSimulator` als
  Gateways, echtes Test-Backend über die REST-API, Test-DB.
- **Portal**: Playwright (Node/TS), echtes Vaadin-Flow-Portal im Backend (`backend/e2e/`),
  frische Test-DB je Lauf.

Legende Priorität: **P1** = Kern-Happy-Path, **P2** = wichtige Varianten/Guards, **P3** =
Lifecycle/Robustheit, **P4** = fortgeschritten/abhängig.

---

## A) Client (JavaFX) – E2E-Umfang

**Test-Fixtures** (JDBC-Seed bzw. – seit dem Terminal-Cutover – über die echten
Backend-Repositories, Reset zwischen Tests):
- Benutzergruppen (inkl. eine, die am Standort *nicht* erlaubt ist)
- Benutzer mit `card_ids` (RFID = Ziffern), Guthaben, blocked-Flag
- Geräte am Standort „Default" (fhem `wm1sw…wm4sw` bzw. deCONZ `wm1…wm4`, passend zum jeweiligen
  Simulator), enabled/disabled, gültige Gruppen
- Programme: FIXED und DYNAMIC (flagfall, rate, time_unit), verknüpft mit Gerät
- ggf. laufende `executions` (für Resume-Test)

| ID | Prio | Testfall | Kern-Assertion |
|----|------|----------|----------------|
| C1 | P1 | Startup → Geräteauswahl | State `SELECT_DEVICE` |
| C2 | P1 | Geräteliste rendert geseedetes Gerät des Standorts | Gerätename sichtbar |
| C3 | P1 | Karten-Login gültiger Benutzer (Ziffern + Enter tippen) | `registeredUser` gesetzt |
| C4 | P1 | Gerät wählen → Programmliste des Geräts | State `CONFIRMATION`, Programm sichtbar |
| C5 | P1 | FIXED-Programm bestätigen (Start) | laufende Execution in DB (Gateway geschaltet) |
| C6 | P2 | Unbekannte Karte | `#userInfo` Style `card-unknown`, kein Login |
| C7 | P2 | Gesperrter Benutzer | `#userInfo` Style `user-blocked`, kein Login |
| C8 | P2 | Benutzergruppe am Standort nicht erlaubt | `#userInfo` Style `location-disallowed`, kein Login |
| C9 | P2 | Zu wenig Guthaben | `#confirmationPane` Style `credit-insufficient`, Start-Button disabled |
| C10 | P2 | Auto-Logout nach `sessionTimeout` Inaktivität | `registeredUser` wird null |
| C11 | P3 | Auto-Ende: Gerät zieht keine Leistung → Ende nach `auto_end_wait_time` | Execution automatisch beendet |
| C12 | P3 | Laufende Execution abbrechen (Bestätigung) | Execution gestoppt |
| C13 | P3 | Unterbrochene Execution beim Start fortsetzen (geseedet) | Execution wird als laufend übernommen |
| C14 | P3 | DYNAMIC-Programm: Preisanzeige (Grundgebühr/Zeitpreis) | „Grundgebühr"/„Zeitpreis" auf Bestätigung sichtbar |
| C15 | P3 | Backend beim Start nicht erreichbar (ohne nutzbaren Offline-Snapshot) → Fehlerzustand | State `ERROR` (statt Absturz), Retry möglich |
| C16 | P2 | Standortfremdes Gerät | erscheint **nicht** in der Liste (nur Geräte des eigenen Standorts) |

**Offline-Nachfolger zu C15** (Offline-Robustheit; bei nutzbarem Snapshot startet der Client im
Offline-Modus statt in `ERROR`, siehe docs/kb/03-modules.md „Offline-Robustheit"):

| ID | Prio | Testfall | Kern-Assertion | Testklasse |
|----|------|----------|-----------------|------------|
| C15a | P3 | Backend fällt während laufender Ausführung aus | lokal beendet + im Journal; nach Reconnect repliziert (`finished=true`) | `ClientOfflineRobustnessE2ETest` |
| C15b | P3 | Backend aus, neue Buchung innerhalb `offline.max-duration` | offline gegen den Snapshot akzeptiert, erst nach Replay beim Backend | `ClientOfflineRobustnessE2ETest` |
| C15c | P3 | Snapshot/Zeitfenster abgelaufen, Backend aus | neue Buchung abgelehnt, Fehlerbild wie C15 (`ERROR`) | `ClientOfflineRobustnessE2ETest` |
| C15d | P3 | Journal-Replay wird wiederholt (Absturz zwischen Backend-Erfolg und Journal-Löschung) | keine Doppelbuchung (Backend dedupliziert über den Idempotenz-Schlüssel) | `ClientOfflineReplayIdempotencyE2ETest` |

**Hinweise/Feasibility**
- Karten-Scan = Tastatureingabe (RFID-Leser emuliert Tastatur) → per TestFX
  `robot.type()`/`write()` simuliert.
- C11–C13 brauchen Timing/Interaktion mit dem Simulator (Schalt-/Power-Events).
- C13: laufende Execution (start gesetzt, `finished=false`) vor App-Start seeden; nach Boot
  übernimmt der `ExecutionManager` sie als laufend (`ElwaManager.initiate` scannt beim Start
  unerledigte Executions).
- C15: der Client zeigt auf ein unerreichbares Backend; die App stürzt nicht ab, sondern landet
  über `AbstractMainFormController.tryInitiate` im `ERROR`-Zustand mit Retry-Aktion
  (`ClientDatabaseErrorE2ETest`). Das Fehlerbild gilt nur, wenn zusätzlich kein nutzbarer
  Offline-Snapshot vorliegt.
- Display-/Backlight-Verhalten ist schwer zuverlässig zu assern → bewusst ausgelassen.

---

## B) Portal (Vaadin Flow) – E2E-Umfang

**Fixtures**: Seed/Reset über die Flyway-Baseline (admin/„Default") plus Playwright
`global-setup.ts` (zusätzliche Nicht-Admin-Benutzer für die Benutzer-Portal-Fälle). Details:
06-ui-tests.md.

| ID | Prio | Testfall | Kern-Assertion |
|----|------|----------|----------------|
| P1 | P1 | Login-Seite rendert | Titel/Felder/Button |
| P2 | P1 | Admin-Login → Dashboard | Admin-Menü sichtbar |
| P3 | P1 | Login mit falschem Passwort | Fehlermeldung, bleibt auf Login |
| P4 | P1 | Logout | zurück auf Login-Seite |
| P5 | P1 | Navigation: alle Admin-Views (Dashboard/Benutzer/Gruppen/Programme/Geräte/Standorte) | aktive Sektion je Menüpunkt |
| P6 | P2 | Benutzer anlegen (Name, Username, Gruppe) | erscheint in Benutzerliste |
| P7 | P2 | Benutzer bearbeiten: sperren | „Gesperrt" persistent (nach erneutem Öffnen gesetzt) |
| P8 | P2 | Guthaben aufladen | Guthaben in der Benutzerliste aktualisiert |
| P9 | P2 | Benutzergruppe anlegen | erscheint in Gruppenliste |
| P10 | P2 | Gerät anlegen (Name, Position, Standort, fhem-Namen) | erscheint in Geräteliste |
| P11 | P2 | Gerät aktiv/inaktiv schalten, bearbeiten | Zustand geändert |
| P12 | P2 | Programm anlegen (FIXED: Preis + Dauern) | erscheint in Programmliste |
| P13 | P2 | Entität löschen (Benutzergruppe, mit Bestätigung) | verschwindet aus Liste |
| P14 | P3 | Standort bearbeiten (eigener „Standorte"-Menüpunkt) | Name vorbelegt, Save-Round-Trip |
| P15 | P3 | Nicht-Admin-Login → Benutzer-Dashboard | „Guthaben"/„Übersicht" sichtbar |
| P16 | P3 | Eigenes Passwort ändern | erneuter Login mit neuem Passwort klappt |
| P17 | P3 | Benutzereinstellungen (E-Mail/Benachrichtigung) | Umschalten persistent |
| P18 | P3 | Nicht-Admin sieht keine Admin-Views | Admin-Views nicht vorhanden; direkter URL-Zugriff auf Admin-Route abgewiesen |
| P19 | P3 | „Passwort vergessen?"-Dialog | Dialog öffnet; Fehlerfall (unbekannte Email, kein SMTP) bleibt offen mit Fehlermeldung, kein Absturz |
| P20 | P4 | Dashboard-Gerätestatus „Frei/Besetzt" aus laufender Execution | Status entspricht DB-Zustand |
| P21 | P4 | Log-Viewer / Client-Neustart (Wartungsverbindung) | **Cross-Component**: Server holt Log, sendet Neustart-Befehl an laufenden Client |
| P22 | P4 | Client-Status/Uptime über Wartungsverbindung | **Cross-Component**: Interface-Status, Startzeit, laufende Executions |
| P23 | P2 | Guthaben-Aufladung lehnt nicht-positiven Betrag ab (#22) | Betrag ≤ 0 abgewiesen · `admin-crud.spec.ts` (~Z. 122) |
| P24 | P2 | Auszahlung blockiert bei Guthaben-Überschreitung (#50) | Auszahlung > Guthaben abgewiesen · `admin-crud.spec.ts` (~Z. 147) |
| P25 | P2 | Benutzer löschen (#50) | Benutzer verschwindet aus Liste · `admin-crud.spec.ts` (~Z. 194) |
| P26 | P3 | Öffentlicher Reset-Link lehnt ungültigen Key ab (#50) | Reset-Seite rendert, ungültiger Key abgewiesen · `user-portal.spec.ts` (~Z. 57) |

P1–P20 und P23–P26 laufen in der Playwright-Suite `backend/e2e/tests/` (P15/P18 teilen sich ein
`test()`). P14 nutzt einen eigenen „Standorte"-Menüpunkt statt des früheren Dashboard-Dialogs,
P16 prüft (wegen Argon2id) nur den erneuten Login mit neuem Passwort – fachliche Verortung:
docs/kb/05-migration-plan.md „Entscheidungen".

**Cross-Component (P21/P22)**: realisiert über `TerminalMaintenanceRealClientE2ETest`
(`backend`-Modul): der Backend-Spring-Kontext hält eine echte `TerminalMaintenanceService`-Bean
als „Portal", ein echter, gepackter Client-Raspi-Jar läuft als Subprozess als „Terminal";
Status/Log/Restart-Roundtrips gehen über den echten, ausgehenden Terminal-WebSocket-Kanal. Runner
`Client-Raspi/run-cross-component-e2e.sh` (siehe 06-ui-tests.md).

**Hinweise/Feasibility**
- Selektor-Strategie für Vaadin Flow (`getByLabel`, `vaadin-grid`-Light-DOM-Fallstrick,
  `[current]`-Navigation): 06-ui-tests.md „Vaadin-Flow-Selektoren".
- Datenisolation: frische DB je Lauf; Buchungen (`credit_accounting`) sind unveränderlich → über
  frische Benutzer je Testlauf arbeiten.
- P20: „Frei/Besetzt" wird direkt aus dem laufenden-Execution-Zustand gerendert (DB-getrieben,
  kein laufender Client nötig).

---

## C) Querschnitt / Infrastruktur

- **Seed-/Reset-Helfer** je Frontend (Client: Setup über die echten Backend-Repositories; Portal:
  Flyway-Baseline + `global-setup.ts`) – zentrale Fixtures für unabhängige, wiederholbare Tests.
- **CI-Anbindung**: Client-TestFX unter Xvfb + Portal-Playwright in PR-CI mit PostgreSQL
  (`.github/workflows/ci.yml`).
- **Reihenfolge-Unabhängigkeit**: stabile IDs, Registrierungs-Reset und frische Datenbanken für
  alle datenverändernden Tests.

## Umfang (Inventar)

Am Code gezählt:
- **Client** (TestFX/JUnit): **71 `@Test`** in 28 Testklassen (40 Testdateien) – C1–C16, die
  C15-Offline-Nachfolger sowie infrastrukturfreie Unit-Tests (Offline-Replay, Uhren-Plausibilität,
  API-Fehlerbilder, Kartenmaskierung).
- **Portal-E2E** (Playwright): **23 `test()`** in `backend/e2e/tests/` (login 2 / admin 3 /
  admin-crud 12 / dashboard 1 / user-portal 5) zzgl. **4** READ-ONLY-Smoke-`test()` in
  `tests-smoke/`.

Backend-JUnit-Zahlen (nur als Querverweis): 06-ui-tests.md „Test-Inventar".

## Historie

- **2026-07-23** — Umfang code-verifiziert (Client 71 `@Test`, Portal-E2E 23 `test()` + 4 Smoke);
  Pre-Launch AP1–AP6 bauten die Suiten aus (u. a. #22/#50-Portal-Fälle P23–P26)
  ([Worklog AP5](../worklog/2026-07-23-ap5-portal-performance-crud.md) ·
  [06-ui-tests.md](06-ui-tests.md)).
- **2026-07-21** — Phase 4 AP5: Cross-Component P21/P22 vom Alt-TCP-Protokoll
  (`ClientMaintenanceConnectionE2ETest`) auf den WS-Kanal (`TerminalMaintenanceRealClientE2ETest`,
  `backend`-Modul) umgestellt
  ([Worklog Phase 4](../worklog/2026-07-21-phase-4-terminal-modernisierung.md) ·
  [Änderungslog](05-migration-plan.md)).
- **2026-07-21** — Phase 3 AP6: Portal-Testplan P1–P20 (inkl. P11) gegen das Vaadin-Flow-Portal
  umgesetzt; die Alt-Portal-Suite (`Portal/e2e`, Vaadin 7) wurde abgelöst
  ([Worklog Phase 3](../worklog/2026-07-20-phase-3-portal-neubau.md)).
- **2026-07-20** — Erstumsetzung des Plans grün: Client C1–C16, Portal P1–P20
  ([Worklog Phase 0/1](../worklog/2026-07-20-phase-0-und-1-fundament.md) ·
  [Phase 3](../worklog/2026-07-20-phase-3-portal-neubau.md)).
