# database/ – Alt-Schema-Fixture (elwasys 0.4.0)

Dieses Verzeichnis enthält das **eingefrorene Bestandsschema** der elwasys-Datenbank in
der Version 0.4.0 – die Ausgangsbasis, von der aus das Backend seine Flyway-Baseline
(`backend/src/main/resources/db/migration/V1__baseline_schema_0_4_0.sql`) ableitet.

| Datei | Zweck |
|-------|-------|
| `database-init.sql` | Vollständiges Alt-Schema (Tabellen, Funktionen, Rollen/Grants, Seed-Daten) einer frischen 0.4.0-Installation. |
| `database-upgrade/` | Historische, manuelle Upgrade-Skripte aus der Zeit vor Flyway (0.3.1→0.3.2, →0.4.0). Nur noch als Referenz. |

## Wer nutzt das?

`database-init.sql` ist **modulübergreifende Test-/Deploy-Infrastruktur**, kein
Anwendungscode:

- **Client-Tests** (`Client-Raspi/run-ui-tests.sh`, `run-client-e2e.sh`) seeden damit die
  gemeinsame Test-DB, gegen die Terminal + Backend laufen.
- **Backend-Verifikation** (`backend/verify-schema-baseline.sh`) prüft die Flyway-Baseline
  gegen diesen Alt-Weg.
- **Cutover-Werkzeuge** (`deploy/cutover/verify-cutover-migration.sh`,
  `01-preflight-check.sh`, `rollback-cutover.sql`) simulieren damit die bestehende
  Produktiv-DB vor dem `baseline-on-migrate`-Cutover.
- **Cloud-Init / CI** seeden damit ihre PostgreSQL-Instanz.

## Historie

Bis zum Abschluss der Modernisierung lag diese Fixture unter `Common/resources/`. Nach
Auflösung des `common`-Moduls (die sechs Utility-Klassen wanderten ins Client-Raspi-Modul,
da nur der Terminal-Client sie zur Laufzeit braucht) wurde die geteilte Schema-Fixture in
dieses neutrale Top-Level-Verzeichnis verschoben – sie gehört keinem einzelnen Modul,
sondern der Test-/Deploy-Infrastruktur. Details: `kb/05-migration-plan.md`.
