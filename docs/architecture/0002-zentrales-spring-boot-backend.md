# 2. Zentrales Spring-Boot-Backend als Zielarchitektur

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Im Bestand greifen zwei Anwendungen (JavaFX-Terminal und Vaadin-Portal) über die
gemeinsame `Common`-Bibliothek (`DataManager`, handgeschriebenes JDBC-Mapping) direkt
auf dieselbe PostgreSQL-DB zu. Das ist die Wurzel vieler Probleme: doppelte
Geschäftslogik, DB-Zugangsdaten auf jedem Terminal, ein Rechtemodell auf DB-Ebene und
eine IP-Registry für die Fernwartung. Als Rahmenbedingung des Auftraggebers ist Java
serverseitig gesetzt, alles andere darf neu gedacht werden.

## Entscheidung

Ein **zentrales Java-Backend auf Basis von Spring Boot 3.x** wird alleiniger Eigentümer
der Datenbank und der Geschäftslogik (Abrechnung, Berechtigungen, Programm-Ende). Nur
noch das Backend spricht SQL (Spring Data JPA). Portal-UI und Terminals werden zu
Clients dieses Backends. Der Umbau erfolgt nach dem Strangler-Muster parallel zum
Bestand auf derselben DB. Quarkus wurde erwogen, brachte aber keinen Team-Vorteil.

## Konsequenzen

- Geschäftslogik und DB-Zugriff sind an genau einer Stelle konsolidiert.
- Direkt-DB-Zugriff aus mehreren Anwendungen entfällt; Rechte werden API-seitig
  durchgesetzt.
- Spring Boot bringt WebSocket, Security, Scheduling und Mail mit.
- Ein neues, zentrales Deployment muss betrieben werden (siehe ADR 14).

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Zielarchitektur und
Technologie-Entscheidungen.
