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

# 2c. Backend bauen (Spring-Boot-Jar, seit Phase 2 AP1) – der main-Code hat keinen
#     Common-Bezug, daher reicht für "package"/"package -DskipTests" ein direkter
#     Aufruf ohne -am. Für "mvn test -pl backend" wird Common vorher benötigt (seit AP2
#     test-scope-Dependency für Alt-vs-Neu-Vergleichstests, siehe unten).
mvn -f pom.xml package -pl backend
#   → target/elwasys-backend.jar (ausführbar: java -jar backend/target/elwasys-backend.jar)

# Alternative: kompletter Reactor-Build aller vier Module in einem Aufruf
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

### Backend bauen, testen, lokal starten (seit Phase 2 AP1, JPA/Services seit AP2, REST-API/WS seit AP4)

Neues Modul `backend/` (Spring Boot 3.x, siehe kb/01-architecture.md, kb/03-modules.md).
Läuft zur Laufzeit weiterhin unabhängig von Common/Client-Raspi/Portal (eigenes
Datenmodell, keine Laufzeit-Abhängigkeit) – seit AP2 hat es aber eine **test-scope**-
Abhängigkeit auf `common` (Alt-vs-Neu-Vergleichstests, siehe kb/05-migration-plan.md), die
für den Testklassenpfad in der lokalen Maven-Repo verfügbar sein muss:

```bash
# Common (+ Parent-POM) erst installieren, sonst schlägt "mvn test -pl backend" beim
# Auflösen der test-scope-Abhängigkeit auf common fehl (Muster wie bei Client-Raspi/Portal):
mvn -f pom.xml install -pl Common -am -DskipTests

# Bauen (nur kompilieren/packen, ohne Tests) - reicht ohne obigen Schritt, da main-Code
# keinen common-Bezug hat:
mvn -f pom.xml package -pl backend -DskipTests

# Tests: siehe unten – Testcontainers (Default) braucht einen Docker-Daemon.
```

**Tests – zwei Wege, je nachdem ob ein Docker-Daemon verfügbar ist:**

- **Mit Docker (z. B. lokaler Rechner, GitHub-Actions-CI)**: Testcontainers startet die
  PostgreSQL-Instanz automatisch, kein Setup nötig (Common muss trotzdem vorher installiert
  sein, s. o.):
  ```bash
  mvn -f pom.xml test -pl backend
  ```
- **Ohne Docker (diese Remote-Sandbox – verifiziert: `docker ps` scheitert mit „no such file
  or directory“, kein laufender Daemon)**: Override auf das lokale PostgreSQL 16 über die
  Umgebungsvariable `ELWASYS_TEST_JDBC_URL` (+ `ELWASYS_TEST_DB_USER`/`_DB_PASSWORD`, siehe
  `backend/src/test/.../support/TestPostgres.java`). Das mitgelieferte Skript übernimmt das
  (Muster: `Client-Raspi/run-ui-tests.sh`, inkl. des Common-Installationsschritts oben):
  ```bash
  backend/run-backend-tests.sh
  ```
  Legt eine frische Testdatenbank (`elwasys_backend_it`) auf dem lokalen Cluster an und führt
  `mvn test -pl backend` mit dem Override aus. **In dieser Umgebung so verifiziert: 27/27
  Tests grün** (2 aus AP1 + 25 aus AP2: JPA-Entities/Repositories, Services
  `PricingService`/`CreditService`/`PermissionService`/`ExecutionService`, siehe
  kb/05-migration-plan.md Änderungslog AP2).

**Flyway-Baseline-Verifikation** (Schema-Äquivalenz Alt-Weg ↔ Flyway, `baselineOnMigrate`
gegen eine Bestands-DB) – dokumentiertes, reproduzierbares Skript, kein Dauertest:
```bash
backend/verify-schema-baseline.sh
```
Details/Ergebnis siehe kb/02-data-model.md und kb/05-migration-plan.md (Änderungslog, Phase 2
AP1).

**Lokal starten** (Konfiguration siehe `backend/src/main/resources/application.yml`, per
Umgebungsvariable überschreibbar):
```bash
sudo pg_ctlcluster 16 main start
ELWASYS_DB_URL=jdbc:postgresql://localhost:5432/elwasys \
ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=elwaportal \
java -jar backend/target/elwasys-backend.jar
# Health-Check:
curl http://localhost:8080/actuator/health
```
Gegen eine über `database-init.sql` angelegte Bestands-DB migriert Flyway beim ersten Start
per `baselineOnMigrate` (kein DDL, keine Datenänderung); gegen eine leere DB durchläuft Flyway
die Baseline-Migration `V1__baseline_schema_0_4_0.sql` normal.

**Backend-Tests seit AP4**: `backend/run-backend-tests.sh` führt jetzt **96/96** Tests aus (52
aus AP1–AP3 + 44 neu aus AP4: Standort-Token-Auth, REST-API v1, WebSocket-Endpunkt – siehe
kb/05-migration-plan.md Änderungslog).

**Standort-Tokens erzeugen/widerrufen (AP4, kein Admin-UI vor Phase 3)** – über das Profil
`token-cli` (`application-token-cli.yml` setzt `spring.main.web-application-type: none`, der
Prozess führt nur den `TerminalTokenCliRunner` aus und beendet sich danach von selbst):
```bash
# Neues Token für einen Standort erzeugen (Standortname muss existieren, z.B. "Default"):
ELWASYS_DB_URL=jdbc:postgresql://localhost:5432/elwasys \
ELWASYS_DB_USER=elwaportal ELWASYS_DB_PASSWORD=elwaportal \
java -jar backend/target/elwasys-backend.jar \
    --spring.profiles.active=token-cli \
    --location=Default \
    --label=terminal-kueche   # optional, rein informativ

# Ausgabe zeigt das Klartext-Token GENAU EINMAL - sofort in die Terminal-Konfiguration
# übernehmen, es wird nirgends gespeichert und kann nicht erneut angezeigt werden.

# Altes Token widerrufen (z.B. nach Rotation auf ein neues Token):
java -jar backend/target/elwasys-backend.jar \
    --spring.profiles.active=token-cli \
    --revoke-token-id=<Id aus der Erzeuge-Ausgabe>
```
Details/Design (Hash statt Klartext, mehrere aktive Tokens pro Standort für
ausfallfreie Rotation, `Authorization: Bearer <token>`-Header) siehe kb/03-modules.md und
kb/05-migration-plan.md.

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

`.github/workflows/ci.yml` *(seit 2026-07-20, JDK-Version am 2026-07-20 im
Phase-1-QA-Review korrigiert; Backend-Job seit Phase 2 AP1, 2026-07-20)*:
- Trigger: jeder Pull Request + Pushes auf `master`
- 4 parallele Jobs (Common / Client / Portal / Backend): Build + Tests, spiegeln die lokalen
  Runner-Skripte (`run-ui-tests.sh` etc., siehe kb/06) bzw. für Backend
  `backend/run-backend-tests.sh` als lokales Analogon
- **JDK 21** (Liberica) in **allen vier** Jobs – nicht mehr JDK 17: Seit Phase 1
  verlangt der Parent-POM-Default `maven.compiler.release=21` für Common/
  Client-Raspi; ein JDK 17 kann `--release 21` nicht bedienen
  (`invalid target release: 21`). Da Common/Client/Portal-Jobs Common zuerst bauen
  (`mvn -f pom.xml install -pl Common -am`), brauchen sie ein >= 21-JDK, obwohl Portal selbst
  weiterhin mit Sprachlevel 1.8 kompiliert. Der Backend-Job braucht JDK 21 unabhängig davon,
  da `backend` selbst Java 21 nutzt.
- **Backend-Job**: nutzt **Testcontainers** (nicht den Local-PG-Ansatz des Client-Jobs), weil
  GitHub-Actions-`ubuntu-24.04`-Runner einen Docker-Daemon mitbringen (anders als diese
  Sandbox-Entwicklungsumgebung, siehe kb/07-cloud-init.md) – das ist der von Spring Boot
  standardmäßig vorgesehene Testweg und braucht dort kein manuelles DB-Setup. `backend` hat
  keine Reactor-Abhängigkeit auf `common`, der Job baut daher nur `-pl backend` ohne `-am`.

`.github/workflows/maven-publish.yml` (Release) *(seit 2026-07-20: Parent-POM-Versionierung;
JDK-Version am 2026-07-20 im Phase-1-QA-Review korrigiert)*:
- Trigger: **nur** bei `release: created`
- **JDK 21** (Liberica) – aus demselben Grund wie oben (`mvn install -pl
  Common,Client-Raspi -am` baut Common mit, das Sprachlevel 21 verlangt)
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
- **Raspi-Terminal-Laufzeit vs. Build-Sprachlevel**: `setup.sh` installiert seit
  Phase 1 `bellsoft-java21-runtime-full` (armhf) statt `-java17-`, weil das
  Client-Raspi-fat-jar seit dem Java-21-Sprachlevel-Sprung (s.o.) Bytecode
  Major-Version 65 erzeugt und auf einem Java-17-JRE mit
  `UnsupportedClassVersionError` fehlschlagen würde. Wurde im Phase-1-QA-Review
  gefunden (Gefahr: bestehende, bereits mit `setup.sh` provisionierte
  Raspberry-Pi-Terminals laufen noch mit dem alten Java-17-JRE – ein
  Fat-Jar-Update ohne erneuten `setup.sh`-Lauf bzw. manuelles JRE-Upgrade auf
  diesen Geräten würde das Terminal beim Start crashen lassen). Für neu
  provisionierte/aktualisierte Terminals ist das jetzt behoben; ein
  JRE-Upgrade-Pfad für bereits im Feld befindliche Geräte ist **nicht**
  Bestandteil dieses Fixes und sollte vor dem nächsten Release explizit
  geklärt werden (z. B. in `setup.sh`/Update-Doku ergänzen).
