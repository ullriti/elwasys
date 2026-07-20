# 04 – Build, Ausführung & Deployment

## Build-Reihenfolge

Seit Phase 1 (2026-07-20) gibt es ein **Aggregator-/Parent-POM** (`/pom.xml`,
`packaging=pom`, Module `Common`/`Client-Raspi`/`Portal`, gemeinsame Version
`0.0.0-local-development`, zentrale `dependencyManagement` für
postgresql/logback/slf4j-api/commons-email, `maven.compiler.release=21` als
Default). Wichtig: Ein isoliertes `mvn -f Common/pom.xml install` installiert
**nur** das `common`-Artefakt ins lokale Repo, **nicht** die Parent-POM selbst –
andere Module, die `common` später als Dependency auflösen (Client-Raspi,
Portal), scheitern dann mit `Could not find artifact
org.kabieror.elwasys:elwasys-parent:pom:...`. Deshalb immer über den
Root-Reactor bauen, wenn Common isoliert installiert werden soll:

```bash
# 1. Common (+ Parent-POM) installieren – WICHTIG: über den Root-Reactor,
#    nicht "mvn -f Common/pom.xml install" allein (siehe Hinweis oben)
mvn -f pom.xml install -pl Common -am -DskipTests

# 2a. Raspi-Client bauen (fat-jar)
mvn -f Client-Raspi/pom.xml package
#   → target/raspi-client-<version>-jar-with-dependencies.jar

# 2b. Portal bauen (WAR) bzw. lokal starten
mvn -f Portal/pom.xml package      # → target/*.war
#   oder Entwicklungsserver:
mvn -f Portal/pom.xml jetty:run    # http://localhost:8080

# Alternative: kompletter Reactor-Build aller drei Module in einem Aufruf
mvn install   # von der Repo-Wurzel aus
```

Portal friert sein javac-Sprachlevel weiterhin explizit auf 1.8
(`project.source.version`/`-target.version` in `Portal/pom.xml`) ein –
unabhängig vom `maven.compiler.release=21`-Default des Parents (Vaadin 7/GWT 2.7
ist nicht für neuere Sprachlevel getestet). Der Build-JDK selbst ist weiterhin 21.

> ✅ Portal-Build in der Remote-Umgebung repariert (2026-07-19), siehe unten.

### Portal-Build: durchgeführte Reparaturen (2026-07-19)

Der Portal-Build war mehrfach kaputt. Behoben in `Portal/pom.xml` +
`WaschportalUI.java` + `DeviceWindow.java`:

1. **Versionskonflikt**: `common`-Dependency `0.3.4-SNAPSHOT` → `0.0.0-local-development`
   (wie von Common/Client/CI verwendet).
2. **Tote HTTP-Repos entfernt**: `vaadin-addons` (http, von Maven 3.9 geblockt),
   `vaadin-snapshots` (403), `codehaus-snapshots` (http, tot). Alle benötigten Vaadin-7-
   Artefakte liegen auf Maven Central.
3. **Widgetset-Kompilation entfernt**: `vaadin-maven-plugin` + `gwt-maven-plugin`-Blöcke
   raus. Die App nutzt nur `com.vaadin.DefaultWidgetSet` (vorkompiliert in
   `vaadin-client-compiled`); `MyAppWidgetset` erbt nur davon, keine Add-ons. Servlet in
   `WaschportalUI` auf `com.vaadin.DefaultWidgetSet` gestellt. → kein langsamer GWT-Compile.
4. **`maven-war-plugin` 2.3 → 3.4.0**: 2.3 ist mit JDK 21 inkompatibel
   (`module java.base does not "opens java.util"`).
5. **PostgreSQL-Treiber `9.3-1103` → `42.6.0`**: der alte Treiber kann kein
   SCRAM-SHA-256 (Default-Auth bei PostgreSQL ≥ 14).
6. **API-Drift behoben**: `DeviceWindow` gegen die aktuelle `Common.Device`-API
   aktualisiert (deCONZ-UUID-Feld ergänzt: Formularfeld + create/modify/edit).
7. **Jetty-Plugin `9.2.3` → `9.4.53`**: JDK-21-tauglich, weiterhin `javax.servlet`
   (passt zu Vaadin 7).

**Verifiziert**: `mvn package` erzeugt das WAR; `mvn jetty:run` liefert die Vaadin-Login-
Seite (HTTP 200, Titel „Waschportal", DefaultWidgetSet, DB-Verbindung ok).

### Portal lokal starten (Remote-Umgebung)

```bash
# 1. PostgreSQL starten & DB initialisieren
sudo pg_ctlcluster 16 main start
sudo -u postgres psql -f Common/resources/database-init.sql
sudo -u postgres psql -c "ALTER USER elwaportal WITH PASSWORD 'elwaportal';"

# 2. Config bereitstellen (/etc/elwaportal/elwaportal.properties)
#    database.user=elwaportal, database.password=elwaportal, database.name=elwasys

# 3. Common (+ Parent-POM) installieren, Portal starten
mvn -f pom.xml install -pl Common -am -DskipTests
mvn -f Portal/pom.xml jetty:run        # http://localhost:8080  (Login: admin/admin)
```

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

`.github/workflows/ci.yml` *(seit 2026-07-20)*:
- Trigger: jeder Pull Request + Pushes auf `master`
- 3 parallele Jobs (Common / Client / Portal): Build + Tests, spiegeln die lokalen
  Runner-Skripte (`run-ui-tests.sh` etc., siehe kb/06)

`.github/workflows/maven-publish.yml` (Release) *(seit 2026-07-20: Parent-POM-Versionierung)*:
- Trigger: **nur** bei `release: created`
- JDK 17 (Liberica)
- `mvn versions:set -DnewVersion=<tag>` im Root setzt die Version in Parent-POM
  **und** allen drei Modulen konsistent (kein sed-Hack über mehrere POMs mehr);
  `Utilities.APP_VERSION` ist eine reine Java-Konstante und bleibt per
  `sed -i -E` gesetzt
- Reactor-Build `mvn install -pl Common,Client-Raspi -am` (installiert dabei
  auch die neu versionierte Parent-POM), lädt das fat-jar als Release-Asset hoch

## Bekannte Build-Risiken

- Vaadin 7 / GWT 2.7 Widgetset-Compilation: langsam, speicherhungrig, alte Repos
  (`maven.vaadin.com`, teils `http://`), ggf. nicht mehr erreichbar.
- Alte Plugin-/Dependency-Versionen (Postgres 9.3-Treiber im Portal, unirest 1.x).
- Gemischte Java-Level (8/16) und gemischte Test-Frameworks (JUnit + TestNG).
