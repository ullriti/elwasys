# 2026-07-20 — Phase 2: Backend-Gerüst (AP1–AP6)

**Ziel:** Ein neues zentrales Spring-Boot-Backend-Modul aufbauen, das das PostgreSQL-Schema
über Flyway verwaltet, die Geschäftslogik verhaltenserhaltend aus dem Alt-Code portiert,
Auth/REST-API/WebSocket bereitstellt und deploybar ist – additiv, ohne die drei Altmodule
(Common/Client-Raspi/Portal) anzufassen (Parallelbetrieb).

## Erledigt
- **AP1 – Backend-Gerüst + Flyway-Baseline**: neues Modul `backend/` im Root-Reactor
  (Spring Boot 3.5.16, Java 21, Actuator-Health, JDBC+Flyway); Baseline
  `V1__baseline_schema_0_4_0.sql` aus `database-init.sql` erzeugt (Rollenanlage idempotent,
  Spaltentypo bewusst erhalten). Schema-Äquivalenz + `baselineOnMigrate` gegen Bestands-DB
  verifiziert. Vierter CI-Job „Backend" (Testcontainers, lokaler Postgres-Override für die
  Docker-lose Sandbox). Tests 2/2.
- **AP2 – JPA-Entities/Repositories + Geschäftslogik**: 8 Entities 1:1 aufs Bestandsschema
  gemappt, 6 Repositories, 4 Services (`PricingService`, `CreditService`,
  `PermissionService`, `ExecutionService`) als 1:1-Portierung mit Alt-Code-Quellenverweis.
  **Alt-vs-Neu-Parity-Tests** (`Common` als test-scope, `LegacyDataManagerFactory`)
  vergleichen bitgenau – deckten die `new BigDecimal(double)`-Fließkomma-Eigenheit und die
  skalasensitive `price.equals(BigDecimal.ZERO)`-Prüfung auf, beide bewusst nachgebildet.
  27/27 grün.
- **AP3 – Auth (Argon2id + SHA1-Migrationspfad)**: `PasswordVerificationService`
  (Format-Erkennung Argon2id/SHA1, konstante Byte-Vergleiche), `ElwasysAuthenticationProvider`,
  `SecurityConfig`. Befund: `users.password VARCHAR(50)` zu klein für Argon2id (97 Zeichen)
  → additive Migration `V2` auf `VARCHAR(255)`. Re-Hash hinter Flag
  `elwasys.auth.rehash-on-login` (Default AUS, sonst Aussperrung im SHA1-Alt-Portal).
  Neues Backend weist gesperrte Nutzer aktiv ab (dokumentierte Abweichung). 52/52 grün.
- **AP4 – REST-API v1 + Standort-Token-Auth + WebSocket**: additive Migration
  `V3__create_terminal_tokens.sql`; `TerminalTokenService` (Hash statt Klartext, Rotation)
  + `TerminalTokenCliRunner` (Profil `token-cli`); eigene zustandslose `SecurityFilterChain`
  für `/api/v1/**` (Bearer-Token, `@Order(1)`). REST-API v1 (Kartenlogin, Geräte-/
  Programmliste, Execution-Lebenszyklus, Guthaben) mit DTOs, RFC-7807-Fehlerbildern,
  striktem Standort-Scope (404 statt 403, vgl. C16); springdoc/OpenAPI. WebSocket-Fundament
  `/api/v1/terminal-ws` (HELLO/PING/PONG/STATUS als Gerüst). 96/96 grün.
- **AP5 – Benachrichtigungsdienst (SMTP/Pushover)**: `NotificationService` 1:1 aus
  `ExecutionFinisher` portiert (E-Mail via `spring-boot-starter-mail`, Pushover via
  eigenem `java.net.http`-Client). Hinter `elwasys.notifications.enabled` (Default AUS, kein
  Doppelversand mit dem Client-Alt-Code), noch von keinem produktiven Ablauf aufgerufen.
  elwaApp-Push und Portal-Passwort-E-Mails bewusst nicht portiert. Fallstrick dokumentiert:
  `push_notification`-Spalte ist NICHT das Pushover-Opt-in. 11 neue Tests (GreenMail,
  JDK-`HttpServer`-Mock) → 107/107 grün.
- **AP6 – Deployment**: `backend/Dockerfile` (Multi-Stage, non-root, HEALTHCHECK) +
  `.dockerignore`; `deploy/compose/` (Backend + PostgreSQL 16, optionales TLS-Overlay mit
  Caddy); `deploy/helm/elwasys-backend/` (bewusst ohne PostgreSQL-Sub-Chart). Release-
  Workflow pusht das Image nach GHCR. Helm beschafft (über `go install`) und
  `helm lint`/`template` + `docker compose config` grün verifiziert.
- **Phase 2 abgeschlossen (QA-Review ohne Befunde)**: Diff-Review aller Commits (137
  Dateien, ~10.500 Zeilen), Tiefenprüfung kritischer Stellen (Auth-Hashing, Token-Speicherung,
  Flyway, Standort-Scope, Benachrichtigungs-Gating, Deployment). Kein Alt-Modul verändert.
  Parallelbetriebs-Beweis: V1–V3 auf die geteilte E2E-DB angewendet, Client-/Portal-Suiten
  danach erneut vollständig grün.

## Entscheidungen
- Nach Phase-2-Abschluss auf Auftraggeber-Wunsch **Offline-Buchungs-Konzept** aufgenommen
  (Snapshot, Offline-Entscheidung, Idempotenz-Journal, Replay, Zeitfenster); API-v1-
  Erweiterungsbedarf (Idempotenz/Snapshot) für Phase-4-Beginn vorgemerkt.

## Offen / nächster Schritt
- Phase 3 (Portal-Neubau): Vaadin-Flow-UI im Backend mit voller Feature-Parität.

## Referenzen
- docs/kb/05-migration-plan.md (Änderungslog), docs/kb/03-modules.md, docs/kb/02-data-model.md,
  docs/kb/04-build-and-run.md
