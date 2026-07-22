#!/usr/bin/env bash
#
# check-ai-docs.sh — mechanischer Check, dass die Agenten-/Wissensdateien strukturell
# korrekt genutzt werden (richtige Ordner, Index-Einträge, Pflichtabschnitte).
# Ergänzt die Beurteilungs-Checks im Command /audit-ai-docs.
#
# Nutzung:
#   scripts/check-ai-docs.sh          # prüfen, Exit 1 bei Befunden
#
# Exit-Code: 0 = sauber, 1 = mindestens ein Befund (CI-tauglich).
#
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

findings=0
section() { printf '\n== %s ==\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m⚠\033[0m %s\n' "$1"; findings=$((findings+1)); }

# 1) Pflichtdateien / -ordner
section "Struktur"
for f in AGENTS.md CLAUDE.md CHANGELOG.md \
         docs/kb/README.md docs/worklog/README.md docs/specs/README.md; do
  [[ -f "$f" ]] && ok "vorhanden: $f" || warn "fehlt: $f"
done
[[ -d docs/architecture ]] && ok "vorhanden: docs/architecture/" \
  || warn "fehlt: docs/architecture/"

# 2) CLAUDE.md zieht AGENTS.md per Import herein
section "CLAUDE.md → AGENTS.md"
if grep -q '@AGENTS.md' CLAUDE.md 2>/dev/null; then
  ok "CLAUDE.md importiert @AGENTS.md"
else
  warn "CLAUDE.md hat keinen '@AGENTS.md'-Import"
fi

# 3) Pflichtabschnitte
section "Pflichtabschnitte"
grep -q 'Aktueller Stand' docs/kb/README.md 2>/dev/null \
  && ok "KB hat 'Aktueller Stand'" \
  || warn "docs/kb/README.md: Abschnitt 'Aktueller Stand' fehlt"
grep -q '\[Unreleased\]' CHANGELOG.md 2>/dev/null \
  && ok "CHANGELOG hat '[Unreleased]'" \
  || warn "CHANGELOG.md: Abschnitt '[Unreleased]' fehlt"

# 4) Namens-/Ablagekonvention: Specs & ADRs = NNNN-*.md
section "Namenskonvention Specs/ADRs"
for dir in docs/specs docs/architecture; do
  [[ -d "$dir" ]] || continue
  for f in "$dir"/*.md; do
    [[ -e "$f" ]] || continue
    base="$(basename "$f")"
    [[ "$base" == "README.md" ]] && continue
    if [[ "$base" =~ ^[0-9]{4}- ]]; then
      ok "$dir/$base"
    else
      warn "$dir/$base: erwartet 'NNNN-titel.md' (falscher Ort/Name?)"
    fi
  done
done

# 5) Worklog: Dateiname YYYY-MM-DD-*.md und im Index verlinkt
section "Worklog-Einträge"
wl_readme="docs/worklog/README.md"
for f in docs/worklog/*.md; do
  [[ -e "$f" ]] || continue
  base="$(basename "$f")"
  [[ "$base" == "README.md" ]] && continue
  if [[ "$base" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}- ]]; then
    ok "Dateiname ok: $base"
  else
    warn "$base: erwartet 'YYYY-MM-DD-kurztitel.md'"
  fi
  if grep -qF "($base)" "$wl_readme" 2>/dev/null; then
    ok "im Index verlinkt: $base"
  else
    warn "nicht im Worklog-Index verlinkt: $base (erwartet Markdown-Link '[...]($base)')"
  fi
done

# 6) Tote Links im Worklog-Index (verlinkt, aber Datei fehlt)
section "Worklog-Index: tote Links"
if [[ -f "$wl_readme" ]]; then
  while IFS= read -r target; do
    [[ -n "$target" ]] || continue
    if [[ -f "docs/worklog/$target" ]]; then
      ok "Link ok: $target"
    else
      warn "toter Link im Index: $target"
    fi
  done < <(grep -oE '\(([0-9]{4}-[0-9]{2}-[0-9]{2}-[^)]+\.md)\)' "$wl_readme" \
            | tr -d '()')
fi

# Ergebnis
printf '\n'
if [[ "$findings" -eq 0 ]]; then
  printf '\033[32mOK: keine strukturellen Befunde.\033[0m\n'
  exit 0
fi
printf '\033[33m%d Befund(e) – siehe oben.\033[0m\n' "$findings"
exit 1
