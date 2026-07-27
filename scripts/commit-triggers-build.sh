#!/bin/bash
# Entscheidet anhand einer Commit-Message, ob ein Push auf master einen CI-/Release-Lauf
# auslösen soll.
#
# Warum: Jeder master-Commit kommt hier über einen Pull Request, und der PR-Lauf hat die
# vollständige Suite bereits grün gesehen. Der master-Lauf ist damit Absicherung des
# Standard-Branches + Auslöser der Paketbereitstellung. Für Commits, die weder Code noch
# Paket verändern (docs:, chore:, style:, test:, ci:), ist beides Verschwendung: es entsteht
# kein neues Artefakt, das jemand installieren könnte, und die Terminals bekämen ein
# Vorab-Release ohne jede Verhaltensänderung angeboten.
#
# Gegenrichtung (bewusst fail-safe): Im Zweifel wird GEBAUT. Eine Message ohne erkennbaren
# Conventional-Commit-Typ (Merge-Commit, Handarbeit) gilt als relevant, ebenso jeder
# Breaking-Change-Marker – auch wenn er an einem sonst überspringbaren Typ hängt
# ("docs!: …" ändert per Definition Nutzer-sichtbares Verhalten).
#
# Aufruf:
#   commit-triggers-build.sh "<commit-message>"     Exit 0 = bauen, Exit 1 = überspringen
#   echo "<commit-message>" | commit-triggers-build.sh
#
# Verwendet von .github/workflows/ci.yml und .github/workflows/release.yml (Job "guard").
# Selbsttest: scripts/commit-triggers-build-selftest.sh
set -euo pipefail

# Conventional-Commit-Typen, die für sich genommen weder Verhalten noch Paket ändern.
# Alles andere (feat, fix, perf, refactor, revert, build …) löst einen Lauf aus - "build:"
# ist bewusst NICHT dabei: eine Abhängigkeits-/Dockerfile-Änderung will man gebaut sehen.
SKIPPABLE_TYPES='docs|style|chore|test|ci'

message="${1-}"
if [[ -z "${message}" ]]; then
    message="$(cat)"
fi

# Nur die Betreffzeile trägt den Typ; der Rest der Message wird auf Breaking-Change-Marker
# geprüft.
subject="$(printf '%s\n' "${message}" | head -n1)"

# Notausgang: erzwingt einen Lauf, egal welcher Typ davorsteht (z.B. "docs: … [build]").
if printf '%s' "${message}" | grep -qiE '\[(build|release)\]|\+semver:'; then
    exit 0
fi

# Breaking Change - immer bauen (Betreffzeile "typ(scope)!:" oder Footer "BREAKING CHANGE:").
if printf '%s' "${subject}" | grep -qE '^[a-zA-Z]+(\([^)]*\))?!:'; then
    exit 0
fi
if printf '%s\n' "${message}" | grep -qE '^BREAKING[ -]CHANGE:'; then
    exit 0
fi

# Überspringbarer Typ?
if printf '%s' "${subject}" | grep -qE "^(${SKIPPABLE_TYPES})(\([^)]*\))?: "; then
    exit 1
fi

# Alles andere: bauen.
exit 0
