# 04 – Build, Ausführung & Deployment

## Build-Reihenfolge

Seit Phase 1 (2026-07-20) gibt es ein **Aggregator-/Parent-POM** (`/pom.xml`,
`packaging=pom`, Module `Common`/`Client-Raspi`/`backend`, gemeinsame Version
`0.0.0-local-development`, zentrale `dependencyManagement` für
postgresql/logback/slf4j-api/commons-email, `maven.compiler.release=21` als
Default). Wichtig: Ein isoliertes `mvn -f Common/pom.xml install` installiert
**nur** das `common`-Artefakt ins lokale Repo, **nicht** die Parent-POM selbst –
andere Module, die `common` später als Dependency auflösen (Client-Raspi,
backend), scheitern dann mit `Could not find artifact
org.kabieror.elwasys:elwasys-parent:pom:...`. Deshalb immer über den
Root-Reactor bauen, wenn Common isoliert installiert werden soll:

```bash
# 1. Common (+ Parent-POM) installieren – WICHTIG: über den Root-Reactor,
#    nicht "mvn -f Common/pom.xml install" allein (siehe Hinweis oben)
mvn -f pom.xml install -pl Common -am -DskipTests

# 2a. Raspi-Client bauen (fat-jar)
mvn -f Client-Raspi/pom.xml package
#   → target/raspi-client-<version>-jar-with-dependencies.jar

# 2b. Backend bauen (Spring-Boot-Jar, seit Phase 2 AP1) – der main-Code hat keinen
#     Common-Bezug, daher reicht für "package"/"package -DskipTests" ein direkter
#     Aufruf ohne -am. Für "mvn test -pl backend" wird Common vorher benötigt (seit AP2
#     test-scope-Dependency für Auth-Parity-Tests, siehe unten).
mvn -f pom.xml package -pl backend
#   → target/elwasys-backend.jar (ausführbar: java -jar backend/target/elwasys-backend.jar)

# Alternative: kompletter Reactor-Build aller drei Module in einem Aufruf
mvn install   # von der Repo-Wurzel aus
```

> **Phase 5 AP1 (2026-07-21)**: das Alt-Portal-Modul (`Portal/`, Vaadin 7 WAR) wurde
> komplett aus dem Repo entfernt (war seit Phase 3 AP6 nur noch als CI-Build-Ziel ohne
> eigenes E2E vorhanden, siehe kb/03-modules.md). Root-Reactor jetzt 3 statt 4 Module.

### Backend bauen, testen, lokal starten (seit Phase 2 AP1, JPA/Services seit AP2, REST-API/WS seit AP4)

Neues Modul `backend/` (Spring Boot 3.x, siehe kb/01-architecture.md, kb/03-modules.md).
Läuft zur Laufzeit unabhängig von Common/Client-Raspi (eigenes Datenmodell, keine
Laufzeit-Abhängigkeit) – seit AP2 hat es aber eine **test-scope**-Abhängigkeit auf `common`
(seit Phase 5 AP1 nur noch für die Auth-Parity-Tests, siehe kb/05-migration-plan.md), die
für den Testklassenpfad in der lokalen Maven-Repo verfügbar sein muss:

```bash
# Common (+ Parent-POM) erst installieren, sonst schlägt "mvn test -pl backend" beim
# Auflösen der test-scope-Abhängigkeit auf common fehl (Muster wie bei Client-Raspi):
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

**Achtung seit Phase 3 AP1 (Vaadin-Flow-Portal-Grundgerüst)**: der obige Start (Dev-Modus,
`vaadin.productionMode` nicht gesetzt) scheitert in DIESER Sandbox-Umgebung beim
Vaadin-Servlet-Init mit einer `LicenseException` – Vaadin 24.10.x verlangt im Dev-Modus einen
Online-Lizenzcheck gegen vaadin.com, den diese Umgebung mangels Netzwerkzugriff nicht
bedienen kann (Details/Klärungsbedarf siehe kb/05-migration-plan.md, Risikotabelle + „Offene
Fragen"). Betrifft NICHT `backend/run-backend-tests.sh` (erzwingt
`vaadin.productionMode=true`, siehe kb/03-modules.md, Abschnitt „Portal-UI (Vaadin Flow)“).
Für einen echten produktiven Start mit funktionierendem Frontend-Bundle:
`mvn -f backend/pom.xml package -Pproduction` (baut das Bundle, braucht npm/Internetzugang
zum npm-Registry, aber KEINEN Vaadin-Lizenzcheck – der greift nur im Dev-Modus).

**Wichtiger Fund (Phase 4 AP4, 2026-07-21)**: diese Einschränkung betrifft nicht nur den
interaktiven Dev-Modus-Start, sondern JEDEN länger laufenden, real gestarteten Backend-
Prozess ohne `-Pproduction` – auch einen, der zunächst erfolgreich hochfährt (Actuator-Health
meldet „UP"). Der Dev-Modus-Lizenzcheck wird über Tomcats „deferred load-on-startup" erst
NACH dem sichtbaren Start ausgelöst und hängt in dieser Sandbox mangels Netzwerkzugriff auf
vaadin.com ca. 60 Sekunden fest, bevor er mit einer `LicenseException` den kompletten
Spring-Kontext (Tomcat-Connector + Hikari-Pool) einreißt. Das hat konkret die
Client-Raspi-Testharness getroffen: `Client-Raspi/ci-support/start-test-backend.sh` baute
den Test-Backend-Jar ursprünglich ohne `-Pproduction`, wodurch `run-client-e2e.sh` ab der
~7. Testklasse (kumulierte Laufzeit >60s) deterministisch mit „Backend nicht erreichbar"
abbrach – siehe kb/05-migration-plan.md, Änderungslog „Phase 4 AP4" für die vollständige
Ursachenanalyse. Fix: `start-test-backend.sh` baut jetzt ebenfalls mit `-Pproduction`
(dieselbe Umgehung wie `backend/e2e/scripts/start-backend.sh`, siehe kb/06-ui-tests.md).
**Merksatz für künftige Arbeitspakete**: jeder Testharness-/Deployment-Skript-Start eines
echten, längere Zeit laufenden Backend-Prozesses in dieser Sandbox braucht `-Pproduction` –
ein `mvn package -DskipTests` allein reicht nur für kurzlebige Aufrufe (z. B. `token-cli`,
das sich nach wenigen Sekunden selbst beendet und daher nie in die Nähe des ~60s-Fensters
kommt).

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

**Seit Phase 4 AP5 (2026-07-21, Fernwartung umgedreht)**: `backend.url` (Basis-URL des
Backends) + `backend.token` (Standort-Token, erzeugt über `token-cli`, siehe „Standort-Tokens
erzeugen/widerrufen" unten) sind die **einzigen** Zugangsdaten, die der Client noch braucht –
sie bedienen sowohl die REST-API v1 (seit Phase 4 AP4) als auch die ausgehende
Fernwartungs-WebSocket-Verbindung (`ws/TerminalWebSocketClient`, seit Phase 4 AP5, siehe
kb/03-modules.md). **`database.*` und `maintenance.server`/`maintenance.port`/
`maintenance.ip` entfallen vollständig** – der Client hat keinen Direkt-DB-Zugriff mehr und
lauscht nicht mehr als Server (bis Phase 4 AP4 galt `database.*` noch transitional für die
mittlerweile entfernte Fernwartungs-Registrierung `LocationManager`). Weitere Keys: `location`
(nur noch Anzeigename), `portalUrl`. Gateway: entweder `deconz.*` oder `fhem.*`. Die zuvor
hier dokumentierten `smtp.*`-Keys entfallen ebenfalls (Benachrichtigungsversand läuft seit AP4
zentral über das Backend, siehe kb/03-modules.md „Benachrichtigungsdienst"). **Seit Phase 4
AP6**: `offline.pollIntervalSeconds` (Default 20) steuert das Intervall des periodischen
Offline-Abgleichs (Snapshot-Aktualisierung + Journal-Replay, siehe kb/03-modules.md
„Offline-Robustheit (AP6)").

Start (aus `setup.sh`, `run.sh`):
```bash
java -Djavafx.platform=gtk \
     -Dlogback.configurationFile=/opt/elwasys/logback.xml \
     -jar raspi-client.latest.jar -verbose
```
(Der `-Djavax.net.ssl.trustStore*`-Flag samt Truststore-Erzeugung in `setup.sh` ist seit Phase
4 AP5 entfallen – er diente ausschließlich der Verifikation des TLS-Zertifikats der jetzt
entfallenen Datenbankverbindung.)

### Datenbank
PostgreSQL, initialisiert über `Common/resources/database-init.sql` (legt DB `elwasys`,
Schema, Rollen `elwaclient1`/`elwaportal`/`elwaapi` und Seed-Daten an).

## Deployment (Produktion)

- **Client**: One-Line-Setup auf frischem Raspberry Pi:
  ```bash
  bash <(curl -s https://raw.githubusercontent.com/kabieror/elwasys/master/Client-Raspi/setup.sh)
  ```
  `setup.sh` installiert Java 21 (BellSoft armhf, `bellsoft-java21-runtime-full`, siehe
  „Bekannte Build-Risiken" unten), UFW-Firewall, deCONZ, lädt das neueste Release-JAR,
  schreibt `elwasys.properties`/`logback.xml`/`run.sh`, richtet Autostart über
  `~/.xsession` ein. **Seit Phase 4 AP4**: fragt interaktiv Backend-URL + Standort-Token
  statt Datenbank-/SMTP-Zugangsdaten ab (Datenbankzugang bleibt als transitionale Zusatzfrage
  für die Fernwartungs-Registrierung bestehen, siehe „Client" oben und
  kb/05-migration-plan.md).
- **Backend** (seit Phase 2 AP6, siehe kb/05-migration-plan.md): Container-Image
  (`backend/Dockerfile`), Betrieb per docker-compose oder Kubernetes/Helm - siehe unten.

### Backend: Container-Image bauen

`backend/Dockerfile` (Build-Kontext ist die **Repo-Wurzel**, nicht `backend/` - der Build
braucht die Parent-POM und `Common`, siehe den bekannten Root-Reactor-Fallstrick oben):
```bash
docker build -f backend/Dockerfile -t elwasys-backend:local .
```
Multi-Stage: Maven-Build (Root-Reactor, zwei Aufrufe wie oben dokumentiert: erst
`install -pl Common -am -DskipTests`, dann `package -pl backend -DskipTests`) → schlankes
`eclipse-temurin:21-jre-jammy`-Runtime-Image, non-root User (UID/GID 1000), `HEALTHCHECK`
gegen `/actuator/health`. `.dockerignore` an der Repo-Wurzel hält den Build-Kontext klein
(Client-Raspi-Quellcode wird für den Backend-Build nicht gebraucht, nur dessen `pom.xml`,
damit Maven den Reactor parsen kann).

### Backend: docker-compose

`deploy/compose/` (Backend + eine mitgelieferte PostgreSQL-16-Instanz für Neuinstallationen):
```bash
cd deploy/compose
cp .env.example .env   # Platzhalter durch echte Werte ersetzen
docker compose up -d --build
curl http://localhost:8080/actuator/health
```
Eine leere Datenbank wird beim ersten Start vollständig per Flyway-Baseline-Migration
angelegt (kein gemountetes `database-init.sql` - würde sich mit dem bereits per
`POSTGRES_DB`/`_USER` angelegten Datenbanknamen beißen und wäre doppelte Schemaverwaltung
neben Flyway). Variante **„Anbindung an eine Bestands-DB"**: `ELWASYS_DB_URL`/`_USER`/
`_PASSWORD` in `.env` auf die externe Instanz setzen und nur den `backend`-Service starten
(`docker compose up backend`, ohne den mitgelieferten `postgres`-Service) -
`baselineOnMigrate` übernimmt eine bestehende Alt-Weg-DB unverändert (siehe
kb/02-data-model.md).

TLS-Konzept Compose: das Basis-File terminiert selbst kein TLS (reiner HTTP-Zugriff, gedacht
für lokale Tests oder Betrieb hinter einem bereits vorhandenen externen Reverse Proxy).
Optionales TLS-Overlay mit Caddy (automatisches Let's-Encrypt-Zertifikat):
```bash
docker compose -f docker-compose.yml -f docker-compose.proxy.yml up -d --build
```

### Backend: Kubernetes / Helm Chart

`deploy/helm/elwasys-backend/` (Deployment, Service, Ingress, ConfigMap/Secret getrennt,
Liveness/Readiness gegen `/actuator/health`). `values.yaml` ist zugleich die
Values-Dokumentation (jeder Abschnitt kommentiert). Der Chart bringt bewusst **kein**
PostgreSQL-Sub-Chart mit - externe/bereits vorhandene DB ist der dokumentierte Regelfall
(Begründung in `values.yaml` unter `database:` und in kb/05-migration-plan.md
„Entscheidungen"); für Neuinstallationen ohne vorhandene DB wird empfohlen, z. B. das
Bitnami-`postgresql`-Chart separat zu installieren und Service/Secret in `values.yaml`
einzutragen:
```bash
helm install elwasys-db bitnami/postgresql -n elwasys \
  --set auth.database=elwasys --set auth.username=elwaportal

helm install elwasys deploy/helm/elwasys-backend -n elwasys \
  --set secret.password=<DB-Passwort> \
  --set database.host=elwasys-db-postgresql.elwasys.svc.cluster.local
```
TLS über einen bereits im Cluster vorhandenen Ingress-Controller + entweder eine
cert-manager-`ClusterIssuer`-Annotation (automatisch) oder ein selbst verwaltetes
TLS-Secret (siehe `values.yaml` unter `ingress:`):
```bash
helm install elwasys deploy/helm/elwasys-backend -n elwasys \
  --set secret.password=<DB-Passwort> \
  --set ingress.enabled=true \
  --set ingress.hosts[0].host=elwasys.example.com
```
Validierung: `helm lint`/`helm template` (kein `helm` in dieser Sandbox-Umgebung
vorinstalliert - über `go install helm.sh/helm/v3/cmd/helm@v3.15.4` beschafft, siehe
kb/05-migration-plan.md AP6).

### Backend: Standort-Tokens im Container erzeugen/widerrufen

Dasselbe Image, Profil `token-cli` (siehe oben, „Standort-Tokens erzeugen/widerrufen"),
gegen dieselbe Datenbank wie das laufende Deployment - als Einmal-Container, nicht per
`exec` in den laufenden Webserver-Container:
```bash
# docker-compose:
docker compose -f deploy/compose/docker-compose.yml run --rm backend \
    --spring.profiles.active=token-cli --location=Default --label=terminal-kueche

# Kubernetes (siehe auch "helm install ... " NOTES.txt):
kubectl run elwasys-token-cli --rm -it --restart=Never -n elwasys \
    --image=<dasselbe Image wie im Deployment> \
    --env="ELWASYS_DB_URL=..." --env="ELWASYS_DB_USER=..." --env="ELWASYS_DB_PASSWORD=..." \
    -- --spring.profiles.active=token-cli --location=Default --label=terminal-kueche
```
Das Klartext-Token erscheint GENAU EINMAL in der Ausgabe.

## CI (GitHub Actions)

`.github/workflows/ci.yml` *(seit 2026-07-20, JDK-Version am 2026-07-20 im
Phase-1-QA-Review korrigiert; Backend-Job seit Phase 2 AP1, 2026-07-20; backend-e2e-Job seit
Phase 3 AP6, 2026-07-21; das frühere `portal-legacy-build`-Job mitsamt dem Alt-Portal-Modul
in Phase 5 AP1, 2026-07-21 entfernt – siehe kb/05-migration-plan.md, kb/06-ui-tests.md)*:
- Trigger: jeder Pull Request + Pushes auf `master`
- 4 parallele Jobs: **common** / **client** (inkl. Cross-Component-Suite P21/P22) /
  **backend-e2e** / **backend** – Build + Tests, spiegeln die
  lokalen Runner-Skripte (`run-ui-tests.sh` etc., siehe kb/06) bzw. für Backend
  `backend/run-backend-tests.sh` als lokales Analogon.
- **JDK 21** (Liberica) in **allen vier** Jobs – nicht mehr JDK 17: Seit Phase 1
  verlangt der Parent-POM-Default `maven.compiler.release=21` für alle Module. Ein JDK 17
  kann `--release 21` nicht bedienen (`invalid target release: 21`). Jeder Job baut Common
  zuerst (`mvn -f pom.xml install -pl Common -am`), braucht also ein >= 21-JDK.
- **backend-e2e** (seit Phase 3 AP6, 2026-07-21, fachlicher Nachfolger des früheren
  `portal`-Jobs bzw. des seit Phase 5 AP1 entfernten Alt-Portal-Moduls): Playwright-E2E
  (P1–P20) gegen das neue, ins Backend eingebettete Vaadin-Flow-Portal –
  `backend/e2e/scripts/start-backend.sh` baut das Backend-Jar im Produktionsmodus
  (`mvn package -Pproduction`, kein Vaadin-Lizenzcheck-Show-Stopper, siehe unten) und startet
  es gegen eine frische, dedizierte PostgreSQL-Datenbank. Details/Selektor-Strategie/
  Test-Status in kb/06-ui-tests.md.
- **backend-Job**: nutzt **Testcontainers** (nicht den Local-PG-Ansatz der Client-/
  backend-e2e-Jobs), weil GitHub-Actions-`ubuntu-24.04`-Runner einen Docker-Daemon mitbringen
  (anders als diese Sandbox-Entwicklungsumgebung, siehe kb/07-cloud-init.md) – das ist der von
  Spring Boot standardmäßig vorgesehene Testweg und braucht dort kein manuelles DB-Setup.
  `backend` hat keine Reactor-Abhängigkeit auf `common`, der Job baut daher nur `-pl backend`
  ohne `-am`.
- **backend-Job, Image-Build** (seit Phase 2 AP6, 2026-07-20): zusätzlicher Schritt
  `docker build -f backend/Dockerfile -t elwasys-backend:ci .` - baut nur (kein Push),
  beweist aber, dass `backend/Dockerfile` in einer echten Docker-Umgebung tatsächlich baubar
  ist (kann in dieser daemonlosen Sandbox nicht direkt verifiziert werden, siehe
  kb/07-cloud-init.md).

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
- **Backend-Image-Veröffentlichung** (seit Phase 2 AP6, 2026-07-20): baut zusätzlich
  `backend/Dockerfile` (derselbe, bereits per `versions:set` versionierte Arbeitsbaum als
  Build-Kontext) und pusht es nach GHCR (`ghcr.io/<owner>/elwasys-backend:<tag>` +
  `:latest`) - Anmeldung über den eingebauten `GITHUB_TOKEN` (`packages: write` war bereits
  gesetzt, kein zusätzliches Secret nötig). Andere Registries (z. B. Docker Hub) würden ein
  separates, hier bewusst nicht angelegtes Secret brauchen - offener Punkt für eine spätere
  Phase, falls gewünscht.

## Bekannte Build-Risiken

- Vaadin 7 / GWT 2.7 Widgetset-Compilation: langsam, speicherhungrig, alte Repos
  (`maven.vaadin.com`, teils `http://`), ggf. nicht mehr erreichbar.
- Alte Plugin-/Dependency-Versionen (Postgres 9.3-Treiber im Portal). Client-Raspi:
  unirest 1.x/HttpComponents 4.x sind seit Phase 4 AP2 entfernt (siehe
  kb/05-migration-plan.md); `pushover-client` bringt weiterhin transitiv eine eigene,
  ältere `httpcomponents:httpclient:4.2.1` mit (außerhalb des AP2-Auftrags, nicht
  angefasst).
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
