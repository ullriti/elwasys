# Terminal-Runbook (Phase 6 AP3)

Werkzeuge und Ablauf, um ein **Raspberry-Pi-Terminal auf die neue Architektur zu
bringen** (kb/05-migration-plan.md, „Phase 6 – Produktivumschaltung (Cutover)",
Roadmap-Punkt „Terminals neu aufsetzen"). Neue Architektur heißt: **Java 21**,
Datenzugriff ausschließlich über die Backend-REST-API v1 + eine ausgehende
Wartungs-WebSocket-Verbindung (`backend.url`/`backend.token` in
`elwasys.properties`) statt Direkt-DB-Zugriff (seit Phase 4).

**Scope**: die Skripte `upgrade-jre.sh` (AP3) und `update.sh` (AP4) + dieses
Runbook + der zum Supervisor umgebaute `run.sh`-Generator in `Client-Raspi/setup.sh`.
Die echten apt-/X-Schritte laufen NUR auf dem Gerät (armhf, Raspberry Pi OS) und
wurden hier lediglich **trocken/syntaktisch** verifiziert (`bash -n` +
Funktions-/Ablauf-Trockentests, siehe Änderungslog in kb/05). Das optionale
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
**Java-17-Restrisiko** (Risikotabelle in kb/05) für das jeweilige Gerät aufgelöst.

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

**Auto-Update mit Rollback (folgt in AP5):** Ein Watchdog rollt automatisch aus
und fällt bei Startproblemen zurück. Der Rollback braucht dank obiger Konvention
nur **`latest` zurück auf das `previous`-Ziel + `java`-Prozess beenden** – die
Loop startet dann wieder die vorige Version.

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
github.com ausgeführt. Details im Änderungslog „Phase 6 AP3"/„Phase 6 AP4" in
kb/05-migration-plan.md.
