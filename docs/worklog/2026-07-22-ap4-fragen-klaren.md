# 2026-07-22 — AP4 (Auth & Security): Auftraggeber-Fragen geklärt

**Ziel:** Die drei offenen Auftraggeber-Entscheidungen (🧩) des Arbeitspakets **AP4 der
Pre-Launch-Review (Epic #66, „Auth & Security")** vor der Umsetzung klären und einfrieren,
damit die AP4-Fix-Umsetzung ohne Rückfrage laufen kann. Dieser Branch liefert **nur die
Entscheidungen + Doku**, keine Code-Änderung an der Auth-Naht (die folgt in der
AP4-Umsetzung).

> **Namens-Kollision beachtet:** „AP4" hier = Epic-#66-Paket *Auth & Security*, nicht die
> gleichnamige Migrations-Phase „Phase 4 AP4" (Client-Cutover).

## Geklärt (Auftraggeber, 2026-07-22)

- **#24 Passwort-Reset-Enumeration → neutralisieren.** Bei unbekannter Adresse still
  beenden, immer dieselbe neutrale Meldung; `UserNotFoundForEmailException` raus aus dem
  öffentlichen Pfad. Bewusste Verhaltensänderung ggü. Alt-Portal, freigegeben. Plus
  serverseitiges Rate-Limit gegen Mail-Flooding (gemeinsames Konzept mit Login-Brute-Force
  #25).
- **#44 Passwort-Mindestlänge → ≥ 8 Zeichen** zentral in
  `PasswordService.setNewPassword`/`changeOwnPassword` erzwingen. Bewusste Verschärfung,
  freigegeben.
- **#43 Standort-Token → minimale Variante (nur Doku/Runbook + ADR).** Restrisiko
  dokumentieren und bewusst akzeptieren, ADR 0008 (Admin-UI-Versprechen) korrigieren,
  Rotation/Widerruf mit verpflichtendem `revoked_at` beim Gerätetausch ins Runbook. **Kein**
  `expires_at`, **kein** Portal-UI, **keine** Snapshot-/Guthaben-Minimierung.

## Erledigt (Doku)

- Neue **ADR 0018** (`0018-ap4-auth-security-entscheidungen.md`) friert die drei
  Entscheidungen samt Kontext/Konsequenzen ein; ADR-Index ergänzt.
- **ADR 0008** korrigiert: das in Aussicht gestellte Token-Admin-UI wird nicht gebaut
  (Verwaltung bleibt CLI + Runbook), Restrisiko benannt.
- Auftraggeber-Festlegung chronologisch in `docs/kb/05-migration-plan.md`
  („Entscheidungen") nachgetragen.

## Offen / nächster Schritt

- **AP4-Umsetzung** als eigener PR: die drei geklärten 🧩-Punkte **plus** die
  nicht-entscheidungsbedürftigen Härtungen des Pakets — #21 (Regex-Injection Kartenlogin),
  #23 (case-insensitiver Login vs. Constraint), #25 (Login-Brute-Force/Rate-Limit),
  #26 (Fernwartungs-Standortvalidierung), #42, #45, #46, #47, #48 — jeweils mit
  Regressionstests (`PasswordResetServiceTest`, `PasswordServiceTest`, …).
- Runbook-Ergänzung (Token-Rotation/`revoked_at`) und KB-Restrisiko-Eintrag gehören mit in
  die AP4-Umsetzung bzw. AP6/AP7.
- Review-Gate (`code-reviewer`) vor Abschluss der AP4-Umsetzung.

## Referenzen

- Epic: https://github.com/ullriti/elwasys/issues/66 ; Issues #24, #43, #44
- ADR: [docs/architecture/0018-ap4-auth-security-entscheidungen.md](../architecture/0018-ap4-auth-security-entscheidungen.md),
  [0008](../architecture/0008-api-auth-standort-token-und-admin-session.md)
- Branch: `claude/ap4-fragen-klaren-kq6qvv`
