# syntax=docker/dockerfile:1
# elwasys backend – container image (Phase 2 AP6, siehe kb/05-migration-plan.md).
#
# Build-Kontext ist die REPO-WURZEL, nicht dieses Verzeichnis (siehe docker-compose.yml unter
# deploy/compose/ und die "docker build"-Schritte in .github/workflows/*.yml):
#   docker build -f backend/Dockerfile -t elwasys-backend:local .
#
# Grund: das Backend-Modul hängt am Aggregator-Parent-POM (/pom.xml) und wird über den
# Root-Reactor gebaut - genau der Fallstrick aus kb/04-build-and-run.md/kb/05-migration-plan.md
# ("mvn -f Common/pom.xml install" installiert die Parent-POM NICHT mit ins lokale Repo).
# Ein reiner "backend/"-Build-Kontext hätte weder die Parent-POM noch Common zur Verfügung.
#
# Zwei mvn-Aufrufe, exakt wie in kb/04-build-and-run.md dokumentiert:
#   1. mvn -f pom.xml install -pl Common -am -DskipTests   (Common + Parent-POM ins Repo)
#   2. mvn -f pom.xml package -pl backend -DskipTests      (Backend-Jar bauen)
# Tests laufen hier bewusst NICHT (das Backend hat eine eigene, von Docker unabhängige
# Test-Pipeline mit Testcontainers/lokalem PostgreSQL-Override, siehe
# backend/run-backend-tests.sh) - der Image-Build ist ein reiner Paketierungsschritt.

# ---------------------------------------------------------------------------
# Stage 1: Abhängigkeiten auflösen (eigener Layer, cachebar solange sich die POMs nicht
# ändern - Quellcodeänderungen invalidieren diesen Layer NICHT).
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build

# Nur die POM-Dateien aller vier Reactor-Module kopieren: Maven muss beim Parsen des
# Root-Reactors (Parent-POM listet alle vier <module>-Einträge auf) jede Modul-POM lesen
# können, auch wenn mit -pl/-am letztlich nur Common und backend tatsächlich gebaut werden.
# Client-Raspi/Portal brauchen dafür NUR ihre pom.xml, kein Quellcode (werden nicht gebaut).
COPY pom.xml pom.xml
COPY Common/pom.xml Common/pom.xml
COPY Client-Raspi/pom.xml Client-Raspi/pom.xml
COPY Portal/pom.xml Portal/pom.xml
COPY backend/pom.xml backend/pom.xml

# Abhängigkeiten für Common + backend vorab auflösen/cachen (go-offline scheitert nicht an
# fehlenden Client-Raspi/Portal-Sourcen, da -pl/-am die Ausführung auf Common+backend
# beschränkt). Läuft mit dem lokalen Maven-Repo-Cache-Mount, damit wiederholte Builds in
# derselben Umgebung nicht jedes Mal alles neu herunterladen.
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -f pom.xml -pl backend -am dependency:go-offline

# ---------------------------------------------------------------------------
# Stage 2: Quellcode kopieren und bauen (eigener Layer - nur Common/backend-Quellcode nötig).
# ---------------------------------------------------------------------------
FROM deps AS build

COPY Common/src Common/src
COPY backend/src backend/src

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -f pom.xml install -pl Common -am -DskipTests \
    && mvn -B -f pom.xml package -pl backend -DskipTests \
    && cp backend/target/elwasys-backend.jar /build/elwasys-backend.jar

# ---------------------------------------------------------------------------
# Stage 3: schlankes Runtime-Image (nur JRE, non-root, Healthcheck).
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl wird ausschließlich für den HEALTHCHECK unten gebraucht (Actuator-Health per HTTP
# abfragen) - kleine, gezielte Ergänzung statt eines vollen Basis-Images.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root-User mit fester UID/GID (praktisch für Kubernetes PodSecurityContext, siehe
# deploy/helm/elwasys-backend/values.yaml runAsUser/runAsGroup). 10001 statt 1000 gewählt,
# weil einige Ubuntu-basierte Basis-Images (eclipse-temurin ist eines) bereits eine UID 1000
# vordefinieren ("ubuntu") - 10001 ist eine gängige, kollisionsarme Konvention für
# Container-Nutzer (u. a. von Distroless-Images verwendet).
RUN groupadd --gid 10001 elwasys \
    && useradd --uid 10001 --gid elwasys --shell /usr/sbin/nologin --no-create-home elwasys

WORKDIR /app
COPY --from=build /build/elwasys-backend.jar /app/elwasys-backend.jar
RUN chown -R elwasys:elwasys /app

USER elwasys

EXPOSE 8080

# Actuator-Health ist öffentlich erreichbar (siehe SecurityConfig, AP3) - kein Login nötig.
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/elwasys-backend.jar"]
