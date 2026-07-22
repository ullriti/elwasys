# 12. Java 21, Aggregator-Parent-POM und JUnit 5 als einheitliches Fundament

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Das Repo hatte drei lose POMs ohne Parent mit Versions-Inkonsistenzen (Common
`0.0.0-local-development` vs. `0.3.4-SNAPSHOT`), gemischte Java-Level (8/16) und gemischte
Testframeworks (JUnit 4 + TestNG). Das ist fehleranfällig und erschwert reproduzierbare
Builds.

## Entscheidung

Das Repo bekommt ein **Aggregator-Parent-POM** mit einheitlichen Versionen/Properties und
einer Java-Toolchain auf **Java 21 LTS** (von JavaFX und Spring Boot 3 getragen; 25 LTS
später als Drop-in). Tests werden durchgängig auf **JUnit 5** vereinheitlicht; im Backend
kommen Testcontainers (Postgres) dazu, TestFX (Client) und Playwright (Portal) werden
weitergeführt. Der Terminal-Client führt DI ein (Voraussetzung für isolierte Tests), ohne
den Ablauf zu ändern.

## Konsequenzen

- Reproduzierbare, konsistente Builds; ein Einzelmodul-Build braucht die per
  `mvn -N install` installierte Parent-POM.
- Einheitlicher Test-Stack als Sicherheitsnetz.
- Modulstruktur änderte sich im Projektverlauf (später Auflösung des `common`-Moduls).

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Komponenten-Inventur (Infrastruktur/Repo)
und Technologie-Entscheidungen.
