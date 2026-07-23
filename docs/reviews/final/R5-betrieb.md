# R5 – Betrieb: Deployment / Alerting / Backup / Monitoring

> Finale Review vor dem Feldeinsatz (Spec [0001](../../specs/0001-finale-review.md)), Track R5.
> READ-ONLY – keine Fixes, nur Befunde. Maßstab: **kleine Genossenschafts-Waschküche,
> ehrenamtlicher Betreiber** (nicht Enterprise-SRE).
> Schwere: kritisch / hoch / mittel / niedrig – `hoch` = Risiko, das im ersten Betriebsjahr
> realistisch zuschlägt und dann Daten-/Geldverlust oder längeren Ausfall bedeutet.

## Gesamturteil (über alle vier Fragen)

Der **Bau- und Ausrollpfad** ist überdurchschnittlich sorgfältig: reproduzierbares
Multi-Stage-Image (non-root, Healthcheck), `versions:set` statt sed-Hack, GHCR-Push über den
eingebauten `GITHUB_TOKEN`, Client-Jar-SHA-256, ein durchdachtes Cutover-/Rollback-Werkzeugset
(21/21 verifizierte Migrations-Asserts, idempotentes Reverse-DDL), ein bemerkenswert robuster
Terminal-Watchdog (Rollback + Endlosschleifen-Guard + Kill-Rechte-Erkennung + Leerlauf-Gate) und
die **fachlich korrekte Trennung** von Orchestrierungs-Probes (`/liveness`+`/readiness`) und
Alerting (`/operational`). Die entscheidende Schwäche liegt nicht im Mechanismus, sondern in der
**„letzten Meile" des Betriebs**: die Health-Indikatoren liefern zwar 503, aber **kein Alarm ist
tatsächlich an einen Menschen verdrahtet** (nur „im Runbook empfohlen"), und ein **Backup
existiert, ein Restore-Weg ist aber weder Schritt für Schritt beschrieben noch geprobt/geskriptet**.
Genau diese beiden Lücken – stiller 503 und ungeprobter Restore – sind für einen ehrenamtlichen
Betreiber die, die einen kleinen Vorfall im ersten Jahr zu Geld-/Datenverlust oder langem Ausfall
eskalieren lassen. Deployment (Frage 1) und nachhaltiges Monitoring (Frage 4) sind für den Maßstab
gut überlegt; Alerting (Frage 2) und Backup/Recovery (Frage 3) sind konzeptionell durchdacht, aber
im „erreicht es einen Menschen / ist es durchführbar"-Teil unfertig.

---

## Frage 1 – Deployment gut überlegt und reproduzierbar?

### Befund (was existiert)

- **Image-Bau:** `backend/Dockerfile` Multi-Stage (deps → build → schlankes
  `eclipse-temurin:21-jre-jammy`-Runtime), non-root User UID/GID 10001, `HEALTHCHECK` gegen
  `/actuator/health/liveness`, `TZ=Europe/Berlin` fest. Build-Kontext = Repo-Wurzel (Parent-POM-
  Fallstrick sauber dokumentiert). `.dockerignore` hält den Kontext klein.
- **Reproduzierbarkeit CI/Release:** `ci.yml` baut das Image zusätzlich als reinen Build-Beweis;
  `maven-publish.yml` setzt Versionen über `versions:set -DgenerateBackupPoms=false` (Parent +
  beide Module konsistent, kein sed über mehrere POMs), pusht das Backend-Image nach
  `ghcr.io/<owner>/elwasys-backend:<tag>` **und** `:latest` über den eingebauten `GITHUB_TOKEN`
  (`packages: write`), lädt das Client-fat-jar + `.sha256` als Release-Asset hoch
  (`maven-publish.yml:62-86`).
- **Client-Jar-Integrität:** `update.sh` verifiziert den Download vor dem Ausrollen per
  `sha256sum -c` + Zip-Strukturprüfung (Issue #62); Rollback-Layout `latest`/`previous`.
- **Preflight/Gates:** `deploy/cutover/01-preflight-check.sh` (read-only Readiness-Report),
  `verify-cutover-migration.sh` (21/21 Asserts, real ausgeführt), `verify-rollback.sh`,
  `post-deploy-smoke.sh` als Rollout-Gate (Liveness + read-only Playwright-Teilmenge).
- **Secrets:** `.env` ist nicht eingecheckt (`.env.example` nur Platzhalter); Compose erzwingt
  `POSTGRES_PASSWORD` per `${VAR:?...}`-Guard; Helm `secret.password` hat bewusst **keinen**
  Default (leeres Passwort fällt auf), CI rendert mit Dummy. Keine Secrets im Repo gefunden.
- **Terminal:** `setup.sh` (frisch), `upgrade-jre.sh` (Bestand Java 17→21, zwingend zuerst),
  `update.sh`, `auto-update-watchdog.sh` – alle mit Trockentest-/Selftest-CI-Job
  (`terminal-scripts`), enge sudoers-Regel statt `NOPASSWD:ALL`.
- **Compose/Helm-Härtung:** Port nur auf `127.0.0.1`, TLS-Pflicht, Log-Limits, TZ überall,
  `helm lint`+`template` als CI-Gate.

### Urteil

**Gut überlegt** und für den Maßstab überdurchschnittlich reproduzierbar. Der Release- und
Cutover-Pfad ist dokumentiert und weitgehend stolperfrei. Zwei echte Parität-/Pinning-Schwächen
trüben das Bild, keine davon blockierend.

### Findings

- **mittel · `deploy/compose/docker-compose.yml:63-69`** · Der Compose-Produktivpfad **baut das
  Image lokal** (`build: context ../..`, `image: elwasys-backend:local`) statt das im Release nach
  GHCR gepushte Image zu **ziehen**. Damit hängt „was läuft in Produktion" am Git-Checkout des
  Docker-Hosts, nicht an einem Release-Tag/Digest – im Gegensatz zum Helm-Weg, der
  `ghcr.io/ullriti/elwasys-backend` referenziert. Die beiden Wege deployen also grundsätzlich
  verschiedene Artefakt-Quellen; ein reproduzierbarer, an ein Release gebundener Compose-Deploy ist
  nicht der Default. · **Empfehlung:** eine dokumentierte `IMAGE`-Variable/Override, die im
  Produktivbetrieb `ghcr.io/ullriti/elwasys-backend:<tag>` (ideal per `@sha256:`-Digest) zieht statt
  lokal zu bauen; Compose und Helm auf dieselbe Release-Artefaktquelle bringen.

- **mittel · `deploy/helm/elwasys-backend/Chart.yaml:18` + `values.yaml:21`** · `image.tag: ""`
  fällt auf `Chart.appVersion` zurück – die steht hart auf `0.0.0-local-development` und wird vom
  Release-Workflow **nicht** mitgehoben (`versions:set` fasst nur die POMs an, nicht `Chart.yaml`).
  Ein `helm install` ohne explizites `--set image.tag=<release>` würde also ein Image
  `:0.0.0-local-development` ziehen, das in GHCR nicht existiert. Der Kommentar empfiehlt zwar das
  explizite Pinnen, aber der Default ist eine Stolperfalle. · **Empfehlung:** `Chart.yaml`-
  `appVersion` beim Release mitheben (Schritt im `maven-publish.yml`) **oder** in NOTES.txt/README
  hart als Pflicht kennzeichnen, dass `image.tag` gesetzt werden MUSS.

- **niedrig · kein Image-Digest-Pinning** · Weder Compose noch Helm pinnen per `@sha256:`; `:latest`
  wird zusätzlich gepusht. Für den Maßstab akzeptabel, aber `:latest` in Produktion zu vermeiden ist
  billig. · **Empfehlung:** Produktion an Tag **oder** Digest binden, `:latest` nur für Bequem-
  Pulls; im Runbook „Release einfrieren" (Generalprobe #11) den Digest festhalten.

- **niedrig · `deploy/helm/elwasys-backend/values.yaml:47`** · `readOnlyRootFilesystem: false`. Das
  Backend schreibt zur Laufzeit nichts ins Image-FS außer ggf. `/tmp`. · **Empfehlung:** perspektivisch
  `true` mit `emptyDir` für `/tmp` – reine Defense-in-Depth, kein Betriebsrisiko.

---

## Frage 2 – Alerting gut überlegt und tatsächlich verdrahtet?

### Befund

- **Health-Indikatoren:** `TerminalConnectivityHealthIndicator` (aktiver Standort = mind. ein Gerät
  ohne verbundenes Terminal-WS) und `ExpiredExecutionsHealthIndicator` (offene, abgelaufene,
  unabgerechnete Executions) ziehen bei einem Fehlerbild auf `OUT_OF_SERVICE`/HTTP 503.
- **Gruppentrennung (application.yml:63-101):** dedizierte Gruppe `/actuator/health/operational`
  (nur die beiden Indikatoren) fürs Alerting; `/liveness`+`/readiness` (nur Prozess-Status) für
  Orchestrierung/Gates. `show-details: when-authorized` (kein Leak). Diese Trennung verhindert
  korrekt falsche Neustarts/Gate-Flattern – gut durchdacht.
- **DB-Ausfall** wird indirekt sichtbar: die beiden Indikatoren fragen die DB an (würden bei
  DB-down fehlschlagen), zusätzlich aggregiert das Root-`/actuator/health` den Spring-Boot-
  DataSource-Indikator.
- **Terminal-seitige kritische Ereignisse:** Dead-Letter-Journaleinträge und die „Geister-Execution"-
  Kompensation werden als `logger.error(...)` protokolliert (`OfflineGateway.java:416`, `435`,
  `363`) – „laut alarmiert" heißt hier: eine Zeile im **lokalen Logback-Log des Terminals**.
- **Runbook Kap. 7b:** empfiehlt, `/operational` „regelmäßig zu pollen (Monitoring/Uptime-Check)"
  und auf `status != UP` zu alarmieren.

### Urteil

Das **Konzept** ist gut überlegt (richtige Signale, richtige Endpunkt-Trennung, kein Leak). Der
**Vollzug fehlt**: es ist **kein konkreter Alarmkanal eingerichtet**. `/operational` liefert 503,
aber im Repo/Runbook pollt das niemand, und kein Weg (Uptime-Monitor, Mail, Push) ist verdrahtet.
Für einen ehrenamtlichen Betreiber bedeutet ein stiller 503 einen stillen Ausfall. Das ist die
größte Betriebslücke und deckt sich mit Generalprobe-Punkt 5 der Spec.

### Findings

- **hoch · `deploy/CUTOVER-RUNBOOK.md:326-347` (Kap. 7b) + `application.yml:94-101`** · Es gibt
  **keinen verdrahteten Alarmkanal**. Kein Uptime-Monitor, kein Cron-Heartbeat, keine Mail/Push auf
  `status != UP` – nur die Empfehlung „regelmäßig pollen". Backend down, DB down, Terminal offline,
  offene abgelaufene Execution: alle erzeugen 503, aber **kein Mensch erfährt davon**, solange der
  Betreiber nicht selbst ein externes Monitoring aufsetzt. · **Empfehlung:** ein konkretes,
  minimales Alerting-Rezept mitliefern und als Pflicht-Vor-Feldeinsatz kennzeichnen – z. B. ein
  dokumentierter Cron auf dem Docker-Host, der `/actuator/health/operational` curlt und bei Fehler
  per Pushover/Mail meldet, **oder** eine `healthchecks.io`/Uptime-Kuma-Anleitung. Die vorhandene
  Pushover-/SMTP-Konfiguration (nur für Nutzer-Benachrichtigungen genutzt) könnte denselben Kanal
  bedienen. Aus „empfohlen" muss „verdrahtet" werden.

- **mittel · `Client-Raspi/.../offline/OfflineGateway.java:363,416,435`** · **Dead-Letter- und
  Replay-Fehler am Terminal erreichen niemanden.** Ein dead-gelettertes Offline-Journal-Ereignis =
  eine **verlorene Offline-Buchung (Geld)**; es landet nur als `logger.error` im lokalen
  `/opt/elwasys/log`-Logback des Pi, wird nicht ans Backend gemeldet und taucht in keinem
  Health-Indikator auf. Die Geister-Execution wird immerhin per `abort` bereinigt (schlägt der
  abort fehl, fällt sie über `isExpired` in den `ExpiredExecutionsHealthIndicator` – gute
  Teilabdeckung); der reine Dead-Letter-Fall bleibt aber ein **stiller Geldverlust**. · **Empfehlung:**
  Dead-Letter-/Replay-Fehlerzähler des Terminals über den bereits bestehenden Wartungs-WS-Kanal ans
  Backend melden und in einen Health-Indikator/Alarm heben; mindestens eine dokumentierte
  Log-Scan-Betriebsaufgabe je Terminal festhalten.

- **mittel · Runbook / setup.sh** · **Kein Alarm für Zertifikatsablauf, Plattenplatz und
  NTP-Drift** (Detail zu Zertifikat/NTP unter Frage 4). „Backend down" löst über die Liveness-Probe
  nur einen k8s-/Compose-Neustart aus – **keinen Menschen-Alarm**; dieselbe Wurzel wie das
  Hoch-Finding (nichts pollt von außen). · **Empfehlung:** diese drei Signale in dasselbe
  Alerting-Rezept aufnehmen (Cert-Expiry-Check, `df`-Schwellwert, `timedatectl`-Sync-Check).

- **niedrig · `TerminalConnectivityHealthIndicator.java:63-67`** · Ein Standort ohne zugeordnete
  Geräte gilt als „inaktiv" und alarmiert nie. Werden vorübergehend alle Geräte eines aktiven
  Standorts entfernt, verstummt der Alarm dort still. Edge-Case, für den Maßstab unkritisch. ·
  **Empfehlung:** so belassen; ggf. als bewusste Annahme in der Klassen-Javadoc (steht bereits dort).

---

## Frage 3 – Backup & Recovery gut überlegt und praktikabel?

### Befund

- **Backup (Runbook Kap. 7a):** täglicher `pg_dump | gzip`-Cron auf dem Docker-Host (Beispiel
  03:15), Retention `find … -mtime +30 -delete`; Hinweis „Backup-Verzeichnis außerhalb des Hosts
  spiegeln/sichern"; für Kubernetes an den DB-Betreiber delegiert (CloudNativePG/CronJob/Snapshots).
  Backup ist im Cutover ausdrücklich **zwingende Voraussetzung** (Kap. 2, Kap. 6 Schritt 0).
- **Restore:** im Cutover-Rollback als **primärer** Weg genannt („das vor Schritt 2 gezogene Backup
  zurückspielen", `deploy/cutover/README.md:146-153`) – konzeptionell, ohne konkretes Kommando; der
  sekundäre Reverse-DDL-Weg (`rollback-cutover.sh`) ist dagegen geskriptet, idempotent und mit
  Caveats beschrieben. „Restore regelmäßig proben – ein nie getestetes Backup ist keins" steht als
  Satz (Kap. 7a).
- Es existiert **kein Backup-/Restore-Skript im Repo** (Suche: keine `*backup*`/`*restore*`-Dateien).

### Urteil

**Backup-Seite gut überlegt** (zwingend, mit Retention, offsite angerissen). **Recovery-Seite
unfertig:** der Restore ist nur als Konzept/Satz vorhanden, nicht Schritt für Schritt und nicht
geprobt/geskriptet. RPO ist implizit ~24 h (täglicher Dump), RTO ist **unbekannt**, weil der
Restore nie durchgespielt wurde. Für das Szenario „Blitz zerstört den Server am Samstagabend" fehlt
genau die durchführbare Anleitung, die ein ehrenamtlicher Betreiber unter Stress braucht.

### Findings

- **hoch · `deploy/CUTOVER-RUNBOOK.md` Kap. 7a / `deploy/cutover/README.md:146-153`** · **Kein
  Schritt-für-Schritt-Restore und kein Restore-Skript.** Das Backup ist ein Einzeiler; der inverse
  Restore (Ziel-DB/Rollen anlegen, `gunzip -c dump.sql.gz | psql …`, Backend darauf zeigen) ist
  nirgends ausgeschrieben, RPO/RTO nicht beziffert, kein Restore-Test-Werkzeug. · **Empfehlung:** ein
  `deploy/`-Restore-Runbook (+ optional ein Restore-Helper analog `verify-cutover-migration.sh`),
  RPO (24 h) und ein grobes RTO explizit nennen, und die Restore-Probe als Repo-Checklistenpunkt
  verankern (nicht nur als Satz; Generalprobe #3 der Spec adressiert das, gehört aber als Prozedur
  ins Repo).

- **mittel · Backup-Scope** · Gesichert wird **nur die DB**. **Nicht** gesichert: die
  Terminal-Konfiguration (`elwasys.properties` inkl. `backend.token`, `logback.xml`, `run.sh`) und –
  betrieblich am heikelsten – das **Offline-Journal auf der SD-Karte** (noch nicht replayte
  Offline-Buchungen = Geld). Stirbt eine Pi-SD-Karte mitten in der Woche mit unrepliziertem Journal,
  ist diese Buchung verloren. Auch die Backend-Secrets (`.env` / Helm-Values) sind von keiner
  Backup-Vorgabe erfasst. · **Empfehlung:** dokumentieren, dass `.env`/Values (Secrets) und je
  Terminal die `elwasys.properties` in das (getrennte, verschlüsselte) Betreiber-Backup gehören; das
  SD-Karten-/Journal-Verlustrisiko als Betriebsrisiko benennen.

- **niedrig · Runbook Kap. 7a** · Der Backup-Cron schreibt nach `/var/backups/elwasys` auf
  **demselben Host**; „außerhalb des Hosts spiegeln" ist nur ein Klammerzusatz. Das Blitz-/
  Diebstahl-Szenario vernichtet Host **und** lokale Backups gemeinsam. · **Empfehlung:** die Offsite-
  /Offhost-Kopie als feste Anweisung formulieren (z. B. `rclone`/`scp` auf ein zweites Ziel), nicht
  als Parenthese.

---

## Frage 4 – Nachhaltiges Monitoring (trägt der Betrieb über Monate)?

### Befund

- **DB-Wachstum:** `IdempotencyKeyRetentionScheduler` (täglich 03:00, `retention-days` Default 30,
  beides per Env konfigurierbar) räumt `terminal_idempotency_keys` – die **einzige** hochfrequente
  Wachstumstabelle. Läuft über das bestehende `@EnableScheduling`
  (`TerminalWebSocketConfig`, für die CLI-Profile abgeschaltet).
- **Log-Rotation:** Compose json-file-Limits `max-size 10m`/`max-file 3` auf **beiden** Containern;
  Terminal: Logback tägliche Rotation + `run.sh`-`stdout`/`errout` per `ELWA_LOG_MAX_BYTES` (Default
  5 MiB) gekappt; Watchdog-Log vorhanden.
- **Zeit/TZ:** `Europe/Berlin` in Dockerfile, Compose (Backend+Postgres, inkl. `PGTZ`), Helm
  durchgereicht; Preflight-Gate prüft TZ-Gleichheit.
- **Zertifikate:** Caddy (Compose-Overlay) und cert-manager (Helm, empfohlen) erneuern automatisch.

### Urteil

Für den Dauerbetrieb über Monate **gut überlegt in den Mechaniken** – der richtige Purge-Zielort,
Log-Rotation an allen drei Stellen, TZ konsistent. Drei Lücken bleiben (NTP, Zert-Selbstverwaltung,
Plattenplatz), davon zwei mit realem Zuschlag-Risiko im ersten Jahr.

### Findings

- **mittel · `Client-Raspi/setup.sh` (keine NTP-Konfiguration/-Prüfung)** · Zeit-Synchronisierung
  wird weder eingerichtet noch verifiziert – nur die **Zeitzone** prüft das Runbook (`timedatectl`).
  Der Raspberry Pi hat **keine RTC**; bootet er ohne Netz, ist die Uhr falsch, und Uhr-Plausibilität
  speist `ClientTimestampPolicy`/Replay + DYNAMIC-Preis. Die serverseitige Replay-Härtung (#67) ist
  ein Auffangnetz, aber eine driftende Pi-Uhr korrumpiert lokale Zeitstempel still. · **Empfehlung:**
  `setup.sh` stellt `systemd-timesyncd`/`chrony` sicher (enable + Sync verifizieren); NTP-Sync-Check
  in Preflight/Feld-Checkliste aufnehmen.

- **mittel · `deploy/compose/docker-compose.proxy.yml` / `values.yaml:64-73`** ·
  Zertifikats-**Erneuerung ist nur auf den empfohlenen Pfaden** (Caddy, cert-manager) automatisiert.
  Der Weg „selbst verwaltetes TLS-Secret" (Helm-Option 2) bzw. ein externer Reverse Proxy vor dem
  Basis-Compose-File hat **keine** Erneuerungs-Automatik und **kein** Ablauf-Monitoring – ein
  vergessenes Zertifikat läuft ab → Totalausfall (auch der Terminals, TLS-Pflicht). · **Empfehlung:**
  klarstellen, dass der Selbstverwaltungs-Pfad eigene Erneuerung + Ablauf-Alarm erfordert; die
  Auto-Pfade als Default bewerben; Cert-Expiry-Check ins Alerting-Rezept (Frage 2).

- **niedrig (positiv anzumerken) · `executions` / `credit_accounting`** · Beide wachsen unbegrenzt,
  sind aber der **finanzielle/Audit-Ledger** und dürfen **nicht** gepurgt werden. Bei
  Waschküchen-Maßstab (einstellige Gerätezahl) sind das wenige Tausend Zeilen/Jahr – kein reales
  Risiko. Dass ausgerechnet die hochfrequenten Idempotenz-Schlüssel gepurgt werden und der Ledger
  nicht, ist die **richtige** Entscheidung. · **Empfehlung:** so belassen; ggf. einen Satz ins
  Runbook, dass diese Tabellen bewusst dauerhaft wachsen.

- **niedrig · kein Plattenplatz-Monitoring** · Weder `pgdata`-Volumen noch Backup-Verzeichnis werden
  überwacht. Bei dem Maßstab unwahrscheinlich voll, aber in Kombination mit dem fehlenden Alerting
  (Frage 2) wäre ein langsames Volllaufen unsichtbar. · **Empfehlung:** `df`-Schwellwert in dasselbe
  Alerting-Rezept.

- **niedrig · `deploy/terminal/README.md:319` (Watchdog-Cron-Log)** · Die
  `auto-update-watchdog.cron.log` wird nur „bei Bedarf per logrotate" begrenzt, nicht erzwungen.
  Geringes Volumen, unkritisch. · **Empfehlung:** eine fertige `logrotate`-Beispieldatei beilegen.

---

## Was bereits gut gelöst ist (ausdrücklich)

- **Probe-Trennung Orchestrierung vs. Alerting** (`/liveness`+`/readiness` vs. `/operational`) –
  korrekt gedacht, verhindert Neustart-/Gate-Flattern beim Cutover; konsistent in Dockerfile,
  Compose, Helm, Smoke.
- **Terminal-Watchdog** – Rollback, Endlosschleifen-Guard (`.update-failed`, #34),
  Fetch-≠-Deploy-Unterscheidung (B1), Leerlauf-Gate (M1), Kill-Rechte-Erkennung (#63), Lockfile –
  bemerkenswert durchdacht für „schlanke Shell-Variante".
- **Cutover-/Rollback-Werkzeuge** – Preflight, `verify-cutover-migration.sh` (21/21 real),
  idempotentes Reverse-DDL mit ehrlichen Caveats, `verify-rollback.sh`, Rollback-Entscheidungsbaum.
- **Reproduzierbarer Release** – `versions:set`, Client-Jar-SHA-256, GHCR via `GITHUB_TOKEN`,
  Image-Build-Beweis in CI, `helm lint`/`template`-Gate, Terminal-Selftest-Job.
- **Purge-Zielwahl** – genau die hochfrequente Tabelle wird geräumt, der Ledger bewusst nicht.
- **Secrets-Hygiene** – keine Secrets im Repo, `.env`-Guard, Helm-Passwort-Guard ohne unsicheren
  Default.
