#!/usr/bin/env bash
#
# bootstrap.sh — richtet das Projekt für Menschen und Agenten ein.
# In der Remote-Umgebung erledigt das der SessionStart-Hook
# (.claude/hooks/session-start.sh) bereits automatisch; dieses Skript ist das
# lokale Pendant.
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Aggregator-Parent-POM ins lokale Maven-Repo installieren"
mvn -q -B -N install -DskipTests

echo "==> Client-Raspi-Dependencies auflösen (Offline-Vorwärmung)"
mvn -q -B -f Client-Raspi/pom.xml dependency:go-offline -DskipTests || \
  echo "   (einige Dependencies konnten nicht vorab geladen werden – werden bei Bedarf aufgelöst)"

echo "==> Fertig. Nächste Schritte in AGENTS.md (Abschnitt 3: Build, Test, Lint)."
