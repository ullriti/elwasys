# 2026-07-22 — Phase-5-Nachträge: common-Modul aufgelöst, Alt-Schema konsolidiert

**Ziel:** Zwei nach der Phase-5-Aufräumphase aufgekommene Nachfragen des Auftraggebers
abarbeiten: prüfen, ob das `common`-Modul noch nötig ist, und die doppelt vorgehaltene
Alt-Schema-Repräsentation auf eine einzige Quelle konsolidieren.

## Erledigt
- **`common`-Modul aufgelöst (Root-Reactor 3 → 2 Module)**: Analyse ergab, dass der
  Backend-Produktivcode **0** Imports auf `common` hatte (nur 3 Auth-Parity-Tests,
  test-scope); nur der Terminal-Client nutzt die 6 Utility-Klassen zur Laufzeit. Die ~20
  gleichnamigen DTO-/WS-Klassen in beiden Modulen sind der bewusst duplizierte REST-/WS-
  Wire-Contract (dürfen NICHT geteilt werden, sonst Wiederkopplung Terminal↔Backend).
  Umgesetzt: die 6 Klassen (Package `org.kabieror.elwasys.common` unverändert) nach
  `Client-Raspi/src/main/` verschoben; `commons-lang3` als direkte + PostgreSQL-JDBC-Treiber
  als test-scope Client-Raspi-Dependency ergänzt; geteilte Fixture `database-init.sql`(+
  `database-upgrade/`) → neutrales Top-Level `database/`; tote `ISO_7010_*.svg`-Alt-Portal-
  Reste gelöscht; Backend-Parity-Tests nutzen den neuen Helfer `LegacySha1` statt der
  common-Dependency. ~15 Skripte/CI/Hook/cloud-init auf `mvn -N install` umgestellt,
  separater CI-Job „Common" entfernt. Flyway V1–V10 unangetastet (Checksummen). Backend
  200/200, Client-UI 53/53, Client-E2E 29/29, `verify-cutover-migration.sh` 22/22 PASS.
- **Alt-Schema auf eine einzige Quelle konsolidiert**: geklärt, dass die Fixture
  `database-init.sql` faktisch ein byte-äquivalentes Duplikat der Flyway-Baseline
  `V1__baseline_schema_0_4_0.sql` war (per `pg_dump` bewiesen; einzige Differenz = zufalls-
  generierte `authkey.prefix`/`auth_key`-Werte, die V10 ohnehin droppt). Zwei Kopien = latentes
  Drift-Risiko für `baseline-on-migrate`. `V1` ist jetzt die EINZIGE Quelle; die 5 Test-/
  Cutover-/CI-/cloud-init-Seed-Stellen spielen `V1` direkt per psql ein (DB vorher anlegen,
  da V1 keine `CREATE DATABASE`-Präambel hat). Das Verzeichnis `database/` und das obsolete
  `backend/verify-schema-baseline.sh` (Phase-2-Relikt, in keiner CI) entfernt. Verhalten
  unverändert; Backend 200/200, Client-E2E 29/29, `verify-cutover-migration.sh` 22/22 PASS.

## Entscheidungen
- Der REST-/WS-Wire-Contract wird bewusst zwischen Terminal und Backend dupliziert gehalten
  (keine geteilte Klasse), um eine erneute Kopplung zu vermeiden.
- Flyway-Baseline `V1` ist die alleinige Quelle des Alt-/Basis-Schemas; die parallele
  `database-init.sql`-Fixture entfällt.

## Offen / nächster Schritt
- Phase 6 (Produktivumschaltung) läuft parallel/anschließend; formale QA-Review Phase 5 offen.

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog + Entscheidungen), docs/kb/03-modules.md,
  docs/kb/04-build-and-run.md
