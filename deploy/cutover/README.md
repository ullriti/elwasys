# Cutover-Runbook (Phase 6 AP1)

Werkzeuge für die **Produktivumschaltung** (kb/05-migration-plan.md, "Phase 6 –
Produktivumschaltung (Cutover)"): das bestehende, über den Alt-Weg
(`Common/resources/database-init.sql`, Schema-Endstand 0.4.0) angelegte Produktiv-Setup
(physische Raspi-Terminals + laufendes Alt-Portal/DB) auf die neue Architektur (Backend mit
Flyway-verwaltetem Schema, Terminals über REST-API/Standort-Token statt Direkt-DB-Zugriff)
umstellen - ohne Datenverlust.

**Scope dieses Arbeitspakets**: Skripte + dieses Runbook, lokal gegen eine Testkopie des
Bestandsschemas verifiziert (siehe `verify-cutover-migration.sh`). Das eigentliche Umstellen
echter Hardware/der echten Produktiv-DB ist NICHT Teil dieses Arbeitspakets (siehe
kb/05-migration-plan.md, Phase-6-Roadmap: "Terminals neu aufsetzen" ist ein eigener,
späterer Schritt). Ein Rollback-/Rückbau-Skript für einen abgebrochenen Cutover ist ebenfalls
NICHT Teil von AP1 - siehe "Rollback" unten.

## Vorher: Backup!

**Vor JEDEM Schritt gegen die echte Produktiv-DB ein vollständiges Datenbank-Backup ziehen**
(z. B. `pg_dump`/`pg_basebackup` oder der Snapshot-Mechanismus der Betriebsumgebung). Keines
der Skripte hier ersetzt ein Backup - `01`/`04` sind zwar rein lesend, aber der eigentliche
Cutover-Schritt (Backend gegen die Bestands-DB starten, siehe unten) führt echte
Flyway-Migrationen (DDL + eine Datenänderung, siehe V7) gegen die Produktiv-DB aus.

## Voraussetzungen

- Backend-Jar gebaut (`mvn -f pom.xml install -pl Common -am -DskipTests` dann
  `mvn -f pom.xml package -pl backend -DskipTests`, siehe kb/04-build-and-run.md) bzw. das
  Container-Image (`backend/Dockerfile`), falls per Docker/Kubernetes betrieben.
  Für einen langlebigen Produktivbetrieb **`-Pproduction`** verwenden (siehe
  kb/04-build-and-run.md, "Wichtiger Fund Phase 4 AP4" - ohne Produktionsmodus bricht ein
  länger laufender Prozess in dieser Sandbox nach ca. 60s ab; für eine ECHTE
  Produktivumgebung mit gültiger Vaadin-Lizenz/-Zugang gilt dieselbe Empfehlung ohnehin
  unabhängig von der Sandbox-Einschränkung).
- Netzwerkzugriff vom Ausführungsort dieser Skripte auf die Ziel-DB.
- `psql` (Client) verfügbar, JDK 21 + `java` verfügbar für die CLI-Wrapper (02/03).

## Ablauf

### 1. Preflight-Check (rein lesend)

```bash
ELWASYS_DB_URL=jdbc:postgresql://<host>:5432/elwasys \
ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<Passwort> \
deploy/cutover/01-preflight-check.sh
```
Liefert einen Readiness-Report: Flyway-Status, noch vorhandene Alt-Artefakte
(`client_*`-Spalten, App-Reste, Alt-Rollen - alle werden automatisch von V6/V8/V9/V10
bereinigt, sobald das neue Backend das erste Mal gegen diese DB läuft), Dateninventar
(Nutzer/Standorte inkl. Geräte- und Token-Anzahl je Standort/Geräte/Programme/
Ausführungen/Abrechnungsposten) und Warnungen (z. B. Standorte ohne aktives Token, admin
ohne Passwort). **Verändert nichts** - beliebig oft wiederholbar.

### 2. Backend gegen die Bestands-DB starten (Flyway migriert automatisch)

Kein eigenes Skript nötig - der normale Backend-Start erledigt das (siehe
kb/04-build-and-run.md "Lokal starten" bzw. "Deployment (Produktion)" für Docker-
Compose/Helm):
```bash
ELWASYS_DB_URL=jdbc:postgresql://<host>:5432/elwasys \
ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<Passwort> \
java -jar backend/target/elwasys-backend.jar
```
Flyway erkennt die fehlende `flyway_schema_history`-Tabelle, baselined die DB auf Version 1
(== der eingefrorene 0.4.0-Alt-Weg-Stand, KEIN erneutes Ausführen von V1) und wendet danach
V2..V10 automatisch an (`baseline-on-migrate`, siehe `application.yml`,
kb/02-data-model.md). Das ist genau der Pfad, den `verify-cutover-migration.sh` (siehe
unten) lokal nachstellt und verifiziert. Mit `curl http://<host>:8080/actuator/health`
bestätigen, dass der Start sauber durchlief.

### 3. Standort-Tokens ausstellen

Für jeden Standort, an dem ein Terminal auf die neue REST-API/den WS-Kanal umgestellt werden
soll (siehe `01`s Warnung "kein aktives Standort-Token"):
```bash
ELWASYS_DB_URL=jdbc:postgresql://<host>:5432/elwasys \
ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<Passwort> \
deploy/cutover/02-issue-terminal-tokens.sh --location=<Standortname> [--label=<Text>]
```
Listet zunächst alle Standorte, erzeugt danach ein neues Token für den angegebenen Standort
(dünner Wrapper um das bestehende `token-cli`-Profil, siehe kb/04-build-and-run.md). Das
Klartext-Token erscheint **genau einmal** in der Ausgabe und wird nirgends gespeichert -
sofort in die Terminal-Konfiguration (`backend.token` in `elwasys.properties`) übernehmen.

### 4. Admin-/Benutzer-Passwort setzen

Seit Phase 5 (V7) hat eine gehärtete Bestands-DB **kein bekanntes Admin-Passwort mehr**
(das alte Default-Passwort wird beim ersten Migrationslauf entfernt) - ohne diesen Schritt
ist kein Portal-Login möglich:
```bash
ELWASYS_DB_URL=jdbc:postgresql://<host>:5432/elwasys \
ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<Passwort> \
deploy/cutover/03-set-admin-password.sh [--username=admin]
```
Fragt das neue Passwort interaktiv ab (`read -s`, zweimalige Eingabe zur Bestätigung) und
setzt es im neuen Argon2id-Format (dünner Wrapper um das bestehende `admin-cli`-Profil).

### 5. Obsolete Standorte reviewen (optional, rein lesend)

```bash
psql -h <host> -p 5432 -U elwaportal -d elwasys -f deploy/cutover/04-review-obsolete-locations.sql
```
Zeigt Standorte ohne Geräte und ohne aktives Token (typischerweise ungenutzte Test-/Seed-
Standorte wie das mitgelieferte `Default`) zur manuellen Prüfung. **Löscht nichts
automatisch** - ein auskommentiertes `DELETE`-Template steht am Dateiende für eine bewusste,
manuelle Aufräumaktion (nach Backup!). Die spaltenweise Alt-Registrierungs-Bereinigung
(`locations.client_*`) erledigt bereits Flyway V9 automatisch - hier geht es nur um ganze,
ungenutzte Standort-Zeilen.

## Lokale Verifikation (kein Zugriff auf echte Hardware/Produktiv-DB nötig)

```bash
deploy/cutover/verify-cutover-migration.sh
```
Baut lokal eine Testkopie des Bestandsschemas (`Common/resources/database-init.sql`, DB-Name
per `CUTOVER_VERIFY_DB`, Default `elwasys_cutover_verify`), füllt sie mit realistischen
Bestandsdaten, startet das Backend-Jar dagegen (Port per `CUTOVER_VERIFY_PORT`, Default
18090) und prüft per `psql`-Asserts: Flyway-Historie (BASELINE@1 + V2..V10 alle
`success=true`), dass die Bestandsdaten unverändert erhalten bleiben, und dass die
Schema-Härtung (Typo-Fix, `client_*`-Spalten weg, App-Reste weg, Alt-Rollen-Grants weg,
admin-Passwort NULL) tatsächlich wirkt. Anders als das historische
`backend/verify-schema-baseline.sh` (Phase-2-Relikt, vergleicht Schema-Dumps 1:1 - das
funktioniert seit V2 nicht mehr, siehe dessen Header) prüft dieses Skript explizite,
wartbare Assert-Aussagen statt eine mittlerweile falsche Gleichheitsannahme.

Die Test-DB `elwasys_cutover_verify` wird am Ende **nicht** gedroppt - sie ist die Grundlage
für das kommende Rollback-Arbeitspaket (Phase 6 AP2, siehe unten).

## Rollback

**Noch nicht Teil dieses Arbeitspakets** (AP1 liefert nur die Migrationswerkzeuge). Ein
eigenes Rückbau-/Rollback-Skript für einen abgebrochenen Cutover ist laut Roadmap
(kb/05-migration-plan.md, Phase 6) als eigener Schritt vorgesehen - **folgt in AP2**. Bis
dahin gilt als Rollback-Strategie das vor Schritt 2 gezogene DB-Backup (siehe oben) plus die
Beobachtung, dass Schritt 2 (Flyway-Migration) additiv/schema-härtend, aber nicht destruktiv
gegenüber den fachlichen Daten ist (siehe `verify-cutover-migration.sh`-Asserts oben) - ein
Abbruch mitten in der Migration ist bei PostgreSQL-DDL i. d. R. transaktional pro Migration
(Flyway führt jede `V*.sql`-Datei in einer eigenen Transaktion aus), ersetzt aber kein echtes
Backup/getestetes Rollback-Verfahren.
