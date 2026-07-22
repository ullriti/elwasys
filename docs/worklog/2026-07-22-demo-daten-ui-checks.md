# 2026-07-22 — Demo-Daten fürs visuelle UI-Prüfen

**Ziel:** Einen wiederverwendbaren Demo-Datenbestand anlegen, damit UI-Änderungen (Admin-/
Benutzer-Portal, Dashboard) visuell geprüft werden können, ohne die Daten jedes Mal von Hand
zu erstellen.

## Erledigt
- **`DemoDataSeeder`** (`backend/src/main/java/.../demo/DemoDataSeeder.java`,
  `@Profile("demo")`, `ApplicationRunner`): seedet beim Start einen zusammenhängenden
  Beispielbestand – 4 Benutzergruppen (Rabattarten `NONE`/`FIX`/`FACTOR`), 3 Standorte,
  5 Programme (`FIXED`/`DYNAMIC`), 6 Geräte (fhem/deCONZ, eines deaktiviert), 5 Benutzer
  (inkl. gesperrtem Gast) mit Guthaben, abgeschlossener Ausführungshistorie und laufenden
  Ausführungen (Dashboard „Besetzt" inkl. Restzeit). Nutzt die echten Repositories/Services
  (Argon2id via `PasswordVerificationService`, `CreditService`, `PricingService`) → konsistente
  Guthaben/Preise. Idempotent über Marker-Benutzer `anna`.
- **`application-demo.yml`**: Profil `demo` fährt Backend + Portal normal hoch (anders als die
  reinen CLI-Profile) und aktiviert den Seeder.
- **`backend/run-demo.sh`**: startet PostgreSQL + Demo-DB `elwasys_demo` + Backend (Profil `demo`,
  `-Pproduction`) auf :8080; `RESET_DEMO_DB=1` für frischen Bestand.
- **`DemoDataSeederTest`** (extends `AbstractBackendIT`, `@ActiveProfiles("demo")`, 5 Tests):
  Marker-Benutzer/Gruppe/Guthaben, gesperrter Gast, deaktiviertes Gerät, „besetztes"
  Dashboard-Gerät, Idempotenz eines zweiten Laufs.
- **Verifiziert**: `backend/run-backend-tests.sh` **205/205** grün (inkl. der 5 neuen). Backend
  im Profil `demo` hochgefahren, per Playwright/Chromium als admin eingeloggt und
  Dashboard/Benutzer/Geräte/Programme gerendert – „Besetzt"/„Frei", rabatt-korrekte Guthaben,
  „Gesperrt"/„Deaktiviert" sichtbar.
- **Doku**: docs/kb/06-ui-tests.md („Demo-Daten"), docs/kb/04-build-and-run.md („Demo-Modus"),
  „Aktueller Stand" in docs/kb/README.md, CHANGELOG `[Unreleased]`.

## Entscheidungen
- **Seeder statt SQL-Fixture / Flyway-Migration**: Demo-Daten gehören nicht ins Produktivschema
  (jede echte Installation würde sonst mit Beispielnutzern verschmutzt). Ein profilgebundener
  `ApplicationRunner` (Muster wie `AdminPasswordCliRunner`/`TerminalTokenCliRunner`) durchläuft
  dieselben Wege wie produktive Daten und hält Guthaben/Preise/Passwörter konsistent.
- **Idempotent statt „immer frisch"**: ein Neustart gegen dieselbe DB verdoppelt nichts; für
  einen frischen Bestand `RESET_DEMO_DB=1`. So bleibt der Bestand über Neustarts stabil, ohne
  ihn neu anlegen zu müssen (Kern des Auftrags).

## Offen / nächster Schritt
- Optional: analoge Demo-Abdeckung für die JavaFX-Terminal-UI (aktuell deckt der Seeder den
  Portal-/Dashboard-Pfad ab, den das Terminal über die REST-API ohnehin mitnutzt).

## Referenzen
- docs/kb/06-ui-tests.md („Demo-Daten fürs visuelle UI-Prüfen")
- docs/kb/04-build-and-run.md („Demo-Modus")
- `backend/src/main/java/.../demo/DemoDataSeeder.java`, `backend/run-demo.sh`,
  `backend/src/main/resources/application-demo.yml`
