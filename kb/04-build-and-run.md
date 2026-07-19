# 04 – Build, Ausführung & Deployment

## Build-Reihenfolge

Es gibt **kein** Aggregator-/Parent-POM. Common muss zuerst ins lokale Maven-Repo
installiert werden, dann können Client und Portal es als Dependency auflösen.

```bash
# 1. Common (Bibliothek) installieren
cd Common && mvn install

# 2a. Raspi-Client bauen (fat-jar)
cd ../Client-Raspi && mvn package
#   → target/raspi-client-<version>-jar-with-dependencies.jar

# 2b. Portal bauen (WAR) bzw. lokal starten
cd ../Portal && mvn package        # → target/*.war
#   oder Entwicklungsserver:
mvn jetty:run                      # http://localhost:8080
```

> ⚠️ Versions-Stolperfalle: Portal referenziert `common:0.3.4-SNAPSHOT`, Common trägt aber
> `0.0.0-local-development`. Für lokalen Portal-Build muss die Common-Version passend gesetzt
> oder das Portal-POM angepasst werden. Die CI umgeht das, indem sie beim Release
> `0.0.0-local-development` → Tag-Name ersetzt (nur Common + Client).

## Umgebung (dieser Remote-Container)

- **OS**: Ubuntu 24.04.4 LTS
- **Java**: OpenJDK **21.0.10** (Projekt kompiliert Ziel 8/16 → mit JDK 21 baubar, ggf.
  `--release`-Anpassungen nötig)
- **Maven**: 3.9.11
- Ausgehende HTTPS-Verbindungen laufen über einen Agent-Proxy (CA-Bundle unter
  `/root/.ccr/ca-bundle.crt`, Java-Truststore via `JAVA_TOOL_OPTIONS` gesetzt).

## Laufzeit-Konfiguration

### Client (`elwasys.properties`)
Liegt neben dem JAR (bzw. unter `/opt/elwasys`). Siehe `Client-Raspi/elwasys.example.properties`.
Pflicht: `database.*`, `location`, `portalUrl`. Gateway: entweder `deconz.*` oder `fhem.*`.

Start (aus `setup.sh`, `run.sh`):
```bash
java -Djavafx.platform=gtk \
     -Dlogback.configurationFile=/opt/elwasys/logback.xml \
     -Djavax.net.ssl.trustStore=/opt/elwasys/.truststore \
     -Djavax.net.ssl.trustStorePassword=<pw> \
     -jar raspi-client.latest.jar -verbose
```

### Portal (`/etc/elwaportal/elwaportal.properties`)
Siehe `Portal/elwaportal.example.properties`. Keys: `database.*`, `smtp.*`,
`maintenance.timeout`.

### Datenbank
PostgreSQL, initialisiert über `Common/resources/database-init.sql` (legt DB `elwasys`,
Schema, Rollen `elwaclient1`/`elwaportal`/`elwaapi` und Seed-Daten an).

## Deployment (Produktion)

- **Client**: One-Line-Setup auf frischem Raspberry Pi:
  ```bash
  bash <(curl -s https://raw.githubusercontent.com/kabieror/elwasys/master/Client-Raspi/setup.sh)
  ```
  `setup.sh` installiert Java 17 (BellSoft armhf), UFW-Firewall, deCONZ, lädt das neueste
  Release-JAR, schreibt `elwasys.properties`/`logback.xml`/`run.sh`, richtet Autostart über
  `~/.xsession` ein.
- **Portal**: WAR auf Servlet-Container/Jetty deployen; `elwaportal.properties` bereitstellen.

## CI (GitHub Actions)

`.github/workflows/maven-publish.yml`:
- Trigger: **nur** bei `release: created`
- JDK 17 (Liberica), ersetzt Version im POM/Utilities durch Tag-Namen
- Baut Common → Client-Raspi, lädt das fat-jar als Release-Asset hoch
- **Kein** Test-/Lint-/PR-CI vorhanden → Ansatzpunkt für die Modernisierung.

## Bekannte Build-Risiken

- Vaadin 7 / GWT 2.7 Widgetset-Compilation: langsam, speicherhungrig, alte Repos
  (`maven.vaadin.com`, teils `http://`), ggf. nicht mehr erreichbar.
- Alte Plugin-/Dependency-Versionen (Postgres 9.3-Treiber im Portal, unirest 1.x).
- Gemischte Java-Level (8/16) und gemischte Test-Frameworks (JUnit + TestNG).
