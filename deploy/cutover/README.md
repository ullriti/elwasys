# Cutover-Runbook (Phase 6 AP1)

Werkzeuge für die **Produktivumschaltung** (kb/05-migration-plan.md, "Phase 6 –
Produktivumschaltung (Cutover)"): das bestehende, über den Alt-Weg
(Schema-Endstand 0.4.0, entspricht der Flyway-V1-Baseline) angelegte Produktiv-Setup
(physische Raspi-Terminals + laufendes Alt-Portal/DB) auf die neue Architektur (Backend mit
Flyway-verwaltetem Schema, Terminals über REST-API/Standort-Token statt Direkt-DB-Zugriff)
umstellen - ohne Datenverlust.

**Scope dieses Arbeitspakets**: Skripte + dieses Runbook, lokal gegen eine Testkopie des
Bestandsschemas verifiziert (siehe `verify-cutover-migration.sh` für den Hinweg,
`verify-rollback.sh` für den Rückweg). Das eigentliche Umstellen echter Hardware/der echten
Produktiv-DB ist NICHT Teil dieses Arbeitspakets (siehe kb/05-migration-plan.md,
Phase-6-Roadmap: "Terminals neu aufsetzen" ist ein eigener, späterer Schritt).

## Vorher: Backup!

**Vor JEDEM Schritt gegen die echte Produktiv-DB ein vollständiges Datenbank-Backup ziehen**
(z. B. `pg_dump`/`pg_basebackup` oder der Snapshot-Mechanismus der Betriebsumgebung). Keines
der Skripte hier ersetzt ein Backup - `01`/`04` sind zwar rein lesend, aber der eigentliche
Cutover-Schritt (Backend gegen die Bestands-DB starten, siehe unten) führt echte
Flyway-Migrationen (DDL + eine Datenänderung, siehe V7) gegen die Produktiv-DB aus.

## Voraussetzungen

- Backend-Jar gebaut (`mvn -N install -DskipTests` dann
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
Baut lokal eine Testkopie des Bestandsschemas (die Flyway-V1-Baseline direkt per `psql`
eingespielt, DB-Name per `CUTOVER_VERIFY_DB`, Default `elwasys_cutover_verify`), füllt sie
mit realistischen Bestandsdaten, startet das Backend-Jar dagegen (Port per
`CUTOVER_VERIFY_PORT`, Default 18090) und prüft per `psql`-Asserts: Flyway-Historie
(BASELINE@1 + V2..V10 alle `success=true`), dass die Bestandsdaten unverändert erhalten
bleiben, und dass die Schema-Härtung (Typo-Fix, `client_*`-Spalten weg, App-Reste weg,
Alt-Rollen-Grants weg, admin-Passwort NULL) tatsächlich wirkt. Das Skript prüft explizite,
wartbare Assert-Aussagen und ist das maßgebliche Cutover-Verifikationswerkzeug.

Die Test-DB `elwasys_cutover_verify` wird am Ende **nicht** gedroppt - sie ist die Grundlage
für `verify-rollback.sh` (Phase 6 AP2, siehe unten).

## Rollback

Für einen **abgebrochenen Cutover** - der Betreiber will wieder auf das alte Feld-System
(Alt-Portal-WAR + Alt-Client mit JDBC-Direktzugriff) zurück - gibt es zwei Rückwege. **Immer
zuerst Option A prüfen**, Option B ist die bewusste Alternative, wenn im Cutover-Fenster
bereits entstandene Neu-System-Daten erhalten bleiben sollen.

### A) Primär/sicherste Option: Restore aus dem Backup

Das **vor Schritt 2** gezogene vollständige DB-Backup (siehe "Vorher: Backup!" oben)
zurückspielen. Das ist der sicherste Weg - die DB ist danach exakt in dem Zustand, in dem sie
unmittelbar vor dem Cutover war. Nachteil: **jede** zwischen Backup und Restore entstandene
Änderung geht verloren, auch fachliche Buchungen/neue Nutzer, die während des Cutover-Fensters
über das (bereits umgestellte) neue System gemacht wurden - nicht nur die Cutover-Migration
selbst.

### B) Sekundär: `rollback-cutover.sh` (Reverse-DDL)

Wenn im Cutover-Fenster bereits entstandene Neu-System-Daten (z. B. neu ausgestellte
Standort-Tokens, ein bereits im neuen Portal geändertes Nutzer-Passwort, neue
Ausführungen/Buchungen) erhalten bleiben sollen, statt sie durch einen Backup-Restore zu
verlieren: `deploy/cutover/rollback-cutover.sh` macht die additiven Flyway-Migrationen
V3..V10 (siehe `backend/src/main/resources/db/migration/`) idempotent rückgängig, ohne
Geschäftsdaten (`users`, `user_groups`, `locations`, `devices`, `programs`,
`device_program_rel`, `executions`, `credit_accounting`, die `*_valid_user_groups`) zu
verlieren. `V2` (Verbreiterung von `users.password`) wird bewusst NICHT umgekehrt (siehe
Kommentar in `rollback-cutover.sql`) - Verengen würde einen evtl. im Neu-System gesetzten
Argon2-Hash abschneiden, der Alt-Code stört sich an der breiteren Spalte nicht.

```bash
# ACHTUNG: braucht CREATEROLE-Rechte bzw. eine Superuser-Verbindung (die Umkehrung von V6
# legt Rollen an) - anders als 01/02/03 reicht der normale Anwendungs-User "elwaportal" NICHT.
ELWASYS_DB_URL=jdbc:postgresql://<host>:5432/elwasys \
ELWASYS_DB_USER=postgres ELWASYS_DB_PASSWORD=<Superuser-Passwort> \
deploy/cutover/rollback-cutover.sh
```

Details zu jeder einzelnen Umkehrung (mit Verweis auf die jeweilige `V*`-Migration) stehen als
Kommentare direkt in `deploy/cutover/rollback-cutover.sql`. Idempotent - mehrfaches Ausführen
gegen dieselbe DB ist sicher (siehe `verify-rollback.sh`, Abschnitt "Idempotenz").

**Caveats (unbedingt vor dem Einsatz gegen eine echte Produktiv-DB lesen):**

- **Alt-Rollen-Default-Passwörter kommen zurück**: die Umkehrung von V6 legt `elwaclient1`
  (Passwort `elwaclient1`) und `elwaapi` (Passwort `api1234`) mit ihren historischen
  Klartext-Default-Passwörtern wieder an, falls sie nicht mehr existieren. **Nach dem
  Rollback zeitnah rotieren** (`ALTER USER ... WITH PASSWORD '<neu>'`).
- **Admin-Passwort nur im V7-genullten Fall wiederhergestellt**: die Umkehrung von V7 setzt
  das Alt-Default-Passwort (SHA1-Hash von `admin`) NUR dort wieder, wo `password IS NULL` ist
  (also nur bei einer Installation, bei der niemand seit dem Cutover ein Passwort gesetzt
  hat). Ein bereits gesetztes Passwort (Alt- ODER Neu-Portal) bleibt unangetastet.
- **Ein im Neu-Portal gesetztes Argon2-Passwort versteht das Alt-Portal nicht**: der
  Rollback ändert an einem bereits im neuen Portal geänderten Argon2id-Hash nichts (siehe
  oben) - das Alt-Portal kann diesen Hash aber nicht prüfen (kennt nur SHA1). Für einen
  betroffenen Nutzer ist nach dem Rollback ein manueller Passwort-Reset nötig.
- **App-Spalten kommen leer zurück**: `users.app_id`/`access_key`/`auth_key`,
  `locations.client_uid`/`client_ip`/`client_port`/`client_last_seen` werden mit `NULL`
  wieder angelegt - ihr letzter Inhalt vor dem Cutover ist nicht rekonstruierbar (V9/V10
  haben die Spalten ersatzlos gedroppt). Der Alt-Client registriert sich bei der nächsten
  Verbindung ohnehin neu; ein `auth_key` wird beim nächsten `INSERT` auf `users` automatisch
  neu vergeben (Trigger).

### Lokale Verifikation des Rollback-Pfads

```bash
deploy/cutover/verify-rollback.sh
```

Stellt sicher, dass eine migrierte Bestands-DB vorliegt (per Default die von
`verify-cutover-migration.sh` hinterlassene `elwasys_cutover_verify`, wird bei Bedarf
automatisch hergestellt), führt `rollback-cutover.sh` aus, prüft per `psql`-Asserts sowohl
das wiederhergestellte Alt-Schema als auch den unveränderten Erhalt der Geschäftsdaten, führt
`rollback-cutover.sh` ein **zweites Mal** aus (Idempotenz-Beweis: keine Fehler, Zustand
unverändert) und startet danach das Backend-Jar erneut gegen die zurückgebaute DB (**Re-
Cutover-Beweis**: Flyway baselined erneut auf V1 und wendet V2..V10 erneut erfolgreich an,
Backend kommt wieder `/actuator/health` UP) - beweist, dass die DB nach einem Rollback wieder
sauber cutover-fähig ist, kein kaputter Zwischenzustand.
