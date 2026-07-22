# 11. DB-Rollen-Härtung: ein technischer Anwendungs-User

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Die DB kannte mehrere Rollen mit Default-Passwörtern im Init-SQL
(`elwaclient1`/`elwaportal`/`elwaapi`, Gruppe `elwaclients`) und setzte darüber ein
Rechtemodell auf DB-Ebene um. Mit mehreren direkt zugreifenden Anwendungen war das nötig,
aber ein Sicherheitsrisiko (Default-Secrets, verteilte Credentials).

## Entscheidung

Da nur noch das Backend mit der DB spricht (ADR 2), wird das Rechtemodell auf
**Anwendungsebene** durchgesetzt und die DB-Rollen werden gehärtet: `elwaclient1`,
`elwaapi` und die Gruppe `elwaclients` entfallen, **`elwaportal` ist der einzige
Anwendungs-DB-User**. Default-Secrets entfallen; das Default-Admin-Passwort wird nicht
mehr im Init-SQL gesetzt, sondern per `admin-cli` gesetzt. Reste der nicht mehr relevanten
mobilen App (`elwaapi`) – `auth_key`-Trigger/-Spalten, `reservations`, `foreign_authkeys`
– werden entfernt.

## Konsequenzen

- Keine Default-DB-Secrets und keine verteilten DB-Credentials mehr.
- Terminal-Rechte werden API-seitig statt DB-seitig durchgesetzt.
- Angriffsfläche und Schema um App-Reste bereinigt.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Komponenten-Inventur (Datenbank) und
Entscheidungen.
