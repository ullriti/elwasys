# Architecture Decision Records (ADRs)

Dieses Verzeichnis hält die tragenden Architektur- und Technik-Entscheidungen des
elwasys-Modernisierungsprojekts als einzelne, fortlaufend nummerierte ADRs fest. Die
Herleitung der Einzelentscheidungen findet sich im Modernisierungsplan
[`../kb/05-migration-plan.md`](../kb/05-migration-plan.md) unter „Entscheidungen".

| Nr. | Titel | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Architektur-Entscheidungen festhalten (ADRs) | accepted |
| [0002](0002-zentrales-spring-boot-backend.md) | Zentrales Spring-Boot-Backend als Zielarchitektur | accepted |
| [0003](0003-portal-als-vaadin-flow-im-backend.md) | Admin-Portal als eingebettete Vaadin-Flow-UI im Backend | accepted |
| [0004](0004-terminals-ohne-direkt-db-zugriff.md) | Terminals ohne Direkt-DB-Zugriff (REST + WebSocket + Standort-Token) | accepted |
| [0005](0005-fernwartung-ueber-ausgehende-websocket-verbindung.md) | Fernwartung über ausgehende WebSocket-Verbindung des Terminals | accepted |
| [0006](0006-postgresql-mit-flyway-schemaverwaltung.md) | PostgreSQL bleibt, Schema-Verwaltung über Flyway | accepted |
| [0007](0007-passwort-hashing-argon2id-mit-rehash.md) | Passwort-Hashing auf Argon2id mit transparenter Re-Hash-Migration | accepted |
| [0008](0008-api-auth-standort-token-und-admin-session.md) | API-Authentifizierung: Standort-Token für Terminals, Session-Login für Admins | accepted |
| [0009](0009-zentraler-benachrichtigungsdienst-hinter-flag.md) | Zentraler Benachrichtigungsdienst hinter Konfig-Flag | accepted |
| [0010](0010-offline-buchungen-am-terminal.md) | Offline-Robustheit und Offline-Buchungen am Terminal | accepted |
| [0011](0011-db-rollen-haertung-ein-app-user.md) | DB-Rollen-Härtung: ein technischer Anwendungs-User | accepted |
| [0012](0012-java-21-parent-pom-junit-5.md) | Java 21, Aggregator-Parent-POM und JUnit 5 als einheitliches Fundament | accepted |
| [0013](0013-verhalten-bewahren-strangler-und-e2e.md) | Grundsatz „Verhalten bewahren" mit Strangler-Muster und E2E-Sicherheitsnetz | accepted |
| [0014](0014-deployment-docker-compose-und-kubernetes-helm.md) | Backend-Deployment als Container: Docker-Compose oder Kubernetes (Helm) | accepted |
| [0015](0015-terminal-ui-bleibt-javafx.md) | Terminal-UI bleibt JavaFX (aktualisiert auf Java 21) | accepted |
| [0016](0016-offline-replay-haertung.md) | Offline-Replay-Härtung: privilegierter Nachbuchungs-Pfad, Zeitstempel-Invariante, Preis-Deckel | accepted |
| [0017](0017-abrechnungs-integritaet-locking.md) | Geld-/Abrechnungs-Integrität: pessimistisches Locking, Advisory-Locks, Idempotenz-Härtung | accepted |
