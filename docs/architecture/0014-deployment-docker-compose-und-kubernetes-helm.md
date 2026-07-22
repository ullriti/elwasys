# 14. Backend-Deployment als Container: Docker-Compose oder Kubernetes (Helm)

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Das Alt-Portal wurde als WAR per Jetty-Plugin betrieben. Das neue Backend (inkl.
eingebettetem Portal) braucht ein modernes, reproduzierbares Betriebsmodell. Bestands-
installationen haben bereits eine laufende PostgreSQL-DB mit echten Nutzerdaten.

## Entscheidung

Das Backend wird als **Container-Image** ausgeliefert und wahlweise per **docker-compose**
(Backend + optional mitgelieferte Postgres-Instanz) oder **Kubernetes** betrieben; ein
**Helm-Chart wird mitgeliefert**. Grundsätze: die DB-Schemaanlage übernimmt Flyway (kein
gemountetes `database-init.sql`); eine **externe/bereits vorhandene DB ist der
dokumentierte Regelfall** (das Helm-Chart liefert bewusst kein Postgres-Sub-Chart mit).
TLS wird vor einem reinen HTTP-Backend terminiert – bei Compose per optionalem
Caddy-Overlay, in Kubernetes per (optionalem) Ingress mit cert-manager-Annotation.
Secrets (DB-/SMTP-Passwort, Pushover-Token) sind von der ConfigMap getrennt. Die Terminals
bleiben fat-jar + systemd via `setup.sh`.

## Konsequenzen

- Reproduzierbares, container-basiertes Deployment ohne WAR/Jetty.
- Docker/Kubernetes übernehmen Upgrade/Rollback des Backends selbst; kein eigenes
  Upgrade-Skript nötig (Smoke-Tests nach jedem Deployment genügen).
- Klare DB-Ownership: das Backend ist einziger DB-Client, die DB wird extern verwaltet.
- Das Release-Image wird nach GHCR veröffentlicht.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Technologie-Entscheidungen und
Entscheidungen (AP6, Deployment).
