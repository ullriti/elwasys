# 2026-07-21 — Phase 5: Aufräumen (AP1–AP6)

**Ziel:** Nach dem erfolgreichen Cutover die Altlasten entfernen: das Alt-Portal-Modul und
den Direkt-DB-Zugriff des Clients aus dem Repo tilgen, die Datenbank-Rollen und -Passwörter
härten, obsolete Schema-Reste bereinigen, die Release-Pipeline finalisieren und die gesamte
Dokumentation auf die Zielarchitektur bringen.

## Erledigt
- **AP1 – Alt-Portal + `Common.DataManager`/Maintenance-Altprotokoll entfernt**: `Portal/`-
  Modul komplett aus dem Repo (inkl. `portal-legacy-build`-CI-Job); `Common` von 17 auf 6
  Klassen geschrumpft (`ProgramType`, `Utilities`, `FormatUtilities`, `ConfigurationManager`,
  `LocationOccupiedException`, `NoDataFoundException` bleiben) – entfernt: `DataManager`,
  Alt-Entities, `DiscountType`, `NotEnoughCreditException`, das `maintenance/`-Paket. Root-
  Reactor jetzt 3 Module. Dadurch blockierte Backend-Parity-Tests mit-entfernt. Backend
  207→197.
- **AP2 – DB härten (Rollen + Default-Passwörter)**: additive Migrationen `V6` (entfernt
  Alt-Rollen `elwaclient1`/`elwaapi` + Gruppe `elwaclients` samt Default-Passwörtern;
  `elwaportal` bleibt einziger Anwendungs-DB-User) und `V7` (leert das Seed-`admin`-Passwort,
  aber nur wenn noch der unveränderte Default-SHA1-Hash → Bestand unberührt). Neues Admin-CLI
  `AdminPasswordCliRunner` (Profil `admin-cli`) setzt via Argon2id ein Passwort – auf frischen
  Installationen der einzige Weg, dem `admin` ein Passwort zu geben. Backend 197→200,
  Playwright 19/19 (admin-Login weiter grün).
- **AP3 – Schema-Bereinigung**: `V8` (Spaltentypo `auto_end_power_threashold` →
  `auto_end_power_threshold`, im selben Commit durch Backend/DTOs/Services/UI und Client
  synchron gezogen – Wire-Contract beidseitig umbenannt) + `V9` (obsolete `locations.client_*`-
  Fernwartungsspalten, seit Phase 4 AP5 ungenutzt). Test-Seeds nachgezogen. Suiten unverändert grün.
- **AP4 – App-Reste (`elwaapi`) entfernt**: `V10` entfernt Trigger/Funktionen +
  `users`-Spalten `app_id`/`access_key`/`auth_key`, Tabellen `reservations`/`foreign_authkeys`
  und die ungenutzten Config-Schlüssel `authkey.prefix`/`reservation.duration`. Tote Auth-Key-
  Anzeige aus der medium-UI (Controller + FXML) entfernt (FXML↔Controller-Konsistenz gewahrt).
  Suiten unverändert grün.
- **AP5 – Release-Pipeline final**: **kritischer Fix** – `backend/Dockerfile` kopierte noch
  `Portal/pom.xml` (seit AP1 weg) → Image-Build wäre gescheitert; `COPY`-Liste + `.dockerignore`
  auf die drei Reactor-Module korrigiert. Release-Build um `-DskipTests` ergänzt (Release-
  Runner hat kein Xvfb/PG); `actions/checkout`/`setup-java` v3 → v4. Nur Build-/Release-Infra.
- **AP6 – Doku-Endstand**: reine Doku-/Kommentar-Bereinigung (keine Java-Logik geändert).
  Root-`README.md` + `docs/kb/00/01/03/06` (+ 04/07/08) auf den Zielarchitektur-Endstand
  gebracht (Alt-Portal/Alt-TCP-Maintenance/Terminal-Direkt-DB durchgängig als „entfernt"/
  „abgelöst" markiert). Stale Kommentare in application.yml, POMs, Skripten, Helm/Compose
  korrigiert. Zusatzfund: `CLAUDE.md` hing auf Phase-4-Stand mit kaputtem Build-Befehl
  `mvn -f Portal/pom.xml package` → korrigiert. `portalUrl` bleibt bewusst erhalten (aktiv
  auf dem Terminal-Bestätigungsbildschirm genutzt).

## Entscheidungen
- Die mobile App (`elwaapi`) ist laut Auftraggeber nicht mehr relevant (Entscheidung
  2026-07-20) → ihre DB-/UI-Reste werden vollständig entfernt (AP4).
- `elwaportal` wird der einzige Anwendungs-DB-User; Default-Admin-Passwort entfällt, das
  Setzen läuft ausschließlich über `admin-cli` (bewusste Verhaltensänderung nur für
  Neuinstallationen).

## Offen / nächster Schritt
- Formale QA-Review der Phase 5 durch den Koordinator; anschließend Phase 6
  (Produktivumschaltung). Zwei Phase-5-Nachträge vom 2026-07-22 (common-Modul aufgelöst,
  Alt-Schema konsolidiert) sind separat protokolliert.

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog), docs/kb/02-data-model.md,
  docs/kb/03-modules.md, docs/kb/04-build-and-run.md, docs/kb/06-ui-tests.md
