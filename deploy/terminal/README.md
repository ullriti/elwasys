# Terminal-Runbook (Phase 6 AP3)

Werkzeuge und Ablauf, um ein **Raspberry-Pi-Terminal auf die neue Architektur zu
bringen** (kb/05-migration-plan.md, „Phase 6 – Produktivumschaltung (Cutover)",
Roadmap-Punkt „Terminals neu aufsetzen"). Neue Architektur heißt: **Java 21**,
Datenzugriff ausschließlich über die Backend-REST-API v1 + eine ausgehende
Wartungs-WebSocket-Verbindung (`backend.url`/`backend.token` in
`elwasys.properties`) statt Direkt-DB-Zugriff (seit Phase 4).

**Scope dieses Arbeitspakets**: Skript + dieses Runbook + der zum Supervisor
umgebaute `run.sh`-Generator in `Client-Raspi/setup.sh`. Die echten apt-/X-Schritte
laufen NUR auf dem Gerät (armhf, Raspberry Pi OS) und wurden hier lediglich
**trocken/syntaktisch** verifiziert (`bash -n` + Funktions-Trockentests, siehe
Änderungslog in kb/05). Der komfortable Jar-Update-Pfad (`update.sh`) und das
optionale Auto-Update mit Rollback folgen in Phase 6 AP4/AP5.

## Zwei Fälle unterscheiden

| Ausgangslage | Vorgehen |
|---|---|
| **Neues/frisch aufzusetzendes Gerät** (Ersteinrichtung) | Das komplette interaktive `Client-Raspi/setup.sh` – es installiert bereits `bellsoft-java21-runtime-full` (Java 21), lädt das Release-Jar, legt den Symlink `raspi-client.latest.jar` an, schreibt `elwasys.properties` (mit `backend.url`/`backend.token`/`location`/`portalUrl`), `logback.xml`, den **Supervisor-`run.sh`** und die X-Autologin-`~/.xsession`. Ein separater JRE-Upgrade-Schritt ist hier **nicht** nötig. |
| **Bestandsgerät** (aus einem früheren `setup.sh`-Lauf, trägt nur Java 17) | **Zuerst** `deploy/terminal/upgrade-jre.sh` (JRE auf Java 21 anheben), **danach** das neue Client-Jar ausrollen (`update.sh` – folgt in AP4; bis dahin manuell: neues `raspi-client-<version>.jar` neben das alte legen, den Symlink `raspi-client.latest.jar` darauf umhängen und den laufenden `java`-Prozess beenden – der Supervisor relauncht automatisch, siehe „Supervisor-Vertrag" unten). |

## Reihenfolge (Bestandsgerät): Java 21 ZUERST

Das Client-fat-jar baut seit Phase 1 mit **Sprachlevel 21 / Bytecode-Major 65**.
Ein im Feld noch vorhandenes **Java-17-JRE** bricht ein solches Jar beim Start mit
`UnsupportedClassVersionError` ab. Deshalb gilt zwingend:

```bash
# 1) JRE auf Java 21 anheben (idempotent; verifiziert danach java -version >= 21)
deploy/terminal/upgrade-jre.sh

# 2) ERST DANACH ein mit Sprachlevel 21 gebautes Release-Jar ausrollen
#    (update.sh folgt in AP4; bis dahin Symlink umhängen + java-Prozess beenden).
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

## Hinweis zur Verifikation in dieser Umgebung

Die echten apt-/X-Kommandos wurden hier **nicht** ausgeführt (kein armhf-Gerät,
keine echte X-Sitzung). Verifiziert wurden nur: `bash -n` auf allen Skripten und
auf dem generierten `run.sh`, ein tatsächlich ausgeführter Trockentest der
Supervising-Loop (Symlink-Relaunch greift zwischen zwei Iterationen) und ein
Trockentest der Java-Versionsprüfung (gefaktes `java -version` 17 → Fehler,
21 → ok). Details im Änderungslog „Phase 6 AP3" in kb/05-migration-plan.md.
