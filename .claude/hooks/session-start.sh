#!/bin/bash
# SessionStart hook for elwasys — prepares a remote Claude Code (web) session
# so that the Client-Raspi build and headless JavaFX UI tests can run.
#
# Idempotent and non-interactive. Runs only in the remote environment.
set -euo pipefail

# Only run in Claude Code on the web (remote) environment.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$PROJECT_DIR"

echo "[elwasys hook] Preparing remote session in $PROJECT_DIR"

# --- Tooling sanity check --------------------------------------------------
command -v java >/dev/null 2>&1 || { echo "[elwasys hook] ERROR: java not found"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "[elwasys hook] ERROR: maven not found"; exit 1; }
echo "[elwasys hook] Java: $(java -version 2>&1 | head -1)"
echo "[elwasys hook] Maven: $(mvn -v 2>&1 | head -1)"

# --- Headless display support for JavaFX (TestFX/Monocle fallback) ---------
# Monocle allows fully headless FX; Xvfb is the fallback if a display is needed.
if ! command -v Xvfb >/dev/null 2>&1; then
  echo "[elwasys hook] Installing Xvfb (virtual framebuffer) for headless UI tests"
  sudo apt-get update -qq && sudo apt-get install -y -qq xvfb libgtk-3-0 || \
    echo "[elwasys hook] WARN: could not install Xvfb; headless FX may rely on Monocle only"
fi

# --- Warm the Maven cache: install parent POM, resolve Client deps ----------
# Container state is cached after the hook, so subsequent builds/tests are fast.
# A per-module build (e.g. "mvn -f Client-Raspi/pom.xml package") needs the
# aggregator parent POM in the local repo; "mvn -N install" installs just that
# parent POM. (The former "common" module was dissolved after the migration –
# its classes now live in Client-Raspi, so there is nothing extra to install.)
echo "[elwasys hook] Installing the aggregator parent POM into the local Maven repo"
mvn -q -B -N install -DskipTests

echo "[elwasys hook] Resolving Client-Raspi dependencies (offline warm-up)"
mvn -q -B -f Client-Raspi/pom.xml dependency:go-offline -DskipTests || \
  echo "[elwasys hook] WARN: some Client deps could not be pre-fetched (will resolve on demand)"

echo "[elwasys hook] Done. Parent POM installed; Client dependencies warmed."
