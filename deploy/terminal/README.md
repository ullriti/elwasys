# Terminal-Runbook (Phase 6 AP3)

Werkzeuge und Ablauf, um ein **Raspberry-Pi-Terminal auf die neue Architektur zu
bringen** (docs/kb/05-migration-plan.md, „Phase 6 – Produktivumschaltung (Cutover)",
Roadmap-Punkt „Terminals neu aufsetzen"). Neue Architektur heißt: **Java 21**,
Datenzugriff ausschließlich über die Backend-REST-API v1 + eine ausgehende
Wartungs-WebSocket-Verbindung (`backend.url`/`backend.token` in
`elwasys.properties`) statt Direkt-DB-Zugriff (seit Phase 4).

**Scope**: die Skripte `upgrade-jre.sh` (AP3) und `update.sh` (AP4) + dieses
Runbook + der zum Supervisor umgebaute `run.sh`-Generator in `Client-Raspi/setup.sh`.
Die echten apt-/X-Schritte laufen NUR auf dem Gerät (armhf, Raspberry Pi OS) und
wurden hier lediglich **trocken/syntaktisch** verifiziert (`bash -n` +
Funktions-/Ablauf-Trockentests, siehe Änderungslog in docs/kb/05). Das optionale
Auto-Update mit Rollback folgt in Phase 6 AP5 und baut auf dem hier eingeführten
`latest`/`previous`-Jar-Layout auf.

## Zwei Fälle unterscheiden

| Ausgangslage | Vorgehen |
|---|---|
| **Neues/frisch aufzusetzendes Gerät** (Ersteinrichtung) | Das komplette interaktive `Client-Raspi/setup.sh` – es installiert bereits `bellsoft-java21-runtime-full` (Java 21), lädt das Release-Jar, legt den Symlink `raspi-client.latest.jar` an, schreibt `elwasys.properties` (mit `backend.url`/`backend.token`/`location`/`portalUrl`), `logback.xml`, den **Supervisor-`run.sh`** und die X-Autologin-`~/.xsession`. Ein separater JRE-Upgrade-Schritt ist hier **nicht** nötig. |
| **Bestandsgerät** (aus einem früheren `setup.sh`-Lauf, trägt nur Java 17) | **Zuerst** `deploy/terminal/upgrade-jre.sh` (JRE auf Java 21 anheben), **danach** das neue Client-Jar ausrollen mit `deploy/terminal/update.sh` (siehe Abschnitt „`update.sh`" unten). |

## Reihenfolge (Bestandsgerät): Java 21 ZUERST

Das Client-fat-jar baut seit Phase 1 mit **Sprachlevel 21 / Bytecode-Major 65**.
Ein im Feld noch vorhandenes **Java-17-JRE** bricht ein solches Jar beim Start mit
`UnsupportedClassVersionError` ab. Deshalb gilt zwingend:

```bash
# 1) JRE auf Java 21 anheben (idempotent; verifiziert danach java -version >= 21)
deploy/terminal/upgrade-jre.sh

# 2) ERST DANACH ein mit Sprachlevel 21 gebautes Release-Jar ausrollen
deploy/terminal/update.sh --version <tag>   # bzw. --jar <lokaler Pfad>
```

`upgrade-jre.sh` nutzt dieselbe apt-Quelle/denselben Schlüssel wie `setup.sh`
(`install_java`) und prüft am Ende robust, dass `java -version` eine Major-Version
`>= 21` meldet – schlägt sonst klar fehl. Damit ist das in Phase 1 dokumentierte
**Java-17-Restrisiko** (Risikotabelle in docs/kb/05) für das jeweilige Gerät aufgelöst.

## `backend.url` / `backend.token`

Seit Phase 4 spricht das Terminal ausschließlich mit dem Backend – kein
DB-Zugriff mehr. In `elwasys.properties` stehen dafür:

- **`backend.url`** – Basis-URL des elwasys-Backends (REST-API v1 für
  Login/Geräte/Programme/Ausführungen/Guthaben + ausgehende Wartungs-WebSocket-
  Verbindung für Status/Log/Restart), z. B. `https://backend-host:8080/`.
- **`backend.token`** – das **Standort-Token** dieses Terminals für die API v1
  (bestimmt den Zugriffs-Scope). Ausgestellt über das Backend-`token-cli` bzw.
  komfortabel über `deploy/cutover/02-issue-terminal-tokens.sh --location=<Name>`
  (siehe `deploy/cutover/README.md`, Schritt 3). Das Klartext-Token erscheint
  dort **genau einmal** – sofort in `elwasys.properties` übernehmen.

`location` (Anzeigename) und `portalUrl` schreibt `setup.sh` ebenfalls; der
tatsächliche Zugriffs-Scope hängt am `backend.token`, nicht am `location`-Wert.

## Supervisor-Vertrag (der `run.sh`-Loop)

`setup.sh` generiert `run.sh` seit Phase 6 AP3 als **Supervising-Loop**: eine
Endlosschleife, die das per Symlink `raspi-client.latest.jar` referenzierte Jar im
Vordergrund startet (unveränderte JavaFX-Touch-App, identischer Bedienfluss unter
derselben X-Autologin-`~/.xsession`) und – sobald sich die JVM beendet (Crash oder
gezielt von außen) – nach kurzer Wartezeit den **dann aktuell verlinkten** Jar
erneut startet. Kein systemd.

**Vertrag für Watchdog/Update (AP4/AP5):** Ein externer Neustart = **den laufenden
`java`-Prozess beenden** (z. B. `sudo killall java` oder `pkill -f raspi-client`);
die `run.sh`-Loop relauncht automatisch das aktuell verlinkte Jar. Ein Update
hängt also nur den Symlink `raspi-client.latest.jar` auf das neue
`raspi-client-<version>.jar` um und beendet den `java`-Prozess – die nächste
Iteration liest das Symlink-Ziel **neu** und startet das neue Jar. `killall java`
trifft nur die JVM, nicht den bash-Supervisor (der einmalige `killall`-Cleanup
läuft bewusst **vor** der Schleife, nicht in ihr).

**stdout/stderr der Loop (N3, QA-Nacharbeit):** `run.sh` schreibt `log/stdout`/
`log/errout` seit der Phase-6-Nacharbeit **anhängend** (`>>`/`2>>`), nicht mehr
abschneidend – ein Update-/Crash-Neustart löscht damit nicht mehr das rohe
STDOUT/STDERR des vorigen Laufs (Anwendungslogs laufen ohnehin separat über
`logback.xml`; diese Rohdateien fangen zusätzlich alles, was direkt auf
STDOUT/STDERR landet, z. B. einen JVM-Absturz vor der Logging-Initialisierung –
genau das Postmortem-Artefakt, das nach einem fehlgeschlagenen Auto-Update
gebraucht wird). Gegen unbegrenztes Wachstum rotiert die Loop vor jedem
(Re-)Start die jeweilige Datei einmalig nach `.1`, sobald sie eine Schwelle
überschreitet (`ELWA_LOG_MAX_BYTES`, Default 5 MiB) – einfache Shell-Prüfung,
kein externes `logrotate` auf dem Gerät nötig.

## `update.sh` – neues Client-Jar ausrollen

`deploy/terminal/update.sh` hebt ein **bereits provisioniertes** Terminal auf ein
neues Client-fat-jar, **ohne** das interaktive `setup.sh` erneut zu fahren –
`elwasys.properties`, `logback.xml`, `run.sh` und `~/.xsession` bleiben
unangetastet.

**Aufruf** (genau eine Bezugsquelle):

```bash
# a) Online: Release-Jar von GitHub laden (raspi-client-<tag>.jar, gleiches
#    URL-Muster/Host wie setup.sh install_elwasys)
deploy/terminal/update.sh --version 1.4.0

# b) Offline-Rollout: bereits vorliegendes Jar verwenden
deploy/terminal/update.sh --jar /media/usb/raspi-client-1.4.0.jar
```

Ohne Argument gibt das Skript eine Usage-Meldung aus und beendet mit Exit 1.

**Java-21-Voraussetzung:** Braucht der Versionssprung ein höheres Java als das auf
dem Gerät installierte JRE (Bestandsgeräte tragen ggf. nur Java 17), **zuerst**
[`upgrade-jre.sh`](upgrade-jre.sh) ausführen – siehe „Reihenfolge (Bestandsgerät)"
oben.

**Jar-Layout-Konvention** (hier etabliert, das AP5-Auto-Update/Rollback baut
darauf auf):

| Datei | Bedeutung |
|---|---|
| `raspi-client-<version>.jar` | versionierte Jars, bleiben liegen (kein Löschen) |
| `raspi-client.latest.jar` | Symlink → **aktuell** laufende Version (der `run.sh`-Loop startet genau dieses Symlink) |
| `raspi-client.previous.jar` | Symlink → **zuvor** laufende Version (Rollback-Ziel für AP5) |

**Ablauf** (Symlinks atomar per `ln -sfn` auf relative Basenames):

1. Neues Jar bereitstellen (`--version`: robust erst `.part`, dann `mv`; `--jar`:
   nach `ELWA_ROOT` kopieren) → `raspi-client-<version>.jar`.
2. Bisheriges `latest`-Ziel als `previous` merken (Rollback-Ziel).
3. `latest` auf das neue Jar zeigen.
4. **Neustart gemäß Supervisor-Vertrag**: läuft eine JVM, wird sie beendet – die
   `run.sh`-Loop liest das Symlink-Ziel neu und startet das neue Jar. Läuft keine
   JVM (Terminal/Display aus), sauberer Hinweis statt Fehler; der Supervisor
   startet beim nächsten Lauf ohnehin das jetzt verlinkte Jar.

**Idempotenz:** Zeigt `latest` bereits auf das neue Jar, hängt das Skript nichts
um (`previous` bleibt unangetastet, damit das Rollback-Ziel nicht überschrieben
wird) und stößt nur den Neustart an.

**Env-Overrides** (für lokale Tests / Sonderfälle): `ELWA_ROOT` (Default
`/opt/elwasys`), `ELWA_RESTART_CMD` (Default `sudo killall java`),
`ELWA_JAVA_PGREP` (Default `pgrep -x java`), `ELWA_GITHUB_REPO` (Default
`kabieror/elwasys`).

## Auto-Update mit Rollback (`auto-update-watchdog.sh`, Phase 6 AP5)

`deploy/terminal/auto-update-watchdog.sh` hebt ein Terminal **selbsttätig** auf
eine neue Client-Version und **fällt bei einem fehlgeschlagenen Start automatisch
zurück**. Es ist bewusst die schlanke Shell-/Cron-Variante – **kein systemd, kein
großer Java-Umbau** (Auftraggeber-Entscheidung 2026-07-22, „kein Overkill"). Der
einzige Client-Java-Zusatz ist ein kleiner **Readiness-Marker** (siehe unten).

### Funktionsweise (ein Cron-Lauf)

1. **Aktuelle Version** aus dem `raspi-client.latest.jar`-Symlink ableiten.
2. **Ziel-Version** ermitteln: primär GitHub-Releases-`latest` (gleiches Muster
   wie `setup.sh`/`update.sh`, per `ELWA_LATEST_VERSION_CMD` überschreibbar).
   Existiert die Datei `${ELWA_ROOT}/.update-target`, hat ihr Inhalt **Vorrang**
   – so kann der Betrieb/das Backend ein Update anstoßen, **ohne Backend-Code zu
   ändern** (der GitHub-Poll bleibt der Normalpfad; die Datei wird nach einem
   erfolgreichen Update konsumiert).
3. **Up-to-date** (Ziel == aktuell) → stiller No-op (Exit 0, nur Logdatei).
4. **Lockfile** `${ELWA_ROOT}/.watchdog.lock` (atomar via `mkdir`) verhindert
   parallele Cron-Läufe.
5. Läuft **keine JVM** (Terminal aus), wird das Update auf einen späteren Lauf
   verschoben – **kein erzwungener Start**.
6. **Leerlauf-Gate** (siehe Abschnitt „Leerlauf-Gate" unten): läuft am Standort ein
   Gerät oder war der Bedienfluss sehr kürzlich aktiv, wird dieser Tick
   übersprungen (kein `update.sh`, kein `java`-Kill) – der nächste Cron-Lauf
   versucht es erneut.
7. **Update mit Verifikation** (Ziel neuer): Konfig-Snapshot
   (`elwasys.properties` → `.previous`), `restart_epoch`+Marker-mtime merken,
   `update.sh --version <ziel>` (rotiert `latest`/`previous` + Neustart), dann bis
   zu `ELWA_UPDATE_DEADLINE` Sekunden (Default 180) darauf warten, dass der
   **Readiness-Marker** einen mtime **> `restart_epoch`** bekommt.
8. **Erfolg** → loggen, Snapshot aufräumen, Exit 0.
9. **Fetch-/Download-Fehlschlag ohne jede Änderung** (siehe „B1" unten): schlägt
   `update.sh` fehl, **ohne** dass sich `latest` geändert hat (z. B. Netzwerk-
   /Tag-/Platzproblem beim Download, noch vor jedem Symlink-Wechsel), wird
   **nicht** zurückgerollt und **kein** `java` beendet – nur eine WARNUNG
   geloggt, Exit 1. Der nächste Cron-Lauf versucht den Fetch erneut.
10. **Echter Fehlschlag** (`latest` wurde umgehängt, aber der Readiness-Marker
    erreicht die Deadline nicht) → **ROLLBACK**: `latest` zurück auf das
    `previous`-Ziel, Konfig ggf. aus `.previous` zurückspielen, `java` killen
    (Supervisor relauncht die vorige Version), erneut kurz auf den Marker warten
    (Recovery bestätigen); klarer **FAILURE**-Log auf stderr **und** in
    `${ELWA_ROOT}/log/auto-update-watchdog.log`; Exit != 0.

### B1 – Fetch-Fehlschlag ≠ fehlgeschlagenes Deploy (QA-Nacharbeit)

Ein bloßer Download-/Netzwerkfehler von `update.sh` (schlechtes DNS, abgelaufener/
umbenannter Release-Tag, volle Platte – jeweils **vor** jedem Symlink-Wechsel)
darf **nicht** denselben Rollback-Pfad auslösen wie ein echtes fehlgeschlagenes
Deploy: sonst würde ein bereits gesundes, laufendes Terminal bei jedem Cron-Tick
grundlos auf die (ältere) `previous`-Version heruntergestuft und neu gestartet –
potenziell endlos, solange der Fetch fehlschlägt. Der Watchdog unterscheidet die
beiden Fälle daher anhand des `latest`-Symlink-Ziels vor/nach dem `update.sh`-
Aufruf: hat sich das Ziel **nicht** geändert, war es ein reiner No-op-Fehlschlag
→ WARNUNG + Exit 1, **kein** Symlink-Anfassen, **kein** `java`-Kill. Nur wenn
`latest` tatsächlich umgehängt wurde, der Marker danach aber nicht vorrückt, gilt
es als echter fehlgeschlagener Deploy und der Rollback-Pfad greift.

### Leerlauf-Gate (M1, QA-Nacharbeit)

**Problem:** Der einzige Gate vor einem Update-Neustart war ursprünglich „läuft
überhaupt eine JVM" – kein Blick auf den Bedienzustand des Terminals. Ein Nutzer,
der gerade authentifiziert, ein Programm wählt oder ein Gerät laufen hat, konnte
von einem unbeaufsichtigten Cron-Tick jederzeit unterbrochen werden. Der
Cutover-Runbook-Grundsatz „die Terminal-Charge erst anfassen, wenn ihre Geräte
frei sind" (`deploy/CUTOVER-RUNBOOK.md`) galt bisher nur für die einmalige,
manuelle Migration – nicht für diesen wiederkehrenden, automatischen Mechanismus.

**Umgesetztes Signal:** Vor dem eigentlichen Update prüft der Watchdog (Funktion
`terminal_is_busy`, per `ELWA_IDLE_GATE_ENABLED=1` standardmäßig aktiv):

1. **Laufende Ausführung** – anonyme Geräteübersicht des Standorts
   (`GET /api/v1/devices/overview`, Standort-Token aus `elwasys.properties`,
   dasselbe, das der Client selbst benutzt). Ist irgendein Gerät `occupied:true`,
   gilt der Standort als beschäftigt.
2. **Sehr kürzliche Bedienung** – Alter des Readiness-Marker-mtime
   (`ELWA_RECENT_INTERACTION_SECONDS`, Default 60s): wurde er gerade erst
   aktualisiert (Client hat SELECT_DEVICE gerade erst wieder erreicht), war
   jemand sehr kürzlich am Terminal.

Trifft eines der beiden Signale zu (oder ist der Status nicht ermittelbar –
Backend nicht erreichbar, kein `backend.token` konfiguriert, kaputte Antwort),
wird der Tick **fail-safe übersprungen**: kein `update.sh`-Aufruf, kein
`java`-Kill; der nächste Cron-Lauf prüft erneut. Unsicherheit führt **nie** zum
Durchlassen, nur zum Verschieben.

**Bekannte Restrisiko-Lücke:** Beide Signale decken laufende Ausführungen und
die Zeit unmittelbar nach einer Bedienung ab, **nicht** aber das kurze Fenster
*vor* dem Start einer Ausführung – ein Nutzer, der gerade seine Karte auflegt,
sich authentifiziert oder ein Programm auswählt (noch keine laufende Ausführung,
Marker noch nicht wieder aktualisiert), kann in diesem schmalen Zeitfenster
weiterhin von einem Update-Neustart getroffen werden. Das ist ein bewusst
akzeptiertes Restrisiko (kein client-seitiger Code-Zusatz nötig, siehe
Auftraggeber-Entscheidung „kein Overkill"), keine Erfindung eines fragilen
Zusatzsignals. **Datensicherheit ist davon nicht betroffen** (Backend bleibt die
Quelle der Wahrheit, siehe Phase 4 AP4–AP6), lediglich die UI-Sitzung würde auf
`STARTUP` zurückgesetzt (erneute Anmeldung nötig).

**Betriebsempfehlung:** Den Cron so legen, dass er außerhalb der Hauptnutzungs-
zeiten der jeweiligen Waschküche läuft (z. B. nachts), um die Kollisions-
wahrscheinlichkeit weiter zu senken – das Gate ersetzt umsichtige Zeitplanung
nicht, es reduziert nur das Restrisiko innerhalb eines Ticks. Das Gate lässt
sich für Sonderfälle (erzwungenes Update, kein Backend-Zugriff möglich) über
`ELWA_IDLE_GATE_ENABLED=0` abschalten – dann gilt wieder das alte Verhalten
(nur der JVM-läuft-Check).

### Der Readiness-Marker (`SELECT_DEVICE` → `.terminal-ready`)

Der JavaFX-Client schreibt beim Erreichen des bedienbereiten Zustands
`SELECT_DEVICE` (u. a. beim frischen Start `STARTUP → SELECT_DEVICE`) eine
Marker-Datei mit frischem `mtime` (Klasse
`org.kabieror.elwasys.raspiclient.application.TerminalReadinessMarker`). Pfad:
System-Property `elwasys.readyMarkerFile`, sonst `${user.dir}/.terminal-ready` –
auf dem Gerät `/opt/elwasys/.terminal-ready`, weil `run.sh` `cd $ELWA_ROOT` macht.
Ein mtime-Fortschritt **nach** einem Update ist der Beweis, dass die neue Version
tatsächlich hochgekommen ist. Der Marker-Schreiber ist robust: jeder IO-Fehler
wird gefangen und nur geloggt, **nie** in die UI geworfen – der Bedienfluss bleibt
unverändert. Watchdog-Marker-Pfad per `ELWA_MARKER_FILE` (Default
`${ELWA_ROOT}/.terminal-ready`).

### Cron-Einrichtung

Als **Terminal-User** (der auch den Client fährt), damit `${user.dir}`/Rechte
passen. `sudo killall java` muss ohne Passwort laufen (bei `setup.sh`-Geräten via
sudoers gegeben). Beispiel – alle 30 Minuten:

```cron
# /etc/cron.d/elwasys-watchdog  (oder `crontab -e` des Terminal-Users)
*/30 * * * * pi /opt/elwasys/../deploy/terminal/auto-update-watchdog.sh >> /opt/elwasys/log/auto-update-watchdog.cron.log 2>&1
```

(Pfad zum Skript an die Ablage anpassen; `update.sh` wird per Default **neben**
dem Watchdog gesucht, überschreibbar via `ELWA_UPDATE_SCRIPT`.)

**Empfehlung (siehe „Leerlauf-Gate" oben):** den Cron-Zeitpunkt bewusst in
nutzungsarme Zeiten der jeweiligen Waschküche legen (z. B. nachts), auch wenn
das Leerlauf-Gate bereits die meisten Kollisionen abfängt – das schmale
Restrisiko-Fenster kurz vor dem Start einer Ausführung (Kartenauflegen/Login/
Programmwahl) bleibt sonst bestehen.

**Env-Overrides** (Tests/Sonderfälle): `ELWA_ROOT`, `ELWA_RESTART_CMD`,
`ELWA_JAVA_PGREP`, `ELWA_LATEST_VERSION_CMD`, `ELWA_UPDATE_DEADLINE`,
`ELWA_MARKER_FILE`, `ELWA_UPDATE_SCRIPT`, `ELWA_GITHUB_REPO`,
`ELWA_IDLE_GATE_ENABLED` (Default `1`), `ELWA_DEVICE_OVERVIEW_CMD` (Default:
eingebaute curl-Abfrage gegen `backend.url`/`backend.token`),
`ELWA_DEVICE_OVERVIEW_TIMEOUT` (Default `5`), `ELWA_RECENT_INTERACTION_SECONDS`
(Default `60`).

**Vor einem Java-Versionssprung** zuerst [`upgrade-jre.sh`](upgrade-jre.sh)
ausführen (Java 21) – der Watchdog rollt nur das Jar, nicht das JRE.

**Verifikation:** echte Rollouts wurden **nur trocken** nachgewiesen (Temp-
`ELWA_ROOT`, Fake-Jars, Fake-`java`, Fake-Version-Cmd, kurze Deadline) – keine
echten Downloads/apt/sudo. Details im Abschnitt unten.

## Hinweis zur Verifikation in dieser Umgebung

Die echten apt-/X-Kommandos wurden hier **nicht** ausgeführt (kein armhf-Gerät,
keine echte X-Sitzung). Verifiziert wurden nur: `bash -n` auf allen Skripten und
auf dem generierten `run.sh`, ein tatsächlich ausgeführter Trockentest der
Supervising-Loop (Symlink-Relaunch greift zwischen zwei Iterationen) und ein
Trockentest der Java-Versionsprüfung (gefaktes `java -version` 17 → Fehler,
21 → ok). Für `update.sh` (AP4) wurde in einem Temp-`ELWA_ROOT` mit Fake-Jars und
einem gefakten `run.sh`-Loop (Overrides `ELWA_RESTART_CMD`/`ELWA_JAVA_PGREP`)
nachgewiesen: `latest`/`previous`-Umschaltung, Relaunch der Loop mit der neuen
Version, Idempotenz (erneuter Lauf → No-op, `previous` unverändert) und der
Kein-java-Fall. Der GitHub-Download (`--version`) wurde **nicht** real gegen
github.com ausgeführt. Für `auto-update-watchdog.sh` (AP5) wurde in einem Temp-
`ELWA_ROOT` mit Fake-Jars, gefaktem `java`-Check/-Restart und gefakter
Version-Ermittlung (kurze Deadline) nachgewiesen: Szenario A (gutes Update →
`latest` = neue Version, kein Rollback), Szenario B (Marker bleibt aus → Deadline
→ Rollback auf `previous`, Recovery bestätigt, FAILURE-Log), Szenario C
(up-to-date → stiller No-op) und die Lockfile-Sperre gegen parallele Läufe – ohne
echte Downloads/apt/sudo, ohne hängende Prozesse. Details im Änderungslog
„Phase 6 AP3"/„Phase 6 AP4"/„Phase 6 AP5" in docs/kb/05-migration-plan.md.

**QA-Nacharbeiten (B1/M1/N3, siehe docs/kb/05 Änderungslog „Phase 6 QA-Nacharbeiten"):**
zusätzlich zu obigen Szenarien A–C wurde in derselben Trocken-Umgebung
nachgewiesen: Szenario **C-neu** (`update.sh` scheitert, OHNE `latest` zu ändern
→ WARNUNG + Exit 1, `latest`/`previous` unverändert, **kein** `${ELWA_RESTART_CMD}`
ausgeführt – der B1-Regressionstest) und Szenario **D** (Leerlauf-Gate: Gerät als
`occupied:true` gemeldet bzw. Marker sehr frisch → Update verschoben, Exit 0,
weder `update.sh` noch `${ELWA_RESTART_CMD}` aufgerufen). Für `run.sh` (N3) wurde
zusätzlich nachgewiesen, dass zwei aufeinanderfolgende Läufe `log/stdout`
anhängen statt abzuschneiden, und dass eine künstlich über die Schwelle
vergrößerte Logdatei beim nächsten Loop-Durchlauf nach `.1` rotiert wird, bevor
neu geschrieben wird.
