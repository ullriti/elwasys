# Produktiv-Cutover-Runbook (Phase 6 AP7)

Das **übergreifende, orchestrierende Runbook** für die eigentliche Produktivumschaltung des
elwasys-Bestands auf die neue Architektur. Es führt die in Phase 6 AP1–AP6 gebauten Werkzeuge
zu **einer sequenzierten Anleitung** zusammen und hält die **Strangler-Reihenfolge** fest
(erst Portal/Backend, dann Terminals schrittweise – kein Big-Bang).

Dieses Dokument **dupliziert nichts**, sondern **verlinkt** die drei bestehenden
Detail-Runbooks, die jeweils der Maßstab für die einzelnen Schritte bleiben:

- **DB-/Backend-Seite (Cutover-Werkzeuge)** → [`deploy/cutover/README.md`](cutover/README.md)
- **Terminal-Seite (JRE-21, Update, Watchdog)** → [`deploy/terminal/README.md`](terminal/README.md)
- **Rollout-Gate (Post-Deploy-Smoke)** → [`deploy/smoke/README.md`](smoke/README.md)

Hintergrund/Roadmap: [docs/kb/05-migration-plan.md](../kb/05-migration-plan.md), Abschnitt
„Phase 6 – Produktivumschaltung (Cutover)".

> **Scope-Grenze (ehrlich):** Die **Feld-Umschaltung selbst** – echte Raspi-Hardware, echte
> Produktiv-DB, das Wartungsfenster – ist ein **operativer Schritt des Auftraggebers** und
> NICHT Teil dieses Repos/dieser Session. Was hier steht, ist die geprobte Reihenfolge plus
> die dazu gehörenden, in der Sandbox verifizierten Werkzeuge. Was in der Sandbox nicht real
> ausführbar ist (kein Docker-Daemon, keine armhf-Hardware, keine Produktiv-DB), ist unten
> jeweils als „nur dokumentiert/trocken" gekennzeichnet.

---

## 1. Zielbild & Rahmenbedingungen

- **Ausgangslage:** bestehendes Produktiv-Setup = physische Raspi-Terminals im Feld
  (Direkt-DB-Zugriff, Java 17) + ein laufendes Alt-Portal (Vaadin 7) auf einer gemeinsamen
  PostgreSQL-DB, angelegt über den Alt-Weg (Schema 0.4.0, entspricht der Flyway-V1-Baseline).
- **Ziel:** dieselbe DB (mit allen Bestandsdaten) unter das neue Backend nehmen
  (Flyway-verwaltetes Schema, Vaadin-Flow-Portal im Backend) und die Terminals auf
  REST-API + Standort-Token + ausgehende Wartungs-WebSocket umstellen (kein Direkt-DB-Zugriff
  mehr, Java 21).
- **Rahmenbedingungen (Auftraggeber):**
  - **Kein Datenverlust** – die Migration ist additiv/nicht-destruktiv, ein DB-Backup ist
    zwingende Voraussetzung, ein klarer Rollback-Pfad existiert.
  - **Nutzer-sichtbares Verhalten bleibt gleich** – Terminal-Bedienfluss und Portal-Features
    unverändert; die E2E-Suiten sind der Maßstab.
  - **Strangler statt Big-Bang** – Komponenten einzeln umhängen, jede Stufe mit
    Beobachtungs-/Gate- und Rollback-Punkt, statt alles gleichzeitig umzustellen.

---

## 2. Voraussetzungen & Vorbereitung

- [ ] **DB-Backup ZWINGEND vorher** – vollständiges Backup der Produktiv-DB
      (`pg_dump`/`pg_basebackup` oder der Snapshot-Mechanismus der Betriebsumgebung). Kein
      Skript ersetzt das Backup; der Cutover-Schritt 3a führt echte Flyway-DDL (+ eine
      Datenänderung in V7) gegen die Produktiv-DB aus. Backup **verifizieren** (restaurierbar).
- [ ] **Wartungsfenster planen** und **Nutzer informieren** (Waschküche kurzzeitig nicht
      buchbar). Laufende Waschvorgänge berücksichtigen (die Terminals führen laufende
      Executions ohnehin lokal zu Ende, Phase 4 AP6 – die Terminal-Charge erst anfassen, wenn
      ihre Geräte frei sind).
- [ ] **Release-Artefakte bereitstellen und festhalten** (für einen reproduzierbaren
      Rollback):
      - Backend-**Image-Tag** (GHCR, aus dem Release-Workflow) bzw. Backend-Jar-Version.
      - Terminal-**Jar-Version** (GitHub-Release-Tag `raspi-client-<tag>.jar`).
      - Den **aktuell laufenden** Alt-Portal-Stand / das vorherige Backend-Image als
        Rollback-Ziel notieren.
- [ ] **Preflight-Check gegen die Produktiv-DB** (rein lesend, verändert nichts):
      ```bash
      ELWASYS_DB_URL=jdbc:postgresql://<host>:5432/elwasys \
      ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<pw> \
      deploy/cutover/01-preflight-check.sh
      ```
      Liefert den Readiness-Report (Flyway-Status, Alt-Artefakte, Dateninventar, Warnungen wie
      „Standort ohne aktives Token", „admin ohne Passwort"). Details:
      [`deploy/cutover/README.md`](cutover/README.md), Schritt 1.
- [ ] **Migration vorab an einer Bestandskopie proben** – siehe
      [`deploy/cutover/verify-cutover-migration.sh`](cutover/verify-cutover-migration.sh)
      (baut lokal eine Kopie des Bestandsschemas, migriert, prüft Datenerhalt + Härtung per
      Assert-Liste). Idealerweise gegen eine **echte, anonymisierte Kopie** der Produktiv-DB
      laufen lassen, nicht nur gegen die synthetischen Bestandsdaten des Skripts.

---

## 3. Reihenfolge (Strangler) – nummeriert, je Schritt Gate + Rollback

### Schritt 3a – Portal/Backend zuerst umstellen (gegen die BESTEHENDE Produktiv-DB)

Das neue Backend gegen die **bestehende** Produktiv-DB deployen – **nicht** eine neue DB
danebenstellen. Flyway erkennt die fehlende `flyway_schema_history`, **baselined die DB auf
V1** (== eingefrorener 0.4.0-Alt-Weg-Stand, kein erneutes Ausführen von V1) und wendet danach
**V2..V10** automatisch an (`baseline-on-migrate`, siehe `application.yml`; die
Baseline-on-migrate-Konfig steht ebenda). Die Bestandsdaten bleiben unverändert.

```bash
# docker-compose-Redeploy (Bestands-DB: den mitgelieferten "postgres"-Service NICHT starten,
# ELWASYS_DB_URL auf die externe Produktiv-DB zeigen – siehe deploy/compose/.env.example):
cd deploy/compose
docker compose up -d backend        # bzw. helm upgrade ... für Kubernetes/Helm
```

- **Beobachtung/Log:** `curl http://<host>:8080/actuator/health` → `{"status":"UP"}`, Logs auf
  saubere Flyway-Migration prüfen (BASELINE@1, dann V2..V10 `success`).
- **Gate:** [`deploy/smoke/post-deploy-smoke.sh`](smoke/post-deploy-smoke.sh) – Health `UP`
  **und** die schlanke, strikt read-only Playwright-Teilmenge müssen grün sein. **Erst bei
  GRÜN gilt der Rollout als erfolgreich.**
  ```bash
  BASE_URL=http://<host>:8080 \
  SMOKE_ADMIN_USER=admin SMOKE_ADMIN_PASSWORD='<pw>' \
  deploy/smoke/post-deploy-smoke.sh
  ```
- **Rollback (Gate rot oder Fehler):**
  1. **Plattform-Rollback** (Normalfall, DB additiv/unverändert): Redeploy des **vorherigen**
     Backend-Images per `docker compose` bzw. `helm rollback`. Details:
     [`deploy/smoke/README.md`](smoke/README.md).
  2. **Falls die DB zurück muss** (z. B. die Migration selbst wird als Ursache verworfen):
     - **primär** den **Backup-Restore** (sicherster Weg, exakter Vor-Cutover-Zustand),
     - **sekundär** [`deploy/cutover/rollback-cutover.sh`](cutover/rollback-cutover.sh)
       (idempotentes Reverse-DDL, erhält im Cutover-Fenster entstandene Neu-System-Daten –
       braucht CREATEROLE/Superuser). Auswahl + **Caveats** (Alt-Rollen-Default-Passwörter,
       Argon2 vs. SHA1, leere App-Spalten): [`deploy/cutover/README.md`](cutover/README.md),
       Abschnitt „Rollback".

### Schritt 3b – Standort-Tokens ausstellen + Admin-Passwort setzen

Nach grünem Gate, bevor Terminals angefasst werden:

- **Standort-Token je Bestandsterminal** (der Zugriffs-Scope hängt am Token, nicht am
  Anzeigenamen):
  ```bash
  ELWASYS_DB_URL=... ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<pw> \
  deploy/cutover/02-issue-terminal-tokens.sh --location=<Standortname> [--label=<Text>]
  ```
  Das Klartext-Token erscheint **genau einmal** – sofort in die Terminal-Konfiguration
  (`backend.token` in `elwasys.properties`) übernehmen (siehe Schritt 3d).
- **Admin-/Benutzer-Passwort setzen** – eine gehärtete Bestands-DB hat seit V7 **kein
  bekanntes Admin-Passwort mehr**; ohne diesen Schritt ist kein Portal-Login möglich:
  ```bash
  ELWASYS_DB_URL=... ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=<pw> \
  deploy/cutover/03-set-admin-password.sh [--username=admin]
  ```
- **Optional (rein lesend):** obsolete Standorte reviewen –
  [`deploy/cutover/04-review-obsolete-locations.sql`](cutover/04-review-obsolete-locations.sql)
  (markiert Standorte ohne Geräte + ohne aktives Token; löscht nichts automatisch).

Details zu 02/03/04: [`deploy/cutover/README.md`](cutover/README.md), Schritte 3–5.

### Schritt 3c – Beobachten (bevor Terminals angefasst werden)

Portal/Backend eine **definierte Zeit** (z. B. 15–30 min, je nach Wartungsfenster) beobachten:
`/actuator/health` bleibt `UP`, Logs sauber, ein Admin-Login ins neue Portal funktioniert, die
Kernsektionen (Benutzer/Geräte/Programme/Dashboard) rendern. Erst dann die Terminals umstellen.
Bleibt hier etwas rot → Rollback nach Schritt 3a, **bevor** Terminals umgehängt werden (dann
ist der Rückweg am einfachsten, weil die Terminals noch am Alt-Stand hängen).

### Schritt 3d – Terminals schrittweise (NICHT alle gleichzeitig)

Terminals **in Chargen** umstellen (z. B. eine Waschküche / ein Standort nach dem anderen),
damit ein Problem nie alle Geräte gleichzeitig trifft. **Pro Charge:**

1. **JRE-21 ZUERST** (Bestandsgeräte tragen nur Java 17; ein Sprachlevel-21-Jar bricht sonst
   mit `UnsupportedClassVersionError` ab):
   ```bash
   deploy/terminal/upgrade-jre.sh          # idempotent, verifiziert danach java -version >= 21
   ```
2. **Dann auf backend.url + Token umstellen:**
   - **Bestandsgerät:** `elwasys.properties` auf `backend.url`/`backend.token` setzen (Token
     aus Schritt 3b) und das neue Client-Jar ausrollen:
     ```bash
     deploy/terminal/update.sh --version <tag>     # bzw. --jar <lokaler Pfad> (Offline)
     ```
   - **Neu/frisch aufzusetzendes Gerät:** das interaktive `Client-Raspi/setup.sh` (installiert
     Java 21, schreibt Konfig + Supervisor-`run.sh` + X-Autologin).
3. **Start verifizieren:** das Terminal erreicht den bedienbereiten Zustand `SELECT_DEVICE`
   (Readiness-Marker `${ELWA_ROOT}/.terminal-ready` bekommt frischen `mtime`); der
   Bedienfluss ist unverändert.
4. Erst dann die **nächste Charge**.

Künftige Updates übernimmt der [`auto-update-watchdog.sh`](terminal/auto-update-watchdog.sh)
(Cron, GitHub-`latest` bzw. `.update-target`) mit **Auto-Rollback** bei fehlgeschlagenem Start
(Deadline auf den Readiness-Marker → `latest` zurück auf `previous`).

- **Rollback einer Charge:** vorheriges Jar zurückrollen –
  ```bash
  deploy/terminal/update.sh --jar <vorheriges raspi-client-<version>.jar>
  ```
  bzw. der Watchdog-Rollback (`latest` → `previous`). Zur Not das Gerät wieder auf den
  Alt-Stand (Alt-Jar + Alt-`elwasys.properties` mit DB-Zugang) setzen. Details + Jar-Layout
  (`latest`/`previous`): [`deploy/terminal/README.md`](terminal/README.md).

### Schritt 3e – Benachrichtigungsdienst scharfschalten (bewusst SEPARAT, ZULETZT)

Der Backend-Benachrichtigungsdienst ist per Default **AUS** (`application.yml`:
`elwasys.notifications.enabled` → `${ELWASYS_NOTIFICATIONS_ENABLED:false}`). Das
Scharfschalten ist ein **bewusster, separater operativer Schritt NACH stabilem Cutover** –
nicht Teil von 3a:

```bash
# z. B. in deploy/compose/.env bzw. den Helm-Values:
ELWASYS_NOTIFICATIONS_ENABLED=true
```

- **Warum zuletzt/separat:** **Doppelversand-/Fehlkonfig-Risiko**. Erst muss die
  SMTP-/Pushover-Konfig stehen (`ELWASYS_SMTP_*`, `ELWASYS_PUSHOVER_API_TOKEN`,
  `ELWASYS_SMTP_SENDER_ADDRESS`). Der Alt-Client versendet seit Phase 4 nichts mehr, aber das
  Flag erst umlegen, wenn keine Alt-Terminals mit eigenem Versand mehr laufen (nach 3d) und
  die Mail-/Push-Konfig getestet ist.
- **Rollback:** Flag wieder auf `false`, Redeploy – rein additiv, kein DB-Effekt.

> Hinweis: `spring.mail.*`/Passwort-Reset (`elwasys.password-reset.enabled`, Default **AN**)
> ist davon unabhängig (kein Doppelversand, nur durch explizite Portal-Aktion ausgelöst) –
> siehe `application.yml`.

---

## 4. Rollback-Entscheidungsbaum (kompakt)

| Fehlerbild | Rückweg |
|---|---|
| **Gate (3a) rot**, DB unverändert/additiv | **Plattform-Rollback** (compose-Redeploy Alt-Image / `helm rollback`). Fertig. |
| Gate rot **und** DB soll auf Vor-Cutover-Zustand | **Backup-Restore** (primär). Neu-System-Daten aus dem Fenster gehen verloren. |
| DB zurück, aber **Cutover-Fenster-Daten erhalten** | `deploy/cutover/rollback-cutover.sh` (sekundär, CREATEROLE) + Caveats in `deploy/cutover/README.md`. |
| **Einzelnes Terminal/Charge (3d)** startet nicht | `update.sh --jar <vorheriges Jar>` bzw. Watchdog-Rollback (`latest`→`previous`); Rest des Cutovers bleibt stehen. |
| **Benachrichtigungen (3e)** falsch/doppelt | `ELWASYS_NOTIFICATIONS_ENABLED=false`, Redeploy. Kein DB-Effekt. |
| Problem **vor** Terminal-Umstellung erkannt (3c) | Am einfachsten: Backend zurückrollen, Terminals hängen noch am Alt-Stand. |

Faustregel: **je später der Schritt, desto lokaler der Rückweg** – Portal/Backend über die
Plattform, DB über Backup/Reverse-DDL, Terminals einzeln über das vorherige Jar.

---

## 5. Post-Cutover

- [ ] **Abschluss-Verifikation:** `post-deploy-smoke.sh` grün; alle Terminal-Chargen erreichen
      `SELECT_DEVICE` und sind bedienbar; ein **echter Testwaschgang** (Karten-Login → Gerät
      buchen → Programm starten → Ende/Abrechnung) über ein umgestelltes Terminal.
- [ ] **Benachrichtigungen** (falls in 3e scharfgeschaltet) an einem realen Vorgang bestätigt
      (eine Mail/Push kommt an, keine Doppelversände).
- [ ] **Nutzerkommunikation „fertig"** – Waschküche wieder normal nutzbar.
- [ ] **Monitoring** für den Dauerbetrieb: `/actuator/health` überwacht, Backend-/DB-Logs,
      Terminal-Watchdog-Logs (`${ELWA_ROOT}/log/auto-update-watchdog.log`).
- [ ] **Backup vom neuen Zustand** ziehen (frischer Ausgangspunkt nach dem Cutover).
- [ ] Alt-Portal-WAR/Alt-Deployment außer Betrieb nehmen (das Alt-Portal-Modul ist bereits in
      Phase 5 AP1 aus dem Repo entfernt – hier geht es nur noch um eine evtl. noch laufende
      Alt-Instanz).

---

## 6. Checkliste / Timeline (Wartungsfenster-tauglich)

| # | Schritt | Gate / Beweis | Rollback |
|---|---|---|---|
| 0 | DB-Backup ziehen **und verifizieren**; Release-Artefakte + Rollback-Ziele festhalten | Backup restaurierbar | — |
| 0b | `01-preflight-check.sh` gegen Produktiv-DB; Migration an Bestandskopie proben (`verify-cutover-migration.sh`) | Report ok, Asserts PASS | — |
| 1 | **3a** Backend gegen Bestands-DB deployen (compose/Helm), Flyway migriert | `/actuator/health` UP, Flyway V2..V10 `success` | Plattform-Rollback |
| 2 | **3a Gate** `post-deploy-smoke.sh` | Health UP **+** Playwright-Smoke grün | Plattform-Rollback / Backup-Restore / `rollback-cutover.sh` |
| 3 | **3b** Standort-Tokens (`02`) + Admin-Passwort (`03`); optional `04` review | Token(s) erzeugt, Portal-Login möglich | — (rein additiv) |
| 4 | **3c** Portal/Backend beobachten (definierte Zeit) | Health/Logs stabil, Admin-Login/Sektionen ok | Backend-Rollback (Terminals noch alt) |
| 5 | **3d** Terminals Charge für Charge: JRE-21 → Konfig/Jar → `SELECT_DEVICE` | Readiness-Marker frisch, bedienbar | `update.sh --jar <prev>` / Watchdog-Rollback |
| 6 | **3e** `ELWASYS_NOTIFICATIONS_ENABLED=true` (SMTP/Pushover-Konfig steht) | Testvorgang → genau eine Mail/Push | Flag `false`, Redeploy |
| 7 | **Post-Cutover:** Testwaschgang, Nutzerkomm. „fertig", Monitoring, Neu-Backup | echter Waschgang ok | — |

---

## Geprobt vs. nur dokumentiert (Ehrlichkeit)

- **In der Sandbox real ausgeführt (DB-/Backend-Seite):**
  - `deploy/cutover/verify-cutover-migration.sh` – Bestandskopie → migriert, **21/21 Asserts
    PASS** (Flyway BASELINE@1 + V2..V10, Datenerhalt, Schema-Härtung; Backend-Jar gegen die
    Alt-Weg-DB `/actuator/health` UP).
  - Das **Rollout-Gate** `deploy/smoke/post-deploy-smoke.sh` gegen einen im **Produktionsmodus**
    per `java -jar` gestarteten Server (derselbe Artefakt-Typ, den compose/Helm ausrollen; nur
    die Startmethode unterscheidet sich) – Health UP + schlanke Playwright-Teilmenge grün
    (bereits in Phase 6 AP6 verifiziert, in AP7 als Verkettungs-Baustein erneut belegt).
- **Nur dokumentiert / trocken (in dieser Umgebung nicht real ausführbar):** der
  compose-/Helm-Redeploy selbst (kein Docker-Daemon), die Terminal-Feldschritte
  (`upgrade-jre.sh`/`update.sh`/Watchdog – keine armhf-Hardware/echte X-Sitzung; nur
  Trockentests, siehe `deploy/terminal/README.md`), und die eigentliche Umschaltung gegen die
  **echte Produktiv-DB/-Hardware** (operativer Schritt des Auftraggebers).
