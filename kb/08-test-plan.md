# 08 – Testplan: Vertiefung der Frontend-Tests

Vorschlag für den weiteren Ausbau der UI-/E2E-Tests beider Frontends. Aufbauend auf der
bestehenden Infrastruktur (siehe [06-ui-tests.md](06-ui-tests.md)):
- **Client**: TestFX + Xvfb, echte App über `Main`, `FhemSimulator` als Gateway, Test-DB.
- **Portal**: Playwright (Node/TS), echter Jetty/Vaadin, Test-DB.

Legende Priorität: **P1** = Kern-Happy-Path zuerst, **P2** = wichtige Varianten/Guards,
**P3** = Lifecycle/Robustheit, **P4** = fortgeschritten/abhängig.

---

## A) Client (JavaFX) – E2E-Vorschläge

**Benötigte Test-Fixtures** (wiederverwendbarer JDBC-Seed-Helper, als `postgres` bzw.
`elwaportal`, Reset zwischen Tests):
- Benutzergruppen (inkl. eine, die am Standort *nicht* erlaubt ist)
- Benutzer mit `card_ids` (RFID = Ziffern), Guthaben, blocked-Flag
- Geräte am Standort „Default", `fhem_switch_name` = `wm1sw…wm4sw` (passend zum Simulator),
  enabled/disabled, gültige Gruppen
- Programme: FIXED und DYNAMIC (flagfall, rate, time_unit), verknüpft mit Gerät
- ggf. laufende `executions` (für Resume-Test)

| ID | Prio | Testfall | Kern-Assertion |
|----|------|----------|----------------|
| C1 | P1 | Startup → Geräteauswahl ✅ | State `SELECT_DEVICE` |
| C2 | P1 | Geräteliste rendert geseedetes Gerät des Standorts ✅ | Gerätename sichtbar |
| C3 | P1 | Karten-Login gültiger Benutzer (Ziffern + Enter tippen) ✅ | `registeredUser` gesetzt (Name passt) |
| C4 | P1 | Gerät wählen → Programmliste des Geräts ✅ | State `CONFIRMATION`, Programm sichtbar |
| C5 | P1 | FIXED-Programm bestätigen (Start) ✅ | laufende Execution in DB (fhem geschaltet) |
| C6 | P2 | Unbekannte Karte ✅ | `#userInfo` Style `card-unknown`, kein Login |
| C7 | P2 | Gesperrter Benutzer ✅ | `#userInfo` Style `user-blocked`, kein Login |
| C8 | P2 | Benutzergruppe am Standort nicht erlaubt ✅ | `#userInfo` Style `location-disallowed`, kein Login |
| C9 | P2 | Zu wenig Guthaben ✅ | `#confirmationPane` Style `credit-insufficient`, Start-Button disabled |
| C10 | P2 | Auto-Logout nach `sessionTimeout` Inaktivität ✅ | `registeredUser` wird null |
| C11 | P3 | Auto-Ende: Gerät zieht keine Leistung → Ende nach `auto_end_wait_time` ✅ | Execution automatisch beendet |
| C12 | P3 | Laufende Execution abbrechen (Bestätigung) ✅ | Execution gestoppt (keine laufende Execution mehr) |
| C13 | P3 | Unterbrochene Execution beim Start fortsetzen (geseedet) | Execution wird als laufend übernommen |
| C14 | P3 | DYNAMIC-Programm: Preisanzeige (Grundgebühr/Zeitpreis/Höchstpreis) | korrekte Formatierung/Werte |
| C15 | P3 | DB beim Start nicht erreichbar → Fehlerzustand + Retry | State `ERROR`/`ERROR_RETRYABLE`, Recovery nach Retry |
| C16 | P2 | Deaktiviertes / standortfremdes Gerät | erscheint **nicht** in der Liste |

**Hinweise/Feasibility**
- Karten-Scan = Tastatureingabe (RFID-Leser emuliert Tastatur) → per TestFX
  `robot.type()`/`write()` simulierbar.
- C11–C13 brauchen etwas Timing/Interaktion mit dem Simulator (Schalt-/Power-Events).
- C15 durch temporär falsche DB-Daten in der Config oder gestoppten PG.
- Display-/Backlight-Verhalten schwer zuverlässig zu assern → bewusst ausgelassen.
- **Enabler (optional):** die harte `ElwaManager`-Kopplung entkoppeln (DI), um Teile ohne
  vollständigen App-Start feiner zu testen (Phase 1).

---

## B) Portal (Vaadin 7) – E2E-Vorschläge

**Benötigte Fixtures**: Seed/Reset über SQL (in Playwright `globalSetup` bzw. pro Test),
z. B. zusätzlicher Nicht-Admin-Benutzer mit Passwort, Gruppen, Geräte, Programme, Guthaben.

| ID | Prio | Testfall | Kern-Assertion |
|----|------|----------|----------------|
| P1 | P1 | Login-Seite rendert ✅ | Titel/Felder/Button |
| P2 | P1 | Admin-Login → Dashboard ✅ | Admin-Menü sichtbar |
| P3 | P1 | Login mit falschem Passwort ✅ | `.v-Notification` „Login fehlgeschlagen", bleibt auf Login |
| P4 | P1 | Logout ✅ | zurück auf Login-Seite |
| P5 | P1 | Navigation: alle Admin-Views (Dashboard/Benutzer/Gruppen/Programme/Geräte) ✅ | URL-Fragment `#!<view>` je Sektion |
| P6 | P2 | Benutzer anlegen (Name, Username, Gruppe) ✅ | erscheint in Benutzerliste |
| P7 | P2 | Benutzer bearbeiten: sperren ✅ | „Gesperrt" persistent (nach erneutem Öffnen gesetzt) |
| P8 | P2 | Guthaben aufladen (UserCreditWindow) ✅ | Guthaben in der Benutzerliste aktualisiert |
| P9 | P2 | Benutzergruppe anlegen ✅ | erscheint in Gruppenliste |
| P10 | P2 | Gerät anlegen (Name, Position, Standort, fhem-Namen) ✅ | erscheint in Geräteliste |
| P11 | P2 | Gerät aktiv/inaktiv schalten, bearbeiten | Zustand geändert |
| P12 | P2 | Programm anlegen (FIXED/statisch: Preis + Dauern) ✅ | erscheint in Programmliste |
| P13 | P2 | Entität löschen (z. B. Programm/Gerät) | verschwindet aus Liste |
| P14 | P3 | Standort-Verwaltung (LocationWindow) | anlegen/bearbeiten |
| P15 | P3 | Nicht-Admin-Login → Benutzer-Dashboard | Guthaben + Buchungstabelle sichtbar |
| P16 | P3 | Eigenes Passwort ändern | erneuter Login mit neuem Passwort klappt |
| P17 | P3 | Benutzereinstellungen (E-Mail/Push-Benachrichtigung) | Umschalten persistent |
| P18 | P3 | Nicht-Admin sieht keine Admin-Views (Berechtigung) | Admin-Menü/Views nicht erreichbar |
| P19 | P3 | „Passwort vergessen?"-Dialog | Dialog öffnet (kein echter Mailversand) |
| P20 | P4 | Dashboard-Gerätestatus „Frei/Besetzt" aus laufender Execution | Status entspricht DB-Zustand |
| P21 | P4 | Log-Viewer / Client-Neustart (Wartungsverbindung) | **Cross-Component** (Portal + laufender Client) – optional |

**Hinweise/Feasibility**
- Vaadin 7 vergibt keine stabilen IDs → Lokatoren über Captions/CSS-Klassen. **Empfehlung
  (optional, kleiner Prod-Eingriff):** Schlüssel-Komponenten mit `setId()`/DebugId
  versehen, um E2E robuster & lesbarer zu machen.
- P20/P21 hängen an der Wartungs-Verbindung (Portal ⇄ Client). P21 wäre ein echter
  Cross-Component-E2E (Portal + Client gemeinsam hochfahren) – als eigener, späterer
  Meilenstein sinnvoll.
- Datenisolation: relevante Tabellen pro Test zurücksetzen; Buchungen (`credit_accounting`)
  sind unveränderlich → über frische Benutzer je Testlauf arbeiten.

---

## C) Querschnitt / Infrastruktur

- **Seed-/Reset-Helfer** je Frontend (Client: JDBC in Test-Setup; Portal: SQL in
  Playwright-Setup) – zentrale Fixtures, damit Tests unabhängig & wiederholbar sind.
- **CI-Anbindung** (Phase 1): Client-TestFX unter Xvfb + Portal-Playwright in PR-CI mit
  PostgreSQL-Service.
- **Reihenfolge-Unabhängigkeit**: wie beim Client-E2E (stabile IDs, Registrierungs-Reset)
  konsequent für alle datenverändernden Tests.

## Empfohlene Reihenfolge
1. **Client P1** (C2–C5) – der Kern-Nutzungsablauf am Terminal.
2. **Portal P1** (P3–P5) – Auth/Navigation absichern (schnell, hoher Wert).
3. **Portal P2** (P6–P13) – Admin-CRUD.
4. **Client P2/P3** (C6–C12) – Login-Varianten & Execution-Lifecycle.
5. **Portal P3** (P15–P19) – Benutzer-Frontend & Berechtigungen.
6. **P4/Cross-Component** – Wartungs-abhängige Flows als eigener Meilenstein.
