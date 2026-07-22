# 8. API-Authentifizierung: Standort-Token für Terminals, Session-Login für Admins

- **Status:** accepted
- **Datum:** 2026-07-20

## Kontext

Terminals brauchen eine offline-fähig konfigurierbare, einfache Authentifizierung gegen
die neue API; Admins brauchen einen klassischen Login fürs Portal. OAuth/OIDC wäre für
diesen Einsatzfall Overkill.

## Entscheidung

Terminals authentifizieren sich mit einem **statischen Token pro Standort** (rotierbar),
übertragen als `Authorization: Bearer <token>` (Standard-HTTP-Mechanismus, nativ von
`java.net.http` und beim WS-Handshake nutzbar; kein proprietärer Header). Das Token wird
**nur als SHA-256-Hash** gespeichert (nie Klartext) – ein einfacher Hash genügt, weil das
Token selbst ein hochentropisches 32-Byte-`SecureRandom`-Geheimnis ist, keine
Wörterbuch-Zielscheibe. **Mehrere gleichzeitig aktive Tokens pro Standort** ermöglichen
Rotation ohne Ausfallfenster (neues Token anlegen, Terminal umstellen, altes per
`revoked_at` widerrufen). Admins nutzen einen Session-Login. Die Token-Verwaltung ist in
Phase 2 minimal (`TerminalTokenCliRunner`, Profil `token-cli`); ein Admin-UI kommt mit dem
Portal.

## Konsequenzen

- Einfache, offline-taugliche Terminal-Auth ohne OAuth-Infrastruktur.
- Rotation ohne Downtime möglich.
- Kein Klartext-Token in der DB.

Herkunft: docs/kb/05-migration-plan.md, Abschnitte Technologie-Entscheidungen und
Entscheidungen (AP4).
