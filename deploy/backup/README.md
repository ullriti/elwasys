# elwasys Backup & Restore

Backup **und** der ausgearbeitete Restore-Weg (Issue #84/H5). Die finale Review (Track R5) fand
das Backup geregelt, aber den Restore nur als Satz („Restore regelmäßig proben") – ohne
Schritt-für-Schritt-Weg, Skript oder bezifferte RPO/RTO. Dieses Verzeichnis liefert beides mit.

> **Ein nie geprobter Restore ist kein Backup.** Die Restore-Probe gehört in die Generalprobe
> (Spec 0001, Punkt 3) und als wiederkehrende Betriebsaufgabe ins Runbook (Kap. 7a).

## Backup – `backup-db.sh`

Zieht ein komprimiertes Plain-SQL-Backup (die geskriptete Form des Runbook-`pg_dump`-Cron):

```bash
# Externe/Host-DB:
BACKUP_DB_USER=elwasys ./backup-db.sh --host db.internal --dbname elwasys --out-dir /var/backups/elwasys
# Compose-Container:
./backup-db.sh --docker-container elwasys-postgres --out-dir /var/backups/elwasys
# Plan ansehen, nichts tun:
./backup-db.sh --dry-run
```

Als Cron (täglich 03:15, Retention 30 Tage per `--keep-days`):
```cron
15 3 * * * root /opt/elwasys/backup/backup-db.sh --docker-container elwasys-postgres >> /var/log/elwasys-backup.log 2>&1
```

### Backup-Scope (WICHTIG – nicht nur die DB!)

`backup-db.sh` sichert **nur die Datenbank**. Separat (verschlüsselt, offsite) sichern:

- **Backend-Secrets:** `deploy/compose/.env` bzw. die Helm-Values (DB-/SMTP-Passwort,
  Pushover-Token) – ohne sie startet das wiederhergestellte Backend nicht.
- **Je Terminal:** `/opt/elwasys/elwasys.properties` (inkl. `backend.token`/Standort-Token),
  `logback.xml`, `run.sh`.
- **Bewusstes Nicht-Backup:** das **Offline-Journal auf der Terminal-SD-Karte** (noch nicht
  replayte Offline-Buchungen = Geld). Stirbt eine SD-Karte mit unrepliziertem Journal, ist
  diese Buchung verloren – als Betriebsrisiko benannt, nicht abgedeckt.
- **Offsite:** das Backup-Verzeichnis auf ein zweites Ziel spiegeln (z. B. `rclone`/`scp`) –
  Blitz/Diebstahl vernichten sonst Host **und** lokale Backups gemeinsam.

## Restore – `restore-db.sh`

Szenario „der Server ist weg": neuer Host, frisches PostgreSQL, jüngstes Offsite-Backup.

```bash
# Immer ZUERST der Trockenlauf (zeigt den Plan, ändert nichts):
sudo -u postgres ./restore-db.sh --dump /var/backups/elwasys/elwasys-2026-07-24.sql.gz --dry-run
# Dann echt:
sudo -u postgres ./restore-db.sh --dump /var/backups/elwasys/elwasys-2026-07-24.sql.gz
```

Schritte: Owner-Rolle (`elwaportal`) sicherstellen → leere Ziel-DB anlegen → Dump einspielen
(`gunzip -c … | psql`) → Stichproben-Zeilenzahlen. Danach das Backend gegen die Ziel-DB starten;
Flyway **validiert** die im Dump enthaltene Historie (kein erneutes Migrieren), `/actuator/
health/liveness` muss `UP` sein.

**RPO/RTO:** RPO = Alter des jüngsten Backups (bei täglichem Cron ~24 h). RTO = Dauer dieses
Restores – bei der Generalprobe-Probe **einmal real messen und festhalten** (hängt v. a. an der
DB-Größe und daran, wie schnell das Offsite-Backup verfügbar ist).

> **Rollen-Hinweis:** Für den reinen Restore genügt die Owner-Rolle `elwaportal`. Die gehärteten
> App-Rollen legt die Migration V6 an – wird das Backend nach dem Restore normal gestartet, ist
> das bereits Teil der (im Dump enthaltenen) Historie. Ein Dump einer **bereits migrierten** DB
> enthält `flyway_schema_history`; das Backend migriert dann nicht erneut, sondern validiert nur.

## Test

`restore-db-selftest.sh` prüft **offline** (kein PostgreSQL) die Argument-Validierung und den
`--dry-run`-Plan beider Skripte; läuft in der CI. Der **echte** Restore ist Teil der Generalprobe.

```bash
bash deploy/backup/restore-db-selftest.sh
```
