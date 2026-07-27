#!/bin/bash
# Offline-Selbsttest für scripts/commit-triggers-build.sh - die Regel, die auf master
# entscheidet, ob ein Push CI und Paketbereitstellung auslöst. Kein Netz/Git nötig.
# Läuft in der CI (siehe .github/workflows/ci.yml, Job "guard").
#
# Zweck: Die Regel steuert, ob ein Release überhaupt entsteht. Ein stiller Fehler hier ist
# teuer in beide Richtungen - entweder bleibt eine Fehlerbehebung unveröffentlicht, oder
# jede Doku-Korrektur bietet den Terminals ein Update ohne Verhaltensänderung an.
set -euo pipefail

cd "$(dirname "$0")/.."   # Repo-Wurzel

SCRIPT="scripts/commit-triggers-build.sh"

FAIL=0

# $1 = erwartet ("build"/"skip"), $2 = Commit-Message
check() {
  local expected="$1" message="$2" actual
  if bash "${SCRIPT}" "${message}"; then actual="build"; else actual="skip"; fi
  if [[ "${actual}" == "${expected}" ]]; then
    printf 'PASS: %-5s <- %s\n' "${actual}" "$(printf '%s' "${message}" | head -n1)"
  else
    printf 'FAIL: %-5s (erwartet %s) <- %s\n' "${actual}" "${expected}" "$(printf '%s' "${message}" | head -n1)"
    FAIL=$((FAIL + 1))
  fi
}

echo "== Selbsttest: Commit-Message -> CI-/Release-Lauf auf master =="

# --- Löst einen Lauf aus: alles, was Code oder Paket verändert ---------------------------
check build "feat: add report export (#13)"
check build "feat(portal): add report export"
check build "fix(terminal): guard against a missing card reader (#12)"
check build "perf: cache program lookups"
check build "refactor: extract the execution finisher"
check build "revert: feat: add report export"
check build "build: bump spring-boot to 3.3.4"

# --- Kein Lauf: reine Doku-/Aufräum-/Test-/CI-Commits ------------------------------------
check skip  "docs: add ui mapping from claude design to vaadin flow"
check skip  "docs(kb): update current state"
check skip  "style: reformat the terminal controllers"
check skip  "chore: tidy up the scratch files"
check skip  "test: add a regression test for the offline replay"
check skip  "ci: cache the maven repository"

# --- Breaking Change schlägt die Skip-Liste ----------------------------------------------
check build "docs!: drop the documented legacy endpoint"
check build "refactor(api)!: drop legacy endpoint"
check build "$(printf 'chore: bump the terminal protocol\n\nBREAKING CHANGE: terminals below 1.2 no longer connect')"

# --- Notausgang: erzwungener Lauf --------------------------------------------------------
check build "docs: rewrite the runbook [build]"
check build "chore: retag the release [release]"
check build "chore: bump the schema +semver: minor"

# --- Fail-safe: unbekanntes Format wird gebaut, nicht übersprungen -----------------------
check build "Merge branch 'master' of https://github.com/ullriti/elwasys"
check build "update the thing"
check build "docsomething: not a real type"
# "docs:" ohne Leerzeichen dahinter ist kein gültiger Conventional-Commit-Betreff -> bauen.
check build "docs:no space after the colon"

# --- Message über stdin statt als Argument -----------------------------------------------
if printf 'docs: via stdin\n' | bash "${SCRIPT}"; then
  echo "FAIL: stdin-Pfad liefert 'build', erwartet 'skip'"
  FAIL=$((FAIL + 1))
else
  echo "PASS: skip  <- docs: via stdin (über stdin gelesen)"
fi

echo
if [[ "${FAIL}" == "0" ]]; then
  echo "Selbsttest bestanden."
else
  echo "Selbsttest FEHLGESCHLAGEN: ${FAIL} Abweichung(en)."
  exit 1
fi
