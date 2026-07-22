# 2026-07-22 — Finale Pre-Launch-Review

**Ziel:** Vor dem Live-Gang eine tiefe, möglichst vollständige Review durchführen –
nicht nur die Migrationsänderungen, sondern auch übernommene Konzepte – und für jedes
Finding ein GitHub-Issue anlegen, damit die Korrekturen vor dem Launch abgearbeitet
werden können.

## Erledigt
- Review in 6 parallelen Bereichen (Backend-API/WS/Auth, Backend-Geschäftslogik/DB,
  Vaadin-Portal, Terminal-Client, Deployment/CI/Cutover, Konzept/Architektur).
- 49 GitHub-Issues in `ullriti/elwasys` angelegt (#16–#64), gegliedert nach Schweregrad
  (BLOCKER/MAJOR/MINOR), jeweils mit Datei:Zeile, Fehlerszenario, Fix-Vorschlag und Tests.
- Kritischste Befunde vor dem Anlegen im Code stichprobenartig verifiziert
  (Regex-Injection Kartenlogin, Zeitstempel-Policy, CreditTopUpDialog).

## Kernbefunde
- **4 BLOCKER** (#16–#19): Offline-Replay verklemmt das Journal dauerhaft
  (Backend-Wächter + Client Poison-Entry/NPE/clear()-Race), Zeitstempel-Ersetzung erzeugt
  falsche/0-€-Abrechnung, deCONZ-WebSocket ohne Reconnect (Programm-Ende-Erkennung fällt aus).
- **16 MAJOR** (#20–#35): u.a. fehlendes Locking auf allen Geldpfaden, Regex-Injection im
  Kartenlogin, fehlende Betragsvalidierung beim Guthaben, Ausperren durch case-insensitiven
  Login, Reset-Enumeration + fehlendes Rate-Limiting, fehlendes Zeitzonen-Konzept (UTC),
  unvollständiges Dauerbetriebs-Konzept, offene Vaadin-Lizenzfrage, Watchdog-Update-Schleife,
  TLS im Runbook nur optional.
- **29 MINOR** (#36–#64): Härtung, Konsistenz, Testdeterminismus, Aufräumen.

## Entscheidungen (offen, Auftraggeber)
- Replay-Semantik (privilegierter Nachbuchungs-Pfad, negative Salden) – ADR-würdig (#16).
- Reset-Enumeration-Fix ist bewusste Verhaltensänderung ggü. Alt-Portal – Freigabe nötig (#24).
- Vaadin-Lizenz produktiv klären (#33).
- Doppelbelegungs-Bremse (partieller Unique-Index vs. Advisory-Lock) (#20).

## Offen / nächster Schritt
- Issues #16–#19 (BLOCKER) und die geldrelevanten MAJOR (#20, #21, #22, #29) vor dem
  Launch beheben; übrige MAJOR entscheiden/beheben; MINOR priorisiert nacharbeiten.
- Nach den Fixes: Testsuiten grün, dann Live-Gang.

## Referenzen
- Issues: https://github.com/ullriti/elwasys/issues (#16–#64)
- Branch: `claude/final-review-before-launch-qwv2by`
