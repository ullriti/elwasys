# 6. PostgreSQL bleibt, Schema-Verwaltung über Flyway

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

PostgreSQL ist als Datenbank gesetzt und enthält Bestandsdaten (Nutzer, Guthaben,
Historie), die erhalten bleiben müssen. Das Schema (0.4.0) wurde bisher über
handgepflegte SQL-Skripte (`database-init.sql`, `database-upgrade/*.sql`) und ein
manuelles Upgrade über `config.db.version` verwaltet – fehleranfällig und ohne
reproduzierbaren Migrationsstand.

## Entscheidung

Das Schema wird über **Flyway** versioniert. Die **Baseline-Migration
(`V1__baseline_schema_0_4_0.sql`)** entspricht dem Bestandsschema; gegen eine Bestands-DB
läuft Flyway mit `baselineOnMigrate` und übernimmt sie unverändert, gegen eine leere DB
legt `V1` das komplette Schema an. Alle künftigen Schemaänderungen laufen ausschließlich
über weitere Flyway-Migrationen (`V2`, `V3`, …); Änderungen bleiben additiv/
abwärtskompatibel, solange der Alt-Pfad parallel läuft (z. B. `V2` verbreitert
`users.password` auf `VARCHAR(255)` für Argon2id).

## Konsequenzen

- Reproduzierbarer, versionierter Schemastand für leere und Bestands-DBs.
- Bestandsdaten bleiben erhalten.
- Handgepflegte SQL-Skripte und das `config.db.version`-Upgrade entfallen.
- Kosmetische Altlasten (z. B. Spaltentypo `auto_end_power_threashold`) werden per
  Migration bereinigt, sobald kein Alt-Code mehr direkt liest.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Zielarchitektur und Entscheidungen.
